package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DropsPanelTest
{
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
}
