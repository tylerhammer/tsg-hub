package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import com.tsghub.group.GroupMembersPanel;
import com.tsghub.group.data.PartyPlayer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

final class TsgHubSidebarPanel extends PluginPanel
{
	private static final int TEXT_W = 215;
	private static final int CARD_TEXT_W = 185;
	private static final int CARD_TITLE_W = 150;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

	private enum View { LOGGED_OUT, NOT_IN_CLAN, SHARING_OFF, EVENTS, PREVIEW, BOARD, COMPETITION_PREVIEW, COMPETITION, DROP_PARTY, GROUPS }

	private final TsgHubPlugin plugin;
	private final JButton back = TsgHubUi.iconButton(new TsgHubUi.BackIcon(), "Back to events");
	private final JLabel title = TsgHubUi.label("TSG Hub", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont());
	private final JLabel subtitle = TsgHubUi.label("", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont());
	private final TsgHubUi.RefreshIcon refreshIcon = new TsgHubUi.RefreshIcon();
	private final JButton refresh = TsgHubUi.iconButton(refreshIcon, "Refresh");
	private final JButton organizer = TsgHubUi.iconButton(new TsgHubUi.OrganizerIcon(), "Admin tools");
	private final TsgHubUi.StatusLine status = new TsgHubUi.StatusLine(TEXT_W);

	private final CardLayout centerLayout = new CardLayout();
	private final JPanel center = new JPanel(centerLayout);
	private final TsgHubUi.WidthTrackingPanel page = new TsgHubUi.WidthTrackingPanel();

	private final JPanel boardSummary = TsgHubUi.stack();
	private final TsgHubUi.WidthTrackingPanel tasksTab = new TsgHubUi.WidthTrackingPanel();
	private final TsgHubUi.WidthTrackingPanel scoreboardTab = new TsgHubUi.WidthTrackingPanel();
	private final TsgHubUi.WidthTrackingPanel teamTab = new TsgHubUi.WidthTrackingPanel();
	private final JCheckBox hideCompleted = new JCheckBox("Hide completed");
	private final JTextField manualNote = new JTextField();

	private final JTextField codeField = new JTextField();
	private final JButton joinButton = TsgHubUi.primaryButton("Join event");
	private final JLabel joinError = TsgHubUi.label("", TsgHubUi.ERROR, FontManager.getRunescapeSmallFont());

	private View view = View.LOGGED_OUT;
	private String plainTitle = "TSG Hub";
	private JsonArray events;
	private JsonObject previewEvent;
	private JsonObject boardEvent;
	private String boardDisplayName = "";
	private String openManualTaskId = "";
	private int busy;
	private volatile boolean active;
	private volatile boolean boardShowing;
	private JsonObject competitionEvent;
	private String competitionName = "";
	private volatile String openCompetitionId;
	private final JButton competitionJoinButton = TsgHubUi.primaryButton("Join");

	private final JButton eventsTab = TsgHubUi.button("Events");
	private final JButton groupsTab = TsgHubUi.button("Parties");
	private final JPanel tabRow = new JPanel(new GridLayout(1, 2, 4, 0));
	private final JPanel headerRow = new JPanel(new BorderLayout(4, 0));
	private final JPanel titleRow = new JPanel(new BorderLayout(4, 0));
	private final Color tabBackground = eventsTab.getBackground();
	private final TsgHubUi.WidthTrackingPanel groupsPage = new TsgHubUi.WidthTrackingPanel();
	private final GroupMembersPanel groupMembers;
	private static final String OTHER_ACTIVITY = "Other...";
	private static final String[] GROUP_ACTIVITIES = {
		"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut",
		"Nex", "The Nightmare", "Corporeal Beast", "God Wars Dungeon", "Royal Titans", "Yama", "The Hueycoatl", "Wilderness bosses",
		"Barbarian Assault", "Guardians of the Rift", "Tempoross", "Wintertodt", "Zalcano", "Soul Wars", "Castle Wars",
		OTHER_ACTIVITY
	};
	private final javax.swing.JComboBox<String> activityPicker = new javax.swing.JComboBox<>(GROUP_ACTIVITIES);
	private final JTextField otherActivityField = new JTextField();
	private final JButton createGroupButton = TsgHubUi.primaryButton("Start party");
	private final JLabel groupError = TsgHubUi.label("", TsgHubUi.ERROR, FontManager.getRunescapeSmallFont());
	private JsonArray groupList;
	private boolean groupBusy;
	// Browsing the full list from inside a party.
	private boolean browsingParties;
	// Events tab view to restore when switching back.
	private View eventsView = View.EVENTS;
	private String eventsTitle = "", eventsSubtitle = "";
	private boolean eventsBack, eventsRefresh;

	TsgHubSidebarPanel(TsgHubPlugin plugin, GroupMembersPanel groupMembers)
	{
		super(false);
		this.plugin = plugin;
		this.groupMembers = groupMembers;
		setLayout(new BorderLayout(0, 6));
		setBackground(TsgHubUi.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);

		center.setOpaque(false);
		center.add(TsgHubUi.scroll(page), "page");
		center.add(buildBoard(), "board");
		center.add(TsgHubUi.scroll(groupsPage), "groups");
		add(center, BorderLayout.CENTER);

		codeField.setToolTipText("Team code from an admin");
		codeField.putClientProperty("JTextField.placeholderText", "Team code, e.g. DRGN42");
		manualNote.putClientProperty("JTextField.placeholderText", "Screenshot link or note");
		codeField.addActionListener(e -> submitJoin());
		joinButton.addActionListener(e -> submitJoin());
		joinError.setVisible(false);
		activityPicker.setFocusable(false);
		activityPicker.setToolTipText("What the party is for");
		activityPicker.addActionListener(e -> {
			boolean other = OTHER_ACTIVITY.equals(activityPicker.getSelectedItem());
			otherActivityField.setVisible(other);
			if (other) SwingUtilities.invokeLater(otherActivityField::requestFocusInWindow);
			groupsPage.revalidate();
		});
		otherActivityField.putClientProperty("JTextField.placeholderText", "Activity, e.g. Clue scrolls");
		otherActivityField.setVisible(false);
		otherActivityField.addActionListener(e -> submitCreateGroup());
		createGroupButton.addActionListener(e -> submitCreateGroup());
		groupError.setVisible(false);

		hideCompleted.setOpaque(false);
		hideCompleted.setForeground(TsgHubUi.MUTED);
		hideCompleted.setFont(FontManager.getRunescapeSmallFont());
		hideCompleted.setFocusPainted(false);
		hideCompleted.setSelected("true".equals(TsgHubSession.get("hideCompleted")));
		hideCompleted.addActionListener(e -> {
			TsgHubSession.set("hideCompleted", hideCompleted.isSelected() ? "true" : "");
			renderTasks();
		});

		showLoggedOut();
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		titleRow.setOpaque(false);
		titleRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		back.addActionListener(e -> {
			if (view == View.GROUPS)
			{
				browsingParties = false;
				renderGroups();
				return;
			}
			showEventList();
			plugin.loadClanEvents();
		});
		titleRow.add(back, BorderLayout.WEST);

		JPanel titles = TsgHubUi.stack();
		titles.add(title);
		titles.add(subtitle);
		titleRow.add(titles, BorderLayout.CENTER);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		actions.setOpaque(false);
		organizer.addActionListener(e -> plugin.openWorkspace());
		organizer.setVisible(false);
		refresh.addActionListener(e -> {
			if (view == View.GROUPS) plugin.groups().refresh();
			else if (view == View.BOARD) plugin.refreshBoard();
			else if (view == View.COMPETITION && competitionEvent != null) plugin.openCompetition(TsgHubUi.str(competitionEvent, "id"));
			else plugin.loadClanEvents();
		});
		JPanel refreshSlot = new JPanel(new BorderLayout());
		refreshSlot.setOpaque(false);
		refreshSlot.setPreferredSize(refresh.getPreferredSize());
		refreshSlot.add(refresh);
		actions.add(organizer);
		actions.add(refreshSlot);

		tabRow.setOpaque(false);
		eventsTab.addActionListener(e -> showEventsTab());
		groupsTab.addActionListener(e -> showGroupsTab());
		eventsTab.setToolTipText("Clan events and your team's board");
		groupsTab.setToolTipText("Join a clanmate's party or start one");
		tabRow.add(eventsTab);
		tabRow.add(groupsTab);
		tabRow.setVisible(false);
		headerRow.add(tabRow, BorderLayout.CENTER);
		headerRow.add(actions, BorderLayout.EAST);

		JPanel below = TsgHubUi.stack();
		below.add(titleRow);
		below.add(status);

		header.setOpaque(false);
		header.add(headerRow, BorderLayout.NORTH);
		header.add(below, BorderLayout.CENTER);
		header.setBorder(BorderFactory.createCompoundBorder(TsgHubUi.bottomRule(), BorderFactory.createEmptyBorder(0, 0, 6, 0)));
		return header;
	}

	private JPanel buildBoard()
	{
		JPanel board = new JPanel(new BorderLayout(0, 6));
		board.setOpaque(false);
		JPanel display = new JPanel(new BorderLayout());
		display.setOpaque(false);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new java.awt.GridLayout(1, 3, 4, 0));
		tabs.setOpaque(false);
		MaterialTab tasks = new MaterialTab("Tasks", tabs, TsgHubUi.scroll(tasksTab));
		tabs.addTab(tasks);
		tabs.addTab(new MaterialTab("Scores", tabs, TsgHubUi.scroll(scoreboardTab)));
		tabs.addTab(new MaterialTab("Team", tabs, TsgHubUi.scroll(teamTab)));
		tabs.select(tasks);

		JPanel north = TsgHubUi.stack();
		north.add(boardSummary);
		north.add(Box.createVerticalStrut(6));
		north.add(TsgHubUi.fitHeight(tabs));
		board.add(north, BorderLayout.NORTH);
		board.add(display, BorderLayout.CENTER);
		return board;
	}

	void setOrganizerAccess(boolean allowed)
	{
		organizer.setVisible(allowed);
		layoutHeader();
	}

	void setStatus(String message, TsgHubUi.Tone tone)
	{
		status.show(message, tone);
	}

	void setBusy(boolean isBusy)
	{
		busy = Math.max(0, busy + (isBusy ? 1 : -1));
		refreshIcon.setSpinning(busy > 0, refresh);
	}

	boolean wantsAutoRefresh()
	{
		return active && boardShowing;
	}

	@Override
	public void onActivate()
	{
		active = true;
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	void showLoggedOut()
	{
		setView(View.LOGGED_OUT);
		setHeader("TSG Hub", "", false, false);
		page.removeAll();
		page.add(errorPanel("Not logged in", "Log in to see your clan's events and your team's progress."));
		refreshPage();
	}

	void showNotInClan(String hubClan, String detectedClan)
	{
		setView(View.NOT_IN_CLAN);
		setHeader("TSG Hub", "", false, false);
		page.removeAll();
		String body = detectedClan == null || detectedClan.isEmpty()
			? "TSG Hub is for members of the " + hubClan + " clan. Join the clan in game to see its events."
			: "TSG Hub is for members of the " + hubClan + " clan. This character is in " + detectedClan + ".";
		page.add(errorPanel("For " + hubClan + " members", body));
		refreshPage();
	}

	void showSharingOff()
	{
		setView(View.SHARING_OFF);
		setHeader("TSG Hub", "", false, false);
		page.removeAll();
		page.add(TsgHubUi.label("Share your progress", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
		page.add(Box.createVerticalStrut(6));
		page.add(TsgHubUi.wrapped("TSG Hub tracks your boss kills, drops and raids for clan events. "
			+ "To do that it sends your RuneScape name, clan and rank, and that progress to your clan's event service.",
			TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
		page.add(Box.createVerticalStrut(4));
		page.add(TsgHubUi.wrapped("Nothing is ever posted in game chat.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
		page.add(Box.createVerticalStrut(10));
		JButton enable = TsgHubUi.primaryButton("Enable sharing");
		enable.addActionListener(e -> plugin.enableSharing());
		page.add(TsgHubUi.fitHeight(fullWidth(enable)));
		page.add(Box.createVerticalStrut(6));
		page.add(TsgHubUi.wrapped("You can turn this off anytime in the TSG Hub plugin settings.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
		refreshPage();
	}

	void setEvents(JsonArray events)
	{
		this.events = events;
		if (view == View.EVENTS) renderEvents();
	}

	void showEventList()
	{
		setView(View.EVENTS);
		renderEvents();
	}

	void showBoard(JsonObject event, String displayName, boolean open)
	{
		// Background refreshes must not pull the user off another screen.
		if (!open && view != View.BOARD) return;
		boolean sameEvent = boardEvent != null && TsgHubUi.str(boardEvent, "id").equals(TsgHubUi.str(event, "id"));
		if (!sameEvent) openManualTaskId = "";
		boardEvent = event;
		boardDisplayName = displayName == null ? "" : displayName;
		setView(View.BOARD);
		refresh.setToolTipText("Refresh (updated " + LocalTime.now().format(TIME) + ")");
		renderBoard();
	}

	String openCompetitionId()
	{
		return active ? openCompetitionId : null;
	}

	private String unit()
	{
		return "skill".equals(TsgHubUi.str(competitionEvent, "type")) ? "XP" : "kills";
	}

	private void showCompetitionPreview(JsonObject event)
	{
		competitionEvent = event;
		setView(View.COMPETITION_PREVIEW);
		setHeader(TsgHubUi.str(event, "name"), TsgHubUi.statusLabel(TsgHubUi.str(event, "status")) + " · " + TsgHubUi.dateRange(event), true, false);
		page.removeAll();
		Font small = FontManager.getRunescapeSmallFont();
		JsonObject config = TsgHubUi.eventConfig(event);
		boolean skill = "skill".equals(TsgHubUi.str(event, "type"));
		String what = skill ? "Gain the most " + TsgHubUi.skillName(TsgHubUi.str(config, "skill")) + " XP" : "Get the most " + TsgHubUi.str(config, "npcName") + " kills";
		page.add(TsgHubUi.label(TsgHubUi.eventTypeLabel(event), TsgHubUi.ACCENT, FontManager.getRunescapeBoldFont()));
		page.add(Box.createVerticalStrut(4));
		page.add(TsgHubUi.wrapped(what + " from " + TsgHubUi.dateRange(event) + ".", TsgHubUi.TEXT, FontManager.getRunescapeFont(), TEXT_W));
		String description = TsgHubUi.str(event, "description").trim();
		if (!description.isEmpty()) { page.add(Box.createVerticalStrut(4)); page.add(TsgHubUi.wrapped(description, TsgHubUi.MUTED, small, TEXT_W)); }
		page.add(Box.createVerticalStrut(10));
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		for (java.awt.event.ActionListener l : competitionJoinButton.getActionListeners()) competitionJoinButton.removeActionListener(l);
		competitionJoinButton.addActionListener(e -> {
			competitionJoinButton.setEnabled(false);
			competitionJoinButton.setText("Joining...");
			joinError.setVisible(false);
			plugin.participate(TsgHubUi.str(event, "id"));
		});
		page.add(TsgHubUi.fitHeight(fullWidth(competitionJoinButton)));
		joinError.setVisible(false);
		page.add(joinError);
		page.add(Box.createVerticalStrut(6));
		page.add(TsgHubUi.wrapped(skill
			? "Your XP counts from the first time TSG Hub sees you after joining. Gains on other devices count the next time you log in with it."
			: !"loot".equals(TsgHubUi.str(config, "signal"))
				? "Kills count from your kill count message, so kills while TSG Hub isn't running still count at your next kill."
				: "Each kill counts when its loot drops while TSG Hub is running.", TsgHubUi.MUTED, small, TEXT_W));
		refreshPage();
	}

	void competitionJoinFailed(String message)
	{
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		joinError.setText(TsgHubUi.html(TsgHubUi.escape(message), TEXT_W));
		joinError.setVisible(true);
	}

	void showCompetition(JsonObject event, String displayName, boolean open)
	{
		if (!open && view != View.COMPETITION) return;
		competitionEvent = event;
		competitionName = displayName == null ? "" : displayName;
		setView(View.COMPETITION);
		openCompetitionId = TsgHubUi.str(event, "id");
		refresh.setToolTipText("Refresh (updated " + LocalTime.now().format(TIME) + ")");
		setHeader(TsgHubUi.str(event, "name"), TsgHubUi.statusLabel(TsgHubUi.str(event, "status")) + " · " + TsgHubUi.dateRange(event), true, true);
		page.removeAll();
		Font small = FontManager.getRunescapeSmallFont();
		JsonObject config = TsgHubUi.eventConfig(event);
		boolean skill = "skill".equals(TsgHubUi.str(event, "type"));
		boolean hidden = TsgHubUi.bool(event, "hideScores");
		JsonArray rows = TsgHubUi.array(event, "leaderboard");
		JsonObject mine = null;
		for (int i = 0; i < rows.size(); i++)
			if (TsgHubUi.str(rows.get(i).getAsJsonObject(), "displayName").equalsIgnoreCase(competitionName)) mine = rows.get(i).getAsJsonObject();

		JPanel summary = TsgHubUi.card();
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(skill ? TsgHubUi.skillName(TsgHubUi.str(config, "skill")) : TsgHubUi.str(config, "npcName")) + "</b>", CARD_TITLE_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
		if (mine != null && mine.has("rank") && !hidden)
			top.add(TsgHubUi.label("#" + TsgHubUi.integer(mine, "rank", 0) + " of " + TsgHubUi.integer(event, "participants", rows.size()), TsgHubUi.integer(mine, "rank", 0) == 1 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, FontManager.getRunescapeBoldFont()), BorderLayout.EAST);
		summary.add(top, BorderLayout.NORTH);
		JPanel lines = TsgHubUi.stack();
		boolean tracking = mine != null && TsgHubUi.bool(mine, "tracking");
		lines.add(TsgHubUi.label("You: +" + String.format("%,d", mine == null ? 0 : TsgHubUi.integer(mine, "gained", 0)) + " " + unit(), TsgHubUi.SUCCESS, FontManager.getRunescapeBoldFont()));
		if (!tracking)
			lines.add(TsgHubUi.wrapped(skill ? "Starts counting the next time you gain XP." : "Starts counting at your next kill.", TsgHubUi.MUTED, small, CARD_TEXT_W));
		summary.add(lines, BorderLayout.CENTER);
		page.add(TsgHubUi.fitHeight(summary));
		page.add(Box.createVerticalStrut(8));

		if (hidden)
		{
			page.add(errorPanel("Scores are hidden", "The admins are keeping the leaderboard secret for now."));
		}
		else
		{
			page.add(TsgHubUi.label(TsgHubUi.integer(event, "participants", rows.size()) + " taking part", TsgHubUi.MUTED, small));
			page.add(Box.createVerticalStrut(4));
			for (int i = 0; i < rows.size(); i++)
			{
				JsonObject row = rows.get(i).getAsJsonObject();
				boolean me = TsgHubUi.str(row, "displayName").equalsIgnoreCase(competitionName);
				int rank = TsgHubUi.integer(row, "rank", i + 1);
				JPanel card = TsgHubUi.card();
				card.setLayout(new BorderLayout(8, 0));
				if (me) card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, TsgHubUi.ACCENT), BorderFactory.createEmptyBorder(7, 5, 7, 8)));
				JLabel rankLabel = TsgHubUi.label(String.valueOf(rank), rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, FontManager.getRunescapeBoldFont());
				rankLabel.setPreferredSize(new Dimension(18, rankLabel.getPreferredSize().height));
				card.add(rankLabel, BorderLayout.WEST);
				card.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(TsgHubUi.str(row, "displayName")) + (me ? " <font color='#8f8f8f'>(you)</font>" : ""), 110), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
				String gained = TsgHubUi.bool(row, "tracking") ? String.format("%,d", TsgHubUi.integer(row, "gained", 0)) : "-";
				card.add(TsgHubUi.label(gained, rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), BorderLayout.EAST);
				page.add(TsgHubUi.fitHeight(card));
				page.add(Box.createVerticalStrut(4));
			}
		}
		page.add(Box.createVerticalStrut(14));
		JButton leave = TsgHubUi.button("Leave competition");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> {
			int choice = JOptionPane.showConfirmDialog(this, "Leave \"" + TsgHubUi.str(event, "name") + "\"?\nYou'll drop off the leaderboard on this device. You can rejoin while it's running.",
				"Leave competition", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.OK_OPTION) plugin.leaveEvent(TsgHubUi.str(event, "id"));
		});
		page.add(TsgHubUi.fitHeight(fullWidth(leave)));
		refreshPage();
	}

	private void showDropParty(JsonObject event)
	{
		setView(View.DROP_PARTY);
		setHeader(TsgHubUi.str(event, "name"), "Custom", true, false);
		page.removeAll();
		JsonObject config = TsgHubUi.eventConfig(event);
		Font small = FontManager.getRunescapeSmallFont();
		JPanel card = TsgHubUi.card();
		JPanel lines = TsgHubUi.stack();
		String countdown = TsgHubUi.dropPartyCountdown(config);
		if (!countdown.isEmpty()) lines.add(TsgHubUi.label(countdown, "Finished".equals(countdown) ? TsgHubUi.MUTED : TsgHubUi.SUCCESS, FontManager.getRunescapeBoldFont()));
		lines.add(Box.createVerticalStrut(4));
		lines.add(detail("When", TsgHubUi.dropPartyTime(config, true) + " (your time)"));
		if (TsgHubUi.integer(config, "world", 0) > 0) lines.add(detail("World", String.valueOf(TsgHubUi.integer(config, "world", 0))));
		if (!TsgHubUi.str(config, "location").isEmpty()) lines.add(detail("Where", TsgHubUi.str(config, "location")));
		if (!TsgHubUi.str(config, "host").isEmpty()) lines.add(detail("Host", TsgHubUi.str(config, "host")));
		card.add(lines, BorderLayout.CENTER);
		page.add(TsgHubUi.fitHeight(card));
		String notes = TsgHubUi.str(config, "notes");
		if (!notes.isEmpty())
		{
			page.add(Box.createVerticalStrut(8));
			page.add(TsgHubUi.wrapped(notes, TsgHubUi.MUTED, small, TEXT_W));
		}
		refreshPage();
	}

	private JLabel detail(String label, String value)
	{
		JLabel line = TsgHubUi.label(TsgHubUi.html("<font color='#8f8f8f'>" + label + ":</font> " + TsgHubUi.escape(value), CARD_TEXT_W), TsgHubUi.TEXT, FontManager.getRunescapeFont());
		line.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return line;
	}

	void joinFailed(String message)
	{
		joinButton.setEnabled(true);
		joinButton.setText("Join event");
		joinError.setText(TsgHubUi.html(TsgHubUi.escape(message), TEXT_W));
		joinError.setVisible(true);
		codeField.requestFocusInWindow();
		codeField.selectAll();
	}

	void manualSubmitFinished(boolean success)
	{
		if (success)
		{
			openManualTaskId = "";
			manualNote.setText("");
		}
		renderTasks();
	}

	private void showEventsTab()
	{
		if (view != View.GROUPS) return;
		setView(eventsView);
		if (eventsView == View.EVENTS)
		{
			renderEvents();
			plugin.loadClanEvents();
		}
		else setHeader(eventsTitle, eventsSubtitle, eventsBack, eventsRefresh);
	}

	private void showGroupsTab()
	{
		if (view == View.GROUPS) return;
		eventsView = view;
		eventsTitle = plainTitle;
		eventsSubtitle = subtitle.getText();
		eventsBack = back.isVisible();
		eventsRefresh = refresh.isVisible();
		groupError.setVisible(false);
		setView(View.GROUPS);
		renderGroups();
		plugin.groups().refresh();
	}

	void showGroupList()
	{
		groupBusy = false;
		if (view == View.GROUPS) renderGroups();
	}

	void setGroups(JsonArray groups)
	{
		groupList = groups;
		if (view == View.GROUPS) renderGroups();
	}

	void showGroup(JsonObject group)
	{
		groupBusy = false;
		browsingParties = false;
		otherActivityField.setText("");
		groupMembers.clear();
		if (view != View.GROUPS)
		{
			eventsView = view == View.LOGGED_OUT || view == View.NOT_IN_CLAN || view == View.SHARING_OFF ? View.EVENTS : view;
			eventsTitle = plainTitle;
			eventsSubtitle = subtitle.getText();
			eventsBack = back.isVisible();
			eventsRefresh = refresh.isVisible();
			setView(View.GROUPS);
		}
		renderGroups();
	}

	void groupRefreshed(JsonObject group)
	{
		if (view == View.GROUPS) renderGroups();
	}

	void groupActionFailed(String message)
	{
		groupBusy = false;
		groupError.setText(TsgHubUi.html(TsgHubUi.escape(message), TEXT_W));
		groupError.setVisible(true);
		if (view == View.GROUPS) renderGroups();
	}

	void groupMemberUpdated(PartyPlayer player, boolean bannerChanged, boolean self)
	{
		groupMembers.update(player, bannerChanged, self);
	}

	void groupMemberRemoved(PartyPlayer player)
	{
		groupMembers.remove(player);
	}

	void groupSettingsChanged(boolean expandChanged)
	{
		groupMembers.settingsChanged(expandChanged);
	}

	void groupSelfHidden()
	{
		groupMembers.removeSelf();
	}

	void groupMembersCleared()
	{
		groupMembers.clear();
	}

	private void renderGroups()
	{
		TsgHubGroups groups = plugin.groups();
		JsonObject current = groups.currentGroup();
		groupsPage.removeAll();
		if (!groups.inGroup()) browsingParties = false;
		if (groups.inGroup() && !browsingParties) renderCurrentGroup(current);
		else renderGroupList(groups.inGroup() ? current : null);
		groupsPage.revalidate();
		groupsPage.repaint();
	}

	private void renderCurrentGroup(JsonObject group)
	{
		Font small = FontManager.getRunescapeSmallFont();
		int count = group == null ? 0 : TsgHubUi.array(group, "members").size();
		setHeader(group == null ? "Your party" : TsgHubUi.str(group, "activity"),
			count == 0 ? "" : count == 1 ? "Just you so far" : count + " members", false, true);

		groupsPage.add(groupMembers);
		groupsPage.add(Box.createVerticalStrut(10));

		String currentId = group == null ? "" : TsgHubUi.str(group, "id");
		int others = 0;
		if (groupList != null)
			for (int i = 0; i < groupList.size(); i++)
				if (!TsgHubUi.str(groupList.get(i).getAsJsonObject(), "id").equals(currentId)) others++;
		JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
		actions.setOpaque(false);
		JButton browse = TsgHubUi.button(others == 0 ? "Other parties" : "Other parties (" + others + ")");
		browse.setToolTipText("See the clan's other parties and switch to one");
		browse.addActionListener(e -> {
			browsingParties = true;
			groupError.setVisible(false);
			renderGroups();
		});
		JButton leave = TsgHubUi.button("Leave party");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> {
			leave.setEnabled(false);
			plugin.groups().leave();
		});
		actions.add(browse);
		actions.add(leave);
		groupsPage.add(TsgHubUi.fitHeight(fullWidth(actions)));
		groupsPage.add(groupError);
	}

	private void renderGroupList(JsonObject current)
	{
		Font small = FontManager.getRunescapeSmallFont();
		setHeader(current != null ? "Other parties" : "", "", current != null, true);
		back.setToolTipText(current != null ? "Back to your party" : "Back to events");
		if (groupList == null)
		{
			groupsPage.add(TsgHubUi.label("Loading parties...", TsgHubUi.MUTED, small));
		}
		else if (groupList.size() == 0)
		{
			groupsPage.add(errorPanel("No parties yet", "Start one below. Clanmates can join it with one click, no code needed."));
		}
		else
		{
			for (int i = 0; i < groupList.size(); i++)
			{
				groupsPage.add(groupCard(groupList.get(i).getAsJsonObject(), current));
				groupsPage.add(Box.createVerticalStrut(5));
			}
		}

		groupsPage.add(Box.createVerticalStrut(10));
		groupsPage.add(TsgHubUi.label(current != null ? "Start a new party" : "Start a party", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
		groupsPage.add(Box.createVerticalStrut(4));
		groupsPage.add(TsgHubUi.fitHeight(activityPicker));
		groupsPage.add(Box.createVerticalStrut(4));
		groupsPage.add(TsgHubUi.fitHeight(otherActivityField));
		groupsPage.add(Box.createVerticalStrut(6));
		createGroupButton.setEnabled(!groupBusy);
		createGroupButton.setText(groupBusy ? "Working..." : "Start party");
		groupsPage.add(TsgHubUi.fitHeight(fullWidth(createGroupButton)));
		groupsPage.add(groupError);
		groupsPage.add(Box.createVerticalStrut(8));
		if (current != null) groupsPage.add(TsgHubUi.wrapped("Joining or starting another party leaves your current one.", TsgHubUi.MUTED, small, TEXT_W));
		else groupsPage.add(TsgHubUi.wrapped("Party members see each other's health, prayer, gear, inventory and skills while they're in the party.",
			TsgHubUi.MUTED, small, TEXT_W));
		if (plugin.groups().inOtherParty())
		{
			groupsPage.add(Box.createVerticalStrut(4));
			groupsPage.add(TsgHubUi.wrapped("You're in a RuneLite party from outside TSG Hub. Joining or starting a party here leaves it.", TsgHubUi.WARNING, small, TEXT_W));
		}
	}

	private JPanel groupCard(JsonObject group, JsonObject current)
	{
		JsonArray members = TsgHubUi.array(group, "members");
		String leader = TsgHubUi.str(group, "leaderName");
		int world = 0;
		List<String> names = new ArrayList<>();
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
			names.add(TsgHubUi.str(member, "displayName"));
			if (TsgHubUi.samePlayer(TsgHubUi.str(member, "displayName"), leader)) world = TsgHubUi.integer(member, "world", 0);
		}
		JPanel card = TsgHubUi.card();
		JPanel text = TsgHubUi.stack();
		text.add(TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(TsgHubUi.str(group, "activity")) + "</b>", CARD_TITLE_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()));
		text.add(Box.createVerticalStrut(2));
		String meta = (members.size() == 1 ? "1 member" : members.size() + " members") + (world > 0 ? " · W" + world : "") + " · " + leader;
		text.add(TsgHubUi.label(meta, TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
		text.add(TsgHubUi.wrapped(String.join(", ", names), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), CARD_TITLE_W));
		card.add(text, BorderLayout.CENTER);
		JPanel east = new JPanel(new BorderLayout());
		east.setOpaque(false);
		boolean mine = current != null && TsgHubUi.str(group, "id").equals(TsgHubUi.str(current, "id"));
		east.add(mine ? TsgHubUi.badge("Yours", TsgHubUi.SUCCESS)
			: TsgHubUi.label(groupBusy ? "..." : current != null ? "Switch" : "Join", TsgHubUi.ACCENT, FontManager.getRunescapeSmallFont()), BorderLayout.NORTH);
		card.add(east, BorderLayout.EAST);
		card.setToolTipText(mine ? "Back to your party" : (current != null ? "Switch to " : "Join ") + leader + "'s " + TsgHubUi.str(group, "activity") + " party");
		TsgHubUi.clickable(card, () -> {
			if (mine)
			{
				browsingParties = false;
				renderGroups();
				return;
			}
			if (groupBusy) return;
			if (current != null && JOptionPane.showConfirmDialog(this,
				"Leave your " + TsgHubUi.str(current, "activity") + " party and join " + leader + "'s " + TsgHubUi.str(group, "activity") + " party?",
				"Switch parties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
			groupBusy = true;
			groupError.setVisible(false);
			renderGroups();
			plugin.groups().join(TsgHubUi.str(group, "id"));
		});
		return TsgHubUi.fitHeight(card);
	}

	private void submitCreateGroup()
	{
		if (groupBusy) return;
		String activity = OTHER_ACTIVITY.equals(activityPicker.getSelectedItem()) ? otherActivityField.getText().trim() : String.valueOf(activityPicker.getSelectedItem());
		if (activity.isEmpty())
		{
			groupActionFailed("Type the activity for your party.");
			otherActivityField.requestFocusInWindow();
			return;
		}
		if (activity.length() > 40)
		{
			groupActionFailed("Keep the activity to 40 characters.");
			return;
		}
		JsonObject current = plugin.groups().inGroup() ? plugin.groups().currentGroup() : null;
		if (current != null && JOptionPane.showConfirmDialog(this,
			"Leave your " + TsgHubUi.str(current, "activity") + " party and start a " + activity + " party?",
			"Start a new party", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
		groupBusy = true;
		groupError.setVisible(false);
		renderGroups();
		plugin.groups().create(activity);
	}

	private void setView(View next)
	{
		view = next;
		boardShowing = next == View.BOARD;
		if (next != View.COMPETITION) openCompetitionId = null;
		centerLayout.show(center, next == View.BOARD ? "board" : next == View.GROUPS ? "groups" : "page");
		if (next != View.GROUPS) back.setToolTipText("Back to events");
		layoutHeader();
		styleTab(groupsTab, next == View.GROUPS);
		styleTab(eventsTab, next != View.GROUPS);
	}

	private void styleTab(JButton tab, boolean selected)
	{
		tab.setBackground(selected ? TsgHubUi.ACCENT.darker() : tabBackground);
		tab.setForeground(selected ? Color.WHITE : TsgHubUi.MUTED);
	}

	private void setHeader(String titleText, String subtitleText, boolean showBack, boolean showRefresh)
	{
		plainTitle = titleText;
		title.setText(TsgHubUi.html(TsgHubUi.escape(titleText), 150));
		subtitle.setText(subtitleText);
		subtitle.setVisible(!subtitleText.isEmpty());
		back.setVisible(showBack);
		refresh.setVisible(showRefresh);
		layoutHeader();
	}

	private void layoutHeader()
	{
		boolean member = view != View.LOGGED_OUT && view != View.NOT_IN_CLAN && view != View.SHARING_OFF;
		tabRow.setVisible(member);
		headerRow.setVisible(member || organizer.isVisible());
		titleRow.setVisible(!plainTitle.isEmpty());
		titleRow.setBorder(headerRow.isVisible() ? BorderFactory.createEmptyBorder(6, 0, 0, 0) : null);
	}

	private void renderEvents()
	{
		setHeader("", "", false, true);
		page.removeAll();
		String clan = plugin.getDetectedClanName();
		if (clan.isEmpty())
		{
			page.add(errorPanel("No clan detected", "Join a clan in game to see its events."));
		}
		else if (events == null)
		{
			page.add(TsgHubUi.label("Loading events...", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
		}
		else
		{
			List<JsonObject> visible = new ArrayList<>();
			for (int i = 0; i < events.size(); i++)
			{
				JsonObject event = events.get(i).getAsJsonObject();
				if (!"ended".equals(TsgHubUi.str(event, "status"))) visible.add(event);
			}
			visible.sort(Comparator
				.comparing((JsonObject e) -> !TsgHubUi.bool(e, "joined"))
				.thenComparing(e -> !"active".equals(TsgHubUi.str(e, "status")))
				.thenComparing(e -> TsgHubUi.str(e, "startDate")));
			if (visible.isEmpty())
			{
				page.add(errorPanel("No events yet", "When an admin creates an event, it will show up here."));
			}
			for (JsonObject event : visible)
			{
				page.add(eventCard(event));
				page.add(Box.createVerticalStrut(5));
			}
		}
		refreshPage();
	}

	private JPanel eventCard(JsonObject event)
	{
		String state = TsgHubUi.str(event, "status");
		boolean joined = TsgHubUi.bool(event, "joined");
		JPanel card = TsgHubUi.card();
		JPanel text = TsgHubUi.stack();
		text.add(TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(TsgHubUi.str(event, "name")) + "</b>", CARD_TITLE_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()));
		JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		meta.setOpaque(false);
		meta.add(TsgHubUi.badge(TsgHubUi.statusLabel(state), TsgHubUi.statusColor(state)));
		meta.add(Box.createHorizontalStrut(5));
		boolean isParty = "drop-party".equals(TsgHubUi.str(event, "type"));
		meta.add(TsgHubUi.label(isParty ? TsgHubUi.dropPartyTime(TsgHubUi.eventConfig(event), false) : TsgHubUi.dateRange(event), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
		text.add(Box.createVerticalStrut(3));
		text.add(meta);
		text.add(Box.createVerticalStrut(2));
		text.add(TsgHubUi.wrapped(TsgHubUi.eventTypeLine(event), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), CARD_TITLE_W));
		card.add(text, BorderLayout.CENTER);
		String type = TsgHubUi.str(event, "type");
		boolean dropParty = "drop-party".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		JPanel east = new JPanel(new BorderLayout());
		east.setOpaque(false);
		east.add(joined ? TsgHubUi.badge("Joined", TsgHubUi.SUCCESS)
			: TsgHubUi.label(dropParty ? "Details" : "Join", TsgHubUi.ACCENT, FontManager.getRunescapeSmallFont()), BorderLayout.NORTH);
		card.add(east, BorderLayout.EAST);
		card.setToolTipText(dropParty ? "See when and where" : joined ? (competition ? "Open the leaderboard" : "Open your team's board")
			: competition ? "Join this competition" : "Join this event with a team code");
		TsgHubUi.clickable(card, () -> {
			String id = TsgHubUi.str(event, "id");
			if (dropParty) showDropParty(event);
			else if (competition && joined) plugin.openCompetition(id);
			else if (competition) showCompetitionPreview(event);
			else if (joined) plugin.activateEvent(id);
			else showPreview(event);
		});
		return TsgHubUi.fitHeight(card);
	}

	private void showPreview(JsonObject event)
	{
		previewEvent = event;
		setView(View.PREVIEW);
		setHeader(TsgHubUi.str(event, "name"), TsgHubUi.statusLabel(TsgHubUi.str(event, "status")) + " · " + TsgHubUi.dateRange(event), true, false);
		page.removeAll();
		page.add(TsgHubUi.wrapped("Enter the team code an admin gave you.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
		page.add(Box.createVerticalStrut(6));
		codeField.setText("");
		page.add(TsgHubUi.fitHeight(codeField));
		joinError.setVisible(false);
		page.add(joinError);
		page.add(Box.createVerticalStrut(6));
		joinButton.setEnabled(true);
		joinButton.setText("Join event");
		page.add(TsgHubUi.fitHeight(fullWidth(joinButton)));
		page.add(Box.createVerticalStrut(8));
		page.add(TsgHubUi.wrapped("Your team is set by the code and can't be changed after you join.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
		refreshPage();
		SwingUtilities.invokeLater(codeField::requestFocusInWindow);
	}

	private void submitJoin()
	{
		String code = codeField.getText().trim();
		if (code.isEmpty())
		{
			joinFailed("Enter your team code.");
			return;
		}
		joinError.setVisible(false);
		joinButton.setEnabled(false);
		joinButton.setText("Joining...");
		plugin.join(code, TsgHubUi.str(previewEvent, "id"));
	}

	private void renderBoard()
	{
		JsonObject event = boardEvent;
		setHeader(TsgHubUi.str(event, "name"), TsgHubUi.statusLabel(TsgHubUi.str(event, "status")) + " · " + TsgHubUi.dateRange(event), true, true);
		renderSummary();
		renderTasks();
		renderScoreboard();
		renderTeam();
	}

	private List<JsonObject> rankedTeams()
	{
		JsonArray teams = TsgHubUi.array(boardEvent, "teams");
		JsonArray scores = TsgHubUi.array(boardEvent, "teamScores");
		List<JsonObject> ranked = new ArrayList<>();
		for (int i = 0; i < teams.size(); i++) ranked.add(teams.get(i).getAsJsonObject());
		ranked.sort(Comparator
			.comparingInt((JsonObject team) -> TsgHubUi.integer(TsgHubUi.scoreFor(scores, TsgHubUi.str(team, "id")), "points", 0)).reversed()
			.thenComparing(Comparator.comparingInt((JsonObject team) -> TsgHubUi.integer(TsgHubUi.scoreFor(scores, TsgHubUi.str(team, "id")), "completedTasks", 0)).reversed())
			.thenComparing(team -> TsgHubUi.str(team, "name"), String.CASE_INSENSITIVE_ORDER));
		return ranked;
	}

	private String ownTeamId()
	{
		return TsgHubUi.teamIdFor(boardEvent, boardDisplayName);
	}

	private void renderSummary()
	{
		boardSummary.removeAll();
		String teamId = ownTeamId();
		List<JsonObject> ranked = rankedTeams();
		JsonObject score = TsgHubUi.scoreFor(TsgHubUi.array(boardEvent, "teamScores"), teamId);
		int totalTasks = TsgHubUi.array(boardEvent, "tasks").size();
		int completed = TsgHubUi.integer(score, "completedTasks", 0);
		String teamName = "No team";
		int rank = 0;
		for (int i = 0; i < ranked.size(); i++)
		{
			if (TsgHubUi.str(ranked.get(i), "id").equals(teamId))
			{
				teamName = TsgHubUi.str(ranked.get(i), "name");
				rank = i + 1;
			}
		}

		JPanel card = TsgHubUi.card();
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(teamName) + "</b>", CARD_TITLE_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
		if (rank > 0 && !TsgHubUi.bool(boardEvent, "hideScores"))
		{
			JLabel rankLabel = TsgHubUi.label("#" + rank + " of " + ranked.size(), rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, FontManager.getRunescapeBoldFont());
			row.add(rankLabel, BorderLayout.EAST);
		}
		card.add(row, BorderLayout.NORTH);
		ProgressBar bar = progressBar(completed, Math.max(totalTasks, 1));
		bar.setLeftLabel(TsgHubUi.integer(score, "points", 0) + " pts");
		bar.setRightLabel(completed + "/" + totalTasks + " tasks");
		card.add(bar, BorderLayout.CENTER);
		boardSummary.add(TsgHubUi.fitHeight(card));
		boardSummary.revalidate();
		boardSummary.repaint();
	}

	private void renderTasks()
	{
		if (boardEvent == null) return;
		tasksTab.removeAll();
		JsonArray tasks = TsgHubUi.array(boardEvent, "tasks");
		JsonObject score = TsgHubUi.scoreFor(TsgHubUi.array(boardEvent, "teamScores"), ownTeamId());
		JsonArray progressRows = TsgHubUi.array(score, "tasks");

		List<JsonObject> open = new ArrayList<>();
		List<JsonObject> done = new ArrayList<>();
		for (int i = 0; i < tasks.size(); i++)
		{
			JsonObject task = tasks.get(i).getAsJsonObject();
			boolean completed = TsgHubUi.bool(TsgHubUi.progressFor(progressRows, TsgHubUi.str(task, "id")), "completed");
			(completed ? done : open).add(task);
		}

		JPanel controls = new JPanel(new BorderLayout());
		controls.setOpaque(false);
		controls.add(hideCompleted, BorderLayout.WEST);
		JLabel left = TsgHubUi.label(open.size() + " left", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont());
		left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		controls.add(left, BorderLayout.EAST);
		tasksTab.add(TsgHubUi.fitHeight(controls));
		tasksTab.add(Box.createVerticalStrut(4));

		if (tasks.size() == 0)
		{
			tasksTab.add(errorPanel("No tasks yet", "The admins haven't added tasks to this event yet."));
		}
		else if (open.isEmpty() && hideCompleted.isSelected())
		{
			tasksTab.add(errorPanel("All done!", "Your team has completed every task."));
		}
		for (JsonObject task : open) addTaskCard(task, TsgHubUi.progressFor(progressRows, TsgHubUi.str(task, "id")));
		if (!hideCompleted.isSelected())
		{
			for (JsonObject task : done) addTaskCard(task, TsgHubUi.progressFor(progressRows, TsgHubUi.str(task, "id")));
		}
		refresh(tasksTab);
	}

	private void addTaskCard(JsonObject task, JsonObject progress)
	{
		tasksTab.add(taskCard(task, progress));
		tasksTab.add(Box.createVerticalStrut(5));
	}

	private JPanel taskCard(JsonObject task, JsonObject progress)
	{
		String taskId = TsgHubUi.str(task, "id");
		boolean completed = TsgHubUi.bool(progress, "completed");
		boolean pending = TsgHubUi.bool(progress, "pending");
		boolean individual = "individual".equals(TsgHubUi.str(task, "scope"));
		boolean solo = "solo".equals(TsgHubUi.str(task, "scope"));
		boolean manual = "manual".equals(TsgHubUi.str(task, "type"));

		JPanel card = TsgHubUi.card();
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		JLabel name = TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(TsgHubUi.str(task, "title")) + "</b>", CARD_TITLE_W - (completed ? 18 : 0)),
			completed ? TsgHubUi.MUTED : TsgHubUi.TEXT, FontManager.getRunescapeFont());
		if (completed) name.setIcon(new TsgHubUi.CheckIcon());
		top.add(name, BorderLayout.CENTER);
		JPanel points = new JPanel(new BorderLayout());
		points.setOpaque(false);
		points.add(TsgHubUi.badge(TsgHubUi.integer(task, "points", 1) + " pts", completed ? TsgHubUi.MUTED : TsgHubUi.ACCENT), BorderLayout.NORTH);
		top.add(points, BorderLayout.EAST);
		card.add(top, BorderLayout.NORTH);

		JPanel body = TsgHubUi.stack();
		Font small = FontManager.getRunescapeSmallFont();
		body.add(TsgHubUi.label(TsgHubUi.taskTypeLabel(task) + (individual ? " · Everyone" : solo ? " · Solo" : " · Team"), TsgHubUi.MUTED, small));
		String description = TsgHubUi.str(task, "description").trim();
		if (!description.isEmpty() && !completed) body.add(TsgHubUi.wrapped(description, TsgHubUi.MUTED, small, CARD_TEXT_W));

		int value = TsgHubUi.integer(progress, "progress", 0);
		int target = Math.max(1, TsgHubUi.integer(progress, "target", 1));
		if (completed)
		{
			boolean creditedToOrganizer = TsgHubUi.bool(progress, "override") && !TsgHubUi.bool(progress, "overrideCredited");
			body.add(TsgHubUi.label(creditedToOrganizer ? "Marked complete by an admin" : TsgHubUi.completedLine(progress), TsgHubUi.SUCCESS, small));
		}
		else if (addSetProgress(body, individual || solo ? myEntry(progress) : progress, individual || solo))
		{
			if (individual) body.add(TsgHubUi.label(value + " of " + target + " teammates done", TsgHubUi.MUTED, small));
			if (solo) addSoloLeader(body, progress);
		}
		else if (solo)
		{
			JsonObject mine = myEntry(progress);
			int have = mine == null ? 0 : TsgHubUi.integer(mine, "progress", 0);
			if (target > 1)
			{
				ProgressBar bar = progressBar(have, target);
				bar.setCenterLabel("You: " + have + "/" + target);
				body.add(Box.createVerticalStrut(4));
				body.add(bar);
				body.add(Box.createVerticalStrut(4));
			}
			addSoloLeader(body, progress);
		}
		else if (individual)
		{
			JsonObject mine = myEntry(progress);
			if (mine != null && TsgHubUi.integer(mine, "target", 1) > 1 && !TsgHubUi.bool(mine, "completed"))
			{
				ProgressBar bar = progressBar(TsgHubUi.integer(mine, "progress", 0), TsgHubUi.integer(mine, "target", 1));
				bar.setCenterLabel("You: " + TsgHubUi.integer(mine, "progress", 0) + "/" + TsgHubUi.integer(mine, "target", 1));
				body.add(Box.createVerticalStrut(4));
				body.add(bar);
				body.add(Box.createVerticalStrut(4));
			}
			else if (mine != null && TsgHubUi.bool(mine, "completed"))
			{
				body.add(TsgHubUi.label("You're done", TsgHubUi.SUCCESS, small));
			}
			body.add(TsgHubUi.label(value + " of " + target + " teammates done", TsgHubUi.MUTED, small));
		}
		else if (target > 1)
		{
			ProgressBar bar = progressBar(value, target);
			bar.setCenterLabel(value + " / " + target);
			body.add(Box.createVerticalStrut(4));
			body.add(bar);
			body.add(Box.createVerticalStrut(4));
		}
		boolean setTile = TsgHubUi.array(progress, "alternatives").size() > 0 || progress.has("items");
		if (!individual && !solo && (!setTile || completed))
		{
			String amounts = TsgHubUi.contributorsText(progress, " · ", 4);
			if (!amounts.isEmpty()) body.add(TsgHubUi.wrapped(amounts, TsgHubUi.MUTED, small, CARD_TEXT_W));
		}
		if (pending && !completed)
		{
			body.add(Box.createVerticalStrut(3));
			body.add(TsgHubUi.badge("Awaiting admin review", TsgHubUi.WARNING));
		}
		if (manual && !completed && !pending) addManualSubmit(body, taskId);
		addLineSpacing(body);
		card.add(body, BorderLayout.CENTER);
		card.setToolTipText(memberTooltip(progress, individual, solo));
		return TsgHubUi.fitHeight(card);
	}

	private void addManualSubmit(JPanel body, String taskId)
	{
		body.add(Box.createVerticalStrut(5));
		if (!taskId.equals(openManualTaskId))
		{
			JButton submit = TsgHubUi.button("Submit proof");
			submit.addActionListener(e -> {
				openManualTaskId = taskId;
				manualNote.setText("");
				renderTasks();
				SwingUtilities.invokeLater(manualNote::requestFocusInWindow);
			});
			body.add(TsgHubUi.fitHeight(fullWidth(submit)));
			return;
		}
		body.add(TsgHubUi.wrapped("Add a note or link for the admins.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), CARD_TEXT_W));
		body.add(Box.createVerticalStrut(3));
		body.add(TsgHubUi.fitHeight(manualNote));
		body.add(Box.createVerticalStrut(4));
		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		JButton cancel = TsgHubUi.button("Cancel");
		cancel.addActionListener(e -> {
			openManualTaskId = "";
			renderTasks();
		});
		JButton send = TsgHubUi.primaryButton("Send");
		Runnable doSend = () -> {
			if (manualNote.getText().trim().isEmpty())
			{
				setStatus("Add a short note before sending.", TsgHubUi.Tone.ERROR);
				manualNote.requestFocusInWindow();
				return;
			}
			send.setEnabled(false);
			plugin.submitManual(taskId, manualNote.getText().trim());
		};
		send.addActionListener(e -> doSend.run());
		for (java.awt.event.ActionListener listener : manualNote.getActionListeners()) manualNote.removeActionListener(listener);
		manualNote.addActionListener(e -> doSend.run());
		buttons.add(cancel);
		buttons.add(send);
		body.add(TsgHubUi.fitHeight(buttons));
	}

	private boolean addSetProgress(JPanel body, JsonObject source, boolean mine)
	{
		if (source == null || TsgHubUi.array(source, "alternatives").size() == 0 && !source.has("items")) return false;
		if (TsgHubUi.bool(source, "completed")) return true;
		Font small = FontManager.getRunescapeSmallFont();
		List<TsgHubUi.SetLine> sets = TsgHubUi.setLines(source, !mine);
		List<TsgHubUi.SetLine> shown = new ArrayList<>();
		for (TsgHubUi.SetLine set : sets) if (set.have > 0) shown.add(set);
		if (shown.isEmpty() && !sets.isEmpty()) shown.add(sets.get(0));
		for (TsgHubUi.SetLine set : shown)
		{
			ProgressBar bar = progressBar(set.have, set.target);
			bar.setCenterLabel((mine ? "You: " : "") + set.name + " " + set.have + "/" + set.target);
			body.add(Box.createVerticalStrut(4));
			body.add(bar);
			body.add(Box.createVerticalStrut(3));
			if (!set.found.isEmpty()) body.add(TsgHubUi.wrapped("Have: " + String.join(", ", set.found), TsgHubUi.MUTED, small, CARD_TEXT_W));
			if (!set.needed.isEmpty()) body.add(TsgHubUi.wrapped("Need: " + String.join(", ", set.needed), TsgHubUi.MUTED, small, CARD_TEXT_W));
		}
		int notStarted = sets.size() - shown.size();
		if (notStarted > 0) body.add(TsgHubUi.label("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started", TsgHubUi.MUTED, small));
		return true;
	}

	private static void addLineSpacing(JPanel body)
	{
		java.awt.Component[] parts = body.getComponents();
		body.removeAll();
		for (int i = 0; i < parts.length; i++)
		{
			body.add(parts[i]);
			boolean next = i + 1 < parts.length;
			if (next && parts[i] instanceof JLabel && parts[i + 1] instanceof JLabel) body.add(Box.createVerticalStrut(2));
		}
	}

	private String memberTooltip(JsonObject progress, boolean individual, boolean solo)
	{
		JsonArray members = TsgHubUi.array(progress, "members");
		if (individual || solo)
		{
			if (members.size() == 0) return null;
			List<JsonObject> sorted = new ArrayList<>();
			for (int i = 0; i < members.size(); i++) sorted.add(members.get(i).getAsJsonObject());
			sorted.sort((a, b) -> TsgHubUi.integer(b, "progress", 0) - TsgHubUi.integer(a, "progress", 0));
			List<String> done = new ArrayList<>();
			List<String> waiting = new ArrayList<>();
			for (JsonObject member : sorted)
			{
				String name = TsgHubUi.escape(TsgHubUi.str(member, "displayName"));
				if (TsgHubUi.str(member, "displayName").equalsIgnoreCase(boardDisplayName)) name += " (you)";
				int have = TsgHubUi.integer(member, "progress", 0);
				int need = TsgHubUi.integer(member, "target", 1);
				if (solo) waiting.add(name + " " + have + "/" + need);
				else if (TsgHubUi.bool(member, "completed")) done.add(name);
				else waiting.add(need > 1 ? name + " " + have + "/" + need : name);
			}
			if (solo) return "<html>" + String.join("<br>", waiting) + "</html>";
			StringBuilder text = new StringBuilder("<html>");
			text.append("<b>Done:</b> ").append(done.isEmpty() ? "nobody yet" : String.join(", ", done));
			text.append("<br><b>Still need:</b> ").append(waiting.isEmpty() ? "nobody" : String.join(", ", waiting));
			return text.append("</html>").toString();
		}
		String contributors = TsgHubUi.contributorsText(progress, "<br>", 50);
		return contributors.isEmpty() ? null : "<html><b>Contributors</b><br>" + contributors + "</html>";
	}

	private void addSoloLeader(JPanel body, JsonObject progress)
	{
		String leader = TsgHubUi.str(progress, "leader");
		if (leader.isEmpty() || leader.equalsIgnoreCase(boardDisplayName)) return;
		body.add(TsgHubUi.label("Leader: " + leader + " (" + TsgHubUi.integer(progress, "progress", 0) + "/" + TsgHubUi.integer(progress, "target", 1) + ")",
			TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
	}

	private JsonObject myEntry(JsonObject progress)
	{
		JsonArray members = TsgHubUi.array(progress, "members");
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
			if (TsgHubUi.str(member, "displayName").equalsIgnoreCase(boardDisplayName)) return member;
		}
		return null;
	}

	private void renderScoreboard()
	{
		scoreboardTab.removeAll();
		if (TsgHubUi.bool(boardEvent, "hideScores"))
		{
			renderHiddenScoreboard();
			return;
		}
		List<JsonObject> ranked = rankedTeams();
		JsonArray scores = TsgHubUi.array(boardEvent, "teamScores");
		int totalTasks = TsgHubUi.array(boardEvent, "tasks").size();
		String ownTeam = ownTeamId();
		if (ranked.isEmpty()) scoreboardTab.add(errorPanel("No teams yet", "Teams appear here once an admin adds them."));
		for (int i = 0; i < ranked.size(); i++)
		{
			JsonObject team = ranked.get(i);
			JsonObject score = TsgHubUi.scoreFor(scores, TsgHubUi.str(team, "id"));
			boolean mine = TsgHubUi.str(team, "id").equals(ownTeam);
			JPanel card = TsgHubUi.card();
			card.setLayout(new BorderLayout(8, 0));
			if (mine) card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, TsgHubUi.ACCENT), BorderFactory.createEmptyBorder(7, 5, 7, 8)));
			JLabel rank = TsgHubUi.label(String.valueOf(i + 1), i == 0 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, FontManager.getRunescapeBoldFont());
			rank.setPreferredSize(new Dimension(14, rank.getPreferredSize().height));
			card.add(rank, BorderLayout.WEST);
			JPanel text = TsgHubUi.stack();
			text.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(TsgHubUi.str(team, "name")) + (mine ? " <font color='#8f8f8f'>(you)</font>" : ""), 120), TsgHubUi.TEXT, FontManager.getRunescapeFont()));
			text.add(TsgHubUi.label(TsgHubUi.integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
			card.add(text, BorderLayout.CENTER);
			card.add(TsgHubUi.label(TsgHubUi.integer(score, "points", 0) + " pts", i == 0 ? TsgHubUi.ACCENT : TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), BorderLayout.EAST);
			scoreboardTab.add(TsgHubUi.fitHeight(card));
			scoreboardTab.add(Box.createVerticalStrut(4));
		}
		refresh(scoreboardTab);
	}

	private void renderHiddenScoreboard()
	{
		scoreboardTab.add(errorPanel("Scores are hidden", "The admins are keeping scores secret for now. Your own team's progress is on the Tasks tab."));
		List<JsonObject> teams = new ArrayList<>();
		JsonArray all = TsgHubUi.array(boardEvent, "teams");
		for (int i = 0; i < all.size(); i++) teams.add(all.get(i).getAsJsonObject());
		teams.sort((a, b) -> TsgHubUi.str(a, "name").compareToIgnoreCase(TsgHubUi.str(b, "name")));
		String ownTeam = ownTeamId();
		for (JsonObject team : teams)
		{
			boolean mine = TsgHubUi.str(team, "id").equals(ownTeam);
			JPanel card = TsgHubUi.card();
			if (mine) card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, TsgHubUi.ACCENT), BorderFactory.createEmptyBorder(7, 5, 7, 8)));
			card.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(TsgHubUi.str(team, "name")) + (mine ? " <font color='#8f8f8f'>(you)</font>" : ""), CARD_TEXT_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
			scoreboardTab.add(TsgHubUi.fitHeight(card));
			scoreboardTab.add(Box.createVerticalStrut(4));
		}
		refresh(scoreboardTab);
	}

	private void renderTeam()
	{
		teamTab.removeAll();
		String ownTeam = ownTeamId();
		List<String> teammates = new ArrayList<>();
		JsonArray members = TsgHubUi.array(boardEvent, "members");
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
			if (!ownTeam.isEmpty() && TsgHubUi.str(member, "teamId").equals(ownTeam)) teammates.add(TsgHubUi.str(member, "displayName"));
		}
		teammates.sort(String.CASE_INSENSITIVE_ORDER);
		teamTab.add(TsgHubUi.label(teammates.size() == 1 ? "1 member" : teammates.size() + " members", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
		teamTab.add(Box.createVerticalStrut(4));
		for (String name : teammates)
		{
			JPanel row = TsgHubUi.card();
			boolean me = name.equalsIgnoreCase(boardDisplayName);
			row.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(name) + (me ? " <font color='#8f8f8f'>(you)</font>" : ""), CARD_TEXT_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
			teamTab.add(TsgHubUi.fitHeight(row));
			teamTab.add(Box.createVerticalStrut(3));
		}
		teamTab.add(Box.createVerticalStrut(14));
		JButton leave = TsgHubUi.button("Disconnect from event");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> confirmLeave());
		teamTab.add(TsgHubUi.fitHeight(fullWidth(leave)));
		teamTab.add(Box.createVerticalStrut(3));
		teamTab.add(TsgHubUi.wrapped("Stops tracking on this device. Your team keeps its progress and you can rejoin with the same code.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
		refresh(teamTab);
	}

	private void confirmLeave()
	{
		if (boardEvent == null) return;
		int choice = JOptionPane.showConfirmDialog(this,
			"Stop tracking \"" + TsgHubUi.str(boardEvent, "name") + "\" on this device?\nYour team keeps its progress and you can rejoin with your team code.",
			"Disconnect from event", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.OK_OPTION) plugin.leaveEvent(TsgHubUi.str(boardEvent, "id"));
	}

	private ProgressBar progressBar(int value, int max)
	{
		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(Math.max(1, max));
		bar.setValue(Math.min(value, Math.max(1, max)));
		bar.setBackground(TsgHubUi.BACKGROUND);
		bar.setForeground(value >= max ? TsgHubUi.SUCCESS.darker() : TsgHubUi.ACCENT.darker().darker());
		// ProgressBar passes its fill color to labels; keep text white.
		for (java.awt.Component child : bar.getComponents()) if (child instanceof JLabel) child.setForeground(Color.WHITE);
		bar.setPreferredSize(new Dimension(100, 16));
		return TsgHubUi.fitHeight(bar);
	}

	private JPanel errorPanel(String heading, String body)
	{
		return TsgHubUi.emptyState(heading, body, TEXT_W - 20);
	}

	private static <T extends javax.swing.JComponent> T fullWidth(T component)
	{
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return component;
	}

	private void refreshPage()
	{
		refresh(page);
	}

	private static void refresh(JPanel panel)
	{
		panel.revalidate();
		panel.repaint();
	}
}
