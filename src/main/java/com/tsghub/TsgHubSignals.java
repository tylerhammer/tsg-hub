package com.tsghub;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import lombok.*;
import lombok.extern.slf4j.*;

@Slf4j
@RequiredArgsConstructor
final class TsgHubSignals
{
	private static final int MAX_BATCH = 50;

	private final TsgHubSocket socket;
	private final TsgHubOutbox outbox;
	private final TsgHubApi api;
	private final Consumer<JsonObject> receiver;
	private final Map<String, Integer> offlineXp = new HashMap<>();
	private boolean flushing;

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

	CompletableFuture<Void> flush(Map<String, String> tokens)
	{
		List<JsonObject> xp = new ArrayList<>();
		synchronized (this)
		{
			if (flushing) return CompletableFuture.completedFuture(null);
			if (socket.isLive() || tokens.isEmpty())
			{
				offlineXp.clear();
				return CompletableFuture.completedFuture(null);
			}
			offlineXp.forEach((skill, total) -> xp.add(TsgHubJson.obj("type", "xp", "skill", skill, "xp", total)));
			offlineXp.clear();
			flushing = true;
		}
		AtomicBoolean retryXp = new AtomicBoolean();
		CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
		for (Map.Entry<String, String> event : tokens.entrySet()) chain = chain.thenCompose(done -> post(event.getKey(), event.getValue(), xp, retryXp));
		return chain.handle((done, error) -> {
			outbox.deliveredEverywhere(tokens.keySet());
			synchronized (this)
			{
				flushing = false;
				if (retryXp.get()) for (JsonObject total : xp) offlineXp.merge(total.get("skill").getAsString(), total.get("xp").getAsInt(), Math::max);
			}
			return null;
		});
	}

	private CompletableFuture<Void> post(String eventId, String token, List<JsonObject> xp, AtomicBoolean retryXp)
	{
		List<JsonObject> undelivered = outbox.signalBatch(MAX_BATCH - xp.size(), eventId);
		if (undelivered.isEmpty() && xp.isEmpty()) return CompletableFuture.completedFuture(null);
		JsonArray signals = new JsonArray();
		xp.forEach(signals::add);
		undelivered.forEach(signals::add);
		return api.call("POST", "/v1/events/" + eventId + "/signals", token, TsgHubJson.obj("signals", signals)).handle((result, error) -> {
			int status = TsgHubApi.status(error);
			if (error == null || status == 401 || status == 403 || status == 404) outbox.delivered(undelivered, eventId);
			else
			{
				retryXp.set(true);
				log.debug("TSG Hub signal fallback failed", error);
			}
			if (result != null) TsgHubJson.objects(TsgHubJson.array(result, "messages")).forEach(receiver);
			return null;
		});
	}
}
