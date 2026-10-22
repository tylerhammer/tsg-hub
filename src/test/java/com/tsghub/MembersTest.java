package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public class MembersTest
{
	@BeforeClass
	public static void rules()
	{
		TsgHubRules.loadBundled(new Gson());
	}

	@Test
	public void mainCandidatesSkipAltsAndSelf()
	{
		Map<String, String> ranks = new LinkedHashMap<>();
		ranks.put("Zed", "Recruit");
		ranks.put("Gnome", "Gnome Child");
		ranks.put("Alice", "Owner");
		ranks.put("Me", "Recruit");
		assertEquals(Arrays.asList("Alice", "Zed"), TsgHubMemberAdmin.mainCandidates("me", ranks));
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
}
