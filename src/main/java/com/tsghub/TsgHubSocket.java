package com.tsghub;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Slf4j
final class TsgHubSocket
{
	interface Listener
	{
		void onReady();

		void onChanged(String topic, String eventId);
	}

	private static final int CLOSE_NORMAL = 1000;
	private static final int CLOSE_GOING_AWAY = 1001;
	private static final long PING_SECONDS = 30;
	private static final long MIN_BACKOFF_MILLIS = 1_000;
	private static final long MAX_BACKOFF_MILLIS = 60_000;
	private static final long RESTART_MIN_MILLIS = 2_000;
	private static final long RESTART_SPREAD_MILLIS = 8_000;

	private final OkHttpClient http;
	private final HttpUrl url;
	private final ScheduledExecutorService executor;
	private final Listener listener;
	private WebSocket socket;
	private ScheduledFuture<?> retry;
	private String clanName = "";
	private String rejectedClan = "";
	private boolean ready;
	private int attempts;

	TsgHubSocket(OkHttpClient http, String baseUrl, ScheduledExecutorService executor, Listener listener)
	{
		this.http = http.newBuilder().pingInterval(PING_SECONDS, TimeUnit.SECONDS).build();
		this.url = HttpUrl.get(baseUrl.replaceAll("/+$", "") + "/v1/ws");
		this.executor = executor;
		this.listener = listener;
	}

	synchronized boolean isLive()
	{
		return ready;
	}

	synchronized void connect(String clan)
	{
		if (clan.isEmpty() || clan.equals(rejectedClan)) return;
		if (clan.equals(clanName) && (socket != null || retry != null)) return;
		close();
		clanName = clan;
		attempts = 0;
		open();
	}

	synchronized void disconnect()
	{
		close();
		clanName = "";
		rejectedClan = "";
	}

	private void open()
	{
		retry = null;
		Request request = new Request.Builder().url(url.newBuilder().addQueryParameter("clanName", clanName).build()).build();
		socket = http.newWebSocket(request, new Handler());
	}

	private void close()
	{
		if (retry != null) retry.cancel(false);
		retry = null;
		if (socket != null) socket.close(CLOSE_NORMAL, null);
		socket = null;
		ready = false;
	}

	private synchronized void opened(WebSocket ws)
	{
		if (ws != socket) return;
		ready = true;
		attempts = 0;
		executor.execute(listener::onReady);
	}

	private synchronized void dropped(WebSocket ws, int code, int status)
	{
		if (ws != socket) return;
		socket = null;
		ready = false;
		if (clanName.isEmpty() || executor.isShutdown()) return;
		if (status == 403)
		{
			rejectedClan = clanName;
			clanName = "";
			return;
		}
		long delay = code == CLOSE_GOING_AWAY ? restartDelay() : backoff(attempts++);
		retry = executor.schedule(this::reopen, delay, TimeUnit.MILLISECONDS);
	}

	private synchronized void reopen()
	{
		if (retry == null || clanName.isEmpty()) return;
		open();
	}

	private static long backoff(int attempt)
	{
		long ceiling = Math.min(MAX_BACKOFF_MILLIS, MIN_BACKOFF_MILLIS << Math.min(attempt, 6));
		return ceiling / 2 + ThreadLocalRandom.current().nextLong(ceiling / 2 + 1);
	}

	private static long restartDelay()
	{
		return RESTART_MIN_MILLIS + ThreadLocalRandom.current().nextLong(RESTART_SPREAD_MILLIS);
	}

	private final class Handler extends WebSocketListener
	{
		@Override
		public void onMessage(WebSocket ws, String text)
		{
			JsonObject message;
			try
			{
				JsonElement element = new JsonParser().parse(text);
				if (!element.isJsonObject()) return;
				message = element.getAsJsonObject();
			}
			catch (RuntimeException e) { return; }
			String type = string(message, "type");
			String eventId = string(message, "eventId");
			switch (type)
			{
				case "ready":
					opened(ws);
					break;
				case "changed":
					String topic = string(message, "topic");
					executor.execute(() -> listener.onChanged(topic, eventId));
					break;
				case "error":
					log.debug("TSG Hub socket error: {}", string(message, "message"));
					break;
				default:
			}
		}

		@Override
		public void onClosing(WebSocket ws, int code, String reason)
		{
			ws.close(CLOSE_NORMAL, null);
			dropped(ws, code, 0);
		}

		@Override
		public void onClosed(WebSocket ws, int code, String reason)
		{
			dropped(ws, code, 0);
		}

		@Override
		public void onFailure(WebSocket ws, Throwable error, Response response)
		{
			log.debug("TSG Hub socket failed", error);
			dropped(ws, 0, response == null ? 0 : response.code());
		}
	}

	private static String string(JsonObject object, String key)
	{
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}
}
