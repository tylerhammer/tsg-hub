package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

final class TsgHubMemberMap
{
	static final int REFRESH_SECONDS = 10;
	private static final int MARKER_SIZE = 12;
	private static final BufferedImage MARKER = marker();

	private final TsgHubPlugin plugin;
	private final ClientThread clientThread;
	private final WorldMapPointManager manager;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Map<String, WorldMapPoint> points = new HashMap<>();
	private volatile boolean open;

	TsgHubMemberMap(TsgHubPlugin plugin, ClientThread clientThread, WorldMapPointManager manager, ScheduledExecutorService executor, Supplier<TsgHubApi> api)
	{
		this.plugin = plugin;
		this.clientThread = clientThread;
		this.manager = manager;
		this.executor = executor;
		this.api = api;
	}

	boolean isOpen()
	{
		return open;
	}

	void setOpen(boolean open)
	{
		this.open = open;
		if (!open) clear();
	}

	void autoRefresh()
	{
		if (!open || !plugin.canUseHub() || executor.isShutdown()) return;
		String clan = TsgHubApi.encode(plugin.getDetectedClanName());
		executor.submit(() -> {
			try { apply(TsgHubUi.array(api.get().request("GET", "/v1/presence/positions?clanName=" + clan, null, null), "members")); }
			catch (Exception ignored) { }
		});
	}

	void apply(JsonArray members)
	{
		if (!open) return;
		String self = PlayerNames.normalize(plugin.getDetectedPlayerName());
		clientThread.invokeLater(() -> {
			if (!open) return;
			Set<String> seen = new HashSet<>();
			for (JsonObject member : TsgHubUi.objects(members))
			{
				String name = TsgHubUi.str(member, "displayName");
				String key = PlayerNames.normalize(name);
				if (key.isEmpty() || key.equals(self) || !seen.add(key)) continue;
				WorldPoint at = new WorldPoint(TsgHubUi.integer(member, "x", 0), TsgHubUi.integer(member, "y", 0), TsgHubUi.integer(member, "plane", 0));
				int world = TsgHubUi.integer(member, "world", 0);
				String tooltip = world > 0 ? name + " (W" + world + ")" : name;
				WorldMapPoint point = points.get(key);
				if (point == null)
				{
					point = WorldMapPoint.builder().worldPoint(at).image(MARKER).name(name).tooltip(tooltip).snapToEdge(true).jumpOnClick(true).build();
					points.put(key, point);
					manager.add(point);
				}
				else
				{
					point.setWorldPoint(at);
					point.setTooltip(tooltip);
				}
			}
			points.entrySet().removeIf(entry -> {
				if (seen.contains(entry.getKey())) return false;
				manager.remove(entry.getValue());
				return true;
			});
		});
	}

	void clear()
	{
		clientThread.invokeLater(() -> {
			points.values().forEach(manager::remove);
			points.clear();
		});
	}

	private static BufferedImage marker()
	{
		BufferedImage image = new BufferedImage(MARKER_SIZE, MARKER_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(TsgHubTheme.ACCENT);
		g.fillOval(1, 1, MARKER_SIZE - 2, MARKER_SIZE - 2);
		g.setColor(TsgHubTheme.CARD);
		g.setStroke(new BasicStroke(1.5f));
		g.drawOval(1, 1, MARKER_SIZE - 3, MARKER_SIZE - 3);
		g.dispose();
		return image;
	}
}
