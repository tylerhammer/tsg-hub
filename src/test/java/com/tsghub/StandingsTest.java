package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class StandingsTest
{
	@Test
	public void ranksTeamsInServerOrder()
	{
		JsonObject event = event(new String[]{"a", "b", "c", "d"}, new String[]{"c", "a", "b"});
		event.getAsJsonArray("teamScores").get(1).getAsJsonObject().addProperty("points", 99);
		assertEquals(List.of("c", "a", "b", "d"), ids(TsgHubEvents.rankedTeams(event)));
	}

	@Test
	public void unscoredTeamsKeepTheirOrderAtTheEnd()
	{
		JsonObject event = event(new String[]{"z", "a", "m"}, new String[]{"m"});
		assertEquals(List.of("m", "z", "a"), ids(TsgHubEvents.rankedTeams(event)));
	}

	@Test
	public void finishedNeedsFinishedAt()
	{
		JsonObject done = new JsonObject();
		done.addProperty("finishedAt", "2026-11-18T10:00:00Z");
		assertTrue(TsgHubEvents.finished(done));
		assertFalse(TsgHubEvents.finished(new JsonObject()));
	}

	private static JsonObject event(String[] teamIds, String[] scoreIds)
	{
		JsonObject event = new JsonObject();
		JsonArray teams = new JsonArray();
		for (String id : teamIds)
		{
			JsonObject team = new JsonObject();
			team.addProperty("id", id);
			team.addProperty("name", id.toUpperCase());
			teams.add(team);
		}
		JsonArray scores = new JsonArray();
		for (String id : scoreIds)
		{
			JsonObject score = new JsonObject();
			score.addProperty("teamId", id);
			scores.add(score);
		}
		event.add("teams", teams);
		event.add("teamScores", scores);
		return event;
	}

	private static List<String> ids(List<JsonObject> teams)
	{
		List<String> ids = new ArrayList<>();
		for (JsonObject team : teams) ids.add(team.get("id").getAsString());
		return ids;
	}
}
