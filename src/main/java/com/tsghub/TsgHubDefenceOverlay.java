package com.tsghub;

import java.awt.*;
import java.util.*;
import java.util.List;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.*;

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
	private final boolean panel;

	TsgHubDefenceOverlay(Plugin plugin, TsgHubDefence defence, TsgHubConfig config, OverlayPosition position)
	{
		super(position == OverlayPosition.TOP_LEFT ? plugin : null);
		this.defence = defence;
		this.config = config;
		this.panel = position == OverlayPosition.TOP_LEFT;
		setPosition(position);
		if (!panel) setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public String getName()
	{
		return panel ? "TsgHubDefencePanel" : super.getName();
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		TsgHubConfig.DefenceStyle style = config.defenceStyle();
		if (!config.defenceLabel() || (style == TsgHubConfig.DefenceStyle.PANEL) != panel) return null;
		g.setFont(FontManager.getRunescapeSmallFont());
		if (panel) return panel(g);
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
		int baseline = at.getY() - 1;
		Rectangle glyphs = g.getFont().createGlyphVector(g.getFontRenderContext(), text).getPixelBounds(null, 0, 0);
		card(g, at.getX() + glyphs.x - 4, baseline + glyphs.y - 3, glyphs.width + 9, glyphs.height + 7, t.atFloor(), false);
		g.setColor(TsgHubTheme.MUTED);
		text(g, LABEL, at.getX(), baseline);
		g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.TEXT);
		text(g, String.valueOf(t.def), at.getX() + m.stringWidth(LABEL + " "), baseline);
	}

	private static void card(Graphics2D g, int x, int y, int width, int height, boolean capped, boolean thick)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(CARD);
		g.fillRoundRect(x, y, width, height, 5, 5);
		g.setColor(capped ? TsgHubTheme.CAPPED : TsgHubTheme.ACCENT);
		g.drawRoundRect(x, y, width - 1, height - 1, 5, 5);
		if (capped && thick) g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 4, 4);
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
		card(g, x, y, width, height, t.atFloor(), true);
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
		bar(g, t, x + PAD, baseline + 2, width - PAD * 2);
	}

	private Dimension panel(Graphics2D g)
	{
		List<TsgHubDefenceTarget> shown = new ArrayList<>();
		for (Map.Entry<NPC, TsgHubDefenceTarget> e : defence.targets().entrySet())
		{
			if (!e.getKey().isDead()) shown.add(e.getValue());
		}
		if (shown.isEmpty()) return null;
		int pad = 5;
		int rowGap = 3;
		FontMetrics m = g.getFontMetrics();
		TsgHubDefenceTarget focus = defence.focused();
		int line = m.getAscent();
		int textWidth = 0;
		int height = pad * 2 - rowGap;
		List<String> caps = new ArrayList<>();
		for (TsgHubDefenceTarget t : shown)
		{
			String cap = TsgHubDefence.capLabel(t, defence.held());
			caps.add(cap);
			textWidth = Math.max(textWidth, m.stringWidth(t.boss.name) + 10 + m.stringWidth(String.valueOf(t.def)));
			if (cap != null) textWidth = Math.max(textWidth, m.stringWidth(cap));
			height += line + 2 + BAR + rowGap + (cap == null ? 0 : line + 1);
		}
		int width = textWidth + pad * 2;
		card(g, 0, 0, width, height, focus != null && focus.atFloor(), true);
		int y = pad;
		for (int i = 0; i < shown.size(); i++)
		{
			TsgHubDefenceTarget t = shown.get(i);
			String value = String.valueOf(t.def);
			int baseline = y + line - 1;
			g.setColor(t == focus ? TsgHubTheme.TEXT : TsgHubTheme.MUTED);
			text(g, t.boss.name, pad, baseline);
			g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.TEXT);
			text(g, value, width - pad - m.stringWidth(value), baseline);
			y += line;
			if (caps.get(i) != null)
			{
				y += line + 1;
				g.setColor(TsgHubTheme.WARNING);
				text(g, caps.get(i), pad, y - 1);
			}
			y += 2;
			bar(g, t, pad, y, width - pad * 2);
			y += BAR + rowGap;
		}
		return new Dimension(width, height);
	}

	private static void bar(Graphics2D g, TsgHubDefenceTarget t, int x, int y, int width)
	{
		g.setColor(TRACK);
		g.fillRect(x, y, width, BAR);
		g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.ACCENT);
		g.fillRect(x, y, t.startDef <= 0 ? 0 : width * Math.max(0, t.def) / t.startDef, BAR);
	}

	private static void text(Graphics2D g, String s, int x, int y)
	{
		Color color = g.getColor();
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(color);
		g.drawString(s, x, y);
	}
}
