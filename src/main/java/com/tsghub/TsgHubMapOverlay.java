package com.tsghub;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

final class TsgHubMapOverlay extends Overlay
{
	private static final int PAD = 5;
	private static final int ROW_GAP = 2;
	private static final int COLUMN_GAP = 10;
	private static final int OFFSET = 12;
	private static final Color CARD = new Color(0x1F, 0x1D, 0x22, 235);

	private final Client client;
	private final WorldMapOverlay worldMap;
	private final TsgHubMemberMap memberMap;

	TsgHubMapOverlay(Client client, WorldMapOverlay worldMap, TsgHubMemberMap memberMap)
	{
		this.client = client;
		this.worldMap = worldMap;
		this.memberMap = memberMap;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		setPriority(Math.nextUp(PRIORITY_HIGHEST));
		drawAfterInterface(InterfaceID.WORLDMAP);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!memberMap.isOpen()) return null;
		Point mouse = client.getMouseCanvasPosition();
		List<TsgHubMemberMap.Member> hovered = hovered(mouse);
		if (hovered == null) return null;
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		int nameWidth = 0;
		int worldWidth = 0;
		for (TsgHubMemberMap.Member m : hovered)
		{
			nameWidth = Math.max(nameWidth, metrics.stringWidth(m.name));
			worldWidth = Math.max(worldWidth, metrics.stringWidth(world(m)));
		}
		int row = metrics.getAscent() + ROW_GAP;
		int width = PAD * 2 + nameWidth + (worldWidth > 0 ? COLUMN_GAP + worldWidth : 0);
		int height = PAD * 2 + row * hovered.size() - ROW_GAP;
		Rectangle canvas = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
		int x = Math.min(mouse.getX() + OFFSET, canvas.width - width - 1);
		int y = Math.min(mouse.getY() + OFFSET, canvas.height - height - 1);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(CARD);
		g.fillRoundRect(x, y, width, height, 6, 6);
		g.setColor(TsgHubTheme.ACCENT);
		g.drawRoundRect(x, y, width - 1, height - 1, 6, 6);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		int baseline = y + PAD + metrics.getAscent() - 1;
		for (TsgHubMemberMap.Member m : hovered)
		{
			g.setColor(TsgHubTheme.TEXT);
			g.drawString(m.name, x + PAD, baseline);
			String world = world(m);
			g.setColor(TsgHubTheme.MUTED);
			g.drawString(world, x + width - PAD - metrics.stringWidth(world), baseline);
			baseline += row;
		}
		return null;
	}

	private List<TsgHubMemberMap.Member> hovered(Point mouse)
	{
		if (mouse == null) return null;
		for (Map.Entry<WorldMapPoint, List<TsgHubMemberMap.Member>> entry : memberMap.groups().entrySet())
		{
			WorldMapPoint point = entry.getKey();
			if (point.isCurrentlyEdgeSnapped()) continue;
			Point at = worldMap.mapWorldPointToGraphicsPoint(point.getWorldPoint());
			BufferedImage image = point.getImage();
			if (at == null || image == null) continue;
			Point anchor = point.getImagePoint();
			int left = at.getX() - (anchor == null ? image.getWidth() / 2 : anchor.getX());
			int top = at.getY() - (anchor == null ? image.getHeight() / 2 : anchor.getY());
			if (new Rectangle(left, top, image.getWidth(), image.getHeight()).contains(mouse.getX(), mouse.getY())) return entry.getValue();
		}
		return null;
	}

	private static String world(TsgHubMemberMap.Member m)
	{
		return m.world > 0 ? "W" + m.world : "";
	}
}
