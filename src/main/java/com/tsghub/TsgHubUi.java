package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.SwingUtil;

final class TsgHubUi
{
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	static final Color BORDER = ColorScheme.MEDIUM_GRAY_COLOR;
	static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	static final Color TEXT = Color.WHITE;
	static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	static final Color SUCCESS = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color ERROR = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color WARNING = new Color(230, 180, 60);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d");
	private static final DateTimeFormatter DATE_WITH_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy");

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

	static JPanel emptyState(String heading, String body, int widthPx)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));
		JLabel title = label(heading, TEXT, FontManager.getRunescapeBoldFont());
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		JLabel text = label(html("<div style='text-align:center'>" + escape(body) + "</div>", widthPx), MUTED, FontManager.getRunescapeSmallFont());
		text.setAlignmentX(Component.CENTER_ALIGNMENT);
		text.setHorizontalAlignment(JLabel.CENTER);
		panel.add(title);
		panel.add(javax.swing.Box.createVerticalStrut(8));
		panel.add(text);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	static JLabel sectionTitle(String text)
	{
		JLabel label = label(text, ACCENT, FontManager.getRunescapeBoldFont());
		label.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
		return label;
	}

	static JLabel badge(String text, Color color)
	{
		JLabel label = label(text, color, FontManager.getRunescapeSmallFont());
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

	static JPanel card()
	{
		JPanel panel = new JPanel(new BorderLayout(6, 4))
		{
			// Anchor tooltips beside the card so they don't follow the mouse.
			@Override
			public java.awt.Point getToolTipLocation(MouseEvent event)
			{
				String text = getToolTipText(event);
				if (text == null) return null;
				javax.swing.JToolTip tip = createToolTip();
				tip.setTipText(text);
				return new java.awt.Point(-tip.getPreferredSize().width - 6, 0);
			}
		};
		panel.setBackground(CARD);
		panel.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
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
		if (component instanceof java.awt.Container)
			for (Component child : ((java.awt.Container) component).getComponents()) recolor(child, from, to);
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
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
	}

	static final class StatusLine extends JLabel
	{
		private final Timer clear = new Timer(0, e -> setVisible(false));
		private final int widthPx;

		StatusLine(int widthPx)
		{
			this.widthPx = widthPx;
			clear.setRepeats(false);
			setFont(FontManager.getRunescapeSmallFont());
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

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TsgHubUi.class);

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
		String left = a == null ? "" : a.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
		String right = b == null ? "" : b.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
		return !left.isEmpty() && left.equals(right);
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

	static String formatDate(String isoDate, boolean withYear)
	{
		if (isoDate == null || isoDate.isEmpty()) return "TBD";
		try { return LocalDate.parse(isoDate).format(withYear ? DATE_WITH_YEAR : DATE); }
		catch (RuntimeException ignored) { return isoDate; }
	}

	static String dateRange(JsonObject event)
	{
		String start = str(event, "startDate");
		String end = str(event, "endDate");
		boolean showYear = !start.startsWith(String.valueOf(LocalDate.now().getYear()))
			|| !end.isEmpty() && !end.startsWith(String.valueOf(LocalDate.now().getYear()));
		return formatDate(start, false) + " to " + formatDate(end, showYear);
	}

	static String statusLabel(String status)
	{
		if ("active".equals(status)) return "Live";
		if ("ended".equals(status)) return "Ended";
		return "Upcoming";
	}

	static Color statusColor(String status)
	{
		if ("active".equals(status)) return SUCCESS;
		if ("ended".equals(status)) return MUTED;
		return WARNING;
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
		return event != null && event.has("config") && event.get("config").isJsonObject() ? event.getAsJsonObject("config") : new JsonObject();
	}

	static String skillName(String key)
	{
		if (key == null || key.isEmpty()) return "";
		String lower = key.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	static String eventTypeLine(JsonObject event)
	{
		JsonObject config = eventConfig(event);
		switch (str(event, "type"))
		{
			case "skill": return "Skill of the Week · " + skillName(str(config, "skill"));
			case "boss": return "Boss of the Week · " + str(config, "npcName");
			case "drop-party":
				String where = (integer(config, "world", 0) > 0 ? " · W" + integer(config, "world", 0) : "")
					+ (str(config, "location").isEmpty() ? "" : " · " + str(config, "location"));
				return "Custom" + where;
			default: return "Bingo";
		}
	}

	static String dropPartyTime(JsonObject config, boolean long_)
	{
		try
		{
			java.time.ZonedDateTime when = java.time.Instant.parse(str(config, "startsAt")).atZone(java.time.ZoneId.systemDefault());
			return when.format(DateTimeFormatter.ofPattern(long_ ? "EEEE, MMM d 'at' h:mm a" : "EEE MMM d, h:mm a"));
		}
		catch (RuntimeException e) { return "time TBD"; }
	}

	static String dropPartyCountdown(JsonObject config)
	{
		try
		{
			long seconds = java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(str(config, "startsAt"))).getSeconds();
			if (seconds <= 0) return seconds > -2 * 3600 ? "Happening now" : "Finished";
			long days = seconds / 86400, hours = seconds % 86400 / 3600, minutes = seconds % 3600 / 60;
			if (days > 0) return "Starts in " + days + "d " + hours + "h";
			if (hours > 0) return "Starts in " + hours + "h " + minutes + "m";
			return "Starts in " + Math.max(1, minutes) + "m";
		}
		catch (RuntimeException e) { return ""; }
	}

	static String taskTypeLabel(JsonObject task)
	{
		String type = str(task, "type");
		JsonObject config = task.has("config") && task.get("config").isJsonObject() ? task.getAsJsonObject("config") : new JsonObject();
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
		for (int i = 0; i < scores.size(); i++)
		{
			JsonObject score = scores.get(i).getAsJsonObject();
			if (str(score, "teamId").equals(teamId)) return score;
		}
		JsonObject empty = new JsonObject();
		empty.addProperty("points", 0);
		empty.addProperty("completedTasks", 0);
		empty.add("tasks", new JsonArray());
		return empty;
	}

	static JsonObject progressFor(JsonArray progressRows, String taskId)
	{
		for (int i = 0; i < progressRows.size(); i++)
		{
			JsonObject progress = progressRows.get(i).getAsJsonObject();
			if (str(progress, "taskId").equals(taskId)) return progress;
		}
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
		JsonArray alternatives = array(progress, "alternatives");
		JsonObject best = null;
		for (int i = 0; i < alternatives.size(); i++)
		{
			JsonObject alt = alternatives.get(i).getAsJsonObject();
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
		List<Integer> ids = new ArrayList<>();
		JsonArray items = array(alt, "items");
		for (int i = 0; i < items.size(); i++) ids.add(integer(items.get(i).getAsJsonObject(), "id", 0));
		String name = TsgHubItemSets.nameFor(ids);
		return name.isEmpty() ? "Set " + integer(alt, "group", 1) : name;
	}

	static final class SetLine
	{
		final String name;
		final int have;
		final int target;
		final List<String> found = new ArrayList<>();   // "helm (Demo Lynx)" or "helm"
		final List<String> needed = new ArrayList<>();  // "flail"

		SetLine(String name, int have, int target)
		{
			this.name = name;
			this.have = have;
			this.target = target;
		}
	}

	static List<SetLine> setLines(JsonObject source, boolean withFinders)
	{
		List<JsonObject> options = new ArrayList<>();
		JsonArray alternatives = array(source, "alternatives");
		for (int i = 0; i < alternatives.size(); i++) options.add(alternatives.get(i).getAsJsonObject());
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
			JsonArray items = array(option, "items");
			for (int i = 0; i < items.size(); i++)
			{
				JsonObject item = items.get(i).getAsJsonObject();
				String piece = str(item, "name");
				if (!prefix.isEmpty() && piece.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) piece = piece.substring(prefix.length());
				if (bool(item, "complete"))
				{
					String who = str(item, "obtainedBy");
					line.found.add(withFinders && !who.isEmpty() ? piece + " (" + who + ")" : piece);
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
		for (int i = 0; i < items.size(); i++)
		{
			JsonObject item = items.get(i).getAsJsonObject();
			if (!bool(item, "complete")) missing.add(str(item, "name"));
		}
		return missing;
	}

	static String contributorsText(JsonObject progress, String separator, int max)
	{
		JsonArray contributors = array(progress, "contributors");
		if (contributors.size() == 0) return "";
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < Math.min(max, contributors.size()); i++)
		{
			JsonObject entry = contributors.get(i).getAsJsonObject();
			parts.add(str(entry, "displayName") + " " + integer(entry, "amount", 1));
		}
		if (contributors.size() > max) parts.add("+" + (contributors.size() - max) + " more");
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
		JsonArray members = array(event, "members");
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
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
		int choice = javax.swing.JOptionPane.showOptionDialog(parent, message, title, javax.swing.JOptionPane.DEFAULT_OPTION,
			javax.swing.JOptionPane.WARNING_MESSAGE, null, options, options[1]);
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

	static final class MenuIcon extends HoverIcon
	{
		MenuIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.fillOval(7, 2, 3, 3);
			g.fillOval(7, 7, 3, 3);
			g.fillOval(7, 12, 3, 3);
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

	static void onEnter(javax.swing.JTextField field, Consumer<String> action)
	{
		field.addActionListener(e -> action.accept(field.getText()));
	}
}
