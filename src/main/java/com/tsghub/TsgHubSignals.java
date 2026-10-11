package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TsgHubSignals
{
	private static final long RETRY_MILLIS = 10_000;
	private static final long MAX_AGE_MILLIS = 600_000;
	private static final int MAX_PENDING = 200;
	private static final int MAX_BATCH = 50;

	private static final class Pending
	{
		final JsonObject message;
		final long createdAt;
		long sentAt;

		Pending(JsonObject message, long now)
		{
			this.message = message;
			this.createdAt = now;
		}
	}

	private final TsgHubSocket socket;
	private final Supplier<TsgHubApi> api;
	private final Consumer<JsonObject> receiver;
	private final Map<String, Pending> pending = new LinkedHashMap<>();
	private final Map<String, Integer> offlineXp = new HashMap<>();

	TsgHubSignals(TsgHubSocket socket, Supplier<TsgHubApi> api, Consumer<JsonObject> receiver)
	{
		this.socket = socket;
		this.api = api;
		this.receiver = receiver;
	}

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
		if (!message.has("id")) message.addProperty("id", newId());
		long now = System.currentTimeMillis();
		Pending entry = new Pending(message, now);
		synchronized (this)
		{
			pending.put(message.get("id").getAsString(), entry);
			while (pending.size() > MAX_PENDING) pending.remove(pending.keySet().iterator().next());
			if (socket.send(message)) entry.sentAt = now;
		}
	}

	void xp(String skill, int xp, int tick)
	{
		if (socket.xp(skill, xp, tick)) return;
		synchronized (this) { offlineXp.merge(skill, xp, Math::max); }
	}

	synchronized void acked(String id)
	{
		pending.remove(id);
	}

	synchronized void resend()
	{
		long now = System.currentTimeMillis();
		for (Pending entry : pending.values()) if (socket.send(entry.message)) entry.sentAt = now;
	}

	synchronized void clear()
	{
		pending.clear();
		offlineXp.clear();
	}

	void flush(Map<String, String> tokens)
	{
		long now = System.currentTimeMillis();
		List<JsonObject> batch = new ArrayList<>();
		synchronized (this)
		{
			pending.values().removeIf(entry -> now - entry.createdAt > MAX_AGE_MILLIS);
			if (socket.isLive())
			{
				offlineXp.clear();
				for (Pending entry : pending.values())
				{
					if (now - entry.sentAt >= RETRY_MILLIS && socket.send(entry.message)) entry.sentAt = now;
				}
				return;
			}
			if (tokens.isEmpty())
			{
				offlineXp.clear();
				return;
			}
			for (Map.Entry<String, Integer> entry : offlineXp.entrySet())
			{
				JsonObject xp = new JsonObject();
				xp.addProperty("type", "xp");
				xp.addProperty("skill", entry.getKey());
				xp.addProperty("xp", entry.getValue());
				batch.add(xp);
			}
			offlineXp.clear();
			Iterator<Pending> entries = pending.values().iterator();
			while (entries.hasNext() && batch.size() < MAX_BATCH) batch.add(entries.next().message);
		}
		if (batch.isEmpty()) return;
		JsonArray signals = new JsonArray();
		batch.forEach(signals::add);
		JsonObject body = new JsonObject();
		body.add("signals", signals);
		boolean delivered = true;
		for (Map.Entry<String, String> entry : tokens.entrySet())
		{
			try
			{
				JsonObject result = api.get().request("POST", "/v1/events/" + entry.getKey() + "/signals", entry.getValue(), body);
				for (JsonObject message : TsgHubUi.objects(TsgHubUi.array(result, "messages"))) receiver.accept(message);
			}
			catch (Exception e)
			{
				delivered = false;
				log.debug("TSG Hub signal fallback failed", e);
			}
		}
		synchronized (this)
		{
			for (JsonObject message : batch)
			{
				boolean xp = !message.has("id");
				if (delivered && !xp) pending.remove(message.get("id").getAsString());
				if (!delivered && xp) offlineXp.merge(message.get("skill").getAsString(), message.get("xp").getAsInt(), Math::max);
			}
		}
	}
}
