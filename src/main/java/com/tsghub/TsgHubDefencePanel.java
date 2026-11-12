package com.tsghub;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.NPC;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubDefencePanel extends Overlay
{
	private static final int PAD = 5;
	private static final int GAP = 10;
	private static final int ROW_GAP = 3;
	private static final int BAR = 2;
	private static final Color TRACK = new Color(0x3B, 0x37, 0x41);

	private final TsgHubDefence defence;
	private final TsgHubConfig config;

	TsgHubDefencePanel(Plugin plugin, TsgHubDefence defence, TsgHubConfig config)
	{
		super(plugin);
		this.defence = defence;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.defenceLabel() || config.defenceStyle() != TsgHubConfig.DefenceStyle.PANEL) return null;
		List<TsgHubDefenceTarget> shown = new ArrayList<>();
		for (Map.Entry<NPC, TsgHubDefenceTarget> e : defence.targets().entrySet())
		{
			if (!e.getKey().isDead()) shown.add(e.getValue());
		}
		if (shown.isEmpty()) return null;
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics m = g.getFontMetrics();
		TsgHubDefenceTarget focus = defence.focused();
		int line = m.getAscent();
		int textWidth = 0;
		int height = PAD * 2 - ROW_GAP;
		List<String> caps = new ArrayList<>();
		for (TsgHubDefenceTarget t : shown)
		{
			String cap = TsgHubDefence.capLabel(t, defence.held());
			caps.add(cap);
			textWidth = Math.max(textWidth, m.stringWidth(t.boss.name) + GAP + m.stringWidth(String.valueOf(t.def)));
			if (cap != null) textWidth = Math.max(textWidth, m.stringWidth(cap));
			height += line + 2 + BAR + ROW_GAP + (cap == null ? 0 : line + 1);
		}
		int width = textWidth + PAD * 2;
		TsgHubDefenceOverlay.card(g, 0, 0, width, height, focus != null && focus.atFloor());
		int y = PAD;
		for (int i = 0; i < shown.size(); i++)
		{
			TsgHubDefenceTarget t = shown.get(i);
			String value = String.valueOf(t.def);
			int baseline = y + line - 1;
			g.setColor(t == focus ? TsgHubTheme.TEXT : TsgHubTheme.MUTED);
			g.drawString(t.boss.name, PAD, baseline);
			g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.TEXT);
			g.drawString(value, width - PAD - m.stringWidth(value), baseline);
			y += line;
			if (caps.get(i) != null)
			{
				y += line + 1;
				g.setColor(TsgHubTheme.WARNING);
				g.drawString(caps.get(i), PAD, y - 1);
			}
			y += 2;
			int barWidth = width - PAD * 2;
			g.setColor(TRACK);
			g.fillRect(PAD, y, barWidth, BAR);
			g.setColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.ACCENT);
			g.fillRect(PAD, y, t.startDef <= 0 ? 0 : barWidth * Math.max(0, t.def) / t.startDef, BAR);
			y += BAR + ROW_GAP;
		}
		return new Dimension(width, height);
	}
}
