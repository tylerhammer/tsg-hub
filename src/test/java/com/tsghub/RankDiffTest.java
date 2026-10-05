package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

public class RankDiffTest
{
	private static Map<String, Integer> roster(Object... pairs)
	{
		Map<String, Integer> roster = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) roster.put((String) pairs[i], (Integer) pairs[i + 1]);
		return roster;
	}

	private static List<String> text(List<TsgHubRanks.Change> changes)
	{
		return changes.stream().map(Object::toString).collect(Collectors.toList());
	}

	@Test
	public void unchangedRosterHasNoChanges()
	{
		assertTrue(TsgHubRanks.diff(roster("Owner", 126, "Member", 0), roster("Owner", 126, "Member", 0)).isEmpty());
	}

	@Test
	public void reportsPromotionsJoinsAndLeaves()
	{
		List<TsgHubRanks.Change> changes = TsgHubRanks.diff(
			roster("Owner", 126, "Promoted", 0, "Leaver", 100),
			roster("Owner", 126, "Promoted", 100, "Joiner", 0));
		assertEquals(Arrays.asList("Promoted: 0 -> 100", "Joiner: -1 -> 0", "Leaver: 100 -> -1"), text(changes));
	}

	@Test
	public void mergeKeepsFirstOldRankAndDropsReverts()
	{
		Map<String, TsgHubRanks.Change> pending = new LinkedHashMap<>();
		TsgHubRanks.merge(pending, TsgHubRanks.diff(roster("A", 0, "B", 10), roster("A", 50, "B", 20)));
		TsgHubRanks.merge(pending, TsgHubRanks.diff(roster("A", 50, "B", 20), roster("A", 100, "B", 10)));
		assertEquals(Arrays.asList("A: 0 -> 100"), text(new java.util.ArrayList<>(pending.values())));
	}

	@Test
	public void onlyOwnersAndDeputiesReport()
	{
		assertFalse(TsgHubRanks.mayReport(null));
		assertFalse(TsgHubRanks.mayReport(100));
		assertFalse(TsgHubRanks.mayReport(-1));
		assertTrue(TsgHubRanks.mayReport(125));
		assertTrue(TsgHubRanks.mayReport(126));
	}

	@Test
	public void keyRoleFromMe()
	{
		JsonObject member = new JsonObject();
		member.addProperty("role", "member");
		JsonObject admin = new JsonObject();
		admin.addProperty("role", "admin");
		assertFalse(TsgHubPlugin.grantsAdmin(member));
		assertTrue(TsgHubPlugin.grantsAdmin(admin));
		assertTrue(TsgHubPlugin.grantsAdmin(new JsonObject()));
	}

	@Test
	public void clientErrorsStopReporting()
	{
		assertTrue(TsgHubRanks.permanent(400));
		assertTrue(TsgHubRanks.permanent(403));
		assertFalse(TsgHubRanks.permanent(429));
		assertFalse(TsgHubRanks.permanent(503));
	}
}
