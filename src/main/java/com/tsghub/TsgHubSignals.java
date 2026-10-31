package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
final class TsgHubSignals
{
	private static final int MAX_BATCH = 50;

	private final TsgHubSocket socket;
	private final TsgHubOutbox outbox;
	private final Supplier<TsgHubApi> api;
	private final Consumer<JsonObject> receiver;
	private final Map<String, Integer> offlineXp = new HashMap<>();

	boolean wantsChat(String message)
	{
		return matches(TsgHubRules.get().chat, message);
	}

	boolean wantsPlayers(String message)
	{
		return matches(TsgHubRules.get().players, message);
	}

	private static boolean matches(List<String> rules, String message)
	{
		for (String rule : rules) if (message.contains(rule)) return true;
		return false;
	}

	static String newId()
	{
		return UUID.randomUUID().toString();
	}

	void send(String type, JsonObject message)
	{
		message.addProperty("type", type);
		outbox.signal(type, message, socketOnly(message));
	}

	void xp(String skill, int xp, int tick)
	{
		if (socket.xp(skill, xp, tick)) return;
		synchronized (this) { offlineXp.merge(skill, xp, Math::max); }
	}

	static boolean socketOnly(JsonObject message)
	{
		String type = TsgHubJson.str(message, "type");
		return "sale".equals(type) || "payment".equals(type);
	}

	synchronized void clear()
	{
		outbox.clearSignals();
		offlineXp.clear();
	}

	void flush(Map<String, String> tokens)
	{
		List<JsonObject> xp = new ArrayList<>();
		synchronized (this)
		{
			if (socket.isLive() || tokens.isEmpty())
			{
				offlineXp.clear();
				return;
			}
			for (Map.Entry<String, Integer> entry : offlineXp.entrySet())
			{
				JsonObject total = new JsonObject();
				total.addProperty("type", "xp");
				total.addProperty("skill", entry.getKey());
				total.addProperty("xp", entry.getValue());
				xp.add(total);
			}
			offlineXp.clear();
		}
		boolean retryXp = false;
		for (Map.Entry<String, String> event : tokens.entrySet())
		{
			List<JsonObject> undelivered = outbox.signalBatch(MAX_BATCH - xp.size(), event.getKey());
			if (undelivered.isEmpty() && xp.isEmpty()) continue;
			JsonArray signals = new JsonArray();
			xp.forEach(signals::add);
			undelivered.forEach(signals::add);
			JsonObject body = new JsonObject();
			body.add("signals", signals);
			try
			{
				JsonObject result = api.get().request("POST", "/v1/events/" + event.getKey() + "/signals", event.getValue(), body);
				outbox.delivered(undelivered, event.getKey());
				for (JsonObject message : TsgHubJson.objects(TsgHubJson.array(result, "messages"))) receiver.accept(message);
			}
			catch (TsgHubApi.HttpError e)
			{
				if (e.status == 401 || e.status == 403 || e.status == 404) outbox.delivered(undelivered, event.getKey());
				else retryXp = true;
			}
			catch (Exception e)
			{
				retryXp = true;
				log.debug("TSG Hub signal fallback failed", e);
			}
		}
		outbox.deliveredEverywhere(tokens.keySet());
		if (!retryXp) return;
		synchronized (this)
		{
			for (JsonObject total : xp) offlineXp.merge(total.get("skill").getAsString(), total.get("xp").getAsInt(), Math::max);
		}
	}
}
