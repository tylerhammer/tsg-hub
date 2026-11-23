package com.tsghub;

import com.google.gson.*;
import java.awt.*;
import java.time.format.*;
import javax.swing.*;
import net.runelite.client.ui.*;
import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.DOCK_VIEW;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

final class TsgHubSidebarPanel extends PluginPanel
{
	static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

	enum View
	{
		LOGGED_OUT, NOT_IN_CLAN, SHARING_OFF, HOME, EVENTS("Events", null), PREVIEW, SIGNUP, BOARD, COMPETITION_PREVIEW, COMPETITION, DROP_PARTY,
		GROUPS("Parties", "party"), MEMBERS("Members", "members"), LOOKUP, DROPS("Drops", "drops"), NEWS("Home", null), RECORDS("Home", null);

		private final String back;
		final String pin;

		View()
		{
			this(null, null);
		}

		View(String back, String pin)
		{
			this.back = back;
			this.pin = pin;
		}

		boolean top()
		{
			return back != null;
		}

		boolean gated()
		{
			return ordinal() < HOME.ordinal();
		}

		String card()
		{
			return pin != null || this == BOARD ? name().toLowerCase() : "page";
		}

		String backLabel()
		{
			return back != null ? back : this == BOARD || this == COMPETITION ? "Back to event" : "Home";
		}
	}

	private final TsgHubPlugin plugin;
	final TsgHubHomeView homeView;
	final TsgHubMembersView membersView;
	final TsgHubMemberAdmin memberAdmin;
	final TsgHubLookupView lookupView;
	final TsgHubDropsView dropsView;
	final TsgHubNewsView newsView;
	final TsgHubRecordsView recordsView;
	final TsgHubEventsView eventsView;
	final TsgHubCompetitionView competitionView;
	final TsgHubPartiesView partiesView;
	final TsgHubBoardView boardView;
	final JButton back = iconButton(hover("back"), "Back to events");
	private final JLabel title = boldLabel("TSG Hub");
	private final JLabel subtitle = caption(" ");
	private String titleText = "TSG Hub";
	private final Glyph refreshIcon = hover("refresh");
	final JButton refresh = iconButton(refreshIcon, "Refresh");
	private final JButton admin = iconButton(hover("organizer"), "Open the admin site");
	private final JButton settings = iconButton(hover("cog"), "Plugin settings");
	private final JButton pin = iconButton(hover("pin"), "Pin below");
	private final JPanel footer = new JPanel();
	private final JButton discord = iconButton(hover("discord", DISCORD), "Open the clan Discord");
	private final JButton link = button("Link Discord");
	private final Timer linkTimer = new Timer(1000, e -> renderLink());
	private boolean needsLink;
	private long linkDeadline;
	private Color connectionColor = TsgHubTheme.ERROR;
	private String connectionText = "Disconnected";
	private String subtitleText = "";
	private String subtitleTip;
	private final JLabel version = label("v" + TsgHubVersion.VERSION, DIM, smallFont());
	private final StatusLine status = new StatusLine(TEXT_W);

	private final CardLayout centerLayout = new CardLayout();
	private final JPanel center = panel(centerLayout);
	final WidthTrackingPanel page = new WidthTrackingPanel();
	final TsgHubDock dock;
	View pinned;
	View view = View.LOGGED_OUT;
	private int busy;
	volatile boolean active;
	private volatile boolean boardShowing;
	private volatile boolean membersWanted;
	private volatile boolean groupsWanted;
	private volatile boolean dropsWanted;
	private final JPanel titleRow = panel(new BorderLayout(4, 0));
	private final JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
	final GroupMembersPanel groupMembers;
	TsgHubSidebarPanel(TsgHubPlugin plugin, GroupMembersPanel groupMembers)
	{
		super(false);
		this.plugin = plugin;
		this.groupMembers = groupMembers;
		membersView = new TsgHubMembersView(plugin, this);
		memberAdmin = new TsgHubMemberAdmin(plugin, this);
		lookupView = new TsgHubLookupView(plugin, this);
		dropsView = new TsgHubDropsView(plugin, this);
		newsView = new TsgHubNewsView(plugin, this);
		recordsView = new TsgHubRecordsView(plugin, this);
		eventsView = new TsgHubEventsView(plugin, this);
		competitionView = new TsgHubCompetitionView(plugin, this);
		partiesView = new TsgHubPartiesView(plugin, this);
		boardView = new TsgHubBoardView(plugin, this);
		homeView = new TsgHubHomeView(plugin, this);
		dock = new TsgHubDock(center, this::unpin, plugin.session());
		setLayout(new BorderLayout(0, 6));
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildHeader(), BorderLayout.NORTH);

		center.add(scroll(page), "page");
		center.add(boardView.buildBoard(), "board");
		center.add(partiesView.groupsScroll, "groups");
		center.add(membersView.membersScroll, "members");
		center.add(dropsView.buildDropsView(), "drops");
		add(dock, BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		showLoggedOut();
		restorePin();
	}

	private JPanel buildHeader()
	{
		JPanel header = panel(new BorderLayout());
		back.addActionListener(e -> {
			plugin.usage().count("button.back");
			if (view == View.GROUPS && partiesView.browsingParties && plugin.groups().inGroup())
			{
				partiesView.browsingParties = false;
				partiesView.renderGroups();
				return;
			}
			if (view == View.LOOKUP)
			{
				reopen(lookupView.lookupReturn);
				return;
			}
			if (view.top())
			{
				homeView.showHome();
				return;
			}
			eventsView.showEventList();
			if (!plugin.live()) plugin.sessions().loadClanEvents();
		});
		titleRow.add(back, BorderLayout.WEST);

		JPanel titles = stack();
		titles.add(title);
		titles.add(shrinkable(subtitle));
		titleRow.add(fillWidth(titles), BorderLayout.CENTER);

		admin.addActionListener(e -> {
			plugin.usage().count("button.admin-site");
			admin.setEnabled(false);
			plugin.openAdminSite(() -> admin.setEnabled(true));
		});
		admin.setVisible(false);
		refresh.addActionListener(e -> {
			plugin.usage().count("button.refresh");
			if (view == View.HOME)
			{
				plugin.sessions().loadClanEvents();
				plugin.groups().refresh();
				plugin.presence().loadMembers(false);
				plugin.announcements().load();
			}
			else if (view == View.GROUPS) plugin.groups().refresh();
			else if (view == View.NEWS) plugin.announcements().load();
			else if (view == View.MEMBERS) plugin.presence().loadMembers(false);
			else if (view == View.LOOKUP) lookupView.loadLookup();
			else if (view == View.DROPS) plugin.drops().load(false);
			else if (view == View.RECORDS) plugin.records().load();
			else if (view == View.BOARD || view == View.SIGNUP) plugin.sessions().refreshBoard();
			else if (view == View.COMPETITION && competitionView.competitionEvent != null) plugin.sessions().openCompetition(str(competitionView.competitionEvent, "id"));
			else plugin.sessions().loadClanEvents();
		});
		JPanel refreshSlot = panel(new BorderLayout());
		refreshSlot.setPreferredSize(refresh.getPreferredSize());
		refreshSlot.add(refresh);
		settings.addActionListener(e -> {
			plugin.usage().count("button.settings");
			plugin.openSettings();
		});
		pin.addActionListener(e -> {
			plugin.usage().count("button.pin");
			pinCurrent();
		});
		pin.setVisible(false);
		actions.add(admin);
		actions.add(pin);
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

	void setAdminAccess(boolean allowed)
	{
		admin.setVisible(allowed);
		setHtml(title, html(escape(titleText), titleWidth()));
		if (shows(View.MEMBERS)) membersView.renderMembers();
	}

	private int titleWidth()
	{
		return 150 - (admin.isVisible() ? admin.getPreferredSize().width + 2 : 0)
			- (pin.isVisible() ? pin.getPreferredSize().width + 2 : 0);
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
		plugin.usage().count("panel.open");
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
		JButton enable = onClick(primaryButton("Enable sharing"), () -> plugin.enableSharing());
		showMessage(View.SHARING_OFF,
			boldLabel("Share your progress"),
			gap(GAP_S),
			hint("TSG Hub tracks your boss kills, drops and raids for clan events. "
				+ "To do that it sends your RuneScape name, clan and rank, and that progress to your clan's event service."),
			gap(GAP_S),
			hint("Nothing is ever posted in game chat."),
			gap(GAP_L),
			fitHeight(enable),
			gap(GAP_S),
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

	void setConnection(TsgHubSocket.State state)
	{
		Color color = state == TsgHubSocket.State.CONNECTED ? SUCCESS : state == TsgHubSocket.State.CONNECTING ? WARNING : TsgHubTheme.ERROR;
		String status = state == TsgHubSocket.State.CONNECTED ? "Connected" : state == TsgHubSocket.State.CONNECTING ? "Connecting…" : "Disconnected";
		connectionColor = color;
		connectionText = status;
		renderSubtitle();
	}

	void setView(View next)
	{
		if (next != view && !next.gated()) plugin.usage().count(TsgHubUsage.viewName(next));
		view = next;
		homeView.homeBuilt = false;
		boardShowing = next == View.BOARD || next == View.SIGNUP;
		boolean docked = pinned != null && !next.gated();
		membersWanted = next == View.MEMBERS || next == View.HOME || docked && pinned == View.MEMBERS;
		groupsWanted = next == View.GROUPS || next == View.HOME || docked && pinned == View.GROUPS;
		dropsWanted = next == View.DROPS || docked && pinned == View.DROPS;
		if (next != View.COMPETITION) competitionView.openCompetitionId = null;
		centerLayout.show(center, next.card());
		discord.setVisible(next == View.HOME && !needsLink);
		link.setVisible(next == View.HOME && needsLink);
		version.setVisible(next == View.HOME);
		dock.setShown(!next.gated());
		pin.setVisible(next.pin != null);
		setHtml(title, html(escape(titleText), titleWidth()));
		back.setToolTipText(next == View.LOOKUP ? lookupView.lookupReturn.backLabel() : next.top() ? "Home" : "Back to events");
	}

	boolean shows(View owner)
	{
		return view == owner || pinned == owner;
	}

	private void reopen(View target)
	{
		switch (target)
		{
			case MEMBERS: membersView.showMembers(); break;
			case DROPS: dropsView.showDrops(); break;
			case GROUPS: partiesView.showParties(); break;
			case EVENTS: eventsView.showEventList(); break;
			case BOARD:
				if (boardView.boardEvent != null) boardView.showBoard(boardView.boardEvent, boardView.boardDisplayName, true);
				else homeView.showHome();
				break;
			case COMPETITION:
				if (competitionView.competitionEvent != null) competitionView.showCompetition(competitionView.competitionEvent, competitionView.competitionName, true);
				else homeView.showHome();
				break;
			default: homeView.showHome();
		}
	}

	void viewHeader(View owner, String titleText, String subtitleText, String dockDetail)
	{
		if (pinned == owner) dock.setTitle(titleText, dockDetail);
		else setHeader(titleText, subtitleText, true, true);
	}

	private JComponent pane(View owner)
	{
		return owner == View.MEMBERS ? membersView.membersScroll : owner == View.DROPS ? dropsView.dropsPanel : partiesView.groupsScroll;
	}

	void pinCurrent()
	{
		if (view.pin == null) return;
		View next = view;
		if (pinned != null) unpin();
		pin(next);
		homeView.showHome();
	}

	private void pin(View next)
	{
		pinned = next;
		plugin.session().set(DOCK_VIEW, next.name());
		dock.pin(next.pin, pane(next));
		dock.expand();
		renderPinned();
	}

	private void unpin()
	{
		if (pinned == null) return;
		View was = pinned;
		pinned = null;
		plugin.session().set(DOCK_VIEW, "");
		dock.unpin();
		center.add(pane(was), was.card());
		setView(view);
	}

	private void restorePin()
	{
		String saved = plugin.session().get(DOCK_VIEW);
		for (View candidate : View.values())
			if (candidate.pin != null && candidate.name().equals(saved))
			{
				pinned = candidate;
				dock.pin(candidate.pin, pane(candidate));
				setView(view);
				renderPinned();
			}
	}

	private void renderPinned()
	{
		if (pinned == View.MEMBERS) membersView.renderMembers();
		else if (pinned == View.DROPS) dropsView.renderDrops();
		else if (pinned == View.GROUPS) partiesView.renderGroups();
	}

	private JPanel buildFooter()
	{
		discord.addActionListener(e -> {
			plugin.usage().count("button.discord");
			discord.setEnabled(false);
			plugin.openDiscordInvite(() -> discord.setEnabled(true));
		});
		discord.setAlignmentX(Component.CENTER_ALIGNMENT);
		link.setIcon(icon("discord", Color.WHITE, 14));
		link.setIconTextGap(6);
		link.setBackground(DISCORD);
		link.setForeground(Color.WHITE);
		link.setAlignmentX(Component.CENTER_ALIGNMENT);
		link.addActionListener(e -> {
			plugin.usage().count("button.link-discord");
			link.setEnabled(false);
			if (linkDeadline > System.currentTimeMillis()) plugin.keys().cancelLink();
			else plugin.keys().startLink();
		});
		subtitle.setIconTextGap(4);
		setConnection(TsgHubSocket.State.DISCONNECTED);
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setOpaque(false);
		footer.add(discord);
		footer.add(link);
		version.setAlignmentX(Component.CENTER_ALIGNMENT);
		addGap(footer, GAP_XS, version);
		discord.setVisible(false);
		link.setVisible(false);
		return footer;
	}

	void setDiscordLink(boolean needsLink, long deadline, boolean starting)
	{
		this.needsLink = needsLink;
		linkDeadline = needsLink ? deadline : 0;
		link.setEnabled(!starting);
		discord.setVisible(view == View.HOME && !needsLink);
		link.setVisible(view == View.HOME && needsLink);
		renderLink();
	}

	private void renderLink()
	{
		long left = linkDeadline - System.currentTimeMillis();
		if (linkDeadline > 0 && left > 0)
		{
			long seconds = (left + 999) / 1000;
			link.setText(String.format("Waiting\u2026 %d:%02d", seconds / 60, seconds % 60));
			link.setToolTipText("Run the copied command in the clan Discord. Click to cancel.");
			if (!linkTimer.isRunning()) linkTimer.start();
			return;
		}
		linkTimer.stop();
		link.setText("Link Discord");
		link.setToolTipText("Copy a /plugin link command and open the clan Discord");
	}

	private void renderSubtitle()
	{
		subtitle.setIcon(icon("dot", connectionColor, 6));
		subtitle.setText(subtitleText.isEmpty() ? connectionText : subtitleText);
		subtitle.setToolTipText(subtitleTip == null ? connectionText : subtitleTip + " · " + connectionText);
	}

	void setEventHeader(JsonObject event, boolean showBack, boolean showRefresh)
	{
		String relative = eventRelative(event);
		setHeader(eventName(event), relative.isEmpty() ? statusLabel(str(event, "status")) : capitalize(relative), showBack, showRefresh);
		subtitleTip = eventWhen(event);
		renderSubtitle();
	}

	void setHeader(String titleText, String subtitleText, boolean showBack, boolean showRefresh)
	{
		this.titleText = titleText;
		setHtml(title, html(escape(titleText), titleWidth()));
		this.subtitleText = subtitleText;
		subtitleTip = null;
		renderSubtitle();
		back.setVisible(showBack);
		refresh.setVisible(showRefresh);
	}

	void refreshPage()
	{
		refresh(page);
	}
}
