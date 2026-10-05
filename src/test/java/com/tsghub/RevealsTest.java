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
		assertTrue(reveals.update(event("red", "t0", "t1"), "red", "Crab Legs").isEmpty());
		assertTrue(reveals.update(event("red", "t0", "t1"), "red", "Crab Legs").isEmpty());
	}

	@Test
	public void newTilesAndLinesAreRevealed()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		reveals.update(event("red", "t0", "t1"), "red", "Crab Legs");
		List<TsgHubReveals.Reveal> found = reveals.update(event("red", "t0", "t1", "t2"), "red", "Crab Legs");
		assertEquals(2, found.size());
		assertFalse(found.get(0).line);
		assertEquals("Task 2", found.get(0).title);
		assertEquals("by Crab Legs", found.get(0).detail);
		assertEquals(3, found.get(0).points);
		assertTrue(found.get(1).line);
		assertEquals("Row 1", found.get(1).title);
		assertEquals(5, found.get(1).points);
		assertTrue(reveals.update(event("red", "t0", "t1", "t2"), "red", "Crab Legs").isEmpty());
	}

	@Test
	public void switchingEventsOrTeamsResetsBaseline()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		reveals.update(event("red"), "red", "Crab Legs");
		assertTrue(reveals.update(event("blue", "t0"), "blue", "Crab Legs").isEmpty());
		reveals.update(null, null, "");
		assertTrue(reveals.update(event("blue", "t0", "t1"), "blue", "Crab Legs").isEmpty());
	}

	@Test
	public void teammatesDoNotGetTheReveal()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		reveals.update(event("red", "t0", "t1"), "red", "Mossy Rock");
		assertTrue(reveals.update(event("red", "t0", "t1", "t2"), "red", "Mossy Rock").isEmpty());
	}

	@Test
	public void lineGoesToWhoeverFinishedIt()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		JsonObject before = event("red", "t0", "t1");
		JsonObject after = event("red", "t0", "t1", "t2", "t4");
		row(after, "t2").addProperty("completedBy", "Mossy Rock");
		reveals.update(before, "red", "Crab Legs");
		List<TsgHubReveals.Reveal> found = reveals.update(after, "red", "Crab Legs");
		assertEquals(1, found.size());
		assertEquals("Task 4", found.get(0).title);
	}

	@Test
	public void everyoneTileGoesToTheLastFinisher()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		JsonObject before = event("red");
		everyone(before, "t5", false, false);
		JsonObject after = event("red", "t5");
		everyone(after, "t5", true, true);
		reveals.update(before, "red", "Crab Legs");
		List<TsgHubReveals.Reveal> found = reveals.update(after, "red", "Crab Legs");
		assertEquals(1, found.size());
		assertEquals("Task 5", found.get(0).title);

		TsgHubReveals early = new TsgHubReveals();
		JsonObject waiting = event("red");
		everyone(waiting, "t5", true, false);
		early.update(waiting, "red", "Crab Legs");
		assertTrue(early.update(after, "red", "Crab Legs").isEmpty());
	}

	private static void everyone(JsonObject event, String taskId, boolean crab, boolean mossy)
	{
		for (int i = 0; i < event.getAsJsonArray("tasks").size(); i++)
		{
			JsonObject task = event.getAsJsonArray("tasks").get(i).getAsJsonObject();
			if (taskId.equals(task.get("id").getAsString())) task.addProperty("scope", "individual");
		}
		JsonObject row = row(event, taskId);
		row.remove("completedBy");
		JsonArray members = new JsonArray();
		members.add(member("Crab Legs", crab));
		members.add(member("Mossy Rock", mossy));
		row.add("members", members);
	}

	private static JsonObject member(String name, boolean completed)
	{
		JsonObject member = new JsonObject();
		member.addProperty("displayName", name);
		member.addProperty("completed", completed);
		return member;
	}

	private static JsonObject row(JsonObject event, String taskId)
	{
		JsonArray rows = event.getAsJsonArray("teamScores").get(0).getAsJsonObject().getAsJsonArray("tasks");
		for (int i = 0; i < rows.size(); i++) if (taskId.equals(rows.get(i).getAsJsonObject().get("taskId").getAsString())) return rows.get(i).getAsJsonObject();
		throw new IllegalArgumentException(taskId);
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
