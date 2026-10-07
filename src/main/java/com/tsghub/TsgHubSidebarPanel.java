package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.group.GroupMembersPanel;
import com.tsghub.group.data.PartyPlayer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;

final class TsgHubSidebarPanel extends PluginPanel
{
	private static final int TEXT_W = 215;
	private static final int CARD_TEXT_W = 185;
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
	private static final Set<String> COX_ITEMS = Set.of(
		"Dexterous prayer scroll", "Arcane prayer scroll", "Twisted buckler", "Dragon hunter crossbow",
		"Dinh's bulwark", "Ancestral hat", "Ancestral robe top", "Ancestral robe bottom", "Dragon claws",
		"Elder maul", "Kodai insignia", "Twisted bow", "Olmlet", "Metamorphic dust", "Twisted ancestral colour kit");
	private static final Set<String> TOB_ITEMS = Set.of(
		"Avernic defender hilt", "Ghrazi rapier", "Sanguinesti staff", "Justiciar faceguard", "Justiciar chestguard",
		"Justiciar legguards", "Scythe of vitur", "Lil' zik", "Sanguine dust", "Sanguine ornament kit", "Holy ornament kit");
	private static final Set<String> TOA_ITEMS = Set.of(
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

		TsgHubUi.plain(hideCompleted, TsgHubUi.MUTED).setFont(FontManager.getRunescapeSmallFont());
		hideCompleted.setSelected("true".equals(TsgHubSession.get("hideCompleted")));
		hideCompleted.addActionListener(e -> {
			TsgHubSession.set("hideCompleted", hideCompleted.isSelected() ? "true" : "");
			renderTasks();
		});

		showLoggedOut();
	}

	private JPanel buildHeader()
	{
		JPanel header = TsgHubUi.panel(new BorderLayout());
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
		titleRow.add(fillWidth(titles), BorderLayout.CENTER);

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
		JPanel refreshSlot = TsgHubUi.panel(new BorderLayout());
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

		header.add(below, BorderLayout.CENTER);
		header.setBorder(BorderFactory.createCompoundBorder(TsgHubUi.bottomRule(), BorderFactory.createEmptyBorder(0, 0, 6, 0)));
		return header;
	}

	private JPanel buildBoard()
	{
		JPanel board = TsgHubUi.panel(new BorderLayout(0, 6));
		JPanel display = TsgHubUi.panel(new BorderLayout());
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 3, 4, 0));
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
		showMessage(View.LOGGED_OUT, errorPanel("Not logged in", "Log in to see your clan's events and your team's progress."));
	}

	void showNotInClan(String hubClan, String detectedClan)
	{
		String body = detectedClan == null || detectedClan.isEmpty()
			? "TSG Hub is for members of the " + hubClan + " clan. Join the clan in game to see its events."
			: "TSG Hub is for members of the " + hubClan + " clan. This character is in " + detectedClan + ".";
		showMessage(View.NOT_IN_CLAN, errorPanel("For " + hubClan + " members", body));
	}

	void showCheckingClan()
	{
		showMessage(View.NOT_IN_CLAN, TsgHubUi.caption("Checking your clan..."));
	}

	void showSharingOff()
	{
		JButton enable = TsgHubUi.primaryButton("Enable sharing");
		enable.addActionListener(e -> plugin.enableSharing());
		showMessage(View.SHARING_OFF,
			TsgHubUi.label("Share your progress", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()),
			Box.createVerticalStrut(6),
			hint("TSG Hub tracks your boss kills, drops and raids for clan events. "
				+ "To do that it sends your RuneScape name, clan and rank, and that progress to your clan's event service."),
			Box.createVerticalStrut(4),
			hint("Nothing is ever posted in game chat."),
			Box.createVerticalStrut(10),
			TsgHubUi.fitHeight(enable),
			Box.createVerticalStrut(6),
			hint("You can turn this off anytime in the TSG Hub plugin settings."));
	}

	private void showMessage(View next, Component... parts)
	{
		setView(next);
		setHeader("TSG Hub", "", false, false);
		page.removeAll();
		for (Component part : parts) page.add(part);
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
			JPanel row = TsgHubUi.panel(new GridLayout(1, pair ? 2 : 1, 6, 0));
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
			page.add(TsgHubUi.fitHeight(notice));
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
		for (Component part : tile.getComponents())
		{
			((JComponent) part).setAlignmentX(CENTER_ALIGNMENT);
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

	void setMembers(JsonArray members)
	{
		this.members = members;
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
		setHeader("Members", "", true, true);
		page.removeAll();
		if (members == null)
		{
			page.add(TsgHubUi.caption("Loading members..."));
		}
		else if (members.size() == 0)
		{
			page.add(errorPanel("Nobody online", "Clanmates show up here while they're in clan chat with TSG Hub sharing on."));
		}
		else
		{
			int myWorld = 0;
			for (JsonObject member : TsgHubUi.objects(members))
				if (TsgHubUi.samePlayer(TsgHubUi.str(member, "displayName"), plugin.getDetectedPlayerName())) myWorld = TsgHubUi.integer(member, "world", 0);
			for (JsonObject member : TsgHubUi.objects(members))
			{
				page.add(memberCard(member, myWorld));
				page.add(Box.createVerticalStrut(6));
			}
		}
		page.add(Box.createVerticalStrut(8));
		page.add(hint(plugin.locationSharingEnabled() ? "Leave clan chat to hide yourself."
			: "You show as Online. Turn on location sharing in settings to show what you're doing."));
		refreshPage();
	}

	private static void highlightSelf(JPanel card)
	{
		card.setBackground(SELF_CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(SELF_BORDER),
			BorderFactory.createEmptyBorder(6, 7, 6, 7)));
	}

	private JPanel memberCard(JsonObject member, int myWorld)
	{
		Font small = FontManager.getRunescapeSmallFont();
		String name = TsgHubUi.str(member, "displayName");
		String activity = TsgHubUi.str(member, "activity");
		int world = TsgHubUi.integer(member, "world", 0);
		boolean self = TsgHubUi.samePlayer(name, plugin.getDetectedPlayerName());
		JPanel card = TsgHubUi.card();
		if (self) highlightSelf(card);

		JLabel nameLabel = TsgHubUi.shrinkable(TsgHubUi.label(name, TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
		String rank = TsgHubUi.str(member, "rank");
		BufferedImage rankIcon = plugin.presence().rankIcon(member);
		if (rankIcon != null)
		{
			nameLabel.setIcon(new ImageIcon(rankIcon));
			nameLabel.setIconTextGap(4);
		}
		if (!rank.isEmpty()) nameLabel.setToolTipText(rank);
		JPanel top = TsgHubUi.row();
		top.add(nameLabel, BorderLayout.CENTER);
		if (world > 0)
		{
			boolean sameWorld = !self && world == myWorld;
			JLabel worldLabel = TsgHubUi.label("W" + world, sameWorld ? TsgHubUi.SUCCESS : TsgHubUi.MUTED, small);
			if (sameWorld) worldLabel.setToolTipText("On your world");
			top.add(worldLabel, BorderLayout.EAST);
		}

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
		return TsgHubUi.fitHeight(card);
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
		setHeader("Drops", "", true, true);
		page.removeAll();
		if (drops == null)
		{
			page.add(TsgHubUi.caption("Loading drops..."));
		}
		else if (drops.size() == 0)
		{
			page.add(errorPanel("No drops yet", "Clan broadcasts for drops, raid loot, pets and collection log items show up here."));
		}
		else
		{
			Instant now = TsgHubUi.clock.instant();
			ZoneId zone = ZoneId.systemDefault();
			String day = null;
			for (int i = 0; i < drops.size(); i++)
			{
				JsonObject drop = drops.get(i).getAsJsonObject();
				String nextDay = dropDay(TsgHubUi.str(drop, "receivedAt"), now, zone);
				if (!nextDay.equals(day))
				{
					page.add(TsgHubUi.listHeading(nextDay, i == 0));
					day = nextDay;
				}
				page.add(dropCard(drop, now, zone));
				page.add(Box.createVerticalStrut(6));
			}
		}
		refreshPage();
	}

	private JPanel dropCard(JsonObject drop, Instant now, ZoneId zone)
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
		JPanel extras = TsgHubUi.panel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		for (String tag : dropTags(drop)) extras.add(TsgHubUi.badge(tag, TAG_COLORS.getOrDefault(tag, TAG_DEFAULT)));
		long value = drop.has("value") ? drop.get("value").getAsLong() : 0;
		if (value > 0) extras.add(TsgHubUi.label(TsgHubUi.formatGp(value), TsgHubUi.coinColor(value), small));
		card.add(fillWidth(TsgHubUi.twoLines(
			TsgHubUi.row(name, TsgHubUi.caption(dropWhen(TsgHubUi.str(drop, "receivedAt"), now, zone))),
			TsgHubUi.row(TsgHubUi.shrinkable(TsgHubUi.label(player, TsgHubUi.ACCENT, small)), extras))), BorderLayout.CENTER);
		return TsgHubUi.fitHeight(card);
	}

	private static JPanel fillWidth(Component content)
	{
		JPanel slot = TsgHubUi.panel(new GridBagLayout());
		GridBagConstraints fill = new GridBagConstraints();
		fill.weightx = 1;
		fill.fill = GridBagConstraints.HORIZONTAL;
		slot.add(content, fill);
		return slot;
	}

	private JLabel dropIcon(int itemId)
	{
		if (itemId <= 0) return null;
		AsyncBufferedImage image = plugin.getItemImage(itemId);
		if (image == null) return null;
		JLabel label = new JLabel();
		label.setVerticalAlignment(JLabel.CENTER);
		label.setPreferredSize(new Dimension(DROP_ICON_W, 32));
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

	static String dropWhen(String iso, Instant now, ZoneId zone)
	{
		String day = dropDay(iso, now, zone);
		if ("Earlier".equals(day)) return "";
		if ("Today".equals(day)) return dropAge(iso, now);
		return Instant.parse(iso).atZone(zone).format(CLOCK_TIME);
	}

	static String dropDay(String iso, Instant now, ZoneId zone)
	{
		Instant then;
		try { then = Instant.parse(iso); }
		catch (Exception e) { return "Earlier"; }
		LocalDate day = then.atZone(zone).toLocalDate();
		LocalDate today = now.atZone(zone).toLocalDate();
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
		JsonObject config = TsgHubUi.eventConfig(event);
		boolean skill = "skill".equals(TsgHubUi.str(event, "type"));
		String subject = skill ? TsgHubUi.skillName(TsgHubUi.str(config, "skill")) : TsgHubUi.str(config, "npcName");
		page.add(eventSummaryCard(event, subject, skill ? "Most " + subject + " XP gained wins." : "Most " + subject + " kills wins."));
		page.add(Box.createVerticalStrut(12));
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		for (ActionListener l : competitionJoinButton.getActionListeners()) competitionJoinButton.removeActionListener(l);
		competitionJoinButton.addActionListener(e -> {
			competitionJoinButton.setEnabled(false);
			competitionJoinButton.setText("Joining...");
			joinError.setVisible(false);
			plugin.participate(TsgHubUi.str(event, "id"));
		});
		page.add(TsgHubUi.fitHeight(competitionJoinButton));
		joinError.setVisible(false);
		page.add(joinError);
		page.add(Box.createVerticalStrut(6));
		page.add(hint(skill
			? "Your XP counts from the first time TSG Hub sees you after joining. Gains on other devices count the next time you log in with it."
			: !"loot".equals(TsgHubUi.str(config, "signal"))
				? "Kills count from your kill count message, so kills while TSG Hub isn't running still count at your next kill."
				: "Each kill counts when its loot drops while TSG Hub is running."));
		refreshPage();
	}

	void competitionJoinFailed(String message)
	{
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		showError(joinError, message);
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
		for (JsonObject row : TsgHubUi.objects(rows)) if (TsgHubUi.str(row, "displayName").equalsIgnoreCase(competitionName)) mine = row;

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
			lines.add(cardNote(start.isEmpty() ? "Counting starts when the event begins." : "Counting starts " + start + "."));
		}
		else
		{
			lines.add(TsgHubUi.label("+" + String.format("%,d", mine == null ? 0 : TsgHubUi.integer(mine, "gained", 0)) + " " + unit(), TsgHubUi.SUCCESS, FontManager.getRunescapeBoldFont()));
			if (!tracking)
			{
				lines.add(Box.createVerticalStrut(2));
				lines.add(cardNote(skill ? "Starts counting the next time you gain XP." : "Starts counting at your next kill."));
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
			page.add(TsgHubUi.caption(TsgHubUi.integer(event, "participants", rows.size()) + (started ? " taking part" : " signed up")));
			page.add(Box.createVerticalStrut(4));
			for (int i = 0; i < rows.size(); i++)
			{
				JsonObject row = rows.get(i).getAsJsonObject();
				boolean me = TsgHubUi.str(row, "displayName").equalsIgnoreCase(competitionName);
				int rank = TsgHubUi.integer(row, "rank", i + 1);
				JPanel card = TsgHubUi.card();
				card.setLayout(new BorderLayout(8, 0));
				if (me) highlightSelf(card);
				if (started) card.add(TsgHubUi.rankLabel(rank, FontManager.getRunescapeBoldFont(), 18), BorderLayout.WEST);
				card.add(TsgHubUi.wrapped(TsgHubUi.str(row, "displayName"), TsgHubUi.TEXT, FontManager.getRunescapeFont(), 110), BorderLayout.CENTER);
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
		page.add(TsgHubUi.fitHeight(leave));
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
			page.add(hint(notes));
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
		showError(joinError, message);
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
		showError(groupError, message);
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
		List<String> names = new ArrayList<>();
		for (JsonObject member : TsgHubUi.objects(TsgHubUi.array(group, "members"))) names.add(TsgHubUi.str(member, "displayName"));
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
			JPanel leaderActions = TsgHubUi.panel(new GridLayout(1, 2, 4, 0));
			leaderActions.add(title);
			leaderActions.add(lock);
			groupsPage.add(TsgHubUi.fitHeight(leaderActions));
		}
		else groupsPage.add(TsgHubUi.fitHeight(title));
		groupsPage.add(Box.createVerticalStrut(4));

		String currentId = group == null ? "" : TsgHubUi.str(group, "id");
		int others = 0;
		if (groupList != null)
			for (int i = 0; i < groupList.size(); i++)
				if (!TsgHubUi.str(groupList.get(i).getAsJsonObject(), "id").equals(currentId)) others++;
		JPanel actions = TsgHubUi.panel(new GridLayout(1, 2, 4, 0));
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
		groupsPage.add(TsgHubUi.fitHeight(actions));
		groupsPage.add(groupError);
	}

	private void renderGroupList(JsonObject current)
	{
		setHeader(current != null ? "Other parties" : "Parties", "", true, true);
		back.setToolTipText(current != null ? "Back to your party" : "Home");
		if (groupList == null)
		{
			groupsPage.add(TsgHubUi.caption("Loading parties..."));
		}
		else if (groupList.size() == 0)
		{
			groupsPage.add(errorPanel("No parties yet", "Start one below. Clanmates can join it with one click, no code needed."));
		}
		else
		{
			for (JsonObject group : TsgHubUi.objects(groupList))
			{
				groupsPage.add(groupCard(group, current));
				groupsPage.add(Box.createVerticalStrut(5));
			}
		}

		groupsPage.add(Box.createVerticalStrut(10));
		createGroupButton.setEnabled(!groupBusy);
		createGroupButton.setText(groupBusy ? "Working..." : "New party");
		groupsPage.add(TsgHubUi.fitHeight(createGroupButton));
		groupsPage.add(groupError);
		groupsPage.add(Box.createVerticalStrut(8));
		groupsPage.add(hint(current != null ? "Joining or starting another party leaves your current one."
			: "Party members see each other's health, prayer, gear, inventory and skills while they're in the party."));
		if (plugin.groups().inOtherParty())
		{
			groupsPage.add(Box.createVerticalStrut(4));
			groupsPage.add(TsgHubUi.wrapped("You're in a RuneLite party from outside TSG Hub. Joining or starting a party here leaves it.", TsgHubUi.WARNING, FontManager.getRunescapeSmallFont(), TEXT_W));
		}
	}

	private JPanel groupCard(JsonObject group, JsonObject current)
	{
		JsonArray members = TsgHubUi.array(group, "members");
		String leader = TsgHubUi.str(group, "leaderName");
		int world = 0;
		List<String> names = new ArrayList<>();
		for (JsonObject member : TsgHubUi.objects(members))
		{
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
		text.add(TsgHubUi.caption(meta));
		text.add(TsgHubUi.wrapped(String.join(", ", names), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), CARD_TITLE_W));
		card.add(text, BorderLayout.CENTER);
		card.add(TsgHubUi.north(mine ? TsgHubUi.badge("Yours", TsgHubUi.SUCCESS)
			: locked ? new JLabel()
			: TsgHubUi.label(groupBusy ? "..." : current != null ? "Switch" : "Join", TsgHubUi.ACCENT, FontManager.getRunescapeSmallFont())), BorderLayout.EAST);
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
		for (JsonObject member : TsgHubUi.objects(members)) areas.add(TsgHubUi.str(member, "area"));
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
		return new AbstractMap.SimpleImmutableEntry<>(name, top.getValue());
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
			page.add(TsgHubUi.caption("Loading events..."));
		}
		else
		{
			List<JsonObject> visible = TsgHubUi.objects(events);
			visible.removeIf(event -> "ended".equals(TsgHubUi.str(event, "status")));
			visible.sort(Comparator
				.comparing((JsonObject e) -> !TsgHubUi.bool(e, "joined"))
				.thenComparing(e -> !"active".equals(TsgHubUi.str(e, "status")))
				.thenComparing(TsgHubUi::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
			if (visible.isEmpty())
			{
				page.add(errorPanel("No events yet", "When an admin creates an event, it will show up here."));
			}
			String section = null;
			for (JsonObject event : visible)
			{
				String next = "active".equals(TsgHubUi.str(event, "status")) ? "Live" : "Upcoming";
				if (!next.equals(section))
				{
					page.add(TsgHubUi.listHeading(next, section == null));
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
		boolean joined = TsgHubUi.bool(event, "joined");
		JPanel card = TsgHubUi.card();
		if (joined) highlightSelf(card);
		card.add(TsgHubUi.eventLines(event, plugin::getCoinImage), BorderLayout.CENTER);

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
		lines.add(cardNote(TsgHubUi.eventWhen(event)));
		String description = TsgHubUi.str(event, "description").trim();
		if (!description.isEmpty())
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(cardNote(description));
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
		page.add(TsgHubUi.fitHeight(joinButton));
		page.add(Box.createVerticalStrut(6));
		page.add(hint("Ask an admin for your team's code. Your team can't be changed after you join."));
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
		JPanel row = TsgHubUi.row();
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
		for (JsonObject task : TsgHubUi.objects(tasks))
		{
			boolean completed = TsgHubUi.bool(TsgHubUi.progressFor(progressRows, TsgHubUi.str(task, "id")), "completed");
			(completed ? done : open).add(task);
		}

		JPanel controls = TsgHubUi.panel(new BorderLayout());
		controls.add(hideCompleted, BorderLayout.WEST);
		JLabel left = TsgHubUi.caption(open.size() + " left");
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
		JLabel name = TsgHubUi.label(TsgHubUi.html("<b>" + TsgHubUi.escape(TsgHubUi.str(task, "title")) + "</b>", CARD_TITLE_W - (completed ? 18 : 0)),
			completed ? TsgHubUi.MUTED : TsgHubUi.TEXT, FontManager.getRunescapeFont());
		if (completed) name.setIcon(new TsgHubUi.CheckIcon());
		card.add(TsgHubUi.row(name, TsgHubUi.north(TsgHubUi.badge(TsgHubUi.integer(task, "points", 1) + " pts", completed ? TsgHubUi.MUTED : TsgHubUi.ACCENT))), BorderLayout.NORTH);

		JPanel body = TsgHubUi.stack();
		Font small = FontManager.getRunescapeSmallFont();
		body.add(TsgHubUi.caption(TsgHubUi.taskTypeLabel(task) + (individual ? " · Everyone" : solo ? " · Solo" : " · Team")));
		String description = TsgHubUi.str(task, "description").trim();
		if (!description.isEmpty() && !completed) body.add(cardNote(description));

		int value = TsgHubUi.integer(progress, "progress", 0);
		int target = Math.max(1, TsgHubUi.integer(progress, "target", 1));
		if (completed)
		{
			boolean creditedToOrganizer = TsgHubUi.bool(progress, "override") && !TsgHubUi.bool(progress, "overrideCredited");
			body.add(TsgHubUi.label(creditedToOrganizer ? "Marked complete by an admin" : TsgHubUi.completedLine(progress), TsgHubUi.SUCCESS, small));
		}
		else if (addSetProgress(body, individual || solo ? myEntry(progress) : progress, individual || solo))
		{
			if (individual) body.add(TsgHubUi.caption(value + " of " + target + " teammates done"));
			if (solo) addSoloLeader(body, progress);
		}
		else if (solo)
		{
			JsonObject mine = myEntry(progress);
			int have = mine == null ? 0 : TsgHubUi.integer(mine, "progress", 0);
			if (target > 1) addBar(body, have, target, "You: " + have + "/" + target);
			addSoloLeader(body, progress);
		}
		else if (individual)
		{
			JsonObject mine = myEntry(progress);
			int have = mine == null ? 0 : TsgHubUi.integer(mine, "progress", 0);
			int need = mine == null ? 1 : TsgHubUi.integer(mine, "target", 1);
			if (mine != null && need > 1 && !TsgHubUi.bool(mine, "completed")) addBar(body, have, need, "You: " + have + "/" + need);
			else if (mine != null && TsgHubUi.bool(mine, "completed")) body.add(TsgHubUi.label("You're done", TsgHubUi.SUCCESS, small));
			body.add(TsgHubUi.caption(value + " of " + target + " teammates done"));
		}
		else if (target > 1) addBar(body, value, target, value + " / " + target);
		boolean setTile = TsgHubUi.array(progress, "alternatives").size() > 0 || progress.has("items");
		if (!individual && !solo && (!setTile || completed))
		{
			String amounts = TsgHubUi.contributorsText(progress, " · ", 4);
			if (!amounts.isEmpty()) body.add(cardNote(amounts));
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
			body.add(TsgHubUi.fitHeight(submit));
			return;
		}
		body.add(cardNote("Add a note or link for the admins."));
		body.add(Box.createVerticalStrut(3));
		body.add(TsgHubUi.fitHeight(manualNote));
		body.add(Box.createVerticalStrut(4));
		JPanel buttons = TsgHubUi.panel(new GridLayout(1, 2, 4, 0));
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
		for (ActionListener listener : manualNote.getActionListeners()) manualNote.removeActionListener(listener);
		manualNote.addActionListener(e -> doSend.run());
		buttons.add(cancel);
		buttons.add(send);
		body.add(TsgHubUi.fitHeight(buttons));
	}

	private boolean addSetProgress(JPanel body, JsonObject source, boolean mine)
	{
		if (source == null || TsgHubUi.array(source, "alternatives").size() == 0 && !source.has("items")) return false;
		if (TsgHubUi.bool(source, "completed")) return true;
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
			if (!set.found.isEmpty()) body.add(cardNote("Have: " + String.join(", ", set.found)));
			if (!set.needed.isEmpty()) body.add(cardNote("Need: " + String.join(", ", set.needed)));
		}
		int notStarted = sets.size() - shown.size();
		if (notStarted > 0) body.add(TsgHubUi.caption("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started"));
		return true;
	}

	private static void addLineSpacing(JPanel body)
	{
		Component[] parts = body.getComponents();
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
			List<JsonObject> sorted = TsgHubUi.objects(members);
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
		body.add(TsgHubUi.caption("Leader: " + leader + " (" + TsgHubUi.integer(progress, "progress", 0) + "/" + TsgHubUi.integer(progress, "target", 1) + ")"));
	}

	private JsonObject myEntry(JsonObject progress)
	{
		for (JsonObject member : TsgHubUi.objects(TsgHubUi.array(progress, "members")))
			if (TsgHubUi.str(member, "displayName").equalsIgnoreCase(boardDisplayName)) return member;
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
			card.add(TsgHubUi.rankLabel(i + 1, FontManager.getRunescapeBoldFont(), 14), BorderLayout.WEST);
			JPanel text = TsgHubUi.stack();
			text.add(TsgHubUi.wrapped(TsgHubUi.str(team, "name"), TsgHubUi.TEXT, FontManager.getRunescapeFont(), 120));
			text.add(TsgHubUi.caption(TsgHubUi.integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks"));
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
		List<JsonObject> teams = TsgHubUi.objects(TsgHubUi.array(boardEvent, "teams"));
		teams.sort((a, b) -> TsgHubUi.str(a, "name").compareToIgnoreCase(TsgHubUi.str(b, "name")));
		String ownTeam = ownTeamId();
		for (JsonObject team : teams)
		{
			boolean mine = TsgHubUi.str(team, "id").equals(ownTeam);
			JPanel card = TsgHubUi.card();
			if (mine) highlightSelf(card);
			card.add(TsgHubUi.wrapped(TsgHubUi.str(team, "name"), TsgHubUi.TEXT, FontManager.getRunescapeFont(), CARD_TEXT_W), BorderLayout.CENTER);
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
		for (JsonObject member : TsgHubUi.objects(TsgHubUi.array(boardEvent, "members")))
		{
			if (!ownTeam.isEmpty() && TsgHubUi.str(member, "teamId").equals(ownTeam)) teammates.add(TsgHubUi.str(member, "displayName"));
		}
		teammates.sort(String.CASE_INSENSITIVE_ORDER);
		teamTab.add(TsgHubUi.caption(teammates.size() == 1 ? "1 member" : teammates.size() + " members"));
		teamTab.add(Box.createVerticalStrut(4));
		for (String name : teammates)
		{
			JPanel row = TsgHubUi.card();
			if (name.equalsIgnoreCase(boardDisplayName)) highlightSelf(row);
			row.add(TsgHubUi.wrapped(name, TsgHubUi.TEXT, FontManager.getRunescapeFont(), CARD_TEXT_W), BorderLayout.CENTER);
			teamTab.add(TsgHubUi.fitHeight(row));
			teamTab.add(Box.createVerticalStrut(3));
		}
		teamTab.add(Box.createVerticalStrut(14));
		JButton leave = TsgHubUi.button("Disconnect from event");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> confirmLeave());
		teamTab.add(TsgHubUi.fitHeight(leave));
		teamTab.add(Box.createVerticalStrut(3));
		teamTab.add(hint("Stops tracking on this device. Your team keeps its progress and you can rejoin with the same code."));
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

	private void addBar(JPanel body, int value, int max, String label)
	{
		ProgressBar bar = progressBar(value, max);
		bar.setCenterLabel(label);
		body.add(Box.createVerticalStrut(4));
		body.add(bar);
		body.add(Box.createVerticalStrut(4));
	}

	private ProgressBar progressBar(int value, int max)
	{
		ProgressBar bar = new ProgressBar();
		bar.setMaximumValue(Math.max(1, max));
		bar.setValue(Math.min(value, Math.max(1, max)));
		bar.setBackground(TsgHubUi.BACKGROUND);
		bar.setForeground(value >= max ? TsgHubUi.SUCCESS.darker() : TsgHubUi.ACCENT.darker().darker());
		// ProgressBar passes its fill color to labels; keep text white.
		for (Component child : bar.getComponents()) if (child instanceof JLabel) child.setForeground(Color.WHITE);
		bar.setPreferredSize(new Dimension(100, 16));
		return TsgHubUi.fitHeight(bar);
	}

	private JPanel errorPanel(String heading, String body)
	{
		return TsgHubUi.emptyState(heading, body, TEXT_W - 20);
	}

	private static JLabel hint(String text)
	{
		return TsgHubUi.wrapped(text, TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), TEXT_W);
	}

	private static JLabel cardNote(String text)
	{
		return TsgHubUi.wrapped(text, TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), CARD_TEXT_W);
	}

	private static void showError(JLabel label, String message)
	{
		label.setText(TsgHubUi.html(TsgHubUi.escape(message), TEXT_W));
		label.setVisible(true);
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
