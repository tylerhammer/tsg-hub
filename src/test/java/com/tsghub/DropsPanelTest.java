package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class DropsPanelTest
{
	@Test
	public void formatGp()
	{
		assertEquals("", TsgHubSidebarPanel.formatGp(0));
		assertEquals("950 gp", TsgHubSidebarPanel.formatGp(950));
		assertEquals("7.2K", TsgHubSidebarPanel.formatGp(7_250));
		assertEquals("1.5M", TsgHubSidebarPanel.formatGp(1_512_345));
		assertEquals("12M", TsgHubSidebarPanel.formatGp(12_000_000));
		assertEquals("1.2B", TsgHubSidebarPanel.formatGp(1_234_567_890L));
	}

	@Test
	public void dropItem()
	{
		assertEquals("Abyssal whip", TsgHubSidebarPanel.dropItem(drop("Abyssal whip", 1)));
		assertEquals("3 x Dragon bones", TsgHubSidebarPanel.dropItem(drop("Dragon bones", 3)));
		assertEquals("Tumeken's shadow", TsgHubSidebarPanel.dropItem(drop("Tumeken's shadow (uncharged)", 1)));
		assertEquals("3 x Onyx bolts", TsgHubSidebarPanel.dropItem(drop("Onyx bolts (e)", 3)));
	}

	@Test
	public void dropBadges()
	{
		JsonObject raid = drop("Twisted bow", 1);
		raid.addProperty("kind", "raid");
		assertEquals(Arrays.asList("Raid"), TsgHubSidebarPanel.dropBadges(raid));
		raid.addProperty("newLog", true);
		assertEquals(Arrays.asList("Raid", "New log"), TsgHubSidebarPanel.dropBadges(raid));
		JsonObject clog = drop("Dragon warhammer", 1);
		clog.addProperty("kind", "clog");
		assertEquals(Arrays.asList("New log"), TsgHubSidebarPanel.dropBadges(clog));
		JsonObject dupe = drop("Vorki", 1);
		dupe.addProperty("kind", "dupe");
		assertEquals(Arrays.asList("Dupe pet"), TsgHubSidebarPanel.dropBadges(dupe));
		JsonObject plain = drop("Abyssal whip", 1);
		plain.addProperty("kind", "drop");
		assertEquals(Collections.emptyList(), TsgHubSidebarPanel.dropBadges(plain));
	}

	@Test
	public void dropAge()
	{
		Instant now = Instant.parse("2026-09-28T12:00:00Z");
		assertEquals("just now", TsgHubSidebarPanel.dropAge("2026-09-28T11:59:30Z", now));
		assertEquals("5m ago", TsgHubSidebarPanel.dropAge("2026-09-28T11:54:59.500Z", now));
		assertEquals("3h ago", TsgHubSidebarPanel.dropAge("2026-09-28T09:00:00Z", now));
		assertEquals("2d ago", TsgHubSidebarPanel.dropAge("2026-09-26T12:00:00Z", now));
		assertEquals("1 Jul 2026", TsgHubSidebarPanel.dropAge("2026-07-01T12:00:00Z", now));
		assertEquals("", TsgHubSidebarPanel.dropAge("", now));
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
