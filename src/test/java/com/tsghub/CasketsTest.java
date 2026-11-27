package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.*;
import org.junit.Test;

public class CasketsTest
{
	private static Map<String, Integer> of(Object... pairs)
	{
		Map<String, Integer> out = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], (Integer) pairs[i + 1]);
		return out;
	}

	@Test
	public void tiers()
	{
		assertEquals("hard", TsgHubCaskets.casketTier("Reward casket (hard)"));
		assertEquals("master", TsgHubCaskets.clueTier("Clue scroll (master)"));
		assertEquals("elite", TsgHubCaskets.clueTier("Challenge scroll (elite)"));
		assertNull(TsgHubCaskets.casketTier("Casket"));
		assertNull(TsgHubCaskets.clueTier("Clue bottle (easy)"));
	}

	@Test
	public void finishedClueCounts()
	{
		TsgHubCaskets c = new TsgHubCaskets();
		assertTrue(c.update(of("hard", 3), of("hard", 1), 10, false).isEmpty());
		assertEquals(Collections.singletonList("hard"), c.update(of("hard", 4), of(), 20, false));
	}

	@Test
	public void clueGoneTickBeforeCasket()
	{
		TsgHubCaskets c = new TsgHubCaskets();
		c.update(of(), of("easy", 1), 10, false);
		assertTrue(c.update(of(), of(), 11, false).isEmpty());
		assertEquals(Collections.singletonList("easy"), c.update(of("easy", 1), of(), 12, false));
	}

	@Test
	public void withdrawnOrPickedUpCasketsDontCount()
	{
		TsgHubCaskets c = new TsgHubCaskets();
		c.update(of(), of("hard", 1), 10, false);
		assertTrue(c.update(of("hard", 5), of(), 11, true).isEmpty());
		assertTrue(c.update(of(), of(), 20, false).isEmpty());
		assertTrue(c.update(of("hard", 5), of(), 30, false).isEmpty());
		c.update(of("hard", 5), of("hard", 1), 40, false);
		assertTrue(c.update(of("hard", 5), of(), 50, false).isEmpty());
		assertTrue(c.update(of("hard", 6), of(), 60, false).isEmpty());
	}

	@Test
	public void firstSnapshotOnlyPrimes()
	{
		TsgHubCaskets c = new TsgHubCaskets();
		assertTrue(c.update(of("elite", 2), of(), 1, false).isEmpty());
		c.reset();
		assertTrue(c.update(of("elite", 2), of(), 2, false).isEmpty());
	}
}
