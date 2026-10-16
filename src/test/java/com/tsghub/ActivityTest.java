package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.tsghub.TsgHubActivity.Member;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ActivityTest
{
	private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

	private static JsonArray list(String json)
	{
		return new JsonParser().parse(json).getAsJsonArray();
	}

	private static Member clan(String name)
	{
		return new Member(name, "Recruit", 10, 3, LocalDate.of(2025, 1, 2), null);
	}

	@Test
	public void mergeMatchesNormalizedAndPreviousNames()
	{
		JsonArray server = list("[{\"displayName\":\"Iron Bob\",\"lastSeenAt\":\"2026-10-15T12:00:00Z\",\"eventsAttended\":2},"
			+ "{\"displayName\":\"New Name\",\"previousNames\":[\"Old_Name\"],\"lastSeenAt\":\"2026-09-01T00:00:00Z\"},"
			+ "{\"displayName\":\"Not In Clan\"}]");
		List<Member> merged = TsgHubActivity.merge(Arrays.asList(clan("iron_bob"), clan("Old Name"), clan("Lurker")), server);

		assertEquals(3, merged.size());
		assertEquals("iron_bob", merged.get(0).name);
		assertEquals("Recruit", merged.get(0).rank);
		assertEquals(LocalDate.of(2025, 1, 2), merged.get(0).joined);
		assertEquals(2, merged.get(0).stat("eventsAttended"));
		assertEquals("New Name", TsgHubUi.str(merged.get(1).stats, "displayName"));
		assertFalse(merged.get(2).onHub());
		assertEquals(-1, merged.get(2).stat("drops"));
	}

	@Test
	public void currentNameWinsOverPreviousName()
	{
		JsonArray server = list("[{\"displayName\":\"Renamed\",\"previousNames\":[\"Taken\"]},{\"displayName\":\"Taken\",\"drops\":5}]");
		List<Member> merged = TsgHubActivity.merge(Arrays.asList(clan("Taken")), server);
		assertEquals(5, merged.get(0).stat("drops"));
	}

	@Test
	public void mergeWithoutClanSettingsKeepsServerMembers()
	{
		List<Member> merged = TsgHubActivity.merge(null, list("[{\"displayName\":\"Solo\"}]"));
		assertEquals(1, merged.size());
		assertEquals("Solo", merged.get(0).name);
		assertTrue(merged.get(0).onHub());
		assertNull(merged.get(0).joined);
	}

	@Test
	public void summaryCountsActiveAdoptionAndTurnout()
	{
		JsonArray server = list("[{\"displayName\":\"A\",\"lastSeenAt\":\"2026-10-15T00:00:00Z\",\"eventsAttended\":4},"
			+ "{\"displayName\":\"B\",\"lastSeenAt\":\"2026-08-01T00:00:00Z\",\"eventsAttended\":0}]");
		List<Member> merged = TsgHubActivity.merge(Arrays.asList(clan("A"), clan("B"), clan("C"), clan("D")), server);
		TsgHubActivity.Summary summary = TsgHubActivity.summarize(merged, 4, NOW, 14);
		assertEquals(4, summary.total);
		assertEquals(1, summary.active);
		assertEquals(2, summary.onHub);
		assertEquals(50, summary.turnoutPercent);
		assertTrue(merged.get(1).inactive(NOW, 14));
		assertTrue(merged.get(2).inactive(NOW, 14));
		assertEquals(-1, TsgHubActivity.summarize(merged, 0, NOW, 14).turnoutPercent);
	}

	@Test
	public void agoFormatsRelativeTimes()
	{
		assertEquals("never", TsgHubActivity.ago(null, NOW));
		assertEquals("5m ago", TsgHubActivity.ago(NOW.minusSeconds(300), NOW));
		assertEquals("3h ago", TsgHubActivity.ago(NOW.minusSeconds(3 * 3600), NOW));
		assertEquals("2d ago", TsgHubActivity.ago(NOW.minusSeconds(2 * 86400), NOW));
	}

	@Test
	public void compareVersionsIsNumeric()
	{
		assertTrue(TsgHubActivity.compareVersions("1.10.0", "1.9.1") > 0);
		assertTrue(TsgHubActivity.compareVersions("1.9", "1.9.1") < 0);
		assertTrue(TsgHubActivity.compareVersions("", "1.0.0") < 0);
		assertEquals(0, TsgHubActivity.compareVersions("1.9.1", "1.9.1"));
	}

	@Test
	public void clanmatesDedupesAndSkipsSelf()
	{
		List<String> names = TsgHubPlugin.clanmates(Arrays.asList("Iron Bob", "iron_bob", "Me", null, " ", "Carol"), "me");
		assertEquals(Arrays.asList("Iron Bob", "Carol"), names);
		assertTrue(TsgHubPlugin.clanmates(Arrays.asList("Me"), "Me").isEmpty());
	}

	@Test
	public void bundledRulesIncludeGroupContent()
	{
		assertTrue(TsgHubRules.get().groupContent.contains("count is:"));
		assertTrue(TsgHubRules.get().groupContent.contains("amount of rifts you have closed"));
	}
}
