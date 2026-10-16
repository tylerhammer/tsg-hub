/*
 * Adapted from Party Panel by TheStonedTurtle (https://github.com/TheStonedTurtle/party-panel),
 * BSD 2-Clause License. See THIRD_PARTY_NOTICES.md.
 */
package com.tsghub.group;

import com.tsghub.TsgHubTheme;
import com.google.common.base.Strings;
import com.tsghub.PlayerNames;
import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.ui.PlayerPanel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.DynamicGridLayout;

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
	private final Set<Long> offline = new HashSet<>();
	private BooleanSupplier canKick = () -> false;
	private Consumer<String> kick = name -> { };

	public GroupMembersPanel(GroupViewSettings settings, SpriteManager spriteManager, ItemManager itemManager)
	{
		this.settings = settings;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		setLayout(new DynamicGridLayout(0, 1, 0, TsgHubTheme.LIST_GAP));
		setOpaque(false);
		empty.setForeground(TsgHubTheme.MUTED);
		empty.setFont(TsgHubTheme.smallFont());
		add(empty);
	}

	public void setKickHandler(BooleanSupplier canKick, Consumer<String> kick)
	{
		this.canKick = canKick;
		this.kick = kick;
	}

	public void setOrder(List<String> names)
	{
		List<String> keys = preferred.stream().filter(k -> names.stream().anyMatch(n -> PlayerNames.normalize(n).equals(k))).collect(Collectors.toList());
		names.stream().map(PlayerNames::normalize).filter(k -> !keys.contains(k)).forEach(keys::add);
		if (keys.equals(order)) return;
		order = keys;
		rebuild();
	}

	public void update(PartyPlayer player, boolean self)
	{
		final long id = player.getMember().getMemberId();
		if (self && selfId != id)
		{
			players.remove(selfId);
			panels.remove(selfId);
			selfId = id;
		}
		players.put(id, player);
		final boolean moved = isOffline(player) ? offline.add(id) : offline.remove(id);
		final PlayerPanel existing = panels.get(id);
		if (existing != null)
		{
			existing.setSelf(id == selfId);
			existing.updatePlayerData(player);
			if (moved) rebuild();
			return;
		}
		final PlayerPanel panel = new PlayerPanel(player, settings, spriteManager, itemManager);
		panel.setSelf(self);
		panel.updatePlayerData(player);
		addMenu(panel, id);
		panels.put(id, panel);
		rebuild();
	}

	public void remove(PartyPlayer player)
	{
		final long id = player.getMember().getMemberId();
		players.remove(id);
		offline.remove(id);
		if (panels.remove(id) != null) rebuild();
	}

	public void settingsChanged(boolean expandChanged)
	{
		for (PlayerPanel panel : panels.values())
		{
			if (expandChanged)
			{
				panel.setShowInfo(settings.autoExpandMembers());
				panel.updatePanel();
				panel.updatePlayerData(panel.getPlayer());
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
		offline.remove(selfId);
		if (panels.remove(selfId) != null) rebuild();
		selfId = -1;
	}

	public void clear()
	{
		players.clear();
		panels.clear();
		offline.clear();
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

	private void rebuild()
	{
		removeAll();
		Comparator<PartyPlayer> sort = order.isEmpty()
			? Comparator.comparing((PartyPlayer p) -> p.getMember().getMemberId() != selfId)
			: Comparator.comparingInt(this::position);
		final List<Long> ids = players.values().stream()
			.filter(this::inRoster)
			.sorted(Comparator.comparing(GroupMembersPanel::isOffline).thenComparing(sort).thenComparing(p -> name(p).toLowerCase()))
			.map(p -> p.getMember().getMemberId())
			.collect(Collectors.toList());
		int others = 0;
		for (Long id : ids)
		{
			final PlayerPanel panel = panels.get(id);
			if (panel == null) continue;
			add(panel);
			if (id != selfId) others++;
		}
		if (others == 0) add(empty);
		revalidate();
		repaint();
	}

	private boolean inRoster(PartyPlayer player)
	{
		if (player.getMember().getMemberId() == selfId || order.isEmpty() || Strings.isNullOrEmpty(player.getUsername())) return true;
		return order.contains(PlayerNames.normalize(player.getUsername()));
	}

	private static boolean isOffline(PartyPlayer player)
	{
		return Strings.isNullOrEmpty(player.getUsername()) || player.getWorld() <= 0;
	}

	private int position(PartyPlayer player)
	{
		int index = Strings.isNullOrEmpty(player.getUsername()) ? -1 : order.indexOf(PlayerNames.normalize(player.getUsername()));
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
		panel.addBannerListener(listener);
	}

	private void showMenu(MouseEvent e, long id)
	{
		PartyPlayer player = players.get(id);
		if (player == null) return;
		String username = player.getUsername();
		int index = Strings.isNullOrEmpty(username) ? -1 : order.indexOf(PlayerNames.normalize(username));
		JPopupMenu menu = new JPopupMenu();
		if (index >= 0 && order.size() >= 2)
		{
			JMenuItem up = new JMenuItem("Move up");
			up.setEnabled(index > 0);
			up.addActionListener(a -> moveMember(index, index - 1));
			JMenuItem down = new JMenuItem("Move down");
			down.setEnabled(index < order.size() - 1);
			down.addActionListener(a -> moveMember(index, index + 1));
			menu.add(up);
			menu.add(down);
		}
		if (index >= 0 && id != selfId && canKick.getAsBoolean())
		{
			JMenuItem remove = new JMenuItem("Remove from party");
			remove.addActionListener(a -> kick.accept(username));
			if (menu.getComponentCount() > 0) menu.addSeparator();
			menu.add(remove);
		}
		if (menu.getComponentCount() == 0) return;
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
}
