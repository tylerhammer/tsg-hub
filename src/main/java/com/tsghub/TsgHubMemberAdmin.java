package com.tsghub;

import com.google.gson.*;
import com.tsghub.TsgHubUi.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

final class TsgHubMemberAdmin
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	TsgHubMemberAdmin(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	void addNoteItems(JPopupMenu menu, JsonObject member)
	{
		String name = str(member, "displayName");
		String altOf = str(member, "altOf");
		String note = str(member, "note");
		boolean alt = TsgHubRoster.isAltRank(str(member, "rank"));
		int active = TsgHubRoster.activeWarnings(array(member, "warnings")).size();
		menu.addSeparator();
		item(menu, alt ? "Edit alt and admin note" : "Edit admin note", () -> editNote(name, alt, altOf, note, mainCandidates(name)));
		menu.addSeparator();
		item(menu, "Add warning...", () -> addWarning(name, active));
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

	private void editNote(String name, boolean alt, String altOf, String note, List<String> candidates)
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
		if (JOptionPane.showConfirmDialog(shell, form, name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		String typed = main.getText();
		String nextMain = typed.equals(altOf) ? altOf : PlayerNames.resolveName(candidates, typed);
		String nextNote = notes.getText().trim();
		if (PlayerNames.samePlayer(nextMain, name))
		{
			shell.setStatus("A character can't be its own alt.", Tone.ERROR);
			return;
		}
		if (nextNote.length() > MAX_NOTE_LENGTH)
		{
			shell.setStatus("Keep the note to " + MAX_NOTE_LENGTH + " characters.", Tone.ERROR);
			return;
		}
		if (nextMain.equals(altOf) && nextNote.equals(note)) return;
		after(plugin.presence().saveNote(name, nextMain, nextNote), "Saved " + name + ".");
	}

	private void addWarning(String name, int active)
	{
		JTextArea reason = new JTextArea(3, 22);
		reason.setLineWrap(true);
		reason.setWrapStyleWord(true);
		JComboBox<String> expiry = new JComboBox<>(new String[] {"After 30 days", "After 90 days", "Never"});
		expiry.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel form = stack();
		form.add(label(active == 0 ? name + " has no active warnings." : name + " has " + active + " active warning" + (active == 1 ? "." : "s."), MUTED, smallFont()));
		addGap(form, GAP_M, new JLabel("Reason"));
		form.add(new JScrollPane(reason));
		addGap(form, GAP_M, new JLabel("Expires"));
		form.add(expiry);
		addGap(form, GAP_M, label("Only admins see warnings.", MUTED, smallFont()));
		Object[] options = {"Add warning", "Cancel"};
		if (JOptionPane.showOptionDialog(shell, form, "Warn " + name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) != 0) return;
		String text = reason.getText().trim();
		if (text.isEmpty())
		{
			shell.setStatus("Add a reason for the warning.", Tone.ERROR);
			return;
		}
		if (text.length() > MAX_WARNING_LENGTH)
		{
			shell.setStatus("Keep the reason to " + MAX_WARNING_LENGTH + " characters.", Tone.ERROR);
			return;
		}
		after(plugin.presence().addWarning(name, text, EXPIRY_DAYS[expiry.getSelectedIndex()]), "Warned " + name + ".");
	}

	private void after(CompletableFuture<?> write, String success)
	{
		shell.setBusy(true);
		write.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
			shell.setBusy(false);
			if (error == null) shell.setStatus(success, Tone.SUCCESS);
			else shell.setStatus("Couldn't save. " + TsgHubFormat.friendlyError(error), Tone.ERROR);
		}));
	}
}
