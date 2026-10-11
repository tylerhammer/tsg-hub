package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

final class TsgHubDrops
{
	private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final Set<String> COX_ITEMS = Set.of(
		"Dexterous prayer scroll", "Arcane prayer scroll", "Twisted buckler", "Dragon hunter crossbow",
		"Dinh's bulwark", "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Dragon claws",
		"Elder maul", "Kodai insignia", "Twisted bow", "Olmlet", "Metamorphic dust", "Twisted ancestral colour kit");
	private static final Set<String> TOB_ITEMS = Set.of(
		"Avernic defender hilt", "Ghrazi rapier", "Sanguinesti staff", "Justiciar faceguard", "Justiciar chestguard",
		"Justiciar legguards", "Scythe of vitur", "Lil' zik", "Sanguine dust", "Sanguine ornament kit", "Holy ornament kit");
	private static final Set<String> TOA_ITEMS = Set.of(
		"Osmumten's fang", "Lightbearer", "Elidinis' ward", "Masori mask", "Masori body", "Masori chaps",
		"Tumeken's shadow", "Tumeken's guardian", "Thread of Elidinis", "Breach of the scarab", "Eye of the corruptor",
		"Jewel of the sun", "Menaphite ornament kit", "Remnant of Akkha",
		"Remnant of Ba-Ba", "Remnant of Kephri", "Remnant of Zebak", "Ancient remnant");
	static final int REFRESH_SECONDS = 60;
	private static final int INDEX_CHUNK = 2000;
	private static final int CONTEXT_TICKS = 10;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private final Map<String, Integer> itemIds = new HashMap<>();
	private int indexedItems;
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
		String message = Text.removeTags(raw).replace(' ', ' ').trim();
		String lower = message.toLowerCase(Locale.ROOT);
		for (String keyword : TsgHubRules.get().dropKeywords) if (lower.contains(keyword)) return message;
		return null;
	}

	void load(boolean quiet)
	{
		if (!plugin.isInHubClan() || !plugin.sharingEnabled() || executor.isShutdown()) return;
		String clan = TsgHubApi.encode(plugin.getDetectedClanName());
		if (!quiet) plugin.ui(s -> s.setBusy(true));
		executor.submit(() -> {
			try
			{
				apply(api.get().request("GET", "/v1/drops?clanName=" + clan, null, null).getAsJsonArray("drops"), quiet ? null : () -> plugin.ui(s -> s.setBusy(false)));
			}
			catch (Exception e)
			{
				if (quiet) return;
				plugin.ui(s -> s.setStatus("Couldn't load drops. " + TsgHubUi.friendlyError(e), Tone.ERROR));
				plugin.ui(s -> s.setBusy(false));
			}
		});
	}

	void apply(JsonArray drops)
	{
		apply(drops, null);
	}

	private void apply(JsonArray drops, Runnable done)
	{
		if (drops == null)
		{
			if (done != null) done.run();
			return;
		}
		if (clientThread == null)
		{
			plugin.ui(s -> s.setDrops(drops));
			return;
		}
		clientThread.invoke(() -> {
			if (!indexItems()) return false;
			addItemIds(drops);
			plugin.ui(s -> s.setDrops(drops));
			if (done != null) done.run();
			return true;
		});
	}

	private void addItemIds(JsonArray drops)
	{
		for (int i = 0; i < drops.size(); i++)
		{
			JsonObject drop = drops.get(i).getAsJsonObject();
			Integer id = itemIds.get(TsgHubUi.str(drop, "item").toLowerCase(Locale.ROOT));
			if (id != null) drop.addProperty("itemId", id);
		}
	}

	private boolean indexItems()
	{
		int end = Math.min(client.getItemCount(), indexedItems + INDEX_CHUNK);
		for (int id = indexedItems; id < end; id++)
		{
			ItemComposition item = client.getItemDefinition(id);
			if (item == null || item.getNote() != -1 || item.getPlaceholderTemplateId() != -1) continue;
			String name = item.getName();
			if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name)) continue;
			itemIds.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
		}
		indexedItems = end;
		return client.getItemCount() > 0 && end >= client.getItemCount();
	}

	void autoRefresh()
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null && s.wantsDrops()) load(true);
	}

	static String dropTooltip(JsonObject drop)
	{
		String source = str(drop, "source");
		String rate = str(drop, "rate");
		int kc = integer(drop, "killCount", 0);
		String out = "";
		if (!source.isEmpty())
		{
			out += tipSection(true, tipLine(MUTED, "<b>DROPPED BY</b>"))
				+ tipCard(ACCENT, "<b>" + escape(source) + "</b>" + (rate.isEmpty() ? "" : "<br>" + escape(rate) + tipLine(MUTED, " drop rate")));
		}
		if (kc > 0) out += tipSection(out.isEmpty(), tipLine(MUTED, "<b>KILL COUNT</b> ") + String.format(Locale.US, "%,d", kc));
		return out;
	}

	static List<String> dropTags(JsonObject drop)
	{
		List<String> tags = new ArrayList<>();
		String kind = str(drop, "kind");
		if ("raid".equals(kind)) tags.add(raidName(str(drop, "item")));
		else if ("pet".equals(kind)) tags.add("Pet");
		else if ("dupe".equals(kind)) tags.add("Dupe pet");
		if (bool(drop, "newLog") || "clog".equals(kind)) tags.add("Log");
		return tags;
	}

	static String raidName(String item)
	{
		String base = item.replaceAll("(\\s*\\([^)]*\\))+$", "");
		if (COX_ITEMS.contains(base)) return "CoX";
		if (TOB_ITEMS.contains(base)) return "ToB";
		if (TOA_ITEMS.contains(base)) return "ToA";
		return "Raid";
	}

	static String dropItem(JsonObject drop)
	{
		int quantity = integer(drop, "quantity", 1);
		String item = str(drop, "item").replaceAll("(\\s*\\([^)]*\\))+$", "");
		return quantity > 1 ? quantity + " x " + item : item;
	}

	static String dropAge(String iso, Instant now)
	{
		Instant then;
		try { then = Instant.parse(iso); }
		catch (Exception e) { return ""; }
		long minutes = Math.max(0, Duration.between(then, now).toMinutes());
		if (minutes < 1) return "now";
		if (minutes < 60) return minutes + "m";
		long hours = minutes / 60;
		if (hours < 24) return hours + "h";
		return hours / 24 + "d";
	}

	static String dropWhen(String iso, Instant now, ZoneId zone)
	{
		String day = dropDay(iso, now, zone);
		if ("Earlier".equals(day)) return "";
		if ("Today".equals(day)) return dropAge(iso, now);
		return Instant.parse(iso).atZone(zone).format(CLOCK_TIME);
	}

	static String dropDay(String iso, Instant now, ZoneId zone)
	{
		Instant then;
		try { then = Instant.parse(iso); }
		catch (Exception e) { return "Earlier"; }
		LocalDate day = then.atZone(zone).toLocalDate();
		LocalDate today = now.atZone(zone).toLocalDate();
		if (!day.isBefore(today)) return "Today";
		if (day.equals(today.minusDays(1))) return "Yesterday";
		return localDate(then, zone);
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
