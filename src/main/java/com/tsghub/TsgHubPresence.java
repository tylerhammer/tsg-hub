package com.tsghub;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

final class TsgHubPresence
{
	private static final long HEARTBEAT_MILLIS = 30_000;
	private static final long MIN_GAP_MILLIS = 5_000;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final ActivityDetector activity = new ActivityDetector();
	private volatile JsonObject listed;
	private volatile long sentAt;

	TsgHubPresence(TsgHubPlugin plugin, Client client, ScheduledExecutorService executor, Supplier<TsgHubApi> api)
	{
		this.plugin = plugin;
		this.client = client;
		this.executor = executor;
		this.api = api;
	}

	void onXp(Skill skill, int xp)
	{
		activity.onXp(skill, xp, System.currentTimeMillis());
	}

	void onGameTick()
	{
		JsonObject next = snapshot();
		if (next == null)
		{
			leave();
			return;
		}
		long now = System.currentTimeMillis();
		JsonObject current = listed;
		boolean changed = current == null || !next.equals(current);
		if ((changed && now - sentAt >= MIN_GAP_MILLIS) || now - sentAt >= HEARTBEAT_MILLIS) send(next, now);
	}

	void onLoggedOut()
	{
		leave();
		activity.reset();
	}

	void leave()
	{
		JsonObject body = leaveBody();
		if (body == null || executor.isShutdown()) return;
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

	private JsonObject snapshot()
	{
		if (!plugin.sharingEnabled() || !plugin.isInHubClan() || !plugin.inClanChat()) return null;
		if (client.getGameState() != GameState.LOGGED_IN) return null;
		Player player = client.getLocalPlayer();
		long hash = client.getAccountHash();
		String name = plugin.getDetectedPlayerName();
		if (player == null || player.getLocalLocation() == null || hash == -1 || name.isEmpty()) return null;
		WorldPoint point = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		AreaNames.Area area = point == null ? null : AreaNames.forRegion(point.getRegionID());
		JsonObject payload = new JsonObject();
		payload.addProperty("clanName", plugin.getDetectedClanName());
		payload.addProperty("displayName", name);
		payload.addProperty("accountHash", Long.toString(hash));
		payload.addProperty("world", client.getWorld());
		payload.addProperty("area", area == null ? "" : area.name);
		payload.addProperty("activity", activity.activity(area, System.currentTimeMillis()));
		return payload;
	}

	private void send(JsonObject payload, long now)
	{
		listed = payload;
		sentAt = now;
		if (!executor.isShutdown()) executor.submit(() -> post("/v1/presence", payload));
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

}
