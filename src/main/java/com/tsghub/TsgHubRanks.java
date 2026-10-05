package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.util.Text;

final class TsgHubRanks
{
	static final int DEPUTY_OWNER = 125;
	static final int LEFT_CLAN = -1;
	private static final long DEBOUNCE_SECONDS = 3;
	private static final int INTERFACE_SCAN_TICKS = 5;
	private static final long RETRY_MILLIS = 60_000;

	static final class Change
	{
		final String name;
		final int oldRank;
		final int rank;

		Change(String name, int oldRank, int rank)
		{
			this.name = name;
			this.oldRank = oldRank;
			this.rank = rank;
		}

		@Override
		public String toString()
		{
			return name + ": " + oldRank + " -> " + rank;
		}
	}

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<String> key;
	private final Map<String, Change> pending = new LinkedHashMap<>();
	private final Map<Integer, String> titles = new HashMap<>();
	private volatile Map<String, Integer> known;
	private volatile int ownRank = LEFT_CLAN;
	private volatile boolean refused;
	private volatile long retryAt;
	private ScheduledFuture<?> flushTask;
	private int ticks;

	TsgHubRanks(TsgHubPlugin plugin, Client client, ScheduledExecutorService executor, Supplier<TsgHubApi> api, Supplier<String> key)
	{
		this.plugin = plugin;
		this.client = client;
		this.executor = executor;
		this.api = api;
		this.key = key;
	}

	void onScriptPostFired(int scriptId)
	{
		if (scriptId == ScriptID.CLAN_SIDEPANEL_DRAW) scan();
	}

	void onClanChannelChanged()
	{
		scan();
	}

	void onGameTick()
	{
		if (!allowed())
		{
			known = null;
			return;
		}
		ticks++;
		if (known == null || ticks % INTERFACE_SCAN_TICKS == 0 && client.getWidget(InterfaceID.ClansMembers.UNIVERSE) != null) scan();
	}

	void reset()
	{
		known = null;
		refused = false;
		retryAt = 0;
		synchronized (pending) { pending.clear(); }
	}

	void shutDown()
	{
		synchronized (pending)
		{
			if (flushTask != null) flushTask.cancel(false);
			flushTask = null;
			pending.clear();
		}
	}

	private boolean allowed()
	{
		return !refused && System.currentTimeMillis() >= retryAt && plugin.sharingEnabled() && plugin.isInHubClan() && !key.get().isEmpty() && plugin.getDetectedClanRank() >= DEPUTY_OWNER;
	}

	private void scan()
	{
		if (!allowed())
		{
			known = null;
			return;
		}
		ClanSettings settings = client.getClanSettings();
		if (settings == null) return;
		Map<String, Integer> current = new LinkedHashMap<>();
		Map<Integer, String> currentTitles = new HashMap<>();
		for (ClanMember member : settings.getMembers())
		{
			if (member == null || member.getName() == null || member.getRank() == null) continue;
			int rank = member.getRank().getRank();
			current.put(Text.toJagexName(member.getName()), rank);
			if (!currentTitles.containsKey(rank))
			{
				ClanTitle title = settings.titleForRank(new ClanRank(rank));
				currentTitles.put(rank, title == null || title.getName() == null ? "" : title.getName());
			}
		}
		Integer own = current.get(Text.toJagexName(plugin.getDetectedPlayerName()));
		if (!mayReport(own)) return;
		ownRank = own;
		synchronized (titles)
		{
			titles.clear();
			titles.putAll(currentTitles);
		}
		Map<String, Integer> before = known;
		known = current;
		if (before == null)
		{
			List<Change> all = new ArrayList<>();
			for (Map.Entry<String, Integer> entry : current.entrySet()) all.add(new Change(entry.getKey(), entry.getValue(), entry.getValue()));
			send(all, true);
			return;
		}
		List<Change> changes = diff(before, current);
		if (!changes.isEmpty()) queue(changes);
	}

	static boolean permanent(int status)
	{
		return status >= 400 && status < 500 && status != 429;
	}

	static boolean mayReport(Integer ownRank)
	{
		return ownRank != null && ownRank >= DEPUTY_OWNER;
	}

	static List<Change> diff(Map<String, Integer> before, Map<String, Integer> after)
	{
		List<Change> changes = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : after.entrySet())
		{
			Integer old = before.get(entry.getKey());
			if (old == null || !old.equals(entry.getValue())) changes.add(new Change(entry.getKey(), old == null ? LEFT_CLAN : old, entry.getValue()));
		}
		for (Map.Entry<String, Integer> entry : before.entrySet())
		{
			if (!after.containsKey(entry.getKey())) changes.add(new Change(entry.getKey(), entry.getValue(), LEFT_CLAN));
		}
		return changes;
	}

	static void merge(Map<String, Change> pending, List<Change> changes)
	{
		for (Change change : changes)
		{
			Change earlier = pending.get(change.name);
			int oldRank = earlier == null ? change.oldRank : earlier.oldRank;
			if (oldRank == change.rank) pending.remove(change.name);
			else pending.put(change.name, new Change(change.name, oldRank, change.rank));
		}
	}

	private void queue(List<Change> changes)
	{
		synchronized (pending)
		{
			merge(pending, changes);
			if (flushTask != null || pending.isEmpty() || executor.isShutdown()) return;
			flushTask = executor.schedule(this::flush, DEBOUNCE_SECONDS, TimeUnit.SECONDS);
		}
	}

	private void flush()
	{
		List<Change> changes;
		synchronized (pending)
		{
			flushTask = null;
			changes = new ArrayList<>(pending.values());
			pending.clear();
		}
		if (!changes.isEmpty()) post(changes, false);
	}

	private void send(List<Change> changes, boolean snapshot)
	{
		if (executor.isShutdown()) return;
		executor.submit(() -> post(changes, snapshot));
	}

	private void post(List<Change> changes, boolean snapshot)
	{
		String token = key.get();
		if (token.isEmpty() || !plugin.sharingEnabled()) return;
		JsonArray members = new JsonArray();
		for (Change change : changes)
		{
			JsonObject member = new JsonObject();
			member.addProperty("name", change.name);
			member.addProperty("rank", change.rank);
			if (!snapshot) member.addProperty("oldRank", change.oldRank);
			String title;
			synchronized (titles) { title = titles.get(change.rank); }
			if (title != null && !title.isEmpty()) member.addProperty("title", title);
			members.add(member);
		}
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.getDetectedClanName());
		body.addProperty("displayName", plugin.getDetectedPlayerName());
		body.addProperty("clanRank", ownRank);
		body.addProperty("snapshot", snapshot);
		body.add("members", members);
		try { api.get().request("POST", "/v1/clan/ranks", token, body); }
		catch (TsgHubApi.HttpError e)
		{
			if (permanent(e.status)) refused = true;
			else retry();
		}
		catch (Exception e) { retry(); }
	}

	private void retry()
	{
		retryAt = System.currentTimeMillis() + RETRY_MILLIS;
		known = null;
	}
}
