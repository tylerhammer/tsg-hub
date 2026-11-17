package com.tsghub;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.*;
import lombok.extern.slf4j.*;
import okhttp3.*;
import static com.tsghub.TsgHubJson.*;

@Slf4j
final class TsgHubSocket implements TsgHubOutbox.Link
{
	enum State { DISCONNECTED, CONNECTING, CONNECTED }

	private static final int CLOSE_NORMAL = 1000;
	private static final int CLOSE_GOING_AWAY = 1001;
	private static final long PING_SECONDS = 30;
	private static final long RESTART_MIN_MILLIS = 2_000;
	private static final long RESTART_SPREAD_MILLIS = 8_000;

	@Value
	private static class Subscription
	{
		String token;
		boolean inClanChat;
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final HttpUrl url;
	private final ScheduledExecutorService executor;
	private final Map<String, Consumer<JsonObject>> handlers;
	private final Consumer<State> connection;
	private final Map<String, Subscription> subscriptions = new HashMap<>();
	private final Map<String, String> rejectedTokens = new HashMap<>();
	private final Set<String> confirmed = new HashSet<>();
	private WebSocket socket;
	private JsonObject presence;
	private String adminToken = "";
	private Set<String> watching = new HashSet<>();
	private ScheduledFuture<?> retry;
	private String clanName = "";
	private String rejectedClan = "";
	private boolean ready;
	private final TsgHubBackoff backoff = new TsgHubBackoff(1_000, 60_000);
	private State state = State.DISCONNECTED;

	TsgHubSocket(OkHttpClient http, Gson gson, String baseUrl, ScheduledExecutorService executor, Map<String, Consumer<JsonObject>> handlers, Consumer<State> connection)
	{
		this.gson = gson;
		this.http = http.newBuilder().pingInterval(PING_SECONDS, TimeUnit.SECONDS).build();
		this.url = HttpUrl.get(baseUrl.replaceAll("/+$", "") + "/v1/ws");
		this.executor = executor;
		this.handlers = handlers;
		this.connection = connection;
	}

	@Override
	public synchronized boolean isLive()
	{
		return ready;
	}

	synchronized void connect(String clan)
	{
		if (clan.isEmpty() || clan.equals(rejectedClan)) return;
		if (clan.equals(clanName) && (socket != null || retry != null)) return;
		close();
		clanName = clan;
		backoff.reset();
		open();
	}

	synchronized void disconnect()
	{
		close();
		clanName = "";
		rejectedClan = "";
		subscriptions.clear();
		rejectedTokens.clear();
		confirmed.clear();
		presence = null;
		adminToken = "";
		watching = new HashSet<>();
		changed();
	}

	synchronized void sync(Map<String, String> tokens, boolean inClanChat)
	{
		subscriptions.keySet().removeIf(eventId -> {
			if (tokens.containsKey(eventId)) return false;
			confirmed.remove(eventId);
			send("unsubscribe", eventId, null, false);
			return true;
		});
		for (Map.Entry<String, String> entry : tokens.entrySet())
		{
			Subscription wanted = new Subscription(entry.getValue(), inClanChat);
			if (wanted.equals(subscriptions.get(entry.getKey())) || wanted.token.equals(rejectedTokens.get(entry.getKey()))) continue;
			subscriptions.put(entry.getKey(), wanted);
			confirmed.remove(entry.getKey());
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

	@Override
	public synchronized boolean send(String type, JsonObject body)
	{
		return emit(type, body.deepCopy());
	}

	synchronized boolean group(String type, String token, JsonObject body)
	{
		JsonObject message = body.deepCopy();
		message.addProperty("token", token);
		return emit(type, message);
	}

	private boolean emit(String type, JsonObject message)
	{
		if (!ready || socket == null) return false;
		message.addProperty("type", type);
		return socket.send(message.toString());
	}

	synchronized boolean xp(String skill, int xp, int tick)
	{
		return emit("xp", obj("skill", skill, "xp", xp, "tick", tick));
	}

	synchronized void rewardInterface(int group, boolean open, int tick)
	{
		emit("interface", obj("group", group, "open", open, "tick", tick));
	}

	synchronized void watch(Set<String> topics)
	{
		if (topics.equals(watching)) return;
		watching = new HashSet<>(topics);
		if (ready && socket != null) sendWatch();
	}

	private void sendWatch()
	{
		JsonArray topics = new JsonArray();
		watching.forEach(topics::add);
		emit("watch", obj("topics", topics, "deltas", true, "sets", true));
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
		emit("admin", obj("token", adminToken));
	}

	private void sendPresence()
	{
		emit("presence", presence.deepCopy());
	}

	private void open()
	{
		retry = null;
		Request request = new Request.Builder().url(url.newBuilder().addQueryParameter("clanName", clanName).addQueryParameter("version", TsgHubVersion.VERSION).addQueryParameter("protocol", "2").build()).build();
		socket = http.newWebSocket(request, new Handler());
		changed();
	}

	private void close()
	{
		if (retry != null) retry.cancel(false);
		retry = null;
		if (socket != null) socket.close(CLOSE_NORMAL, null);
		socket = null;
		ready = false;
		confirmed.clear();
	}

	private void send(String type, String eventId, String token, boolean inClanChat)
	{
		emit(type, token == null ? obj("eventId", eventId) : obj("eventId", eventId, "token", token, "inClanChat", inClanChat));
	}

	private synchronized void opened(WebSocket ws, JsonObject message)
	{
		if (ws != socket) return;
		ready = true;
		backoff.reset();
		for (Map.Entry<String, Subscription> entry : subscriptions.entrySet())
		{
			send("subscribe", entry.getKey(), entry.getValue().token, entry.getValue().inClanChat);
		}
		if (presence != null) sendPresence();
		if (!adminToken.isEmpty()) sendAdmin();
		if (!watching.isEmpty()) sendWatch();
		changed();
		dispatch(ws, message);
	}

	private synchronized void rejected(WebSocket ws, String eventId)
	{
		if (ws != socket) return;
		confirmed.remove(eventId);
		Subscription subscription = subscriptions.remove(eventId);
		if (subscription != null) rejectedTokens.put(eventId, subscription.token);
	}

	private synchronized void subscribed(WebSocket ws, JsonObject message)
	{
		String eventId = str(message, "eventId");
		if (ws != socket || !subscriptions.containsKey(eventId)) return;
		confirmed.add(eventId);
		dispatch(ws, message);
	}

	private synchronized void adminRevoked(WebSocket ws)
	{
		if (ws != socket || adminToken.isEmpty()) return;
		dispatch(ws, obj("type", "admin.revoked", "token", adminToken));
		adminToken = "";
	}

	private synchronized void dropped(WebSocket ws, int code, int status)
	{
		if (ws != socket) return;
		socket = null;
		ready = false;
		confirmed.clear();
		if (status == 403)
		{
			rejectedClan = clanName;
			clanName = "";
		}
		if (!clanName.isEmpty() && !executor.isShutdown())
		{
			long delay = code == CLOSE_GOING_AWAY ? restartDelay() : backoff.next();
			retry = executor.schedule(this::reopen, delay, TimeUnit.MILLISECONDS);
		}
		changed();
	}

	private void changed()
	{
		State next = ready ? State.CONNECTED : socket != null || retry != null ? State.CONNECTING : State.DISCONNECTED;
		if (next == state) return;
		state = next;
		if (!executor.isShutdown()) executor.execute(() -> connection.accept(next));
	}

	private synchronized void reopen()
	{
		if (retry == null || clanName.isEmpty()) return;
		open();
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
				JsonElement element = gson.fromJson(text, JsonElement.class);
				if (element == null || !element.isJsonObject()) return;
				message = element.getAsJsonObject();
			}
			catch (RuntimeException e) { return; }
			String eventId = str(message, "eventId");
			switch (str(message, "type"))
			{
				case "ready":
					opened(ws, message);
					break;
				case "subscribed":
					subscribed(ws, message);
					break;
				case "admin.revoked":
					adminRevoked(ws);
					break;
				case "error":
					log.debug("TSG Hub socket error: {}", str(message, "message"));
					boolean answer = !str(message, "id").isEmpty();
					if (!answer && !eventId.isEmpty()) rejected(ws, eventId);
					else if (!answer && "admin".equals(str(message, "topic")) && integer(message, "status", 0) == 401) adminRevoked(ws);
					else dispatch(ws, message);
					break;
				default:
					dispatch(ws, message);
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

	private synchronized void dispatch(WebSocket ws, JsonObject message)
	{
		Consumer<JsonObject> handler = handlers.get(str(message, "type"));
		if (handler != null && ws == socket && !executor.isShutdown()) executor.execute(() -> handler.accept(message));
	}
}
