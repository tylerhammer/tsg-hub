/*
 * Adapted from Party Panel by TheStonedTurtle (https://github.com/TheStonedTurtle/party-panel),
 * BSD 2-Clause License. See THIRD_PARTY_NOTICES.md.
 */
package com.tsghub.group;

import com.google.common.base.Strings;
import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.ui.PlayerPanel;
import java.awt.Color;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

// Swing thread only.
public final class GroupMembersPanel extends JPanel
{
	private final Map<Long, PlayerPanel> panels = new HashMap<>();
	private final Map<Long, PartyPlayer> players = new HashMap<>();
	private final GroupViewSettings settings;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final JLabel empty = new JLabel("No one else has connected yet.");
	private long selfId = -1;

	public GroupMembersPanel(GroupViewSettings settings, SpriteManager spriteManager, ItemManager itemManager)
	{
		this.settings = settings;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		setLayout(new DynamicGridLayout(0, 1, 0, 5));
		setOpaque(false);
		empty.setForeground(new Color(0x8f8f8f));
		empty.setFont(FontManager.getRunescapeSmallFont());
		add(empty);
	}

	public void update(PartyPlayer player, boolean bannerChanged, boolean self)
	{
		final long id = player.getMember().getMemberId();
		if (self) selfId = id;
		players.put(id, player);
		final PlayerPanel existing = panels.get(id);
		if (existing != null)
		{
			existing.updatePlayerData(player, bannerChanged);
			return;
		}
		final PlayerPanel panel = new PlayerPanel(player, settings, spriteManager, itemManager);
		panel.updatePlayerData(player, true);
		panels.put(id, panel);
		rebuild();
	}

	public void remove(PartyPlayer player)
	{
		final long id = player.getMember().getMemberId();
		players.remove(id);
		if (panels.remove(id) != null) rebuild();
	}

	public void settingsChanged(boolean expandChanged)
	{
		for (PlayerPanel panel : panels.values())
		{
			if (expandChanged)
			{
				panel.setShowInfo(settings.autoExpandMembers());
				panel.getBanner().setExpandIcon(settings.autoExpandMembers());
				panel.updatePanel();
			}
			panel.updateDisplayVirtualLevels();
			panel.updateDisplayPlayerWorlds();
		}
		revalidate();
		repaint();
	}

	public void removeSelf()
	{
		players.remove(selfId);
		if (panels.remove(selfId) != null) rebuild();
		selfId = -1;
	}

	public void clear()
	{
		players.clear();
		panels.clear();
		selfId = -1;
		rebuild();
	}

	// Track live height so BoxLayout neither stretches nor clips us.
	@Override
	public java.awt.Dimension getMaximumSize()
	{
		return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	public int memberCount()
	{
		return panels.size();
	}

	private void rebuild()
	{
		removeAll();
		final List<Long> order = players.values().stream()
			.sorted(Comparator.comparing((PartyPlayer p) -> p.getMember().getMemberId() != selfId)
				.thenComparing(p -> (Strings.isNullOrEmpty(p.getUsername()) ? p.getMember().getDisplayName() : p.getUsername()).toLowerCase()))
			.map(p -> p.getMember().getMemberId())
			.collect(Collectors.toList());
		for (Long id : order)
		{
			final PlayerPanel panel = panels.get(id);
			if (panel != null) add(panel);
		}
		if (panels.size() - (panels.containsKey(selfId) ? 1 : 0) == 0) add(empty);
		revalidate();
		repaint();
	}
}
