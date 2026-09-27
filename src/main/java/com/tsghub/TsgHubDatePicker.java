package com.tsghub;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

final class TsgHubDatePicker extends JButton
{
	private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
	private LocalDate date = LocalDate.now();
	private YearMonth shownMonth = YearMonth.now();
	private final List<Runnable> listeners = new ArrayList<>();
	private final JPopupMenu popup = new JPopupMenu();
	private final JLabel monthLabel = TsgHubUi.label("", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont());
	private final JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));

	TsgHubDatePicker()
	{
		setHorizontalAlignment(SwingConstants.LEFT);
		setIcon(new CalendarIcon());
		setIconTextGap(8);
		setFocusPainted(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("Pick a date");
		addActionListener(e -> open());
		buildPopup();
		refreshText();
	}

	LocalDate getDate()
	{
		return date;
	}

	void setDate(LocalDate value)
	{
		date = value == null ? LocalDate.now() : value;
		refreshText();
	}

	void onChange(Runnable listener)
	{
		listeners.add(listener);
	}

	private void refreshText()
	{
		setText(date.format(DISPLAY));
	}

	private void buildPopup()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBackground(TsgHubUi.CARD);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JButton previous = navButton("<", -1);
		JButton next = navButton(">", 1);
		monthLabel.setHorizontalAlignment(SwingConstants.CENTER);
		header.add(previous, BorderLayout.WEST);
		header.add(monthLabel, BorderLayout.CENTER);
		header.add(next, BorderLayout.EAST);
		panel.add(header, BorderLayout.NORTH);
		grid.setOpaque(false);
		panel.add(grid, BorderLayout.CENTER);
		popup.setBorder(BorderFactory.createLineBorder(TsgHubUi.BORDER));
		popup.add(panel);
	}

	private JButton navButton(String text, int months)
	{
		JButton button = TsgHubUi.button(text);
		button.setMargin(new java.awt.Insets(2, 8, 2, 8));
		button.addActionListener(e -> {
			shownMonth = shownMonth.plusMonths(months);
			renderMonth();
		});
		return button;
	}

	private void open()
	{
		shownMonth = YearMonth.from(date);
		renderMonth();
		popup.show(this, 0, getHeight());
	}

	private void renderMonth()
	{
		monthLabel.setText(shownMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + shownMonth.getYear());
		grid.removeAll();
		for (int i = 0; i < 7; i++)
		{
			DayOfWeek day = DayOfWeek.SUNDAY.plus(i);
			JLabel name = TsgHubUi.label(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).substring(0, 2), TsgHubUi.MUTED, FontManager.getRunescapeSmallFont());
			name.setHorizontalAlignment(SwingConstants.CENTER);
			grid.add(name);
		}
		int leading = shownMonth.atDay(1).getDayOfWeek().getValue() % 7; // Sunday first
		for (int i = 0; i < leading; i++) grid.add(new JLabel());
		LocalDate today = LocalDate.now();
		for (int d = 1; d <= shownMonth.lengthOfMonth(); d++)
		{
			LocalDate day = shownMonth.atDay(d);
			JButton cell = new JButton(String.valueOf(d));
			cell.setFocusPainted(false);
			cell.setMargin(new java.awt.Insets(2, 2, 2, 2));
			cell.setPreferredSize(new Dimension(34, 26));
			cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			boolean selected = day.equals(date);
			cell.setBackground(selected ? TsgHubUi.ACCENT.darker() : TsgHubUi.BACKGROUND);
			cell.setForeground(selected ? Color.WHITE : TsgHubUi.TEXT);
			if (day.equals(today) && !selected) cell.setBorder(BorderFactory.createLineBorder(TsgHubUi.ACCENT));
			cell.addActionListener(e -> {
				date = day;
				refreshText();
				popup.setVisible(false);
				listeners.forEach(Runnable::run);
			});
			grid.add(cell);
		}
		grid.revalidate();
		grid.repaint();
		popup.pack();
	}

	private static final class CalendarIcon implements javax.swing.Icon
	{
		@Override public int getIconWidth() { return 14; }
		@Override public int getIconHeight() { return 14; }
		@Override public void paintIcon(Component c, java.awt.Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setColor(TsgHubUi.MUTED);
			g.drawRect(1, 2, 12, 11);
			g.fillRect(1, 2, 13, 3);
			g.fillRect(4, 0, 2, 3);
			g.fillRect(9, 0, 2, 3);
			for (int row = 0; row < 2; row++)
				for (int col = 0; col < 3; col++) g.fillRect(3 + col * 3, 7 + row * 3, 2, 2);
			g.dispose();
		}
	}
}
