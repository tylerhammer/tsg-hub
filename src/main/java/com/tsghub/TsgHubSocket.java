package com.tsghub;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

		void onAdminRevoked(String token);

		void onAnnouncement(String text);

		void onUpdateAvailable(String version);
	}

	private static final int CLOSE_NORMAL = 1000;
	private static final int CLOSE_GOING_AWAY = 1001;
	private static final long PING_SECONDS = 30;
	private static final long MIN_BACKOFF_MILLIS = 1_000;
	private static final long MAX_BACKOFF_MILLIS = 60_000;
	private static final long RESTART_MIN_MILLIS = 2_000;
	private static final long RESTART_SPREAD_MILLIS = 8_000;

	private static final class Subscription
	{
		final String token;
		final boolean inClanChat;

		Subscription(String token, boolean inClanChat)
		{
			this.token = token;
			this.inClanChat = inClanChat;
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof Subscription)) return false;
			Subscription that = (Subscription) other;
			return inClanChat == that.inClanChat && token.equals(that.token);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(token, inClanChat);
		}
	}

	private final OkHttpClient http;
	private final HttpUrl url;
	private final ScheduledExecutorService executor;
	private final Listener listener;
	private final Map<String, Subscription> subscriptions = new HashMap<>();
	private final Map<String, String> rejectedTokens = new HashMap<>();
	private WebSocket socket;
	private JsonObject presence;
	private String adminToken = "";
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
		subscriptions.clear();
		rejectedTokens.clear();
		presence = null;
		adminToken = "";
	}

	synchronized void sync(Map<String, String> tokens, boolean inClanChat)
	{
		subscriptions.keySet().removeIf(eventId -> {
			if (tokens.containsKey(eventId)) return false;
			send("unsubscribe", eventId, null, false);
			return true;
		});
		for (Map.Entry<String, String> entry : tokens.entrySet())
		{
			Subscription wanted = new Subscription(entry.getValue(), inClanChat);
			if (wanted.equals(subscriptions.get(entry.getKey())) || wanted.token.equals(rejectedTokens.get(entry.getKey()))) continue;
			subscriptions.put(entry.getKey(), wanted);
			send("subscribe", entry.getKey(), wanted.token, wanted.inClanChat);
		}
	}

	synchronized boolean presence(JsonObject payload)
	{
		boolean live = ready && socket != null;
		if (payload == null)
		{
			if (live && presence != null) socket.send("{\"type\":\"presence.leave\"}");
			presence = null;
			return live;
		}
		presence = payload;
		if (live) sendPresence();
		return live;
	}

	synchronized void admin(String token)
	{
		String wanted = token == null ? "" : token;
		if (wanted.equals(adminToken)) return;
		adminToken = wanted;
		if (ready && socket != null && !adminToken.isEmpty()) sendAdmin();
	}

	private void sendAdmin()
	{
		JsonObject message = new JsonObject();
		message.addProperty("type", "admin");
		message.addProperty("token", adminToken);
		socket.send(message.toString());
	}

	private void sendPresence()
	{
		JsonObject message = presence.deepCopy();
		message.addProperty("type", "presence");
		socket.send(message.toString());
	}

	private void open()
	{
		retry = null;
		Request request = new Request.Builder().url(url.newBuilder().addQueryParameter("clanName", clanName).addQueryParameter("version", TsgHubVersion.VERSION).build()).build();
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

	private void send(String type, String eventId, String token, boolean inClanChat)
	{
		if (!ready || socket == null) return;
		JsonObject message = new JsonObject();
		message.addProperty("type", type);
		message.addProperty("eventId", eventId);
		if (token != null)
		{
			message.addProperty("token", token);
			message.addProperty("inClanChat", inClanChat);
		}
		socket.send(message.toString());
	}

	private synchronized void opened(WebSocket ws)
	{
		if (ws != socket) return;
		ready = true;
		attempts = 0;
		for (Map.Entry<String, Subscription> entry : subscriptions.entrySet())
		{
			send("subscribe", entry.getKey(), entry.getValue().token, entry.getValue().inClanChat);
		}
		if (presence != null) sendPresence();
		if (!adminToken.isEmpty()) sendAdmin();
		executor.execute(listener::onReady);
	}

	private synchronized void rejected(WebSocket ws, String eventId)
	{
		if (ws != socket) return;
		Subscription subscription = subscriptions.remove(eventId);
		if (subscription != null) rejectedTokens.put(eventId, subscription.token);
	}

	private synchronized void adminRevoked(WebSocket ws)
	{
		if (ws != socket || adminToken.isEmpty()) return;
		String token = adminToken;
		adminToken = "";
		executor.execute(() -> listener.onAdminRevoked(token));
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
				case "admin.revoked":
					adminRevoked(ws);
					break;
				case "announcement":
					String announcement = string(message, "text");
					if (!announcement.isEmpty()) executor.execute(() -> listener.onAnnouncement(announcement));
					break;
				case "update":
					String version = string(message, "version");
					if (!version.isEmpty()) executor.execute(() -> listener.onUpdateAvailable(version));
					break;
				case "error":
					if (!eventId.isEmpty()) rejected(ws, eventId);
					else if ("admin".equals(string(message, "topic")) && "401".equals(string(message, "status"))) adminRevoked(ws);
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
