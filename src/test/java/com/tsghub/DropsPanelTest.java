package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.BeforeClass;
import org.junit.Test;

public class DropsPanelTest
{
	@BeforeClass
	public static void rules()
	{
		TsgHubRules.loadBundled(new Gson());
	}

	@Test
	public void formatGp()
	{
		assertEquals("", TsgHubFormat.formatGp(0));
		assertEquals("950 gp", TsgHubFormat.formatGp(950));
		assertEquals("7.2K", TsgHubFormat.formatGp(7_250));
		assertEquals("1.5M", TsgHubFormat.formatGp(1_512_345));
		assertEquals("12M", TsgHubFormat.formatGp(12_000_000));
		assertEquals("1.2B", TsgHubFormat.formatGp(1_234_567_890L));
	}

	private static JsonObject luck(double share, String verdict)
	{
		JsonObject luck = new JsonObject();
		luck.addProperty("share", share);
		luck.addProperty("verdict", verdict);
		return luck;
	}

	@Test
	public void luckTooltip()
	{
		JsonObject drop = drop("Vorki", 1);
		drop.addProperty("killCount", 12);
		drop.add("luck", luck(0.004, "spooned"));
		assertTrue(TsgHubDrops.dropTooltip(drop).contains("LUCK</b> </font>Only 0.4% of players would have it by this kill count"));
		drop.add("luck", luck(0.93, "dry"));
		assertTrue(TsgHubDrops.dropTooltip(drop).contains("93% of players would have it by this kill count"));
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

	@Test
	public void broadcastFromSelf()
	{
		assertTrue(TsgHubDrops.fromSelf("Iron Alice received a new collection log item: Hydra's fang (1/1717)", "Iron\u00a0Alice"));
		assertTrue(TsgHubDrops.fromSelf("HammerKR received a drop: Hydra's fang (1,000,000 coins).", "hammerkr"));
		assertFalse(TsgHubDrops.fromSelf("HammerKR2 received a drop: Hydra's fang (1,000,000 coins).", "HammerKR"));
		assertFalse(TsgHubDrops.fromSelf("Bob received a drop: Hydra's fang (1,000,000 coins).", "HammerKR"));
		assertFalse(TsgHubDrops.fromSelf("HammerKR received a drop: Hydra's fang (1,000,000 coins).", ""));
	}

	@Test
	public void recentContextSinceTick()
	{
		TsgHubDrops.Recent<String> recent = new TsgHubDrops.Recent<>();
		recent.add(80, "Kraken");
		recent.add(99, "Abyssal demon");
		JsonArray rows = recent.since(90, source -> {
			JsonObject row = new JsonObject();
			row.addProperty("source", source);
			return row;
		});
		assertEquals(1, rows.size());
		assertEquals(99, rows.get(0).getAsJsonObject().get("tick").getAsInt());
		assertEquals("Abyssal demon", rows.get(0).getAsJsonObject().get("source").getAsString());
	}

	@Test
	public void dropTooltip()
	{
		JsonObject drop = drop("Dragon pickaxe", 1);
		assertEquals("", TsgHubDrops.dropTooltip(drop));
		drop.addProperty("killCount", 1812);
		assertTrue(TsgHubDrops.dropTooltip(drop).endsWith("KILL COUNT</b> </font>1,812</div>"));
		drop.addProperty("source", "Spindel");
		drop.addProperty("rate", "1/358");
		String tip = TsgHubDrops.dropTooltip(drop);
		assertTrue(tip, tip.contains("<b>Spindel</b><br>1/358") && tip.contains("DROPPED BY"));
	}

	private static JsonObject drop(String item, int quantity)
	{
		JsonObject drop = new JsonObject();
		drop.addProperty("item", item);
		drop.addProperty("quantity", quantity);
		return drop;
	}
}
