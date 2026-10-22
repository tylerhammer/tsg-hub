package com.tsghub;

import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

final class TsgHubMemberAdmin
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;
	private final Feedback feedback;

	TsgHubMemberAdmin(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		feedback = new Feedback()
		{
			@Override public void busy(boolean busy) { shell.setBusy(busy); }
			@Override public void status(String message, Tone tone) { shell.setStatus(message, tone); }
		};
	}

	void addNoteItems(JPopupMenu menu, JsonObject member)
	{
		String name = str(member, "displayName");
		String altOf = str(member, "altOf");
		String note = str(member, "note");
		boolean alt = TsgHubRoster.isAltRank(str(member, "rank"));
		int active = TsgHubRoster.activeWarnings(array(member, "warnings")).size();
		menu.addSeparator();
		JMenuItem edit = new JMenuItem(alt ? "Edit alt and admin note" : "Edit admin note");
		edit.addActionListener(e -> editNote(shell, plugin.presence(), name, alt, altOf, note, mainCandidates(name), feedback));
		menu.add(edit);
		menu.addSeparator();
		JMenuItem warn = new JMenuItem("Add warning...");
		warn.addActionListener(e -> addWarning(shell, plugin.presence(), name, active, feedback));
		menu.add(warn);
	}

	private List<String> mainCandidates(String alt)
	{
		Map<String, String> ranks = new LinkedHashMap<>();
		for (JsonArray list : new JsonArray[] {shell.membersView.members, shell.membersView.offlineMembers})
		{
			if (list == null) continue;
			for (JsonObject member : objects(list)) ranks.put(str(member, "displayName"), str(member, "rank"));
		}
		return mainCandidates(alt, ranks);
	}

	static final int MAX_NOTE_LENGTH = 500;
	static final int MAX_WARNING_LENGTH = 300;
	private static final int[] EXPIRY_DAYS = {30, 90, 0};

	interface Feedback
	{
		void busy(boolean busy);

		void status(String message, Tone tone);
	}

	static List<String> mainCandidates(String alt, Map<String, String> ranksByName)
	{
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, String> entry : ranksByName.entrySet())
		{
			if (!PlayerNames.samePlayer(entry.getKey(), alt) && !TsgHubRoster.isAltRank(entry.getValue())) names.add(entry.getKey());
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	static void editNote(Component parent, TsgHubPresence presence, String name, boolean alt, String altOf, String note, List<String> candidates,
		Feedback feedback)
	{
		TsgHubMemberPicker main = new TsgHubMemberPicker(candidates, altOf);
		JTextArea notes = new JTextArea(note, 4, 20);
		notes.setLineWrap(true);
		notes.setWrapStyleWord(true);
		JPanel form = stack();
		if (alt)
		{
			form.add(new JLabel("Alt of (main character)"));
			form.add(main);
			form.add(gap(GAP_M));
		}
		form.add(new JLabel("Admin note (only admins see this)"));
		form.add(new JScrollPane(notes));
		if (JOptionPane.showConfirmDialog(parent, form, name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		String typed = main.getText();
		String nextMain = typed.equals(altOf) ? altOf : PlayerNames.resolveName(candidates, typed);
		String nextNote = notes.getText().trim();
		if (PlayerNames.samePlayer(nextMain, name))
		{
			feedback.status("A character can't be its own alt.", Tone.ERROR);
			return;
		}
		if (nextNote.length() > MAX_NOTE_LENGTH)
		{
			feedback.status("Keep the note to " + MAX_NOTE_LENGTH + " characters.", Tone.ERROR);
			return;
		}
		if (nextMain.equals(altOf) && nextNote.equals(note)) return;
		after(presence.saveNote(name, nextMain, nextNote), "Saved " + name + ".", feedback);
	}

	static void addWarning(Component parent, TsgHubPresence presence, String name, int active, Feedback feedback)
	{
		JTextArea reason = new JTextArea(3, 22);
		reason.setLineWrap(true);
		reason.setWrapStyleWord(true);
		JComboBox<String> expiry = new JComboBox<>(new String[] {"After 30 days", "After 90 days", "Never"});
		expiry.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel form = stack();
		form.add(label(active == 0 ? name + " has no active warnings." : name + " has " + active + " active warning" + (active == 1 ? "." : "s."), MUTED, smallFont()));
		form.add(gap(GAP_M));
		form.add(new JLabel("Reason"));
		form.add(new JScrollPane(reason));
		form.add(gap(GAP_M));
		form.add(new JLabel("Expires"));
		form.add(expiry);
		form.add(gap(GAP_M));
		form.add(label("Only admins see warnings.", MUTED, smallFont()));
		Object[] options = {"Add warning", "Cancel"};
		if (JOptionPane.showOptionDialog(parent, form, "Warn " + name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) != 0) return;
		String text = reason.getText().trim();
		if (text.isEmpty())
		{
			feedback.status("Add a reason for the warning.", Tone.ERROR);
			return;
		}
		if (text.length() > MAX_WARNING_LENGTH)
		{
			feedback.status("Keep the reason to " + MAX_WARNING_LENGTH + " characters.", Tone.ERROR);
			return;
		}
		after(presence.addWarning(name, text, EXPIRY_DAYS[expiry.getSelectedIndex()]), "Warned " + name + ".", feedback);
	}

	private static void after(CompletableFuture<?> write, String success, Feedback feedback)
	{
		feedback.busy(true);
		write.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
			feedback.busy(false);
			if (error == null) feedback.status(success, Tone.SUCCESS);
			else feedback.status("Couldn't save. " + TsgHubFormat.friendlyError(error), Tone.ERROR);
		}));
	}
}
