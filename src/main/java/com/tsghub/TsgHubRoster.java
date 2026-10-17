package com.tsghub;

import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TsgHubRoster
{
	private static final String ALT_RANK = "Gnome Child";
	private static final int MAX_PAST_WARNINGS = 3;

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
		return then == null ? "" : TsgHubFormat.ago(then, now);
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
		int active = activeWarningCount(warnings), expired = warnings.size() - active;
		return active + " active" + (expired > 0 ? " · " + expired + " expired" : "");
	}

	static String warningCards(List<JsonObject> warnings, Instant now, ZoneId zone)
	{
		List<JsonObject> all = activeFirst(warnings);
		int current = activeWarningCount(warnings);
		int inactive = all.size() - current;
		List<JsonObject> ordered = all.subList(0, current + Math.min(inactive, MAX_PAST_WARNINGS));
		StringBuilder out = new StringBuilder();
		for (JsonObject warning : ordered)
		{
			boolean active = bool(warning, "active");
			String meta = warningMeta(warning, now, zone);
			Color text = active ? TEXT : DIM;
			out.append(tipCard(active ? TsgHubTheme.ERROR : BORDER, tipLine(text, escape(str(warning, "reason"))) + "<br>" + tipLine(active ? MUTED : DIM, meta)));
		}
		if (inactive > MAX_PAST_WARNINGS) out.append(tipLine(DIM, "+" + (inactive - MAX_PAST_WARNINGS) + " older"));
		return out.toString();
	}

	static List<JsonObject> activeFirst(List<JsonObject> warnings)
	{
		List<JsonObject> ordered = new ArrayList<>();
		List<JsonObject> past = new ArrayList<>();
		for (JsonObject warning : warnings) (bool(warning, "active") ? ordered : past).add(warning);
		ordered.addAll(past);
		return ordered;
	}

	private static int activeWarningCount(List<JsonObject> warnings)
	{
		int count = 0;
		for (JsonObject warning : warnings) if (bool(warning, "active")) count++;
		return count;
	}

	static String warningMeta(JsonObject warning, Instant now, ZoneId zone)
	{
		return TsgHubFormat.shortDate(str(warning, "issuedAt"), now, zone) + " · " + escape(str(warning, "issuedBy")) + " · " + warningStatus(warning, now, zone);
	}

	static String warningStatus(JsonObject warning, Instant now, ZoneId zone)
	{
		if (!bool(warning, "active")) return "expired";
		String expires = str(warning, "expiresAt");
		return expires.isEmpty() ? "no expiry" : "expires " + TsgHubFormat.shortDate(expires, now, zone);
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
