package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import com.tsghub.group.data.PartyPlayer;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

final class TsgHubPartiesView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private final WidthTrackingPanel groupsPage = new WidthTrackingPanel();
	final JScrollPane groupsScroll = scroll(groupsPage);
	private final JButton createGroupButton = primaryButton("New party");
	private final JLabel groupError = label("", TsgHubTheme.ERROR, smallFont());
	private JsonArray groupList;
	private boolean groupBusy;
	// Browsing the full list from inside a party.
	boolean browsingParties;

	TsgHubPartiesView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		shell.groupMembers.setKickHandler(() -> plugin.groups().isLeader(), this::confirmKick);
		createGroupButton.addActionListener(e -> submitCreateGroup());
		groupError.setVisible(false);
	}

	String partiesSummary()
	{
		TsgHubGroups groups = plugin.groups();
		if (groups.inGroup())
		{
			JsonObject current = groups.currentGroup();
			return current == null ? "You're in a party" : "You're in " + currentTitle(current);
		}
		if (groupList == null) return "Loading...";
		if (groupList.size() == 0) return "No parties yet";
		return groupList.size() == 1 ? "1 party" : groupList.size() + " parties";
	}

	void showParties()
	{
		groupError.setVisible(false);
		if (shell.pinned == View.GROUPS)
		{
			shell.dock.expand();
			plugin.groups().open();
			return;
		}
		shell.setView(View.GROUPS);
		renderGroups();
		plugin.groups().open();
	}

	void showGroupList()
	{
		groupBusy = false;
		if (shell.shows(View.GROUPS)) renderGroups();
	}

	void setGroups(JsonArray groups)
	{
		if (Objects.equals(groupList, groups)) return;
		groupList = groups;
		applyMemberOrder(plugin.groups().currentGroup());
		if (shell.shows(View.GROUPS)) renderGroups();
		if (shell.view == View.HOME) shell.homeView.renderHome();
	}

	void showGroup(JsonObject group)
	{
		groupBusy = false;
		browsingParties = false;
		applyMemberOrder(group);
		if (shell.pinned == View.GROUPS) shell.dock.expand();
		else if (shell.view != View.GROUPS) shell.setView(View.GROUPS);
		renderGroups();
	}

	void groupRefreshed(JsonObject group)
	{
		applyMemberOrder(group);
		if (shell.shows(View.GROUPS)) renderGroups();
		if (shell.view == View.HOME) shell.homeView.renderHome();
	}

	void groupActionFailed(String message)
	{
		groupBusy = false;
		showError(groupError, message, TsgHubSidebarPanel.TEXT_W);
		if (shell.shows(View.GROUPS)) renderGroups();
	}

	void groupMemberUpdated(PartyPlayer player, boolean self)
	{
		shell.groupMembers.update(player, self);
	}

	void groupMemberRemoved(PartyPlayer player)
	{
		shell.groupMembers.remove(player);
		partyAreasChanged();
	}

	void partyAreasChanged()
	{
		TsgHubGroups groups = plugin.groups();
		JsonObject current = groups.currentGroup();
		if (current == null || !str(current, "activity").isEmpty()) return;
		if (shell.shows(View.GROUPS) && !browsingParties) setPartyHeader(current);
		else if (shell.shows(View.GROUPS)) renderGroups();
		if (shell.view == View.HOME) shell.homeView.renderHome();
	}

	void groupSettingsChanged(boolean expandChanged)
	{
		shell.groupMembers.settingsChanged(expandChanged);
	}

	void groupSelfHidden()
	{
		shell.groupMembers.removeSelf();
	}

	void groupMembersCleared()
	{
		shell.groupMembers.clear();
	}

	private void confirmKick(String name)
	{
		if (!confirm(shell, "Remove member", "Remove " + name + " from the party?\nThey won't be able to rejoin it.", "Remove", true)) return;
		plugin.groups().kick(name);
	}

	private void applyMemberOrder(JsonObject group)
	{
		if (group == null) return;
		List<String> names = new ArrayList<>();
		for (JsonObject member : objects(array(group, "members"))) names.add(str(member, "displayName"));
		shell.groupMembers.setOrder(names);
	}

	void renderGroups()
	{
		TsgHubGroups groups = plugin.groups();
		JsonObject current = groups.currentGroup();
		groupsPage.removeAll();
		if (!groups.inGroup()) browsingParties = false;
		if (groups.inGroup() && !browsingParties) renderCurrentGroup(current);
		else renderGroupList(groups.inGroup() ? current : null);
		refresh(groupsPage);
	}

	private void setPartyHeader(JsonObject group)
	{
		int count = group == null ? 0 : array(group, "members").size();
		String members = count == 0 ? "" : count == 1 ? "Just you so far" : count + " members";
		if (group != null && bool(group, "locked")) members = members.isEmpty() ? "Locked" : members + " · Locked";
		shell.viewHeader(View.GROUPS, group == null ? "Your party" : currentTitle(group), members, members);
	}

	private void renderCurrentGroup(JsonObject group)
	{
		setPartyHeader(group);

		groupsPage.add(shell.groupMembers);
		groupsPage.add(gap(GAP_L));

		JButton title = button("Set title");
		title.setToolTipText("Name the party. Leave blank to title it by location.");
		title.setEnabled(group != null);
		title.addActionListener(e -> promptTitle(group));
		if (plugin.groups().isLeader())
		{
			boolean locked = bool(group, "locked");
			JButton lock = button(locked ? "Unlock party" : "Lock party");
			lock.setToolTipText(locked ? "Let clanmates join again" : "Stop anyone else from joining");
			lock.addActionListener(e -> {
				lock.setEnabled(false);
				plugin.groups().setLocked(!locked);
			});
			JPanel leaderActions = panel(new GridLayout(1, 2, 4, 0));
			leaderActions.add(title);
			leaderActions.add(lock);
			groupsPage.add(fitHeight(leaderActions));
		}
		else groupsPage.add(fitHeight(title));
		groupsPage.add(gap(GAP_S));

		String currentId = group == null ? "" : str(group, "id");
		int others = 0;
		if (groupList != null)
			for (int i = 0; i < groupList.size(); i++)
				if (!str(groupList.get(i).getAsJsonObject(), "id").equals(currentId)) others++;
		JPanel actions = panel(new GridLayout(1, 2, 4, 0));
		JButton browse = button(others == 0 ? "Other parties" : "Other parties (" + others + ")");
		browse.setToolTipText("See the clan's other parties and switch to one");
		browse.addActionListener(e -> {
			browsingParties = true;
			groupError.setVisible(false);
			renderGroups();
		});
		JButton leave = dangerButton("Leave party");
		leave.addActionListener(e -> {
			leave.setEnabled(false);
			plugin.groups().leave();
		});
		actions.add(browse);
		actions.add(leave);
		groupsPage.add(fitHeight(actions));
		groupsPage.add(groupError);
	}

	private void renderGroupList(JsonObject current)
	{
		shell.viewHeader(View.GROUPS, current != null ? "Other parties" : "Parties", "", partiesSummary());
		if (shell.pinned != View.GROUPS) shell.back.setToolTipText(current != null ? "Back to your party" : "Home");
		if (groupList == null)
		{
			groupsPage.add(caption("Loading parties..."));
		}
		else if (groupList.size() == 0)
		{
			groupsPage.add(shell.errorPanel("No parties yet", "Start one below. Clanmates can join it with one click, no code needed."));
		}
		else
		{
			String currentId = current == null ? "" : str(current, "id");
			List<JsonObject> sorted = objects(groupList);
			sorted.sort(Comparator.comparing((JsonObject g) -> !str(g, "id").equals(currentId)));
			for (JsonObject group : sorted)
			{
				addCard(groupsPage, groupCard(group, current));
			}
		}

		groupsPage.add(gap(GAP_L));
		createGroupButton.setEnabled(!groupBusy);
		createGroupButton.setText(groupBusy ? "Working..." : "New party");
		groupsPage.add(fitHeight(createGroupButton));
		groupsPage.add(groupError);
		groupsPage.add(gap(GAP_M));
		groupsPage.add(TsgHubSidebarPanel.hint(current != null ? "Joining or starting another party leaves your current one."
			: "Party members see each other's health, prayer, gear, inventory and skills while they're in the party."));
		if (plugin.groups().inOtherParty())
		{
			groupsPage.add(gap(GAP_S));
			groupsPage.add(wrapped("You're in a RuneLite party from outside TSG Hub. Joining or starting a party here leaves it.", WARNING, smallFont(), TsgHubSidebarPanel.TEXT_W));
		}
	}

	private JPanel groupCard(JsonObject group, JsonObject current)
	{
		JsonArray members = array(group, "members");
		String leader = str(group, "leaderName");
		int world = 0;
		boolean leaderListed = false;
		String self = plugin.clan().playerName();
		List<String> names = new ArrayList<>();
		for (JsonObject member : objects(members))
		{
			String name = str(member, "displayName");
			if (samePlayer(name, leader))
			{
				world = integer(member, "world", 0);
				leaderListed = true;
				names.add(0, samePlayer(name, self) ? selfName(escape(name)) : "<font color='" + toHexColor(TEXT) + "'>" + escape(name) + "</font>");
			}
			else names.add(nameHtml(name, self));
		}
		JPanel card = card();
		boolean mine = current != null && str(group, "id").equals(str(current, "id"));
		if (mine) highlightSelf(card);
		boolean locked = !mine && bool(group, "locked");
		String title = mine ? currentTitle(group) : TsgHubGroups.partyTitle(group);

		JLabel heading = shrinkable(label(title, locked ? MUTED : TEXT, boldFont()));
		heading.putClientProperty("html.disable", Boolean.TRUE);
		if (bool(group, "locked"))
		{
			heading.setIcon(icon("lock", MUTED));
			heading.setIconTextGap(5);
		}
		int myWorld = myPartyWorld();
		boolean sameWorld = !mine && world > 0 && world == myWorld;
		JPanel top = world > 0 ? row(heading, label("W" + world, sameWorld ? SUCCESS : MUTED, smallFont())) : row(heading, new JLabel());

		Map.Entry<String, Integer> area = TsgHubGroups.areaSummary(members);
		boolean showArea = area != null && !area.getKey().equalsIgnoreCase(title) && (area.getValue() >= 2 || members.size() == 1);
		String extra = showArea ? area.getKey() : leaderListed || leader.isEmpty() ? "" : "Led by " + leader;
		JLabel detail = shrinkable(showArea ? label(extra, SUCCESS, smallFont()) : caption(extra));
		detail.putClientProperty("html.disable", Boolean.TRUE);
		JLabel who = label(html(String.join(", ", names), TsgHubSidebarPanel.CARD_TEXT_W), MUTED, smallFont());
		JPanel text = extra.isEmpty() ? twoLines(top, who) : twoLines(top, detail);
		if (!extra.isEmpty())
		{
			text.add(gap(GAP_XS));
			text.add(who);
		}
		card.add(text, BorderLayout.CENTER);

		fullTextTooltip(card, null);
		if (locked) return fitHeight(card);
		clickable(card, () -> {
			if (mine)
			{
				browsingParties = false;
				renderGroups();
				return;
			}
			if (groupBusy) return;
			if (current != null && !confirm(shell, "Switch parties", "Leave " + currentTitle(current) + " and join " + title + "?", "Switch", false)) return;
			groupBusy = true;
			groupError.setVisible(false);
			renderGroups();
			plugin.groups().join(str(group, "id"));
		});
		return fitHeight(card);
	}

	private int myPartyWorld()
	{
		if (groupList == null) return 0;
		for (JsonObject group : objects(groupList))
			for (JsonObject member : objects(array(group, "members")))
				if (samePlayer(str(member, "displayName"), plugin.clan().playerName())) return integer(member, "world", 0);
		return 0;
	}

	private String currentTitle(JsonObject group)
	{
		if (!str(group, "activity").isEmpty()) return TsgHubGroups.partyTitle(group);
		Map.Entry<String, Integer> live = TsgHubGroups.areaSummary(plugin.groups().liveAreas());
		return live != null ? live.getKey() : TsgHubGroups.partyTitle(group);
	}

	private void promptTitle(JsonObject group)
	{
		Object input = JOptionPane.showInputDialog(shell, "Party title (leave blank to title it by location)", "Set title",
			JOptionPane.PLAIN_MESSAGE, null, null, str(group, "activity"));
		if (input == null) return;
		String title = input.toString().trim();
		if (title.length() > 40)
		{
			groupActionFailed("Keep the title to 40 characters.");
			return;
		}
		groupError.setVisible(false);
		plugin.groups().setTitle(title);
	}

	private void submitCreateGroup()
	{
		if (groupBusy) return;
		JsonObject current = plugin.groups().inGroup() ? plugin.groups().currentGroup() : null;
		if (current != null && !confirm(shell, "Start a new party", "Leave " + currentTitle(current) + " and start a new party?", "Start", false)) return;
		groupBusy = true;
		groupError.setVisible(false);
		renderGroups();
		plugin.groups().create();
	}
}
