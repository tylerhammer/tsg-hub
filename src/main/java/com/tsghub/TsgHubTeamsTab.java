package com.tsghub;

import static com.tsghub.PlayerNames.*;
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

final class TsgHubTeamsTab
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	final WidthTrackingPanel teamsTab = new WidthTrackingPanel();
	private final JTextField newTeamName = new JTextField();

	String viewedTeamId = "";

	TsgHubTeamsTab(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		newTeamName.addActionListener(e -> addTeam());
		placeholder(newTeamName, "e.g. Dragon Slayers");
	}

	void teamCreated()
	{
		newTeamName.setText("");
		newTeamName.requestFocusInWindow();
	}

	private void confirmDeleteTeam(JsonObject team, int members)
	{
		String message = "Delete team \"" + str(team, "name") + "\"?\n\n"
			+ (members > 0 ? "Its " + plural(members, "member") + " will be removed from the event, and " : "")
			+ (members > 0 ? "its" : "Its") + " progress will be deleted. Its invite code stops working.\n"
			+ "This can't be undone.";
		if (confirm(shell, "Delete team", message, "Delete team", true)) plugin.organizer().deleteTeam(str(team, "id"));
	}

	void renderTeams()
	{
		teamsTab.removeAll();
		if (!viewedTeamId.isEmpty() && shell.findTeam(viewedTeamId) != null)
		{
			renderTeamDetail(shell.findTeam(viewedTeamId));
			refresh(teamsTab);
			return;
		}
		viewedTeamId = "";
		JsonArray scores = array(shell.currentEvent, "teamScores");
		int totalTasks = array(shell.currentEvent, "tasks").size();
		List<JsonObject> ranked = rankedTeams(shell.currentEvent);

		if (ranked.isEmpty())
		{
			teamsTab.add(wrapped("No teams yet. Add your first team below, then share its code with the players on it.", MUTED, plainFont(), TsgHubPanel.DETAIL_TEXT_W));
			teamsTab.add(Box.createVerticalStrut(GAP_M));
		}
		for (int i = 0; i < ranked.size(); i++)
		{
			JsonObject team = ranked.get(i);
			String teamId = str(team, "id");
			JsonObject score = scoreFor(scores, teamId);
			int members = shell.countMembers(teamId);

			JPanel card = card();
			card.setLayout(new BorderLayout(10, 4));
			card.add(rankLabel(i + 1, headingFont(), 20), BorderLayout.WEST);
			JPanel text = stack();
			text.add(boldLabel(str(team, "name")));
			text.add(caption(integer(score, "points", 0) + " pts · " + integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks"));
			text.add(caption(members + (members == 1 ? " member" : " members")));
			card.add(text, BorderLayout.CENTER);

			JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			String code = str(team, "inviteCode");
			if (!code.isEmpty())
			{
				JLabel codeLabel = label(code, ACCENT, new Font(Font.MONOSPACED, Font.BOLD, 13));
				codeLabel.setToolTipText("Team invite code");
				actions.add(codeLabel);
				JButton copy = button("Copy code");
				copy.addActionListener(e -> {
					copyToClipboard(code);
					flashText(copy, "Copied!", 1500);
				});
				actions.add(copy);
			}
			JButton rename = button("Rename");
			rename.addActionListener(e -> promptRename(teamId, str(team, "name")));
			actions.add(rename);
			JButton view = button("Details");
			view.addActionListener(e -> {
				viewedTeamId = teamId;
				renderTeams();
			});
			actions.add(view);
			JButton delete = iconButton(hover("trash", TsgHubTheme.ERROR), "Delete team");
			delete.addActionListener(e -> confirmDeleteTeam(team, members));
			actions.add(delete);
			card.add(actions, BorderLayout.EAST);
			teamsTab.add(fitHeight(card));
			teamsTab.add(Box.createVerticalStrut(LIST_GAP));
		}

		teamsTab.add(Box.createVerticalStrut(GAP_M));
		JButton add = button("Add team");
		add.addActionListener(e -> addTeam());
		JPanel addField = panel(new BorderLayout(0, 3));
		addField.add(caption("New team name"), BorderLayout.NORTH);
		addField.add(row(newTeamName, add), BorderLayout.CENTER);
		teamsTab.add(fitHeight(addField));
		refresh(teamsTab);
	}

	private void renderTeamDetail(JsonObject team)
	{
		String teamId = str(team, "id");
		JButton back = button("All teams");
		back.setIcon(hover("back"));
		back.addActionListener(e -> {
			viewedTeamId = "";
			renderTeams();
		});
		teamsTab.add(back);
		teamsTab.add(Box.createVerticalStrut(GAP_M));
		JsonObject score = scoreFor(array(shell.currentEvent, "teamScores"), teamId);
		teamsTab.add(label(str(team, "name"), TEXT, headingFont()));
		String code = str(team, "inviteCode");
		teamsTab.add(caption(integer(score, "points", 0) + " pts" + (code.isEmpty() ? "" : " · code " + code)));

		teamsTab.add(sectionTitle("Members"));
		List<String> roster = new ArrayList<>();
		for (JsonObject member : objects(array(shell.currentEvent, "members")))
			if (str(member, "teamId").equals(teamId)) roster.add(str(member, "displayName"));
		roster.sort(String.CASE_INSENSITIVE_ORDER);
		teamsTab.add(wrapped(roster.isEmpty() ? "Nobody has joined yet. Share the team code above." : String.join(", ", roster), TEXT, plainFont(), TsgHubPanel.DETAIL_TEXT_W));

		teamsTab.add(sectionTitle("Task progress"));
		JsonArray progressRows = array(score, "tasks");
		JsonArray tasks = array(shell.currentEvent, "tasks");
		if (tasks.size() == 0) teamsTab.add(label("No tasks yet.", MUTED, plainFont()));
		for (JsonObject task : objects(tasks))
		{
			String taskId = str(task, "id");
			JsonObject progress = progressFor(progressRows, taskId);
			boolean completed = bool(progress, "completed");

			JPanel card = card();
			JPanel text = stack();
			JLabel name = label(str(task, "title"), completed ? MUTED : TEXT, boldFont());
			if (completed) name.setIcon(icon("check", SUCCESS));
			text.add(name);
			String progressScope = str(progress, "scope");
			boolean perMember = "individual".equals(progressScope) || "solo".equals(progressScope);
			if (completed)
			{
				String by = str(progress, "completedBy");
				boolean creditedToOrganizer = creditedToOrganizer(progress);
				text.add(label(completedLine(progress), SUCCESS, smallFont()));
				JsonObject manual = overrideClaim(teamId, taskId);
				if (manual != null)
				{
					JsonObject evidence = manual.getAsJsonObject("evidence");
					String markedBy = str(evidence, "markedBy").isEmpty() ? str(manual, "displayName") : str(evidence, "markedBy");
					String how = !creditedToOrganizer && (markedBy.isEmpty() || samePlayer(markedBy, by)) ? "Marked complete manually" : "Marked complete by " + markedBy;
					String note = str(evidence, "note");
					text.add(wrapped(how + (note.isEmpty() ? "" : " · Reason: " + note), WARNING, smallFont(), TsgHubPanel.DETAIL_TEXT_W - 140));
				}
				if (!perMember && !contributorsText(progress, ", ", 12).isEmpty())
				{
					text.add(wrapped("Contributors: " + contributorsText(progress, ", ", 12), MUTED, smallFont(), TsgHubPanel.DETAIL_TEXT_W - 140));
				}
			}
			else if (perMember)
			{
				String leader = str(progress, "leader");
				if ("solo".equals(progressScope) && !leader.isEmpty())
					text.add(caption("Leader: " + leader + " (" + integer(progress, "progress", 0) + "/" + integer(progress, "target", 1) + ")"));
				JsonArray entries = array(progress, "members");
				if (entries.size() == 0) text.add(caption("No members yet."));
				for (JsonObject entry : objects(entries)) text.add(caption(str(entry, "displayName") + ": " + TsgHubPanel.progressText(entry)));
			}
			else
			{
				List<SetLine> sets = setLines(progress, true);
				if (!sets.isEmpty())
				{
					int started = 0;
					for (SetLine set : sets)
					{
						if (set.have == 0 && started > 0) continue;
						if (set.have > 0) started++;
						String line = set.name + " " + set.have + "/" + set.target
							+ (set.found.isEmpty() ? "" : " · have " + set.foundText())
							+ (set.needed.isEmpty() ? "" : " · need " + String.join(", ", set.needed));
						text.add(wrapped(line, MUTED, smallFont(), TsgHubPanel.DETAIL_TEXT_W - 140));
						if (set.have == 0) break;
					}
					int notStarted = sets.size() - Math.max(1, started);
					if (notStarted > 0) text.add(caption("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started"));
				}
				else
				{
					text.add(caption(TsgHubPanel.progressText(progress)));
					String amounts = contributorsText(progress, ", ", 12);
					if (!amounts.isEmpty()) text.add(wrapped("Contributors: " + amounts, MUTED, smallFont(), TsgHubPanel.DETAIL_TEXT_W - 140));
				}
			}
			card.add(text, BorderLayout.CENTER);
			if (!completed)
			{
				JButton complete = button("Mark complete");
				complete.setToolTipText("Manually complete this task for " + str(team, "name"));
				complete.addActionListener(e -> promptComplete(taskId, teamId, str(task, "title"), str(team, "name")));
				card.add(north(complete), BorderLayout.EAST);
			}
			teamsTab.add(fitHeight(card));
			teamsTab.add(Box.createVerticalStrut(LIST_GAP));
		}
	}

	private void addTeam()
	{
		String name = newTeamName.getText().trim();
		if (name.isEmpty())
		{
			shell.setStatus("Enter a team name first.", Tone.ERROR);
			newTeamName.requestFocusInWindow();
			return;
		}
		plugin.organizer().createTeam(name);
	}

	private void promptRename(String teamId, String currentName)
	{
		String name = (String) JOptionPane.showInputDialog(shell, "New team name:", "Rename team", JOptionPane.PLAIN_MESSAGE, null, null, currentName);
		if (name != null && !name.trim().isEmpty() && !name.trim().equals(currentName)) plugin.organizer().renameTeam(teamId, name.trim());
	}

	private void promptComplete(String taskId, String teamId, String taskName, String teamName)
	{
		JComboBox<String[]> credit = new JComboBox<>();
		credit.addItem(new String[] {"", "No one (admin)"});
		List<String[]> roster = new ArrayList<>();
		for (JsonObject member : objects(array(shell.currentEvent, "members")))
			if (str(member, "teamId").equals(teamId)) roster.add(new String[] {str(member, "id"), str(member, "displayName")});
		roster.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
		roster.forEach(credit::addItem);
		credit.setRenderer((list, value, index, selected, focus) -> listRow(value == null ? "" : value[1], selected));
		JTextField reason = new JTextField(28);
		placeholder(reason, "e.g. Drop confirmed in Discord screenshot");
		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.add(new JLabel("Mark \"" + taskName + "\" complete for " + teamName + "."));
		form.add(new JLabel("Credit to"));
		form.add(credit);
		form.add(new JLabel("Reason (admins only)"));
		form.add(reason);
		SwingUtilities.invokeLater(reason::requestFocusInWindow);
		while (true)
		{
			int choice = JOptionPane.showConfirmDialog(shell, form, "Mark complete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION) return;
			if (!reason.getText().trim().isEmpty()) break;
			JOptionPane.showMessageDialog(shell, "Add a short reason so other admins know why.", "Reason required", JOptionPane.WARNING_MESSAGE);
		}
		String[] picked = (String[]) credit.getSelectedItem();
		plugin.organizer().completeTask(taskId, teamId, picked == null || picked[0].isEmpty() ? null : picked[0], reason.getText().trim());
	}

	private JsonObject overrideClaim(String teamId, String taskId)
	{
		for (JsonObject claim : objects(array(shell.currentEvent, "claims")))
		{
			JsonObject evidence = object(claim, "evidence");
			if (str(claim, "teamId").equals(teamId) && str(claim, "taskId").equals(taskId)
				&& "organizer".equals(str(claim, "source")) && "approved".equals(str(claim, "status"))
				&& bool(evidence, "completedOverride")) return claim;
		}
		return null;
	}
}
