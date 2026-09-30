package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;

final class TsgHubDrops
{
	static final int REFRESH_SECONDS = 60;
	private static final String[] KEYWORDS = {
		"received a drop:", "received special loot from a raid:", "funny feeling", "sneaking into", "acquired something special", "new collection log item:"
	};

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private Map<String, Integer> itemIds;

	TsgHubDrops(TsgHubPlugin plugin, Client client, ClientThread clientThread, ScheduledExecutorService executor, Supplier<TsgHubApi> api, Supplier<TsgHubSidebarPanel> sidebar)
	{
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.api = api;
		this.sidebar = sidebar;
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

	void load(boolean quiet)
	{
		if (!plugin.isInHubClan() || !plugin.sharingEnabled() || executor.isShutdown()) return;
		String clan = encode(plugin.getDetectedClanName());
		if (!quiet) ui(s -> s.setBusy(true));
		executor.submit(() -> {
			try
			{
				JsonArray drops = api.get().request("GET", "/v1/drops?clanName=" + clan, null, null).getAsJsonArray("drops");
				if (clientThread == null) ui(s -> s.setDrops(drops));
				else clientThread.invokeLater(() -> {
					addItemIds(drops);
					ui(s -> s.setDrops(drops));
				});
			}
			catch (Exception e) { if (!quiet) ui(s -> s.setStatus("Couldn't load drops. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
			finally { if (!quiet) ui(s -> s.setBusy(false)); }
		});
	}

	private void addItemIds(JsonArray drops)
	{
		if (itemIds == null) itemIds = itemIndex();
		for (int i = 0; i < drops.size(); i++)
		{
			JsonObject drop = drops.get(i).getAsJsonObject();
			Integer id = itemIds.get(TsgHubUi.str(drop, "item").toLowerCase(Locale.ROOT));
			if (id != null) drop.addProperty("itemId", id);
		}
	}

	private Map<String, Integer> itemIndex()
	{
		Map<String, Integer> ids = new HashMap<>();
		for (int id = 0; id < client.getItemCount(); id++)
		{
			ItemComposition item = client.getItemDefinition(id);
			if (item == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1) continue;
			String name = item.getName();
			if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name)) continue;
			ids.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
		}
		return ids;
	}

	void autoRefresh()
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null && s.wantsDrops()) load(true);
	}

	private static String encode(String value)
	{
		try { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name()); }
		catch (java.io.UnsupportedEncodingException e) { return ""; }
	}

	private void ui(Consumer<TsgHubSidebarPanel> action)
	{
		SwingUtilities.invokeLater(() -> {
			TsgHubSidebarPanel s = sidebar.get();
			if (s != null) action.accept(s);
		});
	}
}
