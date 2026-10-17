package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

final class TsgHubMemberAdmin
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private static final int MAX_WARNING_LENGTH = 300;
	private static final int[] EXPIRY_DAYS = {30, 90, 0};

	TsgHubMemberAdmin(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	void addNoteItems(javax.swing.JPopupMenu menu, JsonObject member)
	{
		String name = str(member, "displayName");
		String altOf = str(member, "altOf");
		String note = str(member, "note");
		boolean alt = TsgHubRoster.isAltRank(str(member, "rank"));
		List<JsonObject> active = TsgHubRoster.activeWarnings(array(member, "warnings"));
		menu.addSeparator();
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
		form.add(Box.createVerticalStrut(GAP_M));
		form.add(new JLabel("Reason"));
		form.add(new javax.swing.JScrollPane(reason));
		form.add(Box.createVerticalStrut(GAP_M));
		form.add(new JLabel("Expires"));
		form.add(expiry);
		form.add(Box.createVerticalStrut(GAP_M));
		form.add(label("Only admins see warnings.", MUTED, smallFont()));
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
			String meta = TsgHubFormat.shortDate(str(warning, "issuedAt"), now, zone) + " · " + escape(str(warning, "issuedBy"));
			javax.swing.JRadioButton choice = plain(new javax.swing.JRadioButton(html(escape(str(warning, "reason")) + "<br>" + tipLine(MUTED, meta), 220)), TEXT);
			choice.setSelected(choices.isEmpty());
			group.add(choice);
			choices.add(choice);
			form.add(choice);
			form.add(Box.createVerticalStrut(GAP_S));
		}
		JTextField reason = new JTextField(22);
		form.add(Box.createVerticalStrut(GAP_S));
		form.add(new JLabel("Reason for revoking (optional)"));
		form.add(reason);
		Object[] options = {"Revoke", "Cancel"};
		if (JOptionPane.showOptionDialog(shell, form, "Revoke a warning for " + name, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) != 0) return;
		for (int i = 0; i < choices.size(); i++)
		{
			if (choices.get(i).isSelected()) plugin.presence().revokeWarning(name, str(active.get(i), "id"), reason.getText().trim());
		}
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
			form.add(Box.createVerticalStrut(GAP_M));
		}
		form.add(new JLabel("Admin note (only admins see this)"));
		form.add(new javax.swing.JScrollPane(notes));
		if (JOptionPane.showConfirmDialog(shell, form, name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		String typed = main.getText();
		String nextMain = typed.equals(altOf) ? altOf : PlayerNames.resolveName(candidates, typed);
		String nextNote = notes.getText().trim();
		if (samePlayer(nextMain, name))
		{
			shell.setStatus("A character can't be its own alt.", Tone.ERROR);
			return;
		}
		if (nextNote.length() > 500)
		{
			shell.setStatus("Keep the note to 500 characters.", Tone.ERROR);
			return;
		}
		if (nextMain.equals(altOf) && nextNote.equals(note)) return;
		plugin.presence().saveNote(name, nextMain, nextNote);
	}

	private List<String> mainCandidates(String alt)
	{
		List<String> names = new ArrayList<>();
		for (JsonArray list : new JsonArray[] {shell.membersView.members, shell.membersView.offlineMembers})
		{
			if (list == null) continue;
			for (int i = 0; i < list.size(); i++)
			{
				JsonObject member = list.get(i).getAsJsonObject();
				String candidate = str(member, "displayName");
				if (!samePlayer(candidate, alt) && !TsgHubRoster.isAltRank(str(member, "rank"))) names.add(candidate);
			}
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}
}
