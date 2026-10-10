package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DropsPanelTest
{
	@Test
	public void formatGp()
	{
		assertEquals("", TsgHubUi.formatGp(0));
		assertEquals("950 gp", TsgHubUi.formatGp(950));
		assertEquals("7.2K", TsgHubUi.formatGp(7_250));
		assertEquals("1.5M", TsgHubUi.formatGp(1_512_345));
		assertEquals("12M", TsgHubUi.formatGp(12_000_000));
		assertEquals("1.2B", TsgHubUi.formatGp(1_234_567_890L));
	}

	@Test
	public void dropItem()
	{
		assertEquals("Abyssal whip", TsgHubDrops.dropItem(drop("Abyssal whip", 1)));
		assertEquals("3 x Dragon bones", TsgHubDrops.dropItem(drop("Dragon bones", 3)));
		assertEquals("Tumeken's shadow", TsgHubDrops.dropItem(drop("Tumeken's shadow (uncharged)", 1)));
		assertEquals("3 x Onyx bolts", TsgHubDrops.dropItem(drop("Onyx bolts (e)", 3)));
	}

	@Test
	public void dropTags()
	{
		JsonObject raid = drop("Twisted bow", 1);
		raid.addProperty("kind", "raid");
		assertEquals(Arrays.asList("CoX"), TsgHubDrops.dropTags(raid));
		raid.addProperty("newLog", true);
		assertEquals(Arrays.asList("CoX", "Log"), TsgHubDrops.dropTags(raid));
		JsonObject clog = drop("Dragon warhammer", 1);
		clog.addProperty("kind", "clog");
		assertEquals(Arrays.asList("Log"), TsgHubDrops.dropTags(clog));
		JsonObject dupe = drop("Vorki", 1);
		dupe.addProperty("kind", "dupe");
		assertEquals(Arrays.asList("Dupe pet"), TsgHubDrops.dropTags(dupe));
		JsonObject plain = drop("Abyssal whip", 1);
		plain.addProperty("kind", "drop");
		assertEquals(Collections.emptyList(), TsgHubDrops.dropTags(plain));
	}

	@Test
	public void raidName()
	{
		assertEquals("ToB", TsgHubDrops.raidName("Scythe of vitur (uncharged)"));
		assertEquals("ToA", TsgHubDrops.raidName("Elidinis' ward"));
		assertEquals("CoX", TsgHubDrops.raidName("Dexterous prayer scroll"));
		assertEquals("Raid", TsgHubDrops.raidName("Mystery box"));
	}

	@Test
	public void dropDay()
	{
		ZoneId zone = ZoneId.of("America/New_York");
		Instant now = Instant.parse("2026-09-28T16:00:00Z");
		assertEquals("Today", TsgHubDrops.dropDay("2026-09-28T05:00:00Z", now, zone));
		assertEquals("Yesterday", TsgHubDrops.dropDay("2026-09-28T03:00:00Z", now, zone));
		assertEquals("26 Sep 2026", TsgHubDrops.dropDay("2026-09-26T12:00:00Z", now, zone));
		assertEquals("Earlier", TsgHubDrops.dropDay("", now, zone));
	}

	@Test
	public void dropWhen()
	{
		ZoneId zone = ZoneId.of("America/New_York");
		Instant now = Instant.parse("2026-09-28T16:00:00Z");
		assertEquals("11h", TsgHubDrops.dropWhen("2026-09-28T05:00:00Z", now, zone));
		assertEquals("23:00", TsgHubDrops.dropWhen("2026-09-28T03:00:00Z", now, zone));
		assertEquals("", TsgHubDrops.dropWhen("", now, zone));
	}

	@Test
	public void coinColor()
	{
		assertEquals(new Color(255, 255, 0), TsgHubUi.coinColor(99_999));
		assertEquals(TsgHubTheme.TEXT, TsgHubUi.coinColor(803_300));
		assertEquals(new Color(0, 255, 128), TsgHubUi.coinColor(10_000_000));
	}

	@Test
	public void broadcastFilter()
	{
		assertEquals("Iron Alice received a drop: Abyssal whip (1,512,345 coins).",
			TsgHubDrops.broadcast("<img=3>Iron Alice received a drop: <col=ef1020>Abyssal whip</col> (1,512,345 coins)."));
		assertEquals("Bob received special loot from a raid: Twisted bow.", TsgHubDrops.broadcast("Bob received special loot from a raid: Twisted bow."));
		assertEquals("itsHammerKR has a funny feeling like he would have been followed: Vorki at 912 killcount from Vorkath.",
			TsgHubDrops.broadcast("itsHammerKR has a funny feeling like he would have been followed: Vorki at 912 killcount from Vorkath."));
		assertNull(TsgHubDrops.broadcast("Bob has reached combat level 100."));
		assertNull(TsgHubDrops.broadcast(null));
	}

	private static JsonObject drop(String item, int quantity)
	{
		JsonObject drop = new JsonObject();
		drop.addProperty("item", item);
		drop.addProperty("quantity", quantity);
		return drop;
	}
}
