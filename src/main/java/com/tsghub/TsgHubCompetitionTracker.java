package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;

final class TsgHubCompetitionTracker
{
	static final Pattern KILL_COUNT = Pattern.compile("(?:kill count|kill-count)[^0-9]*([0-9][0-9,]*)");
	private static final long XP_FLUSH_SECONDS = 10;
	private static final long SCHEDULE_CHECK_SECONDS = 60;
	private static final int REWARD_TICKS = 2;
	static final Set<Integer> REWARD_INTERFACES = Set.of(
		InterfaceID.QUESTSCROLL,
		InterfaceID.XPREWARD,
		InterfaceID.PEST_REWARDSHOP,
		InterfaceID.BARBASSAULT_REWARD_SHOP,
		InterfaceID.SOUL_WARS_REWARDS,
		InterfaceID.AGILITYARENA_REWARDS,
		InterfaceID.TREK_REWARDS);

	private final Supplier<TsgHubApi> api;
	private final ScheduledExecutorService executor;
	private final Client client;
	private final ClientThread clientThread;
	private volatile List<JsonObject> tracked = Collections.emptyList();
	private volatile List<JsonObject> joined = Collections.emptyList();
	// Flushed on a delay so each XP drop doesn't send a request.
	private final Map<String, PendingXp> pendingXp = new ConcurrentHashMap<>();
	private final Map<Skill, Integer> lastXp = new ConcurrentHashMap<>();
	private final Deque<XpGain> recentGains = new ConcurrentLinkedDeque<>();
	private final Set<Integer> openRewards = ConcurrentHashMap.newKeySet();
	private volatile int rewardTick = Integer.MIN_VALUE;

	TsgHubCompetitionTracker(Supplier<TsgHubApi> api, ScheduledExecutorService executor, Client client, ClientThread clientThread)
	{
		this.api = api;
		this.executor = executor;
		this.client = client;
		this.clientThread = clientThread;
		executor.scheduleAtFixedRate(this::checkSchedule, SCHEDULE_CHECK_SECONDS, SCHEDULE_CHECK_SECONDS, TimeUnit.SECONDS);
	}

	void setEvents(JsonArray events)
	{
		List<JsonObject> found = new ArrayList<>();
		for (int i = 0; i < events.size(); i++)
		{
			JsonObject event = events.get(i).getAsJsonObject();
			String type = TsgHubUi.str(event, "type");
			if (!("skill".equals(type) || "boss".equals(type)) || "ended".equals(TsgHubUi.str(event, "status"))) continue;
			if (token(event).isEmpty()) continue;
			found.add(event);
		}
		tracked = found;
		joined = running(found);
		reportAllSkills();
	}

	void clear()
	{
		tracked = Collections.emptyList();
		joined = Collections.emptyList();
		pendingXp.clear();
		lastXp.clear();
		recentGains.clear();
		openRewards.clear();
		rewardTick = Integer.MIN_VALUE;
	}

	private void checkSchedule()
	{
		List<JsonObject> running = running(tracked);
		if (ids(running).equals(ids(joined))) return;
		joined = running;
		reportAllSkills();
	}

	private static List<JsonObject> running(List<JsonObject> events)
	{
		List<JsonObject> running = new ArrayList<>();
		Instant now = Instant.now();
		for (JsonObject event : events)
			if (TsgHubUi.running(event, now)) running.add(event);
		return running;
	}

	private static List<String> ids(List<JsonObject> events)
	{
		List<String> ids = new ArrayList<>();
		for (JsonObject event : events) ids.add(TsgHubUi.str(event, "id"));
		return ids;
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
				if (skill != null) queueXp(event, client.getSkillExperience(skill), 0);
			}
		});
	}

	void onXp(Skill skill, int xp)
	{
		Integer previous = lastXp.put(skill, xp);
		int tick = client.getTickCount();
		if (previous != null && xp > previous)
		{
			if (rewardActive(tick)) excludeXp(skill, xp - previous);
			else recentGains.addLast(new XpGain(tick, skill, xp - previous));
		}
		while (!recentGains.isEmpty() && tick - recentGains.peekFirst().tick > REWARD_TICKS) recentGains.pollFirst();
		for (JsonObject event : joined)
			if ("skill".equals(TsgHubUi.str(event, "type")) && skill == skill(event)) queueXp(event, xp, 0);
	}

	void onInterfaceOpened(int groupId)
	{
		if (!REWARD_INTERFACES.contains(groupId)) return;
		openRewards.add(groupId);
		int tick = client.getTickCount();
		rewardTick = tick;
		for (XpGain gain; (gain = recentGains.pollFirst()) != null; )
			if (tick - gain.tick <= REWARD_TICKS) excludeXp(gain.skill, gain.xp);
	}

	void onInterfaceClosed(int groupId)
	{
		if (openRewards.remove(groupId)) rewardTick = client.getTickCount();
	}

	private boolean rewardActive(int tick)
	{
		return !openRewards.isEmpty() || tick - rewardTick <= REWARD_TICKS;
	}

	private void excludeXp(Skill skill, int xp)
	{
		Integer total = lastXp.get(skill);
		if (total == null) return;
		for (JsonObject event : joined)
			if ("skill".equals(TsgHubUi.str(event, "type")) && skill == skill(event)) queueXp(event, total, xp);
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

	private void queueXp(JsonObject event, int xp, int excluded)
	{
		String id = TsgHubUi.str(event, "id");
		String token = token(event);
		if (token.isEmpty() || executor.isShutdown()) return;
		boolean[] schedule = {false};
		pendingXp.compute(id, (key, previous) -> {
			schedule[0] = previous == null;
			return new PendingXp(Math.max(xp, previous == null ? 0 : previous.xp), token, excluded + (previous == null ? 0 : previous.excluded));
		});
		if (!schedule[0]) return;
		executor.schedule(() -> {
			PendingXp latest = pendingXp.remove(id);
			if (latest == null) return;
			JsonObject body = new JsonObject();
			body.addProperty("value", latest.xp);
			if (latest.excluded > 0)
			{
				body.addProperty("excluded", latest.excluded);
				body.addProperty("evidenceId", "reward-" + UUID.randomUUID());
			}
			send(event, latest.token, body);
		}, XP_FLUSH_SECONDS, TimeUnit.SECONDS);
	}

	private void submit(JsonObject event, JsonObject body)
	{
		String token = token(event);
		if (!token.isEmpty() && !executor.isShutdown()) executor.submit(() -> send(event, token, body));
	}

	private void send(JsonObject event, String token, JsonObject body)
	{
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

	private static final class PendingXp
	{
		private final int xp;
		private final String token;
		private final int excluded;
		private PendingXp(int xp, String token, int excluded) { this.xp = xp; this.token = token; this.excluded = excluded; }
	}

	private static final class XpGain
	{
		private final int tick;
		private final Skill skill;
		private final int xp;
		private XpGain(int tick, Skill skill, int xp) { this.tick = tick; this.skill = skill; this.xp = xp; }
	}
}
