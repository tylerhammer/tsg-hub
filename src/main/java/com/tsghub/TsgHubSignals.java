package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TsgHubSignals
{
	private static final long RETRY_MILLIS = 10_000;
	private static final int MAX_BATCH = 50;

	private static final class Pending extends TsgHubOutbox.Item
	{
		final JsonObject message;
		final Set<String> deliveredTo = new HashSet<>();
		long sentAt;

		Pending(JsonObject message, long now)
		{
			super(now);
			this.message = message;
		}
	}

	private final TsgHubSocket socket;
	private final Supplier<TsgHubApi> api;
	private final Consumer<JsonObject> receiver;
	private final TsgHubOutbox<Pending> pending = new TsgHubOutbox<>();
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
			pending.add(message.get("id").getAsString(), entry);
			if (socket.sendSignal(message)) entry.sentAt = now;
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
		for (Pending entry : pending.values()) if (socket.sendSignal(entry.message)) entry.sentAt = now;
	}

	synchronized void clear()
	{
		pending.clear();
		offlineXp.clear();
	}

	void flush(Map<String, String> tokens)
	{
		long now = System.currentTimeMillis();
		List<JsonObject> xp = new ArrayList<>();
		List<Pending> entries = new ArrayList<>();
		synchronized (this)
		{
			pending.expire(now);
			if (socket.signaling())
			{
				offlineXp.clear();
				for (Pending entry : pending.values())
				{
					if (now - entry.sentAt >= RETRY_MILLIS && socket.sendSignal(entry.message)) entry.sentAt = now;
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
				JsonObject total = new JsonObject();
				total.addProperty("type", "xp");
				total.addProperty("skill", entry.getKey());
				total.addProperty("xp", entry.getValue());
				xp.add(total);
			}
			offlineXp.clear();
			for (Pending entry : pending.values())
			{
				if (entries.size() + xp.size() >= MAX_BATCH) break;
				entries.add(entry);
			}
		}
		boolean retryXp = false;
		for (Map.Entry<String, String> event : tokens.entrySet())
		{
			List<Pending> undelivered = new ArrayList<>();
			synchronized (this) { for (Pending entry : entries) if (!entry.deliveredTo.contains(event.getKey())) undelivered.add(entry); }
			if (undelivered.isEmpty() && xp.isEmpty()) continue;
			JsonArray signals = new JsonArray();
			xp.forEach(signals::add);
			undelivered.forEach(entry -> signals.add(entry.message));
			JsonObject body = new JsonObject();
			body.add("signals", signals);
			try
			{
				JsonObject result = api.get().request("POST", "/v1/events/" + event.getKey() + "/signals", event.getValue(), body);
				delivered(undelivered, event.getKey());
				for (JsonObject message : TsgHubJson.objects(TsgHubJson.array(result, "messages"))) receiver.accept(message);
			}
			catch (TsgHubApi.HttpError e)
			{
				if (e.status == 401 || e.status == 403 || e.status == 404) delivered(undelivered, event.getKey());
				else retryXp = true;
			}
			catch (Exception e)
			{
				retryXp = true;
				log.debug("TSG Hub signal fallback failed", e);
			}
		}
		synchronized (this)
		{
			for (Pending entry : entries)
			{
				if (entry.deliveredTo.containsAll(tokens.keySet())) pending.remove(entry.message.get("id").getAsString());
			}
			if (retryXp) for (JsonObject total : xp) offlineXp.merge(total.get("skill").getAsString(), total.get("xp").getAsInt(), Math::max);
		}
	}

	private synchronized void delivered(List<Pending> entries, String eventId)
	{
		for (Pending entry : entries) entry.deliveredTo.add(eventId);
	}
}
