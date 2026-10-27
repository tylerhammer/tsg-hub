package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

public class RecordsTest
{
	@Test
	public void pbSeconds()
	{
		assertEquals(75.6, TsgHubRecords.seconds("75.6"), 0);
		assertEquals(54.0, TsgHubRecords.seconds(" 54 "), 0);
		assertEquals(0, TsgHubRecords.seconds(null), 0);
		assertEquals(0, TsgHubRecords.seconds("fast"), 0);
		assertEquals(0, TsgHubRecords.seconds("NaN"), 0);
	}

	private static JsonObject entry(String player, String key, Object value)
	{
		JsonObject row = new JsonObject();
		row.addProperty("player", player);
		if (value instanceof Number) row.addProperty(key, (Number) value);
		else row.addProperty(key, (String) value);
		return row;
	}

	@Test
	public void standingsTooltip()
	{
		JsonArray kc = new JsonArray();
		kc.add(entry("Zezima", "score", 1234));
		kc.add(entry("Bob", "score", 900));
		String tip = TsgHubRecordsView.standings("MOST KC", kc, true, "Bob");
		assertTrue(tip, tip.contains("MOST KC"));
		assertTrue(tip, tip.contains("1. Zezima<font color='#a29cab'> 1,234</font>"));
		assertTrue(tip, tip.contains("2. " + TsgHubTheme.selfName("Bob")));

		JsonArray pb = new JsonArray();
		pb.add(entry("Zezima", "time", "0:54.60"));
		assertTrue(TsgHubRecordsView.standings("FASTEST", pb, false, "Bob").contains("0:54.60"));
		assertEquals("", TsgHubRecordsView.standings("FASTEST", new JsonArray(), true, "Bob"));
	}
}
