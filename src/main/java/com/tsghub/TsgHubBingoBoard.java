package com.tsghub;

import com.google.gson.*;
import java.util.*;
import static com.tsghub.TsgHubJson.*;

final class TsgHubBingoBoard
{
	enum Direction { ROW, COLUMN, DIAGONAL, ANTI_DIAGONAL }

	static final class Line
	{
		final Direction direction;
		final int index;

		Line(Direction direction, int index)
		{
			this.direction = direction;
			this.index = index;
		}

		int[] cell(int i, int size)
		{
			switch (direction)
			{
				case ROW: return new int[]{index, i};
				case COLUMN: return new int[]{i, index};
				case DIAGONAL: return new int[]{i, i};
				default: return new int[]{i, size - 1 - i};
			}
		}

		static Line of(String key)
		{
			String[] parts = key.split(":");
			return new Line(Direction.valueOf(parts[0]), Integer.parseInt(parts[1]));
		}

		String key()
		{
			return direction + ":" + index;
		}

		@Override public boolean equals(Object other)
		{
			return other instanceof Line && ((Line) other).key().equals(key());
		}

		@Override public int hashCode()
		{
			return key().hashCode();
		}

		@Override public String toString()
		{
			return key();
		}
	}

	final int size;
	private final String[][] cells;
	final List<String> unplaced;
	final int lineBonus;
	final int randomMin;
	final int randomMax;
	private final Map<String, Integer> bonuses;

	private TsgHubBingoBoard(int size, String[][] cells, List<String> unplaced, int lineBonus, int randomMin, int randomMax, Map<String, Integer> bonuses)
	{
		this.lineBonus = lineBonus;
		this.randomMin = randomMin;
		this.randomMax = randomMax;
		this.bonuses = bonuses;
		this.size = size;
		this.cells = cells;
		this.unplaced = Collections.unmodifiableList(unplaced);
	}

	static boolean hasBoard(JsonObject event)
	{
		return event != null && event.has("board") && event.get("board").isJsonObject();
	}

	static TsgHubBingoBoard of(JsonObject event)
	{
		JsonObject board = hasBoard(event) ? event.getAsJsonObject("board") : new JsonObject();
		Map<String, Integer> bonuses = new HashMap<>();
		if (board.has("bonuses") && board.get("bonuses").isJsonObject())
		{
			for (Map.Entry<String, JsonElement> entry : board.getAsJsonObject("bonuses").entrySet())
			{
				if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) bonuses.put(entry.getKey(), entry.getValue().getAsInt());
			}
		}
		int size = integer(board, "size", 0);
		String[][] cells = new String[size][size];
		for (JsonObject tile : objects(array(board, "tiles"))) cells[integer(tile, "row", 0)][integer(tile, "col", 0)] = str(tile, "taskId");
		return new TsgHubBingoBoard(size, cells, strings(array(board, "unplaced")),
			Math.max(0, integer(board, "lineBonus", 0)), Math.max(0, integer(board, "randomMin", 0)), Math.max(0, integer(board, "randomMax", 0)), bonuses);
	}

	boolean hasBonus()
	{
		return lineBonus > 0 || randomMax > 0;
	}

	int bonusFor(Line line)
	{
		Integer rolled = bonuses.get(line.key());
		return lineBonus + (rolled == null ? 0 : rolled);
	}

	int bonusFor(List<Line> lines)
	{
		int total = 0;
		for (Line line : lines) total += bonusFor(line);
		return total;
	}

	String bonusRule()
	{
		if (!hasBonus()) return "";
		String random = randomMax <= 0 ? "" : randomMin == randomMax ? String.valueOf(randomMax) : randomMin + " to " + randomMax;
		if (lineBonus > 0 && !random.isEmpty()) return "Each line: +" + lineBonus + ", plus a hidden " + random + " pts";
		if (lineBonus > 0) return "Each line: +" + lineBonus + " pts";
		return "Each line: a hidden " + random + " pts";
	}

	String taskAt(int row, int col)
	{
		if (row < 0 || col < 0 || row >= size || col >= size) return null;
		return cells[row][col];
	}

	int[] cellOf(String id)
	{
		for (int row = 0; row < size; row++)
			for (int col = 0; col < size; col++)
				if (id.equals(cells[row][col])) return new int[]{row, col};
		return null;
	}
}
