package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class BingoBoardTest
{
	@Test
	public void boardUsesTheServerLayout()
	{
		JsonObject event = event(11);
		JsonObject board = new JsonObject();
		board.addProperty("size", 3);
		board.add("tiles", tiles(tile("t0", 2, 2), tile("t1", 0, 0), tile("t2", 0, 1)));
		JsonArray unplaced = new JsonArray();
		unplaced.add("t9");
		unplaced.add("t10");
		board.add("unplaced", unplaced);
		event.add("board", board);
		TsgHubBingoBoard resolved = TsgHubBingoBoard.of(event);
		assertEquals(3, resolved.size);
		assertEquals("t0", resolved.taskAt(2, 2));
		assertEquals("t1", resolved.taskAt(0, 0));
		assertEquals("t2", resolved.taskAt(0, 1));
		assertNull(resolved.taskAt(1, 1));
		assertEquals(Arrays.asList("t9", "t10"), resolved.unplaced);
	}

	@Test
	public void onlyEventsWithABoardHaveOne()
	{
		JsonObject list = event(4);
		assertFalse(TsgHubBingoBoard.hasBoard(list));
		list.add("board", new JsonObject());
		assertTrue(TsgHubBingoBoard.hasBoard(list));
		assertFalse(TsgHubBingoBoard.hasBoard(null));
	}

	@Test
	public void lineKeysRoundTrip()
	{
		for (String key : Arrays.asList("ROW:2", "COLUMN:0", "DIAGONAL:0", "ANTI_DIAGONAL:0")) assertEquals(key, TsgHubBingoBoard.Line.of(key).key());
		assertEquals(TsgHubBingoBoard.Direction.COLUMN, TsgHubBingoBoard.Line.of("COLUMN:4").direction);
		assertEquals(4, TsgHubBingoBoard.Line.of("COLUMN:4").index);
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
		assertEquals(17, resolved.bonusFor(Arrays.asList(TsgHubBingoBoard.Line.of("ROW:0"), TsgHubBingoBoard.Line.of("COLUMN:0"))));
		assertEquals("Each line: +5, plus a hidden 1 to 9 pts", resolved.bonusRule());
	}

	@Test
	public void noBonusByDefault()
	{
		JsonObject event = event(9);
		event.add("board", new JsonObject());
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event);
		assertFalse(board.hasBonus());
		assertEquals("", board.bonusRule());
		assertEquals(0, board.bonusFor(Collections.singletonList(TsgHubBingoBoard.Line.of("ROW:0"))));
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
}
