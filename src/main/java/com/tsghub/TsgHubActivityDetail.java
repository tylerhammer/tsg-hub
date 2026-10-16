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
	private final MinutesChart chart = new MinutesChart();
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

		body.add(sectionTitle("Time online, last " + TsgHubActivity.DETAIL_DAYS + " days"));
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
		chart.setMinutes(minutes);
		chartCaption.setText(minutes.isEmpty() ? "No time online recorded." : String.format(Locale.US, "%.1f hours across %s.", total / 60f, plural(minutes.size(), "day")));

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

	static final class MinutesChart extends JComponent
	{
		private static final int HEIGHT = 120;
		private static final int AXIS = 14;
		private static final DateTimeFormatter TIP = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US);
		private static final DateTimeFormatter AXIS_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US);
		private Map<LocalDate, Integer> minutes = new HashMap<>();
		private LocalDate end = LocalDate.now(TsgHubUi.clock);

		MinutesChart()
		{
			setPreferredSize(new Dimension(DETAIL_W, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
			setMinimumSize(new Dimension(100, HEIGHT));
			setFont(smallFont());
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		void setMinutes(Map<LocalDate, Integer> next)
		{
			minutes = next;
			LocalDate today = LocalDate.now(TsgHubUi.clock);
			for (LocalDate day : next.keySet()) if (day.isAfter(today)) today = day;
			end = today;
			repaint();
		}

		private LocalDate dayAt(int index)
		{
			return end.minusDays(TsgHubActivity.DETAIL_DAYS - 1 - index);
		}

		@Override
		public String getToolTipText(MouseEvent event)
		{
			int index = (int) (event.getX() / (getWidth() / (double) TsgHubActivity.DETAIL_DAYS));
			if (index < 0 || index >= TsgHubActivity.DETAIL_DAYS) return null;
			LocalDate day = dayAt(index);
			int value = minutes.getOrDefault(day, 0);
			return day.format(TIP) + ": " + (value == 0 ? "offline" : value >= 60 ? span(Duration.ofMinutes(value)) : value + " min");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int width = getWidth(), plot = getHeight() - AXIS;
			g.setColor(CARD);
			g.fillRect(0, 0, width, plot);
			int max = 1;
			for (int value : minutes.values()) max = Math.max(max, value);
			double slot = width / (double) TsgHubActivity.DETAIL_DAYS;
			int gap = slot >= 4 ? 1 : 0;
			for (int i = 0; i < TsgHubActivity.DETAIL_DAYS; i++)
			{
				int value = minutes.getOrDefault(dayAt(i), 0);
				int x = (int) Math.round(i * slot);
				int w = Math.max(1, (int) Math.round((i + 1) * slot) - x - gap);
				if (value <= 0)
				{
					g.setColor(BORDER);
					g.fillRect(x, plot - 1, w, 1);
					continue;
				}
				int h = Math.max(2, (int) Math.round((plot - 6) * value / (double) max));
				g.setColor(ACCENT);
				g.fillRect(x, plot - h, w, h);
			}
			g.setColor(MUTED);
			g.setFont(getFont());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = getHeight() - 2;
			g.drawString(dayAt(0).format(AXIS_DATE), 0, baseline);
			String today = end.format(AXIS_DATE);
			g.drawString(today, width - metrics.stringWidth(today), baseline);
			if (!minutes.isEmpty())
			{
				String peak = (max >= 60 ? span(Duration.ofMinutes(max)) : max + " min") + " max";
				g.drawString(peak, width - metrics.stringWidth(peak), metrics.getAscent());
			}
			g.dispose();
		}
	}
}
