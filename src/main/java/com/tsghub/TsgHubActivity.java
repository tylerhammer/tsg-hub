package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

final class TsgHubActivity
{
	static final int NO_RANK = Integer.MIN_VALUE;
	private static final String NO_CLAN = "No clan detected. Log in to a clan member character.";

	static final class Member
	{
		final String name;
		final String rank;
		final int rankValue;
		final int rankId;
		final LocalDate joined;
		final JsonObject stats;

		Member(String name, String rank, int rankValue, int rankId, LocalDate joined, JsonObject stats)
		{
			this.name = name;
			this.rank = rank;
			this.rankValue = rankValue;
			this.rankId = rankId;
			this.joined = joined;
			this.stats = stats;
		}

		Member withStats(JsonObject stats)
		{
			return new Member(name, rank, rankValue, rankId, joined, stats);
		}

		boolean onHub()
		{
			return stats != null;
		}

		Instant lastSeen()
		{
			return stats == null ? null : TsgHubJson.instant(TsgHubJson.str(stats, "lastSeenAt"));
		}

		int stat(String key)
		{
			return stats == null ? -1 : TsgHubJson.integer(stats, key, 0);
		}

		String version()
		{
			return TsgHubJson.str(stats, "pluginVersion");
		}

		boolean outdated()
		{
			return TsgHubJson.bool(stats, "outdated");
		}

		boolean discordLinked()
		{
			return TsgHubJson.bool(stats, "discordLinked");
		}

		List<String> previousNames()
		{
			return TsgHubJson.strings(TsgHubJson.array(stats, "previousNames"));
		}

		boolean inactive(Instant now, int thresholdDays)
		{
			Instant seen = lastSeen();
			return seen == null || seen.isBefore(now.minus(Duration.ofDays(thresholdDays)));
		}
	}

	static final class Snapshot
	{
		final List<Member> members;
		final int days;
		final int eventsInWindow;
		final String latestVersion;
		final boolean fromClan;

		Snapshot(List<Member> members, int days, int eventsInWindow, String latestVersion, boolean fromClan)
		{
			this.members = members;
			this.days = days;
			this.eventsInWindow = eventsInWindow;
			this.latestVersion = latestVersion;
			this.fromClan = fromClan;
		}
	}

	static final class Summary
	{
		final int total;
		final int active;
		final int onHub;
		final int turnoutPercent;

		Summary(int total, int active, int onHub, int turnoutPercent)
		{
			this.total = total;
			this.active = active;
			this.onHub = onHub;
			this.turnoutPercent = turnoutPercent;
		}
	}

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ChatIconManager chatIcons;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubActivityPanel> panel;
	private final Map<Integer, BufferedImage> rankIcons = new ConcurrentHashMap<>();
	private final AtomicInteger requests = new AtomicInteger();

	TsgHubActivity(TsgHubPlugin plugin, Client client, ClientThread clientThread, ChatIconManager chatIcons, ScheduledExecutorService executor,
		Supplier<TsgHubApi> api, Supplier<TsgHubActivityPanel> panel)
	{
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.chatIcons = chatIcons;
		this.executor = executor;
		this.api = api;
		this.panel = panel;
	}

	void load()
	{
		TsgHubActivityPanel view = panel.get();
		if (view != null) load(view.days());
	}

	void load(int days)
	{
		String token = plugin.adminKey();
		if (token.isEmpty() || executor.isShutdown())
		{
			ui(p -> p.failed("Clan activity needs a hub key with admin access."));
			return;
		}
		if (plugin.getDetectedClanName().isEmpty())
		{
			ui(p -> p.failed(NO_CLAN));
			return;
		}
		int request = requests.incrementAndGet();
		String query = "/v1/admin/activity?clanName=" + TsgHubApi.encode(plugin.getDetectedClanName()) + "&days=" + days;
		ui(p -> p.setStatus("Loading clan activity...", Tone.INFO));
		api.get().call("GET", query, token, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				clientThread.invokeLater(() -> {
					List<Member> clan = clanMembers();
					Snapshot snapshot = new Snapshot(merge(clan, TsgHubJson.array(result, "members")), TsgHubJson.integer(result, "days", days),
						TsgHubJson.integer(result, "eventsInWindow", 0), TsgHubJson.str(result, "latestVersion"), clan != null);
					ui(p -> { if (request == requests.get()) p.setActivity(snapshot); });
				});
			}
			catch (Exception e)
			{
				String message = "Couldn't load clan activity. " + TsgHubFormat.friendlyError(e);
				ui(p -> { if (request == requests.get()) p.failed(message); });
			}
		}, executor);
	}

	void loadMember(String name, int days, Consumer<JsonObject> loaded, Consumer<String> failed)
	{
		String token = plugin.adminKey();
		if (token.isEmpty() || executor.isShutdown())
		{
			SwingUtilities.invokeLater(() -> failed.accept("Member activity needs a hub key with admin access."));
			return;
		}
		if (plugin.getDetectedClanName().isEmpty())
		{
			SwingUtilities.invokeLater(() -> failed.accept(NO_CLAN));
			return;
		}
		String query = "/v1/admin/activity/member?clanName=" + TsgHubApi.encode(plugin.getDetectedClanName())
			+ "&name=" + TsgHubApi.encode(name) + "&days=" + TsgHubActivityDetail.ActivityChart.requestDays(days);
		api.get().call("GET", query, token, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				SwingUtilities.invokeLater(() -> loaded.accept(result));
			}
			catch (TsgHubApi.HttpError e)
			{
				String message = e.status == 404 ? name + " has no TSG Hub activity yet." : "Couldn't load " + name + ". " + TsgHubFormat.friendlyError(e);
				SwingUtilities.invokeLater(() -> failed.accept(message));
			}
			catch (Exception e)
			{
				String message = "Couldn't load " + name + ". " + TsgHubFormat.friendlyError(e);
				SwingUtilities.invokeLater(() -> failed.accept(message));
			}
		}, executor);
	}

	BufferedImage rankIcon(int rankId)
	{
		return rankIcons.get(rankId);
	}

	private void ui(Consumer<TsgHubActivityPanel> action)
	{
		SwingUtilities.invokeLater(() -> {
			TsgHubActivityPanel view = panel.get();
			if (view != null) action.accept(view);
		});
	}

	private List<Member> clanMembers()
	{
		ClanSettings settings = client.getClanSettings();
		if (settings == null) return null;
		List<Member> members = new ArrayList<>();
		for (ClanMember member : settings.getMembers())
		{
			if (member == null || member.getName() == null) continue;
			ClanRank rank = member.getRank();
			ClanTitle title = rank == null ? null : settings.titleForRank(rank);
			if (title != null && !rankIcons.containsKey(title.getId()))
			{
				BufferedImage icon = chatIcons.getRankImage(title);
				if (icon != null) rankIcons.put(title.getId(), icon);
			}
			members.add(new Member(Text.toJagexName(member.getName()), title == null || title.getName() == null ? "" : title.getName(),
				rank == null ? NO_RANK : rank.getRank(), title == null ? -1 : title.getId(), member.getJoinDate(), null));
		}
		return members;
	}

	static List<Member> merge(List<Member> clan, JsonArray server)
	{
		List<JsonObject> records = new ArrayList<>();
		for (int i = 0; i < server.size(); i++) if (server.get(i).isJsonObject()) records.add(server.get(i).getAsJsonObject());
		List<Member> out = new ArrayList<>();
		if (clan == null)
		{
			for (JsonObject stats : records) out.add(new Member(TsgHubJson.str(stats, "displayName"), "", NO_RANK, -1, null, stats));
			return out;
		}
		Set<String> clanNames = new HashSet<>();
		for (Member member : clan) clanNames.add(PlayerNames.key(member.name));
		Map<String, JsonObject> current = new HashMap<>();
		Map<String, JsonObject> previous = new HashMap<>();
		for (JsonObject stats : records)
		{
			String key = PlayerNames.key(TsgHubJson.str(stats, "displayName"));
			if (!key.isEmpty()) current.putIfAbsent(key, stats);
			if (clanNames.contains(key)) continue;
			for (String old : TsgHubJson.strings(TsgHubJson.array(stats, "previousNames")))
			{
				String oldKey = PlayerNames.key(old);
				if (!oldKey.isEmpty()) previous.putIfAbsent(oldKey, stats);
			}
		}
		for (Member member : clan)
		{
			String key = PlayerNames.key(member.name);
			JsonObject stats = current.get(key);
			out.add(member.withStats(stats != null ? stats : previous.get(key)));
		}
		return out;
	}

	static Summary summarize(List<Member> members, int eventsInWindow, Instant now, int thresholdDays)
	{
		int active = 0, onHub = 0, attended = 0;
		for (Member member : members)
		{
			if (!member.inactive(now, thresholdDays)) active++;
			if (!member.onHub()) continue;
			onHub++;
			attended += Math.min(eventsInWindow, member.stat("eventsAttended"));
		}
		int turnout = eventsInWindow <= 0 || onHub == 0 ? -1 : Math.round(100f * attended / (onHub * (float) eventsInWindow));
		return new Summary(members.size(), active, onHub, turnout);
	}

	static int compareVersions(String a, String b)
	{
		String[] x = a.split("\\."), y = b.split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++)
		{
			int left = i < x.length ? versionPart(x[i]) : 0;
			int right = i < y.length ? versionPart(y[i]) : 0;
			if (left != right) return Integer.compare(left, right);
		}
		return 0;
	}

	private static int versionPart(String part)
	{
		try
		{
			return Integer.parseInt(part);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	static String ago(Instant when, Instant now)
	{
		return when == null ? "never" : TsgHubFormat.ago(when, now);
	}
}
