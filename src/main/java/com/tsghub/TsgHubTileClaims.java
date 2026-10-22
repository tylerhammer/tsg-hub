package com.tsghub;

import static com.tsghub.TsgHubJson.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class TsgHubTileClaims
{
	static final int MAX_NOTE_LENGTH = 100;
	static final String FIELD = "tileClaims";

	private TsgHubTileClaims()
	{
	}

	static boolean supported(JsonObject event)
	{
		return event != null && event.has(FIELD) && event.get(FIELD).isJsonArray();
	}

	static boolean claimable(JsonObject event, String teamId, JsonObject progress)
	{
		String status = str(event, "status");
		return supported(event) && !teamId.isEmpty() && ("active".equals(status) || "scheduled".equals(status)) && !bool(progress, "completed");
	}

	static List<JsonObject> forTask(JsonObject event, String taskId)
	{
		List<JsonObject> claims = new ArrayList<>();
		if (!supported(event)) return claims;
		for (JsonObject claim : objects(array(event, FIELD)))
		{
			if (str(claim, "taskId").equals(taskId)) claims.add(claim);
		}
		claims.sort((a, b) -> Boolean.compare(bool(b, "mine"), bool(a, "mine")));
		return claims;
	}

	static JsonObject mine(JsonObject event, String taskId)
	{
		for (JsonObject claim : forTask(event, taskId))
		{
			if (bool(claim, "mine")) return claim;
		}
		return null;
	}

	static String cleanNote(String note)
	{
		String trimmed = note == null ? "" : note.trim();
		return trimmed.codePointCount(0, trimmed.length()) > MAX_NOTE_LENGTH ? null : trimmed;
	}

	static String path(String eventId, String taskId)
	{
		return "/v1/events/" + eventId + "/tasks/" + taskId + "/claim";
	}

	static JsonObject body(String eventId, String taskId, String note)
	{
		JsonObject body = new JsonObject();
		body.addProperty("eventId", eventId);
		body.addProperty("taskId", taskId);
		if (note != null) body.addProperty("note", note);
		return body;
	}

	static JsonObject withClaim(JsonObject event, String taskId, String teamId, String displayName, String note)
	{
		JsonObject next = event.deepCopy();
		JsonArray claims = new JsonArray();
		JsonObject previous = null;
		for (JsonObject claim : objects(array(event, FIELD)))
		{
			if (str(claim, "taskId").equals(taskId) && bool(claim, "mine")) previous = claim;
			else claims.add(claim.deepCopy());
		}
		if (note != null)
		{
			JsonObject claim = previous == null ? new JsonObject() : previous.deepCopy();
			claim.addProperty("taskId", taskId);
			claim.addProperty("teamId", teamId);
			claim.addProperty("displayName", displayName);
			claim.addProperty("note", note);
			claim.addProperty("mine", true);
			claims.add(claim);
		}
		next.add(FIELD, claims);
		return next;
	}
}
