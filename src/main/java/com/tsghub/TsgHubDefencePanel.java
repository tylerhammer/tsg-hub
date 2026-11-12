package com.tsghub;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import net.runelite.api.NPC;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

final class TsgHubDefencePanel extends OverlayPanel
{
	private static final Color BACKGROUND = new Color(0x1F, 0x1D, 0x22, 225);

	private final TsgHubDefence defence;
	private final TsgHubConfig config;

	TsgHubDefencePanel(Plugin plugin, TsgHubDefence defence, TsgHubConfig config)
	{
		super(plugin);
		this.defence = defence;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setBackgroundColor(BACKGROUND);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.defenceLabel() || config.defenceStyle() != TsgHubConfig.DefenceStyle.PANEL) return null;
		TsgHubDefenceTarget focus = defence.focused();
		boolean any = false;
		for (Map.Entry<NPC, TsgHubDefenceTarget> e : defence.targets().entrySet())
		{
			TsgHubDefenceTarget t = e.getValue();
			if (e.getKey().isDead()) continue;
			if (any) panelComponent.getChildren().add(LineComponent.builder().build());
			any = true;
			Color tone = t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.TEXT;
			panelComponent.getChildren().add(LineComponent.builder()
				.left(t.boss.name).leftColor(t == focus ? TsgHubTheme.ACCENT : TsgHubTheme.MUTED)
				.right(t.def + " / " + t.startDef).rightColor(tone)
				.build());
			String cap = t.atFloor() ? "Def capped" : TsgHubDefence.capLabel(t, defence.held());
			if (cap != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(cap).leftColor(t.atFloor() ? TsgHubTheme.CAPPED : TsgHubTheme.WARNING)
					.build());
			}
		}
		return any ? super.render(g) : null;
	}
}
