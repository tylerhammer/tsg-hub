package com.tsghub;

import static com.tsghub.TsgHubUi.*;

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
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;

final class TsgHubSidebarPanel extends PluginPanel
{
	private static final int TEXT_W = 215;
	private static final int CARD_TEXT_W = 185;
	private static final String ALT_RANK = "Gnome Child";
	private static final int MAX_WARNING_LENGTH = 300;
	private static final int MAX_PAST_WARNINGS = 3;
	private static final int[] EXPIRY_DAYS = {30, 90, 0};
	private static final Color TIP_CARD = new Color(42, 42, 42);
	private static final Color DIM = new Color(106, 106, 106);
	private static final Color DIM_BAR = new Color(68, 68, 68);
	private static final DateTimeFormatter WARNING_DAY = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH);
	private static final DateTimeFormatter WARNING_DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH);
	private static final int CARD_TITLE_W = 150;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

	private enum View { LOGGED_OUT, NOT_IN_CLAN, SHARING_OFF, HOME, EVENTS, PREVIEW, BOARD, COMPETITION_PREVIEW, COMPETITION, DROP_PARTY, GROUPS, MEMBERS, DROPS }

	private final TsgHubPlugin plugin;
	private final JButton back = iconButton(new BackIcon(), "Back to events");
	private final JLabel title = boldLabel("TSG Hub");
	private final JLabel subtitle = caption(" ");
	private String titleText = "TSG Hub";
	private final RefreshIcon refreshIcon = new RefreshIcon();
	private final JButton refresh = iconButton(refreshIcon, "Refresh");
	private final JButton organizer = iconButton(new OrganizerIcon(), "Admin tools");
	private final JButton settings = iconButton(new CogIcon(), "Plugin settings");
	private final JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
	private final StatusLine status = new StatusLine(TEXT_W);

	private final CardLayout centerLayout = new CardLayout();
	private final JPanel center = new JPanel(centerLayout);
	private final WidthTrackingPanel page = new WidthTrackingPanel();

	private final JPanel boardSummary = stack();
	private final WidthTrackingPanel tasksTab = new WidthTrackingPanel();
	private final WidthTrackingPanel scoreboardTab = new WidthTrackingPanel();
	private final WidthTrackingPanel teamTab = new WidthTrackingPanel();
	private final JCheckBox hideCompleted = new JCheckBox("Hide completed");
	private final JTextField manualNote = new JTextField();

	private final JTextField codeField = new JTextField();
	private final JButton joinButton = primaryButton("Join event");
	private final JLabel joinError = label("", TsgHubUi.ERROR, smallFont());

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
	private final JButton competitionJoinButton = primaryButton("Join");

	private final JPanel titleRow = new JPanel(new BorderLayout(4, 0));
	private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
	private final List<Section> sections;
	private final WidthTrackingPanel groupsPage = new WidthTrackingPanel();
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
	private final JButton createGroupButton = primaryButton("New party");
	private final JLabel groupError = label("", TsgHubUi.ERROR, smallFont());
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
			new Section("Events", "Clan events and your team's board", new CalendarIcon(), this::openEvents, this::eventsSummary, () -> liveEvents() > 0),
			new Section("Parties", "Join a clanmate's party or start one", new PartyIcon(), this::showParties, this::partiesSummary, () -> plugin.groups().inGroup()),
			new Section("Members", "See what clanmates are up to", new MembersIcon(), this::showMembers, this::membersSummary, () -> onlineCount() > 0),
			new Section("Drops", "Recent big drops across the clan", new DropsIcon(), this::showDrops, () -> "", () -> false));
		setLayout(new BorderLayout(0, 6));
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);

		center.setOpaque(false);
		center.add(scroll(page), "page");
		center.add(buildBoard(), "board");
		center.add(scroll(groupsPage), "groups");
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

		plain(showOffline, MUTED).setFont(smallFont());
		showOffline.setSelected("true".equals(TsgHubSession.get("showOffline")));
		showOffline.addActionListener(e -> {
			TsgHubSession.set("showOffline", showOffline.isSelected() ? "true" : "");
			renderMembers();
		});

		plain(hideCompleted, MUTED).setFont(smallFont());
		hideCompleted.setSelected("true".equals(TsgHubSession.get("hideCompleted")));
		hideCompleted.addActionListener(e -> {
			TsgHubSession.set("hideCompleted", hideCompleted.isSelected() ? "true" : "");
			renderTasks();
		});

		showLoggedOut();
	}

	private JPanel buildHeader()
	{
		JPanel header = panel(new BorderLayout());
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

		JPanel titles = stack();
		titles.add(title);
		titles.add(shrinkable(subtitle));
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
			else if (view == View.COMPETITION && competitionEvent != null) plugin.openCompetition(str(competitionEvent, "id"));
			else plugin.loadClanEvents();
		});
		JPanel refreshSlot = panel(new BorderLayout());
		refreshSlot.setPreferredSize(refresh.getPreferredSize());
		refreshSlot.add(refresh);
		settings.addActionListener(e -> plugin.openSettings());
		actions.add(organizer);
		actions.add(refreshSlot);
		actions.add(settings);
		titleRow.add(actions, BorderLayout.EAST);

		JPanel below = stack();
		below.add(titleRow);
		below.add(status);

		header.add(below, BorderLayout.CENTER);
		header.setBorder(BorderFactory.createCompoundBorder(bottomRule(), BorderFactory.createEmptyBorder(0, 0, 6, 0)));
		return header;
	}

	private JPanel buildBoard()
	{
		JPanel board = panel(new BorderLayout(0, 6));
		JPanel display = panel(new BorderLayout());
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 3, 4, 0));
		tabs.setOpaque(false);
		MaterialTab tasks = new MaterialTab("Tasks", tabs, scroll(tasksTab));
		tabs.addTab(tasks);
		tabs.addTab(new MaterialTab("Scores", tabs, scroll(scoreboardTab)));
		tabs.addTab(new MaterialTab("Team", tabs, scroll(teamTab)));
		tabs.select(tasks);

		JPanel north = stack();
		north.add(boardSummary);
		north.add(Box.createVerticalStrut(6));
		north.add(fitHeight(tabs));
		board.add(north, BorderLayout.NORTH);
		board.add(display, BorderLayout.CENTER);
		return board;
	}

	void setOrganizerAccess(boolean allowed)
	{
		organizer.setVisible(allowed);
		title.setText(html(escape(titleText), titleWidth()));
		if (view == View.MEMBERS) renderMembers();
	}

	private int titleWidth()
	{
		return 150 - (organizer.isVisible() ? organizer.getPreferredSize().width + 2 : 0);
	}

	void setStatus(String message, Tone tone)
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
		showMessage(View.NOT_IN_CLAN, caption("Checking your clan..."));
	}

	void showSharingOff()
	{
		JButton enable = primaryButton("Enable sharing");
		enable.addActionListener(e -> plugin.enableSharing());
		showMessage(View.SHARING_OFF,
			boldLabel("Share your progress"),
			Box.createVerticalStrut(6),
			hint("TSG Hub tracks your boss kills, drops and raids for clan events. "
				+ "To do that it sends your RuneScape name, clan and rank, and that progress to your clan's event service."),
			Box.createVerticalStrut(4),
			hint("Nothing is ever posted in game chat."),
			Box.createVerticalStrut(10),
			fitHeight(enable),
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
			JPanel row = panel(new GridLayout(1, pair ? 2 : 1, 6, 0));
			row.add(sectionTile(sections.get(i), pair));
			if (pair) row.add(sectionTile(sections.get(i + 1), true));
			page.add(fitHeight(row));
			page.add(Box.createVerticalStrut(6));
		}
		if (update != null)
		{
			page.add(Box.createVerticalStrut(4));
			JLabel notice = label("Update available. Restart RuneLite.", WARNING, smallFont());
			notice.setHorizontalAlignment(JLabel.CENTER);
			notice.setToolTipText("TSG Hub " + update + " is available. Restart RuneLite to update.");
			page.add(fitHeight(notice));
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
		tile.setBackground(CARD);
		tile.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 6));
		JLabel icon = new JLabel(section.icon);
		JLabel name = boldLabel(section.name);
		JLabel summary = label(html("<div style='text-align:center'>" + escape(section.summary.get()) + "</div>", half ? 80 : 180),
			section.live.getAsBoolean() ? SUCCESS : MUTED, smallFont());
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
		clickable(tile, section.open);
		return tile;
	}

	private int liveEvents()
	{
		int live = 0;
		if (events != null)
			for (int i = 0; i < events.size(); i++)
				if ("active".equals(str(events.get(i).getAsJsonObject(), "status"))) live++;
		return live;
	}

	private String eventsSummary()
	{
		if (events == null) return "Loading...";
		int live = liveEvents();
		int upcoming = -live;
		for (int i = 0; i < events.size(); i++)
			if (!"ended".equals(str(events.get(i).getAsJsonObject(), "status"))) upcoming++;
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
		setHeader("Members", "", true, true);
		page.removeAll();
		if (members == null)
		{
			page.add(caption("Loading members..."));
			refreshPage();
			return;
		}
		page.add(sectionHeading("Online (" + members.size() + ")", null));
		if (members.size() == 0) page.add(hint("Nobody online right now."));
		int myWorld = 0;
		for (JsonObject member : objects(members))
			if (samePlayer(str(member, "displayName"), plugin.getDetectedPlayerName())) myWorld = integer(member, "world", 0);
		for (JsonObject member : objects(members))
		{
			page.add(memberCard(member, myWorld));
			page.add(Box.createVerticalStrut(6));
		}
		if (offlineMembers.size() > 0)
		{
			page.add(Box.createVerticalStrut(4));
			page.add(sectionHeading("Offline (" + offlineMembers.size() + ")", showOffline));
			if (showOffline.isSelected())
			{
				for (JsonObject member : objects(offlineMembers))
				{
					page.add(offlineRow(member));
					page.add(Box.createVerticalStrut(3));
				}
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

	private JPanel sectionHeading(String text, JComponent right)
	{
		JPanel row = row();
		row.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
		row.add(label(text.toUpperCase(), MUTED, smallFont()), BorderLayout.CENTER);
		if (right != null) row.add(right, BorderLayout.EAST);
		return fitHeight(row);
	}

	private JLabel memberName(JsonObject member, String text, Color color, Font font)
	{
		JLabel label = shrinkable(label(text, color, font));
		BufferedImage rankIcon = plugin.presence().rankIcon(member);
		if (rankIcon != null)
		{
			label.setIcon(new ImageIcon(rankIcon));
			label.setIconTextGap(4);
		}
		return label;
	}

	private void setMemberTip(JComponent card, JsonObject member, String detail, boolean sameWorld, String seen, String note)
	{
		String tip = rosterTooltip(str(member, "rank"), str(member, "altOf"), array(member, "alts"), detail, sameWorld, seen, note,
			visibleWarnings(member), clock.instant(), ZoneId.systemDefault());
		if (!tip.isEmpty()) card.setToolTipText(tip);
	}

	private void addMemberBadges(JPanel badges, JsonObject member, String note, boolean more)
	{
		boolean warned = hasActiveWarning(visibleWarnings(member));
		if (warned)
		{
			JLabel warning = new JLabel(new WarningIcon());
			if (!note.isEmpty() || more) warning.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			badges.add(warning);
		}
		if (!note.isEmpty())
		{
			JLabel noteLabel = new JLabel(new NoteIcon());
			if (more) noteLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			badges.add(noteLabel);
		}
	}

	private JPanel memberCard(JsonObject member, int myWorld)
	{
		Font small = smallFont();
		String name = str(member, "displayName");
		String activity = str(member, "activity");
		String note = visibleNote(member);
		int world = integer(member, "world", 0);
		boolean self = samePlayer(name, plugin.getDetectedPlayerName());
		JPanel card = card();
		if (self) highlightSelf(card);

		JPanel top = row();
		top.add(memberName(member, name, TEXT, boldFont()), BorderLayout.CENTER);
		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		badges.setOpaque(false);
		boolean sameWorld = !self && world > 0 && world == myWorld;
		addMemberBadges(badges, member, note, world > 0);
		if (world > 0) badges.add(label("W" + world, sameWorld ? SUCCESS : MUTED, small));
		if (badges.getComponentCount() > 0) top.add(badges, BorderLayout.EAST);

		JPanel text = stack();
		text.add(top);
		String detail = activityDetail(activity, str(member, "area"));
		if (!detail.isEmpty())
		{
			boolean active = !"Idle".equals(activity) && !"Online".equals(activity) && !activity.isEmpty();
			JLabel detailLabel = shrinkable(label(detail, active ? SUCCESS : MUTED, small));
			JPanel bottom = row();
			bottom.add(detailLabel, BorderLayout.CENTER);
			text.add(Box.createVerticalStrut(3));
			text.add(bottom);
		}
		card.add(text, BorderLayout.CENTER);
		setMemberTip(card, member, detail, sameWorld, "", note);
		addNoteMenu(card, member);
		return fitHeight(card);
	}

	private JPanel offlineRow(JsonObject member)
	{
		Font small = smallFont();
		String note = visibleNote(member);
		String seen = lastSeen(str(member, "lastSeenAt"), clock.instant());
		JPanel row = card();
		row.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
		row.add(memberName(member, str(member, "displayName"), MUTED, small), BorderLayout.CENTER);
		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		badges.setOpaque(false);
		addMemberBadges(badges, member, note, !seen.isEmpty());
		if (!seen.isEmpty()) badges.add(label(seen, MUTED, small));
		if (badges.getComponentCount() > 0) row.add(badges, BorderLayout.EAST);
		setMemberTip(row, member, "", false, seen, note);
		addNoteMenu(row, member);
		return fitHeight(row);
	}

	private String visibleNote(JsonObject member)
	{
		return plugin.canManageOrganizerUi() ? str(member, "note") : "";
	}

	private JsonArray visibleWarnings(JsonObject member)
	{
		return plugin.canManageOrganizerUi() ? array(member, "warnings") : new JsonArray();
	}

	static boolean hasActiveWarning(JsonArray warnings)
	{
		return !activeWarnings(warnings).isEmpty();
	}

	static List<JsonObject> activeWarnings(JsonArray warnings)
	{
		List<JsonObject> active = new ArrayList<>();
		for (JsonObject warning : objects(warnings)) if (bool(warning, "active")) active.add(warning);
		return active;
	}

	static String lastSeen(String iso, Instant now)
	{
		Instant then = instant(iso);
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
		String name = str(member, "displayName");
		String altOf = str(member, "altOf");
		String note = str(member, "note");
		boolean alt = isAltRank(str(member, "rank"));
		List<JsonObject> active = activeWarnings(array(member, "warnings"));
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem edit = new javax.swing.JMenuItem(alt ? "Edit alt and admin note" : "Edit admin note");
		edit.addActionListener(e -> promptMemberNote(name, alt, altOf, note));
		menu.add(edit);
		menu.addSeparator();
		javax.swing.JMenuItem warn = new javax.swing.JMenuItem("Add warning...");
		warn.addActionListener(e -> promptWarning(name, active.size()));
		menu.add(warn);
		if (!active.isEmpty())
		{
			javax.swing.JMenuItem revoke = new javax.swing.JMenuItem("Revoke a warning...");
			revoke.addActionListener(e -> promptRevoke(name, active));
			menu.add(revoke);
		}
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

	static String rosterTooltip(String rank, String altOf, JsonArray alts, String detail, boolean sameWorld, String seen, String note,
		JsonArray warnings, Instant now, ZoneId zone)
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < alts.size(); i++) if (alts.get(i).isJsonPrimitive()) names.add(alts.get(i).getAsString());
		List<String> lines = new ArrayList<>();
		if (!rank.isEmpty()) lines.add("<b>" + escape(rank) + "</b>");
		if (!altOf.isEmpty()) lines.add(tipLine(ACCENT, "Alt of <b>" + escape(altOf) + "</b>"));
		if (!names.isEmpty()) lines.add(tipLine(MUTED, (names.size() == 1 ? "Alt: " : "Alts: ") + escape(String.join(", ", names))));
		if (!detail.isEmpty()) lines.add(tipLine(MUTED, escape(detail)));
		if (sameWorld) lines.add(tipLine(SUCCESS, "On your world"));
		if (!seen.isEmpty()) lines.add(tipLine(MUTED, "Last seen " + seen));
		String sections = "";
		if (!note.isEmpty())
		{
			sections += tipSection(lines.isEmpty() && sections.isEmpty(), tipLine(WARNING, "<b>ADMIN NOTE</b>"))
				+ tipCard(TIP_CARD, escape(note).replace("\n", "<br>"));
		}
		List<JsonObject> history = objects(warnings);
		if (!history.isEmpty())
		{
			sections += tipSection(lines.isEmpty() && sections.isEmpty(), tipLine(TsgHubUi.ERROR, "<b>WARNINGS</b>") + " " + tipLine(MUTED, warningSummary(history)))
				+ warningCards(history, now, zone);
		}
		if (lines.isEmpty() && sections.isEmpty()) return "";
		String body = "<div style='padding:2px'>" + String.join("<br>", lines) + sections + "</div>";
		return sections.isEmpty() ? "<html>" + body + "</html>" : html(body, 240);
	}

	private static String tipSection(boolean first, String heading)
	{
		return "<div style='margin-top:" + (first ? 0 : 6) + "px'>" + heading + "</div>";
	}

	private static String tipCard(Color bar, String inner)
	{
		return "<table cellspacing='0' cellpadding='0' width='100%' style='margin-top:2px'><tr><td bgcolor='" + hex(bar) + "' width='2'></td>"
			+ "<td bgcolor='" + hex(TIP_CARD) + "' style='padding:3px 6px'>" + inner + "</td></tr></table>";
	}

	private static String warningSummary(List<JsonObject> warnings)
	{
		int active = 0, expired = 0, revoked = 0;
		for (JsonObject warning : warnings)
		{
			if (bool(warning, "active")) active++;
			else if (!str(warning, "revokedAt").isEmpty()) revoked++;
			else expired++;
		}
		List<String> parts = new ArrayList<>();
		parts.add(active + " active");
		if (expired > 0) parts.add(expired + " expired");
		if (revoked > 0) parts.add(revoked + " revoked");
		return String.join(" · ", parts);
	}

	private static String warningCards(List<JsonObject> warnings, Instant now, ZoneId zone)
	{
		List<JsonObject> ordered = new ArrayList<>();
		List<JsonObject> past = new ArrayList<>();
		for (JsonObject warning : warnings) (bool(warning, "active") ? ordered : past).add(warning);
		int inactive = past.size();
		ordered.addAll(past.subList(0, Math.min(inactive, MAX_PAST_WARNINGS)));
		StringBuilder out = new StringBuilder();
		for (JsonObject warning : ordered)
		{
			boolean active = bool(warning, "active");
			String meta = warningDate(str(warning, "issuedAt"), now, zone) + " · " + escape(str(warning, "issuedBy")) + " · " + warningStatus(warning, now, zone);
			Color text = active ? TEXT : DIM;
			out.append(tipCard(active ? TsgHubUi.ERROR : DIM_BAR, tipLine(text, escape(str(warning, "reason"))) + "<br>" + tipLine(active ? MUTED : DIM, meta)));
		}
		if (inactive > MAX_PAST_WARNINGS) out.append(tipLine(DIM, "+" + (inactive - MAX_PAST_WARNINGS) + " older"));
		return out.toString();
	}

	static String warningStatus(JsonObject warning, Instant now, ZoneId zone)
	{
		if (!str(warning, "revokedAt").isEmpty()) return "revoked by " + escape(str(warning, "revokedBy"));
		String expires = str(warning, "expiresAt");
		if (expires.isEmpty()) return "no expiry";
		return bool(warning, "active") ? "expires " + warningDate(expires, now, zone) : "expired";
	}

	static String warningDate(String iso, Instant now, ZoneId zone)
	{
		Instant at = instant(iso);
		if (at == null) return "";
		LocalDate day = at.atZone(zone).toLocalDate();
		return day.format(day.getYear() == now.atZone(zone).getYear() ? WARNING_DAY : WARNING_DAY_YEAR);
	}

	private static String hex(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xffffff);
	}

	private void promptWarning(String name, int active)
	{
		javax.swing.JTextArea reason = new javax.swing.JTextArea(3, 22);
		reason.setLineWrap(true);
		reason.setWrapStyleWord(true);
		javax.swing.JComboBox<String> expiry = new javax.swing.JComboBox<>(new String[] {"After 30 days", "After 90 days", "Never"});
		expiry.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel form = stack();
		form.add(label(active == 0 ? name + " has no active warnings." : name + " has " + active + " active warning" + (active == 1 ? "." : "s."), MUTED, smallFont()));
		form.add(Box.createVerticalStrut(8));
		form.add(new JLabel("Reason"));
		form.add(new javax.swing.JScrollPane(reason));
		form.add(Box.createVerticalStrut(8));
		form.add(new JLabel("Expires"));
		form.add(expiry);
		form.add(Box.createVerticalStrut(8));
		form.add(label("Only admins see warnings.", MUTED, smallFont()));
		Object[] options = {"Add warning", "Cancel"};
		if (JOptionPane.showOptionDialog(this, form, "Warn " + name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) != 0) return;
		String text = reason.getText().trim();
		if (text.isEmpty())
		{
			setStatus("Add a reason for the warning.", Tone.ERROR);
			return;
		}
		if (text.length() > MAX_WARNING_LENGTH)
		{
			setStatus("Keep the reason to " + MAX_WARNING_LENGTH + " characters.", Tone.ERROR);
			return;
		}
		plugin.presence().addWarning(name, text, EXPIRY_DAYS[expiry.getSelectedIndex()]);
	}

	private void promptRevoke(String name, List<JsonObject> active)
	{
		Instant now = clock.instant();
		ZoneId zone = ZoneId.systemDefault();
		javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
		List<javax.swing.JRadioButton> choices = new ArrayList<>();
		JPanel form = stack();
		for (JsonObject warning : active)
		{
			String meta = warningDate(str(warning, "issuedAt"), now, zone) + " · " + escape(str(warning, "issuedBy"));
			javax.swing.JRadioButton choice = plain(new javax.swing.JRadioButton(html(escape(str(warning, "reason")) + "<br>" + tipLine(MUTED, meta), 220)), TEXT);
			choice.setSelected(choices.isEmpty());
			group.add(choice);
			choices.add(choice);
			form.add(choice);
			form.add(Box.createVerticalStrut(4));
		}
		JTextField reason = new JTextField(22);
		form.add(Box.createVerticalStrut(4));
		form.add(new JLabel("Reason for revoking (optional)"));
		form.add(reason);
		Object[] options = {"Revoke", "Cancel"};
		if (JOptionPane.showOptionDialog(this, form, "Revoke a warning for " + name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) != 0) return;
		for (int i = 0; i < choices.size(); i++)
		{
			if (choices.get(i).isSelected()) plugin.presence().revokeWarning(name, str(active.get(i), "id"), reason.getText().trim());
		}
	}

	private static String tipLine(Color color, String html)
	{
		return "<font color='" + String.format("#%06x", color.getRGB() & 0xffffff) + "'>" + html + "</font>";
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
		JPanel form = stack();
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
		if (samePlayer(nextMain, name))
		{
			setStatus("A character can't be its own alt.", Tone.ERROR);
			return;
		}
		if (nextNote.length() > 500)
		{
			setStatus("Keep the note to 500 characters.", Tone.ERROR);
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
				String candidate = str(member, "displayName");
				if (!samePlayer(candidate, alt) && !isAltRank(str(member, "rank"))) names.add(candidate);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	static List<String> matchNames(List<String> names, String query, int limit)
	{
		String needle = playerKey(query);
		List<String> prefix = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String name : names)
		{
			String key = playerKey(name);
			if (key.startsWith(needle)) prefix.add(name);
			else if (key.contains(needle)) contains.add(name);
		}
		prefix.addAll(contains);
		return prefix.size() > limit ? prefix.subList(0, limit) : prefix;
	}

	static String resolveName(List<String> names, String typed)
	{
		if (typed.isEmpty()) return typed;
		for (String name : names) if (samePlayer(name, typed)) return name;
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
		setHeader("Drops", "", true, true);
		page.removeAll();
		if (drops == null)
		{
			page.add(caption("Loading drops..."));
		}
		else if (drops.size() == 0)
		{
			page.add(errorPanel("No drops yet", "Clan broadcasts for drops, raid loot, pets and collection log items show up here."));
		}
		else
		{
			Instant now = clock.instant();
			ZoneId zone = ZoneId.systemDefault();
			String day = null;
			for (int i = 0; i < drops.size(); i++)
			{
				JsonObject drop = drops.get(i).getAsJsonObject();
				String nextDay = dropDay(str(drop, "receivedAt"), now, zone);
				if (!nextDay.equals(day))
				{
					page.add(listHeading(nextDay, i == 0));
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
		String player = str(drop, "player");
		boolean self = samePlayer(player, plugin.getDetectedPlayerName());
		JPanel card = card();
		if (self) highlightSelf(card);
		JLabel icon = dropIcon(integer(drop, "itemId", 0));
		if (icon != null) card.add(icon, BorderLayout.WEST);

		JLabel name = shrinkable(boldLabel(dropItem(drop)));
		name.setToolTipText(str(drop, "item"));
		JPanel extras = panel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		for (String tag : dropTags(drop)) extras.add(badge(tag, TAG_COLORS.getOrDefault(tag, TAG_DEFAULT)));
		long value = drop.has("value") ? drop.get("value").getAsLong() : 0;
		if (value > 0) extras.add(label(formatGp(value), coinColor(value), smallFont()));
		card.add(fillWidth(twoLines(
			row(name, caption(dropWhen(str(drop, "receivedAt"), now, zone))),
			row(shrinkable(label(player, ACCENT, smallFont())), extras))), BorderLayout.CENTER);
		return fitHeight(card);
	}

	private static JPanel fillWidth(Component content)
	{
		JPanel slot = panel(new GridBagLayout());
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
		String kind = str(drop, "kind");
		if ("raid".equals(kind)) tags.add(raidName(str(drop, "item")));
		else if ("pet".equals(kind)) tags.add("Pet");
		else if ("dupe".equals(kind)) tags.add("Dupe pet");
		if (bool(drop, "newLog") || "clog".equals(kind)) tags.add("Log");
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
		int quantity = integer(drop, "quantity", 1);
		String item = str(drop, "item").replaceAll("(\\s*\\([^)]*\\))+$", "");
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
		return localDate(then, zone);
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
		boolean sameEvent = boardEvent != null && str(boardEvent, "id").equals(str(event, "id"));
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
		return "skill".equals(str(competitionEvent, "type")) ? "XP" : "kills";
	}

	private void showCompetitionPreview(JsonObject event)
	{
		competitionEvent = event;
		setView(View.COMPETITION_PREVIEW);
		setEventHeader(event, true, false);
		page.removeAll();
		JsonObject config = eventConfig(event);
		boolean skill = "skill".equals(str(event, "type"));
		String subject = skill ? skillName(str(config, "skill")) : str(config, "npcName");
		page.add(eventSummaryCard(event, subject, skill ? "Most " + subject + " XP gained wins." : "Most " + subject + " kills wins."));
		page.add(Box.createVerticalStrut(12));
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		for (ActionListener l : competitionJoinButton.getActionListeners()) competitionJoinButton.removeActionListener(l);
		competitionJoinButton.addActionListener(e -> {
			competitionJoinButton.setEnabled(false);
			competitionJoinButton.setText("Joining...");
			joinError.setVisible(false);
			plugin.participate(str(event, "id"));
		});
		page.add(fitHeight(competitionJoinButton));
		joinError.setVisible(false);
		page.add(joinError);
		page.add(Box.createVerticalStrut(6));
		page.add(hint(skill
			? "Your XP counts from the first time TSG Hub sees you after joining. Gains on other devices count the next time you log in with it."
			: !"loot".equals(str(config, "signal"))
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
		openCompetitionId = str(event, "id");
		refresh.setToolTipText("Refresh (updated " + LocalTime.now().format(TIME) + ")");
		setEventHeader(event, true, true);
		page.removeAll();
		JsonObject config = eventConfig(event);
		boolean skill = "skill".equals(str(event, "type"));
		boolean hidden = bool(event, "hideScores");
		JsonArray rows = array(event, "leaderboard");
		JsonObject mine = null;
		for (JsonObject row : objects(rows)) if (str(row, "displayName").equalsIgnoreCase(competitionName)) mine = row;

		boolean started = !"scheduled".equals(str(event, "status"));
		boolean tracking = mine != null && bool(mine, "tracking");
		JPanel summary = card();
		JPanel top = row();
		top.add(shrinkable(boldLabel(skill ? skillName(str(config, "skill")) : str(config, "npcName"))), BorderLayout.CENTER);
		if (started && mine != null && mine.has("rank") && !hidden)
		{
			int rank = integer(mine, "rank", 0);
			top.add(label("#" + rank + " of " + integer(event, "participants", rows.size()), rank == 1 ? ACCENT : MUTED, smallFont()), BorderLayout.EAST);
		}
		JPanel lines = stack();
		lines.add(top);
		lines.add(Box.createVerticalStrut(3));
		if (!started)
		{
			String start = eventStartWhen(event);
			lines.add(cardNote(start.isEmpty() ? "Counting starts when the event begins." : "Counting starts " + start + "."));
		}
		else
		{
			lines.add(label("+" + String.format("%,d", mine == null ? 0 : integer(mine, "gained", 0)) + " " + unit(), SUCCESS, boldFont()));
			if (!tracking)
			{
				lines.add(Box.createVerticalStrut(2));
				lines.add(cardNote(skill ? "Starts counting the next time you gain XP." : "Starts counting at your next kill."));
			}
		}
		JPanel prizes = prizeRow(event, smallFont());
		if (prizes != null)
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(prizes);
		}
		summary.add(lines, BorderLayout.CENTER);
		page.add(fitHeight(summary));
		page.add(Box.createVerticalStrut(8));

		if (hidden)
		{
			page.add(errorPanel("Scores are hidden", "The admins are keeping the leaderboard secret for now."));
		}
		else
		{
			page.add(caption(integer(event, "participants", rows.size()) + (started ? " taking part" : " signed up")));
			page.add(Box.createVerticalStrut(4));
			for (int i = 0; i < rows.size(); i++)
			{
				JsonObject row = rows.get(i).getAsJsonObject();
				boolean me = str(row, "displayName").equalsIgnoreCase(competitionName);
				int rank = integer(row, "rank", i + 1);
				JPanel card = card();
				card.setLayout(new BorderLayout(8, 0));
				if (me) highlightSelf(card);
				if (started) card.add(rankLabel(rank, boldFont(), 18), BorderLayout.WEST);
				card.add(wrapped(str(row, "displayName"), TEXT, plainFont(), 110), BorderLayout.CENTER);
				String gained = bool(row, "tracking") ? String.format("%,d", integer(row, "gained", 0)) : "-";
				if (started) card.add(label(gained, rank == 1 ? ACCENT : TEXT, boldFont()), BorderLayout.EAST);
				page.add(fitHeight(card));
				page.add(Box.createVerticalStrut(4));
			}
		}
		page.add(Box.createVerticalStrut(14));
		JButton leave = button("Leave competition");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> {
			int choice = JOptionPane.showConfirmDialog(this, "Leave \"" + eventName(event) + "\"?\nYou'll drop off the leaderboard on this device. You can rejoin while it's running.",
				"Leave competition", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.OK_OPTION) plugin.leaveEvent(str(event, "id"));
		});
		page.add(fitHeight(leave));
		refreshPage();
	}

	private void showDropParty(JsonObject event)
	{
		setView(View.DROP_PARTY);
		setHeader(eventName(event), "Custom", true, false);
		page.removeAll();
		JsonObject config = eventConfig(event);
		JPanel card = card();
		JPanel lines = stack();
		String countdown = capitalize(eventRelative(event));
		if (!countdown.isEmpty()) lines.add(label(countdown, "Ended".equals(countdown) ? MUTED : SUCCESS, boldFont()));
		lines.add(Box.createVerticalStrut(4));
		lines.add(detail("When", eventWhen(event)));
		if (integer(config, "world", 0) > 0) lines.add(detail("World", String.valueOf(integer(config, "world", 0))));
		if (!str(config, "location").isEmpty()) lines.add(detail("Where", str(config, "location")));
		if (!str(config, "host").isEmpty()) lines.add(detail("Host", str(config, "host")));
		JPanel prizes = prizeRow(event, smallFont());
		if (prizes != null)
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(prizes);
		}
		card.add(lines, BorderLayout.CENTER);
		page.add(fitHeight(card));
		String notes = str(config, "notes");
		if (!notes.isEmpty())
		{
			page.add(Box.createVerticalStrut(8));
			page.add(hint(notes));
		}
		refreshPage();
	}

	private JLabel detail(String label, String value)
	{
		JLabel line = label(html("<font color='#8f8f8f'>" + label + ":</font> " + escape(value), CARD_TEXT_W), TEXT, plainFont());
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
		if (current == null || !str(current, "activity").isEmpty()) return;
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
		for (JsonObject member : objects(array(group, "members"))) names.add(str(member, "displayName"));
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
		int count = group == null ? 0 : array(group, "members").size();
		String members = count == 0 ? "" : count == 1 ? "Just you so far" : count + " members";
		if (group != null && bool(group, "locked")) members = members.isEmpty() ? "Locked" : members + " · Locked";
		setHeader(group == null ? "Your party" : currentTitle(group), members, true, true);
	}

	private void renderCurrentGroup(JsonObject group)
	{
		setPartyHeader(group);

		groupsPage.add(groupMembers);
		groupsPage.add(Box.createVerticalStrut(10));

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
		groupsPage.add(Box.createVerticalStrut(4));

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
		JButton leave = button("Leave party");
		leave.setForeground(TsgHubUi.ERROR);
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
		setHeader(current != null ? "Other parties" : "Parties", "", true, true);
		back.setToolTipText(current != null ? "Back to your party" : "Home");
		if (groupList == null)
		{
			groupsPage.add(caption("Loading parties..."));
		}
		else if (groupList.size() == 0)
		{
			groupsPage.add(errorPanel("No parties yet", "Start one below. Clanmates can join it with one click, no code needed."));
		}
		else
		{
			for (JsonObject group : objects(groupList))
			{
				groupsPage.add(groupCard(group, current));
				groupsPage.add(Box.createVerticalStrut(5));
			}
		}

		groupsPage.add(Box.createVerticalStrut(10));
		createGroupButton.setEnabled(!groupBusy);
		createGroupButton.setText(groupBusy ? "Working..." : "New party");
		groupsPage.add(fitHeight(createGroupButton));
		groupsPage.add(groupError);
		groupsPage.add(Box.createVerticalStrut(8));
		groupsPage.add(hint(current != null ? "Joining or starting another party leaves your current one."
			: "Party members see each other's health, prayer, gear, inventory and skills while they're in the party."));
		if (plugin.groups().inOtherParty())
		{
			groupsPage.add(Box.createVerticalStrut(4));
			groupsPage.add(wrapped("You're in a RuneLite party from outside TSG Hub. Joining or starting a party here leaves it.", WARNING, smallFont(), TEXT_W));
		}
	}

	private JPanel groupCard(JsonObject group, JsonObject current)
	{
		JsonArray members = array(group, "members");
		String leader = str(group, "leaderName");
		int world = 0;
		List<String> names = new ArrayList<>();
		for (JsonObject member : objects(members))
		{
			names.add(str(member, "displayName"));
			if (samePlayer(str(member, "displayName"), leader)) world = integer(member, "world", 0);
		}
		JPanel card = card();
		JPanel text = stack();
		boolean mine = current != null && str(group, "id").equals(str(current, "id"));
		boolean locked = !mine && bool(group, "locked");
		String title = mine ? currentTitle(group) : partyTitle(group);
		boolean showLock = bool(group, "locked");
		JLabel heading = label(html("<b>" + escape(title) + "</b>", CARD_TITLE_W - (showLock ? 15 : 0)), TEXT, plainFont());
		if (showLock)
		{
			heading.setIcon(new LockIcon());
			heading.setIconTextGap(5);
		}
		text.add(heading);
		text.add(Box.createVerticalStrut(2));
		Map.Entry<String, Integer> area = str(group, "activity").isEmpty() ? areaSummary(members) : null;
		String here = area != null && area.getValue() < members.size() ? " · " + area.getValue() + " here" : "";
		String meta = (members.size() == 1 ? "1 member" : members.size() + " members") + here + (world > 0 ? " · W" + world : "") + " · " + leader;
		text.add(caption(meta));
		text.add(wrapped(String.join(", ", names), MUTED, smallFont(), CARD_TITLE_W));
		card.add(text, BorderLayout.CENTER);
		card.add(north(mine ? badge("Yours", SUCCESS)
			: locked ? new JLabel()
			: label(groupBusy ? "..." : current != null ? "Switch" : "Join", ACCENT, smallFont())), BorderLayout.EAST);
		if (locked)
		{
			card.setToolTipText("The leader has locked this party");
			return fitHeight(card);
		}
		card.setToolTipText(mine ? "Back to your party" : (current != null ? "Switch to " : "Join ") + title);
		clickable(card, () -> {
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
			plugin.groups().join(str(group, "id"));
		});
		return fitHeight(card);
	}

	static String partyTitle(JsonObject group)
	{
		String title = str(group, "activity");
		if (!title.isEmpty()) return title;
		Map.Entry<String, Integer> area = areaSummary(array(group, "members"));
		if (area != null) return area.getKey();
		String leader = str(group, "leaderName");
		return leader.isEmpty() ? "Party" : leader + "'s party";
	}

	private String currentTitle(JsonObject group)
	{
		if (!str(group, "activity").isEmpty()) return partyTitle(group);
		Map.Entry<String, Integer> live = areaSummary(plugin.groups().liveAreas());
		return live != null ? live.getKey() : partyTitle(group);
	}

	static Map.Entry<String, Integer> areaSummary(JsonArray members)
	{
		List<String> areas = new ArrayList<>();
		for (JsonObject member : objects(members)) areas.add(str(member, "area"));
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
		JButton discord = iconButton(new DiscordIcon(), "Copy an invite to the clan Discord");
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
		String relative = eventRelative(event);
		setHeader(eventName(event), relative.isEmpty() ? statusLabel(str(event, "status")) : capitalize(relative), showBack, showRefresh);
		subtitle.setToolTipText(eventWhen(event));
	}

	private void setHeader(String titleText, String subtitleText, boolean showBack, boolean showRefresh)
	{
		this.titleText = titleText;
		title.setText(html(escape(titleText), titleWidth()));
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
			page.add(caption("Loading events..."));
		}
		else
		{
			List<JsonObject> visible = objects(events);
			visible.removeIf(event -> "ended".equals(str(event, "status")));
			visible.sort(Comparator
				.comparing((JsonObject e) -> !bool(e, "joined"))
				.thenComparing(e -> !"active".equals(str(e, "status")))
				.thenComparing(TsgHubUi::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
			if (visible.isEmpty())
			{
				page.add(errorPanel("No events yet", "When an admin creates an event, it will show up here."));
			}
			String section = null;
			for (JsonObject event : visible)
			{
				String next = "active".equals(str(event, "status")) ? "Live" : "Upcoming";
				if (!next.equals(section))
				{
					page.add(listHeading(next, section == null));
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
		boolean joined = bool(event, "joined");
		JPanel card = card();
		if (joined) highlightSelf(card);
		card.add(eventLines(event, plugin::getCoinImage), BorderLayout.CENTER);

		String type = str(event, "type");
		boolean dropParty = "drop-party".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		String action = dropParty ? "See when and where" : joined ? (competition ? "Open the leaderboard" : "Open your team's board")
			: competition ? "Join this competition" : "Join this event with a team code";
		card.setToolTipText("<html>" + escape(eventWhen(event)) + "<br>" + action + "</html>");
		clickable(card, () -> {
			String id = str(event, "id");
			if (dropParty) showDropParty(event);
			else if (competition && joined && !TsgHubSession.get("memberToken:" + id).isEmpty()) plugin.openCompetition(id, true);
			else if (competition) showCompetitionPreview(event);
			else if (joined) plugin.activateEvent(id);
			else showPreview(event);
		});
		return fitHeight(card);
	}

	private JPanel eventSummaryCard(JsonObject event, String subject, String goal)
	{
		JPanel card = card();
		JPanel lines = stack();
		lines.add(shrinkable(boldLabel(subject)));
		lines.add(Box.createVerticalStrut(3));
		lines.add(wrapped(goal, TEXT, smallFont(), CARD_TEXT_W));
		lines.add(Box.createVerticalStrut(2));
		lines.add(cardNote(eventWhen(event)));
		String description = str(event, "description").trim();
		if (!description.isEmpty())
		{
			lines.add(Box.createVerticalStrut(4));
			lines.add(cardNote(description));
		}
		JPanel prizes = prizeRow(event, smallFont());
		if (prizes != null)
		{
			lines.add(Box.createVerticalStrut(6));
			lines.add(prizes);
		}
		card.add(lines, BorderLayout.CENTER);
		return fitHeight(card);
	}

	private void showPreview(JsonObject event)
	{
		previewEvent = event;
		setView(View.PREVIEW);
		setEventHeader(event, true, false);
		page.removeAll();
		page.add(eventSummaryCard(event, "Bingo", "Complete tasks with your team for points. Most points wins."));
		page.add(Box.createVerticalStrut(12));
		page.add(label("Team code", TEXT, smallFont()));
		page.add(Box.createVerticalStrut(4));
		codeField.setText("");
		page.add(fitHeight(codeField));
		joinError.setVisible(false);
		page.add(joinError);
		page.add(Box.createVerticalStrut(6));
		joinButton.setEnabled(true);
		joinButton.setText("Join event");
		page.add(fitHeight(joinButton));
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
		plugin.join(code, str(previewEvent, "id"));
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
		JsonArray teams = array(boardEvent, "teams");
		JsonArray scores = array(boardEvent, "teamScores");
		List<JsonObject> ranked = new ArrayList<>();
		for (int i = 0; i < teams.size(); i++) ranked.add(teams.get(i).getAsJsonObject());
		ranked.sort(Comparator
			.comparingInt((JsonObject team) -> integer(scoreFor(scores, str(team, "id")), "points", 0)).reversed()
			.thenComparing(Comparator.comparingInt((JsonObject team) -> integer(scoreFor(scores, str(team, "id")), "completedTasks", 0)).reversed())
			.thenComparing(team -> str(team, "name"), String.CASE_INSENSITIVE_ORDER));
		return ranked;
	}

	private String ownTeamId()
	{
		return teamIdFor(boardEvent, boardDisplayName);
	}

	private void renderSummary()
	{
		boardSummary.removeAll();
		String teamId = ownTeamId();
		List<JsonObject> ranked = rankedTeams();
		JsonObject score = scoreFor(array(boardEvent, "teamScores"), teamId);
		int totalTasks = array(boardEvent, "tasks").size();
		int completed = integer(score, "completedTasks", 0);
		String teamName = "No team";
		int rank = 0;
		for (int i = 0; i < ranked.size(); i++)
		{
			if (str(ranked.get(i), "id").equals(teamId))
			{
				teamName = str(ranked.get(i), "name");
				rank = i + 1;
			}
		}

		JPanel card = card();
		JPanel row = row();
		row.add(label(html("<b>" + escape(teamName) + "</b>", CARD_TITLE_W), TEXT, plainFont()), BorderLayout.CENTER);
		if (rank > 0 && !bool(boardEvent, "hideScores"))
		{
			JLabel rankLabel = label("#" + rank + " of " + ranked.size(), rank == 1 ? ACCENT : MUTED, boldFont());
			row.add(rankLabel, BorderLayout.EAST);
		}
		card.add(row, BorderLayout.NORTH);
		ProgressBar bar = progressBar(completed, Math.max(totalTasks, 1));
		bar.setLeftLabel(integer(score, "points", 0) + " pts");
		bar.setRightLabel(completed + "/" + totalTasks + " tasks");
		card.add(bar, BorderLayout.CENTER);
		JPanel prizes = prizeRow(boardEvent, smallFont());
		if (prizes != null)
		{
			prizes.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			card.add(prizes, BorderLayout.SOUTH);
		}
		boardSummary.add(fitHeight(card));
		boardSummary.revalidate();
		boardSummary.repaint();
	}

	private void renderTasks()
	{
		if (boardEvent == null) return;
		tasksTab.removeAll();
		JsonArray tasks = array(boardEvent, "tasks");
		JsonObject score = scoreFor(array(boardEvent, "teamScores"), ownTeamId());
		JsonArray progressRows = array(score, "tasks");

		List<JsonObject> open = new ArrayList<>();
		List<JsonObject> done = new ArrayList<>();
		for (JsonObject task : objects(tasks))
		{
			boolean completed = bool(progressFor(progressRows, str(task, "id")), "completed");
			(completed ? done : open).add(task);
		}

		JPanel controls = panel(new BorderLayout());
		controls.add(hideCompleted, BorderLayout.WEST);
		JLabel left = caption(open.size() + " left");
		left.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		controls.add(left, BorderLayout.EAST);
		tasksTab.add(fitHeight(controls));
		tasksTab.add(Box.createVerticalStrut(4));

		if (tasks.size() == 0)
		{
			tasksTab.add(errorPanel("No tasks yet", "The admins haven't added tasks to this event yet."));
		}
		else if (open.isEmpty() && hideCompleted.isSelected())
		{
			tasksTab.add(errorPanel("All done!", "Your team has completed every task."));
		}
		for (JsonObject task : open) addTaskCard(task, progressFor(progressRows, str(task, "id")));
		if (!hideCompleted.isSelected())
		{
			for (JsonObject task : done) addTaskCard(task, progressFor(progressRows, str(task, "id")));
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
		String taskId = str(task, "id");
		boolean completed = bool(progress, "completed");
		boolean pending = bool(progress, "pending");
		boolean individual = "individual".equals(str(task, "scope"));
		boolean solo = "solo".equals(str(task, "scope"));
		boolean manual = "manual".equals(str(task, "type"));

		JPanel card = card();
		JLabel name = label(html("<b>" + escape(str(task, "title")) + "</b>", CARD_TITLE_W - (completed ? 18 : 0)),
			completed ? MUTED : TEXT, plainFont());
		if (completed) name.setIcon(new CheckIcon());
		card.add(row(name, north(badge(integer(task, "points", 1) + " pts", completed ? MUTED : ACCENT))), BorderLayout.NORTH);

		JPanel body = stack();
		body.add(caption(taskTypeLabel(task) + (individual ? " · Everyone" : solo ? " · Solo" : " · Team")));
		String description = str(task, "description").trim();
		if (!description.isEmpty() && !completed) body.add(cardNote(description));

		int value = integer(progress, "progress", 0);
		int target = Math.max(1, integer(progress, "target", 1));
		if (completed)
		{
			boolean creditedToOrganizer = bool(progress, "override") && !bool(progress, "overrideCredited");
			body.add(label(creditedToOrganizer ? "Marked complete by an admin" : completedLine(progress), SUCCESS, smallFont()));
		}
		else if (addSetProgress(body, individual || solo ? myEntry(progress) : progress, individual || solo))
		{
			if (individual) body.add(caption(value + " of " + target + " teammates done"));
			if (solo) addSoloLeader(body, progress);
		}
		else if (solo)
		{
			JsonObject mine = myEntry(progress);
			int have = mine == null ? 0 : integer(mine, "progress", 0);
			if (target > 1) addBar(body, have, target, "You: " + have + "/" + target);
			addSoloLeader(body, progress);
		}
		else if (individual)
		{
			JsonObject mine = myEntry(progress);
			int have = mine == null ? 0 : integer(mine, "progress", 0);
			int need = mine == null ? 1 : integer(mine, "target", 1);
			if (mine != null && need > 1 && !bool(mine, "completed")) addBar(body, have, need, "You: " + have + "/" + need);
			else if (mine != null && bool(mine, "completed")) body.add(label("You're done", SUCCESS, smallFont()));
			body.add(caption(value + " of " + target + " teammates done"));
		}
		else if (target > 1) addBar(body, value, target, value + " / " + target);
		boolean setTile = array(progress, "alternatives").size() > 0 || progress.has("items");
		if (!individual && !solo && (!setTile || completed))
		{
			String amounts = contributorsText(progress, " · ", 4);
			if (!amounts.isEmpty()) body.add(cardNote(amounts));
		}
		if (pending && !completed)
		{
			body.add(Box.createVerticalStrut(3));
			body.add(badge("Awaiting admin review", WARNING));
		}
		if (manual && !completed && !pending) addManualSubmit(body, taskId);
		addLineSpacing(body);
		card.add(body, BorderLayout.CENTER);
		card.setToolTipText(memberTooltip(progress, individual, solo));
		return fitHeight(card);
	}

	private void addManualSubmit(JPanel body, String taskId)
	{
		body.add(Box.createVerticalStrut(5));
		if (!taskId.equals(openManualTaskId))
		{
			JButton submit = button("Submit proof");
			submit.addActionListener(e -> {
				openManualTaskId = taskId;
				manualNote.setText("");
				renderTasks();
				SwingUtilities.invokeLater(manualNote::requestFocusInWindow);
			});
			body.add(fitHeight(submit));
			return;
		}
		body.add(cardNote("Add a note or link for the admins."));
		body.add(Box.createVerticalStrut(3));
		body.add(fitHeight(manualNote));
		body.add(Box.createVerticalStrut(4));
		JPanel buttons = panel(new GridLayout(1, 2, 4, 0));
		JButton cancel = button("Cancel");
		cancel.addActionListener(e -> {
			openManualTaskId = "";
			renderTasks();
		});
		JButton send = primaryButton("Send");
		Runnable doSend = () -> {
			if (manualNote.getText().trim().isEmpty())
			{
				setStatus("Add a short note before sending.", Tone.ERROR);
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
		body.add(fitHeight(buttons));
	}

	private boolean addSetProgress(JPanel body, JsonObject source, boolean mine)
	{
		if (source == null || array(source, "alternatives").size() == 0 && !source.has("items")) return false;
		if (bool(source, "completed")) return true;
		List<SetLine> sets = setLines(source, !mine);
		List<SetLine> shown = new ArrayList<>();
		for (SetLine set : sets) if (set.have > 0) shown.add(set);
		if (shown.isEmpty() && !sets.isEmpty()) shown.add(sets.get(0));
		for (SetLine set : shown)
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
		if (notStarted > 0) body.add(caption("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started"));
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
		JsonArray members = array(progress, "members");
		if (individual || solo)
		{
			if (members.size() == 0) return null;
			List<JsonObject> sorted = objects(members);
			sorted.sort((a, b) -> integer(b, "progress", 0) - integer(a, "progress", 0));
			List<String> done = new ArrayList<>();
			List<String> waiting = new ArrayList<>();
			for (JsonObject member : sorted)
			{
				String name = escape(str(member, "displayName"));
				if (str(member, "displayName").equalsIgnoreCase(boardDisplayName)) name = "<font color='" + SELF_TEXT + "'>" + name + "</font>";
				int have = integer(member, "progress", 0);
				int need = integer(member, "target", 1);
				if (solo) waiting.add(name + " " + have + "/" + need);
				else if (bool(member, "completed")) done.add(name);
				else waiting.add(need > 1 ? name + " " + have + "/" + need : name);
			}
			if (solo) return "<html>" + String.join("<br>", waiting) + "</html>";
			StringBuilder text = new StringBuilder("<html>");
			text.append("<b>Done:</b> ").append(done.isEmpty() ? "nobody yet" : String.join(", ", done));
			text.append("<br><b>Still need:</b> ").append(waiting.isEmpty() ? "nobody" : String.join(", ", waiting));
			return text.append("</html>").toString();
		}
		String contributors = contributorsText(progress, "<br>", 50);
		return contributors.isEmpty() ? null : "<html><b>Contributors</b><br>" + contributors + "</html>";
	}

	private void addSoloLeader(JPanel body, JsonObject progress)
	{
		String leader = str(progress, "leader");
		if (leader.isEmpty() || leader.equalsIgnoreCase(boardDisplayName)) return;
		body.add(caption("Leader: " + leader + " (" + integer(progress, "progress", 0) + "/" + integer(progress, "target", 1) + ")"));
	}

	private JsonObject myEntry(JsonObject progress)
	{
		for (JsonObject member : objects(array(progress, "members")))
			if (str(member, "displayName").equalsIgnoreCase(boardDisplayName)) return member;
		return null;
	}

	private void renderScoreboard()
	{
		scoreboardTab.removeAll();
		if (bool(boardEvent, "hideScores"))
		{
			renderHiddenScoreboard();
			return;
		}
		List<JsonObject> ranked = rankedTeams();
		JsonArray scores = array(boardEvent, "teamScores");
		int totalTasks = array(boardEvent, "tasks").size();
		String ownTeam = ownTeamId();
		if (ranked.isEmpty()) scoreboardTab.add(errorPanel("No teams yet", "Teams appear here once an admin adds them."));
		for (int i = 0; i < ranked.size(); i++)
		{
			JsonObject team = ranked.get(i);
			JsonObject score = scoreFor(scores, str(team, "id"));
			boolean mine = str(team, "id").equals(ownTeam);
			JPanel card = card();
			card.setLayout(new BorderLayout(8, 0));
			if (mine) highlightSelf(card);
			card.add(rankLabel(i + 1, boldFont(), 14), BorderLayout.WEST);
			JPanel text = stack();
			text.add(wrapped(str(team, "name"), TEXT, plainFont(), 120));
			text.add(caption(integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks"));
			card.add(text, BorderLayout.CENTER);
			card.add(label(integer(score, "points", 0) + " pts", i == 0 ? ACCENT : TEXT, boldFont()), BorderLayout.EAST);
			scoreboardTab.add(fitHeight(card));
			scoreboardTab.add(Box.createVerticalStrut(4));
		}
		refresh(scoreboardTab);
	}

	private void renderHiddenScoreboard()
	{
		scoreboardTab.add(errorPanel("Scores are hidden", "The admins are keeping scores secret for now. Your own team's progress is on the Tasks tab."));
		List<JsonObject> teams = objects(array(boardEvent, "teams"));
		teams.sort((a, b) -> str(a, "name").compareToIgnoreCase(str(b, "name")));
		String ownTeam = ownTeamId();
		for (JsonObject team : teams)
		{
			boolean mine = str(team, "id").equals(ownTeam);
			JPanel card = card();
			if (mine) highlightSelf(card);
			card.add(wrapped(str(team, "name"), TEXT, plainFont(), CARD_TEXT_W), BorderLayout.CENTER);
			scoreboardTab.add(fitHeight(card));
			scoreboardTab.add(Box.createVerticalStrut(4));
		}
		refresh(scoreboardTab);
	}

	private void renderTeam()
	{
		teamTab.removeAll();
		String ownTeam = ownTeamId();
		List<String> teammates = new ArrayList<>();
		for (JsonObject member : objects(array(boardEvent, "members")))
		{
			if (!ownTeam.isEmpty() && str(member, "teamId").equals(ownTeam)) teammates.add(str(member, "displayName"));
		}
		teammates.sort(String.CASE_INSENSITIVE_ORDER);
		teamTab.add(caption(teammates.size() == 1 ? "1 member" : teammates.size() + " members"));
		teamTab.add(Box.createVerticalStrut(4));
		for (String name : teammates)
		{
			JPanel row = card();
			if (name.equalsIgnoreCase(boardDisplayName)) highlightSelf(row);
			row.add(wrapped(name, TEXT, plainFont(), CARD_TEXT_W), BorderLayout.CENTER);
			teamTab.add(fitHeight(row));
			teamTab.add(Box.createVerticalStrut(3));
		}
		teamTab.add(Box.createVerticalStrut(14));
		JButton leave = button("Disconnect from event");
		leave.setForeground(TsgHubUi.ERROR);
		leave.addActionListener(e -> confirmLeave());
		teamTab.add(fitHeight(leave));
		teamTab.add(Box.createVerticalStrut(3));
		teamTab.add(hint("Stops tracking on this device. Your team keeps its progress and you can rejoin with the same code."));
		refresh(teamTab);
	}

	private void confirmLeave()
	{
		if (boardEvent == null) return;
		int choice = JOptionPane.showConfirmDialog(this,
			"Stop tracking \"" + eventName(boardEvent) + "\" on this device?\nYour team keeps its progress and you can rejoin with your team code.",
			"Disconnect from event", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.OK_OPTION) plugin.leaveEvent(str(boardEvent, "id"));
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
		bar.setBackground(BACKGROUND);
		bar.setForeground(value >= max ? SUCCESS.darker() : ACCENT.darker().darker());
		// ProgressBar passes its fill color to labels; keep text white.
		for (Component child : bar.getComponents()) if (child instanceof JLabel) child.setForeground(Color.WHITE);
		bar.setPreferredSize(new Dimension(100, 16));
		return fitHeight(bar);
	}

	private JPanel errorPanel(String heading, String body)
	{
		return emptyState(heading, body, TEXT_W - 20);
	}

	private static JLabel hint(String text)
	{
		return wrapped(text, MUTED, smallFont(), TEXT_W);
	}

	private static JLabel cardNote(String text)
	{
		return wrapped(text, MUTED, smallFont(), CARD_TEXT_W);
	}

	private static void showError(JLabel label, String message)
	{
		label.setText(html(escape(message), TEXT_W));
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
