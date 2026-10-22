package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
		assertEquals("Mon 5 Oct, 4-6:30pm AWST", TsgHubFormat.timeRange(start, end, PERTH, NOW));
		assertEquals("Mon 5 Oct, 6:30-9pm ACDT", TsgHubFormat.timeRange(start, end, ADELAIDE, NOW));
		assertEquals("Mon 5 Oct, 6-8:30pm AEST", TsgHubFormat.timeRange(start, end, BRISBANE, NOW));
		assertEquals("Mon 5 Oct, 7-9:30pm AEDT", TsgHubFormat.timeRange(start, end, SYDNEY, NOW));
	}

	@Test
	public void rangeAcrossDaylightSavingShowsBothZones()
	{
		Instant start = Instant.parse("2026-09-20T09:00:00Z");
		Instant end = Instant.parse("2026-10-11T08:00:00Z");
		assertEquals("Sun 20 Sep, 7pm AEST to Sun 11 Oct, 7pm AEDT", TsgHubFormat.timeRange(start, end, SYDNEY, NOW));
		assertEquals("Sun 20 Sep, 7pm to Sun 11 Oct, 6pm AEST", TsgHubFormat.timeRange(start, end, BRISBANE, NOW));
	}

	@Test
	public void openEndedAndOtherYears()
	{
		Instant start = Instant.parse("2027-01-02T09:00:00Z");
		assertEquals("Sat 2 Jan 2027, 8pm AEDT", TsgHubFormat.timeRange(start, null, SYDNEY, NOW));
		assertEquals("Time TBD", TsgHubFormat.timeRange(null, null, SYDNEY, NOW));
		assertEquals("Mon 5 Oct, 11:30am-1pm AEDT", TsgHubFormat.timeRange(Instant.parse("2026-10-05T00:30:00Z"), Instant.parse("2026-10-05T02:00:00Z"), SYDNEY, NOW));
	}

	@Test
	public void relativeHints()
	{
		Instant start = NOW.plus(Duration.ofHours(3));
		Instant end = start.plus(Duration.ofDays(2)).plus(Duration.ofHours(4));
		assertEquals("starts in 3h", TsgHubFormat.relativeTime(start, end, NOW));
		assertEquals("starts in 2h 30m", TsgHubFormat.relativeTime(NOW.plus(Duration.ofMinutes(150)), end, NOW));
		assertEquals("starts in 1m", TsgHubFormat.relativeTime(NOW.plusSeconds(10), end, NOW));
		assertEquals("ends in 2d 4h", TsgHubFormat.relativeTime(start, end, start));
		assertEquals("ends in 3d", TsgHubFormat.relativeTime(start, start.plus(Duration.ofDays(3)), start));
		assertEquals("ended", TsgHubFormat.relativeTime(start, end, end));
		assertEquals("happening now", TsgHubFormat.relativeTime(start, null, start));
	}

	@Test
	public void eventInstants()
	{
		JsonObject event = new JsonObject();
		event.addProperty("type", "bingo");
		event.addProperty("startsAt", "2026-10-05T08:00:00Z");
		event.addProperty("endsAt", "2026-10-12T08:00:00Z");
		event.addProperty("startDate", "2000-01-01");
		assertEquals(Instant.parse("2026-10-05T08:00:00Z"), TsgHubEvents.eventStart(event));
		assertEquals(Instant.parse("2026-10-12T08:00:00Z"), TsgHubEvents.eventEnd(event));
	}

	@Test
	public void legacyDatesUseTheEventZone()
	{
		JsonObject event = new JsonObject();
		event.addProperty("type", "skill");
		event.addProperty("startDate", "2026-10-03");
		event.addProperty("endDate", "2026-10-04");
		assertEquals(Instant.parse("2026-10-02T14:00:00Z"), TsgHubEvents.eventStart(event));
		assertEquals(Instant.parse("2026-10-04T13:00:00Z"), TsgHubEvents.eventEnd(event));
		event.addProperty("timeZone", "Australia/Perth");
		assertEquals(Instant.parse("2026-10-02T16:00:00Z"), TsgHubEvents.eventStart(event));
		assertEquals(Instant.parse("2026-10-04T16:00:00Z"), TsgHubEvents.eventEnd(event));
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
		assertEquals(Instant.parse("2026-10-04T08:00:00Z"), TsgHubEvents.eventStart(event));
		assertNull(TsgHubEvents.eventEnd(event));
		event.addProperty("status", "ended");
		assertEquals("ended", TsgHubEvents.eventRelative(event));
	}
}
