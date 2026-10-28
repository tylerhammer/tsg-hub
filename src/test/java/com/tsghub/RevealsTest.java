package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import net.runelite.client.hiscore.HiscoreSkill;
import org.junit.Test;

public class RevealsTest
{
	@Test
	public void firstLoadOnlySetsBaseline()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		assertNull(update(reveals, event("red", "t0", "t1"), "red", "Crab Legs"));
		assertNull(update(reveals, event("red", "t0", "t1"), "red", "Crab Legs"));
	}

	@Test
	public void newTileAndLineMakeOneMoment()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t0", "t1"), "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, event("red", "t0", "t1", "t2"), "red", "Crab Legs");
		assertEquals(Collections.singletonList("t2"), moment.tiles);
		assertEquals(new HashSet<>(Arrays.asList("t0", "t1")), moment.before);
		assertEquals(1, moment.lines.size());
		assertEquals("ROW:0", moment.lines.get(0).key());
		assertEquals(5, moment.bonus());
		assertEquals(3, moment.board.size);
		assertNull(update(reveals, event("red", "t0", "t1", "t2"), "red", "Crab Legs"));
	}

	@Test
	public void switchingEventsOrTeamsResetsBaseline()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red"), "red", "Crab Legs");
		assertNull(update(reveals, event("blue", "t0"), "blue", "Crab Legs"));
		update(reveals, null, null, "");
		assertNull(update(reveals, event("blue", "t0", "t1"), "blue", "Crab Legs"));
	}

	@Test
	public void teammatesDoNotGetTheReveal()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t0", "t1"), "red", "Mossy Rock");
		assertNull(update(reveals, event("red", "t0", "t1", "t2"), "red", "Mossy Rock"));
	}

	@Test
	public void lineGoesToWhoeverFinishedIt()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		JsonObject before = event("red", "t0", "t1");
		JsonObject after = event("red", "t0", "t1", "t2", "t4");
		row(after, "t2").addProperty("completedBy", "Mossy Rock");
		update(reveals, before, "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, after, "red", "Crab Legs");
		assertEquals(Collections.singletonList("t4"), moment.tiles);
		assertTrue(moment.before.contains("t2"));
		assertTrue(moment.lines.isEmpty());
	}

	@Test
	public void everyoneTileGoesToTheLastFinisher()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		JsonObject before = event("red");
		everyone(before, "t5", false, false);
		JsonObject after = event("red", "t5");
		everyone(after, "t5", true, true);
		update(reveals, before, "red", "Crab Legs");
		assertEquals(Collections.singletonList("t5"), update(reveals, after, "red", "Crab Legs").tiles);

		TsgHubReveals early = new TsgHubReveals();
		JsonObject waiting = event("red");
		everyone(waiting, "t5", true, false);
		update(early, waiting, "red", "Crab Legs");
		assertNull(update(early, after, "red", "Crab Legs"));
	}

	@Test
	public void linesAlreadyCompleteNeverReplay()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t0", "t1", "t2"), "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, event("red", "t0", "t1", "t2", "t4"), "red", "Crab Legs");
		assertEquals(Collections.singletonList("t4"), moment.tiles);
		assertTrue(moment.lines.isEmpty());
	}

	@Test
	public void resetSeedsTheNextUpdateSilently()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t0"), "red", "Crab Legs");
		reveals.reset();
		assertNull(update(reveals, event("red", "t0", "t1", "t2"), "red", "Crab Legs"));
		assertEquals(Collections.singletonList("t4"), update(reveals, event("red", "t0", "t1", "t2", "t4"), "red", "Crab Legs").tiles);
	}

	@Test
	public void queuedMomentsForTheSameTeamMerge()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red"), "red", "Crab Legs");
		TsgHubReveals.Moment first = update(reveals, event("red", "t0", "t1"), "red", "Crab Legs");
		TsgHubReveals.Moment second = update(reveals, event("red", "t0", "t1", "t2", "t5"), "red", "Crab Legs");
		assertTrue(first.mergesWith(second));
		TsgHubReveals.Moment merged = TsgHubReveals.Moment.merge(first, second);
		assertEquals(Arrays.asList("t0", "t1", "t2", "t5"), merged.tiles);
		assertTrue(merged.before.isEmpty());
		assertEquals(1, merged.lineCount());
		assertEquals("ROW:0", merged.lines.get(0).key());
		TsgHubReveals other = new TsgHubReveals();
		update(other, event("blue"), "blue", "Crab Legs");
		assertFalse(first.mergesWith(update(other, event("blue", "t0"), "blue", "Crab Legs")));
	}

	@Test
	public void cappedRevealsDaubLineTilesFirst()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red"), "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, event("red", "t0", "t1", "t2", "t7", "t8"), "red", "Crab Legs");
		TsgHubReveals.Moment capped = moment.capped();
		assertEquals(Arrays.asList("t0", "t1", "t2"), capped.tiles);
		assertEquals(Arrays.asList("t7", "t8"), capped.hiddenTiles);
		assertTrue(capped.before.containsAll(Arrays.asList("t7", "t8")));
		assertEquals(8 + 9, capped.hiddenPoints());
		assertEquals(1, capped.lineCount());
		assertTrue(moment.capped().tiles.size() <= TsgHubReveals.MAX_DAUBERS);
	}

	@Test
	public void cappedRevealsKeepEveryLineInTheBonus()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t0", "t1", "t3", "t5", "t6", "t7", "t8"), "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, event("red", "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8"), "red", "Crab Legs");
		assertTrue(moment.lines.size() > TsgHubReveals.MAX_WAVES);
		TsgHubReveals.Moment capped = moment.capped();
		assertEquals(TsgHubReveals.MAX_WAVES, capped.lines.size());
		assertEquals(moment.lines.size(), capped.lineCount());
		assertEquals(moment.bonus(), capped.bonus());
		assertEquals(Arrays.asList("t2", "t4"), capped.tiles);
	}

	@Test
	public void smallRevealsAreNotCapped()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t0", "t1"), "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, event("red", "t0", "t1", "t2"), "red", "Crab Legs");
		assertTrue(moment.capped() == moment);
	}

	@Test
	public void taskListBingosNeverReveal()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		JsonObject before = event("red", "t0", "t1");
		JsonObject after = event("red", "t0", "t1", "t2");
		before.remove("board");
		after.remove("board");
		assertNull(update(reveals, before, "red", "Crab Legs"));
		assertNull(update(reveals, after, "red", "Crab Legs"));
	}

	@Test
	public void offBoardTasksStillReveal()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		JsonObject before = event("red");
		extraTask(before, false);
		JsonObject after = event("red");
		extraTask(after, true);
		update(reveals, before, "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, after, "red", "Crab Legs");
		assertEquals(Collections.singletonList("t9"), moment.tiles);
		assertTrue(moment.lines.isEmpty());
		assertEquals(Collections.singletonList("t9"), moment.board.unplaced);
	}

	private static void extraTask(JsonObject event, boolean completed)
	{
		JsonObject task = new JsonObject();
		task.addProperty("id", "t9");
		task.addProperty("title", "Task 9");
		task.addProperty("points", 2);
		event.getAsJsonArray("tasks").add(task);
		JsonObject row = new JsonObject();
		row.addProperty("taskId", "t9");
		row.addProperty("completedBy", "Crab Legs");
		row.addProperty("completed", completed);
		event.getAsJsonArray("teamScores").get(0).getAsJsonObject().getAsJsonArray("tasks").add(row);
	}

	@Test
	public void lineClearTimingIsInOrder()
	{
		TsgHubReveals reveals = new TsgHubReveals();
		update(reveals, event("red", "t1", "t2"), "red", "Crab Legs");
		TsgHubReveals.Moment moment = update(reveals, event("red", "t0", "t1", "t2"), "red", "Crab Legs");
		assertEquals("ROW:0", moment.lines.get(0).key());
		assertTrue(TsgHubRevealOverlay.lineAt(moment, 0) >= TsgHubRevealOverlay.strikesAt(moment));
		assertTrue(TsgHubRevealOverlay.strikeAt(moment, 0) > TsgHubRevealOverlay.lineAt(moment, 0));
		assertTrue(TsgHubRevealOverlay.stampAt(moment) > TsgHubRevealOverlay.strikeAt(moment, 0));
		assertTrue(TsgHubRevealOverlay.duration(moment) > TsgHubRevealOverlay.stampAt(moment));
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

	@Test
	public void malformedItemConfigHasNoIcon()
	{
		JsonObject task = task("drop", "itemGroup", "");
		JsonArray groups = new JsonArray();
		JsonArray first = new JsonArray();
		first.add(4151);
		groups.add(first);
		JsonArray ids = new JsonArray();
		ids.add("abc");
		task.getAsJsonObject("config").add("itemGroups", groups);
		task.getAsJsonObject("config").add("itemIds", ids);
		assertEquals(0, TsgHubTileIcons.itemFor(task));
	}

	@Test
	public void viewSummarizesTheTeamsBoard()
	{
		TsgHubBingoView view = TsgHubBingoView.of(event("red", "t0", "t1", "t2", "t4"), "red");
		assertEquals("e1:red", view.key);
		assertEquals(5, view.left());
		assertEquals(1, view.lines.size());
		assertEquals(4, view.done.size());
		assertEquals(1, view.cellOf("t4")[0]);
		assertEquals(1, view.cellOf("t4")[1]);
		JsonObject list = event("red");
		list.remove("board");
		assertNull(TsgHubBingoView.of(list, "red"));
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

	private static TsgHubReveals.Moment update(TsgHubReveals reveals, JsonObject event, String teamId, String playerName)
	{
		return reveals.update(teamId == null || teamId.isEmpty() ? null : TsgHubBingoView.of(event, teamId), playerName);
	}
}
