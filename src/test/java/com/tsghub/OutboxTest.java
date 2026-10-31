package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class OutboxTest
{
	private final List<JsonObject> socketSent = new ArrayList<>();
	private final List<JsonObject> posted = new ArrayList<>();
	private final List<String> tokens = new ArrayList<>();
	private final List<String> methods = new ArrayList<>();
	private final List<Object[]> rejections = new ArrayList<>();
	private boolean live = true;
	private int protocol = 1;
	private int status = 200;
	private long now = 1_000;

	private TsgHubOutbox reports()
	{
		TsgHubOutbox.Link link = new TsgHubOutbox.Link()
		{
			@Override
			public boolean send(String type, JsonObject body)
			{
				if (!live) return false;
				JsonObject message = body.deepCopy();
				message.addProperty("type", type);
				socketSent.add(message);
				return true;
			}

			@Override
			public boolean isLive()
			{
				return live;
			}

			@Override
			public int protocol()
			{
				return live ? protocol : 0;
			}
		};
		return new TsgHubOutbox(link, (method, path, token, body) -> {
			methods.add(method);
			posted.add(body.deepCopy());
			tokens.add(token);
			if (status == -1) throw new IOException("offline");
			if (status != 200) throw new TsgHubApi.HttpError(status, "HTTP " + status);
		}, () -> now);
	}

	private static JsonObject drop()
	{
		JsonObject body = new JsonObject();
		body.addProperty("message", "Bob received a drop: Abyssal whip");
		return body;
	}

	@Test
	public void ackRemovesPending()
	{
		TsgHubOutbox reports = reports();
		assertTrue(reports.report("broadcast", "/v1/broadcasts", drop()));
		String id = socketSent.get(0).get("id").getAsString();
		assertEquals("broadcast", socketSent.get(0).get("type").getAsString());
		reports.acked(id);
		assertEquals(0, reports.size());
		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertTrue(posted.isEmpty());
	}

	@Test
	public void unackedFallsBackToRestWithSameId()
	{
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		String id = socketSent.get(0).get("id").getAsString();
		reports.flush();
		assertTrue(posted.isEmpty());

		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals(id, posted.get(0).get("id").getAsString());
		assertFalse(posted.get(0).has("type"));
		assertEquals(0, reports.size());
	}

	@Test
	public void offlineSocketPostsImmediately()
	{
		live = false;
		TsgHubOutbox reports = reports();
		assertFalse(reports.report("group", "/v1/group-content", drop()));
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals(0, reports.size());
	}

	@Test
	public void finalErrorDrops()
	{
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		reports.failed(socketSent.get(0).get("id").getAsString(), 400, "");
		assertEquals(0, reports.size());

		live = false;
		status = 403;
		reports.report("broadcast", "/v1/broadcasts", drop());
		reports.flush();
		assertEquals(0, reports.size());
	}

	@Test
	public void rateLimitAndServerErrorsRetry()
	{
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		String id = socketSent.get(0).get("id").getAsString();
		reports.failed(id, 429, "");
		assertEquals(1, reports.size());

		now += TsgHubOutbox.RETRY_MILLIS;
		status = 429;
		reports.flush();
		assertEquals(1, reports.size());
		status = 500;
		reports.flush();
		assertEquals(1, posted.size());
		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertEquals(1, reports.size());
		status = -1;
		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertEquals(1, reports.size());

		status = 200;
		now += TsgHubOutbox.MAX_RETRY_MILLIS;
		reports.flush();
		assertEquals(0, reports.size());
		assertTrue(posted.stream().allMatch(body -> id.equals(body.get("id").getAsString())));
	}

	@Test
	public void networkFailureStopsFlush()
	{
		live = false;
		status = -1;
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		reports.report("broadcast", "/v1/broadcasts", drop());
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals(2, reports.size());
	}

	@Test
	public void reconnectResendsOverSocket()
	{
		live = false;
		status = 500;
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		reports.flush();
		live = true;
		reports.resend();
		assertEquals(1, socketSent.size());
		assertEquals(posted.get(0).get("id"), socketSent.get(0).get("id"));
	}

	@Test
	public void expiresAfterMaxAge()
	{
		live = false;
		status = 500;
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		now += TsgHubOutbox.MAX_AGE_MILLIS + 1;
		reports.flush();
		assertEquals(0, reports.size());
		assertTrue(posted.isEmpty());
	}

	@Test
	public void capsPending()
	{
		TsgHubOutbox reports = reports();
		for (int i = 0; i < TsgHubOutbox.MAX_PENDING + 5; i++) reports.report("broadcast", "/v1/broadcasts", drop());
		assertEquals(TsgHubOutbox.MAX_PENDING, reports.size());
		reports.acked(socketSent.get(0).get("id").getAsString());
		assertEquals(TsgHubOutbox.MAX_PENDING, reports.size());
		reports.acked(socketSent.get(socketSent.size() - 1).get("id").getAsString());
		assertEquals(TsgHubOutbox.MAX_PENDING - 1, reports.size());
	}

	@Test
	public void unauthorizedTokenReportFallsBackToRest()
	{
		TsgHubOutbox reports = reports();
		reports.report("clan-rank", "/v1/events/e1/clan-rank", "member-token", drop());
		reports.failed(socketSent.get(0).get("id").getAsString(), 401, "");
		assertEquals(1, reports.size());
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals("member-token", tokens.get(0));
		assertEquals(0, reports.size());

		reports.report("broadcast", "/v1/broadcasts", drop());
		reports.failed(socketSent.get(1).get("id").getAsString(), 401, "");
		assertEquals(0, reports.size());
	}

	@Test
	public void restFallbackUsesReportMethod()
	{
		live = false;
		TsgHubOutbox reports = reports();
		reports.report("tile.claim", "/v1/events/e1/tasks/t1/claim", "member-token", drop());
		reports.report("tile.unclaim", "DELETE", "/v1/events/e1/tasks/t1/claim", "member-token", drop());
		reports.flush();
		assertEquals(2, posted.size());
		assertEquals("POST", methods.get(0));
		assertEquals("DELETE", methods.get(1));
		assertEquals(0, reports.size());
	}

	private static JsonObject signal()
	{
		JsonObject body = new JsonObject();
		body.addProperty("sourceName", "Vorkath");
		return body;
	}

	private String lastId()
	{
		return socketSent.get(socketSent.size() - 1).get("id").getAsString();
	}

	@Test
	public void answeringServerResendsUnansweredReportsOverSocket()
	{
		protocol = 2;
		TsgHubOutbox reports = reports();
		reports.report("broadcast", "/v1/broadcasts", drop());
		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertTrue(posted.isEmpty());
		assertEquals(2, socketSent.size());
		assertEquals(socketSent.get(0).get("id"), socketSent.get(1).get("id"));

		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertEquals(2, socketSent.size());
		now += TsgHubOutbox.RETRY_MILLIS;
		reports.flush();
		assertEquals(3, socketSent.size());

		live = false;
		now += TsgHubOutbox.MAX_RETRY_MILLIS;
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals(0, reports.size());
	}

	@Test
	public void rateLimitedSignalIsResentAfterBackoff()
	{
		protocol = 2;
		TsgHubOutbox outbox = reports();
		assertTrue(outbox.signal("loot", signal(), false));
		String id = lastId();
		outbox.failed(id, 429, "Too many signals. Try again shortly.");
		assertEquals(1, outbox.size());
		now += TsgHubOutbox.RETRY_MILLIS - 1;
		outbox.flush();
		assertEquals(1, socketSent.size());
		now += 1;
		outbox.flush();
		assertEquals(2, socketSent.size());
		assertEquals(id, lastId());

		outbox.failed(id, 503, "");
		now += TsgHubOutbox.retryDelay(2);
		outbox.flush();
		assertEquals(3, socketSent.size());

		outbox.acked(id);
		assertEquals(0, outbox.size());
		assertTrue(posted.isEmpty());
	}

	@Test
	public void clientErrorsDropAnyMessage()
	{
		protocol = 2;
		TsgHubOutbox outbox = reports();
		outbox.signal("chat", signal(), false);
		outbox.failed(lastId(), 400, "Unknown message type");
		outbox.signal("sale", signal(), true);
		outbox.failed(lastId(), 403, "");
		assertEquals(0, outbox.size());
	}

	@Test
	public void backoffGrowsToCap()
	{
		assertEquals(TsgHubOutbox.RETRY_MILLIS, TsgHubOutbox.retryDelay(1));
		assertEquals(TsgHubOutbox.RETRY_MILLIS * 2, TsgHubOutbox.retryDelay(2));
		assertEquals(TsgHubOutbox.RETRY_MILLIS * 4, TsgHubOutbox.retryDelay(3));
		assertEquals(TsgHubOutbox.MAX_RETRY_MILLIS, TsgHubOutbox.retryDelay(40));
	}

	@Test
	public void oldServerKeepsResendingUnackedSignals()
	{
		TsgHubOutbox outbox = reports();
		outbox.signal("loot", signal(), false);
		now += TsgHubOutbox.RETRY_MILLIS;
		outbox.flush();
		assertEquals(2, socketSent.size());
		assertTrue(posted.isEmpty());
		outbox.acked(lastId());
		assertEquals(0, outbox.size());
	}

	@Test
	public void requestsNeedAnsweringServer()
	{
		TsgHubOutbox outbox = reports();
		JsonObject split = new JsonObject();
		split.addProperty("active", true);
		assertFalse(outbox.request("group.split", split, (status, message) -> rejections.add(new Object[] {status, message})));
		assertEquals(0, outbox.size());
		assertTrue(socketSent.isEmpty());

		protocol = 2;
		assertTrue(outbox.request("group.split", split, (status, message) -> rejections.add(new Object[] {status, message})));
		assertEquals("group.split", socketSent.get(0).get("type").getAsString());
		assertTrue(socketSent.get(0).has("id"));
		outbox.acked(lastId());
		assertEquals(0, outbox.size());

		live = false;
		assertFalse(outbox.request("group.split", split, (status, message) -> { }));
		assertEquals(0, outbox.size());
	}

	@Test
	public void requestErrorsReachCaller()
	{
		protocol = 2;
		TsgHubOutbox outbox = reports();
		JsonObject split = new JsonObject();
		split.addProperty("active", true);
		outbox.request("group.split", split, (status, message) -> rejections.add(new Object[] {status, message}));
		outbox.failed(lastId(), 403, "Only the leader can do that");
		assertEquals(1, rejections.size());
		assertEquals(403, rejections.get(0)[0]);
		assertEquals("Only the leader can do that", rejections.get(0)[1]);

		outbox.request("group.split", split, (status, message) -> rejections.add(new Object[] {status, message}));
		String id = lastId();
		now += TsgHubOutbox.RETRY_MILLIS;
		outbox.flush();
		assertEquals(3, socketSent.size());
		outbox.failed(id, 400, "A loot split is already running");
		assertEquals(400, rejections.get(1)[0]);
		assertEquals(0, outbox.size());
	}

	@Test
	public void missingStatusIsFinalOnlyForAnsweringServer()
	{
		TsgHubOutbox outbox = reports();
		outbox.signal("loot", signal(), false);
		outbox.failed(lastId(), 0, "");
		assertEquals(1, outbox.size());

		protocol = 2;
		outbox.signal("loot", signal(), false);
		outbox.failed(lastId(), 0, "");
		assertEquals(1, outbox.size());
	}

	@Test
	public void reconnectToOldServerDropsRequests()
	{
		protocol = 2;
		TsgHubOutbox outbox = reports();
		outbox.request("group.split", new JsonObject(), (status, message) -> { });
		outbox.signal("loot", signal(), false);
		live = false;
		now += TsgHubOutbox.MAX_RETRY_MILLIS;
		outbox.flush();
		live = true;
		protocol = 1;
		outbox.resend();
		assertEquals(1, outbox.size());
		assertEquals("loot", socketSent.get(socketSent.size() - 1).get("type").getAsString());
	}

	@Test
	public void signalsClearLeavesReports()
	{
		TsgHubOutbox outbox = reports();
		outbox.report("broadcast", "/v1/broadcasts", drop());
		outbox.signal("loot", signal(), false);
		outbox.signal("sale", signal(), true);
		outbox.clearSignals();
		assertEquals(1, outbox.size());
	}
}
