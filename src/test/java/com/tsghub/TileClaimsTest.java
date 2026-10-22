package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class TileClaimsTest
{
	private static JsonObject json(String json)
	{
		return new JsonParser().parse(json).getAsJsonObject();
	}

	private static JsonObject board()
	{
		return json("{\"id\":\"e1\",\"status\":\"active\",\"tileClaims\":["
			+ "{\"taskId\":\"t1\",\"teamId\":\"red\",\"displayName\":\"Bob\",\"note\":\"\",\"claimedAt\":\"2026-10-01T00:00:00Z\"},"
			+ "{\"taskId\":\"t1\",\"teamId\":\"red\",\"displayName\":\"Alice\",\"note\":\"tonight\",\"claimedAt\":\"2026-10-02T00:00:00Z\",\"mine\":true},"
			+ "{\"taskId\":\"t2\",\"teamId\":\"red\",\"displayName\":\"Bob\",\"note\":\"after work\",\"claimedAt\":\"2026-10-03T00:00:00Z\"}]}");
	}

	@Test
	public void forTaskListsOwnClaimFirst()
	{
		List<JsonObject> claims = TsgHubTileClaims.forTask(board(), "t1");
		assertEquals(2, claims.size());
		assertEquals("Alice", TsgHubJson.str(claims.get(0), "displayName"));
		assertEquals("Bob", TsgHubJson.str(claims.get(1), "displayName"));
		assertEquals("tonight", TsgHubJson.str(TsgHubTileClaims.mine(board(), "t1"), "note"));
		assertNull(TsgHubTileClaims.mine(board(), "t2"));
		assertTrue(TsgHubTileClaims.forTask(board(), "t3").isEmpty());
	}

	@Test
	public void olderServersHaveNoClaims()
	{
		JsonObject event = json("{\"id\":\"e1\",\"status\":\"active\"}");
		assertFalse(TsgHubTileClaims.supported(event));
		assertTrue(TsgHubTileClaims.forTask(event, "t1").isEmpty());
		assertFalse(TsgHubTileClaims.claimable(event, "red", new JsonObject()));
	}

	@Test
	public void claimableOnlyOnOpenTilesInLiveEvents()
	{
		JsonObject open = json("{\"completed\":false}");
		assertTrue(TsgHubTileClaims.claimable(board(), "red", open));
		assertFalse(TsgHubTileClaims.claimable(board(), "", open));
		assertFalse(TsgHubTileClaims.claimable(board(), "red", json("{\"completed\":true}")));
		JsonObject scheduled = board();
		scheduled.addProperty("status", "scheduled");
		assertTrue(TsgHubTileClaims.claimable(scheduled, "red", open));
		JsonObject ended = board();
		ended.addProperty("status", "ended");
		assertFalse(TsgHubTileClaims.claimable(ended, "red", open));
	}

	@Test
	public void cleanNoteTrimsAndLimits()
	{
		assertEquals("", TsgHubTileClaims.cleanNote(null));
		assertEquals("on it", TsgHubTileClaims.cleanNote("  on it \n"));
		String longest = new String(new char[TsgHubTileClaims.MAX_NOTE_LENGTH]).replace('\0', 'a');
		assertEquals(longest, TsgHubTileClaims.cleanNote(longest));
		assertNull(TsgHubTileClaims.cleanNote(longest + "a"));
	}

	@Test
	public void bodyAndPath()
	{
		assertEquals("/v1/events/e1/tasks/t1/claim", TsgHubTileClaims.path("e1", "t1"));
		JsonObject claim = TsgHubTileClaims.body("e1", "t1", "tonight");
		assertEquals("e1", TsgHubJson.str(claim, "eventId"));
		assertEquals("t1", TsgHubJson.str(claim, "taskId"));
		assertEquals("tonight", TsgHubJson.str(claim, "note"));
		assertFalse(TsgHubTileClaims.body("e1", "t1", null).has("note"));
	}

	@Test
	public void withClaimUpdatesOnlyOwnClaim()
	{
		JsonObject original = board();
		JsonObject changed = TsgHubTileClaims.withClaim(original, "t1", "red", "Alice", "now");
		assertEquals("tonight", TsgHubJson.str(TsgHubTileClaims.mine(original, "t1"), "note"));
		JsonObject mine = TsgHubTileClaims.mine(changed, "t1");
		assertEquals("now", TsgHubJson.str(mine, "note"));
		assertEquals("2026-10-02T00:00:00Z", TsgHubJson.str(mine, "claimedAt"));
		assertEquals(2, TsgHubTileClaims.forTask(changed, "t1").size());

		JsonObject claimed = TsgHubTileClaims.withClaim(original, "t2", "red", "Alice", "");
		assertEquals(2, TsgHubTileClaims.forTask(claimed, "t2").size());
		assertEquals("Alice", TsgHubJson.str(TsgHubTileClaims.mine(claimed, "t2"), "displayName"));

		JsonObject dropped = TsgHubTileClaims.withClaim(original, "t1", "red", "Alice", null);
		assertNull(TsgHubTileClaims.mine(dropped, "t1"));
		assertEquals(1, TsgHubTileClaims.forTask(dropped, "t1").size());
		assertEquals(1, TsgHubTileClaims.forTask(dropped, "t2").size());
	}
}
