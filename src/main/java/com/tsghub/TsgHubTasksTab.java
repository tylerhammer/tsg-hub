package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

final class TsgHubTasksTab
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	private static final int MAX_RECONCILE_LINES = 10;

	final WidthTrackingPanel tasksTab = new WidthTrackingPanel();

	TsgHubTasksTab(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	void taskDeleted(String taskId)
	{
		if (taskId.equals(shell.taskEditor.editingTaskId)) shell.taskEditor.finishTaskEdit();
	}

	private static boolean reconcilable(JsonObject task)
	{
		JsonObject config = object(task, "config");
		String type = str(task, "type");
		return "drop".equals(type) || "kill".equals(type) && !"chat".equals(str(config, "signal"));
	}

	void confirmReconcile(String taskId, JsonObject preview)
	{
		JsonArray claims = array(preview, "claims");
		if (claims.size() == 0)
		{
			JOptionPane.showMessageDialog(shell, "No logged loot to credit for this task.", "Reconcile", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		StringBuilder message = new StringBuilder("Credit " + claims.size() + (claims.size() == 1 ? " match" : " matches") + " from the loot log to this task?\n\n");
		int shown = Math.min(claims.size(), MAX_RECONCILE_LINES);
		for (int i = 0; i < shown; i++)
		{
			JsonObject claim = claims.get(i).getAsJsonObject();
			message.append(str(claim, "displayName")).append(" (").append(str(claim, "teamName")).append("): ")
				.append(str(claim, "name")).append(" x").append(integer(claim, "quantity", 1)).append("\n");
		}
		if (claims.size() > shown) message.append("...and ").append(claims.size() - shown).append(" more\n");
		int choice = JOptionPane.showConfirmDialog(shell, message.toString(), "Reconcile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice == JOptionPane.OK_OPTION) plugin.organizer().reconcileTask(taskId, false);
	}

	private void confirmDeleteTask(JsonObject task)
	{
		String message = "Delete task \"" + str(task, "title") + "\"?\n\n"
			+ "Every team's progress and claims for it will be deleted.\nThis can't be undone.";
		if (confirm(shell, "Delete task", message, "Delete task", true)) plugin.organizer().deleteTask(str(task, "id"));
	}

	void renderTasks()
	{
		tasksTab.removeAll();
		JPanel top = panel(new BorderLayout());
		JsonArray tasks = array(shell.currentEvent, "tasks");
		top.add(caption(tasks.size() + (tasks.size() == 1 ? " task" : " tasks") + ", shared by every team"), BorderLayout.CENTER);
		JButton add = onClick(primaryButton("New task"), () -> shell.taskEditor.beginNewTask());
		top.add(add, BorderLayout.EAST);
		tasksTab.add(fitHeight(top));
		tasksTab.add(gap(GAP_M));
		if (tasks.size() == 0)
		{
			tasksTab.add(wrapped("No tasks yet. Choose New task to add the first one.", MUTED, plainFont(), TsgHubPanel.DETAIL_TEXT_W));
		}
		for (JsonObject task : objects(tasks))
		{
			JPanel card = card();
			JPanel text = stack();
			text.add(boldLabel(str(task, "title")));
			JPanel meta = panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			meta.add(badge(integer(task, "points", 1) + " pts", ACCENT));
			meta.add(badge(taskTypeLabel(task), MUTED));
			String scopeTag = "individual".equals(str(task, "scope")) ? "Everyone" : "solo".equals(str(task, "scope")) ? "Solo" : "Team";
			meta.add(badge(scopeTag, MUTED));
			String summary = taskSummary(task);
			if (!summary.isEmpty()) meta.add(caption(summary));
			text.add(meta);
			String description = str(task, "description");
			if (!description.isEmpty()) text.add(wrapped(description, MUTED, smallFont(), TsgHubPanel.DETAIL_TEXT_W - 80));
			card.add(text, BorderLayout.CENTER);
			JButton edit = onClick(button("Edit"), () -> shell.taskEditor.beginTaskEdit(task));
			JButton delete = onClick(iconButton(hover("trash", TsgHubTheme.ERROR), "Delete task"), () -> confirmDeleteTask(task));
			JPanel taskButtons = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			if (reconcilable(task))
			{
				JButton reconcile = button("Reconcile");
				reconcile.setToolTipText("Credit matching loot players received earlier in this event");
				reconcile.addActionListener(e -> plugin.organizer().reconcileTask(str(task, "id"), true));
				taskButtons.add(reconcile);
			}
			taskButtons.add(edit);
			taskButtons.add(delete);
			card.add(north(taskButtons), BorderLayout.EAST);
			addCard(tasksTab, fitHeight(card));
		}
		refresh(tasksTab);
	}

	private String taskSummary(JsonObject task)
	{
		JsonObject config = object(task, "config");
		int count = integer(config, "targetCount", 1);
		switch (str(task, "type"))
		{
			case "kill": return count + "x " + str(config, "npcName");
			case "raid":
				return count + "x " + TsgHubTaskEditor.raidSummary(strings(array(config, "modes")));
			case "drop":
				JsonArray sets = array(config, "itemGroups");
				if (sets.size() == 1) return TsgHubTaskEditor.nameOrPieces(sets.get(0).getAsJsonArray());
				if (sets.size() > 1) return "any of " + sets.size() + " sets";
				if (bool(config, "requireAllItems")) return array(config, "itemNames").size() + " pieces";
				if (!str(config, "itemGroup").isEmpty()) return count + "x";
				return count + "x from " + array(config, "itemNames").size() + " items";
			default: return "";
		}
	}
}
