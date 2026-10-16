package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubActivity.Member;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.client.util.QuantityFormatter;

final class TsgHubActivityDetail extends JPanel
{
	static final int DETAIL_W = 520;
	private static final int MAX_GROUP_CONTENT = 20;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);

	private final JLabel name = label(" ", TEXT, titleFont());
	private final JLabel meta = caption(" ");
	private final JLabel message = caption(" ");
	private final WeeklyChart chart = new WeeklyChart();
	private final JLabel chartCaption = caption(" ");
	private final JPanel events = stack();
	private final JPanel groupContent = stack();
	private final JPanel body = stack();
	private String showing = "";

	TsgHubActivityDetail()
	{
		super(new BorderLayout());
		setBackground(BACKGROUND);
		WidthTrackingPanel content = new WidthTrackingPanel();
		content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
		content.add(name);
		content.add(Box.createVerticalStrut(GAP_XS));
		content.add(meta);
		content.add(Box.createVerticalStrut(GAP_M));
		content.add(message);

		body.add(sectionTitle("Hours per week, last " + WeeklyChart.WEEKS + " weeks"));
		body.add(chart);
		body.add(Box.createVerticalStrut(GAP_XS));
		body.add(chartCaption);
		body.add(sectionTitle("Events"));
		body.add(events);
		body.add(sectionTitle("Group content"));
		body.add(groupContent);
		content.add(body);
		add(scroll(content), BorderLayout.CENTER);
	}

	void show(Member member, BufferedImage rankIcon, Instant now)
	{
		showing = member.name;
		name.setText(member.name);
		name.setIcon(rankIcon == null ? null : new ImageIcon(rankIcon));
		List<String> parts = new ArrayList<>();
		if (!member.rank.isEmpty()) parts.add(member.rank);
		if (member.joined != null) parts.add("joined clan " + member.joined.format(DATE));
		if (member.onHub()) parts.add("last seen " + TsgHubActivity.ago(member.lastSeen(), now));
		if (!member.previousNames().isEmpty()) parts.add("previously " + String.join(", ", member.previousNames()));
		meta.setText(html(escape(String.join(" · ", parts)), DETAIL_W));
		body.setVisible(false);
		message.setForeground(MUTED);
		message.setText(member.onHub() ? "Loading activity..." : html(escape(member.name + " isn't on TSG Hub yet, so there's no activity to show."), DETAIL_W));
		message.setVisible(true);
		refresh(this);
	}

	void failed(Member member, String error)
	{
		if (!member.name.equals(showing)) return;
		message.setForeground(TsgHubTheme.ERROR);
		message.setText(html(escape(error), DETAIL_W));
		message.setVisible(true);
		body.setVisible(false);
		refresh(this);
	}

	void setDetail(Member member, JsonObject detail)
	{
		if (!member.name.equals(showing)) return;
		Map<LocalDate, Integer> minutes = new HashMap<>();
		int total = 0;
		for (JsonObject day : objects(array(detail, "days")))
		{
			LocalDate date = parseDay(str(day, "day"));
			int value = integer(day, "minutes", 0);
			if (date == null || value <= 0) continue;
			minutes.merge(date, value, Integer::sum);
			total += value;
		}
		LocalDate last = WeeklyChart.lastDay(minutes);
		chart.setMinutes(minutes, last);
		int active = 0;
		for (LocalDate day : minutes.keySet()) if (!day.isBefore(WeeklyChart.weekStart(last, 0))) active++;
		chartCaption.setText(minutes.isEmpty() ? "No time online recorded." : String.format(Locale.US, "Avg %.1fh a week · active %d of %d days.", total / 60f / WeeklyChart.WEEKS, active, TsgHubActivity.DETAIL_DAYS));

		events.removeAll();
		List<JsonObject> eventRows = objects(array(detail, "events"));
		if (eventRows.isEmpty()) events.add(caption("No events in this window."));
		for (JsonObject event : eventRows) events.add(eventRow(event));

		groupContent.removeAll();
		List<JsonObject> groupRows = objects(array(detail, "groupContent"));
		if (groupRows.isEmpty()) groupContent.add(caption("No group content with clanmates yet."));
		for (int i = 0; i < groupRows.size() && i < MAX_GROUP_CONTENT; i++) groupContent.add(groupRow(groupRows.get(i)));

		message.setVisible(false);
		body.setVisible(true);
		refresh(this);
	}

	private static JComponent eventRow(JsonObject event)
	{
		boolean attended = !event.has("attended") || bool(event, "attended");
		String title = str(event, "name").isEmpty() ? "Untitled event" : str(event, "name");
		Instant joined = instant(str(event, "joinedAt"));
		JPanel top = row(shrinkable(label(title, attended ? TEXT : MUTED, boldFont())), caption(joined == null ? "" : format(joined, DATE)));
		List<String> parts = new ArrayList<>();
		String type = capitalize(str(event, "type").replace('_', ' '));
		if (!type.isEmpty()) parts.add(type);
		if (!str(event, "teamId").isEmpty()) parts.add("team " + str(event, "teamId"));
		if (attended)
		{
			int xp = integer(event, "xpGained", 0), kills = integer(event, "lootKills", 0), claims = integer(event, "claimsApproved", 0);
			if (xp > 0) parts.add(QuantityFormatter.quantityToStackSize(xp) + " xp");
			if (kills > 0) parts.add(plural(kills, "kill"));
			if (claims > 0) parts.add(plural(claims, "approved claim"));
		}
		else parts.add("joined, no contribution");
		Instant left = instant(str(event, "leftAt")), ended = instant(str(event, "endedAt"));
		if (left != null && (ended == null || left.isBefore(ended))) parts.add("left early");
		JLabel detail = label(String.join(" · ", parts), attended ? MUTED : DIM, smallFont());
		return listCard(twoLines(top, detail));
	}

	private static JComponent groupRow(JsonObject group)
	{
		String content = str(group, "content").isEmpty() ? "Unknown content" : str(group, "content");
		Instant last = instant(str(group, "lastAt"));
		List<String> with = new ArrayList<>();
		for (JsonElement name : array(group, "with"))
		{
			if (name.isJsonPrimitive()) with.add(name.getAsString());
		}
		JPanel top = row(shrinkable(label(content + " · " + plural(integer(group, "completions", 0), "completion"), TEXT, plainFont())),
			caption(last == null ? "" : format(last, DATE)));
		JLabel detail = label(with.isEmpty() ? "" : "with " + String.join(", ", with), MUTED, smallFont());
		return listCard(twoLines(top, detail));
	}

	private static JComponent listCard(JComponent content)
	{
		JPanel card = panel(new BorderLayout());
		card.setOpaque(true);
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BACKGROUND), cardBorder()));
		card.add(content, BorderLayout.CENTER);
		return fitHeight(card);
	}

	private static String format(Instant when, DateTimeFormatter formatter)
	{
		return when.atZone(ZoneId.systemDefault()).format(formatter);
	}

	private static LocalDate parseDay(String day)
	{
		try { return day.isEmpty() ? null : LocalDate.parse(day); }
		catch (DateTimeParseException e) { return null; }
	}

	static final class WeeklyChart extends JComponent
	{
		static final int WEEKS = TsgHubActivity.DETAIL_DAYS / 7;
		private static final int HEIGHT = 140;
		private static final int AXIS = 16;
		private static final int TOP = 6;
		private static final int[] STEPS = {60, 120, 300, 600, 1200, 1500, 3000, 6000};
		private static final DateTimeFormatter TIP = DateTimeFormatter.ofPattern("d MMM", Locale.US);
		private static final DateTimeFormatter AXIS_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US);
		private Map<LocalDate, Integer> minutes = new HashMap<>();
		private LocalDate end = LocalDate.now(TsgHubUi.clock);
		private int gutter;

		WeeklyChart()
		{
			setPreferredSize(new Dimension(DETAIL_W, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
			setMinimumSize(new Dimension(100, HEIGHT));
			setFont(smallFont());
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		void setMinutes(Map<LocalDate, Integer> next, LocalDate last)
		{
			minutes = next;
			end = last;
			repaint();
		}

		static LocalDate lastDay(Map<LocalDate, Integer> minutes)
		{
			LocalDate today = LocalDate.now(TsgHubUi.clock);
			for (LocalDate day : minutes.keySet()) if (day.isAfter(today)) today = day;
			return today;
		}

		static LocalDate weekStart(LocalDate end, int week)
		{
			return end.minusDays(7L * (WEEKS - week) - 1);
		}

		static int[] weekMinutes(Map<LocalDate, Integer> minutes, LocalDate end)
		{
			int[] weeks = new int[WEEKS];
			for (int week = 0; week < WEEKS; week++)
			{
				LocalDate start = weekStart(end, week);
				for (int day = 0; day < 7; day++) weeks[week] += minutes.getOrDefault(start.plusDays(day), 0);
			}
			return weeks;
		}

		static int step(int max)
		{
			for (int step : STEPS) if (max <= step * 4) return step;
			int step = STEPS[STEPS.length - 1];
			while (max > step * 4) step *= 2;
			return step;
		}

		private static String hours(int minutes)
		{
			return minutes % 60 == 0 ? minutes / 60 + "h" : span(Duration.ofMinutes(minutes));
		}

		private int weekAt(int x)
		{
			int width = getWidth() - gutter;
			if (width <= 0 || x < gutter) return -1;
			int week = (int) ((x - gutter) / (width / (double) WEEKS));
			return week >= 0 && week < WEEKS ? week : -1;
		}

		@Override
		public String getToolTipText(MouseEvent event)
		{
			int week = weekAt(event.getX());
			if (week < 0) return null;
			LocalDate start = weekStart(end, week);
			int total = 0, active = 0;
			for (int day = 0; day < 7; day++)
			{
				int value = minutes.getOrDefault(start.plusDays(day), 0);
				total += value;
				if (value > 0) active++;
			}
			String range = start.format(TIP) + " to " + start.plusDays(6).format(TIP);
			return range + ": " + (total == 0 ? "offline" : (total >= 60 ? span(Duration.ofMinutes(total)) : total + " min") + ", active " + active + " of 7 days");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(getFont());
			FontMetrics metrics = g.getFontMetrics();
			int[] weeks = weekMinutes(minutes, end);
			int max = 0;
			for (int value : weeks) max = Math.max(max, value);
			int step = step(max);
			int top = Math.max(step, (max + step - 1) / step * step);
			gutter = 0;
			for (int value = 0; value <= top; value += step) gutter = Math.max(gutter, metrics.stringWidth(hours(value)));
			gutter += 8;
			int width = getWidth(), plot = getHeight() - AXIS, height = plot - TOP;
			for (int value = 0; value <= top; value += step)
			{
				int y = plot - (int) Math.round(height * value / (double) top);
				g.setColor(value == 0 ? MUTED : BORDER);
				g.fillRect(gutter, y, width - gutter, 1);
				g.setColor(MUTED);
				String label = hours(value);
				g.drawString(label, gutter - 6 - metrics.stringWidth(label), y + metrics.getAscent() / 2 - 1);
			}
			double slot = (width - gutter) / (double) WEEKS;
			int gap = Math.max(2, (int) (slot / 4));
			g.setColor(ACCENT);
			for (int week = 0; week < WEEKS; week++)
			{
				if (weeks[week] <= 0) continue;
				int x = gutter + (int) Math.round(week * slot) + gap / 2;
				int w = Math.max(1, (int) Math.round(slot) - gap);
				int h = Math.max(2, (int) Math.round(height * weeks[week] / (double) top));
				g.fillRect(x, plot - h, w, h);
			}
			g.setColor(MUTED);
			int baseline = getHeight() - 2;
			g.drawString(weekStart(end, 0).format(AXIS_DATE), gutter, baseline);
			String last = weekStart(end, WEEKS - 1).format(AXIS_DATE);
			g.drawString(last, width - metrics.stringWidth(last), baseline);
			g.dispose();
		}
	}
}
