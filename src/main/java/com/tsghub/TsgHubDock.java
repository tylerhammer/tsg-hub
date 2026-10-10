package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static com.tsghub.TsgHubSession.DOCK_COLLAPSED;
import static com.tsghub.TsgHubSession.DOCK_SPLIT;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

final class TsgHubDock extends JPanel
{
	private static final double DEFAULT_SPLIT = 0.55;
	private static final double MIN_SPLIT = 0.2;
	private static final double MAX_SPLIT = 0.8;
	private static final int DIVIDER = 9;

	private final JComponent main;
	private final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT)
	{
		@Override
		public void doLayout()
		{
			int span = getHeight() - getDividerSize();
			if (applySplit && span > 0)
			{
				applySplit = false;
				setDividerLocation((int) Math.round(span * splitRatio()));
			}
			super.doLayout();
		}
	};
	private final JPanel pane = panel(new BorderLayout(0, 4));
	private final JPanel bar = new JPanel(new BorderLayout(6, 0));
	private final JLabel icon = new JLabel();
	private final JLabel title = boldLabel(" ");
	private final JLabel subtitle = shrinkable(caption(" "));
	private final ChevronIcon chevron = new ChevronIcon();
	private final JButton toggle = iconButton(chevron, "Collapse");
	private final JButton close = iconButton(new CloseIcon(), "Unpin");
	private JComponent content;
	private boolean collapsed = "true".equals(TsgHubSession.get(DOCK_COLLAPSED));
	private boolean shown = true;
	private boolean applySplit;

	TsgHubDock(JComponent main, Runnable onUnpin)
	{
		super(new BorderLayout());
		this.main = main;
		setOpaque(false);

		bar.setBackground(CARD);
		bar.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 3));
		JPanel titles = panel(new BorderLayout(6, 0));
		titles.add(title, BorderLayout.WEST);
		titles.add(subtitle, BorderLayout.CENTER);
		bar.add(icon, BorderLayout.WEST);
		bar.add(titles, BorderLayout.CENTER);
		JPanel buttons = panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttons.add(toggle);
		buttons.add(close);
		bar.add(buttons, BorderLayout.EAST);
		clickable(bar, this::toggle);
		toggle.addActionListener(e -> toggle());
		close.addActionListener(e -> onUnpin.run());
		pane.add(bar, BorderLayout.NORTH);

		split.setBorder(BorderFactory.createEmptyBorder());
		split.setOpaque(false);
		split.setContinuousLayout(true);
		split.setDividerSize(DIVIDER);
		split.setResizeWeight(splitRatio());
		split.setUI(new BasicSplitPaneUI()
		{
			@Override
			public BasicSplitPaneDivider createDefaultDivider()
			{
				return new Grip(this);
			}
		});
		split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> saveSplit());
		rebuild();
	}

	void pin(Icon pinIcon, JComponent next)
	{
		icon.setIcon(new SmallIcon(pinIcon));
		content = next;
		rebuild();
	}

	void unpin()
	{
		content = null;
		rebuild();
	}

	void setTitle(String text, String detail)
	{
		title.setText(text);
		subtitle.setText(detail.isEmpty() ? " " : "· " + detail);
		bar.setToolTipText(detail.isEmpty() ? text : text + " · " + detail);
	}

	void setShown(boolean next)
	{
		if (shown == next) return;
		shown = next;
		rebuild();
	}

	void expand()
	{
		if (!collapsed) return;
		toggle();
	}

	private void toggle()
	{
		collapsed = !collapsed;
		TsgHubSession.set(DOCK_COLLAPSED, collapsed ? "true" : "");
		rebuild();
	}

	private void rebuild()
	{
		removeAll();
		pane.removeAll();
		pane.add(bar, BorderLayout.NORTH);
		if (content == null || !shown)
		{
			add(main, BorderLayout.CENTER);
		}
		else if (collapsed)
		{
			add(main, BorderLayout.CENTER);
			JPanel south = panel(new BorderLayout());
			south.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
			south.add(bar, BorderLayout.CENTER);
			add(south, BorderLayout.SOUTH);
		}
		else
		{
			pane.add(content, BorderLayout.CENTER);
			content.setVisible(true);
			main.setMinimumSize(new Dimension(0, 60));
			pane.setMinimumSize(new Dimension(0, 60));
			split.setTopComponent(main);
			split.setBottomComponent(pane);
			applySplit = true;
			add(split, BorderLayout.CENTER);
		}
		chevron.down = !collapsed;
		toggle.setToolTipText(collapsed ? "Expand" : "Collapse");
		revalidate();
		repaint();
	}

	private double splitRatio()
	{
		try
		{
			double ratio = Double.parseDouble(TsgHubSession.get(DOCK_SPLIT));
			return Math.max(MIN_SPLIT, Math.min(MAX_SPLIT, ratio));
		}
		catch (NumberFormatException e)
		{
			return DEFAULT_SPLIT;
		}
	}

	private void saveSplit()
	{
		int span = split.getHeight() - split.getDividerSize();
		if (applySplit || span <= 0 || split.getParent() == null) return;
		double ratio = Math.max(MIN_SPLIT, Math.min(MAX_SPLIT, split.getDividerLocation() / (double) span));
		split.setResizeWeight(ratio);
		TsgHubSession.set(DOCK_SPLIT, String.format(java.util.Locale.ROOT, "%.3f", ratio));
	}

	private static final class Grip extends BasicSplitPaneDivider
	{
		Grip(BasicSplitPaneUI ui)
		{
			super(ui);
			setBorder(null);
		}

		@Override
		public void paint(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setColor(BACKGROUND);
			g.fillRect(0, 0, getWidth(), getHeight());
			int y = getHeight() / 2;
			g.setColor(BORDER);
			g.fillRect(0, y, getWidth(), 1);
			g.setColor(MUTED);
			int x = getWidth() / 2;
			g.fillRoundRect(x - 12, y - 1, 24, 3, 3, 3);
			g.dispose();
		}
	}

	private static final class SmallIcon implements Icon
	{
		private static final double SCALE = 2 / 3.0;
		private final Icon icon;

		SmallIcon(Icon icon) { this.icon = icon; }
		@Override public int getIconWidth() { return (int) Math.round(icon.getIconWidth() * SCALE); }
		@Override public int getIconHeight() { return (int) Math.round(icon.getIconHeight() * SCALE); }

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.scale(SCALE, SCALE);
			icon.paintIcon(c, g, 0, 0);
			g.dispose();
		}
	}

	private static final class ChevronIcon extends HoverIcon
	{
		private boolean down = true;

		ChevronIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			if (down)
			{
				g.drawLine(4, 6, 8, 10);
				g.drawLine(8, 10, 12, 6);
			}
			else
			{
				g.drawLine(4, 10, 8, 6);
				g.drawLine(8, 6, 12, 10);
			}
		}
	}

	private static final class CloseIcon extends HoverIcon
	{
		CloseIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.drawLine(5, 5, 11, 11);
			g.drawLine(11, 5, 5, 11);
		}
	}
}
