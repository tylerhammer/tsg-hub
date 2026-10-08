package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class MembersPanelTest
{
	@Test
	public void activityDetail()
	{
		assertEquals("Mining · Motherlode Mine", TsgHubSidebarPanel.activityDetail("Skilling - Mining", "Motherlode Mine"));
		assertEquals("Nex", TsgHubSidebarPanel.activityDetail("Bossing - Nex", "Nex"));
		assertEquals("Idle · Grand Exchange", TsgHubSidebarPanel.activityDetail("Idle", "Grand Exchange"));
		assertEquals("Slayer: Abyssal demons · Catacombs of Kourend", TsgHubSidebarPanel.activityDetail("Slayer - Abyssal demons", "Catacombs of Kourend"));
		assertEquals("Combat", TsgHubSidebarPanel.activityDetail("Combat", ""));
		assertEquals("Online", TsgHubSidebarPanel.activityDetail("", ""));
	}

	@Test
	public void memberTooltip()
	{
		JsonArray none = new JsonArray();
		assertEquals("", TsgHubSidebarPanel.memberTooltip("", "", none, "", false, "", ""));
		String alt = TsgHubSidebarPanel.memberTooltip("Gnome Child", "Fenrir", none, "", false, "", "");
		assertTrue(alt.contains("<b>Gnome Child</b>") && alt.contains("Alt of <b>Fenrir</b>"));
		JsonArray alts = new JsonArray();
		alts.add("Iron Fenrir");
		alts.add(new JsonObject());
		assertTrue(TsgHubSidebarPanel.memberTooltip("Owner", "", alts, "", false, "", "").contains("Alt: Iron Fenrir"));
		String full = TsgHubSidebarPanel.memberTooltip("", "", none, "Nex", true, "3d ago", "Owes <5m>");
		assertTrue(full.contains("Nex") && full.contains("On your world") && full.contains("Last seen 3d ago"));
		assertTrue(full.contains("Admin note") && full.contains("Owes &lt;5m&gt;"));
	}

	@Test
	public void isAltRank()
	{
		assertEquals(true, TsgHubSidebarPanel.isAltRank("Gnome Child"));
		assertEquals(true, TsgHubSidebarPanel.isAltRank("gnome child"));
		assertEquals(false, TsgHubSidebarPanel.isAltRank("Owner"));
		assertEquals(false, TsgHubSidebarPanel.isAltRank(""));
	}

	@Test
	public void rosterSplitsOnlineAndOffline()
	{
		JsonArray presence = new JsonArray();
		JsonObject sharing = new JsonObject();
		sharing.addProperty("displayName", "Zezima");
		sharing.addProperty("activity", "Skilling - Mining");
		presence.add(sharing);
		Map<String, Integer> chat = new LinkedHashMap<>();
		chat.put("zezima", 302);
		chat.put("Bob", 420);
		JsonArray notes = new JsonArray();
		JsonObject note = new JsonObject();
		note.addProperty("displayName", "iron alt");
		note.addProperty("altOf", "Bob");
		notes.add(note);

		JsonArray[] roster = TsgHubPresence.roster(presence, chat, Arrays.asList("Bob", "Zezima", "Iron Alt", "Alice"), notes);

		assertEquals(2, roster[0].size());
		assertEquals("Bob", roster[0].get(0).getAsJsonObject().get("displayName").getAsString());
		assertEquals(420, roster[0].get(0).getAsJsonObject().get("world").getAsInt());
		assertEquals("Skilling - Mining", roster[0].get(1).getAsJsonObject().get("activity").getAsString());
		assertEquals(2, roster[1].size());
		assertEquals("Alice", roster[1].get(0).getAsJsonObject().get("displayName").getAsString());
		assertEquals("Bob", roster[1].get(1).getAsJsonObject().get("altOf").getAsString());
	}

	@Test
	public void lastSeen()
	{
		java.time.Instant now = java.time.Instant.parse("2026-10-07T12:00:00Z");
		assertEquals("", TsgHubSidebarPanel.lastSeen("", now));
		assertEquals("just now", TsgHubSidebarPanel.lastSeen("2026-10-07T11:59:40Z", now));
		assertEquals("45m ago", TsgHubSidebarPanel.lastSeen("2026-10-07T11:15:00Z", now));
		assertEquals("5h ago", TsgHubSidebarPanel.lastSeen("2026-10-07T07:00:00Z", now));
		assertEquals("3d ago", TsgHubSidebarPanel.lastSeen("2026-10-04T12:00:00Z", now));
		assertEquals("3w ago", TsgHubSidebarPanel.lastSeen("2026-09-15T12:00:00Z", now));
		assertEquals("4mo ago", TsgHubSidebarPanel.lastSeen("2026-06-01T12:00:00Z", now));
		assertEquals("2y ago", TsgHubSidebarPanel.lastSeen("2024-09-01T12:00:00Z", now));
	}

	@Test
	public void matchNames()
	{
		java.util.List<String> names = Arrays.asList("Fenrir", "Iron Fenrir", "Mossy Rock", "Fen Dweller");
		assertEquals(Arrays.asList("Fenrir", "Fen Dweller", "Iron Fenrir"), TsgHubSidebarPanel.matchNames(names, "fen", 10));
		assertEquals(Arrays.asList("Mossy Rock"), TsgHubSidebarPanel.matchNames(names, "ssy r", 10));
		assertEquals(4, TsgHubSidebarPanel.matchNames(names, "", 10).size());
		assertEquals("Mossy Rock", TsgHubSidebarPanel.resolveName(names, "mossy"));
		assertEquals("Fenrir", TsgHubSidebarPanel.resolveName(names, "fenrir"));
		assertEquals("fen", TsgHubSidebarPanel.resolveName(names, "fen"));
		assertEquals("Ex Member", TsgHubSidebarPanel.resolveName(names, "Ex Member"));
	}
}
