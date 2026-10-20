package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.tsghub.TsgHubMembers.Member;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class MembersTest
{
	@BeforeClass
	public static void rules()
	{
		TsgHubRules.loadBundled(new Gson());
	}

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
		List<Member> merged = TsgHubMembers.merge(Arrays.asList(clan("iron_bob"), clan("Old Name"), clan("Lurker")), server);

		assertEquals(3, merged.size());
		assertEquals("iron_bob", merged.get(0).name);
		assertEquals("Recruit", merged.get(0).rank);
		assertEquals(LocalDate.of(2025, 1, 2), merged.get(0).joined);
		assertEquals(2, merged.get(0).stat("eventsAttended"));
		assertEquals("New Name", TsgHubJson.str(merged.get(1).stats, "displayName"));
		assertFalse(merged.get(2).onHub());
		assertEquals(-1, merged.get(2).stat("drops"));
	}

	@Test
	public void withNotesAttachesNotesAltsAndWarnings()
	{
		JsonArray notes = list("[{\"displayName\":\"Iron Bob\",\"altOf\":\"Main Bob\",\"note\":\"Owes 5m\","
			+ "\"warnings\":[{\"id\":\"w1\",\"active\":false},{\"id\":\"w2\",\"active\":true}]},"
			+ "{\"displayName\":\"Main Bob\",\"alts\":[\"Iron Bob\"]}]");
		List<Member> merged = TsgHubMembers.withNotes(Arrays.asList(clan("iron_bob"), clan("Main Bob"), clan("Lurker")), notes);

		assertEquals("Main Bob", merged.get(0).altOf());
		assertEquals("Owes 5m", merged.get(0).note());
		assertTrue(merged.get(0).alt());
		assertEquals(1, merged.get(0).activeWarnings().size());
		assertEquals("w2", TsgHubJson.str(TsgHubRoster.activeFirst(TsgHubJson.objects(merged.get(0).warnings())).get(0), "id"));
		assertEquals(1, merged.get(1).alts().size());
		assertFalse(merged.get(1).alt());
		assertEquals("", merged.get(2).note());
		assertTrue(merged.get(2).activeWarnings().isEmpty());
		assertEquals(3, TsgHubMembers.withNotes(merged, null).size());
	}

	@Test
	public void hoursRoundToNearestHalf()
	{
		assertEquals("0", TsgHubMembersPanel.halfHours(10));
		assertEquals("0.5", TsgHubMembersPanel.halfHours(20));
		assertEquals("1", TsgHubMembersPanel.halfHours(70));
		assertEquals("15.5", TsgHubMembersPanel.halfHours(925));
	}

	@Test
	public void withNotesFollowsHubName()
	{
		JsonArray server = list("[{\"displayName\":\"New Name\",\"previousNames\":[\"Old Name\"]}]");
		JsonArray notes = list("[{\"displayName\":\"New Name\",\"note\":\"Moved\"}]");
		Member member = TsgHubMembers.withNotes(TsgHubMembers.merge(Arrays.asList(clan("Old Name")), server), notes).get(0);
		assertEquals("Old Name", member.name);
		assertEquals("New Name", member.hubName());
		assertEquals("Moved", member.note());
		assertEquals("Lurker", clan("Lurker").hubName());
	}

	@Test
	public void mainCandidatesSkipAltsAndSelf()
	{
		java.util.Map<String, String> ranks = new java.util.LinkedHashMap<>();
		ranks.put("Zed", "Recruit");
		ranks.put("Gnome", "Gnome Child");
		ranks.put("Alice", "Owner");
		ranks.put("Me", "Recruit");
		assertEquals(Arrays.asList("Alice", "Zed"), TsgHubMemberAdmin.mainCandidates("me", ranks));
	}

	@Test
	public void currentNameWinsOverPreviousName()
	{
		JsonArray server = list("[{\"displayName\":\"Renamed\",\"previousNames\":[\"Taken\"]},{\"displayName\":\"Taken\",\"drops\":5}]");
		List<Member> merged = TsgHubMembers.merge(Arrays.asList(clan("Taken")), server);
		assertEquals(5, merged.get(0).stat("drops"));
	}

	@Test
	public void previousNameIgnoredWhenOwnerIsStillInClan()
	{
		JsonArray server = list("[{\"displayName\":\"Zed2\",\"previousNames\":[\"Zed\"],\"drops\":3}]");
		List<Member> merged = TsgHubMembers.merge(Arrays.asList(clan("Zed2"), clan("Zed")), server);
		assertEquals(3, merged.get(0).stat("drops"));
		assertFalse(merged.get(1).onHub());
	}

	@Test
	public void mergeKeepsDistinctSpacedNames()
	{
		JsonArray server = list("[{\"displayName\":\"Bob 1\",\"drops\":1},{\"displayName\":\"Bob1\",\"drops\":2}]");
		List<Member> merged = TsgHubMembers.merge(Arrays.asList(clan("Bob 1"), clan("Bob1")), server);
		assertEquals(1, merged.get(0).stat("drops"));
		assertEquals(2, merged.get(1).stat("drops"));
	}

	@Test
	public void mergeWithoutClanSettingsKeepsServerMembers()
	{
		List<Member> merged = TsgHubMembers.merge(null, list("[{\"displayName\":\"Solo\"}]"));
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
		List<Member> merged = TsgHubMembers.merge(Arrays.asList(clan("A"), clan("B"), clan("C"), clan("D")), server);
		TsgHubMembers.Summary summary = TsgHubMembers.summarize(merged, 4, NOW, 14);
		assertEquals(4, summary.total);
		assertEquals(1, summary.active);
		assertEquals(2, summary.onHub);
		assertEquals(50, summary.turnoutPercent);
		assertTrue(merged.get(1).inactive(NOW, 14));
		assertTrue(merged.get(2).inactive(NOW, 14));
		assertEquals(-1, TsgHubMembers.summarize(merged, 0, NOW, 14).turnoutPercent);
	}

	@Test
	public void agoFormatsRelativeTimes()
	{
		assertEquals("never", TsgHubMembers.ago(null, NOW));
		assertEquals("5m ago", TsgHubMembers.ago(NOW.minusSeconds(300), NOW));
		assertEquals("3h ago", TsgHubMembers.ago(NOW.minusSeconds(3 * 3600), NOW));
		assertEquals("2d ago", TsgHubMembers.ago(NOW.minusSeconds(2 * 86400), NOW));
	}

	@Test
	public void compareVersionsIsNumeric()
	{
		assertTrue(TsgHubMembers.compareVersions("1.10.0", "1.9.1") > 0);
		assertTrue(TsgHubMembers.compareVersions("1.9", "1.9.1") < 0);
		assertTrue(TsgHubMembers.compareVersions("", "1.0.0") < 0);
		assertEquals(0, TsgHubMembers.compareVersions("1.9.1", "1.9.1"));
	}

	@Test
	public void clanmatesDedupesAndSkipsSelf()
	{
		List<String> names = TsgHubGameTracker.clanmates(Arrays.asList("Iron Bob", "iron_bob", "Me", null, " ", "Carol"), "me");
		assertEquals(Arrays.asList("Iron Bob", "Carol"), names);
		assertTrue(TsgHubGameTracker.clanmates(Arrays.asList("Me"), "Me").isEmpty());
	}

	@Test
	public void bundledRulesIncludeGroupContent()
	{
		assertTrue(TsgHubRules.get().groupContent.contains("kill count is:"));
		assertTrue(TsgHubRules.get().groupContent.contains("amount of rifts you have closed"));
	}

	@Test
	public void chartBucketsFollowWindow()
	{
		LocalDate end = LocalDate.of(2026, 10, 16);
		java.util.Map<LocalDate, Integer> minutes = new java.util.HashMap<>();
		minutes.put(end, 30);
		minutes.put(end.minusDays(6), 45);
		minutes.put(end.minusDays(7), 60);
		minutes.put(end.minusDays(90), 10);
		minutes.put(end.minusDays(91), 999);
		assertEquals(91, TsgHubMemberDetail.ActivityChart.requestDays(90));
		assertEquals(30, TsgHubMemberDetail.ActivityChart.requestDays(30));
		int[] weeks = TsgHubMemberDetail.ActivityChart.bucketMinutes(minutes, end, 13, 7);
		assertEquals(75, weeks[12]);
		assertEquals(60, weeks[11]);
		assertEquals(10, weeks[0]);
		int[] days = TsgHubMemberDetail.ActivityChart.bucketMinutes(minutes, end, 7, 1);
		assertEquals(30, days[6]);
		assertEquals(45, days[0]);
	}

	@Test
	public void chartAxisUsesMinutesForSmallValues()
	{
		assertEquals(5, TsgHubMemberDetail.ActivityChart.step(0));
		assertEquals(10, TsgHubMemberDetail.ActivityChart.step(25));
		assertEquals(60, TsgHubMemberDetail.ActivityChart.step(200));
		assertEquals(12000, TsgHubMemberDetail.ActivityChart.step(30000));
		assertEquals("0", TsgHubMemberDetail.ActivityChart.axisLabel(0));
		assertEquals("15m", TsgHubMemberDetail.ActivityChart.axisLabel(15));
		assertEquals("2h", TsgHubMemberDetail.ActivityChart.axisLabel(120));
	}

	@Test
	public void captionOnlyCountsTrackedDays()
	{
		LocalDate end = LocalDate.of(2026, 10, 16);
		java.util.Map<LocalDate, Integer> minutes = new java.util.HashMap<>();
		minutes.put(end, 25);
		LocalDate windowStart = end.minusDays(90);
		TsgHubMemberDetail.ChartStats fresh = TsgHubMemberDetail.chartStats(minutes, 25, end, windowStart, end, 7);
		assertFalse(fresh.weekly);
		assertEquals("25 min", fresh.average);
		assertEquals("1 of 1 day", fresh.active);
		minutes.put(end.minusDays(13), 95);
		TsgHubMemberDetail.ChartStats weekly = TsgHubMemberDetail.chartStats(minutes, 120, end, windowStart, end.minusDays(13), 7);
		assertTrue(weekly.weekly);
		assertEquals("1.0h", weekly.average);
		assertEquals("2 of 14 days", weekly.active);
		assertEquals("-", TsgHubMemberDetail.chartStats(new java.util.HashMap<>(), 0, end, windowStart, null, 1).average);
	}
}
