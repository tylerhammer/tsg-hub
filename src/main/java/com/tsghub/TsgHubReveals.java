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
	static final class Moment
	{
		final String eventName;
		final TsgHubBingoBoard board;
		final Map<String, JsonObject> tasks;
		final Map<String, JsonObject> progress;
		final Set<String> before;
		final List<String> tiles;
		final List<TsgHubBingoBoard.Line> lines;

		Moment(String eventName, TsgHubBingoBoard board, Map<String, JsonObject> tasks, Map<String, JsonObject> progress, Set<String> before, List<String> tiles, List<TsgHubBingoBoard.Line> lines)
		{
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
			return board.bonusFor(lines);
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

	Moment update(JsonObject event, String teamId, String playerName)
	{
		if (event == null || teamId == null || teamId.isEmpty())
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
			List<TsgHubBingoBoard.Line> newLines = new ArrayList<>();
			for (TsgHubBingoBoard.Line line : complete)
			{
				if (!lines.contains(line.key()) && touches(board, line, finished)) newLines.add(line);
			}
			if (!finished.isEmpty())
			{
				Set<String> before = new HashSet<>(nowDone);
				before.removeAll(finished);
				moment = new Moment(TsgHubUi.str(event, "name"), board, byId, progress, before, finished, newLines);
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
