package com.tsghub;

import static org.junit.Assert.assertEquals;
import net.runelite.api.Skill;
import org.junit.Test;

public class ActivityDetectorTest
{
	@Test
	public void singleDropIsNotSkilling()
	{
		ActivityDetector detector = new ActivityDetector();
		detector.onXp(Skill.MAGIC, 100, 0);
		detector.onXp(Skill.MAGIC, 135, 1_000);
		assertEquals("Idle", detector.activity(null, 2_000));
		detector.onXp(Skill.MAGIC, 170, 200_000);
		assertEquals("Idle", detector.activity(null, 201_000));
	}

	@Test
	public void repeatedDropsAreSkilling()
	{
		ActivityDetector detector = new ActivityDetector();
		detector.onXp(Skill.MINING, 100, 0);
		detector.onXp(Skill.MINING, 135, 1_000);
		detector.onXp(Skill.MINING, 170, 30_000);
		assertEquals("Idle", detector.activity(null, 31_000));
		detector.onXp(Skill.MINING, 205, 60_000);
		assertEquals("Skilling - Mining", detector.activity(null, 61_000));
		detector.onXp(Skill.MAGIC, 100, 62_000);
		detector.onXp(Skill.MAGIC, 135, 63_000);
		assertEquals("Skilling - Mining", detector.activity(null, 64_000));
		detector.onXp(Skill.MINING, 240, 140_000);
		assertEquals("Skilling - Mining", detector.activity(null, 220_000));
		assertEquals("Idle", detector.activity(null, 231_000));
	}
}
