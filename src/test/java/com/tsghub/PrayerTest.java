package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import net.runelite.api.Prayer;
import org.junit.Test;

public class PrayerTest
{
	@Test
	public void loadsPrayerTable()
	{
		PrayerSprites.load(new Gson());
		assertEquals(31, PrayerSprites.all.size());
		assertEquals(Prayer.THICK_SKIN, PrayerSprites.all.get(0).prayer);
		assertEquals(Prayer.AUGURY, PrayerSprites.all.get(30).prayer);
		assertEquals(25939, PrayerSprites.all.stream().filter(p -> p.prayer == Prayer.DEADEYE).findFirst().get().itemId);
	}

	@Test
	public void relayoutsWhenUnlocksSwap()
	{
		int deadeye = PrayerSprites.DEFAULT_UNLOCKED & ~PrayerSprites.bit(Prayer.EAGLE_EYE) | PrayerSprites.bit(Prayer.DEADEYE);
		assertFalse((PrayerSprites.DEFAULT_UNLOCKED & PrayerSprites.bit(Prayer.DEADEYE)) != 0);
		assertTrue((PrayerSprites.DEFAULT_UNLOCKED & PrayerSprites.bit(Prayer.EAGLE_EYE)) != 0);
		assertTrue(PlayerPrayerPanel.relayout(PrayerSprites.DEFAULT_UNLOCKED, new int[]{0, 0, deadeye}, 30));
		assertFalse(PlayerPrayerPanel.relayout(deadeye, new int[]{1, 2, deadeye}, 30));
		assertTrue(PlayerPrayerPanel.relayout(deadeye, new int[]{0, 0, PrayerSprites.DEFAULT_UNLOCKED}, 30));
		assertTrue(PlayerPrayerPanel.relayout(deadeye, new int[]{0, 0, deadeye}, 0));
	}
}
