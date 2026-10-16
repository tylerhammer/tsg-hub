package com.tsghub;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TsgHubReports
{
	static final long RETRY_MILLIS = 10_000;
	static final long MAX_AGE_MILLIS = 600_000;
	static final int MAX_PENDING = 200;

	interface Poster
	{
		void post(String path, JsonObject body) throws Exception;
	}

	private static final class Pending
	{
		final String type;
		final String path;
		final JsonObject body;
		final long createdAt;
		long nextAt;
		boolean socketTried;

		Pending(String type, String path, JsonObject body, long now)
		{
			this.type = type;
			this.path = path;
			this.body = body;
			this.createdAt = now;
			this.nextAt = now;
		}
	}

	private final BiPredicate<String, JsonObject> socket;
	private final Poster poster;
	private final LongSupplier clock;
	private final Map<String, Pending> pending = new LinkedHashMap<>();

	TsgHubReports(BiPredicate<String, JsonObject> socket, Poster poster)
	{
		this(socket, poster, System::currentTimeMillis);
	}

	TsgHubReports(BiPredicate<String, JsonObject> socket, Poster poster, LongSupplier clock)
	{
		this.socket = socket;
		this.poster = poster;
		this.clock = clock;
	}

	synchronized boolean send(String type, String path, JsonObject body)
	{
		if (!body.has("id")) body.addProperty("id", TsgHubSignals.newId());
		long now = clock.getAsLong();
		Pending entry = new Pending(type, path, body, now);
		pending.put(body.get("id").getAsString(), entry);
		while (pending.size() > MAX_PENDING) pending.remove(pending.keySet().iterator().next());
		return sendSocket(entry, now);
	}

	synchronized void acked(String id)
	{
		pending.remove(id);
	}

	synchronized void failed(String id, int status)
	{
		Pending entry = pending.get(id);
		if (entry == null) return;
		if (status == 429) entry.nextAt = clock.getAsLong() + RETRY_MILLIS;
		else pending.remove(id);
	}

	synchronized void resend()
	{
		long now = clock.getAsLong();
		for (Pending entry : pending.values()) sendSocket(entry, now);
	}

	synchronized int size()
	{
		return pending.size();
	}

	synchronized void clear()
	{
		pending.clear();
	}

	void flush()
	{
		long now = clock.getAsLong();
		List<Pending> due = new ArrayList<>();
		synchronized (this)
		{
			pending.values().removeIf(entry -> now - entry.createdAt > MAX_AGE_MILLIS);
			for (Pending entry : pending.values())
			{
				if (entry.nextAt > now || (!entry.socketTried && sendSocket(entry, now))) continue;
				due.add(entry);
			}
		}
		for (Pending entry : due)
		{
			String id = entry.body.get("id").getAsString();
			try
			{
				poster.post(entry.path, entry.body);
				acked(id);
			}
			catch (TsgHubApi.HttpError e)
			{
				if (e.status >= 400 && e.status < 500 && e.status != 429)
				{
					log.debug("TSG Hub {} rejected: {}", entry.type, e.getMessage());
					acked(id);
					continue;
				}
				retryLater(entry, now);
				return;
			}
			catch (Exception e)
			{
				log.debug("TSG Hub {} not sent", entry.type, e);
				retryLater(entry, now);
				return;
			}
		}
	}

	private synchronized void retryLater(Pending entry, long now)
	{
		entry.nextAt = now + RETRY_MILLIS;
	}

	private boolean sendSocket(Pending entry, long now)
	{
		if (!socket.test(entry.type, entry.body)) return false;
		entry.socketTried = true;
		entry.nextAt = now + RETRY_MILLIS;
		return true;
	}
}
