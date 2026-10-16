package com.tsghub;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TsgHubFormat
{
	private static final DateTimeFormatter DATE_WITH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US);
	private static final DateTimeFormatter DAY_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US);
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("h", Locale.US);
	private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("h:mm", Locale.US);
	private static final DateTimeFormatter ZONE = DateTimeFormatter.ofPattern("zzz", Locale.US);
	static Clock clock = Clock.systemUTC();

	private TsgHubFormat()
	{
	}

	static String friendlyError(Throwable error)
	{
		if (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
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

	static String plural(int count, String noun)
	{
		return count + " " + noun + (count == 1 ? "" : "s");
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

	static String day(ZonedDateTime time, Instant now)
	{
		return time.format(time.getYear() == now.atZone(time.getZone()).getYear() ? DAY : DAY_WITH_YEAR);
	}

	static String when(ZonedDateTime time, Instant now)
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
	static String skillName(String key)
	{
		if (key == null || key.isEmpty()) return "";
		String lower = key.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
