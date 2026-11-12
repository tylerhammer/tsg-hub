package com.tsghub;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Map;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubDefenceOverlay extends Overlay
{
	private static final int PAD = 4;
	private static final int BAR = 2;
	private static final int GAP = 4;
	private static final int ROW_GAP = 2;
	private static final int ARROW = 5;
	private static final int HEIGHT_OFFSET = 40;
	private static final Color CARD = new Color(0x1F, 0x1D, 0x22);
	private static final Color TRACK = new Color(0x3B, 0x37, 0x41);
	private static final String LABEL = "DEF";

	private final TsgHubDefence defence;
	private final TsgHubConfig config;

	TsgHubDefenceOverlay(TsgHubDefence defence, TsgHubConfig config)
	{
		this.defence = defence;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		TsgHubConfig.DefenceStyle style = config.defenceStyle();
		if (!config.defenceLabel() || style == TsgHubConfig.DefenceStyle.PANEL) return null;
		g.setFont(FontManager.getRunescapeSmallFont());
		for (Map.Entry<NPC, TsgHubDefenceTarget> e : defence.targets().entrySet())
		{
			NPC npc = e.getKey();
			if (npc.isDead()) continue;
			if (style == TsgHubConfig.DefenceStyle.COMPACT) compact(g, npc, e.getValue());
			else draw(g, npc, e.getValue(), TsgHubDefence.capLabel(e.getValue(), defence.held()));
		}
		return null;
	}

	private void compact(Graphics2D g, NPC npc, TsgHubDefenceTarget t)
	{
		String text = LABEL + " " + t.def;
		FontMetrics m = g.getFontMetrics();
		Point at = npc.getCanvasTextLocation(g, text, npc.getLogicalHeight() + HEIGHT_OFFSET);
		if (at == null) return;
		int x = at.getX() - PAD;
		int y = at.getY() - m.getAscent() - PAD + 1;
		int width = m.stringWidth(text) + PAD * 2;
		int height = m.getAscent() + PAD * 2 - 1;
		card(g, x, y, width, height, t.atFloor());
		int baseline = at.getY() - 1;
		g.setColor(TsgHubTheme.MUTED);
		text(g, LABEL, at.getX(), baseline);
		g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.TEXT);
		text(g, String.valueOf(t.def), at.getX() + m.stringWidth(LABEL + " "), baseline);
	}

	static void card(Graphics2D g, int x, int y, int width, int height, boolean capped)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(CARD);
		g.fillRoundRect(x, y, width, height, 5, 5);
		g.setColor(capped ? TsgHubTheme.CAPPED : TsgHubTheme.ACCENT);
		g.drawRoundRect(x, y, width - 1, height - 1, 5, 5);
		if (capped) g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 4, 4);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	private void draw(Graphics2D g, NPC npc, TsgHubDefenceTarget t, String cap)
	{
		String value = String.valueOf(t.def);
		String drop = t.def < t.startDef ? String.valueOf(t.startDef - t.def) : "";
		FontMetrics m = g.getFontMetrics();
		int dropWidth = drop.isEmpty() ? 0 : GAP + ARROW + 2 + m.stringWidth(drop);
		int textWidth = Math.max(m.stringWidth(LABEL) + GAP + m.stringWidth(value) + dropWidth, cap == null ? 0 : m.stringWidth(cap));
		Point at = npc.getCanvasTextLocation(g, LABEL + " " + value, npc.getLogicalHeight() + HEIGHT_OFFSET);
		if (at == null) return;
		int row = m.getAscent() + ROW_GAP;
		int width = textWidth + PAD * 2;
		int height = m.getAscent() + PAD * 2 + BAR + 1 + (cap == null ? 0 : row);
		int x = at.getX() + (m.stringWidth(LABEL + " " + value) - width) / 2;
		int y = at.getY() - m.getAscent() - PAD - (cap == null ? 0 : row);
		Color tone = t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.ACCENT;
		card(g, x, y, width, height, t.atFloor());
		int baseline = y + PAD + m.getAscent() - 1;
		g.setColor(TsgHubTheme.MUTED);
		text(g, LABEL, x + PAD, baseline);
		g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.TEXT);
		int valueX = x + PAD + m.stringWidth(LABEL) + GAP;
		text(g, value, valueX, baseline);
		if (!drop.isEmpty())
		{
			int ax = valueX + m.stringWidth(value) + GAP;
			int ay = baseline - m.getAscent() / 2 - 1;
			g.setColor(tone);
			g.fillPolygon(new int[]{ax, ax + ARROW, ax + ARROW / 2 + 1}, new int[]{ay, ay, ay + ARROW - 1}, 3);
			g.setColor(TsgHubTheme.MUTED);
			text(g, drop, ax + ARROW + 2, baseline);
		}
		if (cap != null)
		{
			baseline += row;
			g.setColor(TsgHubTheme.WARNING);
			text(g, cap, x + PAD, baseline);
		}
		int barY = baseline + 2;
		int barWidth = width - PAD * 2;
		g.setColor(TRACK);
		g.fillRect(x + PAD, barY, barWidth, BAR);
		int filled = t.startDef <= 0 ? 0 : barWidth * Math.max(0, t.def) / t.startDef;
		g.setColor(tone);
		g.fillRect(x + PAD, barY, filled, BAR);
	}

	static void text(Graphics2D g, String s, int x, int y)
	{
		Color color = g.getColor();
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(color);
		g.drawString(s, x, y);
	}
}
