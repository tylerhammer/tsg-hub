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
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class BingoBoardTest
{
	@Test
	public void oldEventWithoutBoardFillsTasksInOrder()
	{
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event(5));
		assertTrue(board.auto);
		assertEquals(3, board.size);
		assertEquals("t0", board.taskAt(0, 0));
		assertEquals("t3", board.taskAt(1, 0));
		assertEquals("t4", board.taskAt(1, 1));
		assertNull(board.taskAt(2, 2));
		assertEquals(4, board.emptyCells());
		assertTrue(board.unplaced.isEmpty());
	}

	@Test
	public void defaultSizeFitsTasks()
	{
		assertEquals(3, TsgHubBingoBoard.defaultSize(0));
		assertEquals(3, TsgHubBingoBoard.defaultSize(9));
		assertEquals(4, TsgHubBingoBoard.defaultSize(10));
		assertEquals(5, TsgHubBingoBoard.defaultSize(25));
		assertEquals(9, TsgHubBingoBoard.defaultSize(500));
	}

	@Test
	public void savedLayoutIsUsedAndRepaired()
	{
		JsonObject event = event(4);
		JsonObject board = new JsonObject();
		board.addProperty("type", "grid");
		board.addProperty("size", 3);
		board.addProperty("mode", "shared");
		board.addProperty("locked", true);
		board.add("tiles", tiles(tile("t0", 2, 2), tile("gone", 0, 0), tile("t1", 2, 2), tile("t2", 7, 0)));
		event.add("board", board);
		TsgHubBingoBoard resolved = TsgHubBingoBoard.of(event);
		assertFalse(resolved.auto);
		assertTrue(resolved.locked);
		assertEquals("t0", resolved.taskAt(2, 2));
		assertEquals("t1", resolved.taskAt(0, 0));
		assertEquals("t2", resolved.taskAt(0, 1));
		assertEquals("t3", resolved.taskAt(0, 2));
	}

	@Test
	public void tasksThatDoNotFitAreUnplaced()
	{
		JsonObject event = event(11);
		JsonObject board = new JsonObject();
		board.addProperty("size", 3);
		event.add("board", board);
		assertEquals(Arrays.asList("t9", "t10"), TsgHubBingoBoard.of(event).unplaced);
	}

	@Test
	public void teamLayoutWinsForThatTeam()
	{
		JsonObject event = event(2);
		JsonObject board = new JsonObject();
		board.addProperty("size", 3);
		board.addProperty("mode", "team");
		board.add("tiles", tiles(tile("t0", 0, 0), tile("t1", 0, 1)));
		JsonObject teams = new JsonObject();
		teams.add("red", tiles(tile("t0", 2, 2), tile("t1", 1, 1)));
		board.add("teams", teams);
		event.add("board", board);
		assertEquals("t0", TsgHubBingoBoard.of(event, "red").taskAt(2, 2));
		assertEquals("t0", TsgHubBingoBoard.of(event, "blue").taskAt(0, 0));
		assertEquals("team", TsgHubBingoBoard.of(event).mode);
	}

	@Test
	public void detectsRowsColumnsAndDiagonals()
	{
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event(9));
		assertEquals(Collections.emptyList(), board.completedLines(done()));
		assertEquals(keys("ROW:0"), keys(board.completedLines(done("t0", "t1", "t2"))));
		assertEquals(keys("COLUMN:1"), keys(board.completedLines(done("t1", "t4", "t7"))));
		assertEquals(keys("DIAGONAL:0"), keys(board.completedLines(done("t0", "t4", "t8"))));
		assertEquals(keys("ANTI_DIAGONAL:0"), keys(board.completedLines(done("t2", "t4", "t6"))));
		assertEquals(keys("ROW:0", "COLUMN:0", "DIAGONAL:0", "ANTI_DIAGONAL:0"), keys(board.completedLines(done("t0", "t1", "t2", "t3", "t6", "t4", "t8"))));
		assertEquals(keys("ROW:0", "COLUMN:0"), keys(board.completedLines(done("t0", "t1", "t2", "t3", "t6"))));
		assertEquals(8, board.completedLines(done("t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8")).size());
	}

	@Test
	public void emptyCellsBlockLines()
	{
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event(8));
		assertEquals(keys("ROW:0", "ROW:1", "COLUMN:0", "COLUMN:1", "ANTI_DIAGONAL:0"),
			keys(board.completedLines(done("t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7"))));
	}

	@Test
	public void swapMovesTilesAndKeepsJsonInSync()
	{
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event(4)).swap(0, 0, 2, 2);
		assertEquals("t0", board.taskAt(2, 2));
		assertNull(board.taskAt(0, 0));
		assertFalse(board.auto);
		JsonArray tiles = board.tiles();
		assertEquals(4, tiles.size());
		JsonObject last = tiles.get(3).getAsJsonObject();
		assertEquals("t0", last.get("taskId").getAsString());
		assertEquals(2, last.get("row").getAsInt());
		assertEquals(2, last.get("col").getAsInt());
	}

	@Test
	public void lineBonusesAddStaticAndRevealedRandom()
	{
		JsonObject event = event(9);
		JsonObject board = new JsonObject();
		board.addProperty("size", 3);
		board.addProperty("lineBonus", 5);
		board.addProperty("randomMin", 1);
		board.addProperty("randomMax", 9);
		JsonObject bonuses = new JsonObject();
		bonuses.addProperty("ROW:0", 7);
		board.add("bonuses", bonuses);
		event.add("board", board);
		TsgHubBingoBoard resolved = TsgHubBingoBoard.of(event);
		List<TsgHubBingoBoard.Line> lines = resolved.completedLines(done("t0", "t1", "t2", "t3", "t6"));
		assertEquals(2, lines.size());
		assertEquals(17, resolved.bonusFor(lines));
		assertEquals("Each line: +5, plus a hidden 1 to 9 pts", resolved.bonusRule());
		assertEquals(Integer.valueOf(7), resolved.rolledBonus(new TsgHubBingoBoard.Line(TsgHubBingoBoard.Direction.ROW, 0)));
		assertNull(resolved.rolledBonus(new TsgHubBingoBoard.Line(TsgHubBingoBoard.Direction.COLUMN, 0)));
		assertEquals(8, resolved.allLines().size());
		assertEquals("Row 1", resolved.allLines().get(0).label());
		assertEquals(17, resolved.swap(0, 0, 1, 1).bonusFor(lines));
	}

	@Test
	public void noBonusByDefault()
	{
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event(9));
		assertFalse(board.hasBonus());
		assertEquals("", board.bonusRule());
		assertEquals(0, board.bonusFor(board.completedLines(done("t0", "t1", "t2"))));
	}

	@Test
	public void iconsComeFromItemTasks()
	{
		JsonObject set = new JsonObject();
		set.addProperty("type", "drop");
		JsonObject config = new JsonObject();
		JsonArray groups = new JsonArray();
		JsonArray group = new JsonArray();
		JsonObject item = new JsonObject();
		item.addProperty("id", 4716);
		group.add(item);
		groups.add(group);
		config.add("itemGroups", groups);
		set.add("config", config);
		assertEquals(4716, TsgHubBoardGrid.iconItem(set));
		JsonObject manual = new JsonObject();
		manual.addProperty("type", "manual");
		assertEquals(0, TsgHubBoardGrid.iconItem(manual));
	}

	private static JsonObject event(int tasks)
	{
		JsonObject event = new JsonObject();
		JsonArray list = new JsonArray();
		for (int i = 0; i < tasks; i++)
		{
			JsonObject task = new JsonObject();
			task.addProperty("id", "t" + i);
			task.addProperty("title", "Task " + i);
			list.add(task);
		}
		event.add("tasks", list);
		return event;
	}

	private static JsonObject tile(String taskId, int row, int col)
	{
		JsonObject tile = new JsonObject();
		tile.addProperty("taskId", taskId);
		tile.addProperty("row", row);
		tile.addProperty("col", col);
		return tile;
	}

	private static JsonArray tiles(JsonObject... tiles)
	{
		JsonArray array = new JsonArray();
		for (JsonObject tile : tiles) array.add(tile);
		return array;
	}

	private static Set<String> done(String... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}

	private static Set<String> keys(String... keys)
	{
		return new HashSet<>(Arrays.asList(keys));
	}

	private static Set<String> keys(List<TsgHubBingoBoard.Line> lines)
	{
		Set<String> keys = new HashSet<>();
		for (TsgHubBingoBoard.Line line : lines) keys.add(line.key());
		return keys;
	}
}
