package com.tsghub;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;

final class TsgHubDrops
{
	private static final String[] KEYWORDS = {
		"received a drop:", "received special loot from a raid:", "funny feeling", "sneaking into", "acquired something special", "new collection log item:"
	};

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;

	TsgHubDrops(TsgHubPlugin plugin, Client client, ScheduledExecutorService executor, Supplier<TsgHubApi> api)
	{
		this.plugin = plugin;
		this.client = client;
		this.executor = executor;
		this.api = api;
	}

	void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.CLAN_MESSAGE || !plugin.sharingEnabled()) return;
		String message = broadcast(event.getMessage());
		long hash = client.getAccountHash();
		if (message == null || hash == -1 || executor.isShutdown()) return;
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.getDetectedClanName());
		body.addProperty("accountHash", Long.toString(hash));
		body.addProperty("message", message);
		executor.submit(() -> {
			try { api.get().request("POST", "/v1/drops", null, body); }
			catch (Exception ignored) { }
		});
	}

	static String broadcast(String raw)
	{
		if (raw == null) return null;
		String message = raw.replaceAll("<[^>]*>", "").replace(' ', ' ').trim();
		String lower = message.toLowerCase(Locale.ROOT);
		for (String keyword : KEYWORDS) if (lower.contains(keyword)) return message;
		return null;
	}

}
