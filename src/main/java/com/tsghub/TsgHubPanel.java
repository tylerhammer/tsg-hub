package com.tsghub;

import static com.tsghub.TsgHubBingo.*;
import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

final class TsgHubPanel extends JPanel
{
	static final int LIST_W = 270;
	static final int DETAIL_TEXT_W = 500;
	private final TsgHubPlugin plugin;
	final TsgHubOrganizerEventList eventListView;
	final TsgHubEventForm eventForm;
	final TsgHubTaskEditor taskEditor;
	final TsgHubTeamsTab teamsView;
	final TsgHubTasksTab tasksView;
	final TsgHubLeaderboardTab leaderboardView;
	final TsgHubDetailsTab detailsView;

	final CardLayout detailLayout = new CardLayout();
	final JPanel detail = new JPanel(detailLayout);
	private final StatusLine status = new StatusLine(DETAIL_TEXT_W);

	private final JLabel eventTitle = label(" ", TEXT, titleFont());
	private final JLabel eventMeta = caption(" ");
	private final JLabel eventSchedule = caption(" ");
	private final JLabel eventPrizePool = caption(" ");
	MaterialTabGroup tabs;
	private MaterialTab teamsTabButton;
	MaterialTab tasksTabButton;
	private MaterialTab leaderboardTabButton;
	private MaterialTab detailsTabButton;
	private final JButton publishEvent = primaryButton("Publish");
	private final JButton endEvent = button("End event");
	JsonObject currentEvent;
	TsgHubPanel(TsgHubPlugin plugin)
	{
		this.plugin = plugin;
		eventListView = new TsgHubOrganizerEventList(plugin, this);
		eventForm = new TsgHubEventForm(plugin, this);
		taskEditor = new TsgHubTaskEditor(plugin, this);
		teamsView = new TsgHubTeamsTab(plugin, this);
		tasksView = new TsgHubTasksTab(plugin, this);
		leaderboardView = new TsgHubLeaderboardTab(plugin, this);
		detailsView = new TsgHubDetailsTab(plugin, this);
		setLayout(new BorderLayout());
		setBackground(BACKGROUND);

		add(eventListView.buildEventListColumn(), BorderLayout.WEST);

		detail.setOpaque(false);
		detail.add(north(emptyState("Pick an event", "Choose an event on the left, or create a new one.", 320)), "empty");
		detail.add(eventForm.buildEventForm(), "event-form");
		detail.add(buildEventWorkspace(), "event");
		detail.add(taskEditor.buildTaskEditor(), "task-editor");

		JPanel right = panel(new BorderLayout());
		right.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		right.add(detail, BorderLayout.CENTER);
		right.add(status, BorderLayout.SOUTH);
		add(right, BorderLayout.CENTER);

		eventForm.setDetectedClanName(plugin.clan().name(), plugin.clan().rank());
		eventListView.setManagedEvents(new JsonArray());
	}

	private JComponent buildEventWorkspace()
	{
		JPanel workspace = panel(new BorderLayout(0, 8));
		JPanel header = panel(new BorderLayout(10, 0));
		JPanel titles = stack();
		titles.add(eventTitle);
		titles.add(Box.createVerticalStrut(GAP_XS));
		titles.add(eventMeta);
		titles.add(Box.createVerticalStrut(GAP_XS));
		titles.add(eventSchedule);
		titles.add(Box.createVerticalStrut(GAP_XS));
		titles.add(eventPrizePool);
		header.add(titles, BorderLayout.CENTER);
		JButton edit = button("Edit event");
		edit.addActionListener(e -> eventForm.beginEditEvent());
		JButton deleteEvent = iconButton(new TrashIcon(), "Delete event");
		deleteEvent.addActionListener(e -> confirmDeleteEvent());
		publishEvent.setToolTipText("Let players see and join this event");
		publishEvent.addActionListener(e -> { if (currentEvent != null) plugin.organizer().publishEvent(currentEvent); });
		JPanel editButtons = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		endEvent.setToolTipText("Mark this event as finished and let the clan know");
		endEvent.addActionListener(e -> confirmEndEvent());
		editButtons.add(publishEvent);
		editButtons.add(endEvent);
		editButtons.add(edit);
		editButtons.add(deleteEvent);
		header.add(north(editButtons), BorderLayout.EAST);

		JPanel display = panel(new BorderLayout());
		tabs = new MaterialTabGroup(display);
		tabs.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 0));
		tabs.setOpaque(false);
		teamsTabButton = tab("Teams", teamsView.teamsTab);
		tasksTabButton = tab("Tasks", tasksView.tasksTab);
		leaderboardTabButton = tab("Leaderboard", leaderboardView.leaderboardTab);
		detailsTabButton = tab("Details", detailsView.detailsTab);
		tabs.select(teamsTabButton);

		JPanel north = stack();
		north.add(fitHeight(header));
		north.add(Box.createVerticalStrut(GAP_M));
		JPanel tabRow = panel(new BorderLayout());
		tabRow.setBorder(bottomRule());
		tabRow.add(tabs, BorderLayout.WEST);
		north.add(fitHeight(tabRow));
		workspace.add(north, BorderLayout.NORTH);
		display.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		workspace.add(display, BorderLayout.CENTER);

		return workspace;
	}

	private MaterialTab tab(String name, JComponent content)
	{
		MaterialTab tab = themedTab(name, tabs, scroll(content));
		tabs.addTab(tab);
		return tab;
	}

	void setStatus(String text, Tone tone)
	{
		status.show(text, tone);
	}

	void openOrganizerEvent(JsonObject event)
	{
		boolean sameEvent = currentEvent != null && str(currentEvent, "id").equals(str(event, "id"));
		eventListView.selectedEventId = str(event, "id");
		showEvent(event);
		if (!sameEvent)
		{
			teamsView.viewedTeamId = "";
			String type = str(event, "type");
			tabs.select("skill".equals(type) || "boss".equals(type) ? leaderboardTabButton : "drop-party".equals(type) ? detailsTabButton : teamsTabButton);
		}
		detailLayout.show(detail, "event");
		eventListView.setManagedEvents(eventListView.managedEvents);
	}

	boolean editingText()
	{
		Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		return focused instanceof JTextComponent && SwingUtilities.isDescendingFrom(focused, this);
	}

	void showEvent(JsonObject event)
	{
		if (event.equals(currentEvent)) return;
		currentEvent = event.deepCopy();
		eventTitle.setText(TsgHubEvents.eventName(event));
		String state = str(event, "status");
		String type = str(event, "type");
		boolean bingo = type.isEmpty() || "bingo".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		String counts = bingo ? array(event, "teams").size() + " teams · " + array(event, "tasks").size() + " tasks"
			: competition ? integer(event, "participants", 0) + ("scheduled".equals(state) ? " signed up" : " taking part") : "";
		String relative = eventRelative(event);
		String detail = bingo ? "Bingo" : eventDetail(event);
		List<Long> prizes = eventPrizes(event);
		eventMeta.setText((detail.isEmpty() ? "" : detail + " · ") + (relative.isEmpty() ? statusLabel(state) : capitalize(relative))
			+ (counts.isEmpty() ? "" : " · " + counts)
			+ (bool(event, "hideScores") ? " · Scores hidden from players" : "")
			+ (bool(event, "hidden") ? " · Hidden from players" : ""));
		eventSchedule.setText(eventWhen(event));
		eventPrizePool.setText(prizes.isEmpty() ? "" : "Prize pool " + formatGp(prizeTotal(prizes)) + " (" + prizeSummary(prizes) + ")");
		eventPrizePool.setVisible(!prizes.isEmpty());
		publishEvent.setVisible(bool(event, "hidden"));
		endEvent.setVisible("drop-party".equals(type) && !"ended".equals(state));
		teamsTabButton.setVisible(bingo);
		tasksTabButton.setVisible(bingo);
		leaderboardTabButton.setVisible(competition);
		detailsTabButton.setVisible("drop-party".equals(type));
		teamsView.renderTeams();
		tasksView.renderTasks();
		leaderboardView.renderLeaderboard();
		detailsView.renderDetails();
	}

	void eventCreated(JsonObject event)
	{
		eventForm.eventName.setText("");
		openOrganizerEvent(event);
	}

	void eventDeleted(String eventId)
	{
		if (currentEvent != null && str(currentEvent, "id").equals(eventId))
		{
			currentEvent = null;
			eventListView.selectedEventId = "";
			teamsView.viewedTeamId = "";
			detailLayout.show(detail, "empty");
		}
	}

	private void confirmEndEvent()
	{
		if (currentEvent == null) return;
		String message = "End \"" + TsgHubEvents.eventName(currentEvent) + "\" now?\n\n"
			+ "It moves to ended and online clanmates get an announcement that it's over.";
		if (confirm(this, "End event", message, "End event", true)) plugin.organizer().endEvent(str(currentEvent, "id"));
	}

	private void confirmDeleteEvent()
	{
		if (currentEvent == null) return;
		int teams = array(currentEvent, "teams").size();
		int tasks = array(currentEvent, "tasks").size();
		int members = array(currentEvent, "members").size();
		String message = "Delete \"" + TsgHubEvents.eventName(currentEvent) + "\"?\n\n"
			+ "This permanently removes its " + plural(teams, "team") + ", " + plural(tasks, "task") + " and all progress.\n"
			+ (members > 0 ? plural(members, "player") + " will be disconnected.\n" : "")
			+ "This can't be undone.";
		if (confirm(this, "Delete event", message, "Delete event", true)) plugin.organizer().deleteEvent(str(currentEvent, "id"));
	}

	static JPanel radioField(String label, JRadioButton... options)
	{
		ButtonGroup group = new ButtonGroup();
		JPanel choices = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		for (JRadioButton option : options)
		{
			group.add(option);
			choices.add(plain(option, TEXT));
			choices.add(Box.createHorizontalStrut(10));
		}
		return field(label, choices);
	}

	static JPanel footer(JButton cancel, JButton save)
	{
		JPanel footer = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.add(cancel);
		footer.add(save);
		return fitHeight(footer);
	}

	JsonObject findTeam(String teamId)
	{
		for (JsonObject team : objects(array(currentEvent, "teams"))) if (str(team, "id").equals(teamId)) return team;
		return null;
	}

	int countMembers(String teamId)
	{
		int count = 0;
		for (JsonObject member : objects(array(currentEvent, "members"))) if (str(member, "teamId").equals(teamId)) count++;
		return count;
	}

	static String progressText(JsonObject progress)
	{
		if (bool(progress, "completed")) return "Complete";
		if (bool(progress, "pending")) return "Awaiting review";
		JsonObject best = bestSet(progress);
		if (best != null)
		{
			int options = array(progress, "alternatives").size();
			List<String> missing = missingPieces(array(best, "items"));
			return TsgHubBingo.setName(best) + " " + integer(best, "progress", 0) + "/" + integer(best, "target", 1)
				+ (options > 1 ? " (closest of " + options + ")" : "") + (missing.isEmpty() ? "" : " · need " + String.join(", ", missing));
		}
		return integer(progress, "progress", 0) + "/" + integer(progress, "target", 1);
	}
}
