package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TsgHubRoster
{
	private static final String ALT_RANK = "Gnome Child";
	private static final int MAX_PAST_WARNINGS = 3;
	private static final DateTimeFormatter WARNING_DAY = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH);
	private static final DateTimeFormatter WARNING_DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH);

	private TsgHubRoster()
	{
	}

	static String locationTip(String activity, String area)
	{
		String detail = activityDetail(activity, "");
		boolean active = !"Idle".equals(activity) && !"Online".equals(activity) && !activity.isEmpty();
		String html = tipLine(active ? SUCCESS : MUTED, escape(detail));
		return area.isEmpty() || area.equals(detail) ? html : html + "<br>" + tipLine(MUTED, escape(area));
	}

	static boolean hasActiveWarning(JsonArray warnings)
	{
		return !activeWarnings(warnings).isEmpty();
	}

	static List<JsonObject> activeWarnings(JsonArray warnings)
	{
		List<JsonObject> active = new ArrayList<>();
		for (JsonObject warning : objects(warnings)) if (bool(warning, "active")) active.add(warning);
		return active;
	}

	static String lastSeen(String iso, Instant now)
	{
		Instant then = instant(iso);
		if (then == null) return "";
		long minutes = Math.max(0, java.time.Duration.between(then, now).toMinutes());
		if (minutes < 1) return "just now";
		if (minutes < 60) return minutes + "m ago";
		long hours = minutes / 60;
		if (hours < 24) return hours + "h ago";
		long days = hours / 24;
		if (days < 14) return days + "d ago";
		if (days < 60) return days / 7 + "w ago";
		if (days < 365) return days / 30 + "mo ago";
		return days / 365 + "y ago";
	}

	static String rosterTooltip(String location, JsonArray previousNames, String altOf, JsonArray alts, String note, JsonArray warnings, Instant now, ZoneId zone)
	{
		List<String> names = strings(alts);
		List<String> formerly = strings(previousNames);
		Collections.reverse(formerly);
		List<String> lines = new ArrayList<>();
		if (!altOf.isEmpty()) lines.add(tipLine(ACCENT, "Alt of <b>" + escape(altOf) + "</b>"));
		if (!names.isEmpty()) lines.add(tipLine(ACCENT, (names.size() == 1 ? "Alt: " : "Alts: ") + "<b>" + escape(String.join(", ", names)) + "</b>"));
		String head = String.join("<br>", lines);
		if (!location.isEmpty()) head = head.isEmpty() ? location : location + tipSection(false, head);
		String sections = "";
		if (!formerly.isEmpty())
		{
			sections += tipSection(head.isEmpty(), tipLine(MUTED, "<b>PREVIOUS NAMES</b>"))
				+ tipCard(CARD_HOVER, escape(String.join("\n", formerly)).replace("\n", "<br>"));
		}
		if (!note.isEmpty())
		{
			sections += tipSection(head.isEmpty() && sections.isEmpty(), tipLine(WARNING, "<b>ADMIN NOTE</b>"))
				+ tipCard(CARD_HOVER, escape(note).replace("\n", "<br>"));
		}
		List<JsonObject> history = objects(warnings);
		if (!history.isEmpty())
		{
			sections += tipSection(head.isEmpty() && sections.isEmpty(), tipLine(TsgHubTheme.ERROR, "<b>WARNINGS</b>") + " " + tipLine(MUTED, warningSummary(history)))
				+ warningCards(history, now, zone);
		}
		return head + sections;
	}

	static String warningSummary(List<JsonObject> warnings)
	{
		int active = 0, expired = 0, revoked = 0;
		for (JsonObject warning : warnings)
		{
			if (bool(warning, "active")) active++;
			else if (!str(warning, "revokedAt").isEmpty()) revoked++;
			else expired++;
		}
		List<String> parts = new ArrayList<>();
		parts.add(active + " active");
		if (expired > 0) parts.add(expired + " expired");
		if (revoked > 0) parts.add(revoked + " revoked");
		return String.join(" · ", parts);
	}

	static String warningCards(List<JsonObject> warnings, Instant now, ZoneId zone)
	{
		List<JsonObject> ordered = new ArrayList<>();
		List<JsonObject> past = new ArrayList<>();
		for (JsonObject warning : warnings) (bool(warning, "active") ? ordered : past).add(warning);
		int inactive = past.size();
		ordered.addAll(past.subList(0, Math.min(inactive, MAX_PAST_WARNINGS)));
		StringBuilder out = new StringBuilder();
		for (JsonObject warning : ordered)
		{
			boolean active = bool(warning, "active");
			String meta = warningDate(str(warning, "issuedAt"), now, zone) + " · " + escape(str(warning, "issuedBy")) + " · " + warningStatus(warning, now, zone);
			Color text = active ? TEXT : DIM;
			out.append(tipCard(active ? TsgHubTheme.ERROR : BORDER, tipLine(text, escape(str(warning, "reason"))) + "<br>" + tipLine(active ? MUTED : DIM, meta)));
		}
		if (inactive > MAX_PAST_WARNINGS) out.append(tipLine(DIM, "+" + (inactive - MAX_PAST_WARNINGS) + " older"));
		return out.toString();
	}

	static String warningStatus(JsonObject warning, Instant now, ZoneId zone)
	{
		if (!str(warning, "revokedAt").isEmpty()) return "revoked by " + escape(str(warning, "revokedBy"));
		String expires = str(warning, "expiresAt");
		if (expires.isEmpty()) return "no expiry";
		return bool(warning, "active") ? "expires " + warningDate(expires, now, zone) : "expired";
	}

	static String warningDate(String iso, Instant now, ZoneId zone)
	{
		Instant at = instant(iso);
		if (at == null) return "";
		LocalDate day = at.atZone(zone).toLocalDate();
		return day.format(day.getYear() == now.atZone(zone).getYear() ? WARNING_DAY : WARNING_DAY_YEAR);
	}

	static boolean isAltRank(String rank)
	{
		return ALT_RANK.equalsIgnoreCase(rank.trim());
	}

	static String activityDetail(String activity, String area)
	{
		if (activity.isEmpty()) activity = "Online";
		int dash = activity.indexOf(" - ");
		String detail = dash < 0 ? activity : activity.startsWith("Slayer - ") ? "Slayer: " + activity.substring(dash + 3) : activity.substring(dash + 3);
		if (area.isEmpty() || area.equals(detail)) return detail;
		return detail + " · " + area;
	}
}
