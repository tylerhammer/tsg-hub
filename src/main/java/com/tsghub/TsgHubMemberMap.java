package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

final class TsgHubMemberMap
{
	static final int REFRESH_SECONDS = 10;
	private static final int DOT = 8;
	private static final int GAP = 3;

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
					BufferedImage image = marker(name);
					point = WorldMapPoint.builder().worldPoint(at).image(image).imagePoint(new Point(DOT / 2, image.getHeight() / 2)).name(name).tooltip(tooltip).snapToEdge(true).jumpOnClick(true).build();
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

	static BufferedImage marker(String name)
	{
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D measure = probe.createGraphics();
		FontMetrics metrics = measure.getFontMetrics(FontManager.getRunescapeSmallFont());
		measure.dispose();
		int height = Math.max(DOT, metrics.getAscent() + 1);
		BufferedImage image = new BufferedImage(DOT + GAP + metrics.stringWidth(name) + 1, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int top = (height - DOT) / 2;
		g.setColor(Color.BLACK);
		g.fillOval(0, top, DOT, DOT);
		g.setColor(TsgHubTheme.ACCENT);
		g.fillOval(1, top + 1, DOT - 2, DOT - 2);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setFont(FontManager.getRunescapeSmallFont());
		int x = DOT + GAP;
		int baseline = (height + metrics.getAscent()) / 2 - 1;
		g.setColor(Color.BLACK);
		g.drawString(name, x + 1, baseline + 1);
		g.setColor(Color.WHITE);
		g.drawString(name, x, baseline);
		g.dispose();
		return image;
	}
}
