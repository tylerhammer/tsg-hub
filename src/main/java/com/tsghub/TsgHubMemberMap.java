package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

final class TsgHubMemberMap
{
	static final int REFRESH_SECONDS = 10;
	private static final int DOT = 8;
	private static final int GAP = 2;
	private static final int PAD_X = 4;
	private static final int PAD_Y = 3;
	private static final Color PILL = new Color(0x1F, 0x1D, 0x22, 225);

	private final TsgHubPlugin plugin;
	private final ClientThread clientThread;
	private final WorldMapPointManager manager;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Client client;
	private final Map<String, WorldMapPoint> points = new HashMap<>();
	private final Map<String, BufferedImage> images = new HashMap<>();
	private final Map<WorldMapPoint, List<Member>> groups = new IdentityHashMap<>();
	private List<Member> members = new ArrayList<>();
	private float zoom;
	private volatile boolean open;

	TsgHubMemberMap(TsgHubPlugin plugin, Client client, ClientThread clientThread, WorldMapPointManager manager, ScheduledExecutorService executor, Supplier<TsgHubApi> api)
	{
		this.plugin = plugin;
		this.client = client;
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
		String clan = TsgHubApi.encode(plugin.clan().name());
		api.get().call("GET", "/v1/presence/positions?clanName=" + clan, null, null)
			.thenAcceptAsync(result -> apply(TsgHubJson.array(result, "members")), executor);
	}

	void apply(JsonArray members)
	{
		if (!open) return;
		String self = PlayerNames.key(plugin.clan().playerName());
		List<Member> next = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JsonObject member : TsgHubJson.objects(members))
		{
			String name = TsgHubJson.str(member, "displayName");
			String key = PlayerNames.key(name);
			if (key.isEmpty() || key.equals(self) || !seen.add(key)) continue;
			WorldPoint at = new WorldPoint(TsgHubJson.integer(member, "x", 0), TsgHubJson.integer(member, "y", 0), TsgHubJson.integer(member, "plane", 0));
			next.add(new Member(name, key, TsgHubJson.integer(member, "world", 0), at));
		}
		next.sort(Comparator.comparing(m -> m.key));
		clientThread.invokeLater(() -> {
			if (!open) return;
			this.members = next;
			regroup();
		});
	}

	void onClientTick()
	{
		if (!open || members.isEmpty()) return;
		WorldMap map = client.getWorldMap();
		if (map != null && map.getWorldMapZoom() != zoom) regroup();
	}

	private void regroup()
	{
		WorldMap map = client.getWorldMap();
		zoom = map == null ? 0 : map.getWorldMapZoom();
		Map<String, WorldMapPoint> next = new HashMap<>();
		for (List<Member> group : group(members, zoom, m -> image(m.name)))
		{
			Member lead = group.get(0);
			StringBuilder key = new StringBuilder();
			group.forEach(m -> key.append(m.key).append('|'));
			String label = group.size() == 1 ? lead.name : lead.name + " +" + (group.size() - 1);
			WorldMapPoint point = points.remove(key.toString());
			if (point == null)
			{
				BufferedImage image = image(label);
				point = new WorldMapPoint(lead.at, image)
				{
					@Override
					public void onEdgeSnap()
					{
						setJumpOnClick(true);
					}

					@Override
					public void onEdgeUnsnap()
					{
						setJumpOnClick(false);
					}
				};
				point.setImagePoint(new Point(DOT / 2, image.getHeight() / 2));
				point.setName(label);
				point.setSnapToEdge(true);
				manager.add(point);
			}
			point.setWorldPoint(lead.at);
			next.put(key.toString(), point);
			groups.put(point, group);
		}
		points.values().forEach(point -> {
			manager.remove(point);
			groups.remove(point);
		});
		points.clear();
		points.putAll(next);
	}

	Map<WorldMapPoint, List<Member>> groups()
	{
		return groups;
	}

	static List<List<Member>> group(List<Member> members, float zoom, Function<Member, BufferedImage> image)
	{
		List<List<Member>> groups = new ArrayList<>();
		boolean[] taken = new boolean[members.size()];
		for (int i = 0; i < members.size(); i++)
		{
			if (taken[i]) continue;
			Member lead = members.get(i);
			BufferedImage leadImage = image.apply(lead);
			List<Member> group = new ArrayList<>();
			group.add(lead);
			for (int j = i + 1; j < members.size(); j++)
			{
				Member other = members.get(j);
				if (taken[j] || !overlaps(lead, leadImage, other, image.apply(other), zoom)) continue;
				taken[j] = true;
				group.add(other);
			}
			groups.add(group);
		}
		return groups;
	}

	private static boolean overlaps(Member a, BufferedImage aImage, Member b, BufferedImage bImage, float zoom)
	{
		float dx = (b.at.getX() - a.at.getX()) * zoom;
		float dy = (b.at.getY() - a.at.getY()) * zoom;
		return Math.abs(dy) < Math.max(aImage.getHeight(), bImage.getHeight()) && dx < aImage.getWidth() && -dx < bImage.getWidth();
	}

	private BufferedImage image(String label)
	{
		return images.computeIfAbsent(label, TsgHubMemberMap::marker);
	}

	void clear()
	{
		clientThread.invokeLater(() -> {
			points.values().forEach(manager::remove);
			points.clear();
			groups.clear();
			members = new ArrayList<>();
			images.clear();
		});
	}

	@RequiredArgsConstructor
	static final class Member
	{
		final String name;
		final String key;
		final int world;
		final WorldPoint at;
	}

	static BufferedImage marker(String name)
	{
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D measure = probe.createGraphics();
		FontMetrics metrics = measure.getFontMetrics(FontManager.getRunescapeSmallFont());
		measure.dispose();
		int pillWidth = metrics.stringWidth(name) + PAD_X * 2;
		int height = Math.max(DOT, metrics.getAscent() + PAD_Y * 2);
		BufferedImage image = new BufferedImage(DOT + GAP + pillWidth, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int top = (height - DOT) / 2;
		g.setColor(Color.BLACK);
		g.fillOval(0, top, DOT, DOT);
		g.setColor(TsgHubTheme.ACCENT);
		g.fillOval(1, top + 1, DOT - 2, DOT - 2);
		int x = DOT + GAP;
		g.setColor(PILL);
		g.fillRoundRect(x, 0, pillWidth, height, 6, 6);
		g.setColor(TsgHubTheme.ACCENT);
		g.drawRoundRect(x, 0, pillWidth - 1, height - 1, 6, 6);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(TsgHubTheme.TEXT);
		g.drawString(name, x + PAD_X, (height + metrics.getAscent()) / 2 - 1);
		g.dispose();
		return image;
	}
}
