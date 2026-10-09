package com.tsghub;

import static com.tsghub.TsgHubTheme.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
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
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
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
import javax.swing.JScrollPane;
import javax.swing.JToolTip;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
final class TsgHubUi
{
	private static final String LAZY_TOOLTIP = "tsgHub.lazyTooltip";
	private static final String NO_FULL_TEXT = "tsgHub.noFullText";
	private static final DateTimeFormatter DATE_WITH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);
	private static final int COIN_ICON_W = 18;
	private static final int COIN_ICON_H = 16;
	private static final Color COIN_LOW = new Color(255, 255, 0);
	private static final Color COIN_HIGH = new Color(0, 255, 128);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US);
	private static final DateTimeFormatter DAY_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US);
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h", Locale.US);
	private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("h:mm", Locale.US);
	private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("zzz", Locale.US);
	private static final ZoneId CLAN_ZONE = ZoneId.of("Australia/Sydney");
	static Clock clock = Clock.systemUTC();

	enum Tone { INFO, SUCCESS, ERROR }

	private TsgHubUi()
	{
	}

	static Color toneColor(Tone tone)
	{
		return tone == Tone.ERROR ? ERROR : tone == Tone.SUCCESS ? SUCCESS : MUTED;
	}

	static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// Swing HTML treats px as 1/72in but fonts render at 96dpi, so scale by 3/4.
	static String html(String innerHtml, int widthPx)
	{
		return "<html><body style='width:" + Math.round(widthPx * 0.75f) + "px'>" + innerHtml + "</body></html>";
	}

	static JLabel label(String text, Color color, Font font)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(font);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
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

	static JLabel listRow(String text, boolean selected)
	{
		JLabel row = new JLabel(text);
		row.setOpaque(true);
		row.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		row.setBackground(selected ? CARD_HOVER : CARD);
		row.setForeground(TEXT);
		return row;
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
		panel.add(Box.createVerticalStrut(GAP_M));
		panel.add(text);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	static JLabel sectionTitle(String text)
	{
		JLabel label = label(text, ACCENT, boldFont());
		label.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
		return label;
	}

	static JLabel badge(String text, Color color)
	{
		JLabel label = label(text, color, smallFont());
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(color.darker()),
			BorderFactory.createEmptyBorder(0, 4, 0, 4)));
		return label;
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
		text.add(Box.createVerticalStrut(GAP_XS));
		text.add(bottom);
		return text;
	}

	static JPanel eventLines(JsonObject event, LongFunction<AsyncBufferedImage> coins)
	{
		JPanel top = row(shrinkable(boldLabel(eventName(event))),
			label(eventCountdown(event), "active".equals(str(event, "status")) ? SUCCESS : MUTED, smallFont()));
		JPanel extras = panel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		if (bool(event, "hidden")) extras.add(badge("Hidden", MUTED));
		JLabel prize = prizeLabel(event, smallFont(), coins);
		if (prize != null) extras.add(prize);
		return twoLines(top, row(shrinkable(caption(eventDetail(event))), extras));
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
		JPanel panel = new JPanel(new BorderLayout(6, 4))
		{
			@Override
			@SuppressWarnings("unchecked")
			public String getToolTipText(MouseEvent event)
			{
				Object tip = getClientProperty(LAZY_TOOLTIP);
				return tip instanceof Supplier ? ((Supplier<String>) tip).get() : super.getToolTipText(event);
			}

			// Anchor tooltips beside the card so they don't follow the mouse.
			@Override
			public Point getToolTipLocation(MouseEvent event)
			{
				String text = getToolTipText(event);
				if (text == null) return null;
				JToolTip tip = createToolTip();
				tip.setTipText(text);
				return new Point(-tip.getPreferredSize().width - 6, 0);
			}
		};
		panel.setBackground(CARD);
		panel.setBorder(cardBorder());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	static void lazyTooltip(JComponent component, Supplier<String> tip)
	{
		component.putClientProperty(LAZY_TOOLTIP, tip);
		ToolTipManager.sharedInstance().registerComponent(component);
	}

	static void fullTextTooltip(JComponent card, Supplier<String> extra)
	{
		lazyTooltip(card, () -> {
			List<String> parts = new ArrayList<>();
			collectCut(card, parts);
			String more = extra == null ? null : extra.get();
			if (more != null && !more.isEmpty()) parts.add(more);
			if (parts.isEmpty()) return null;
			String body = String.join("<br>", parts);
			return body.contains("<table") ? html("<div style='padding:2px'>" + body + "</div>", 240) : "<html>" + body + "</html>";
		});
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

	static JScrollPane scroll(JComponent content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
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

	static <T extends AbstractButton> T plain(T button, Color color)
	{
		button.setOpaque(false);
		button.setForeground(color);
		button.setFocusPainted(false);
		return button;
	}

	static JButton button(String text)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	static JButton primaryButton(String text)
	{
		JButton button = button(text);
		button.setBackground(ACCENT.darker());
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

	static void flashText(AbstractButton button, String text, int millis)
	{
		String original = button.getText();
		if (text.equals(original)) return;
		button.setText(text);
		Timer timer = new Timer(millis, e -> button.setText(original));
		timer.setRepeats(false);
		timer.start();
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

	static String friendlyError(Throwable error)
	{
		log.warn("TSG Hub error", error);
		Throwable root = error;
		while (root.getCause() != null && root.getCause() != root) root = root.getCause();
		if (root instanceof ConnectException || root instanceof UnknownHostException)
			return "Can't reach the TSG Hub service. Try again in a moment.";
		if (root instanceof SocketTimeoutException)
			return "The TSG Hub service took too long to respond. Try again.";
		String message = error.getMessage();
		if (message == null || message.trim().isEmpty()) return "Something went wrong (" + root.getClass().getSimpleName() + "). Details are in the RuneLite log.";
		return message.endsWith(".") ? message : message + ".";
	}

	static boolean samePlayer(String a, String b)
	{
		String left = playerKey(a);
		return !left.isEmpty() && left.equals(playerKey(b));
	}

	static String playerKey(String name)
	{
		return PlayerNames.normalize(name);
	}

	static String str(JsonObject object, String key)
	{
		if (object == null || !object.has(key)) return "";
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	static int integer(JsonObject object, String key, int fallback)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
		try { return object.get(key).getAsInt(); }
		catch (RuntimeException ignored) { return fallback; }
	}

	static boolean bool(JsonObject object, String key)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return false;
		try { return object.get(key).getAsBoolean(); }
		catch (RuntimeException ignored) { return false; }
	}

	static JsonArray array(JsonObject object, String key)
	{
		return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
	}

	static Instant instant(String iso)
	{
		try { return iso == null || iso.isEmpty() ? null : Instant.parse(iso); }
		catch (DateTimeException e) { return null; }
	}

	private static ZoneId eventZone(JsonObject event)
	{
		try { return ZoneId.of(str(event, "timeZone")); }
		catch (DateTimeException e) { return CLAN_ZONE; }
	}

	private static Instant midnight(String isoDate, ZoneId zone, int plusDays)
	{
		try { return LocalDate.parse(isoDate).plusDays(plusDays).atStartOfDay(zone).toInstant(); }
		catch (DateTimeException e) { return null; }
	}

	static Instant eventStart(JsonObject event)
	{
		Instant start = instant(str(event, "startsAt"));
		if (start == null) start = instant(str(eventConfig(event), "startsAt"));
		return start != null ? start : midnight(str(event, "startDate"), eventZone(event), 0);
	}

	static Instant eventEnd(JsonObject event)
	{
		Instant end = instant(str(event, "endsAt"));
		if (end != null || "drop-party".equals(str(event, "type"))) return end;
		return midnight(str(event, "endDate"), eventZone(event), 1);
	}

	static boolean running(JsonObject event, Instant now)
	{
		Instant start = eventStart(event);
		Instant end = eventEnd(event);
		if (start == null || end == null) return "active".equals(str(event, "status"));
		return !now.isBefore(start) && now.isBefore(end);
	}

	static String zoneName(ZonedDateTime time)
	{
		return time.format(ZONE);
	}

	static String zoneLabel(ZoneId zone, Instant now)
	{
		String name = zoneName(now.atZone(zone));
		return name.equals(zone.getId()) ? name : name + " (" + zone.getId() + ")";
	}

	static String localDate(Instant when, ZoneId zone)
	{
		return when.atZone(zone).format(DATE_WITH_YEAR);
	}

	private static String clock(ZonedDateTime time, boolean meridiem)
	{
		String text = time.format(time.getMinute() == 0 ? HOUR : HOUR_MINUTE);
		return meridiem ? text + (time.getHour() < 12 ? "am" : "pm") : text;
	}

	private static String day(ZonedDateTime time, Instant now)
	{
		return time.format(time.getYear() == now.atZone(time.getZone()).getYear() ? DAY : DAY_WITH_YEAR);
	}

	private static String when(ZonedDateTime time, Instant now)
	{
		return day(time, now) + ", " + clock(time, true);
	}

	static String timeRange(Instant start, Instant end, ZoneId zone, Instant now)
	{
		if (start == null) return "Time TBD";
		ZonedDateTime from = start.atZone(zone);
		if (end == null) return when(from, now) + " " + zoneName(from);
		ZonedDateTime to = end.atZone(zone);
		boolean sameZone = zoneName(from).equals(zoneName(to));
		boolean sameDay = from.toLocalDate().equals(to.toLocalDate());
		if (sameDay && sameZone)
			return day(from, now) + ", " + clock(from, from.getHour() < 12 != to.getHour() < 12) + "-" + clock(to, true) + " " + zoneName(to);
		String until = sameDay ? clock(to, true) : when(to, now);
		return when(from, now) + (sameZone ? "" : " " + zoneName(from)) + " to " + until + " " + zoneName(to);
	}

	static String span(Duration duration)
	{
		long minutes = Math.max(1, (duration.getSeconds() + 59) / 60);
		long days = minutes / 1440, hours = minutes % 1440 / 60, rest = minutes % 60;
		if (days > 0) return days + "d" + (hours > 0 ? " " + hours + "h" : "");
		if (hours > 0) return hours + "h" + (rest > 0 ? " " + rest + "m" : "");
		return rest + "m";
	}

	static String relativeTime(Instant start, Instant end, Instant now)
	{
		if (start == null) return "";
		if (now.isBefore(start)) return "starts in " + span(Duration.between(now, start));
		if (end == null) return "happening now";
		if (now.isBefore(end)) return "ends in " + span(Duration.between(now, end));
		return "ended";
	}

	static String capitalize(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	static String eventWhen(JsonObject event)
	{
		return timeRange(eventStart(event), eventEnd(event), ZoneId.systemDefault(), clock.instant());
	}

	static String eventRelative(JsonObject event)
	{
		if ("ended".equals(str(event, "status"))) return "ended";
		return relativeTime(eventStart(event), eventEnd(event), clock.instant());
	}

	static String eventName(JsonObject event)
	{
		String type = str(event, "type");
		if ("skill".equals(type) || "boss".equals(type)) return eventTypeLabel(event);
		return str(event, "name");
	}

	static String eventCountdown(JsonObject event)
	{
		String relative = eventRelative(event);
		if (relative.startsWith("ends in ")) return relative.substring(8) + " left";
		if (relative.startsWith("starts ")) return relative.substring(7);
		return relative.isEmpty() ? "TBD" : capitalize(relative);
	}

	static String eventDetail(JsonObject event)
	{
		JsonObject config = eventConfig(event);
		switch (str(event, "type"))
		{
			case "skill": return skillName(str(config, "skill"));
			case "boss": return str(config, "npcName");
			case "drop-party":
				List<String> where = new ArrayList<>();
				if (integer(config, "world", 0) > 0) where.add("W" + integer(config, "world", 0));
				if (!str(config, "location").isEmpty()) where.add(str(config, "location"));
				return String.join(" · ", where);
			default: return eventDay(event);
		}
	}

	static JLabel prizeLabel(JsonObject event, Font font, LongFunction<AsyncBufferedImage> coins)
	{
		List<Long> prizes = eventPrizes(event);
		if (prizes.isEmpty()) return null;
		long total = prizeTotal(prizes);
		JLabel label = label(formatGp(total), coinColor(total), font);
		label.setToolTipText("Prize pool: " + prizeSummary(prizes));
		label.setIconTextGap(2);
		AsyncBufferedImage image = coins.apply(total);
		if (image != null)
		{
			Runnable apply = () -> label.setIcon(new ImageIcon(ImageUtil.resizeImage(image, COIN_ICON_W, COIN_ICON_H)));
			apply.run();
			image.onLoaded(apply);
		}
		return label;
	}

	static long prizeTotal(List<Long> prizes)
	{
		long total = 0;
		for (long prize : prizes) total += prize;
		return total;
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

	static List<Long> eventPrizes(JsonObject event)
	{
		List<Long> prizes = new ArrayList<>();
		for (JsonElement item : array(event, "prizes"))
			if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber() && item.getAsLong() > 0) prizes.add(item.getAsLong());
		return prizes;
	}

	static String prizeSummary(List<Long> prizes)
	{
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < prizes.size(); i++) parts.add(place(i + 1) + " " + formatGp(prizes.get(i)));
		return String.join(" · ", parts);
	}

	static String place(int place)
	{
		if (place % 100 >= 11 && place % 100 <= 13) return place + "th";
		switch (place % 10)
		{
			case 1: return place + "st";
			case 2: return place + "nd";
			case 3: return place + "rd";
			default: return place + "th";
		}
	}

	static long parseMillions(String text)
	{
		String value = text.trim().replace(",", "");
		if (value.isEmpty()) return 0;
		try
		{
			BigDecimal amount = new BigDecimal(value).multiply(BigDecimal.valueOf(1_000_000L));
			if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) return -1;
			return amount.longValueExact();
		}
		catch (NumberFormatException | ArithmeticException e)
		{
			return -1;
		}
	}

	static String millions(long gp)
	{
		return BigDecimal.valueOf(gp).divide(BigDecimal.valueOf(1_000_000L)).stripTrailingZeros().toPlainString();
	}

	static String formatGp(long value)
	{
		if (value <= 0) return "";
		if (value >= 1_000_000_000L) return trimDecimal(value / 1_000_000_000.0) + "B";
		if (value >= 1_000_000L) return trimDecimal(value / 1_000_000.0) + "M";
		if (value >= 1_000L) return trimDecimal(value / 1_000.0) + "K";
		return value + " gp";
	}

	private static String trimDecimal(double value)
	{
		String text = String.format(Locale.ROOT, "%.1f", Math.floor(value * 10) / 10);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}
	static Color coinColor(long value)
	{
		if (value < 100_000) return COIN_LOW;
		if (value < 10_000_000) return TEXT;
		return COIN_HIGH;
	}
	static String eventStartWhen(JsonObject event)
	{
		Instant start = eventStart(event);
		if (start == null) return "";
		ZonedDateTime from = start.atZone(ZoneId.systemDefault());
		return when(from, clock.instant()) + " " + zoneName(from);
	}

	static String eventDay(JsonObject event)
	{
		Instant now = clock.instant();
		boolean started = "active".equals(str(event, "status"));
		Instant at = started ? eventEnd(event) : eventStart(event);
		if (at == null) return "";
		return (started ? "Ends " : "Starts ") + day(at.atZone(ZoneId.systemDefault()), now);
	}

	static String statusLabel(String status)
	{
		if ("active".equals(status)) return "Live";
		if ("ended".equals(status)) return "Ended";
		return "Upcoming";
	}

	static String eventTypeLabel(JsonObject event)
	{
		switch (str(event, "type"))
		{
			case "skill": return "Skill of the Week";
			case "boss": return "Boss of the Week";
			case "drop-party": return "Custom";
			default: return "Bingo";
		}
	}

	static JsonObject eventConfig(JsonObject event)
	{
		return object(event, "config");
	}

	static JsonObject object(JsonObject object, String key)
	{
		return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
	}

	static List<JsonObject> objects(JsonArray array)
	{
		List<JsonObject> objects = new ArrayList<>();
		for (JsonElement element : array) objects.add(element.getAsJsonObject());
		return objects;
	}

	static String skillName(String key)
	{
		if (key == null || key.isEmpty()) return "";
		String lower = key.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	static String taskTypeLabel(JsonObject task)
	{
		String type = str(task, "type");
		JsonObject config = eventConfig(task);
		switch (type)
		{
			case "kill": return "Boss KC";
			case "raid": return "Raid";
			case "manual": return "Manual";
			case "xp": return "XP";
			case "drop":
				if (bool(config, "requireAllItems") || array(config, "itemGroups").size() > 0) return "Item set";
				String group = str(config, "itemGroup");
				return "jar".equals(group) ? "Any jar" : "pet".equals(group) ? "Any pet" : "Drop";
			default: return type.isEmpty() ? "Task" : Character.toUpperCase(type.charAt(0)) + type.substring(1);
		}
	}

	static JsonObject scoreFor(JsonArray scores, String teamId)
	{
		for (JsonObject score : objects(scores)) if (str(score, "teamId").equals(teamId)) return score;
		JsonObject empty = new JsonObject();
		empty.addProperty("points", 0);
		empty.addProperty("completedTasks", 0);
		empty.add("tasks", new JsonArray());
		return empty;
	}

	static JsonObject progressFor(JsonArray progressRows, String taskId)
	{
		for (JsonObject progress : objects(progressRows)) if (str(progress, "taskId").equals(taskId)) return progress;
		JsonObject empty = new JsonObject();
		empty.addProperty("progress", 0);
		empty.addProperty("target", 1);
		empty.addProperty("pending", false);
		empty.addProperty("completed", false);
		empty.add("completedBy", JsonNull.INSTANCE);
		return empty;
	}

	static JsonObject bestSet(JsonObject progress)
	{
		JsonObject best = null;
		for (JsonObject alt : objects(array(progress, "alternatives")))
		{
			if (best == null || share(alt) > share(best) || share(alt) == share(best) && integer(alt, "progress", 0) > integer(best, "progress", 0)) best = alt;
		}
		return best;
	}

	private static double share(JsonObject alt)
	{
		return integer(alt, "progress", 0) / (double) Math.max(1, integer(alt, "target", 1));
	}

	static String setName(JsonObject alt)
	{
		String name = TsgHubItemSets.nameFor(itemIds(array(alt, "items")));
		return name.isEmpty() ? "Set " + integer(alt, "group", 1) : name;
	}

	static List<Integer> itemIds(JsonArray items)
	{
		List<Integer> ids = new ArrayList<>();
		for (JsonObject item : objects(items)) ids.add(integer(item, "id", 0));
		return ids;
	}

	static final class SetLine
	{
		final String name;
		final int have;
		final int target;
		final List<String> found = new ArrayList<>();
		final List<String> finders = new ArrayList<>();
		final List<String> needed = new ArrayList<>();

		SetLine(String name, int have, int target)
		{
			this.name = name;
			this.have = have;
			this.target = target;
		}

		String foundText()
		{
			List<String> parts = new ArrayList<>();
			for (int i = 0; i < found.size(); i++) parts.add(finders.get(i).isEmpty() ? found.get(i) : found.get(i) + " (" + finders.get(i) + ")");
			return String.join(", ", parts);
		}

		String foundHtml(String self)
		{
			List<String> parts = new ArrayList<>();
			for (int i = 0; i < found.size(); i++)
			{
				String piece = escape(found.get(i));
				parts.add(finders.get(i).isEmpty() ? piece : piece + " (" + nameHtml(finders.get(i), self) + ")");
			}
			return String.join(", ", parts);
		}
	}

	static List<SetLine> setLines(JsonObject source, boolean withFinders)
	{
		List<JsonObject> options = objects(array(source, "alternatives"));
		if (options.isEmpty() && source.has("items"))
		{
			JsonObject single = new JsonObject();
			single.add("items", array(source, "items"));
			single.addProperty("progress", array(source, "items").size() - missingPieces(array(source, "items")).size());
			single.addProperty("target", array(source, "items").size());
			options.add(single);
		}
		options.sort((a, b) -> {
			int byShare = Double.compare(share(b), share(a));
			return byShare != 0 ? byShare : Integer.compare(integer(b, "progress", 0), integer(a, "progress", 0));
		});
		List<SetLine> lines = new ArrayList<>();
		for (JsonObject option : options)
		{
			boolean legacy = !option.has("group");
			String name = legacy ? "Pieces" : setName(option);
			SetLine line = new SetLine(name, integer(option, "progress", 0), integer(option, "target", 1));
			String prefix = legacy || name.startsWith("Set ") ? "" : name + " ";
			for (JsonObject item : objects(array(option, "items")))
			{
				String piece = str(item, "name");
				if (!prefix.isEmpty() && piece.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) piece = piece.substring(prefix.length());
				if (bool(item, "complete"))
				{
					line.found.add(piece);
					line.finders.add(withFinders ? str(item, "obtainedBy") : "");
				}
				else line.needed.add(piece);
			}
			lines.add(line);
		}
		return lines;
	}

	static List<String> missingPieces(JsonArray items)
	{
		List<String> missing = new ArrayList<>();
		for (JsonObject item : objects(items)) if (!bool(item, "complete")) missing.add(str(item, "name"));
		return missing;
	}

	static String nameHtml(String name, String self)
	{
		return samePlayer(name, self) ? selfName(escape(name)) : escape(name);
	}

	static String contributorsText(JsonObject progress, String separator, int max)
	{
		return contributors(progress, separator, max, name -> name);
	}

	static String contributorsHtml(JsonObject progress, String separator, int max, String self)
	{
		return contributors(progress, separator, max, name -> nameHtml(name, self));
	}

	private static String contributors(JsonObject progress, String separator, int max, UnaryOperator<String> name)
	{
		JsonArray contributors = array(progress, "contributors");
		if (contributors.size() == 0) return "";
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < Math.min(max, contributors.size()); i++)
		{
			JsonObject entry = contributors.get(i).getAsJsonObject();
			parts.add(name.apply(str(entry, "displayName")) + " " + integer(entry, "amount", 1));
		}
		if (contributors.size() > max) parts.add("+" + (contributors.size() - max));
		return String.join(separator, parts);
	}

	static String completedLine(JsonObject progress)
	{
		String scope = str(progress, "scope");
		String by = str(progress, "completedBy");
		boolean creditedToOrganizer = bool(progress, "override") && !bool(progress, "overrideCredited");
		if (creditedToOrganizer) return "Completed (credited to no one)";
		if ("individual".equals(scope) || by.isEmpty()) return "Completed";
		if (!"solo".equals(scope) && array(progress, "contributors").size() > 0) return "Completed";
		return "Completed by " + by;
	}

	static String teamIdFor(JsonObject event, String displayName)
	{
		for (JsonObject member : objects(array(event, "members")))
		{
			if (str(member, "displayName").equalsIgnoreCase(displayName) && !str(member, "teamId").isEmpty()) return str(member, "teamId");
		}
		return "";
	}

	abstract static class HoverIcon implements Icon
	{
		private final int size;
		HoverIcon(int size) { this.size = size; }
		@Override public int getIconWidth() { return size; }
		@Override public int getIconHeight() { return size; }

		@Override
		public final void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			boolean hovered = component instanceof AbstractButton && ((AbstractButton) component).getModel().isRollover();
			boolean enabled = component == null || component.isEnabled();
			g.setColor(!enabled ? BORDER : hovered ? hoverColor() : MUTED);
			g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			paint(g, component);
			g.dispose();
		}

		abstract void paint(Graphics2D g, Component component);

		Color hoverColor() { return ACCENT; }
	}

	static final class TrashIcon extends HoverIcon
	{
		TrashIcon() { super(16); }
		@Override Color hoverColor() { return ERROR; }
		@Override void paint(Graphics2D g, Component c)
		{
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(2, 4, 14, 4);
			g.drawLine(6, 4, 6, 2);
			g.drawLine(6, 2, 10, 2);
			g.drawLine(10, 2, 10, 4);
			g.drawRect(4, 4, 8, 10);
			g.drawLine(7, 7, 7, 11);
			g.drawLine(9, 7, 9, 11);
		}
	}

	static boolean confirmDelete(Component parent, String title, String message, String action)
	{
		Object[] options = {action, "Cancel"};
		int choice = JOptionPane.showOptionDialog(parent, message, title, JOptionPane.DEFAULT_OPTION,
			JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		return choice == 0;
	}

	static final class BackIcon extends HoverIcon
	{
		BackIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.drawLine(10, 3, 5, 8);
			g.drawLine(5, 8, 10, 13);
		}
	}

	static final class RefreshIcon extends HoverIcon
	{
		private float angle;
		private Timer timer;

		RefreshIcon() { super(16); }

		void setSpinning(boolean spinning, Component owner)
		{
			if (spinning)
			{
				if (timer == null)
				{
					timer = new Timer(30, e -> { angle = (angle + 18) % 360; owner.repaint(); });
				}
				if (!timer.isRunning()) timer.start();
			}
			else if (timer != null)
			{
				timer.stop();
				angle = 0;
				owner.repaint();
			}
		}

		@Override void paint(Graphics2D g, Component c)
		{
			g.rotate(Math.toRadians(angle), 8, 8);
			g.drawArc(2, 2, 12, 12, 48, 292);
			g.fillPolygon(new int[] {12, 16, 11}, new int[] {1, 5, 6}, 3);
		}
	}

	static final class OrganizerIcon extends HoverIcon
	{
		OrganizerIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.setStroke(new BasicStroke(1.4f));
			g.drawRect(2, 2, 12, 12);
			g.drawLine(2, 6, 14, 6);
			g.drawLine(6, 6, 6, 14);
		}
	}

	abstract static class TileIcon implements Icon
	{
		@Override public int getIconWidth() { return 24; }
		@Override public int getIconHeight() { return 24; }

		@Override
		public final void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.scale(1.5, 1.5);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ACCENT);
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			paint(g);
			g.dispose();
		}

		abstract void paint(Graphics2D g);
	}

	static final class CalendarIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			g.drawRoundRect(2, 3, 12, 11, 2, 2);
			g.drawLine(2, 7, 14, 7);
			g.drawLine(5, 1, 5, 4);
			g.drawLine(11, 1, 11, 4);
			g.fillRect(5, 9, 2, 2);
			g.fillRect(9, 9, 2, 2);
		}
	}

	static final class PartyIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			g.drawOval(3, 2, 5, 5);
			g.drawArc(0, 9, 11, 10, 0, 180);
			g.drawOval(10, 3, 4, 4);
			g.drawArc(9, 9, 7, 8, 20, 160);
		}
	}

	static final class MembersIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			for (int y = 3; y <= 13; y += 5)
			{
				g.fillOval(1, y - 1, 3, 3);
				g.drawLine(7, y, 15, y);
			}
		}
	}

	static final class DropsIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			g.drawRoundRect(2, 6, 12, 8, 2, 2);
			g.drawLine(2, 9, 14, 9);
			g.drawLine(8, 1, 8, 5);
			g.drawLine(6, 3, 8, 5);
			g.drawLine(10, 3, 8, 5);
		}
	}

	static final class CogIcon extends HoverIcon
	{
		CogIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.drawOval(3, 3, 10, 10);
			g.drawOval(6, 6, 4, 4);
			g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
			for (int i = 0; i < 8; i++)
			{
				double a = Math.PI / 4 * i;
				g.draw(new Line2D.Double(8 + 5 * Math.cos(a), 8 + 5 * Math.sin(a), 8 + 7.5 * Math.cos(a), 8 + 7.5 * Math.sin(a)));
			}
		}
	}

	static final class DiscordIcon extends HoverIcon
	{
		private static final Color BLURPLE = new Color(88, 101, 242);

		DiscordIcon() { super(20); }
		@Override Color hoverColor() { return BLURPLE; }
		@Override void paint(Graphics2D g, Component c)
		{
			Path2D body = new Path2D.Double();
			body.moveTo(5, 3.5);
			body.quadTo(10, 2.5, 15, 3.5);
			body.curveTo(18, 7, 19.5, 11, 19, 15);
			body.quadTo(17, 17, 14.5, 17.5);
			body.lineTo(13.5, 15.5);
			body.quadTo(10, 16.8, 6.5, 15.5);
			body.lineTo(5.5, 17.5);
			body.quadTo(3, 17, 1, 15);
			body.curveTo(0.5, 11, 2, 7, 5, 3.5);
			body.closePath();
			Area face = new Area(body);
			face.subtract(new Area(new Ellipse2D.Double(5.5, 8.5, 3, 3.5)));
			face.subtract(new Area(new Ellipse2D.Double(11.5, 8.5, 3, 3.5)));
			g.fill(face);
		}
	}

	static final class CheckIcon implements Icon
	{
		@Override public int getIconWidth() { return 14; }
		@Override public int getIconHeight() { return 14; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(SUCCESS);
			g.fillOval(0, 0, 14, 14);
			g.setColor(CARD);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D tick = new Path2D.Double();
			tick.moveTo(3.5, 7.5);
			tick.lineTo(6, 10);
			tick.lineTo(10.5, 4.5);
			g.draw(tick);
			g.dispose();
		}
	}

	static final class WarningIcon implements Icon
	{
		@Override public int getIconWidth() { return 12; }
		@Override public int getIconHeight() { return 11; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ERROR);
			g.fillPolygon(new int[] {6, 12, 0}, new int[] {0, 11, 11}, 3);
			g.setColor(CARD);
			g.fillRect(5, 3, 2, 4);
			g.fillRect(5, 8, 2, 2);
			g.dispose();
		}
	}

	static final class NoteIcon implements Icon
	{
		@Override public int getIconWidth() { return 11; }
		@Override public int getIconHeight() { return 13; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(WARNING);
			g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawPolygon(new int[] {1, 7, 10, 10, 1}, new int[] {1, 1, 4, 12, 12}, 5);
			g.drawLine(3, 6, 8, 6);
			g.drawLine(3, 9, 8, 9);
			g.dispose();
		}
	}

	static final class ScopeIcon implements Icon
	{
		private final int people;

		ScopeIcon(int people) { this.people = people; }
		@Override public int getIconWidth() { return (int) Math.ceil((8 + (people - 1) * 5) * 0.75); }
		@Override public int getIconHeight() { return 9; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.scale(0.75, 0.75);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (int i = people - 1; i >= 0; i--)
			{
				int left = i * 5;
				g.setColor(CARD);
				g.fillOval(left - 1, -1, 6, 6);
				g.fillArc(left - 2, 5, 12, 13, 0, 180);
				g.setColor(MUTED);
				g.fillOval(left + 2, 0, 4, 4);
				g.fillArc(left, 6, 8, 10, 0, 180);
			}
			g.dispose();
		}
	}

	static final class LockIcon implements Icon
	{
		@Override public int getIconWidth() { return 10; }
		@Override public int getIconHeight() { return 12; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(MUTED);
			g.setStroke(new BasicStroke(1.5f));
			g.drawArc(2, 1, 6, 8, 0, 180);
			g.drawLine(2, 5, 2, 6);
			g.drawLine(8, 5, 8, 6);
			g.fillRoundRect(0, 6, 10, 6, 2, 2);
			g.dispose();
		}
	}

	static Border bottomRule()
	{
		return BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER);
	}
}
