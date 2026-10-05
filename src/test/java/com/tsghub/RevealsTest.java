package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;

public class RevealsTest
{
	@Test
	public void firstLoadOnlySetsBaseline()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		assertTrue(reveals.update(event("red", "t0", "t1"), "red").isEmpty());
		assertTrue(reveals.update(event("red", "t0", "t1"), "red").isEmpty());
	}

	@Test
	public void newTilesAndLinesAreRevealed()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		reveals.update(event("red", "t0", "t1"), "red");
		List<TsgHubReveals.Reveal> found = reveals.update(event("red", "t0", "t1", "t2"), "red");
		assertEquals(2, found.size());
		assertFalse(found.get(0).line);
		assertEquals("Task 2", found.get(0).title);
		assertEquals("by Crab Legs", found.get(0).detail);
		assertEquals(3, found.get(0).points);
		assertTrue(found.get(1).line);
		assertEquals("Row 1", found.get(1).title);
		assertEquals(5, found.get(1).points);
		assertTrue(reveals.update(event("red", "t0", "t1", "t2"), "red").isEmpty());
	}

	@Test
	public void switchingEventsOrTeamsResetsBaseline()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		reveals.update(event("red"), "red");
		assertTrue(reveals.update(event("blue", "t0"), "blue").isEmpty());
		reveals.update(null, null);
		assertTrue(reveals.update(event("blue", "t0", "t1"), "blue").isEmpty());
	}

	@Test
	public void bossRaidAndSkillTasksUseHiscoreSprites()
	{
		assertEquals(HiscoreSkill.VORKATH.getSpriteId(), TsgHubTileIcons.spriteFor(task("kill", "npcName", "Vorkath")));
		assertEquals(HiscoreSkill.ALCHEMICAL_HYDRA.getSpriteId(), TsgHubTileIcons.spriteFor(task("kill", "npcName", "alchemical hydra")));
		assertEquals(HiscoreSkill.MINING.getSpriteId(), TsgHubTileIcons.spriteFor(task("xp", "skill", "MINING")));
		JsonObject raid = task("raid", "npcName", "");
		JsonArray modes = new JsonArray();
		modes.add("tob_hm");
		raid.getAsJsonObject("config").add("modes", modes);
		assertEquals(HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE.getSpriteId(), TsgHubTileIcons.spriteFor(raid));
		assertEquals(0, TsgHubTileIcons.spriteFor(task("kill", "npcName", "Goblin")));
		assertEquals(0, TsgHubTileIcons.spriteFor(task("manual", "npcName", "")));
	}

	private static JsonObject task(String type, String key, String value)
	{
		JsonObject task = new JsonObject();
		task.addProperty("type", type);
		JsonObject config = new JsonObject();
		config.addProperty(key, value);
		task.add("config", config);
		return task;
	}

	private static JsonObject event(String teamId, String... done)
	{
		JsonObject event = new JsonObject();
		event.addProperty("id", "e1");
		JsonArray tasks = new JsonArray();
		JsonArray rows = new JsonArray();
		for (int i = 0; i < 9; i++)
		{
			JsonObject task = new JsonObject();
			task.addProperty("id", "t" + i);
			task.addProperty("title", "Task " + i);
			task.addProperty("points", i + 1);
			tasks.add(task);
			JsonObject row = new JsonObject();
			row.addProperty("taskId", "t" + i);
			row.addProperty("completedBy", "Crab Legs");
			boolean complete = false;
			for (String id : done) complete |= id.equals("t" + i);
			row.addProperty("completed", complete);
			rows.add(row);
		}
		event.add("tasks", tasks);
		JsonObject board = new JsonObject();
		board.addProperty("size", 3);
		board.addProperty("lineBonus", 5);
		event.add("board", board);
		JsonObject score = new JsonObject();
		score.addProperty("teamId", teamId);
		score.add("tasks", rows);
		JsonArray scores = new JsonArray();
		scores.add(score);
		event.add("teamScores", scores);
		return event;
	}
}
