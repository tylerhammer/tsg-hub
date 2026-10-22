package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubJson.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TsgHubEvents
{
	private static final ZoneId CLAN_ZONE = ZoneId.of("Australia/Sydney");

	private TsgHubEvents()
	{
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

	static long prizeTotal(List<Long> prizes)
	{
		long total = 0;
		for (long prize : prizes) total += prize;
		return total;
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

	static JsonObject scoreFor(JsonArray scores, String teamId)
	{
		for (JsonObject score : objects(scores)) if (str(score, "teamId").equals(teamId)) return score;
		JsonObject empty = new JsonObject();
		empty.addProperty("points", 0);
		empty.addProperty("completedTasks", 0);
		empty.add("tasks", new JsonArray());
		return empty;
	}

	static List<JsonObject> rankedTeams(JsonObject event)
	{
		JsonArray scores = array(event, "teamScores");
		List<JsonObject> ranked = objects(array(event, "teams"));
		ranked.sort(Comparator
			.comparingInt((JsonObject team) -> integer(scoreFor(scores, str(team, "id")), "points", 0)).reversed()
			.thenComparing(Comparator.comparingInt((JsonObject team) -> integer(scoreFor(scores, str(team, "id")), "completedTasks", 0)).reversed())
			.thenComparing(team -> str(team, "name"), String.CASE_INSENSITIVE_ORDER));
		return ranked;
	}

	static JsonObject progressFor(JsonArray progressRows, String taskId)
	{
		for (JsonObject progress : objects(progressRows)) if (str(progress, "taskId").equals(taskId)) return progress;
		JsonObject empty = new JsonObject();
		empty.addProperty("progress", 0);
		empty.addProperty("target", 1);
		empty.addProperty("completed", false);
		empty.add("completedBy", JsonNull.INSTANCE);
		return empty;
	}

	static String teamIdFor(JsonObject event, String displayName)
	{
		for (JsonObject member : objects(array(event, "members")))
		{
			if (samePlayer(str(member, "displayName"), displayName) && !str(member, "teamId").isEmpty()) return str(member, "teamId");
		}
		return "";
	}
}
