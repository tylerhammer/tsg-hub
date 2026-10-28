package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubJson.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubCountdown extends Overlay
{
	static final Duration END_WARNING = Duration.ofHours(24);
	static final Duration OPEN_ENDED = Duration.ofMinutes(30);
	private static final int PAD = 6;
	private static final int BAR = 3;
	private static final int ROW_GAP = 3;
	private static final int COLUMN_GAP = 12;
	private static final int MIN_WIDTH = 130;
	private static final Color CARD = new Color(0x1F, 0x1D, 0x22, 235);
	private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US);

	private final Client client;
	private final TsgHubConfig config;
	private final Supplier<JsonArray> events;
	private final Predicate<String> joined;
	private final Set<String> dismissed = new HashSet<>();
	private final Dimension size = new Dimension();
	private String shownId = "";
	private String name;
	private String phase;
	private String time;
	private String detail;
	private String at;
	private String offWorld;

	TsgHubCountdown(Client client, TsgHubConfig config, Supplier<JsonArray> events, Predicate<String> joined)
	{
		this.client = client;
		this.config = config;
		this.events = events;
		this.joined = joined;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Dismiss", "TSG Hub countdown", e -> dismissed.add(shownId));
	}

	void onGameTick(boolean enabled)
	{
		Instant now = TsgHubFormat.clock.instant();
		JsonObject event = enabled ? pick(events.get(), now, Duration.ofMinutes(config.countdownLeadMinutes()), joined, dismissed) : null;
		if (event == null)
		{
			name = null;
			return;
		}
		shownId = str(event, "id");
		name = eventName(event);
		phase = phase(event, now);
		Instant target = target(event, now);
		time = target == null ? "" : countdown(Duration.between(now, target));
		detail = detail(event);
		at = target == null ? "" : localTime(target, ZoneId.systemDefault(), now);
		int world = integer(eventConfig(event), "world", 0);
		offWorld = world > 0 && client.getWorld() != world ? "You're on W" + client.getWorld() : null;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (name == null) return null;
		g.setFont(TsgHubTheme.boldFont());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(TsgHubTheme.smallFont());
		FontMetrics small = g.getFontMetrics();
		int x = BAR + PAD;
		int width = Math.max(MIN_WIDTH, Math.max(bold.stringWidth(name), Math.max(
			small.stringWidth(phase) + COLUMN_GAP + bold.stringWidth(time),
			small.stringWidth(detail) + COLUMN_GAP + small.stringWidth(at))) + x + PAD);
		if (offWorld != null) width = Math.max(width, small.stringWidth(offWorld) + x + PAD);
		int row = small.getAscent() + ROW_GAP;
		int height = PAD * 2 + bold.getAscent() + row * (offWorld == null ? 2 : 3);
		Color edge = offWorld == null ? TsgHubTheme.ACCENT : TsgHubTheme.WARNING;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(CARD);
		g.fillRoundRect(0, 0, width, height, 8, 8);
		g.setColor(TsgHubTheme.BORDER);
		g.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
		g.setColor(edge);
		g.fillRoundRect(0, 0, BAR, height, BAR, BAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		int baseline = PAD + bold.getAscent() - 1;
		g.setFont(TsgHubTheme.boldFont());
		g.setColor(TsgHubTheme.TEXT);
		g.drawString(name, x, baseline);
		baseline += row;
		g.setColor(edge);
		g.drawString(time, width - PAD - bold.stringWidth(time), baseline);
		g.setFont(TsgHubTheme.smallFont());
		g.setColor(TsgHubTheme.MUTED);
		g.drawString(phase, x, baseline);
		baseline += row;
		g.setColor(offWorld == null ? TsgHubTheme.MUTED : TsgHubTheme.WARNING);
		g.drawString(detail, x, baseline);
		g.setColor(TsgHubTheme.DIM);
		g.drawString(at, width - PAD - small.stringWidth(at), baseline);
		if (offWorld != null)
		{
			baseline += row;
			g.setColor(TsgHubTheme.WARNING);
			g.drawString(offWorld, x, baseline);
		}
		size.setSize(width, height);
		return size;
	}

	static JsonObject pick(JsonArray events, Instant now, Duration lead, Predicate<String> joined, Set<String> dismissed)
	{
		if (events == null) return null;
		JsonObject best = null;
		Instant bestAt = null;
		boolean bestCustom = false;
		for (JsonObject event : objects(events))
		{
			if (dismissed.contains(str(event, "id")) || !showing(event, now, lead, joined)) continue;
			boolean custom = "drop-party".equals(str(event, "type"));
			Instant at = target(event, now);
			if (at == null) at = eventStart(event);
			if (best == null || custom && !bestCustom || custom == bestCustom && at.isBefore(bestAt))
			{
				best = event;
				bestAt = at;
				bestCustom = custom;
			}
		}
		return best;
	}

	static boolean showing(JsonObject event, Instant now, Duration lead, Predicate<String> joined)
	{
		Instant start = eventStart(event);
		if (start == null || "ended".equals(str(event, "status"))) return false;
		Instant end = eventEnd(event);
		boolean soon = !now.isBefore(start.minus(lead)) && now.isBefore(start);
		if ("drop-party".equals(str(event, "type"))) return soon || !now.isBefore(start) && now.isBefore(end != null ? end : start.plus(OPEN_ENDED));
		boolean ending = end != null && !now.isBefore(start) && !now.isBefore(end.minus(END_WARNING)) && now.isBefore(end);
		return (soon || ending) && joined.test(str(event, "id"));
	}

	static Instant target(JsonObject event, Instant now)
	{
		Instant start = eventStart(event);
		return start != null && now.isBefore(start) ? start : eventEnd(event);
	}

	static String phase(JsonObject event, Instant now)
	{
		Instant start = eventStart(event);
		if (start != null && now.isBefore(start)) return "Starts in";
		return eventEnd(event) == null ? "Happening now" : "Ends in";
	}

	static String countdown(Duration left)
	{
		long seconds = Math.max(0, left.getSeconds());
		long hours = seconds / 3600;
		if (hours > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds % 3600 / 60, seconds % 60);
		return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
	}

	static String detail(JsonObject event)
	{
		switch (str(event, "type"))
		{
			case "skill":
			case "boss":
			case "drop-party": return eventDetail(event);
			default: return eventTypeLabel(event);
		}
	}

	static String localTime(Instant at, ZoneId zone, Instant now)
	{
		ZonedDateTime time = at.atZone(zone);
		String day = time.toLocalDate().equals(now.atZone(zone).toLocalDate()) ? "" : time.format(WEEKDAY) + " ";
		return day + TsgHubFormat.clock(time, true) + " " + TsgHubFormat.zoneName(time);
	}
}
