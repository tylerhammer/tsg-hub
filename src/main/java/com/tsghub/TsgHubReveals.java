package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TsgHubReveals
{
	static final int MAX_DAUBERS = 3;
	static final int MAX_WAVES = 2;

	static final class Moment
	{
		final String key;
		final String eventName;
		final TsgHubBingoBoard board;
		final Map<String, JsonObject> tasks;
		final Map<String, JsonObject> progress;
		final Set<String> before;
		final List<String> tiles;
		final List<TsgHubBingoBoard.Line> lines;
		final List<String> hiddenTiles;
		final List<TsgHubBingoBoard.Line> hiddenLines;

		Moment(String eventName, TsgHubBingoBoard board, Map<String, JsonObject> tasks, Map<String, JsonObject> progress, Set<String> before, List<String> tiles, List<TsgHubBingoBoard.Line> lines)
		{
			this("", eventName, board, tasks, progress, before, tiles, lines, Collections.emptyList(), Collections.emptyList());
		}

		Moment(String key, String eventName, TsgHubBingoBoard board, Map<String, JsonObject> tasks, Map<String, JsonObject> progress, Set<String> before, List<String> tiles,
			List<TsgHubBingoBoard.Line> lines, List<String> hiddenTiles, List<TsgHubBingoBoard.Line> hiddenLines)
		{
			this.key = key;
			this.hiddenTiles = hiddenTiles;
			this.hiddenLines = hiddenLines;
			this.eventName = eventName;
			this.board = board;
			this.tasks = tasks;
			this.progress = progress;
			this.before = before;
			this.tiles = tiles;
			this.lines = lines;
		}

		int bonus()
		{
			return board.bonusFor(lines) + board.bonusFor(hiddenLines);
		}

		int lineCount()
		{
			return lines.size() + hiddenLines.size();
		}

		int hiddenPoints()
		{
			int points = 0;
			for (String id : hiddenTiles) points += TsgHubUi.integer(tasks.get(id), "points", 1);
			return points;
		}

		boolean mergesWith(Moment other)
		{
			return other != null && !key.isEmpty() && key.equals(other.key);
		}

		static Moment merge(Moment earlier, Moment later)
		{
			List<String> tiles = union(earlier.tiles, later.tiles);
			List<String> hidden = union(earlier.hiddenTiles, later.hiddenTiles);
			tiles.removeAll(hidden);
			Set<String> before = new HashSet<>(earlier.before);
			for (String id : later.before) if (!tiles.contains(id) && !hidden.contains(id)) before.add(id);
			return new Moment(later.key, later.eventName, later.board, later.tasks, later.progress, before, tiles,
				unionLines(earlier.lines, later.lines), hidden, unionLines(earlier.hiddenLines, later.hiddenLines));
		}

		Moment capped()
		{
			if (tiles.size() <= MAX_DAUBERS && lines.size() <= MAX_WAVES) return this;
			List<String> ranked = new ArrayList<>(tiles);
			ranked.sort((a, b) -> {
				boolean lineA = inLine(a), lineB = inLine(b);
				if (lineA != lineB) return lineA ? -1 : 1;
				int points = TsgHubUi.integer(tasks.get(b), "points", 1) - TsgHubUi.integer(tasks.get(a), "points", 1);
				return points != 0 ? points : tiles.indexOf(a) - tiles.indexOf(b);
			});
			List<String> shown = new ArrayList<>();
			List<String> hidden = new ArrayList<>(hiddenTiles);
			for (String id : tiles)
			{
				if (ranked.indexOf(id) < MAX_DAUBERS) shown.add(id);
				else hidden.add(id);
			}
			List<TsgHubBingoBoard.Line> waves = new ArrayList<>();
			List<TsgHubBingoBoard.Line> quiet = new ArrayList<>(hiddenLines);
			for (TsgHubBingoBoard.Line line : lines) if (waves.size() < MAX_WAVES && covers(line, shown)) waves.add(line);
			for (TsgHubBingoBoard.Line line : lines) if (!waves.contains(line) && waves.size() < MAX_WAVES) waves.add(line);
			for (TsgHubBingoBoard.Line line : lines) if (!waves.contains(line)) quiet.add(line);
			Set<String> daubed = new HashSet<>(before);
			daubed.addAll(hidden);
			return new Moment(key, eventName, board, tasks, progress, daubed, shown, waves, hidden, quiet);
		}

		private boolean inLine(String id)
		{
			for (TsgHubBingoBoard.Line line : lines) if (covers(line, Collections.singletonList(id))) return true;
			return false;
		}

		private boolean covers(TsgHubBingoBoard.Line line, List<String> ids)
		{
			return touches(board, line, ids);
		}

		private static List<String> union(List<String> a, List<String> b)
		{
			List<String> out = new ArrayList<>(a);
			for (String id : b) if (!out.contains(id)) out.add(id);
			return out;
		}

		private static List<TsgHubBingoBoard.Line> unionLines(List<TsgHubBingoBoard.Line> a, List<TsgHubBingoBoard.Line> b)
		{
			List<TsgHubBingoBoard.Line> out = new ArrayList<>(a);
			for (TsgHubBingoBoard.Line line : b) if (!out.contains(line)) out.add(line);
			return out;
		}

		static Moment preview(JsonObject event)
		{
			TsgHubBingoBoard board = TsgHubBingoBoard.of(event);
			Map<String, JsonObject> tasks = tasks(event);
			for (int row = 0; row < board.size; row++)
			{
				for (int col = 0; col < board.size; col++)
				{
					String id = board.taskAt(row, col);
					if (id == null) continue;
					Set<String> before = new HashSet<>();
					boolean full = true;
					for (int c = 0; c < board.size; c++)
					{
						String other = board.taskAt(row, c);
						if (other == null) full = false;
						else if (!other.equals(id)) before.add(other);
					}
					List<TsgHubBingoBoard.Line> lines = full ? Collections.singletonList(new TsgHubBingoBoard.Line(TsgHubBingoBoard.Direction.ROW, row)) : Collections.emptyList();
					return new Moment(TsgHubUi.str(event, "name"), board, tasks, Collections.emptyMap(), before, Collections.singletonList(id), lines);
				}
			}
			return null;
		}
	}

	private String key;
	private Set<String> done;
	private Set<String> lines;
	private Set<String> mine;

	synchronized void reset()
	{
		key = null;
	}

	synchronized Moment update(JsonObject event, String teamId, String playerName)
	{
		if (event == null || teamId == null || teamId.isEmpty() || !TsgHubBingoBoard.hasBoard(event))
		{
			key = null;
			return null;
		}
		JsonArray tasks = TsgHubUi.array(event, "tasks");
		JsonArray progressRows = TsgHubUi.array(TsgHubUi.scoreFor(TsgHubUi.array(event, "teamScores"), teamId), "tasks");
		Set<String> nowDone = new HashSet<>();
		Set<String> nowMine = new HashSet<>();
		Map<String, JsonObject> progress = new HashMap<>();
		for (int i = 0; i < progressRows.size(); i++)
		{
			JsonObject row = progressRows.get(i).getAsJsonObject();
			String taskId = TsgHubUi.str(row, "taskId");
			progress.put(taskId, row);
			if (TsgHubUi.bool(row, "completed")) nowDone.add(taskId);
			JsonArray members = TsgHubUi.array(row, "members");
			for (int m = 0; m < members.size(); m++)
			{
				JsonObject member = members.get(m).getAsJsonObject();
				if (TsgHubUi.bool(member, "completed") && TsgHubUi.samePlayer(TsgHubUi.str(member, "displayName"), playerName)) nowMine.add(taskId);
			}
		}
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event);
		List<TsgHubBingoBoard.Line> complete = board.completedLines(nowDone);
		Set<String> nowLines = new HashSet<>();
		for (TsgHubBingoBoard.Line line : complete) nowLines.add(line.key());
		String nextKey = TsgHubUi.str(event, "id") + ":" + teamId;
		Moment moment = null;
		if (nextKey.equals(key))
		{
			Map<String, JsonObject> byId = tasks(event);
			List<String> finished = new ArrayList<>();
			for (int row = 0; row < board.size; row++)
			{
				for (int col = 0; col < board.size; col++)
				{
					String id = board.taskAt(row, col);
					if (id != null && byMe(byId.get(id), progress.get(id), nowDone, nowMine, playerName)) finished.add(id);
				}
			}
			for (String id : board.unplaced) if (byMe(byId.get(id), progress.get(id), nowDone, nowMine, playerName)) finished.add(id);
			List<TsgHubBingoBoard.Line> newLines = new ArrayList<>();
			for (TsgHubBingoBoard.Line line : complete)
			{
				if (!lines.contains(line.key()) && touches(board, line, finished)) newLines.add(line);
			}
			if (!finished.isEmpty())
			{
				Set<String> before = new HashSet<>(nowDone);
				before.removeAll(finished);
				moment = new Moment(nextKey, TsgHubUi.str(event, "name"), board, byId, progress, before, finished, newLines, Collections.emptyList(), Collections.emptyList());
			}
		}
		key = nextKey;
		done = nowDone;
		lines = nowLines;
		mine = nowMine;
		return moment;
	}

	private boolean byMe(JsonObject task, JsonObject progress, Set<String> nowDone, Set<String> nowMine, String playerName)
	{
		String id = TsgHubUi.str(task, "id");
		if (task == null || !nowDone.contains(id) || done.contains(id)) return false;
		if ("individual".equals(TsgHubUi.str(task, "scope"))) return nowMine.contains(id) && !mine.contains(id);
		return !playerName.isEmpty() && TsgHubUi.samePlayer(TsgHubUi.str(progress, "completedBy"), playerName);
	}

	private static Map<String, JsonObject> tasks(JsonObject event)
	{
		Map<String, JsonObject> tasks = new HashMap<>();
		JsonArray list = TsgHubUi.array(event, "tasks");
		for (int i = 0; i < list.size(); i++) tasks.put(TsgHubUi.str(list.get(i).getAsJsonObject(), "id"), list.get(i).getAsJsonObject());
		return tasks;
	}

	private static boolean touches(TsgHubBingoBoard board, TsgHubBingoBoard.Line line, List<String> tasks)
	{
		for (int i = 0; i < board.size; i++)
		{
			int[] at = line.cell(i, board.size);
			if (tasks.contains(board.taskAt(at[0], at[1]))) return true;
		}
		return false;
	}
}
