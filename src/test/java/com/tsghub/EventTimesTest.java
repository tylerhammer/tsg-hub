package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

public class EventTimesTest
{
	private static final ZoneId PERTH = ZoneId.of("Australia/Perth");
	private static final ZoneId ADELAIDE = ZoneId.of("Australia/Adelaide");
	private static final ZoneId BRISBANE = ZoneId.of("Australia/Brisbane");
	private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
	private static final Instant NOW = Instant.parse("2026-10-04T02:00:00Z");

	@Test
	public void sameInstantInEachZone()
	{
		Instant start = Instant.parse("2026-10-05T08:00:00Z");
		Instant end = Instant.parse("2026-10-05T10:30:00Z");
		assertEquals("Mon Oct 5, 4:00 PM to 6:30 PM AWST", TsgHubUi.timeRange(start, end, PERTH, NOW));
		assertEquals("Mon Oct 5, 6:30 PM to 9:00 PM ACDT", TsgHubUi.timeRange(start, end, ADELAIDE, NOW));
		assertEquals("Mon Oct 5, 6:00 PM to 8:30 PM AEST", TsgHubUi.timeRange(start, end, BRISBANE, NOW));
		assertEquals("Mon Oct 5, 7:00 PM to 9:30 PM AEDT", TsgHubUi.timeRange(start, end, SYDNEY, NOW));
	}

	@Test
	public void rangeAcrossDaylightSavingShowsBothZones()
	{
		Instant start = Instant.parse("2026-09-20T09:00:00Z");
		Instant end = Instant.parse("2026-10-11T08:00:00Z");
		assertEquals("Sun Sep 20, 7:00 PM AEST to Sun Oct 11, 7:00 PM AEDT", TsgHubUi.timeRange(start, end, SYDNEY, NOW));
		assertEquals("Sun Sep 20, 7:00 PM to Sun Oct 11, 6:00 PM AEST", TsgHubUi.timeRange(start, end, BRISBANE, NOW));
	}

	@Test
	public void openEndedAndOtherYears()
	{
		Instant start = Instant.parse("2027-01-02T09:00:00Z");
		assertEquals("Sat Jan 2 2027, 8:00 PM AEDT", TsgHubUi.timeRange(start, null, SYDNEY, NOW));
		assertEquals("Time TBD", TsgHubUi.timeRange(null, null, SYDNEY, NOW));
	}

	@Test
	public void relativeHints()
	{
		Instant start = NOW.plus(Duration.ofHours(3));
		Instant end = start.plus(Duration.ofDays(2)).plus(Duration.ofHours(4));
		assertEquals("starts in 3h", TsgHubUi.relativeTime(start, end, NOW));
		assertEquals("starts in 2h 30m", TsgHubUi.relativeTime(NOW.plus(Duration.ofMinutes(150)), end, NOW));
		assertEquals("starts in 1m", TsgHubUi.relativeTime(NOW.plusSeconds(10), end, NOW));
		assertEquals("ends in 2d 4h", TsgHubUi.relativeTime(start, end, start));
		assertEquals("ends in 3d", TsgHubUi.relativeTime(start, start.plus(Duration.ofDays(3)), start));
		assertEquals("ended", TsgHubUi.relativeTime(start, end, end));
		assertEquals("happening now", TsgHubUi.relativeTime(start, null, start));
	}

	@Test
	public void zoneLabel()
	{
		assertEquals("AEDT (Australia/Sydney)", TsgHubUi.zoneLabel(SYDNEY, NOW));
		assertEquals("AWST (Australia/Perth)", TsgHubUi.zoneLabel(PERTH, NOW));
		assertEquals("UTC", TsgHubUi.zoneLabel(ZoneId.of("UTC"), NOW));
	}

	@Test
	public void eventInstants()
	{
		JsonObject event = new JsonObject();
		event.addProperty("type", "bingo");
		event.addProperty("startsAt", "2026-10-05T08:00:00Z");
		event.addProperty("endsAt", "2026-10-12T08:00:00Z");
		event.addProperty("startDate", "2000-01-01");
		assertEquals(Instant.parse("2026-10-05T08:00:00Z"), TsgHubUi.eventStart(event));
		assertEquals(Instant.parse("2026-10-12T08:00:00Z"), TsgHubUi.eventEnd(event));
		assertFalse(TsgHubUi.running(event, Instant.parse("2026-10-05T07:59:59Z")));
		assertTrue(TsgHubUi.running(event, Instant.parse("2026-10-05T08:00:00Z")));
		assertFalse(TsgHubUi.running(event, Instant.parse("2026-10-12T08:00:00Z")));
	}

	@Test
	public void legacyDatesUseTheEventZone()
	{
		JsonObject event = new JsonObject();
		event.addProperty("type", "skill");
		event.addProperty("startDate", "2026-10-03");
		event.addProperty("endDate", "2026-10-04");
		assertEquals(Instant.parse("2026-10-02T14:00:00Z"), TsgHubUi.eventStart(event));
		assertEquals(Instant.parse("2026-10-04T13:00:00Z"), TsgHubUi.eventEnd(event));
		event.addProperty("timeZone", "Australia/Perth");
		assertEquals(Instant.parse("2026-10-02T16:00:00Z"), TsgHubUi.eventStart(event));
		assertEquals(Instant.parse("2026-10-04T16:00:00Z"), TsgHubUi.eventEnd(event));
	}

	@Test
	public void customEvents()
	{
		JsonObject config = new JsonObject();
		config.addProperty("startsAt", "2026-10-04T08:00:00Z");
		JsonObject event = new JsonObject();
		event.addProperty("type", "drop-party");
		event.addProperty("endDate", "2026-10-04");
		event.addProperty("status", "active");
		event.add("config", config);
		assertEquals(Instant.parse("2026-10-04T08:00:00Z"), TsgHubUi.eventStart(event));
		assertNull(TsgHubUi.eventEnd(event));
		assertTrue(TsgHubUi.running(event, NOW));
		event.addProperty("status", "ended");
		assertEquals("ended", TsgHubUi.eventRelative(event));
	}
}
