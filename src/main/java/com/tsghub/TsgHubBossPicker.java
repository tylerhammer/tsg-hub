package com.tsghub;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

final class TsgHubBossPicker extends JPanel
{
	static final List<String> BOSSES = bossNames();

	private final JTextField field = new JTextField();
	private final DefaultListModel<String> results = new DefaultListModel<>();
	private final JList<String> resultList = new JList<>(results);
	private final JScrollPane resultScroll = new JScrollPane(resultList);
	private final JLabel hint = TsgHubUi.caption("");
	private final Runnable onChosen;

	TsgHubBossPicker(String caption, Runnable onChosen)
	{
		this.onChosen = onChosen;
		setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		JLabel label = TsgHubUi.caption(caption);
		add(label);
		add(Box.createVerticalStrut(TsgHubTheme.GAP_XS));

		TsgHubUi.placeholder(field, "e.g. Vorkath");
		field.setAlignmentX(LEFT_ALIGNMENT);
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { filter(); }
			@Override public void removeUpdate(DocumentEvent e) { filter(); }
			@Override public void changedUpdate(DocumentEvent e) { filter(); }
		});
		field.addActionListener(e -> {
			if (resultScroll.isVisible() && !results.isEmpty()) choose(results.get(Math.max(0, resultList.getSelectedIndex())));
		});
		field.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent e)
			{
				if (!resultScroll.isVisible() || results.isEmpty()) return;
				int index = resultList.getSelectedIndex();
				if (e.getKeyCode() == KeyEvent.VK_DOWN) index = Math.min(results.size() - 1, index + 1);
				else if (e.getKeyCode() == KeyEvent.VK_UP) index = Math.max(0, index - 1);
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { hideResults(); return; }
				else return;
				resultList.setSelectedIndex(index);
				resultList.ensureIndexIsVisible(index);
				e.consume();
			}
		});
		add(TsgHubUi.fitHeight(field));

		resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultList.setVisibleRowCount(6);
		resultList.setBackground(TsgHubTheme.CARD);
		resultList.setCellRenderer((list, value, index, selected, focus) -> {
			JLabel row = new JLabel(value);
			row.setOpaque(true);
			row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
			row.setBackground(selected ? TsgHubTheme.CARD_HOVER : TsgHubTheme.CARD);
			row.setForeground(TsgHubTheme.TEXT);
			return row;
		});
		resultList.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e)
			{
				int index = resultList.locationToIndex(e.getPoint());
				if (index >= 0 && resultList.getCellBounds(index, index).contains(e.getPoint())) choose(results.get(index));
			}
		});
		resultScroll.setBorder(BorderFactory.createLineBorder(TsgHubTheme.BORDER));
		resultScroll.setAlignmentX(LEFT_ALIGNMENT);
		resultScroll.setVisible(false);
		add(resultScroll);
		hint.setText(defaultHint());
		add(hint);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
	}

	String getText()
	{
		return field.getText().trim();
	}

	void setText(String name)
	{
		field.setText(name == null ? "" : name);
		hideResults();
	}

	JTextField field()
	{
		return field;
	}

	private void filter()
	{
		String query = field.getText().trim().toLowerCase(Locale.ROOT);
		results.clear();
		if (query.isEmpty() || BOSSES.stream().anyMatch(b -> b.equalsIgnoreCase(query)))
		{
			hideResults();
			if (!query.isEmpty()) hint.setText("Counts \"" + field.getText().trim() + "\" kill count messages.");
			return;
		}
		List<String> prefix = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String boss : BOSSES)
		{
			String lower = boss.toLowerCase(Locale.ROOT);
			if (lower.startsWith(query) || lower.contains(" " + query)) prefix.add(boss);
			else if (lower.contains(query)) contains.add(boss);
		}
		prefix.addAll(contains);
		for (String boss : prefix) results.addElement(boss);
		resultList.setVisibleRowCount(Math.max(1, Math.min(6, results.size())));
		resultScroll.setVisible(!results.isEmpty());
		if (!results.isEmpty()) resultList.setSelectedIndex(0);
		hint.setText(results.isEmpty()
			? "Not in RuneLite's boss list. It will still count if this name appears in the kill count message."
			: "Click a boss (or press Enter) to pick it.");
		revalidate();
		repaint();
	}

	private void choose(String boss)
	{
		field.setText(boss);
		hideResults();
		hint.setText("Counts \"" + boss + "\" kill count messages.");
		if (onChosen != null) onChosen.run();
	}

	private void hideResults()
	{
		resultScroll.setVisible(false);
		hint.setText(defaultHint());
		revalidate();
	}

	private static String defaultHint()
	{
		return "Type to search bosses, or enter any name shown in its kill count message.";
	}

	private static List<String> bossNames()
	{
		List<String> names = new ArrayList<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() != HiscoreSkillType.BOSS) continue;
			String name = skill.getName();
			if (name.startsWith("Chambers of Xeric") || name.startsWith("Theatre of Blood") || name.startsWith("Tombs of Amascut")) continue;
			names.add(name);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}
}
