package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;

final class TsgHubCompetitionTracker
{
	private static final Pattern KILL_COUNT = Pattern.compile("(?:kill count|kill-count)[^0-9]*([0-9][0-9,]*)");
	private static final long XP_FLUSH_SECONDS = 30;

	private final Supplier<TsgHubApi> api;
	private final ScheduledExecutorService executor;
	private final Client client;
	private final ClientThread clientThread;
	private volatile List<JsonObject> joined = Collections.emptyList();
	// Flushed on a delay so each XP drop doesn't send a request.
	private final Map<String, Integer> pendingXp = new ConcurrentHashMap<>();

	TsgHubCompetitionTracker(Supplier<TsgHubApi> api, ScheduledExecutorService executor, Client client, ClientThread clientThread)
	{
		this.api = api;
		this.executor = executor;
		this.client = client;
		this.clientThread = clientThread;
	}

	void setEvents(JsonArray events)
	{
		List<JsonObject> running = new ArrayList<>();
		for (int i = 0; i < events.size(); i++)
		{
			JsonObject event = events.get(i).getAsJsonObject();
			String type = TsgHubUi.str(event, "type");
			if (!("skill".equals(type) || "boss".equals(type)) || !"active".equals(TsgHubUi.str(event, "status"))) continue;
			if (token(event).isEmpty()) continue;
			running.add(event);
		}
		joined = running;
		reportAllSkills();
	}

	void clear()
	{
		joined = Collections.emptyList();
		pendingXp.clear();
	}

	void reportAllSkills()
	{
		List<JsonObject> events = joined;
		if (events.stream().noneMatch(e -> "skill".equals(TsgHubUi.str(e, "type")))) return;
		clientThread.invokeLater(() -> {
			if (client.getLocalPlayer() == null) return;
			for (JsonObject event : events)
			{
				if (!"skill".equals(TsgHubUi.str(event, "type"))) continue;
				Skill skill = skill(event);
				if (skill != null) queueXp(event, client.getSkillExperience(skill));
			}
		});
	}

	void onXp(Skill skill, int xp)
	{
		for (JsonObject event : joined)
			if ("skill".equals(TsgHubUi.str(event, "type")) && skill == skill(event)) queueXp(event, xp);
	}

	void onChat(String message)
	{
		if (!message.contains("kill count") && !message.contains("kill-count")) return;
		Matcher count = KILL_COUNT.matcher(message);
		if (!count.find()) return;
		int kills;
		try { kills = Integer.parseInt(count.group(1).replace(",", "")); }
		catch (NumberFormatException e) { return; }
		for (JsonObject event : joined)
		{
			JsonObject config = config(event);
			if (!"boss".equals(TsgHubUi.str(event, "type")) || "loot".equals(TsgHubUi.str(config, "signal"))) continue;
			if (!message.contains(TsgHubUi.str(config, "npcName").toLowerCase(Locale.ROOT))) continue;
			JsonObject body = new JsonObject();
			body.addProperty("value", kills);
			submit(event, body);
		}
	}

	void onNpcLoot(String npcName, int tick)
	{
		if (npcName == null) return;
		for (JsonObject event : joined)
		{
			JsonObject config = config(event);
			if (!"boss".equals(TsgHubUi.str(event, "type")) || !"loot".equals(TsgHubUi.str(config, "signal"))) continue;
			if (!npcName.equalsIgnoreCase(TsgHubUi.str(config, "npcName"))) continue;
			JsonObject body = new JsonObject();
			body.addProperty("quantity", 1);
			body.addProperty("evidenceId", "loot-" + npcName.toLowerCase(Locale.ROOT) + "-" + tick);
			submit(event, body);
		}
	}

	private void queueXp(JsonObject event, int xp)
	{
		String id = TsgHubUi.str(event, "id");
		boolean scheduled = pendingXp.containsKey(id);
		pendingXp.put(id, xp);
		if (scheduled || executor.isShutdown()) return;
		executor.schedule(() -> {
			Integer latest = pendingXp.remove(id);
			if (latest == null) return;
			JsonObject body = new JsonObject();
			body.addProperty("value", latest);
			send(event, body);
		}, XP_FLUSH_SECONDS, TimeUnit.SECONDS);
	}

	private void submit(JsonObject event, JsonObject body)
	{
		if (!executor.isShutdown()) executor.submit(() -> send(event, body));
	}

	private void send(JsonObject event, JsonObject body)
	{
		String token = token(event);
		if (token.isEmpty()) return;
		try { api.get().request("POST", "/v1/events/" + TsgHubUi.str(event, "id") + "/progress", token, body); }
		catch (Exception ignored) { /* The next XP change or kill reports the latest total again. */ }
	}

	private static String token(JsonObject event)
	{
		return TsgHubSession.get("memberToken:" + TsgHubUi.str(event, "id"));
	}

	private static JsonObject config(JsonObject event)
	{
		return event.has("config") && event.get("config").isJsonObject() ? event.getAsJsonObject("config") : new JsonObject();
	}

	private static Skill skill(JsonObject event)
	{
		try { return Skill.valueOf(TsgHubUi.str(config(event), "skill")); }
		catch (IllegalArgumentException e) { return null; }
	}
}
