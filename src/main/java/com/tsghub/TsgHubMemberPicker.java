package com.tsghub;

import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

final class TsgHubMemberPicker extends JPanel
{
	private static final int MAX_RESULTS = 20;

	private final List<String> names;
	private final JTextField field = new JTextField(20);
	private final DefaultListModel<String> results = new DefaultListModel<>();
	private final JList<String> resultList = new JList<>(results);
	private final JScrollPane resultScroll = new JScrollPane(resultList);

	TsgHubMemberPicker(List<String> names, String value)
	{
		this.names = names;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);

		field.setText(value);
		field.putClientProperty("JTextField.placeholderText", "Type to search the clan");
		field.setAlignmentX(LEFT_ALIGNMENT);
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { filter(); }
			@Override public void removeUpdate(DocumentEvent e) { filter(); }
			@Override public void changedUpdate(DocumentEvent e) { filter(); }
		});
		field.addActionListener(e -> {
			if (resultScroll.isVisible() && !results.isEmpty()) choose(results.get(Math.max(0, resultList.getSelectedIndex())));
			else submit();
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
		resultList.setBackground(TsgHubTheme.CARD);
		resultList.setCellRenderer((list, name, index, selected, focus) -> {
			JLabel row = new JLabel(name);
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

	private void filter()
	{
		String query = field.getText().trim();
		results.clear();
		if (query.isEmpty() || names.stream().anyMatch(name -> TsgHubUi.samePlayer(name, query)))
		{
			hideResults();
			return;
		}
		for (String name : TsgHubSidebarPanel.matchNames(names, query, MAX_RESULTS)) results.addElement(name);
		resultList.setVisibleRowCount(Math.max(1, Math.min(6, results.size())));
		if (!results.isEmpty()) resultList.setSelectedIndex(0);
		setResultsVisible(!results.isEmpty());
	}

	private void choose(String name)
	{
		field.setText(name);
		hideResults();
	}

	private void hideResults()
	{
		setResultsVisible(false);
	}

	private void setResultsVisible(boolean visible)
	{
		boolean changed = resultScroll.isVisible() != visible;
		resultScroll.setVisible(visible);
		revalidate();
		Window window = SwingUtilities.getWindowAncestor(this);
		if (changed && window != null) window.pack();
	}

	private void submit()
	{
		JButton button = getRootPane() == null ? null : getRootPane().getDefaultButton();
		if (button != null) button.doClick();
	}
}
