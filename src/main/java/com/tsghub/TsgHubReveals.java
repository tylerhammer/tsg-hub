package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TsgHubReveals
{
	static final class Reveal
	{
		final boolean line;
		final String title;
		final String detail;
		final int points;
		final JsonObject task;

		Reveal(boolean line, String title, String detail, int points, JsonObject task)
		{
			this.line = line;
			this.title = title;
			this.detail = detail;
			this.points = points;
			this.task = task;
		}
	}

	private String key;
	private Set<String> done;
	private Set<String> lines;
	private Set<String> mine;

	List<Reveal> update(JsonObject event, String teamId, String playerName)
	{
		List<Reveal> reveals = new ArrayList<>();
		if (event == null || teamId == null || teamId.isEmpty())
		{
			key = null;
			return reveals;
		}
		JsonArray tasks = TsgHubUi.array(event, "tasks");
		JsonArray progressRows = TsgHubUi.array(TsgHubUi.scoreFor(TsgHubUi.array(event, "teamScores"), teamId), "tasks");
		Set<String> nowDone = new HashSet<>();
		Set<String> nowMine = new HashSet<>();
		for (int i = 0; i < progressRows.size(); i++)
		{
			JsonObject row = progressRows.get(i).getAsJsonObject();
			if (TsgHubUi.bool(row, "completed")) nowDone.add(TsgHubUi.str(row, "taskId"));
			JsonArray members = TsgHubUi.array(row, "members");
			for (int m = 0; m < members.size(); m++)
			{
				JsonObject member = members.get(m).getAsJsonObject();
				if (TsgHubUi.bool(member, "completed") && TsgHubUi.samePlayer(TsgHubUi.str(member, "displayName"), playerName)) nowMine.add(TsgHubUi.str(row, "taskId"));
			}
		}
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event);
		List<TsgHubBingoBoard.Line> complete = board.completedLines(nowDone);
		Set<String> nowLines = new HashSet<>();
		for (TsgHubBingoBoard.Line line : complete) nowLines.add(line.key());
		String nextKey = TsgHubUi.str(event, "id") + ":" + teamId;
		if (nextKey.equals(key))
		{
			Set<String> finished = new HashSet<>();
			for (int i = 0; i < tasks.size(); i++)
			{
				JsonObject task = tasks.get(i).getAsJsonObject();
				String id = TsgHubUi.str(task, "id");
				if (!nowDone.contains(id) || done.contains(id)) continue;
				String by = TsgHubUi.str(TsgHubUi.progressFor(progressRows, id), "completedBy");
				boolean byMe = "individual".equals(TsgHubUi.str(task, "scope"))
					? nowMine.contains(id) && !mine.contains(id)
					: !playerName.isEmpty() && TsgHubUi.samePlayer(by, playerName);
				if (!byMe) continue;
				finished.add(id);
				reveals.add(new Reveal(false, TsgHubUi.str(task, "title"), by.isEmpty() ? "" : "by " + by, TsgHubUi.integer(task, "points", 1), task));
			}
			for (TsgHubBingoBoard.Line line : complete)
			{
				if (lines.contains(line.key()) || !touches(board, line, finished)) continue;
				int count = complete.size();
				reveals.add(new Reveal(true, line.label(), count == 1 ? "Your team's first line" : count + " lines done", board.bonusFor(line), null));
			}
		}
		key = nextKey;
		done = nowDone;
		lines = nowLines;
		mine = nowMine;
		return reveals;
	}

	private static boolean touches(TsgHubBingoBoard board, TsgHubBingoBoard.Line line, Set<String> tasks)
	{
		for (int i = 0; i < board.size; i++)
		{
			int[] at = line.cell(i, board.size);
			if (tasks.contains(board.taskAt(at[0], at[1]))) return true;
		}
		return false;
	}
}
