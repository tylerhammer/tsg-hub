package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TsgHubBingoView
{
	final JsonObject event;
	final String key;
	final String name;
	final TsgHubBingoBoard board;
	final Map<String, JsonObject> tasks;
	final Map<String, JsonObject> progress;
	final Set<String> done;
	final List<TsgHubBingoBoard.Line> lines;
	final int points;

	private TsgHubBingoView(JsonObject event, String teamId)
	{
		this.event = event;
		key = TsgHubJson.str(event, "id") + ":" + teamId;
		name = TsgHubJson.str(event, "name");
		board = TsgHubBingoBoard.of(event);
		Map<String, JsonObject> byId = new LinkedHashMap<>();
		for (JsonObject task : TsgHubJson.objects(TsgHubJson.array(event, "tasks"))) byId.put(TsgHubJson.str(task, "id"), task);
		JsonObject score = TsgHubEvents.scoreFor(TsgHubJson.array(event, "teamScores"), teamId);
		Map<String, JsonObject> rows = new HashMap<>();
		Set<String> completed = new HashSet<>();
		for (JsonObject row : TsgHubJson.objects(TsgHubJson.array(score, "tasks")))
		{
			String taskId = TsgHubJson.str(row, "taskId");
			rows.put(taskId, row);
			if (TsgHubJson.bool(row, "completed")) completed.add(taskId);
		}
		tasks = Collections.unmodifiableMap(byId);
		progress = Collections.unmodifiableMap(rows);
		done = Collections.unmodifiableSet(completed);
		lines = Collections.unmodifiableList(board.completedLines(completed));
		points = TsgHubJson.integer(score, "points", 0);
	}

	static TsgHubBingoView of(JsonObject event, String teamId)
	{
		if (event == null || !TsgHubBingoBoard.hasBoard(event)) return null;
		return new TsgHubBingoView(event, teamId == null ? "" : teamId);
	}

	int left()
	{
		int left = 0;
		for (String id : tasks.keySet()) if (!done.contains(id)) left++;
		return left;
	}

	int[] cellOf(String id)
	{
		for (int row = 0; row < board.size; row++)
			for (int col = 0; col < board.size; col++)
				if (id.equals(board.taskAt(row, col))) return new int[]{row, col};
		return null;
	}
}
