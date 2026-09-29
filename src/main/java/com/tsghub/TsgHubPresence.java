package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;

final class TsgHubPresence
{
	static final int REFRESH_SECONDS = 30;
	private static final long HEARTBEAT_MILLIS = 30_000;
	private static final long MIN_GAP_MILLIS = 5_000;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ChatIconManager chatIcons;
	private final Map<Integer, BufferedImage> rankIcons = new ConcurrentHashMap<>();
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private final TsgHubSocket socket;
	private final ActivityDetector activity = new ActivityDetector();
	private volatile JsonObject listed;
	private volatile long sentAt;
	private volatile AreaNames.Area area;

	TsgHubPresence(TsgHubPlugin plugin, Client client, ClientThread clientThread, ChatIconManager chatIcons, ScheduledExecutorService executor,
		Supplier<TsgHubApi> api, Supplier<TsgHubSidebarPanel> sidebar, TsgHubSocket socket)
	{
		this.socket = socket;
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.chatIcons = chatIcons;
		this.executor = executor;
		this.api = api;
		this.sidebar = sidebar;
	}

	void onXp(Skill skill, int xp)
	{
		activity.onXp(skill, xp, System.currentTimeMillis());
	}

	void onGameTick()
	{
		area = locate();
		JsonObject next = snapshot();
		if (next == null)
		{
			leave();
			return;
		}
		long now = System.currentTimeMillis();
		JsonObject current = listed;
		boolean changed = current == null || !next.equals(current);
		boolean due = !socket.isLive() && now - sentAt >= HEARTBEAT_MILLIS;
		if ((changed && now - sentAt >= MIN_GAP_MILLIS) || due) send(next, now);
	}

	void onLoggedOut()
	{
		leave();
		activity.reset();
	}

	void leave()
	{
		JsonObject body = leaveBody();
		if (body == null || socket.presence(null) || executor.isShutdown()) return;
		executor.submit(() -> post("/v1/presence/leave", body));
	}

	void shutDown()
	{
		JsonObject body = leaveBody();
		if (body == null) return;
		Thread thread = new Thread(() -> post("/v1/presence/leave", body), "tsg-hub-presence-leave");
		thread.setDaemon(true);
		thread.start();
	}

	void loadMembers(boolean quiet)
	{
		if (!canUse() || executor.isShutdown()) return;
		String clan = encode(plugin.getDetectedClanName());
		if (!quiet) ui(s -> s.setBusy(true));
		executor.submit(() -> {
			try
			{
				JsonArray members = api.get().request("GET", "/v1/presence?clanName=" + clan, null, null).getAsJsonArray("members");
				clientThread.invokeLater(() -> {
					addRanks(members);
					ui(s -> s.setMembers(members));
				});
			}
			catch (Exception e) { if (!quiet) ui(s -> s.setStatus("Couldn't load members. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
			finally { if (!quiet) ui(s -> s.setBusy(false)); }
		});
	}

	void autoRefresh()
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null && s.wantsMembers()) loadMembers(true);
	}

	private void addRanks(JsonArray members)
	{
		ClanSettings settings = client.getClanSettings();
		if (settings == null) return;
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
			ClanMember found = settings.findMember(TsgHubUi.str(member, "displayName"));
			if (found == null || found.getRank() == null) continue;
			ClanTitle title = settings.titleForRank(found.getRank());
			if (title == null) continue;
			if (title.getName() != null) member.addProperty("rank", title.getName());
			member.addProperty("rankId", title.getId());
			BufferedImage icon = chatIcons.getRankImage(title);
			if (icon != null) rankIcons.put(title.getId(), icon);
		}
	}

	BufferedImage rankIcon(JsonObject member)
	{
		return member.has("rankId") ? rankIcons.get(TsgHubUi.integer(member, "rankId", 0)) : null;
	}

	private JsonObject snapshot()
	{
		if (!plugin.sharingEnabled() || !plugin.isInHubClan() || !plugin.inClanChat()) return null;
		if (client.getGameState() != GameState.LOGGED_IN) return null;
		Player player = client.getLocalPlayer();
		long hash = client.getAccountHash();
		String name = plugin.getDetectedPlayerName();
		if (player == null || player.getLocalLocation() == null || hash == -1 || name.isEmpty()) return null;
		boolean detailed = plugin.locationSharingEnabled();
		AreaNames.Area area = this.area;
		JsonObject payload = new JsonObject();
		payload.addProperty("clanName", plugin.getDetectedClanName());
		payload.addProperty("displayName", name);
		payload.addProperty("accountHash", Long.toString(hash));
		payload.addProperty("world", client.getWorld());
		payload.addProperty("area", area == null ? "" : area.name);
		payload.addProperty("activity", detailed ? activity.activity(area, System.currentTimeMillis()) : "Online");
		return payload;
	}

	String area()
	{
		AreaNames.Area current = area;
		return current == null ? "" : current.name;
	}

	private AreaNames.Area locate()
	{
		if (!plugin.locationSharingEnabled() || client.getGameState() != GameState.LOGGED_IN) return null;
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null) return null;
		return AreaNames.at(WorldPoint.fromLocalInstance(client, player.getLocalLocation()));
	}

	private void send(JsonObject payload, long now)
	{
		listed = payload;
		sentAt = now;
		if (!socket.presence(payload) && !executor.isShutdown()) executor.submit(() -> post("/v1/presence", payload));
	}

	private JsonObject leaveBody()
	{
		JsonObject current = listed;
		if (current == null) return null;
		listed = null;
		sentAt = 0;
		JsonObject body = new JsonObject();
		body.add("clanName", current.get("clanName"));
		body.add("accountHash", current.get("accountHash"));
		return body;
	}

	private void post(String path, JsonObject body)
	{
		try { api.get().request("POST", path, null, body); }
		catch (Exception ignored) { }
	}

	private boolean canUse()
	{
		return plugin.isInHubClan() && plugin.sharingEnabled() && !plugin.getDetectedPlayerName().isEmpty();
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
