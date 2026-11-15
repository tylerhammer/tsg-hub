package com.tsghub;

import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import lombok.RequiredArgsConstructor;
import net.runelite.client.util.SwingUtil;

@RequiredArgsConstructor
final class TsgHubNewsPoll
{
	private final TsgHubPlugin plugin;
	private final TsgHubAnnouncements announcements;
	private final Runnable changed;
	private final Map<String, Set<Integer>> picks = new HashMap<>();
	private final Map<String, String> errors = new HashMap<>();
	private final Set<String> voting = new HashSet<>();

	void reset(JsonArray announcements)
	{
		Set<String> ids = new HashSet<>();
		for (JsonObject announcement : objects(announcements)) ids.add(str(announcement, "id"));
		picks.keySet().retainAll(ids);
		errors.keySet().retainAll(ids);
		voting.clear();
	}

	static String summary(JsonObject poll)
	{
		return "Poll · " + (bool(poll, "multi") ? "pick any" : "pick one") + (bool(poll, "anonymous") ? " · anonymous" : "")
			+ " · " + plural(integer(poll, "voters", 0), "voter");
	}

	JPanel block(String id, JsonObject poll, int widthPx)
	{
		JPanel block = stack();
		boolean multi = bool(poll, "multi");
		boolean canVote = !plugin.clan().accountHash().isEmpty();
		Set<Integer> mine = new TreeSet<>(TsgHubAnnouncements.myVote(poll));
		Set<Integer> chosen = picks.computeIfAbsent(id, k -> new TreeSet<>(mine));
		int voters = integer(poll, "voters", 0);
		JButton vote = primaryButton(mine.isEmpty() ? "Vote" : "Change vote");
		Runnable sync = () -> vote.setEnabled(canVote && !chosen.isEmpty() && !chosen.equals(mine) && !voting.contains(id));
		ButtonGroup group = new ButtonGroup();
		block.add(gap(GAP_S));
		block.add(wrapped(summary(poll), MUTED, smallFont(), widthPx));
		List<JsonObject> options = objects(array(poll, "options"));
		for (int i = 0; i < options.size(); i++)
		{
			JsonObject option = options.get(i);
			int index = i;
			int votes = integer(option, "votes", 0);
			AbstractButton toggle = plain(multi ? new JCheckBox() : new JRadioButton(), TEXT);
			String count = voters == 0 ? "0" : votes + " · " + Math.round(votes * 100f / voters) + "%";
			toggle.setText(html(escape(str(option, "label")), widthPx - 70));
			toggle.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			toggle.setIconTextGap(6);
			toggle.setFont(smallFont());
			toggle.setSelected(chosen.contains(index));
			toggle.setEnabled(canVote);
			Runnable pick = () -> {
				if (!multi) chosen.clear();
				if (toggle.isSelected()) chosen.add(index);
				else chosen.remove(index);
				sync.run();
			};
			toggle.addActionListener(e -> pick.run());
			pressable(toggle, point -> {
				toggle.setSelected(!multi || !toggle.isSelected());
				pick.run();
			});
			if (!multi) group.add(toggle);
			List<String> names = strings(array(option, "voters"));
			if (!names.isEmpty()) toggle.setToolTipText(html(escape(String.join(", ", names)), 200));
			block.add(gap(GAP_S));
			block.add(fitHeight(row(toggle, north(caption(count)))));
			JPanel bar = progressBar(votes, Math.max(voters, 1), null, null);
			bar.setBorder(BorderFactory.createEmptyBorder(0, toggle.getPreferredSize().height - 2, 0, 0));
			block.add(bar);
		}
		String error = errors.get(id);
		if (error != null) block.add(label(html(escape(error), widthPx), TsgHubTheme.ERROR, smallFont()));
		Runnable cast = () -> {
			voting.add(id);
			errors.remove(id);
			vote.setEnabled(false);
			announcements.vote(id, new ArrayList<>(chosen), message -> {
				voting.remove(id);
				errors.put(id, message);
				changed.run();
			});
		};
		vote.addActionListener(e -> cast.run());
		pressable(vote, point -> cast.run());
		sync.run();
		block.add(gap(GAP_M));
		block.add(fitHeight(row(canVote && !mine.isEmpty() ? removeVote(id) : label(canVote ? "" : "Log in to vote.", MUTED, smallFont()), vote)));
		return block;
	}

	private JButton removeVote(String id)
	{
		JButton remove = new JButton("Remove vote");
		remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		SwingUtil.removeButtonDecorations(remove);
		remove.setForeground(MUTED);
		remove.setFont(smallFont());
		remove.setHorizontalAlignment(JButton.LEFT);
		remove.setBorder(BorderFactory.createEmptyBorder());
		remove.setEnabled(!voting.contains(id));
		Runnable undo = () -> {
			voting.add(id);
			errors.remove(id);
			picks.remove(id);
			remove.setEnabled(false);
			announcements.unvote(id, message -> {
				voting.remove(id);
				errors.put(id, message);
				changed.run();
			});
		};
		remove.addActionListener(e -> undo.run());
		return pressable(remove, point -> undo.run());
	}
}
