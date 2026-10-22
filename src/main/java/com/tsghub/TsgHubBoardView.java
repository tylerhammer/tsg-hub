package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.HIDE_COMPLETED;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

final class TsgHubBoardView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private static final int CARD_TITLE_W = 150;
	private static final int CARD_CONTRIBUTORS = 2;
	private static final int CARD_CLAIMS = 3;
	private final JPanel boardSummary = stack();
	private final WidthTrackingPanel tasksTab = new WidthTrackingPanel();
	private final WidthTrackingPanel scoreboardTab = new WidthTrackingPanel();
	private final WidthTrackingPanel teamTab = new WidthTrackingPanel();
	private final JCheckBox hideCompleted = new JCheckBox("Hide completed");

	JsonObject boardEvent;
	String boardDisplayName = "";
	TsgHubBoardView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		plain(hideCompleted, MUTED).setFont(smallFont());
		hideCompleted.setSelected("true".equals(plugin.session().get(HIDE_COMPLETED)));
		hideCompleted.addActionListener(e -> {
			plugin.session().set(HIDE_COMPLETED, hideCompleted.isSelected() ? "true" : "");
			renderTasks();
		});
	}

	JPanel buildBoard()
	{
		JPanel board = panel(new BorderLayout(0, 6));
		JPanel display = panel(new BorderLayout());
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 3, 4, 0));
		tabs.setOpaque(false);
		MaterialTab tasks = themedTab("Tasks", tabs, scroll(tasksTab));
		tabs.addTab(tasks);
		tabs.addTab(themedTab("Scores", tabs, scroll(scoreboardTab)));
		tabs.addTab(themedTab("Team", tabs, scroll(teamTab)));
		tabs.select(tasks);

		JPanel north = stack();
		north.add(boardSummary);
		north.add(gap(GAP_M));
		north.add(fitHeight(tabs));
		board.add(north, BorderLayout.NORTH);
		board.add(display, BorderLayout.CENTER);
		return board;
	}

	void closeBoard()
	{
		if (shell.view == View.BOARD) shell.eventsView.showEventList();
	}

	void showBoard(JsonObject event, String displayName, boolean open)
	{
		// Background refreshes must not pull the user off another screen.
		if (!open && shell.view != View.BOARD) return;
		String name = displayName == null ? "" : displayName;
		shell.refresh.setToolTipText("Refresh (updated " + LocalTime.now().format(TsgHubSidebarPanel.TIME) + ")");
		if (!open && event.equals(boardEvent) && name.equals(boardDisplayName)) return;
		boardEvent = event;
		boardDisplayName = name;
		shell.setView(View.BOARD);
		renderBoard();
	}

	private void renderBoard()
	{
		JsonObject event = boardEvent;
		shell.setEventHeader(event, true, true);
		renderSummary();
		renderTasks();
		renderScoreboard();
		renderTeam();
	}

	private String ownTeamId()
	{
		return teamIdFor(boardEvent, boardDisplayName);
	}

	private void renderSummary()
	{
		boardSummary.removeAll();
		String teamId = ownTeamId();
		List<JsonObject> ranked = rankedTeams(boardEvent);
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
		row.add(label(html(escape(teamName), CARD_TITLE_W), TEXT, boldFont()), BorderLayout.CENTER);
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
		refresh(boardSummary);
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
		tasksTab.add(gap(GAP_S));

		if (tasks.size() == 0)
		{
			tasksTab.add(shell.errorPanel("No tasks yet", "The admins haven't added tasks to this event yet."));
		}
		else if (open.isEmpty() && hideCompleted.isSelected())
		{
			tasksTab.add(shell.errorPanel("All done!", "Your team has completed every task."));
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
		addCard(tasksTab, taskCard(task, progress));
	}

	private JPanel taskCard(JsonObject task, JsonObject progress)
	{
		String taskId = str(task, "id");
		boolean completed = bool(progress, "completed");
		boolean individual = "individual".equals(str(task, "scope"));
		boolean solo = "solo".equals(str(task, "scope"));

		JPanel card = card();
		JLabel name = shrinkable(label(str(task, "title"), completed ? MUTED : TEXT, boldFont()));
		name.putClientProperty("html.disable", Boolean.TRUE);
		if (completed) name.setIcon(icon("check", SUCCESS));
		JLabel points = label(integer(task, "points", 1) + " pts", completed ? MUTED : ACCENT, smallFont());
		card.add(row(name, north(points)), BorderLayout.NORTH);

		JPanel body = stack();
		String description = str(task, "description").trim();
		if (!description.isEmpty() && !completed) body.add(TsgHubSidebarPanel.cardNote(description));

		int value = integer(progress, "progress", 0);
		int target = Math.max(1, integer(progress, "target", 1));
		JsonObject mine = (individual || solo) && progress.has("mine") ? object(progress, "mine") : null;
		if (completed)
		{
			String line = str(progress, "completedLine");
			if (!line.isEmpty() && !"Completed".equals(line)) body.add(label(line, SUCCESS, smallFont()));
		}
		else if (addSetProgress(body, individual || solo ? mine : progress, individual || solo))
		{
			if (individual) body.add(teammatesLine(mine, value, target));
			if (solo) addSoloLeader(body, progress);
		}
		else if (solo)
		{
			int have = mine == null ? 0 : integer(mine, "progress", 0);
			if (target > 1) addBar(body, have, target, "You: " + have + "/" + target);
			addSoloLeader(body, progress);
		}
		else if (individual)
		{
			int have = mine == null ? 0 : integer(mine, "progress", 0);
			int need = mine == null ? 1 : integer(mine, "target", 1);
			if (mine != null && need > 1 && !bool(mine, "completed")) addBar(body, have, need, "You: " + have + "/" + need);
			body.add(teammatesLine(mine, value, target));
		}
		else if (target > 1) addBar(body, value, target, value + " / " + target);
		if (!individual && !solo && (!progress.has("sets") || completed))
		{
			String amounts = contributorsHtml(progress, " · ", CARD_CONTRIBUTORS, boardDisplayName);
			if (!amounts.isEmpty()) body.add(TsgHubSidebarPanel.cardNoteHtml(amounts));
		}
		List<JsonObject> claims = completed ? new ArrayList<>() : TsgHubTileClaims.forTask(boardEvent, taskId);
		if (!claims.isEmpty()) body.add(TsgHubSidebarPanel.cardNoteHtml("Claimed: " + claimNames(claims, CARD_CLAIMS)));
		addLineSpacing(body);
		addScopeIcon(body, solo, individual);
		card.add(body, BorderLayout.CENTER);
		JsonObject myClaim = completed ? null : TsgHubTileClaims.mine(boardEvent, taskId);
		if (myClaim != null) highlightSelf(card);
		if (TsgHubTileClaims.claimable(boardEvent, ownTeamId(), progress)) addClaimMenu(card, taskId, str(task, "title"), myClaim);
		lazyTooltip(card, () -> taskTooltip(task, progress, truncated(name)));
		return fitHeight(card);
	}

	private String claimName(JsonObject claim)
	{
		String name = escape(str(claim, "displayName"));
		return bool(claim, "mine") ? selfName(name) : nameHtml(str(claim, "displayName"), boardDisplayName);
	}

	private String claimNames(List<JsonObject> claims, int max)
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < Math.min(max, claims.size()); i++) names.add(claimName(claims.get(i)));
		if (claims.size() > max) names.add("+" + (claims.size() - max));
		return String.join(", ", names);
	}

	private void addClaimMenu(JPanel card, String taskId, String title, JsonObject myClaim)
	{
		JPopupMenu menu = new JPopupMenu();
		if (myClaim == null)
		{
			JMenuItem claim = new JMenuItem("Claim tile");
			claim.addActionListener(e -> promptClaim(taskId, title, null));
			menu.add(claim);
		}
		else
		{
			JMenuItem note = new JMenuItem("Change note");
			note.addActionListener(e -> promptClaim(taskId, title, myClaim));
			menu.add(note);
			JMenuItem drop = new JMenuItem("Drop claim");
			drop.addActionListener(e -> sendClaim(taskId, null));
			menu.add(drop);
		}
		card.setComponentPopupMenu(menu);
		eachDeep(card, c -> { if (c != card && c instanceof JComponent) ((JComponent) c).setInheritsPopupMenu(true); });
	}

	private void promptClaim(String taskId, String title, JsonObject current)
	{
		String previous = current == null ? "" : str(current, "note");
		Object input = JOptionPane.showInputDialog(shell, "Note for your team (optional, up to " + TsgHubTileClaims.MAX_NOTE_LENGTH + " characters)",
			current == null ? "Claim " + title : "Change note", JOptionPane.PLAIN_MESSAGE, null, null, previous);
		if (input == null) return;
		String note = TsgHubTileClaims.cleanNote(input.toString());
		if (note == null)
		{
			shell.setStatus("Keep the note to " + TsgHubTileClaims.MAX_NOTE_LENGTH + " characters.", Tone.ERROR);
			return;
		}
		if (current != null && note.equals(previous)) return;
		sendClaim(taskId, note);
	}

	private void sendClaim(String taskId, String note)
	{
		if (boardEvent == null) return;
		String eventId = str(boardEvent, "id");
		boolean sent = note == null ? plugin.sessions().dropTileClaim(eventId, taskId) : plugin.sessions().claimTile(eventId, taskId, note);
		if (!sent) return;
		boardEvent = TsgHubTileClaims.withClaim(boardEvent, taskId, ownTeamId(), boardDisplayName, note);
		renderTasks();
	}

	private static void addScopeIcon(JPanel body, boolean solo, boolean individual)
	{
		JLabel scope = new JLabel(icon("scope" + (solo ? 1 : individual ? 3 : 2), MUTED));
		scope.setToolTipText(solo ? "Solo: one player completes it" : individual ? "Everyone: each teammate completes it" : "Team: progress is shared");
		JPanel corner = panel(new BorderLayout());
		corner.add(scope, BorderLayout.SOUTH);
		int last = body.getComponentCount() - 1;
		Component line = last >= 0 && body.getComponent(last) instanceof JLabel ? body.getComponent(last) : new JLabel();
		if (line.getParent() == body) body.remove(last);
		body.add(fitHeight(row(line, corner)));
	}

	private JLabel teammatesLine(JsonObject mine, int value, int target)
	{
		String count = value + " of " + target + " teammates done";
		if (mine == null || !bool(mine, "completed")) return caption(count);
		return label(html("<font color='" + toHexColor(SUCCESS) + "'>You're done</font> · " + count, TsgHubSidebarPanel.CARD_TEXT_W), MUTED, smallFont());
	}

	private boolean addSetProgress(JPanel body, JsonObject source, boolean mine)
	{
		if (source == null || !source.has("sets")) return false;
		if (bool(source, "completed")) return true;
		List<JsonObject> sets = objects(array(source, "sets"));
		List<JsonObject> shown = new ArrayList<>();
		for (JsonObject set : sets) if (integer(set, "have", 0) > 0) shown.add(set);
		if (shown.isEmpty() && !sets.isEmpty()) shown.add(sets.get(0));
		for (JsonObject set : shown)
		{
			int have = integer(set, "have", 0);
			int target = integer(set, "target", 1);
			ProgressBar bar = progressBar(have, target);
			bar.setCenterLabel((mine ? "You: " : "") + str(set, "name") + " " + have + "/" + target);
			body.add(gap(GAP_S));
			body.add(bar);
			body.add(gap(GAP_XS));
			List<String> needed = strings(array(set, "needed"));
			if (!needed.isEmpty()) body.add(TsgHubSidebarPanel.cardNote("Need: " + String.join(", ", needed)));
		}
		int notStarted = sets.size() - shown.size();
		if (notStarted > 0) body.add(caption("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started"));
		return true;
	}

	private static void addLineSpacing(JPanel body)
	{
		Component[] parts = body.getComponents();
		body.removeAll();
		int first = 0;
		while (first < parts.length && parts[first] instanceof Box.Filler) first++;
		for (int i = first; i < parts.length; i++)
		{
			body.add(parts[i]);
			boolean next = i + 1 < parts.length;
			if (next && parts[i] instanceof JLabel && parts[i + 1] instanceof JLabel) body.add(gap(GAP_XS));
		}
	}

	private String taskTooltip(JsonObject task, JsonObject progress, boolean titleCut)
	{
		boolean completed = bool(progress, "completed");
		boolean individual = "individual".equals(str(task, "scope"));
		boolean solo = "solo".equals(str(task, "scope"));
		String head = titleCut ? "<b>" + escape(str(task, "title")) + "</b>" : "";
		String description = str(task, "description").trim();
		if (completed && !description.isEmpty()) head += (head.isEmpty() ? "" : "<br>") + escape(description).replace("\n", "<br>");

		String sections = "";
		if (!completed && !individual && !solo) sections += setSections(progress, head.isEmpty());
		if (individual || solo) sections += memberSections(progress, solo, head.isEmpty());
		else if (sections.isEmpty() && array(progress, "contributors").size() > CARD_CONTRIBUTORS)
		{
			sections += tipSection(head.isEmpty(), tipLine(MUTED, "<b>CONTRIBUTORS</b>")) + tipCard(CARD_HOVER, contributorsHtml(progress, "<br>", 50, boardDisplayName));
		}
		if (!completed) sections += claimSection(str(task, "id"), head.isEmpty() && sections.isEmpty());
		if (head.isEmpty() && sections.isEmpty()) return null;
		return html("<div style='padding:2px'>" + head + sections + "</div>", 240);
	}

	private String claimSection(String taskId, boolean first)
	{
		List<JsonObject> claims = TsgHubTileClaims.forTask(boardEvent, taskId);
		if (claims.isEmpty()) return "";
		List<String> lines = new ArrayList<>();
		for (JsonObject claim : claims)
		{
			String note = str(claim, "note");
			lines.add(claimName(claim) + (note.isEmpty() ? "" : "<br>" + tipLine(MUTED, escape(note))));
		}
		return tipSection(first, tipLine(MUTED, "<b>CLAIMED</b> " + claims.size())) + tipCard(ACCENT, String.join("<br>", lines));
	}

	private String setSections(JsonObject progress, boolean first)
	{
		StringBuilder out = new StringBuilder();
		for (JsonObject set : objects(array(progress, "sets")))
		{
			if (integer(set, "have", 0) == 0) continue;
			List<String> found = new ArrayList<>();
			for (JsonObject piece : objects(array(set, "found")))
			{
				String by = str(piece, "by");
				found.add(escape(str(piece, "piece")) + (by.isEmpty() ? "" : " (" + nameHtml(by, boardDisplayName) + ")"));
			}
			if (!found.isEmpty()) out.append(tipCard(ACCENT, "<b>" + escape(str(set, "name")) + "</b><br>" + String.join(", ", found)));
		}
		return out.length() == 0 ? "" : tipSection(first, tipLine(MUTED, "<b>FOUND</b>")) + out;
	}

	private String memberSections(JsonObject progress, boolean solo, boolean first)
	{
		List<JsonObject> members = objects(array(progress, "members"));
		if (members.isEmpty()) return "";
		members.sort((a, b) -> integer(b, "progress", 0) - integer(a, "progress", 0));
		List<String> done = new ArrayList<>();
		List<String> waiting = new ArrayList<>();
		for (JsonObject member : members)
		{
			String name = nameHtml(str(member, "displayName"), boardDisplayName);
			int have = integer(member, "progress", 0);
			int need = integer(member, "target", 1);
			if (solo) waiting.add(name + " " + tipLine(MUTED, have + "/" + need));
			else if (bool(member, "completed")) done.add(name);
			else waiting.add(need > 1 ? name + " " + tipLine(MUTED, have + "/" + need) : name);
		}
		if (solo) return tipSection(first, tipLine(MUTED, "<b>STANDINGS</b>")) + tipCard(CARD_HOVER, String.join("<br>", waiting));
		String out = "";
		if (!done.isEmpty()) out += tipSection(first, tipLine(SUCCESS, "<b>DONE</b> ") + tipLine(MUTED, String.valueOf(done.size()))) + tipCard(SUCCESS, String.join("<br>", done));
		if (!waiting.isEmpty()) out += tipSection(first && done.isEmpty(), tipLine(MUTED, "<b>STILL NEED</b> " + waiting.size())) + tipCard(CARD_HOVER, String.join("<br>", waiting));
		return out;
	}

	private void addSoloLeader(JPanel body, JsonObject progress)
	{
		String leader = str(progress, "leader");
		if (leader.isEmpty()) return;
		body.add(caption("Leader: " + leader + " (" + integer(progress, "progress", 0) + "/" + integer(progress, "target", 1) + ")"));
	}

	private static String contributorsHtml(JsonObject progress, String separator, int max, String self)
	{
		JsonArray contributors = array(progress, "contributors");
		if (contributors.size() == 0) return "";
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < Math.min(max, contributors.size()); i++)
		{
			JsonObject entry = contributors.get(i).getAsJsonObject();
			parts.add(nameHtml(str(entry, "displayName"), self) + " " + integer(entry, "amount", 1));
		}
		if (contributors.size() > max) parts.add("+" + (contributors.size() - max));
		return String.join(separator, parts);
	}

	private void renderScoreboard()
	{
		scoreboardTab.removeAll();
		if (bool(boardEvent, "hideScores"))
		{
			renderHiddenScoreboard();
			return;
		}
		List<JsonObject> ranked = rankedTeams(boardEvent);
		JsonArray scores = array(boardEvent, "teamScores");
		int totalTasks = array(boardEvent, "tasks").size();
		String ownTeam = ownTeamId();
		if (ranked.isEmpty()) scoreboardTab.add(shell.errorPanel("No teams yet", "Teams appear here once an admin adds them."));
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
			text.add(wrapped(str(team, "name"), TEXT, boldFont(), 120));
			text.add(caption(integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks"));
			card.add(text, BorderLayout.CENTER);
			card.add(label(integer(score, "points", 0) + " pts", i == 0 ? ACCENT : TEXT, boldFont()), BorderLayout.EAST);
			addCard(scoreboardTab, fitHeight(card));
		}
		refresh(scoreboardTab);
	}

	private void renderHiddenScoreboard()
	{
		scoreboardTab.add(shell.errorPanel("Scores are hidden", "The admins are keeping scores secret for now. Your own team's progress is on the Tasks tab."));
		List<JsonObject> teams = objects(array(boardEvent, "teams"));
		teams.sort((a, b) -> str(a, "name").compareToIgnoreCase(str(b, "name")));
		String ownTeam = ownTeamId();
		for (JsonObject team : teams)
		{
			boolean mine = str(team, "id").equals(ownTeam);
			JPanel card = card();
			if (mine) highlightSelf(card);
			card.add(wrapped(str(team, "name"), TEXT, plainFont(), TsgHubSidebarPanel.CARD_TEXT_W), BorderLayout.CENTER);
			addCard(scoreboardTab, fitHeight(card));
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
		teamTab.add(gap(GAP_S));
		for (String name : teammates)
		{
			JPanel row = card();
			if (samePlayer(name, boardDisplayName)) highlightSelf(row);
			row.add(wrapped(name, TEXT, plainFont(), TsgHubSidebarPanel.CARD_TEXT_W), BorderLayout.CENTER);
			addCard(teamTab, fitHeight(row));
		}
		teamTab.add(gap(GAP_L));
		JButton leave = onClick(dangerButton("Disconnect from event"), () -> confirmLeave());
		teamTab.add(fitHeight(leave));
		teamTab.add(gap(GAP_S));
		teamTab.add(TsgHubSidebarPanel.hint("Stops tracking on this device. Your team keeps its progress and you can rejoin with the same code."));
		refresh(teamTab);
	}

	private void confirmLeave()
	{
		if (boardEvent == null) return;
		if (confirm(shell, "Disconnect from event", "Stop tracking \"" + eventName(boardEvent) + "\" on this device?\nYour team keeps its progress and you can rejoin with your team code.", "Disconnect", true))
			plugin.sessions().leaveEvent(str(boardEvent, "id"));
	}

	private void addBar(JPanel body, int value, int max, String label)
	{
		ProgressBar bar = progressBar(value, max);
		bar.setCenterLabel(label);
		body.add(gap(GAP_S));
		body.add(bar);
		body.add(gap(GAP_S));
	}
}
