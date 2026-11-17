package com.tsghub;

import com.google.gson.*;
import java.util.*;
import static com.tsghub.TsgHubJson.*;

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
		key = str(event, "id") + ":" + teamId;
		name = str(event, "name");
		board = TsgHubBingoBoard.of(event);
		Map<String, JsonObject> byId = new LinkedHashMap<>();
		for (JsonObject task : objects(array(event, "tasks"))) byId.put(str(task, "id"), task);
		JsonObject score = TsgHubEvents.scoreFor(array(event, "teamScores"), teamId);
		Map<String, JsonObject> rows = new HashMap<>();
		Set<String> completed = new HashSet<>();
		for (JsonObject row : objects(array(score, "tasks")))
		{
			String taskId = str(row, "taskId");
			rows.put(taskId, row);
			if (bool(row, "completed")) completed.add(taskId);
		}
		tasks = Collections.unmodifiableMap(byId);
		progress = Collections.unmodifiableMap(rows);
		done = Collections.unmodifiableSet(completed);
		List<TsgHubBingoBoard.Line> found = new ArrayList<>();
		for (String line : strings(array(score, "lineKeys"))) found.add(TsgHubBingoBoard.Line.of(line));
		lines = Collections.unmodifiableList(found);
		points = integer(score, "points", 0);
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
}
