/*
 * Adapted from Party Panel by TheStonedTurtle (https://github.com/TheStonedTurtle/party-panel),
 * BSD 2-Clause License. See THIRD_PARTY_NOTICES.md.
 */
package com.tsghub.group;

import com.google.common.base.Strings;
import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.ui.PlayerPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
	private List<String> order = Collections.emptyList();
	private List<String> preferred = Collections.emptyList();

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

	public void setOrder(List<String> names)
	{
		List<String> keys = preferred.stream().filter(k -> names.stream().anyMatch(n -> key(n).equals(k))).collect(Collectors.toList());
		names.stream().map(GroupMembersPanel::key).filter(k -> !keys.contains(k)).forEach(keys::add);
		if (keys.equals(order)) return;
		order = keys;
		rebuild();
	}

	public void update(PartyPlayer player, boolean bannerChanged, boolean self)
	{
		final long id = player.getMember().getMemberId();
		if (self && selfId != id)
		{
			players.remove(selfId);
			panels.remove(selfId);
			selfId = id;
		}
		players.put(id, player);
		final PlayerPanel existing = panels.get(id);
		if (existing != null)
		{
			existing.updatePlayerData(player, bannerChanged);
			return;
		}
		final PlayerPanel panel = new PlayerPanel(player, settings, spriteManager, itemManager);
		panel.updatePlayerData(player, true);
		addMenu(panel, id);
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
		order = Collections.emptyList();
		preferred = Collections.emptyList();
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
		Comparator<PartyPlayer> sort = order.isEmpty()
			? Comparator.comparing((PartyPlayer p) -> p.getMember().getMemberId() != selfId)
			: Comparator.comparingInt(this::position);
		final List<Long> ids = players.values().stream()
			.sorted(sort.thenComparing(p -> name(p).toLowerCase()))
			.map(p -> p.getMember().getMemberId())
			.collect(Collectors.toList());
		for (Long id : ids)
		{
			final PlayerPanel panel = panels.get(id);
			if (panel != null) add(panel);
		}
		if (panels.size() - (panels.containsKey(selfId) ? 1 : 0) == 0) add(empty);
		revalidate();
		repaint();
	}

	private int position(PartyPlayer player)
	{
		int index = Strings.isNullOrEmpty(player.getUsername()) ? -1 : order.indexOf(key(player.getUsername()));
		return index < 0 ? Integer.MAX_VALUE : index;
	}

	private void addMenu(PlayerPanel panel, long id)
	{
		MouseAdapter listener = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger()) showMenu(e, id);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger()) showMenu(e, id);
			}
		};
		panel.getBanner().addMouseListener(listener);
		for (Component c : panel.getBanner().getStatsPanel().getComponents()) c.addMouseListener(listener);
	}

	private void showMenu(MouseEvent e, long id)
	{
		PartyPlayer player = players.get(id);
		if (player == null) return;
		int index = Strings.isNullOrEmpty(player.getUsername()) ? -1 : order.indexOf(key(player.getUsername()));
		if (index < 0 || order.size() < 2) return;
		JPopupMenu menu = new JPopupMenu();
		JMenuItem up = new JMenuItem("Move up");
		up.setEnabled(index > 0);
		up.addActionListener(a -> moveMember(index, index - 1));
		JMenuItem down = new JMenuItem("Move down");
		down.setEnabled(index < order.size() - 1);
		down.addActionListener(a -> moveMember(index, index + 1));
		menu.add(up);
		menu.add(down);
		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	private void moveMember(int from, int to)
	{
		List<String> next = new ArrayList<>(order);
		Collections.swap(next, from, to);
		order = next;
		preferred = next;
		rebuild();
	}

	private static String name(PartyPlayer player)
	{
		return Strings.isNullOrEmpty(player.getUsername()) ? player.getMember().getDisplayName() : player.getUsername();
	}

	private static String key(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
