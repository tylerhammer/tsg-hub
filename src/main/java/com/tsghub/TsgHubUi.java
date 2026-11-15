package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;

import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

final class TsgHubUi
{
	private static final String LAZY_TOOLTIP = "tsgHub.lazyTooltip";
	private static final String NO_FULL_TEXT = "tsgHub.noFullText";
	private static final int COIN_ICON_W = 18;
	private static final int COIN_ICON_H = 16;
	private static final Color COIN_LOW = new Color(255, 255, 0);
	private static final Color COIN_HIGH = new Color(0, 255, 128);
	enum Tone { INFO, SUCCESS, ERROR }

	private TsgHubUi()
	{
	}

	static Color toneColor(Tone tone)
	{
		return tone == Tone.ERROR ? ERROR : tone == Tone.SUCCESS ? SUCCESS : MUTED;
	}

	static JLabel label(String text, Color color, Font font)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(font);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	static JLabel linkedHtml(String innerHtml, Color color, Font font, int widthPx)
	{
		JLabel label = label(html(innerHtml, widthPx), color, font);
		MouseAdapter links = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				String href = linkAt(label, e.getPoint());
				if (href != null && confirm(label, "Open link", "Open this link in your browser?\n\n" + href, "Open", false))
					net.runelite.client.util.LinkBrowser.browse(href);
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				String href = linkAt(label, e.getPoint());
				label.setCursor(href == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				label.setToolTipText(href);
			}
		};
		label.addMouseListener(links);
		label.addMouseMotionListener(links);
		return label;
	}

	private static String linkAt(JLabel label, Point point)
	{
		Object property = label.getClientProperty(BasicHTML.propertyKey);
		if (!(property instanceof View)) return null;
		View view = (View) property;
		Insets insets = label.getInsets();
		Rectangle bounds = new Rectangle(insets.left, insets.top, label.getWidth() - insets.left - insets.right, label.getHeight() - insets.top - insets.bottom);
		Rectangle textRect = new Rectangle();
		SwingUtilities.layoutCompoundLabel(label, label.getFontMetrics(label.getFont()), label.getText(), null, label.getVerticalAlignment(),
			label.getHorizontalAlignment(), label.getVerticalTextPosition(), label.getHorizontalTextPosition(), bounds, new Rectangle(), textRect, 0);
		if (!textRect.contains(point) || !(view.getDocument() instanceof HTMLDocument)) return null;
		int offset = view.viewToModel(point.x, point.y, textRect, new Position.Bias[1]);
		Element element = ((HTMLDocument) view.getDocument()).getCharacterElement(offset);
		Object anchor = element.getAttributes().getAttribute(HTML.Tag.A);
		if (!(anchor instanceof AttributeSet)) return null;
		Object href = ((AttributeSet) anchor).getAttribute(HTML.Attribute.HREF);
		return href == null ? null : href.toString();
	}

	static JLabel wrapped(String text, Color color, Font font, int widthPx)
	{
		return label(html(escape(text), widthPx), color, font);
	}

	static JLabel boldLabel(String text)
	{
		return label(text, TEXT, boldFont());
	}

	static JLabel caption(String text)
	{
		return label(text, MUTED, smallFont());
	}

	static JLabel listHeading(String text, boolean first)
	{
		JLabel heading = caption(text.toUpperCase());
		heading.setBorder(BorderFactory.createEmptyBorder(first ? 0 : 6, 2, 4, 0));
		return heading;
	}

	static JLabel rankLabel(int rank, Font font, int width)
	{
		JLabel label = label(String.valueOf(rank), rank == 1 ? ACCENT : MUTED, font);
		label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
		return label;
	}

	static JPanel emptyState(String heading, String body, int widthPx)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));
		JLabel title = boldLabel(heading);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		JLabel text = caption(html("<div style='text-align:center'>" + escape(body) + "</div>", widthPx));
		text.setAlignmentX(Component.CENTER_ALIGNMENT);
		text.setHorizontalAlignment(JLabel.CENTER);
		panel.add(title);
		panel.add(gap(GAP_M));
		panel.add(text);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	static JLabel badge(String text, Color color)
	{
		Color fill = ColorUtil.colorLerp(CARD, color, .16);
		JLabel label = new JLabel(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(fill);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};
		label.setForeground(color);
		label.setFont(smallFont());
		label.setBorder(BorderFactory.createEmptyBorder(2, 5, 0, 5));
		return label;
	}

	static JLabel worldChip(int world, boolean same)
	{
		JLabel chip = badge("W" + world, same ? SUCCESS : MUTED);
		chip.setHorizontalAlignment(JLabel.CENTER);
		Dimension size = chip.getPreferredSize();
		chip.setPreferredSize(new Dimension(Math.max(size.width, chip.getFontMetrics(chip.getFont()).stringWidth("W000") + 10), size.height));
		return chip;
	}

	static Component gap(int height)
	{
		return Box.createVerticalStrut(height);
	}

	static void eachDeep(Component component, Consumer<Component> action)
	{
		action.accept(component);
		if (component instanceof Container) for (Component child : ((Container) component).getComponents()) eachDeep(child, action);
	}

	static void addCard(Container list, Component card)
	{
		list.add(card);
		list.add(gap(LIST_GAP));
	}

	static JPanel stack()
	{
		JPanel panel = new JPanel()
		{
			@Override
			protected void addImpl(Component component, Object constraints, int index)
			{
				if (component instanceof JComponent) ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
				super.addImpl(component, constraints, index);
			}
		};
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	static JPanel panel(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	static JPanel row()
	{
		return panel(new BorderLayout(6, 0));
	}

	static JPanel row(Component center, Component east)
	{
		JPanel row = row();
		row.add(center, BorderLayout.CENTER);
		row.add(east, BorderLayout.EAST);
		return row;
	}

	static JPanel north(Component component)
	{
		JPanel wrap = panel(new BorderLayout());
		wrap.add(component, BorderLayout.NORTH);
		return wrap;
	}

	static JPanel twoLines(Component top, Component bottom)
	{
		JPanel text = stack();
		text.add(top);
		text.add(gap(GAP_XS));
		text.add(bottom);
		return text;
	}

	static JPanel eventLines(JsonObject event, LongFunction<AsyncBufferedImage> coins)
	{
		boolean active = "active".equals(str(event, "status"));
		Instant start = eventStart(event), end = eventEnd(event);
		boolean dated = !active && start != null;
		JPanel extras = inline();
		if (bool(event, "hidden")) inline(extras, badge("Hidden", MUTED));
		JLabel prize = prizeLabel(event, null, coins);
		if (prize != null) inline(extras, prize);
		JPanel lines = stack();
		lines.add(fitHeight(dated ? row(shrinkable(boldLabel(eventName(event))), new JLabel()) : row(shrinkable(boldLabel(eventName(event))), extras)));
		if (active && start != null && end != null && end.isAfter(start))
		{
			long now = TsgHubFormat.clock.millis();
			lines.add(progressBar((int) ((now - start.toEpochMilli()) / 60000), (int) ((end.toEpochMilli() - start.toEpochMilli()) / 60000), null, null));
		}
		lines.add(gap(GAP_XS));
		boolean soon = !active && start != null && start.toEpochMilli() - TsgHubFormat.clock.millis() < 86_400_000L;
		JLabel countdown = label(eventCountdown(event), active ? SUCCESS : soon ? ACCENT : MUTED, smallFont());
		if (dated) inline(extras, countdown);
		lines.add(fitHeight(row(shrinkable(caption(eventDetail(event))), dated ? extras : countdown)));
		if (!dated) return lines;
		ZonedDateTime day = start.atZone(ZoneId.systemDefault());
		JPanel stub = stack();
		JLabel weekday = label(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(), ACCENT, smallFont());
		JLabel date = boldLabel(String.valueOf(day.getDayOfMonth()));
		for (JLabel part : new JLabel[]{weekday, date})
		{
			part.setAlignmentX(Component.CENTER_ALIGNMENT);
			stub.add(part);
		}
		stub.setPreferredSize(new Dimension(30, stub.getPreferredSize().height));
		stub.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER), BorderFactory.createEmptyBorder(0, 0, 0, 4)));
		JPanel withDate = panel(new BorderLayout(6, 0));
		withDate.add(stub, BorderLayout.WEST);
		withDate.add(lines, BorderLayout.CENTER);
		return withDate;
	}

	static JPanel inline()
	{
		return panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
	}

	static void inline(JPanel row, Component part)
	{
		if (row.getComponentCount() > 0) row.add(Box.createHorizontalStrut(GAP_S));
		row.add(part);
	}

	static JLabel shrinkable(JLabel label)
	{
		int height = label.getPreferredSize().height;
		label.setMinimumSize(new Dimension(0, height));
		label.setPreferredSize(new Dimension(0, height));
		return label;
	}

	static JPanel card()
	{
		JPanel panel = new Card();
		panel.setBackground(CARD);
		panel.setBorder(cardBorder());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static final class Card extends JPanel
	{
		private int tipFor = -1;
		private String tipText;
		private String measured;
		private int measuredWidth;

		Card()
		{
			super(new BorderLayout(6, 4));
			addPropertyChangeListener(LAZY_TOOLTIP, e -> tipFor = -1);
		}

		@Override
		@SuppressWarnings("unchecked")
		public String getToolTipText(MouseEvent event)
		{
			Object tip = getClientProperty(LAZY_TOOLTIP);
			if (!(tip instanceof Supplier)) return super.getToolTipText(event);
			if (tipFor != getWidth())
			{
				tipFor = getWidth();
				tipText = ((Supplier<String>) tip).get();
			}
			return tipText;
		}

		@Override
		public JToolTip createToolTip()
		{
			JToolTip tip = super.createToolTip();
			tip.setBackground(CARD);
			tip.setForeground(TEXT);
			tip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_HOVER), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
			return tip;
		}

		// Anchor tooltips beside the card so they don't follow the mouse.
		@Override
		public Point getToolTipLocation(MouseEvent event)
		{
			String text = getToolTipText(event);
			if (text == null) return null;
			if (!text.equals(measured))
			{
				JToolTip tip = createToolTip();
				tip.setTipText(text);
				measured = text;
				measuredWidth = tip.getPreferredSize().width;
			}
			return new Point(-measuredWidth - 6, 0);
		}
	}

	static void lazyTooltip(JComponent component, Supplier<String> tip)
	{
		component.putClientProperty(LAZY_TOOLTIP, tip);
		ToolTipManager.sharedInstance().registerComponent(component);
	}

	static void fullTextTooltip(JComponent card, Supplier<String> extra)
	{
		fullTextTooltip(card, extra, null);
	}

	static void fullTextTooltip(JComponent card, Supplier<String> extra, Supplier<String> corner)
	{
		lazyTooltip(card, () -> {
			List<String> parts = new ArrayList<>();
			collectCut(card, parts);
			String more = extra == null ? null : extra.get();
			if (more != null && !more.isEmpty()) parts.add(more);
			String side = corner == null ? "" : corner.get();
			if (parts.isEmpty() && side.isEmpty()) return null;
			String body = TsgHubHtml.withCorner(String.join("<br>", parts), side);
			return tooltipHtml(body);
		});
	}

	static String tooltipHtml(String body)
	{
		return body.contains("<table") ? html("<div style='padding:2px'>" + body + "</div>", 240) : "<html>" + body + "</html>";
	}

	static <T extends JComponent> T noFullText(T component)
	{
		component.putClientProperty(NO_FULL_TEXT, true);
		return component;
	}

	private static void collectCut(Container parent, List<String> parts)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JComponent && ((JComponent) child).getClientProperty(NO_FULL_TEXT) != null) continue;
			if (child instanceof JLabel && truncated((JLabel) child)) parts.add(parts.isEmpty() ? "<b>" + escape(((JLabel) child).getText()) + "</b>" : escape(((JLabel) child).getText()));
			else if (child instanceof Container) collectCut((Container) child, parts);
		}
	}

	static <T extends JComponent> T fitHeight(T component)
	{
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return component;
	}

	static void clickable(JComponent component, Runnable onClick)
	{
		Color base = component.getBackground();
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseEntered(MouseEvent e) { recolor(component, base, CARD_HOVER); }
			@Override public void mouseExited(MouseEvent e) { recolor(component, CARD_HOVER, base); }
			@Override public void mouseReleased(MouseEvent e)
			{
				if (component.contains(e.getPoint())) onClick.run();
			}
		});
	}

	private static void recolor(Component component, Color from, Color to)
	{
		if (from.equals(component.getBackground())) component.setBackground(to);
		if (component instanceof Container)
			for (Component child : ((Container) component).getComponents()) recolor(child, from, to);
	}

	static JPanel progressBar(int value, int max, String name, String count)
	{
		int cap = Math.max(1, max);
		float fill = Math.max(0, Math.min(value, cap)) / (float) cap;
		Color color = value >= max ? SUCCESS : ACCENT;
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int y = (getHeight() - 4) / 2;
				g2.setColor(SLOT);
				g2.fillRoundRect(0, y, getWidth(), 4, 4, 4);
				g2.setColor(color);
				g2.fillRoundRect(0, y, Math.round(getWidth() * fill), 4, 4, 4);
				g2.dispose();
			}
		};
		bar.setPreferredSize(new Dimension(40, 12));
		JPanel row = row();
		if (name != null)
		{
			JLabel label = caption(name);
			label.setPreferredSize(new Dimension(52, label.getPreferredSize().height));
			row.add(label, BorderLayout.WEST);
		}
		row.add(bar, BorderLayout.CENTER);
		if (count != null)
		{
			JLabel label = caption(count);
			label.setHorizontalAlignment(JLabel.RIGHT);
			label.setPreferredSize(new Dimension(Math.max(26, label.getPreferredSize().width), label.getPreferredSize().height));
			row.add(label, BorderLayout.EAST);
		}
		return fitHeight(row);
	}

	static JScrollPane scroll(JComponent content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		JScrollBar bar = scroll.getVerticalScrollBar();
		bar.setUnitIncrement(16);
		bar.setOpaque(false);
		bar.setPreferredSize(new Dimension(8, 0));
		bar.setUI(new BasicScrollBarUI()
		{
			@Override
			protected void paintTrack(Graphics g, JComponent c, Rectangle bounds)
			{
			}

			@Override
			protected void paintThumb(Graphics g, JComponent c, Rectangle thumb)
			{
				if (thumb.isEmpty()) return;
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(isThumbRollover() || isDragging ? DIM : BORDER_HOVER);
				g2.fillRoundRect(thumb.x + 3, thumb.y, thumb.width - 3, thumb.height, 5, 5);
				g2.dispose();
			}

			@Override
			protected JButton createDecreaseButton(int orientation)
			{
				return noButton();
			}

			@Override
			protected JButton createIncreaseButton(int orientation)
			{
				return noButton();
			}

			private JButton noButton()
			{
				JButton none = new JButton();
				none.setPreferredSize(new Dimension(0, 0));
				return none;
			}
		});
		return scroll;
	}


	static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		WidthTrackingPanel()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(false);
		}

		@Override
		protected void addImpl(Component component, Object constraints, int index)
		{
			if (component instanceof JComponent) ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
			super.addImpl(component, constraints, index);
		}

		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
		@Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(visible.height - 16, 16); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}

	static <T extends AbstractButton> T onClick(T button, Runnable action)
	{
		button.addActionListener(e -> action.run());
		return button;
	}

	static <T extends AbstractButton> T plain(T button, Color color)
	{
		button.setOpaque(false);
		button.setForeground(color);
		button.setFocusPainted(false);
		return button;
	}

	static JButton button(String text)
	{
		JButton button = new JButton(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				Color fill = getBackground();
				if (!isEnabled()) fill = ColorUtil.colorLerp(fill, BACKGROUND, .5);
				else if (getModel().isPressed()) fill = fill.darker();
				else if (getModel().isRollover()) fill = ACCENT_STRONG.equals(fill) ? ACCENT_HOVER : RAISED.equals(fill) ? RAISED_HOVER : fill.brighter();
				paintSlot(g, this, fill);
				super.paintComponent(g);
			}
		};
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setOpaque(false);
		button.setRolloverEnabled(true);
		button.setBackground(RAISED);
		button.setForeground(TEXT);
		button.setBorder(BorderFactory.createEmptyBorder(6, 10, 5, 10));
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	static JButton dangerButton(String text)
	{
		JButton button = button(text);
		button.setForeground(ERROR);
		return button;
	}

	static void onTextChange(JTextComponent field, Runnable action)
	{
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { action.run(); }
			@Override public void removeUpdate(DocumentEvent e) { action.run(); }
			@Override public void changedUpdate(DocumentEvent e) { action.run(); }
		});
	}

	static JButton primaryButton(String text)
	{
		JButton button = button(text);
		button.setBackground(ACCENT_STRONG);
		button.setForeground(Color.WHITE);
		return button;
	}

	static JButton iconButton(Icon icon, String tooltip)
	{
		JButton button = new JButton(icon);
		SwingUtil.removeButtonDecorations(button);
		button.setToolTipText(tooltip);
		button.getAccessibleContext().setAccessibleName(tooltip);
		button.setPreferredSize(new Dimension(24, 24));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setRolloverEnabled(true);
		button.setFocusable(false);
		return button;
	}

	static void copyToClipboard(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	static final class StatusLine extends JLabel
	{
		private final Timer clear = new Timer(0, e -> setVisible(false));
		private final int widthPx;

		StatusLine(int widthPx)
		{
			this.widthPx = widthPx;
			clear.setRepeats(false);
			setFont(smallFont());
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
			setVisible(false);
		}

		void show(String message, Tone tone)
		{
			clear.stop();
			if (message == null || message.trim().isEmpty()) { setVisible(false); return; }
			setText(html(escape(message), widthPx));
			setForeground(toneColor(tone));
			setVisible(true);
			boolean inProgress = message.endsWith("...");
			if (!inProgress)
			{
				clear.setInitialDelay(tone == Tone.ERROR ? 10_000 : 4_000);
				clear.start();
			}
		}
	}

	static void refresh(JComponent panel)
	{
		panel.revalidate();
		panel.repaint();
	}

	static void showError(JLabel label, String message, int width)
	{
		label.setText(html(escape(message), width));
		label.setVisible(true);
		label.revalidate();
	}

	static void placeholder(JComponent field, String text)
	{
		field.putClientProperty("JTextField.placeholderText", text);
	}

	static JLabel prizeLabel(JsonObject event, Font font, LongFunction<AsyncBufferedImage> coins)
	{
		List<Long> prizes = eventPrizes(event);
		if (prizes.isEmpty()) return null;
		long total = prizeTotal(prizes);
		JLabel label = font == null ? badge(formatGp(total), coinColor(total)) : label(formatGp(total), coinColor(total), font);
		label.setToolTipText("Prize pool: " + prizeSummary(prizes));
		label.setIconTextGap(2);
		int w = font == null ? 11 : COIN_ICON_W, h = font == null ? 10 : COIN_ICON_H;
		AsyncBufferedImage image = coins.apply(total);
		if (image != null)
		{
			ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(image, w, h));
			label.setIcon(icon);
			image.onLoaded(() -> SwingUtilities.invokeLater(() -> {
				icon.setImage(ImageUtil.resizeImage(image, w, h));
				label.repaint();
			}));
		}
		return label;
	}

	static JPanel prizeRow(JsonObject event, Font font)
	{
		List<Long> prizes = eventPrizes(event);
		if (prizes.isEmpty()) return null;
		JPanel row = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(label("Prizes ", MUTED, font));
		for (int i = 0; i < prizes.size(); i++)
		{
			row.add(label((i == 0 ? "" : " · ") + place(i + 1) + " ", MUTED, font));
			row.add(label(formatGp(prizes.get(i)), coinColor(prizes.get(i)), font));
		}
		return fitHeight(row);
	}

	static Color levelColor(int current, int max)
	{
		double ratio = max <= 0 ? 1 : (double) current / max;
		return ratio <= 0.25 ? LOW : ratio <= 0.5 ? WARNING : TEXT;
	}

	static Color coinColor(long value)
	{
		if (value < 100_000) return COIN_LOW;
		if (value < 10_000_000) return TEXT;
		return COIN_HIGH;
	}
	static JPanel field(String label, Component input)
	{
		JPanel row = panel(new BorderLayout(0, 3));
		row.add(caption(label), BorderLayout.NORTH);
		row.add(input, BorderLayout.CENTER);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return fitHeight(row);
	}

	static boolean confirm(Component parent, String title, String message, String action, boolean danger)
	{
		Object[] options = {action, "Cancel"};
		int choice = JOptionPane.showOptionDialog(parent, message, title, JOptionPane.DEFAULT_OPTION,
			danger ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE, null, options, options[danger ? 1 : 0]);
		return choice == 0;
	}

	static Border bottomRule()
	{
		return BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER);
	}
}
