package com.tsghub;

import static org.junit.Assert.assertEquals;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PartyAreaTest
{
	@Test
	public void wildernessLevel()
	{
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(3200, 3519, 0)));
		assertEquals(1, AreaNames.wildernessLevel(new WorldPoint(3200, 3520, 0)));
		assertEquals(56, AreaNames.wildernessLevel(new WorldPoint(3200, 3967, 0)));
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(3200, 3968, 0)));
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(2943, 3700, 0)));
		assertEquals(1, AreaNames.wildernessLevel(new WorldPoint(3200, 9920, 0)));
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(3200, 9919, 0)));
	}
}
