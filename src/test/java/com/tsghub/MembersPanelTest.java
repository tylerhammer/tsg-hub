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

	private static String tip(String altOf, JsonArray alts, String note, JsonArray warnings)
	{
		return TsgHubSidebarPanel.rosterTooltip(new JsonArray(), altOf, alts, note, warnings, NOW, java.time.ZoneOffset.UTC);
	}

	private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-08T12:00:00Z");

	private static JsonObject warning(String reason, boolean active, String expiresAt, String revokedBy)
	{
		JsonObject warning = new JsonObject();
		warning.addProperty("id", reason);
		warning.addProperty("reason", reason);
		warning.addProperty("issuedBy", "Fenrir");
		warning.addProperty("issuedAt", "2026-09-12T10:00:00Z");
		warning.addProperty("active", active);
		if (expiresAt != null) warning.addProperty("expiresAt", expiresAt);
		if (revokedBy != null)
		{
			warning.addProperty("revokedBy", revokedBy);
			warning.addProperty("revokedAt", "2026-09-20T10:00:00Z");
		}
		return warning;
	}

	@Test
	public void rosterTooltip()
	{
		JsonArray none = new JsonArray();
		assertEquals("", tip("", none, "", none));
		String alt = tip("Fenrir", none, "", none);
		assertTrue(alt.contains("Alt of <b>Fenrir</b>") && !alt.contains("Gnome Child"));
		JsonArray alts = new JsonArray();
		alts.add("Iron Fenrir");
		alts.add(new JsonObject());
		assertTrue(tip("", alts, "", none).contains("Alt: Iron Fenrir"));
		JsonArray formerly = new JsonArray();
		formerly.add("First");
		formerly.add("Second");
		String renamed = TsgHubSidebarPanel.rosterTooltip(formerly, "", none, "", none, NOW, java.time.ZoneOffset.UTC);
		assertTrue(renamed.contains("PREVIOUS NAMES") && renamed.contains("Second<br>First"));
		String full = tip("", none, "Owes <5m>", none);
		assertTrue(full.contains("ADMIN NOTE") && full.contains("Owes &lt;5m&gt;"));
	}

	@Test
	public void warningCards()
	{
		JsonArray warnings = new JsonArray();
		warnings.add(warning("Spam <chat>", true, "2026-12-11T10:00:00Z", null));
		warnings.add(warning("Old one", false, "2026-06-01T10:00:00Z", null));
		warnings.add(warning("Mistake", false, null, "Crab Legs"));
		String html = tip("", new JsonArray(), "", warnings);
		assertTrue(html.contains("WARNINGS") && html.contains("1 active · 1 expired · 1 revoked"));
		assertTrue(html.contains("Spam &lt;chat&gt;") && html.contains("12 Sep · Fenrir · expires 11 Dec"));
		assertTrue(html.contains("Fenrir · expired") && html.contains("revoked by Crab Legs"));
		assertTrue(html.indexOf("Spam") < html.indexOf("Old one"));

		JsonArray many = new JsonArray();
		for (int i = 0; i < 5; i++) many.add(warning("Past " + i, false, "2026-06-01T10:00:00Z", null));
		assertTrue(tip("", new JsonArray(), "", many).contains("+2 older"));
		assertTrue(TsgHubSidebarPanel.hasActiveWarning(warnings));
		assertEquals(false, TsgHubSidebarPanel.hasActiveWarning(many));
	}

	@Test
	public void warningDate()
	{
		assertEquals("12 Sep", TsgHubSidebarPanel.warningDate("2026-09-12T10:00:00Z", NOW, java.time.ZoneOffset.UTC));
		assertEquals("3 Jun 2025", TsgHubSidebarPanel.warningDate("2025-06-03T10:00:00Z", NOW, java.time.ZoneOffset.UTC));
		assertEquals("", TsgHubSidebarPanel.warningDate("", NOW, java.time.ZoneOffset.UTC));
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
		note.addProperty("accountType", 2);
		notes.add(note);

		JsonArray[] roster = TsgHubPresence.roster(presence, chat, Arrays.asList("Bob", "Zezima", "Iron Alt", "Alice"), notes);

		assertEquals(2, roster[0].size());
		assertEquals("Bob", roster[0].get(0).getAsJsonObject().get("displayName").getAsString());
		assertEquals(420, roster[0].get(0).getAsJsonObject().get("world").getAsInt());
		assertEquals("Skilling - Mining", roster[0].get(1).getAsJsonObject().get("activity").getAsString());
		assertEquals(2, roster[1].size());
		assertEquals("Alice", roster[1].get(0).getAsJsonObject().get("displayName").getAsString());
		assertEquals("Bob", roster[1].get(1).getAsJsonObject().get("altOf").getAsString());
		assertEquals(2, TsgHubPresence.accountType(roster[1].get(1).getAsJsonObject()));
		assertEquals(0, TsgHubPresence.accountType(roster[1].get(0).getAsJsonObject()));
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
