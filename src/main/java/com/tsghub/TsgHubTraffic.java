package com.tsghub;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
final class TsgHubTraffic
{
	static final TsgHubTraffic OFF = new TsgHubTraffic(false);

	private static final Pattern ID_SEGMENT = Pattern.compile("(?=[^/]*\\d)[^/]{6,}");

	private static final class Counter
	{
		final AtomicLong calls = new AtomicLong();
		final AtomicLong out = new AtomicLong();
		final AtomicLong in = new AtomicLong();
		long lastCalls;
		long lastOut;
		long lastIn;
	}

	private final boolean enabled;
	private final Map<String, Counter> counters = new ConcurrentHashMap<>();
	private final long startedAt = System.currentTimeMillis();

	TsgHubTraffic(boolean enabled)
	{
		this.enabled = enabled;
	}

	OkHttpClient track(OkHttpClient http)
	{
		if (!enabled) return http;
		return http.newBuilder().eventListenerFactory(call -> new Listener()).build();
	}

	void sent(String type, String text)
	{
		if (enabled) add("ws > " + type, text.length(), 0);
	}

	void received(String type, String text)
	{
		if (enabled) add("ws < " + type, 0, text.length());
	}

	private void add(String key, long out, long in)
	{
		Counter counter = counters.computeIfAbsent(key, k -> new Counter());
		counter.calls.incrementAndGet();
		counter.out.addAndGet(out);
		counter.in.addAndGet(in);
	}

	synchronized void log()
	{
		if (!enabled || counters.isEmpty()) return;
		List<Map.Entry<String, Counter>> entries = new ArrayList<>(counters.entrySet());
		entries.sort((a, b) -> Long.compare(b.getValue().out.get() + b.getValue().in.get(), a.getValue().out.get() + a.getValue().in.get()));
		long totalOut = 0;
		long totalIn = 0;
		StringBuilder lines = new StringBuilder();
		for (Map.Entry<String, Counter> entry : entries)
		{
			Counter c = entry.getValue();
			long calls = c.calls.get();
			long out = c.out.get();
			long in = c.in.get();
			totalOut += out;
			totalIn += in;
			lines.append(String.format("%n  %-48s %6d calls %10s out %10s in   | last: %4d calls %9s out %9s in",
				entry.getKey(), calls, bytes(out), bytes(in), calls - c.lastCalls, bytes(out - c.lastOut), bytes(in - c.lastIn)));
			c.lastCalls = calls;
			c.lastOut = out;
			c.lastIn = in;
		}
		long minutes = Math.max(1, (System.currentTimeMillis() - startedAt) / 60_000);
		log.info("TSG Hub traffic over {}m: {} out, {} in ({}/min){}", minutes, bytes(totalOut), bytes(totalIn), bytes((totalOut + totalIn) / minutes), lines);
	}

	private static String bytes(long value)
	{
		if (value < 1024) return value + " B";
		if (value < 1024 * 1024) return String.format("%.1f KB", value / 1024.0);
		return String.format("%.2f MB", value / (1024.0 * 1024.0));
	}

	static String key(Request request)
	{
		String path = ID_SEGMENT.matcher(request.url().encodedPath()).replaceAll(":id");
		return "http " + request.method() + " " + path;
	}

	private final class Listener extends EventListener
	{
		private long out;
		private long in;

		@Override
		public void requestHeadersEnd(Call call, Request request)
		{
			out += request.headers().byteCount();
		}

		@Override
		public void requestBodyEnd(Call call, long byteCount)
		{
			out += byteCount;
		}

		@Override
		public void responseHeadersEnd(Call call, Response response)
		{
			in += response.headers().byteCount();
		}

		@Override
		public void responseBodyEnd(Call call, long byteCount)
		{
			in += byteCount;
		}

		@Override
		public void callEnd(Call call)
		{
			add(key(call.request()), out, in);
		}

		@Override
		public void callFailed(Call call, IOException ioe)
		{
			add(key(call.request()) + " (failed)", out, in);
		}
	}
}
