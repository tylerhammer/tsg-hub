package com.tsghub;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TsgHubReports
{
	static final long RETRY_MILLIS = 10_000;

	interface Poster
	{
		void post(String path, String token, JsonObject body) throws Exception;
	}

	private static final class Pending extends TsgHubOutbox.Item
	{
		final String type;
		final String path;
		final String token;
		final JsonObject body;
		long nextAt;
		boolean socketTried;

		Pending(String type, String path, String token, JsonObject body, long now)
		{
			super(now);
			this.type = type;
			this.path = path;
			this.token = token;
			this.body = body;
			this.nextAt = now;
		}
	}

	private final BiPredicate<String, JsonObject> socket;
	private final Poster poster;
	private final LongSupplier clock;
	private final TsgHubOutbox<Pending> pending = new TsgHubOutbox<>();

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

	boolean send(String type, String path, JsonObject body)
	{
		return send(type, path, null, body);
	}

	synchronized boolean send(String type, String path, String token, JsonObject body)
	{
		if (!body.has("id")) body.addProperty("id", TsgHubSignals.newId());
		long now = clock.getAsLong();
		Pending entry = new Pending(type, path, token, body, now);
		pending.add(body.get("id").getAsString(), entry);
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
		else if (status == 401 && entry.token != null) entry.nextAt = clock.getAsLong();
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
			pending.expire(now);
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
				poster.post(entry.path, entry.token, entry.body);
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
