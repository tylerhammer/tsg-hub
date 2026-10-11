package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

final class TsgHubDrops
{
	static final int REFRESH_SECONDS = 60;
	private static final int CONTEXT_TICKS = 10;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private Map<String, Integer> itemIds;
	private final Recent<String> lootSources = new Recent<>();
	private final Recent<String> countMessages = new Recent<>();

	TsgHubDrops(TsgHubPlugin plugin, Client client, ClientThread clientThread, ScheduledExecutorService executor, Supplier<TsgHubApi> api, Supplier<TsgHubSidebarPanel> sidebar)
	{
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.api = api;
		this.sidebar = sidebar;
	}

	void onLoot(String source)
	{
		if (source == null || source.isEmpty() || !plugin.sharingEnabled()) return;
		lootSources.add(client.getTickCount(), source);
	}

	void onChatMessage(ChatMessage event)
	{
		if (!plugin.sharingEnabled()) return;
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			String text = Text.removeTags(event.getMessage()).trim();
			String lower = text.toLowerCase(Locale.ROOT);
			if (TsgHubRules.get().dropContext.stream().anyMatch(lower::contains)) countMessages.add(client.getTickCount(), text);
			return;
		}
		if (event.getType() != ChatMessageType.CLAN_MESSAGE) return;
		String message = broadcast(event.getMessage());
		long hash = client.getAccountHash();
		if (message == null || hash == -1 || executor.isShutdown()) return;
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.getDetectedClanName());
		body.addProperty("accountHash", Long.toString(hash));
		body.addProperty("message", message);
		body.addProperty("reporter", plugin.getDetectedPlayerName());
		int tick = client.getTickCount();
		body.addProperty("tick", tick);
		executor.schedule(() -> {
			if (!plugin.sharingEnabled()) return;
			JsonObject context = new JsonObject();
			context.add("loot", lootSources.since(tick - CONTEXT_TICKS, "source"));
			context.add("chat", countMessages.since(tick - CONTEXT_TICKS, "text"));
			body.add("context", context);
			post(body);
		}, TsgHubRules.get().dropDelayMs, TimeUnit.MILLISECONDS);
	}

	private void post(JsonObject body)
	{
		try { api.get().request("POST", "/v1/drops", null, body); }
		catch (Exception ignored) { }
	}

	static String broadcast(String raw)
	{
		if (raw == null) return null;
		String message = raw.replaceAll("<[^>]*>", "").replace(' ', ' ').trim();
		String lower = message.toLowerCase(Locale.ROOT);
		for (String keyword : TsgHubRules.get().dropKeywords) if (lower.contains(keyword)) return message;
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
				apply(api.get().request("GET", "/v1/drops?clanName=" + clan, null, null).getAsJsonArray("drops"));
			}
			catch (Exception e) { if (!quiet) ui(s -> s.setStatus("Couldn't load drops. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
			finally { if (!quiet) ui(s -> s.setBusy(false)); }
		});
	}

	void apply(JsonArray drops)
	{
		if (drops == null) return;
		if (clientThread == null) ui(s -> s.setDrops(drops));
		else clientThread.invokeLater(() -> {
			addItemIds(drops);
			ui(s -> s.setDrops(drops));
		});
	}

	private void addItemIds(JsonArray drops)
	{
		if (itemIds == null || itemIds.isEmpty()) itemIds = itemIndex();
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

	static final class Recent<T>
	{
		private static final int KEEP = 8;
		private final ArrayDeque<Map.Entry<Integer, T>> seen = new ArrayDeque<>();

		synchronized void add(int tick, T value)
		{
			seen.addLast(new AbstractMap.SimpleImmutableEntry<>(tick, value));
			while (seen.size() > KEEP) seen.removeFirst();
		}

		synchronized JsonArray since(int tick, String key)
		{
			JsonArray out = new JsonArray();
			for (Map.Entry<Integer, T> entry : seen)
			{
				if (entry.getKey() < tick) continue;
				JsonObject row = new JsonObject();
				row.addProperty("tick", entry.getKey());
				row.addProperty(key, String.valueOf(entry.getValue()));
				out.add(row);
			}
			return out;
		}
	}
}
