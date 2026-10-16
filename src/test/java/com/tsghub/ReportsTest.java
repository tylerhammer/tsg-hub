package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ReportsTest
{
	private final List<JsonObject> socketSent = new ArrayList<>();
	private final List<JsonObject> posted = new ArrayList<>();
	private final List<String> tokens = new ArrayList<>();
	private boolean live = true;
	private int status = 200;
	private long now = 1_000;

	private TsgHubReports reports()
	{
		return new TsgHubReports((type, body) -> {
			if (!live) return false;
			JsonObject message = body.deepCopy();
			message.addProperty("type", type);
			socketSent.add(message);
			return true;
		}, (path, token, body) -> {
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
		TsgHubReports reports = reports();
		assertTrue(reports.send("drop", "/v1/drops", drop()));
		String id = socketSent.get(0).get("id").getAsString();
		assertEquals("drop", socketSent.get(0).get("type").getAsString());
		reports.acked(id);
		assertEquals(0, reports.size());
		now += TsgHubReports.RETRY_MILLIS;
		reports.flush();
		assertTrue(posted.isEmpty());
	}

	@Test
	public void unackedFallsBackToRestWithSameId()
	{
		TsgHubReports reports = reports();
		reports.send("drop", "/v1/drops", drop());
		String id = socketSent.get(0).get("id").getAsString();
		reports.flush();
		assertTrue(posted.isEmpty());

		now += TsgHubReports.RETRY_MILLIS;
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
		TsgHubReports reports = reports();
		assertFalse(reports.send("group", "/v1/group-content", drop()));
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals(0, reports.size());
	}

	@Test
	public void finalErrorDrops()
	{
		TsgHubReports reports = reports();
		reports.send("drop", "/v1/drops", drop());
		reports.failed(socketSent.get(0).get("id").getAsString(), 400);
		assertEquals(0, reports.size());

		live = false;
		status = 403;
		reports.send("drop", "/v1/drops", drop());
		reports.flush();
		assertEquals(0, reports.size());
	}

	@Test
	public void rateLimitAndServerErrorsRetry()
	{
		TsgHubReports reports = reports();
		reports.send("drop", "/v1/drops", drop());
		String id = socketSent.get(0).get("id").getAsString();
		reports.failed(id, 429);
		assertEquals(1, reports.size());

		now += TsgHubReports.RETRY_MILLIS;
		status = 429;
		reports.flush();
		assertEquals(1, reports.size());
		status = 500;
		reports.flush();
		assertEquals(1, posted.size());
		now += TsgHubReports.RETRY_MILLIS;
		reports.flush();
		assertEquals(1, reports.size());
		status = -1;
		now += TsgHubReports.RETRY_MILLIS;
		reports.flush();
		assertEquals(1, reports.size());

		status = 200;
		now += TsgHubReports.RETRY_MILLIS;
		reports.flush();
		assertEquals(0, reports.size());
		assertTrue(posted.stream().allMatch(body -> id.equals(body.get("id").getAsString())));
	}

	@Test
	public void networkFailureStopsFlush()
	{
		live = false;
		status = -1;
		TsgHubReports reports = reports();
		reports.send("drop", "/v1/drops", drop());
		reports.send("drop", "/v1/drops", drop());
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals(2, reports.size());
	}

	@Test
	public void reconnectResendsOverSocket()
	{
		live = false;
		status = 500;
		TsgHubReports reports = reports();
		reports.send("drop", "/v1/drops", drop());
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
		TsgHubReports reports = reports();
		reports.send("drop", "/v1/drops", drop());
		now += TsgHubOutbox.MAX_AGE_MILLIS + 1;
		reports.flush();
		assertEquals(0, reports.size());
		assertTrue(posted.isEmpty());
	}

	@Test
	public void capsPending()
	{
		TsgHubReports reports = reports();
		for (int i = 0; i < TsgHubOutbox.MAX_PENDING + 5; i++) reports.send("drop", "/v1/drops", drop());
		assertEquals(TsgHubOutbox.MAX_PENDING, reports.size());
		reports.acked(socketSent.get(0).get("id").getAsString());
		assertEquals(TsgHubOutbox.MAX_PENDING, reports.size());
		reports.acked(socketSent.get(socketSent.size() - 1).get("id").getAsString());
		assertEquals(TsgHubOutbox.MAX_PENDING - 1, reports.size());
	}

	@Test
	public void unauthorizedTokenReportFallsBackToRest()
	{
		TsgHubReports reports = reports();
		reports.send("clan-rank", "/v1/events/e1/clan-rank", "member-token", drop());
		reports.failed(socketSent.get(0).get("id").getAsString(), 401);
		assertEquals(1, reports.size());
		reports.flush();
		assertEquals(1, posted.size());
		assertEquals("member-token", tokens.get(0));
		assertEquals(0, reports.size());

		reports.send("drop", "/v1/drops", drop());
		reports.failed(socketSent.get(1).get("id").getAsString(), 401);
		assertEquals(0, reports.size());
	}
}
