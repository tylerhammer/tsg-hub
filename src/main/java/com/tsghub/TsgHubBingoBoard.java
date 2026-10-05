package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TsgHubBingoBoard
{
	static final int MIN_SIZE = 3;
	static final int MAX_SIZE = 9;

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

	final String type;
	final int size;
	final String mode;
	final boolean auto;
	final boolean locked;
	private final String[][] cells;
	final List<String> unplaced;

	private TsgHubBingoBoard(String type, int size, String mode, boolean auto, boolean locked, String[][] cells, List<String> unplaced)
	{
		this.type = type;
		this.size = size;
		this.mode = mode;
		this.auto = auto;
		this.locked = locked;
		this.cells = cells;
		this.unplaced = Collections.unmodifiableList(unplaced);
	}

	static int defaultSize(int tasks)
	{
		int size = (int) Math.ceil(Math.sqrt(tasks));
		return Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
	}

	static TsgHubBingoBoard of(JsonObject event)
	{
		return of(event, "");
	}

	static TsgHubBingoBoard of(JsonObject event, String teamId)
	{
		List<String> taskIds = new ArrayList<>();
		JsonArray tasks = TsgHubUi.array(event, "tasks");
		for (int i = 0; i < tasks.size(); i++) taskIds.add(TsgHubUi.str(tasks.get(i).getAsJsonObject(), "id"));
		JsonObject board = event != null && event.has("board") && event.get("board").isJsonObject() ? event.getAsJsonObject("board") : null;
		if (board == null) return resolve("grid", defaultSize(taskIds.size()), "shared", true, false, new JsonArray(), taskIds);
		int size = TsgHubUi.integer(board, "size", 0);
		if (size < MIN_SIZE || size > MAX_SIZE) size = defaultSize(taskIds.size());
		JsonArray tiles = TsgHubUi.array(board, "tiles");
		if (!teamId.isEmpty() && board.has("teams") && board.get("teams").isJsonObject())
		{
			JsonElement own = board.getAsJsonObject("teams").get(teamId);
			if (own != null && own.isJsonArray()) tiles = own.getAsJsonArray();
		}
		String type = TsgHubUi.str(board, "type");
		return resolve(type.isEmpty() ? "grid" : type, size, "team".equals(TsgHubUi.str(board, "mode")) ? "team" : "shared",
			TsgHubUi.bool(board, "auto"), TsgHubUi.bool(board, "locked"), tiles, taskIds);
	}

	private static TsgHubBingoBoard resolve(String type, int size, String mode, boolean auto, boolean locked, JsonArray tiles, List<String> taskIds)
	{
		String[][] cells = new String[size][size];
		Set<String> known = new HashSet<>(taskIds);
		Set<String> placed = new HashSet<>();
		for (int i = 0; i < tiles.size(); i++)
		{
			if (!tiles.get(i).isJsonObject()) continue;
			JsonObject tile = tiles.get(i).getAsJsonObject();
			String id = TsgHubUi.str(tile, "taskId");
			int row = TsgHubUi.integer(tile, "row", -1);
			int col = TsgHubUi.integer(tile, "col", -1);
			if (!known.contains(id) || placed.contains(id) || row < 0 || col < 0 || row >= size || col >= size || cells[row][col] != null) continue;
			cells[row][col] = id;
			placed.add(id);
		}
		List<String> unplaced = new ArrayList<>();
		int next = 0;
		for (String id : taskIds)
		{
			if (placed.contains(id)) continue;
			while (next < size * size && cells[next / size][next % size] != null) next++;
			if (next >= size * size)
			{
				unplaced.add(id);
				continue;
			}
			cells[next / size][next % size] = id;
			placed.add(id);
		}
		return new TsgHubBingoBoard(type, size, mode, auto, locked, cells, unplaced);
	}

	String taskAt(int row, int col)
	{
		if (row < 0 || col < 0 || row >= size || col >= size) return null;
		return cells[row][col];
	}

	int emptyCells()
	{
		int empty = 0;
		for (String[] row : cells) for (String id : row) if (id == null) empty++;
		return empty;
	}

	List<Line> completedLines(Set<String> done)
	{
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < size; i++)
		{
			lines.add(new Line(Direction.ROW, i));
			lines.add(new Line(Direction.COLUMN, i));
		}
		lines.add(new Line(Direction.DIAGONAL, 0));
		lines.add(new Line(Direction.ANTI_DIAGONAL, 0));
		List<Line> complete = new ArrayList<>();
		for (Line line : lines) if (full(line, done)) complete.add(line);
		return complete;
	}

	private boolean full(Line line, Set<String> done)
	{
		for (int i = 0; i < size; i++)
		{
			int[] at = line.cell(i, size);
			String id = cells[at[0]][at[1]];
			if (id == null || !done.contains(id)) return false;
		}
		return true;
	}

	TsgHubBingoBoard swap(int row, int col, int otherRow, int otherCol)
	{
		String[][] copy = new String[size][];
		for (int i = 0; i < size; i++) copy[i] = cells[i].clone();
		String moving = copy[row][col];
		copy[row][col] = copy[otherRow][otherCol];
		copy[otherRow][otherCol] = moving;
		return new TsgHubBingoBoard(type, size, mode, false, locked, copy, new ArrayList<>(unplaced));
	}

	JsonArray tiles()
	{
		JsonArray tiles = new JsonArray();
		for (int row = 0; row < size; row++)
		{
			for (int col = 0; col < size; col++)
			{
				if (cells[row][col] == null) continue;
				JsonObject tile = new JsonObject();
				tile.addProperty("taskId", cells[row][col]);
				tile.addProperty("row", row);
				tile.addProperty("col", col);
				tiles.add(tile);
			}
		}
		return tiles;
	}
}
