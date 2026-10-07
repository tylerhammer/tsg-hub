package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
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
	private static final String ALT_RANK = "Gnome Child";
	private static final int CARD_TITLE_W = 150;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

	private enum View { LOGGED_OUT, NOT_IN_CLAN, SHARING_OFF, HOME, EVENTS, PREVIEW, BOARD, COMPETITION_PREVIEW, COMPETITION, DROP_PARTY, GROUPS, MEMBERS, DROPS }

	private final TsgHubPlugin plugin;
	private final JButton back = TsgHubUi.iconButton(new TsgHubUi.BackIcon(), "Back to events");
	private final JLabel title = TsgHubUi.label("TSG Hub", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont());
	private final JLabel subtitle = TsgHubUi.label(" ", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont());
	private String titleText = "TSG Hub";
	private final TsgHubUi.RefreshIcon refreshIcon = new TsgHubUi.RefreshIcon();
	private final JButton refresh = TsgHubUi.iconButton(refreshIcon, "Refresh");
	private final JButton organizer = TsgHubUi.iconButton(new TsgHubUi.OrganizerIcon(), "Admin tools");
	private final JButton settings = TsgHubUi.iconButton(new TsgHubUi.CogIcon(), "Plugin settings");
	private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
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
	private JsonArray events;
	private JsonObject previewEvent;
	private JsonObject boardEvent;
	private String boardDisplayName = "";
	private String openManualTaskId = "";
	private int busy;
	private volatile boolean active;
	private volatile boolean boardShowing;
	private volatile boolean membersWanted;
	private volatile boolean groupsWanted;
	private volatile boolean dropsWanted;
	private JsonArray members;
	private JsonArray offlineMembers = new JsonArray();
	private boolean notesLoaded;
	private final JCheckBox showOffline = new JCheckBox("Show offline");
	private String update;
	private JsonArray drops;
	private JsonObject competitionEvent;
	private String competitionName = "";
	private volatile String openCompetitionId;
	private final JButton competitionJoinButton = TsgHubUi.primaryButton("Join");

	private final JPanel titleRow = new JPanel(new BorderLayout(4, 0));
	private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
	private final List<Section> sections;
	private final TsgHubUi.WidthTrackingPanel groupsPage = new TsgHubUi.WidthTrackingPanel();
	private final GroupMembersPanel groupMembers;
	private static final String WILDERNESS = "Wilderness lvl ";
	private static final int DROP_ICON_W = 40;
	private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final Color SELF_CARD = new Color(43, 36, 22);
	private static final Color SELF_BORDER = new Color(122, 82, 8);
	private static final String SELF_TEXT = "#e0a82e";
	private static final Color TAG_DEFAULT = new Color(195, 140, 255);
	private static final Map<String, Color> TAG_COLORS = Map.of(
		"Log", new Color(55, 240, 70),
		"CoX", new Color(111, 179, 255),
		"ToB", new Color(255, 122, 122),
		"ToA", new Color(224, 180, 76),
		"Pet", new Color(255, 138, 216),
		"Dupe pet", new Color(255, 138, 216));
	private static final java.util.Set<String> COX_ITEMS = java.util.Set.of(
		"Dexterous prayer scroll", "Arcane prayer scroll", "Twisted buckler", "Dragon hunter crossbow",
		"Dinh's bulwark", "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Dragon claws",
		"Elder maul", "Kodai insignia", "Twisted bow", "Olmlet", "Metamorphic dust", "Twisted ancestral colour kit");
	private static final java.util.Set<String> TOB_ITEMS = java.util.Set.of(
		"Avernic defender hilt", "Ghrazi rapier", "Sanguinesti staff", "Justiciar faceguard", "Justiciar chestguard",
		"Justiciar legguards", "Scythe of vitur", "Lil' zik", "Sanguine dust", "Sanguine ornament kit", "Holy ornament kit");
	private static final java.util.Set<String> TOA_ITEMS = java.util.Set.of(
		"Osmumten's fang", "Lightbearer", "Elidinis' ward", "Masori mask", "Masori body", "Masori chaps",
		"Tumeken's shadow", "Tumeken's guardian", "Thread of Elidinis", "Breach of the scarab", "Eye of the corruptor",
		"Jewel of the sun", "Menaphite ornament kit", "Remnant of Akkha",
		"Remnant of Ba-Ba", "Remnant of Kephri", "Remnant of Zebak", "Ancient remnant");
	private final JButton createGroupButton = TsgHubUi.primaryButton("New party");
	private final JLabel groupError = TsgHubUi.label("", TsgHubUi.ERROR, FontManager.getRunescapeSmallFont());
	private JsonArray groupList;
	private boolean groupBusy;
	// Browsing the full list from inside a party.
	private boolean browsingParties;

	private static final class Section
	{
		private final String name;
		private final String tooltip;
		private final Icon icon;
		private final Runnable open;
		private final Supplier<String> summary;
		private final BooleanSupplier live;

		Section(String name, String tooltip, Icon icon, Runnable open, Supplier<String> summary, BooleanSupplier live)
		{
			this.name = name;
			this.tooltip = tooltip;
			this.icon = icon;
			this.open = open;
			this.summary = summary;
			this.live = live;
		}
	}

	TsgHubSidebarPanel(TsgHubPlugin plugin, GroupMembersPanel groupMembers)
	{
		super(false);
		this.plugin = plugin;
		this.groupMembers = groupMembers;
		sections = Arrays.asList(
			new Section("Events", "Clan events and your team's board", new TsgHubUi.CalendarIcon(), this::openEvents, this::eventsSummary, () -> liveEvents() > 0),
			new Section("Parties", "Join a clanmate's party or start one", new TsgHubUi.PartyIcon(), this::showParties, this::partiesSummary, () -> plugin.groups().inGroup()),
			new Section("Members", "See what clanmates are up to", new TsgHubUi.MembersIcon(), this::showMembers, this::membersSummary, () -> onlineCount() > 0),
			new Section("Drops", "Recent big drops across the clan", new TsgHubUi.DropsIcon(), this::showDrops, () -> "", () -> false));
		setLayout(new BorderLayout(0, 6));
		setBackground(TsgHubUi.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);

		center.setOpaque(false);
		center.add(TsgHubUi.scroll(page), "page");
		center.add(buildBoard(), "board");
		center.add(TsgHubUi.scroll(groupsPage), "groups");
		add(center, BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		codeField.setToolTipText("Team code from an admin");
		codeField.putClientProperty("JTextField.placeholderText", "Team code, e.g. DRGN42");
		manualNote.putClientProperty("JTextField.placeholderText", "Screenshot link or note");
		codeField.addActionListener(e -> submitJoin());
		joinButton.addActionListener(e -> submitJoin());
		joinError.setVisible(false);
		createGroupButton.addActionListener(e -> submitCreateGroup());
		groupError.setVisible(false);

		showOffline.setOpaque(false);
		showOffline.setForeground(TsgHubUi.MUTED);
		showOffline.setFont(FontManager.getRunescapeSmallFont());
		showOffline.setFocusPainted(false);
		showOffline.setSelected("true".equals(TsgHubSession.get("showOffline")));
		showOffline.addActionListener(e -> {
			TsgHubSession.set("showOffline", showOffline.isSelected() ? "true" : "");
			renderMembers();
		});

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
		titleRow.setOpaque(false);
		back.addActionListener(e -> {
			if (view == View.GROUPS && browsingParties && plugin.groups().inGroup())
			{
				browsingParties = false;
				renderGroups();
				return;
			}
			if (view == View.GROUPS || view == View.EVENTS || view == View.MEMBERS || view == View.DROPS)
			{
				showHome();
				return;
			}
			showEventList();
			plugin.loadClanEvents();
		});
		titleRow.add(back, BorderLayout.WEST);

		JPanel titles = TsgHubUi.stack();
		titles.add(title);
		titles.add(TsgHubUi.shrinkable(subtitle));
		JPanel titleSlot = new JPanel(new java.awt.GridBagLayout());
		titleSlot.setOpaque(false);
		java.awt.GridBagConstraints fill = new java.awt.GridBagConstraints();
		fill.weightx = 1;
		fill.fill = java.awt.GridBagConstraints.HORIZONTAL;
		titleSlot.add(titles, fill);
		titleRow.add(titleSlot, BorderLayout.CENTER);

		actions.setOpaque(false);
		organizer.addActionListener(e -> plugin.openWorkspace());
		organizer.setVisible(false);
		refresh.addActionListener(e -> {
			if (view == View.HOME)
			{
				plugin.loadClanEvents();
				plugin.groups().refresh();
				plugin.presence().loadMembers(false);
			}
			else if (view == View.GROUPS) plugin.groups().refresh();
			else if (view == View.MEMBERS) plugin.presence().loadMembers(false);
			else if (view == View.DROPS) plugin.drops().load(false);
			else if (view == View.BOARD) plugin.refreshBoard();
			else if (view == View.COMPETITION && competitionEvent != null) plugin.openCompetition(TsgHubUi.str(competitionEvent, "id"));
			else plugin.loadClanEvents();
		});
		JPanel refreshSlot = new JPanel(new BorderLayout());
		refreshSlot.setOpaque(false);
		refreshSlot.setPreferredSize(refresh.getPreferredSize());
		refreshSlot.add(refresh);
		settings.addActionListener(e -> plugin.openSettings());
		actions.add(organizer);
		actions.add(refreshSlot);
		actions.add(settings);
		titleRow.add(actions, BorderLayout.EAST);

		JPanel below = TsgHubUi.stack();
		below.add(titleRow);
		below.add(status);

		header.setOpaque(false);
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
		title.setText(TsgHubUi.html(TsgHubUi.escape(titleText), titleWidth()));
		if (view == View.MEMBERS) renderMembers();
	}

	private int titleWidth()
	{
		return 150 - (organizer.isVisible() ? organizer.getPreferredSize().width + 2 : 0);
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

	boolean wantsMembers()
	{
		return active && membersWanted;
	}

	boolean wantsDrops()
	{
		return active && dropsWanted;
	}

	boolean wantsGroups()
	{
		return active && groupsWanted;
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

	void showCheckingClan()
	{
		setView(View.NOT_IN_CLAN);
		setHeader("TSG Hub", "", false, false);
		page.removeAll();
		page.add(TsgHubUi.label("Checking your clan...", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
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
		else if (view == View.HOME) renderHome();
	}

	void showHome()
	{
		setView(View.HOME);
		renderHome();
		plugin.groups().refresh();
		plugin.presence().loadMembers(true);
	}

	private void renderHome()
	{
		setHeader("TSG Hub", plugin.getDetectedPlayerName(), false, true);
		page.removeAll();
		for (int i = 0; i < sections.size(); i += 2)
		{
			boolean pair = i + 1 < sections.size();
			JPanel row = new JPanel(new GridLayout(1, pair ? 2 : 1, 6, 0));
			row.setOpaque(false);
			row.add(sectionTile(sections.get(i), pair));
			if (pair) row.add(sectionTile(sections.get(i + 1), true));
			page.add(TsgHubUi.fitHeight(row));
			page.add(Box.createVerticalStrut(6));
		}
		if (update != null)
		{
			page.add(Box.createVerticalStrut(4));
			JLabel notice = TsgHubUi.label("Update available. Restart RuneLite.", TsgHubUi.WARNING, FontManager.getRunescapeSmallFont());
			notice.setHorizontalAlignment(JLabel.CENTER);
			notice.setToolTipText("TSG Hub " + update + " is available. Restart RuneLite to update.");
			page.add(TsgHubUi.fitHeight(fullWidth(notice)));
		}
		refreshPage();
	}

	void setUpdate(String version)
	{
		update = version;
		if (view == View.HOME) renderHome();
	}

	private JPanel sectionTile(Section section, boolean half)
	{
		JPanel tile = new JPanel();
		tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
		tile.setBackground(TsgHubUi.CARD);
		tile.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 6));
		JLabel icon = new JLabel(section.icon);
		JLabel name = TsgHubUi.label(section.name, TsgHubUi.TEXT, FontManager.getRunescapeBoldFont());
		JLabel summary = TsgHubUi.label(TsgHubUi.html("<div style='text-align:center'>" + TsgHubUi.escape(section.summary.get()) + "</div>", half ? 80 : 180),
			section.live.getAsBoolean() ? TsgHubUi.SUCCESS : TsgHubUi.MUTED, FontManager.getRunescapeSmallFont());
		tile.add(icon);
		tile.add(Box.createVerticalStrut(8));
		tile.add(name);
		tile.add(Box.createVerticalStrut(2));
		tile.add(summary);
		for (java.awt.Component part : tile.getComponents())
		{
			((javax.swing.JComponent) part).setAlignmentX(CENTER_ALIGNMENT);
			if (part instanceof JLabel) ((JLabel) part).setHorizontalAlignment(JLabel.CENTER);
		}
		tile.setToolTipText(section.tooltip);
		TsgHubUi.clickable(tile, section.open);
		return tile;
	}

	private int liveEvents()
	{
		int live = 0;
		if (events != null)
			for (int i = 0; i < events.size(); i++)
				if ("active".equals(TsgHubUi.str(events.get(i).getAsJsonObject(), "status"))) live++;
		return live;
	}

	private String eventsSummary()
	{
		if (events == null) return "Loading...";
		int live = liveEvents();
		int upcoming = -live;
		for (int i = 0; i < events.size(); i++)
			if (!"ended".equals(TsgHubUi.str(events.get(i).getAsJsonObject(), "status"))) upcoming++;
		if (live == 0 && upcoming == 0) return "No events right now";
		if (upcoming == 0) return live + " live";
		if (live == 0) return upcoming + " upcoming";
		return live + " live, " + upcoming + " upcoming";
	}

	private String partiesSummary()
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

	private int onlineCount()
	{
		return members == null ? 0 : members.size();
	}

	private String membersSummary()
	{
		if (members == null) return "Loading...";
		if (members.size() == 0) return "Nobody online";
		return members.size() + " online";
	}

	void locationSharingChanged()
	{
		if (view == View.MEMBERS) renderMembers();
	}

	void setMembers(JsonArray members, JsonArray offline, boolean notesLoaded)
	{
		this.members = members;
		this.offlineMembers = offline;
		this.notesLoaded = notesLoaded;
		if (view == View.MEMBERS) renderMembers();
		else if (view == View.HOME) renderHome();
	}

	void showMembers()
	{
		setView(View.MEMBERS);
		renderMembers();
		plugin.presence().loadMembers(false);
	}

	private void renderMembers()
	{
		Font small = FontManager.getRunescapeSmallFont();
		setHeader("Members", "", true, true);
		page.removeAll();
		if (members == null)
		{
			page.add(TsgHubUi.label("Loading members...", TsgHubUi.MUTED, small));
			refreshPage();
			return;
		}
		page.add(sectionHeading("Online (" + members.size() + ")", null));
		if (members.size() == 0) page.add(TsgHubUi.wrapped("Nobody online right now.", TsgHubUi.MUTED, small, TEXT_W));
		int myWorld = 0;
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
			if (TsgHubUi.samePlayer(TsgHubUi.str(member, "displayName"), plugin.getDetectedPlayerName())) myWorld = TsgHubUi.integer(member, "world", 0);
		}
		for (int i = 0; i < members.size(); i++)
		{
			page.add(memberCard(members.get(i).getAsJsonObject(), myWorld));
			page.add(Box.createVerticalStrut(6));
		}
		if (offlineMembers.size() > 0)
		{
			page.add(Box.createVerticalStrut(4));
			page.add(sectionHeading("Offline (" + offlineMembers.size() + ")", showOffline));
			if (showOffline.isSelected())
			{
				for (int i = 0; i < offlineMembers.size(); i++)
				{
					page.add(offlineRow(offlineMembers.get(i).getAsJsonObject()));
					page.add(Box.createVerticalStrut(3));
				}
			}
		}
		page.add(Box.createVerticalStrut(8));
		page.add(TsgHubUi.wrapped(plugin.locationSharingEnabled() ? "Leave clan chat to hide yourself."
			: "You show as Online. Turn on location sharing in settings to show what you're doing.", TsgHubUi.MUTED, small, TEXT_W));
		refreshPage();
	}

	private static void highlightSelf(JPanel card)
	{
		card.setBackground(SELF_CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(SELF_BORDER),
			BorderFactory.createEmptyBorder(6, 7, 6, 7)));
	}

	private JPanel sectionHeading(String text, JComponent right)
	{
		JPanel row = TsgHubUi.row();
		row.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
		row.add(TsgHubUi.label(text.toUpperCase(), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()), BorderLayout.CENTER);
		if (right != null) row.add(right, BorderLayout.EAST);
		return TsgHubUi.fitHeight(row);
	}

	private JLabel memberName(JsonObject member, String text, Color color, Font font)
	{
		JLabel label = TsgHubUi.shrinkable(TsgHubUi.label(text, color, font));
		java.awt.image.BufferedImage rankIcon = plugin.presence().rankIcon(member);
		if (rankIcon != null)
		{
			label.setIcon(new javax.swing.ImageIcon(rankIcon));
			label.setIconTextGap(4);
		}
		String tip = rankTooltip(TsgHubUi.str(member, "rank"), TsgHubUi.str(member, "altOf"), TsgHubUi.array(member, "alts"));
		if (!tip.isEmpty()) label.setToolTipText(tip);
		return label;
	}

	private static JLabel noteIcon(String note)
	{
		JLabel label = new JLabel(new TsgHubUi.NoteIcon());
		label.setToolTipText(TsgHubUi.html("Admin note<br>" + TsgHubUi.escape(note), 200));
		return label;
	}

	private JPanel memberCard(JsonObject member, int myWorld)
	{
		Font small = FontManager.getRunescapeSmallFont();
		String name = TsgHubUi.str(member, "displayName");
		String activity = TsgHubUi.str(member, "activity");
		String note = visibleNote(member);
		int world = TsgHubUi.integer(member, "world", 0);
		boolean self = TsgHubUi.samePlayer(name, plugin.getDetectedPlayerName());
		JPanel card = TsgHubUi.card();
		if (self) highlightSelf(card);

		JPanel top = TsgHubUi.row();
		top.add(memberName(member, name, TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), BorderLayout.CENTER);
		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		badges.setOpaque(false);
		if (!note.isEmpty())
		{
			JLabel noteLabel = noteIcon(note);
			noteLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			badges.add(noteLabel);
		}
		if (world > 0)
		{
			boolean sameWorld = !self && world == myWorld;
			JLabel worldLabel = TsgHubUi.label("W" + world, sameWorld ? TsgHubUi.SUCCESS : TsgHubUi.MUTED, small);
			if (sameWorld) worldLabel.setToolTipText("On your world");
			badges.add(worldLabel);
		}
		if (badges.getComponentCount() > 0) top.add(badges, BorderLayout.EAST);

		JPanel text = TsgHubUi.stack();
		text.add(top);
		String detail = activityDetail(activity, TsgHubUi.str(member, "area"));
		if (!detail.isEmpty())
		{
			boolean active = !"Idle".equals(activity) && !"Online".equals(activity) && !activity.isEmpty();
			JLabel detailLabel = TsgHubUi.shrinkable(TsgHubUi.label(detail, active ? TsgHubUi.SUCCESS : TsgHubUi.MUTED, small));
			detailLabel.setToolTipText(detail);
			JPanel bottom = TsgHubUi.row();
			bottom.add(detailLabel, BorderLayout.CENTER);
			text.add(Box.createVerticalStrut(3));
			text.add(bottom);
		}
		card.add(text, BorderLayout.CENTER);
		addNoteMenu(card, member);
		return TsgHubUi.fitHeight(card);
	}

	private JPanel offlineRow(JsonObject member)
	{
		Font small = FontManager.getRunescapeSmallFont();
		String note = visibleNote(member);
		String seen = lastSeen(TsgHubUi.str(member, "lastSeenAt"), TsgHubUi.clock.instant());
		JPanel row = TsgHubUi.card();
		row.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
		row.add(memberName(member, TsgHubUi.str(member, "displayName"), TsgHubUi.MUTED, small), BorderLayout.CENTER);
		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		badges.setOpaque(false);
		if (!note.isEmpty())
		{
			JLabel noteLabel = noteIcon(note);
			if (!seen.isEmpty()) noteLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			badges.add(noteLabel);
		}
		if (!seen.isEmpty())
		{
			JLabel seenLabel = TsgHubUi.label(seen, TsgHubUi.MUTED, small);
			seenLabel.setToolTipText("Last seen with TSG Hub");
			badges.add(seenLabel);
		}
		if (badges.getComponentCount() > 0) row.add(badges, BorderLayout.EAST);
		addNoteMenu(row, member);
		return TsgHubUi.fitHeight(row);
	}

	private String visibleNote(JsonObject member)
	{
		return plugin.canManageOrganizerUi() ? TsgHubUi.str(member, "note") : "";
	}

	static String lastSeen(String iso, Instant now)
	{
		Instant then = TsgHubUi.instant(iso);
		if (then == null) return "";
		long minutes = Math.max(0, java.time.Duration.between(then, now).toMinutes());
		if (minutes < 1) return "just now";
		if (minutes < 60) return minutes + "m ago";
		long hours = minutes / 60;
		if (hours < 24) return hours + "h ago";
		long days = hours / 24;
		if (days < 14) return days + "d ago";
		if (days < 60) return days / 7 + "w ago";
		if (days < 365) return days / 30 + "mo ago";
		return days / 365 + "y ago";
	}

	private void addNoteMenu(JComponent card, JsonObject member)
	{
		if (!plugin.canManageOrganizerUi() || !notesLoaded) return;
		String name = TsgHubUi.str(member, "displayName");
		String altOf = TsgHubUi.str(member, "altOf");
		String note = TsgHubUi.str(member, "note");
		boolean alt = isAltRank(TsgHubUi.str(member, "rank"));
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem edit = new javax.swing.JMenuItem(alt ? "Edit alt and admin note" : "Edit admin note");
		edit.addActionListener(e -> promptMemberNote(name, alt, altOf, note));
		menu.add(edit);
		card.setComponentPopupMenu(menu);
		inheritMenu(card);
	}

	private static void inheritMenu(java.awt.Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JComponent) ((JComponent) child).setInheritsPopupMenu(true);
			if (child instanceof java.awt.Container) inheritMenu((java.awt.Container) child);
		}
	}

	static String rankTooltip(String rank, String altOf, JsonArray alts)
	{
		List<String> parts = new ArrayList<>();
		if (!rank.isEmpty()) parts.add(rank);
		if (!altOf.isEmpty()) parts.add("Alt of " + altOf);
		List<String> names = new ArrayList<>();
		for (int i = 0; i < alts.size(); i++) if (alts.get(i).isJsonPrimitive()) names.add(alts.get(i).getAsString());
		if (!names.isEmpty()) parts.add((names.size() == 1 ? "Alt: " : "Alts: ") + String.join(", ", names));
		return String.join(" · ", parts);
	}

	static boolean isAltRank(String rank)
	{
		return ALT_RANK.equalsIgnoreCase(rank.trim());
	}

	private void promptMemberNote(String name, boolean alt, String altOf, String note)
	{
		List<String> candidates = mainCandidates(name);
		TsgHubMemberPicker main = new TsgHubMemberPicker(candidates, altOf);
		javax.swing.JTextArea notes = new javax.swing.JTextArea(note, 4, 20);
		notes.setLineWrap(true);
		notes.setWrapStyleWord(true);
		JPanel form = TsgHubUi.stack();
		if (alt)
		{
			form.add(new JLabel("Alt of (main character)"));
			form.add(main);
			form.add(Box.createVerticalStrut(8));
		}
		form.add(new JLabel("Admin note (only admins see this)"));
		form.add(new javax.swing.JScrollPane(notes));
		if (JOptionPane.showConfirmDialog(this, form, name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		String typed = main.getText();
		String nextMain = typed.equals(altOf) ? altOf : resolveName(candidates, typed);
		String nextNote = notes.getText().trim();
		if (TsgHubUi.samePlayer(nextMain, name))
		{
			setStatus("A character can't be its own alt.", TsgHubUi.Tone.ERROR);
			return;
		}
		if (nextNote.length() > 500)
		{
			setStatus("Keep the note to 500 characters.", TsgHubUi.Tone.ERROR);
			return;
		}
		if (nextMain.equals(altOf) && nextNote.equals(note)) return;
		plugin.presence().saveNote(name, nextMain, nextNote);
	}

	private List<String> mainCandidates(String alt)
	{
		List<String> names = new ArrayList<>();
		for (JsonArray list : new JsonArray[] {members, offlineMembers})
		{
			if (list == null) continue;
			for (int i = 0; i < list.size(); i++)
			{
				JsonObject member = list.get(i).getAsJsonObject();
				String candidate = TsgHubUi.str(member, "displayName");
				if (!TsgHubUi.samePlayer(candidate, alt) && !isAltRank(TsgHubUi.str(member, "rank"))) names.add(candidate);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	static List<String> matchNames(List<String> names, String query, int limit)
	{
		String needle = PlayerNames.normalize(query);
		List<String> prefix = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String name : names)
		{
			String key = PlayerNames.normalize(name);
			if (key.startsWith(needle)) prefix.add(name);
			else if (key.contains(needle)) contains.add(name);
		}
		prefix.addAll(contains);
		return prefix.size() > limit ? prefix.subList(0, limit) : prefix;
	}

	static String resolveName(List<String> names, String typed)
	{
		if (typed.isEmpty()) return typed;
		for (String name : names) if (TsgHubUi.samePlayer(name, typed)) return name;
		List<String> matches = matchNames(names, typed, 2);
		return matches.size() == 1 ? matches.get(0) : typed;
	}

	static String activityDetail(String activity, String area)
	{
		if (activity.isEmpty()) activity = "Online";
		int dash = activity.indexOf(" - ");
		String detail = dash < 0 ? activity : activity.startsWith("Slayer - ") ? "Slayer: " + activity.substring(dash + 3) : activity.substring(dash + 3);
		if (area.isEmpty() || area.equals(detail)) return detail;
		return detail + " · " + area;
	}

	void setDrops(JsonArray drops)
	{
		this.drops = drops;
		if (view == View.DROPS) renderDrops();
	}

	void showDrops()
	{
		setView(View.DROPS);
		renderDrops();
		plugin.drops().load(false);
	}

	private void renderDrops()
	{
		Font small = FontManager.getRunescapeSmallFont();
		setHeader("Drops", "", true, true);
		page.removeAll();
		if (drops == null)
		{
			page.add(TsgHubUi.label("Loading drops...", TsgHubUi.MUTED, small));
		}
		else if (drops.size() == 0)
		{
			page.add(errorPanel("No drops yet", "Clan broadcasts for drops, raid loot, pets and collection log items show up here."));
		}
		else
		{
			Instant now = TsgHubUi.clock.instant();
			java.time.ZoneId zone = java.time.ZoneId.systemDefault();
			String day = null;
			for (int i = 0; i < drops.size(); i++)
			{
				JsonObject drop = drops.get(i).getAsJsonObject();
				String nextDay = dropDay(TsgHubUi.str(drop, "receivedAt"), now, zone);
				if (!nextDay.equals(day))
				{
					JLabel heading = TsgHubUi.label(nextDay.toUpperCase(), TsgHubUi.MUTED, small);
					heading.setBorder(BorderFactory.createEmptyBorder(i == 0 ? 0 : 6, 2, 4, 0));
					page.add(heading);
					day = nextDay;
				}
				page.add(dropCard(drop, now, zone));
				page.add(Box.createVerticalStrut(6));
			}
		}
		refreshPage();
	}

	private JPanel dropCard(JsonObject drop, Instant now, java.time.ZoneId zone)
	{
		Font small = FontManager.getRunescapeSmallFont();
		String player = TsgHubUi.str(drop, "player");
		boolean self = TsgHubUi.samePlayer(player, plugin.getDetectedPlayerName());
		JPanel card = TsgHubUi.card();
		if (self) highlightSelf(card);
		JLabel icon = dropIcon(TsgHubUi.integer(drop, "itemId", 0));
		if (icon != null) card.add(icon, BorderLayout.WEST);

		JLabel name = TsgHubUi.shrinkable(TsgHubUi.label(dropItem(drop), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
		name.setToolTipText(TsgHubUi.str(drop, "item"));
		JPanel top = TsgHubUi.row();
		top.add(name, BorderLayout.CENTER);
		top.add(TsgHubUi.label(dropWhen(TsgHubUi.str(drop, "receivedAt"), now, zone), TsgHubUi.MUTED, small), BorderLayout.EAST);

		JPanel bottom = TsgHubUi.row();
		bottom.add(TsgHubUi.shrinkable(TsgHubUi.label(player, TsgHubUi.ACCENT, small)), BorderLayout.CENTER);
		JPanel extras = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		extras.setOpaque(false);
		for (String tag : dropTags(drop)) extras.add(TsgHubUi.badge(tag, TAG_COLORS.getOrDefault(tag, TAG_DEFAULT)));
		long value = drop.has("value") ? drop.get("value").getAsLong() : 0;
		if (value > 0) extras.add(TsgHubUi.label(TsgHubUi.formatGp(value), TsgHubUi.coinColor(value), small));
		bottom.add(extras, BorderLayout.EAST);

		JPanel text = TsgHubUi.stack();
		text.add(top);
		text.add(Box.createVerticalStrut(3));
		text.add(bottom);
		JPanel center = new JPanel(new java.awt.GridBagLayout());
		center.setOpaque(false);
		java.awt.GridBagConstraints fill = new java.awt.GridBagConstraints();
		fill.fill = java.awt.GridBagConstraints.HORIZONTAL;
		fill.weightx = 1;
		center.add(text, fill);
		card.add(center, BorderLayout.CENTER);
		return TsgHubUi.fitHeight(card);
	}


	private JLabel dropIcon(int itemId)
	{
		if (itemId <= 0) return null;
		net.runelite.client.util.AsyncBufferedImage image = plugin.getItemImage(itemId);
		if (image == null) return null;
		JLabel label = new JLabel();
		label.setVerticalAlignment(JLabel.CENTER);
		label.setPreferredSize(new java.awt.Dimension(DROP_ICON_W, 32));
		image.addTo(label);
		return label;
	}

	static List<String> dropTags(JsonObject drop)
	{
		List<String> tags = new ArrayList<>();
		String kind = TsgHubUi.str(drop, "kind");
		if ("raid".equals(kind)) tags.add(raidName(TsgHubUi.str(drop, "item")));
		else if ("pet".equals(kind)) tags.add("Pet");
		else if ("dupe".equals(kind)) tags.add("Dupe pet");
		if (TsgHubUi.bool(drop, "newLog") || "clog".equals(kind)) tags.add("Log");
		return tags;
	}

	static String raidName(String item)
	{
		String base = item.replaceAll("(\\s*\\([^)]*\\))+$", "");
		if (COX_ITEMS.contains(base)) return "CoX";
		if (TOB_ITEMS.contains(base)) return "ToB";
		if (TOA_ITEMS.contains(base)) return "ToA";
		return "Raid";
	}

	static String dropItem(JsonObject drop)
	{
		int quantity = TsgHubUi.integer(drop, "quantity", 1);
		String item = TsgHubUi.str(drop, "item").replaceAll("(\\s*\\([^)]*\\))+$", "");
		return quantity > 1 ? quantity + " x " + item : item;
	}

	static String dropKind(String kind)
	{
		switch (kind)
		{
			case "raid": return "Raid";
			case "pet": return "Pet";
			case "dupe": return "Dupe pet";
			default: return "";
		}
	}


	static String dropAge(String iso, Instant now)
	{
		Instant then;
		try { then = Instant.parse(iso); }
		catch (Exception e) { return ""; }
		long minutes = Math.max(0, Duration.between(then, now).toMinutes());
		if (minutes < 1) return "now";
		if (minutes < 60) return minutes + "m";
		long hours = minutes / 60;
		if (hours < 24) return hours + "h";
		return hours / 24 + "d";
	}

	static String dropWhen(String iso, Instant now, java.time.ZoneId zone)
	{
		String day = dropDay(iso, now, zone);
		if ("Earlier".equals(day)) return "";
		if ("Today".equals(day)) return dropAge(iso, now);
		return Instant.parse(iso).atZone(zone).format(CLOCK_TIME);
	}

	static String dropDay(String iso, Instant now, java.time.ZoneId zone)
	{
		Instant then;
		try { then = Instant.parse(iso); }
		catch (Exception e) { return "Earlier"; }
		java.time.LocalDate day = then.atZone(zone).toLocalDate();
		java.time.LocalDate today = now.atZone(zone).toLocalDate();
		if (!day.isBefore(today)) return "Today";
		if (day.equals(today.minusDays(1))) return "Yesterday";
		return TsgHubUi.localDate(then, zone);
	}

	private void openEvents()
	{
		showEventList();
		plugin.loadClanEvents();
	}

	void showEventList()
	{
		setView(View.EVENTS);
		renderEvents();
	}

	void closeBoard()
	{
		if (view == View.BOARD) showEventList();
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
		setEventHeader(event, true, false);
		page.removeAll();
		Font small = FontManager.getRunescapeSmallFont();
		JsonObject config = TsgHubUi.eventConfig(event);
		boolean skill = "skill".equals(TsgHubUi.str(event, "type"));
		String subject = skill ? TsgHubUi.skillName(TsgHubUi.str(config, "skill")) : TsgHubUi.str(config, "npcName");
		page.add(eventSummaryCard(event, subject, skill ? "Most " + subject + " XP gained wins." : "Most " + subject + " kills wins."));
		page.add(Box.createVerticalStrut(12));
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
		setEventHeader(event, true, true);
		page.removeAll();
		Font small = FontManager.getRunescapeSmallFont();
		JsonObject config = TsgHubUi.eventConfig(event);
		boolean skill = "skill".equals(TsgHubUi.str(event, "type"));
		boolean hidden = TsgHubUi.bool(event, "hideScores");
		JsonArray rows = TsgHubUi.array(event, "leaderboard");
		JsonObject mine = null;
		for (int i = 0; i < rows.size(); i++)
			if (TsgHubUi.str(rows.get(i).getAsJsonObject(), "displayName").equalsIgnoreCase(competitionName)) mine = rows.get(i).getAsJsonObject();

		boolean started = !"scheduled".equals(TsgHubUi.str(event, "status"));
		boolean tracking = mine != null && TsgHubUi.bool(mine, "tracking");
		JPanel summary = TsgHubUi.card();
		JPanel top = TsgHubUi.row();
		top.add(TsgHubUi.shrinkable(TsgHubUi.label(skill ? TsgHubUi.skillName(TsgHubUi.str(config, "skill")) : TsgHubUi.str(config, "npcName"), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont())), BorderLayout.CENTER);
		if (started && mine != null && mine.has("rank") && !hidden)
		{
			int rank = TsgHubUi.integer(mine, "rank", 0);
			top.add(TsgHubUi.label("#" + rank + " of " + TsgHubUi.integer(event, "participants", rows.size()), rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, small), BorderLayout.EAST);
		}
		JPanel lines = TsgHubUi.stack();
		lines.add(top);
		lines.add(Box.createVerticalStrut(3));
		if (!started)
		{
			String start = TsgHubUi.eventStartWhen(event);
			lines.add(TsgHubUi.wrapped(start.isEmpty() ? "Counting starts when the event begins." : "Counting starts " + start + ".", TsgHubUi.MUTED, small, CARD_TEXT_W));
		}
		else
		{
			lines.add(TsgHubUi.label("+" + String.format("%,d", mine == null ? 0 : TsgHubUi.integer(mine, "gained", 0)) + " " + unit(), TsgHubUi.SUCCESS, FontManager.getRunescapeBoldFont()));
			if (!tracking)
			{
				lines.add(Box.createVerticalStrut(2));
				lines.add(TsgHubUi.wrapped(skill ? "Starts counting the next time you gain XP." : "Starts counting at your next kill.", TsgHubUi.MUTED, small, CARD_TEXT_W));
			}
		}
		JPanel prizes = TsgHubUi.prizeRow(event, small);
		if (prizes != null)
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(prizes);
		}
		summary.add(lines, BorderLayout.CENTER);
		page.add(TsgHubUi.fitHeight(summary));
		page.add(Box.createVerticalStrut(8));

		if (hidden)
		{
			page.add(errorPanel("Scores are hidden", "The admins are keeping the leaderboard secret for now."));
		}
		else
		{
			page.add(TsgHubUi.label(TsgHubUi.integer(event, "participants", rows.size()) + (started ? " taking part" : " signed up"), TsgHubUi.MUTED, small));
			page.add(Box.createVerticalStrut(4));
			for (int i = 0; i < rows.size(); i++)
			{
				JsonObject row = rows.get(i).getAsJsonObject();
				boolean me = TsgHubUi.str(row, "displayName").equalsIgnoreCase(competitionName);
				int rank = TsgHubUi.integer(row, "rank", i + 1);
				JPanel card = TsgHubUi.card();
				card.setLayout(new BorderLayout(8, 0));
				if (me) highlightSelf(card);
				if (started)
				{
					JLabel rankLabel = TsgHubUi.label(String.valueOf(rank), rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, FontManager.getRunescapeBoldFont());
					rankLabel.setPreferredSize(new Dimension(18, rankLabel.getPreferredSize().height));
					card.add(rankLabel, BorderLayout.WEST);
				}
				card.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(TsgHubUi.str(row, "displayName")), 110), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
				String gained = TsgHubUi.bool(row, "tracking") ? String.format("%,d", TsgHubUi.integer(row, "gained", 0)) : "-";
				if (started) card.add(TsgHubUi.label(gained, rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), BorderLayout.EAST);
				page.add(TsgHubUi.fitHeight(card));
				page.add(Box.createVerticalStrut(4));
			}
		}
		page.add(Box.createVerticalStrut(14));
		JButton leave = TsgHubUi.button("Leave competition");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> {
			int choice = JOptionPane.showConfirmDialog(this, "Leave \"" + TsgHubUi.eventName(event) + "\"?\nYou'll drop off the leaderboard on this device. You can rejoin while it's running.",
				"Leave competition", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.OK_OPTION) plugin.leaveEvent(TsgHubUi.str(event, "id"));
		});
		page.add(TsgHubUi.fitHeight(fullWidth(leave)));
		refreshPage();
	}

	private void showDropParty(JsonObject event)
	{
		setView(View.DROP_PARTY);
		setHeader(TsgHubUi.eventName(event), "Custom", true, false);
		page.removeAll();
		JsonObject config = TsgHubUi.eventConfig(event);
		Font small = FontManager.getRunescapeSmallFont();
		JPanel card = TsgHubUi.card();
		JPanel lines = TsgHubUi.stack();
		String countdown = TsgHubUi.capitalize(TsgHubUi.eventRelative(event));
		if (!countdown.isEmpty()) lines.add(TsgHubUi.label(countdown, "Ended".equals(countdown) ? TsgHubUi.MUTED : TsgHubUi.SUCCESS, FontManager.getRunescapeBoldFont()));
		lines.add(Box.createVerticalStrut(4));
		lines.add(detail("When", TsgHubUi.eventWhen(event)));
		if (TsgHubUi.integer(config, "world", 0) > 0) lines.add(detail("World", String.valueOf(TsgHubUi.integer(config, "world", 0))));
		if (!TsgHubUi.str(config, "location").isEmpty()) lines.add(detail("Where", TsgHubUi.str(config, "location")));
		if (!TsgHubUi.str(config, "host").isEmpty()) lines.add(detail("Host", TsgHubUi.str(config, "host")));
		JPanel prizes = TsgHubUi.prizeRow(event, small);
		if (prizes != null)
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(prizes);
		}
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

	void showParties()
	{
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
		applyMemberOrder(plugin.groups().currentGroup());
		if (view == View.GROUPS) renderGroups();
		else if (view == View.HOME) renderHome();
	}

	void showGroup(JsonObject group)
	{
		groupBusy = false;
		browsingParties = false;
		applyMemberOrder(group);
		if (view != View.GROUPS) setView(View.GROUPS);
		renderGroups();
	}

	void groupRefreshed(JsonObject group)
	{
		applyMemberOrder(group);
		if (view == View.GROUPS) renderGroups();
		else if (view == View.HOME) renderHome();
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
		partyAreasChanged();
	}

	void partyAreasChanged()
	{
		TsgHubGroups groups = plugin.groups();
		JsonObject current = groups.currentGroup();
		if (current == null || !TsgHubUi.str(current, "activity").isEmpty()) return;
		if (view == View.GROUPS && !browsingParties) setPartyHeader(current);
		else if (view == View.GROUPS) renderGroups();
		else if (view == View.HOME) renderHome();
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

	private void applyMemberOrder(JsonObject group)
	{
		if (group == null) return;
		JsonArray members = TsgHubUi.array(group, "members");
		List<String> names = new ArrayList<>();
		for (int i = 0; i < members.size(); i++) names.add(TsgHubUi.str(members.get(i).getAsJsonObject(), "displayName"));
		groupMembers.setOrder(names);
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

	private void setPartyHeader(JsonObject group)
	{
		int count = group == null ? 0 : TsgHubUi.array(group, "members").size();
		String members = count == 0 ? "" : count == 1 ? "Just you so far" : count + " members";
		if (group != null && TsgHubUi.bool(group, "locked")) members = members.isEmpty() ? "Locked" : members + " · Locked";
		setHeader(group == null ? "Your party" : currentTitle(group), members, true, true);
	}

	private void renderCurrentGroup(JsonObject group)
	{
		Font small = FontManager.getRunescapeSmallFont();
		setPartyHeader(group);

		groupsPage.add(groupMembers);
		groupsPage.add(Box.createVerticalStrut(10));

		JButton title = TsgHubUi.button("Set title");
		title.setToolTipText("Name the party. Leave blank to title it by location.");
		title.setEnabled(group != null);
		title.addActionListener(e -> promptTitle(group));
		if (plugin.groups().isLeader())
		{
			boolean locked = TsgHubUi.bool(group, "locked");
			JButton lock = TsgHubUi.button(locked ? "Unlock party" : "Lock party");
			lock.setToolTipText(locked ? "Let clanmates join again" : "Stop anyone else from joining");
			lock.addActionListener(e -> {
				lock.setEnabled(false);
				plugin.groups().setLocked(!locked);
			});
			JPanel leaderActions = new JPanel(new GridLayout(1, 2, 4, 0));
			leaderActions.setOpaque(false);
			leaderActions.add(title);
			leaderActions.add(lock);
			groupsPage.add(TsgHubUi.fitHeight(fullWidth(leaderActions)));
		}
		else groupsPage.add(TsgHubUi.fitHeight(fullWidth(title)));
		groupsPage.add(Box.createVerticalStrut(4));

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
		setHeader(current != null ? "Other parties" : "Parties", "", true, true);
		back.setToolTipText(current != null ? "Back to your party" : "Home");
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
		createGroupButton.setEnabled(!groupBusy);
		createGroupButton.setText(groupBusy ? "Working..." : "New party");
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
		boolean mine = current != null && TsgHubUi.str(group, "id").equals(TsgHubUi.str(current, "id"));
		boolean locked = !mine && TsgHubUi.bool(group, "locked");
		String title = mine ? currentTitle(group) : partyTitle(group);
		boolean showLock = TsgHubUi.bool(group, "locked");
		JLabel heading = TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(title) + "</b>", CARD_TITLE_W - (showLock ? 15 : 0)), TsgHubUi.TEXT, FontManager.getRunescapeFont());
		if (showLock)
		{
			heading.setIcon(new TsgHubUi.LockIcon());
			heading.setIconTextGap(5);
		}
		text.add(heading);
		text.add(Box.createVerticalStrut(2));
		Map.Entry<String, Integer> area = TsgHubUi.str(group, "activity").isEmpty() ? areaSummary(members) : null;
		String here = area != null && area.getValue() < members.size() ? " · " + area.getValue() + " here" : "";
		String meta = (members.size() == 1 ? "1 member" : members.size() + " members") + here + (world > 0 ? " · W" + world : "") + " · " + leader;
		text.add(TsgHubUi.label(meta, TsgHubUi.MUTED, FontManager.getRunescapeSmallFont()));
		text.add(TsgHubUi.wrapped(String.join(", ", names), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), CARD_TITLE_W));
		card.add(text, BorderLayout.CENTER);
		JPanel east = new JPanel(new BorderLayout());
		east.setOpaque(false);
		east.add(mine ? TsgHubUi.badge("Yours", TsgHubUi.SUCCESS)
			: locked ? new JLabel()
			: TsgHubUi.label(groupBusy ? "..." : current != null ? "Switch" : "Join", TsgHubUi.ACCENT, FontManager.getRunescapeSmallFont()), BorderLayout.NORTH);
		card.add(east, BorderLayout.EAST);
		if (locked)
		{
			card.setToolTipText("The leader has locked this party");
			return TsgHubUi.fitHeight(card);
		}
		card.setToolTipText(mine ? "Back to your party" : (current != null ? "Switch to " : "Join ") + title);
		TsgHubUi.clickable(card, () -> {
			if (mine)
			{
				browsingParties = false;
				renderGroups();
				return;
			}
			if (groupBusy) return;
			if (current != null && JOptionPane.showConfirmDialog(this,
				"Leave " + currentTitle(current) + " and join " + title + "?",
				"Switch parties", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
			groupBusy = true;
			groupError.setVisible(false);
			renderGroups();
			plugin.groups().join(TsgHubUi.str(group, "id"));
		});
		return TsgHubUi.fitHeight(card);
	}

	static String partyTitle(JsonObject group)
	{
		String title = TsgHubUi.str(group, "activity");
		if (!title.isEmpty()) return title;
		Map.Entry<String, Integer> area = areaSummary(TsgHubUi.array(group, "members"));
		if (area != null) return area.getKey();
		String leader = TsgHubUi.str(group, "leaderName");
		return leader.isEmpty() ? "Party" : leader + "'s party";
	}

	private String currentTitle(JsonObject group)
	{
		if (!TsgHubUi.str(group, "activity").isEmpty()) return partyTitle(group);
		Map.Entry<String, Integer> live = areaSummary(plugin.groups().liveAreas());
		return live != null ? live.getKey() : partyTitle(group);
	}

	static Map.Entry<String, Integer> areaSummary(JsonArray members)
	{
		List<String> areas = new ArrayList<>();
		for (int i = 0; i < members.size(); i++) areas.add(TsgHubUi.str(members.get(i).getAsJsonObject(), "area"));
		return areaSummary(areas);
	}

	static Map.Entry<String, Integer> areaSummary(List<String> areas)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		int low = Integer.MAX_VALUE;
		int high = 0;
		for (String area : areas)
		{
			if (area == null || area.isEmpty()) continue;
			if (area.startsWith(WILDERNESS))
			{
				try
				{
					int level = Integer.parseInt(area.substring(WILDERNESS.length()).trim());
					low = Math.min(low, level);
					high = Math.max(high, level);
					area = "Wilderness";
				}
				catch (NumberFormatException ignored) { }
			}
			counts.merge(area, 1, Integer::sum);
		}
		if (counts.isEmpty()) return null;
		Map.Entry<String, Integer> top = Collections.max(counts.entrySet(), Map.Entry.comparingByValue());
		String name = top.getKey();
		if (name.equals("Wilderness") && high > 0) name += " lvl " + (low == high ? String.valueOf(low) : low + "-" + high);
		return new java.util.AbstractMap.SimpleImmutableEntry<>(name, top.getValue());
	}

	private void promptTitle(JsonObject group)
	{
		Object input = JOptionPane.showInputDialog(this, "Party title (leave blank to title it by location)", "Set title",
			JOptionPane.PLAIN_MESSAGE, null, null, TsgHubUi.str(group, "activity"));
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
		if (current != null && JOptionPane.showConfirmDialog(this,
			"Leave " + currentTitle(current) + " and start a new party?",
			"Start a new party", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
		groupBusy = true;
		groupError.setVisible(false);
		renderGroups();
		plugin.groups().create();
	}

	private void setView(View next)
	{
		view = next;
		boardShowing = next == View.BOARD;
		membersWanted = next == View.MEMBERS || next == View.HOME;
		groupsWanted = next == View.GROUPS || next == View.HOME;
		dropsWanted = next == View.DROPS;
		if (next != View.COMPETITION) openCompetitionId = null;
		centerLayout.show(center, next == View.BOARD ? "board" : next == View.GROUPS ? "groups" : "page");
		footer.setVisible(next == View.HOME);
		back.setToolTipText(next == View.EVENTS || next == View.GROUPS || next == View.MEMBERS || next == View.DROPS ? "Home" : "Back to events");
	}

	private JPanel buildFooter()
	{
		JButton discord = TsgHubUi.iconButton(new TsgHubUi.DiscordIcon(), "Copy an invite to the clan Discord");
		discord.addActionListener(e -> {
			discord.setEnabled(false);
			plugin.copyDiscordInvite(() -> discord.setEnabled(true));
		});
		footer.setOpaque(false);
		footer.add(discord);
		footer.setVisible(false);
		return footer;
	}

	private void setEventHeader(JsonObject event, boolean showBack, boolean showRefresh)
	{
		String relative = TsgHubUi.eventRelative(event);
		setHeader(TsgHubUi.eventName(event), relative.isEmpty() ? TsgHubUi.statusLabel(TsgHubUi.str(event, "status")) : TsgHubUi.capitalize(relative), showBack, showRefresh);
		subtitle.setToolTipText(TsgHubUi.eventWhen(event));
	}

	private void setHeader(String titleText, String subtitleText, boolean showBack, boolean showRefresh)
	{
		this.titleText = titleText;
		title.setText(TsgHubUi.html(TsgHubUi.escape(titleText), titleWidth()));
		subtitle.setText(subtitleText);
		subtitle.setVisible(!subtitleText.isEmpty());
		subtitle.setToolTipText(null);
		back.setVisible(showBack);
		refresh.setVisible(showRefresh);
	}

	private void renderEvents()
	{
		String clan = plugin.getDetectedClanName();
		setHeader("Events", "", true, true);
		page.removeAll();
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
				.thenComparing(TsgHubUi::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
			if (visible.isEmpty())
			{
				page.add(errorPanel("No events yet", "When an admin creates an event, it will show up here."));
			}
			Font small = FontManager.getRunescapeSmallFont();
			String section = null;
			for (JsonObject event : visible)
			{
				String next = "active".equals(TsgHubUi.str(event, "status")) ? "Live" : "Upcoming";
				if (!next.equals(section))
				{
					JLabel heading = TsgHubUi.label(next.toUpperCase(), TsgHubUi.MUTED, small);
					heading.setBorder(BorderFactory.createEmptyBorder(section == null ? 0 : 6, 2, 4, 0));
					page.add(heading);
					section = next;
				}
				page.add(eventCard(event));
				page.add(Box.createVerticalStrut(6));
			}
		}
		refreshPage();
	}

	private JPanel eventCard(JsonObject event)
	{
		Font small = FontManager.getRunescapeSmallFont();
		boolean live = "active".equals(TsgHubUi.str(event, "status"));
		boolean joined = TsgHubUi.bool(event, "joined");
		JPanel card = TsgHubUi.card();
		if (joined) highlightSelf(card);

		JPanel top = TsgHubUi.row();
		top.add(TsgHubUi.shrinkable(TsgHubUi.label(TsgHubUi.eventName(event), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont())), BorderLayout.CENTER);
		top.add(TsgHubUi.label(TsgHubUi.eventCountdown(event), live ? TsgHubUi.SUCCESS : TsgHubUi.MUTED, small), BorderLayout.EAST);

		JPanel bottom = TsgHubUi.row();
		bottom.add(TsgHubUi.shrinkable(TsgHubUi.label(TsgHubUi.eventDetail(event), TsgHubUi.MUTED, small)), BorderLayout.CENTER);
		JPanel extras = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		extras.setOpaque(false);
		if (TsgHubUi.bool(event, "hidden")) extras.add(TsgHubUi.badge("Hidden", TsgHubUi.MUTED));
		JLabel prize = TsgHubUi.prizeLabel(event, small, plugin::getCoinImage);
		if (prize != null) extras.add(prize);
		bottom.add(extras, BorderLayout.EAST);

		JPanel text = TsgHubUi.stack();
		text.add(top);
		text.add(Box.createVerticalStrut(3));
		text.add(bottom);
		card.add(text, BorderLayout.CENTER);

		String type = TsgHubUi.str(event, "type");
		boolean dropParty = "drop-party".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		String action = dropParty ? "See when and where" : joined ? (competition ? "Open the leaderboard" : "Open your team's board")
			: competition ? "Join this competition" : "Join this event with a team code";
		card.setToolTipText("<html>" + TsgHubUi.escape(TsgHubUi.eventWhen(event)) + "<br>" + action + "</html>");
		TsgHubUi.clickable(card, () -> {
			String id = TsgHubUi.str(event, "id");
			if (dropParty) showDropParty(event);
			else if (competition && joined && !TsgHubSession.get("memberToken:" + id).isEmpty()) plugin.openCompetition(id, true);
			else if (competition) showCompetitionPreview(event);
			else if (joined) plugin.activateEvent(id);
			else showPreview(event);
		});
		return TsgHubUi.fitHeight(card);
	}

	private JPanel eventSummaryCard(JsonObject event, String subject, String goal)
	{
		Font small = FontManager.getRunescapeSmallFont();
		JPanel card = TsgHubUi.card();
		JPanel lines = TsgHubUi.stack();
		lines.add(TsgHubUi.shrinkable(TsgHubUi.label(subject, TsgHubUi.TEXT, FontManager.getRunescapeBoldFont())));
		lines.add(Box.createVerticalStrut(3));
		lines.add(TsgHubUi.wrapped(goal, TsgHubUi.TEXT, small, CARD_TEXT_W));
		lines.add(Box.createVerticalStrut(2));
		lines.add(TsgHubUi.wrapped(TsgHubUi.eventWhen(event), TsgHubUi.MUTED, small, CARD_TEXT_W));
		String description = TsgHubUi.str(event, "description").trim();
		if (!description.isEmpty())
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(TsgHubUi.wrapped(description, TsgHubUi.MUTED, small, CARD_TEXT_W));
		}
		JPanel prizes = TsgHubUi.prizeRow(event, small);
		if (prizes != null)
		{
			lines.add(Box.createVerticalStrut(6));
			lines.add(prizes);
		}
		card.add(lines, BorderLayout.CENTER);
		return TsgHubUi.fitHeight(card);
	}

	private void showPreview(JsonObject event)
	{
		previewEvent = event;
		setView(View.PREVIEW);
		setEventHeader(event, true, false);
		page.removeAll();
		page.add(eventSummaryCard(event, "Bingo", "Complete tasks with your team for points. Most points wins."));
		page.add(Box.createVerticalStrut(12));
		page.add(TsgHubUi.label("Team code", TsgHubUi.TEXT, FontManager.getRunescapeSmallFont()));
		page.add(Box.createVerticalStrut(4));
		codeField.setText("");
		page.add(TsgHubUi.fitHeight(codeField));
		joinError.setVisible(false);
		page.add(joinError);
		page.add(Box.createVerticalStrut(6));
		joinButton.setEnabled(true);
		joinButton.setText("Join event");
		page.add(TsgHubUi.fitHeight(fullWidth(joinButton)));
		page.add(Box.createVerticalStrut(6));
		page.add(TsgHubUi.wrapped("Ask an admin for your team's code. Your team can't be changed after you join.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W));
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
		setEventHeader(event, true, true);
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
		JPanel prizes = TsgHubUi.prizeRow(boardEvent, FontManager.getRunescapeSmallFont());
		if (prizes != null)
		{
			prizes.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			card.add(prizes, BorderLayout.SOUTH);
		}
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
				if (TsgHubUi.str(member, "displayName").equalsIgnoreCase(boardDisplayName)) name = "<font color='" + SELF_TEXT + "'>" + name + "</font>";
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
			if (mine) highlightSelf(card);
			JLabel rank = TsgHubUi.label(String.valueOf(i + 1), i == 0 ? TsgHubUi.ACCENT : TsgHubUi.MUTED, FontManager.getRunescapeBoldFont());
			rank.setPreferredSize(new Dimension(14, rank.getPreferredSize().height));
			card.add(rank, BorderLayout.WEST);
			JPanel text = TsgHubUi.stack();
			text.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(TsgHubUi.str(team, "name")), 120), TsgHubUi.TEXT, FontManager.getRunescapeFont()));
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
			if (mine) highlightSelf(card);
			card.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(TsgHubUi.str(team, "name")), CARD_TEXT_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
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
			if (name.equalsIgnoreCase(boardDisplayName)) highlightSelf(row);
			row.add(TsgHubUi.label(TsgHubUi.html(TsgHubUi.escape(name), CARD_TEXT_W), TsgHubUi.TEXT, FontManager.getRunescapeFont()), BorderLayout.CENTER);
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
			"Stop tracking \"" + TsgHubUi.eventName(boardEvent) + "\" on this device?\nYour team keeps its progress and you can rejoin with your team code.",
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
