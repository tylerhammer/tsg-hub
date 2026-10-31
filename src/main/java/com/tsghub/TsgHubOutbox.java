package com.tsghub;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TsgHubOutbox
{
	static final long RETRY_MILLIS = 10_000;
	static final long MAX_RETRY_MILLIS = 120_000;
	static final long MAX_AGE_MILLIS = 600_000;
	static final int MAX_PENDING = 200;
	static final int ANSWERING_PROTOCOL = 2;

	interface Link
	{
		boolean send(String type, JsonObject body);

		boolean isLive();

		int protocol();
	}

	interface Poster
	{
		void post(String method, String path, String token, JsonObject body) throws Exception;
	}

	interface Rejection
	{
		void rejected(int status, String message, boolean resent);
	}

	enum Kind { REPORT, SIGNAL, SOCKET_SIGNAL, REQUEST }

	private static final class Pending
	{
		final String id;
		final String type;
		final JsonObject body;
		final Kind kind;
		final String method;
		final String path;
		final String token;
		final Rejection rejection;
		final long createdAt;
		final Set<String> deliveredTo = new HashSet<>();
		long nextAt;
		int attempts;
		boolean socketTried;
		boolean restOnly;

		Pending(String id, String type, JsonObject body, Kind kind, String method, String path, String token, Rejection rejection, long now)
		{
			this.id = id;
			this.type = type;
			this.body = body;
			this.kind = kind;
			this.method = method;
			this.path = path;
			this.token = token;
			this.rejection = rejection;
			this.createdAt = now;
			this.nextAt = now;
		}

		JsonObject message()
		{
			JsonObject message = body.deepCopy();
			message.addProperty("type", type);
			return message;
		}
	}

	private final Link link;
	private final Poster poster;
	private final LongSupplier clock;
	private final Map<String, Pending> pending = new LinkedHashMap<>();

	TsgHubOutbox(Link link, Poster poster)
	{
		this(link, poster, System::currentTimeMillis);
	}

	TsgHubOutbox(Link link, Poster poster, LongSupplier clock)
	{
		this.link = link;
		this.poster = poster;
		this.clock = clock;
	}

	static long retryDelay(int attempts)
	{
		return Math.min(MAX_RETRY_MILLIS, RETRY_MILLIS << Math.min(Math.max(attempts - 1, 0), 16));
	}

	boolean report(String type, String path, JsonObject body)
	{
		return report(type, path, null, body);
	}

	boolean report(String type, String path, String token, JsonObject body)
	{
		return report(type, "POST", path, token, body);
	}

	synchronized boolean report(String type, String method, String path, String token, JsonObject body)
	{
		return sendSocket(add(type, body, Kind.REPORT, method, path, token, null), clock.getAsLong());
	}

	synchronized boolean signal(String type, JsonObject body, boolean socketOnly)
	{
		return sendSocket(add(type, body, socketOnly ? Kind.SOCKET_SIGNAL : Kind.SIGNAL, null, null, null, null), clock.getAsLong());
	}

	synchronized boolean request(String type, JsonObject body, Rejection rejection)
	{
		if (link.protocol() < ANSWERING_PROTOCOL) return false;
		Pending entry = add(type, body, Kind.REQUEST, null, null, null, rejection);
		if (sendSocket(entry, clock.getAsLong())) return true;
		pending.remove(entry.id);
		return false;
	}

	private Pending add(String type, JsonObject body, Kind kind, String method, String path, String token, Rejection rejection)
	{
		JsonObject copy = body.deepCopy();
		copy.remove("type");
		if (!copy.has("id")) copy.addProperty("id", TsgHubSignals.newId());
		body.add("id", copy.get("id"));
		Pending entry = new Pending(copy.get("id").getAsString(), type, copy, kind, method, path, token, rejection, clock.getAsLong());
		pending.put(entry.id, entry);
		while (pending.size() > MAX_PENDING) pending.remove(pending.keySet().iterator().next());
		return entry;
	}

	synchronized void acked(String id)
	{
		pending.remove(id);
	}

	void failed(String id, int status, String message)
	{
		Pending entry;
		synchronized (this)
		{
			entry = pending.get(id);
			if (entry == null) return;
			long now = clock.getAsLong();
			if (status == 429 || status >= 500 || status == 0)
			{
				entry.nextAt = now + retryDelay(entry.attempts);
				return;
			}
			if (status == 401 && entry.kind == Kind.REPORT && entry.token != null)
			{
				entry.restOnly = true;
				entry.nextAt = now;
				return;
			}
			pending.remove(id);
		}
		log.debug("TSG Hub {} rejected ({}): {}", entry.type, status, message);
		if (entry.rejection != null) entry.rejection.rejected(status, message, entry.attempts > 1);
	}

	synchronized void resend()
	{
		long now = clock.getAsLong();
		if (link.protocol() < ANSWERING_PROTOCOL) pending.values().removeIf(entry -> entry.kind == Kind.REQUEST);
		for (Pending entry : pending.values()) if (!entry.restOnly) sendSocket(entry, now);
	}

	synchronized void resendSignals()
	{
		long now = clock.getAsLong();
		for (Pending entry : pending.values()) if (entry.kind == Kind.SIGNAL || entry.kind == Kind.SOCKET_SIGNAL) sendSocket(entry, now);
	}

	synchronized int size()
	{
		return pending.size();
	}

	synchronized void clear()
	{
		pending.clear();
	}

	synchronized void clearSignals()
	{
		pending.values().removeIf(entry -> entry.kind == Kind.SIGNAL || entry.kind == Kind.SOCKET_SIGNAL);
	}

	void flush()
	{
		long now = clock.getAsLong();
		List<Pending> due = new ArrayList<>();
		synchronized (this)
		{
			expire(now);
			boolean live = link.isLive();
			boolean answering = live && link.protocol() >= ANSWERING_PROTOCOL;
			for (Pending entry : pending.values())
			{
				if (entry.nextAt > now) continue;
				if (entry.kind == Kind.REQUEST && !answering) continue;
				if (live && !entry.restOnly && (answering || entry.kind != Kind.REPORT || !entry.socketTried) && sendSocket(entry, now)) continue;
				if (entry.kind == Kind.REPORT) due.add(entry);
			}
		}
		for (Pending entry : due)
		{
			try
			{
				poster.post(entry.method, entry.path, entry.token, entry.body);
				acked(entry.id);
			}
			catch (TsgHubApi.HttpError e)
			{
				if (e.status >= 400 && e.status < 500 && e.status != 429)
				{
					log.debug("TSG Hub {} rejected: {}", entry.type, e.getMessage());
					acked(entry.id);
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

	synchronized List<JsonObject> signalBatch(int max, String eventId)
	{
		List<JsonObject> batch = new ArrayList<>();
		for (Pending entry : pending.values())
		{
			if (batch.size() >= max) break;
			if (entry.kind == Kind.SIGNAL && !entry.deliveredTo.contains(eventId)) batch.add(entry.message());
		}
		return batch;
	}

	synchronized void delivered(List<JsonObject> messages, String eventId)
	{
		for (JsonObject message : messages)
		{
			Pending entry = pending.get(TsgHubJson.str(message, "id"));
			if (entry != null) entry.deliveredTo.add(eventId);
		}
	}

	synchronized void deliveredEverywhere(Set<String> eventIds)
	{
		pending.values().removeIf(entry -> entry.kind == Kind.SIGNAL && !entry.deliveredTo.isEmpty() && entry.deliveredTo.containsAll(eventIds));
	}

	private synchronized void retryLater(Pending entry, long now)
	{
		entry.attempts++;
		entry.nextAt = now + retryDelay(entry.attempts);
	}

	private void expire(long now)
	{
		for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); )
		{
			if (now - it.next().createdAt > MAX_AGE_MILLIS) it.remove();
		}
	}

	private boolean sendSocket(Pending entry, long now)
	{
		if (!link.isLive() || !link.send(entry.type, entry.body)) return false;
		entry.socketTried = true;
		entry.attempts++;
		entry.nextAt = now + retryDelay(entry.attempts);
		return true;
	}
}
