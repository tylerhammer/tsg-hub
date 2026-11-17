package com.tsghub;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.extern.slf4j.*;
import static com.tsghub.TsgHubJson.*;

@Slf4j
final class TsgHubOutbox
{
	static final long RETRY_MILLIS = 10_000;
	static final long MAX_RETRY_MILLIS = 120_000;
	static final long MAX_AGE_MILLIS = 600_000;
	static final int MAX_PENDING = 200;

	interface Link
	{
		boolean send(String type, JsonObject body);

		boolean isLive();
	}

	interface Poster
	{
		CompletableFuture<?> post(String method, String path, String token, JsonObject body);
	}

	interface Rejection
	{
		void rejected(int status, String message);
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
		boolean restOnly;
		boolean posting;

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
			if (status == 429 || status >= 500)
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
		if (entry.rejection != null) entry.rejection.rejected(status, message);
	}

	synchronized void resend()
	{
		long now = clock.getAsLong();
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
			for (Pending entry : pending.values())
			{
				if (entry.nextAt > now || entry.posting) continue;
				if (live && !entry.restOnly && sendSocket(entry, now)) continue;
				if (entry.kind != Kind.REPORT) continue;
				entry.posting = true;
				due.add(entry);
			}
		}
		post(due.iterator(), now);
	}

	private void post(Iterator<Pending> due, long now)
	{
		if (!due.hasNext()) return;
		Pending entry = due.next();
		poster.post(entry.method, entry.path, entry.token, entry.body).whenComplete((result, error) -> {
			int status = TsgHubApi.status(error);
			if (error == null || (status >= 400 && status < 500 && status != 429))
			{
				if (error != null) log.debug("TSG Hub {} rejected: {}", entry.type, error.getMessage());
				acked(entry.id);
				post(due, now);
				return;
			}
			log.debug("TSG Hub {} not sent", entry.type, error);
			synchronized (this)
			{
				entry.attempts++;
				entry.nextAt = now + retryDelay(entry.attempts);
				entry.posting = false;
				due.forEachRemaining(skipped -> skipped.posting = false);
			}
		});
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
			Pending entry = pending.get(str(message, "id"));
			if (entry != null) entry.deliveredTo.add(eventId);
		}
	}

	synchronized void deliveredEverywhere(Set<String> eventIds)
	{
		pending.values().removeIf(entry -> entry.kind == Kind.SIGNAL && !entry.deliveredTo.isEmpty() && entry.deliveredTo.containsAll(eventIds));
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
		entry.attempts++;
		entry.nextAt = now + retryDelay(entry.attempts);
		return true;
	}
}
