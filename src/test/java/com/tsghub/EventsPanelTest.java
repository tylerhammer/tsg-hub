package com.tsghub;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.Instant;
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
		original = TsgHubUi.clock;
		TsgHubUi.clock = Clock.fixed(Instant.parse("2026-10-04T02:00:00Z"), ZoneOffset.UTC);
	}

	@After
	public void restoreClock()
	{
		TsgHubUi.clock = original;
	}

	private static JsonObject event(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	@Test
	public void eventCountdown()
	{
		assertEquals("7d 6h left", TsgHubUi.eventCountdown(event("{\"status\":\"active\",\"startsAt\":\"2026-09-20T09:00:00Z\",\"endsAt\":\"2026-10-11T08:00:00Z\"}")));
		assertEquals("in 6h", TsgHubUi.eventCountdown(event("{\"status\":\"scheduled\",\"startsAt\":\"2026-10-04T08:00:00Z\",\"endsAt\":\"2026-10-04T10:00:00Z\"}")));
		assertEquals("TBD", TsgHubUi.eventCountdown(event("{\"status\":\"scheduled\"}")));
	}

	@Test
	public void eventDetail()
	{
		assertEquals("Mining", TsgHubUi.eventDetail(event("{\"type\":\"skill\",\"config\":{\"skill\":\"MINING\"}}")));
		assertEquals("Vardorvis", TsgHubUi.eventDetail(event("{\"type\":\"boss\",\"config\":{\"npcName\":\"Vardorvis\"}}")));
		assertEquals("W420 · Falador Party Room", TsgHubUi.eventDetail(event("{\"type\":\"drop-party\",\"config\":{\"world\":420,\"location\":\"Falador Party Room\"}}")));
	}

	@Test
	public void parseMillions()
	{
		assertEquals(0, TsgHubUi.parseMillions(""));
		assertEquals(20_000_000, TsgHubUi.parseMillions("20"));
		assertEquals(1_500_000_000, TsgHubUi.parseMillions("1,500"));
		assertEquals(500_000, TsgHubUi.parseMillions("0.5"));
		assertEquals(-1, TsgHubUi.parseMillions("20m"));
		assertEquals(-1, TsgHubUi.parseMillions("-5"));
		assertEquals(-1, TsgHubUi.parseMillions("0.0000001"));
		assertEquals("2.5", TsgHubUi.millions(2_500_000));
		assertEquals("1500", TsgHubUi.millions(1_500_000_000));
	}

	@Test
	public void prizes()
	{
		JsonObject event = event("{\"prizes\":[20000000,10000000,5000000]}");
		assertEquals("1st 20M · 2nd 10M · 3rd 5M", TsgHubUi.prizeSummary(TsgHubUi.eventPrizes(event)));
		assertEquals(35_000_000, TsgHubUi.prizeTotal(TsgHubUi.eventPrizes(event)));
		assertEquals(0, TsgHubUi.eventPrizes(event("{}")).size());
		assertEquals("11th", TsgHubUi.place(11));
		assertEquals("22nd", TsgHubUi.place(22));
	}
}
