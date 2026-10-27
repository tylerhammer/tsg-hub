package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;

public class CountdownTest
{
	private static final Instant NOW = Instant.parse("2026-10-04T08:00:00Z");
	private static final Duration LEAD = Duration.ofMinutes(30);
	private static final Predicate<String> NONE = id -> false;
	private static final Predicate<String> ALL = id -> true;

	private static JsonObject event(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	private static JsonObject custom(String id, String startsAt)
	{
		return event("{\"id\":\"" + id + "\",\"type\":\"drop-party\",\"status\":\"scheduled\",\"name\":\"Drop party\",\"startsAt\":\"" + startsAt + "\",\"config\":{\"world\":420,\"location\":\"Falador Party Room\"}}");
	}

	private static JsonObject competition(String id, String startsAt, String endsAt)
	{
		return event("{\"id\":\"" + id + "\",\"type\":\"skill\",\"status\":\"active\",\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\",\"config\":{\"skill\":\"MINING\"}}");
	}

	private static JsonArray list(JsonObject... events)
	{
		JsonArray array = new JsonArray();
		for (JsonObject event : events) array.add(event);
		return array;
	}

	@Test
	public void customEventsShowFromLeadTimeUntilTheyEnd()
	{
		JsonObject party = custom("a", "2026-10-04T08:20:00Z");
		assertTrue(TsgHubCountdown.showing(party, NOW, LEAD, NONE));
		assertFalse(TsgHubCountdown.showing(party, NOW, Duration.ofMinutes(10), NONE));
		assertTrue(TsgHubCountdown.showing(party, Instant.parse("2026-10-04T08:49:00Z"), LEAD, NONE));
		assertFalse(TsgHubCountdown.showing(party, Instant.parse("2026-10-04T08:50:00Z"), LEAD, NONE));
		party.addProperty("endsAt", "2026-10-04T10:00:00Z");
		assertTrue(TsgHubCountdown.showing(party, Instant.parse("2026-10-04T09:30:00Z"), LEAD, NONE));
		party.addProperty("status", "ended");
		assertFalse(TsgHubCountdown.showing(party, NOW, LEAD, NONE));
	}

	@Test
	public void joinedCompetitionsShowNearStartAndLastDay()
	{
		JsonObject week = competition("c", "2026-09-28T08:00:00Z", "2026-10-05T07:00:00Z");
		assertFalse(TsgHubCountdown.showing(week, NOW, LEAD, NONE));
		assertTrue(TsgHubCountdown.showing(week, NOW, LEAD, ALL));
		assertFalse(TsgHubCountdown.showing(week, Instant.parse("2026-10-04T06:59:00Z"), LEAD, ALL));
		JsonObject next = competition("d", "2026-10-04T08:15:00Z", "2026-10-11T08:00:00Z");
		assertTrue(TsgHubCountdown.showing(next, NOW, LEAD, ALL));
		assertFalse(TsgHubCountdown.showing(next, Instant.parse("2026-10-05T08:15:00Z"), LEAD, ALL));
	}

	@Test
	public void pickPrefersCustomEventsThenSoonest()
	{
		JsonObject later = custom("a", "2026-10-04T08:25:00Z");
		JsonObject sooner = custom("b", "2026-10-04T08:05:00Z");
		JsonObject week = competition("c", "2026-09-28T08:00:00Z", "2026-10-04T09:00:00Z");
		Set<String> dismissed = new HashSet<>();
		assertEquals(sooner, TsgHubCountdown.pick(list(later, week, sooner), NOW, LEAD, ALL, dismissed));
		dismissed.add("b");
		assertEquals(later, TsgHubCountdown.pick(list(later, week, sooner), NOW, LEAD, ALL, dismissed));
		dismissed.add("a");
		assertEquals(week, TsgHubCountdown.pick(list(later, week, sooner), NOW, LEAD, ALL, dismissed));
		assertNull(TsgHubCountdown.pick(list(later, week, sooner), NOW, LEAD, NONE, dismissed));
		assertNull(TsgHubCountdown.pick(null, NOW, LEAD, ALL, Collections.emptySet()));
	}

	@Test
	public void text()
	{
		assertEquals("04:05", TsgHubCountdown.countdown(Duration.ofSeconds(245)));
		assertEquals("23:59:59", TsgHubCountdown.countdown(Duration.ofHours(24).minusSeconds(1)));
		assertEquals("00:00", TsgHubCountdown.countdown(Duration.ofSeconds(-5)));
		JsonObject party = custom("a", "2026-10-04T08:20:00Z");
		assertEquals("Starts in", TsgHubCountdown.phase(party, NOW));
		assertEquals("Happening now", TsgHubCountdown.phase(party, Instant.parse("2026-10-04T08:21:00Z")));
		assertEquals("Ends in", TsgHubCountdown.phase(competition("c", "2026-09-28T08:00:00Z", "2026-10-05T07:00:00Z"), NOW));
		assertEquals("W420 · Falador Party Room", TsgHubCountdown.detail(party));
		assertEquals("Mining", TsgHubCountdown.detail(competition("c", "2026-09-28T08:00:00Z", "2026-10-05T07:00:00Z")));
		assertEquals("Bingo", TsgHubCountdown.detail(event("{\"type\":\"bingo\"}")));
		ZoneId sydney = ZoneId.of("Australia/Sydney");
		assertEquals("7:20pm AEDT", TsgHubCountdown.localTime(Instant.parse("2026-10-04T08:20:00Z"), sydney, NOW));
		assertEquals("Mon 6pm AEDT", TsgHubCountdown.localTime(Instant.parse("2026-10-05T07:00:00Z"), sydney, NOW));
	}
}
