package com.tsghub;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

abstract class TsgHubSearchField extends JPanel
{
	static final int HIDDEN = -1;
	private static final int VISIBLE_ROWS = 6;

	private final JTextField field;
	private final DefaultListModel<String> results = new DefaultListModel<>();
	private final JList<String> resultList = new JList<>(results);
	private final JScrollPane resultScroll = new JScrollPane(resultList);

	TsgHubSearchField(JTextField field)
	{
		this.field = field;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
	}

	void addSearch(String placeholder)
	{
		TsgHubUi.placeholder(field, placeholder);
		field.setAlignmentX(LEFT_ALIGNMENT);
		TsgHubUi.onTextChange(field, this::filter);
		field.addActionListener(e -> {
			if (resultScroll.isVisible() && !results.isEmpty()) choose(results.get(Math.max(0, resultList.getSelectedIndex())));
			else enterWithoutResults();
		});
		field.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent e)
			{
				if (!resultScroll.isVisible() || results.isEmpty()) return;
				int index = resultList.getSelectedIndex();
				if (e.getKeyCode() == KeyEvent.VK_DOWN) index = Math.min(results.size() - 1, index + 1);
				else if (e.getKeyCode() == KeyEvent.VK_UP) index = Math.max(0, index - 1);
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { hideResults(); e.consume(); return; }
				else return;
				resultList.setSelectedIndex(index);
				resultList.ensureIndexIsVisible(index);
				e.consume();
			}
		});
		add(TsgHubUi.fitHeight(field));

		resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultList.setVisibleRowCount(VISIBLE_ROWS);
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
	}

	String getText()
	{
		return field.getText().trim();
	}

	void setText(String value)
	{
		field.setText(value == null ? "" : value);
		hideResults();
	}

	JTextField field()
	{
		return field;
	}

	abstract boolean isExact(String query);

	abstract List<String> matches(String query);

	void resultsChanged(String query, int matches)
	{
	}

	void resultsToggled()
	{
	}

	void chosen()
	{
	}

	void enterWithoutResults()
	{
	}

	private void filter()
	{
		String query = getText();
		results.clear();
		if (!query.isEmpty() && !isExact(query)) for (String match : matches(query)) results.addElement(match);
		resultList.setVisibleRowCount(Math.max(1, Math.min(VISIBLE_ROWS, results.size())));
		if (!results.isEmpty()) resultList.setSelectedIndex(0);
		showResults(!results.isEmpty(), results.size());
	}

	private void choose(String value)
	{
		field.setText(value);
		hideResults();
		chosen();
	}

	private void hideResults()
	{
		showResults(false, HIDDEN);
	}

	private void showResults(boolean visible, int matches)
	{
		boolean changed = resultScroll.isVisible() != visible;
		resultScroll.setVisible(visible);
		resultsChanged(getText(), matches);
		TsgHubUi.refresh(this);
		if (changed) resultsToggled();
	}
}
