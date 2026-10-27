package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EventsPanelTest
{
	private Clock original;

	@Before
	public void fixClock()
	{
		original = TsgHubFormat.clock;
		TsgHubFormat.clock = Clock.fixed(Instant.parse("2026-10-04T02:00:00Z"), ZoneOffset.UTC);
	}

	@After
	public void restoreClock()
	{
		TsgHubFormat.clock = original;
	}

	private static JsonObject event(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	@Test
	public void eventCountdown()
	{
		assertEquals("7d 6h left", TsgHubEvents.eventCountdown(event("{\"status\":\"active\",\"startsAt\":\"2026-09-20T09:00:00Z\",\"endsAt\":\"2026-10-11T08:00:00Z\"}")));
		assertEquals("in 6h", TsgHubEvents.eventCountdown(event("{\"status\":\"scheduled\",\"startsAt\":\"2026-10-04T08:00:00Z\",\"endsAt\":\"2026-10-04T10:00:00Z\"}")));
		assertEquals("TBD", TsgHubEvents.eventCountdown(event("{\"status\":\"scheduled\"}")));
	}

	@Test
	public void eventDetail()
	{
		assertEquals("Mining", TsgHubEvents.eventDetail(event("{\"type\":\"skill\",\"config\":{\"skill\":\"MINING\"}}")));
		assertEquals("Vardorvis", TsgHubEvents.eventDetail(event("{\"type\":\"boss\",\"config\":{\"npcName\":\"Vardorvis\"}}")));
		assertEquals("W420 · Falador Party Room", TsgHubEvents.eventDetail(event("{\"type\":\"drop-party\",\"config\":{\"world\":420,\"location\":\"Falador Party Room\"}}")));
	}

	@Test
	public void prizes()
	{
		JsonObject event = event("{\"prizes\":[20000000,10000000,5000000]}");
		assertEquals("1st 20M · 2nd 10M · 3rd 5M", TsgHubEvents.prizeSummary(TsgHubEvents.eventPrizes(event)));
		assertEquals(35_000_000, TsgHubEvents.prizeTotal(TsgHubEvents.eventPrizes(event)));
		assertEquals(0, TsgHubEvents.eventPrizes(event("{}")).size());
		assertEquals("11th", TsgHubFormat.place(11));
		assertEquals("22nd", TsgHubFormat.place(22));
	}

	@Test
	public void signupsOpen()
	{
		assertTrue(TsgHubEvents.signupsOpen(event("{\"type\":\"bingo\",\"signups\":{\"enabled\":true,\"open\":true,\"count\":0}}")));
		assertTrue(TsgHubEvents.signupsOpen(event("{\"type\":\"bingo\",\"signups\":{\"open\":true,\"closesAt\":\"2026-10-04T03:00:00Z\"}}")));
		assertFalse(TsgHubEvents.signupsOpen(event("{\"type\":\"bingo\",\"signups\":{\"open\":true,\"closesAt\":\"2026-10-04T01:00:00Z\"}}")));
		assertFalse(TsgHubEvents.signupsOpen(event("{\"type\":\"bingo\",\"signups\":{\"enabled\":true,\"open\":false}}")));
		assertFalse(TsgHubEvents.signupsOpen(event("{\"type\":\"bingo\"}")));
		assertFalse(TsgHubEvents.signupsOpen(event("{\"type\":\"skill\",\"signups\":{\"open\":true}}")));
	}

	@Test
	public void signupSummary()
	{
		ZoneId sydney = ZoneId.of("Australia/Sydney");
		assertEquals("No one waiting yet", TsgHubEvents.signupSummary(event("{\"signups\":{\"open\":true,\"count\":0}}"), sydney));
		assertEquals("3 waiting for a team · Closes Mon 5 Oct, 7pm AEDT", TsgHubEvents.signupSummary(event("{\"signups\":{\"open\":true,\"count\":3,\"closesAt\":\"2026-10-05T08:00:00Z\"}}"), sydney));
		assertEquals("1 waiting for a team · Closed Sun 4 Oct, 12pm AEDT", TsgHubEvents.signupSummary(event("{\"signups\":{\"open\":false,\"count\":1,\"closesAt\":\"2026-10-04T01:00:00Z\"}}"), sydney));
	}

	@Test
	public void awaitingTeam()
	{
		JsonObject signedUp = event("{\"members\":[{\"displayName\":\"Zezima\",\"teamId\":\"\"}]}");
		assertTrue(TsgHubEvents.awaitingTeam(signedUp, "zezima"));
		assertFalse(TsgHubEvents.awaitingTeam(event("{\"members\":[{\"displayName\":\"Zezima\",\"teamId\":\"t1\"}]}"), "Zezima"));
		assertFalse(TsgHubEvents.awaitingTeam(signedUp, "Lynx Titan"));
		assertFalse(TsgHubEvents.awaitingTeam(event("{}"), "Zezima"));
	}

	@Test
	public void attended()
	{
		JsonObject party = event("{\"type\":\"drop-party\",\"attended\":[\"Lynx Titan\",\"Zezima\"]}");
		assertTrue(TsgHubEvents.attended(party, "zezima"));
		assertFalse(TsgHubEvents.attended(party, "Woox"));
		assertFalse(TsgHubEvents.attended(event("{\"type\":\"drop-party\"}"), "Zezima"));
	}
}
