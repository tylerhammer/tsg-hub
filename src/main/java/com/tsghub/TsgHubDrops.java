package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

@RequiredArgsConstructor
final class TsgHubDrops
{
	static final int REFRESH_SECONDS = 60;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ScheduledExecutorService executor;
	private final Set<ScheduledFuture<?>> pending = ConcurrentHashMap.newKeySet();
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private final Recent<JsonObject> lootSources = new Recent<>();
	private final Recent<String> countMessages = new Recent<>();

	void onLoot(String source, List<String> items)
	{
		if (source == null || source.isEmpty() || !plugin.sharingEnabled()) return;
		JsonObject loot = new JsonObject();
		loot.addProperty("source", source);
		JsonArray names = new JsonArray();
		items.forEach(names::add);
		loot.add("items", names);
		lootSources.add(client.getTickCount(), loot);
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
		String self = plugin.clan().playerName();
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.clan().name());
		body.addProperty("accountHash", Long.toString(hash));
		body.addProperty("message", message);
		body.addProperty("reporter", self);
		int tick = client.getTickCount();
		body.addProperty("tick", tick);
		pending.removeIf(Future::isDone);
		pending.add(executor.schedule(() -> {
			if (!plugin.sharingEnabled()) return;
			if (!fromSelf(message, self))
			{
				post(body);
				return;
			}
			JsonObject context = new JsonObject();
			int since = tick - TsgHubRules.get().dropContextTicks;
			context.add("loot", lootSources.since(since, JsonObject::deepCopy));
			context.add("chat", countMessages.since(since, text -> {
				JsonObject row = new JsonObject();
				row.addProperty("text", text);
				return row;
			}));
			body.add("context", context);
			post(body);
		}, TsgHubRules.get().dropDelayMs, TimeUnit.MILLISECONDS));
	}

	void shutDown()
	{
		pending.forEach(task -> task.cancel(false));
		pending.clear();
	}

	private void post(JsonObject body)
	{
		plugin.tracker().report("broadcast", "/v1/broadcasts", body);
	}

	static boolean fromSelf(String message, String self)
	{
		if (self == null || self.isEmpty() || message.length() <= self.length() || message.charAt(self.length()) != ' ') return false;
		return samePlayer(message.substring(0, self.length()), self);
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
		if (!plugin.clan().isHubClan() || !plugin.sharingEnabled() || executor.isShutdown()) return;
		String clan = TsgHubApi.encode(plugin.clan().name());
		String query = query();
		Runnable done = quiet ? null : () -> plugin.ui(s -> s.setBusy(false));
		if (!quiet) plugin.ui(s -> s.setBusy(true));
		api.get().call("GET", "/v1/drops?clanName=" + clan + query, null, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonArray drops = TsgHubApi.result(response, error).getAsJsonArray("drops");
				if (query.equals(query())) apply(drops, done);
				else if (done != null) done.run();
			}
			catch (Exception e)
			{
				if (quiet) return;
				plugin.ui(s -> s.setStatus("Couldn't load drops. " + TsgHubFormat.friendlyError(e), Tone.ERROR));
				plugin.ui(s -> s.setBusy(false));
			}
		}, executor);
	}

	void onSnapshot(JsonArray drops)
	{
		if (query().isEmpty()) apply(drops, null);
		else load(true);
	}

	private String query()
	{
		TsgHubSidebarPanel s = sidebar.get();
		return s == null ? "" : s.dropsView.dropsQuery();
	}

	private void apply(JsonArray drops, Runnable done)
	{
		if (drops != null) plugin.ui(s -> s.dropsView.setDrops(drops));
		if (done != null) done.run();
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
		if (kc > 0) out += tipSection(out.isEmpty(), tipLine(MUTED, "<b>KILL COUNT</b> ") + TsgHubFormat.number(kc));
		JsonObject luck = object(drop, "luck");
		if (luck != null && luck.has("share") && kc > 0)
		{
			String share = luckPercent(luck.get("share").getAsDouble());
			String text = "spooned".equals(str(luck, "verdict")) ? "Only " + share + " of players" : share + " of players";
			out += tipSection(out.isEmpty(), tipLine(MUTED, "<b>LUCK</b> ") + text + " would have it by this kill count");
		}
		return out;
	}

	static String luckPercent(double share)
	{
		double percent = share * 100;
		return (percent < 1 ? String.format(Locale.ROOT, "%.1f", percent) : String.format(Locale.ROOT, "%.0f", percent)) + "%";
	}

	static String dropAge(String iso, Instant now)
	{
		Instant then = TsgHubJson.instant(iso);
		if (then == null) return "";
		String elapsed = TsgHubFormat.elapsed(then, now);
		return elapsed.isEmpty() ? "now" : elapsed;
	}

	static String dropWhen(String iso, Instant now, ZoneId zone)
	{
		String day = dropDay(iso, now, zone);
		if ("Earlier".equals(day)) return "";
		if ("Today".equals(day)) return dropAge(iso, now);
		return Instant.parse(iso).atZone(zone).format(TsgHubFormat.CLOCK_24);
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
		private final ArrayDeque<Map.Entry<Integer, T>> seen = new ArrayDeque<>();

		synchronized void add(int tick, T value)
		{
			seen.addLast(new AbstractMap.SimpleImmutableEntry<>(tick, value));
			while (seen.size() > TsgHubRules.get().dropContextKeep) seen.removeFirst();
		}

		synchronized JsonArray since(int tick, Function<T, JsonObject> row)
		{
			JsonArray out = new JsonArray();
			for (Map.Entry<Integer, T> entry : seen)
			{
				if (entry.getKey() < tick) continue;
				JsonObject value = row.apply(entry.getValue());
				value.addProperty("tick", entry.getKey());
				out.add(value);
			}
			return out;
		}
	}
}
