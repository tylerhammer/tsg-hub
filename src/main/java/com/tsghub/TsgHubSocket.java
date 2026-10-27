package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.Value;
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
	enum State { DISCONNECTED, CONNECTING, CONNECTED }

	interface Listener
	{
		void onConnection(State state);

		void onSnapshot(JsonObject message);

		void onReady(String rulesVersion);

		void onRulesChanged(String version);

		void onChanged(String topic, String eventId);

		void onAdminRevoked(String token);

		void onAnnouncement(String text);

		void onUpdateAvailable(String version);

		void onAck(String id);

		void onRejected(String id, int status);

		void onSubscribed();

		void onReply(JsonObject message);

		void onXpRequest(String skills);

		void onGroupHeartbeat(JsonObject message);

		void onGroupRejected(int status);
	}

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
	private final Listener listener;
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

	TsgHubSocket(OkHttpClient http, Gson gson, String baseUrl, ScheduledExecutorService executor, Listener listener)
	{
		this.gson = gson;
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

	synchronized boolean send(String type, JsonObject body)
	{
		return emit(type, body.deepCopy());
	}

	synchronized boolean groupHeartbeat(String token, JsonObject body)
	{
		JsonObject message = body.deepCopy();
		message.addProperty("token", token);
		return emit("group.heartbeat", message);
	}

	private boolean emit(String type, JsonObject message)
	{
		if (!ready || socket == null) return false;
		message.addProperty("type", type);
		return socket.send(message.toString());
	}

	synchronized boolean sendSignal(JsonObject message)
	{
		return isLive() && socket.send(message.toString());
	}

	synchronized boolean xp(String skill, int xp, int tick)
	{
		JsonObject message = new JsonObject();
		message.addProperty("skill", skill);
		message.addProperty("xp", xp);
		message.addProperty("tick", tick);
		return emit("xp", message);
	}

	synchronized void rewardInterface(int group, boolean open, int tick)
	{
		JsonObject message = new JsonObject();
		message.addProperty("group", group);
		message.addProperty("open", open);
		message.addProperty("tick", tick);
		emit("interface", message);
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
		JsonObject message = new JsonObject();
		message.add("topics", topics);
		message.addProperty("deltas", true);
		message.addProperty("sets", true);
		emit("watch", message);
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
		message.addProperty("token", adminToken);
		emit("admin", message);
	}

	private void sendPresence()
	{
		emit("presence", presence.deepCopy());
	}

	private void open()
	{
		retry = null;
		Request request = new Request.Builder().url(url.newBuilder().addQueryParameter("clanName", clanName).addQueryParameter("version", TsgHubVersion.VERSION).build()).build();
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
		JsonObject message = new JsonObject();
		message.addProperty("eventId", eventId);
		if (token != null)
		{
			message.addProperty("token", token);
			message.addProperty("inClanChat", inClanChat);
		}
		emit(type, message);
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
		String rulesVersion = TsgHubJson.str(message, "rules");
		executor.execute(() -> listener.onReady(rulesVersion));
	}

	private synchronized void rejected(WebSocket ws, String eventId)
	{
		if (ws != socket) return;
		confirmed.remove(eventId);
		Subscription subscription = subscriptions.remove(eventId);
		if (subscription != null) rejectedTokens.put(eventId, subscription.token);
	}

	private synchronized void subscribed(WebSocket ws, String eventId)
	{
		if (ws != socket || !subscriptions.containsKey(eventId)) return;
		confirmed.add(eventId);
		if (listener != null) executor.execute(listener::onSubscribed);
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
		if (listener != null && !executor.isShutdown()) executor.execute(() -> listener.onConnection(next));
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
			String type = TsgHubJson.str(message, "type");
			String eventId = TsgHubJson.str(message, "eventId");
			switch (type)
			{
				case "ready":
					opened(ws, message);
					break;
				case "changed":
					String topic = TsgHubJson.str(message, "topic");
					dispatch(ws, () -> listener.onChanged(topic, eventId));
					break;
				case "rules":
					String rules = TsgHubJson.str(message, "rules");
					if (!rules.isEmpty()) dispatch(ws, () -> listener.onRulesChanged(rules));
					break;
				case "subscribed":
					subscribed(ws, eventId);
					break;
				case "progress":
				case "claimed":
				case "notice":
					dispatch(ws, () -> listener.onReply(message));
					break;
				case "state":
					dispatch(ws, () -> listener.onSnapshot(message));
					break;
				case "group.heartbeat":
					dispatch(ws, () -> listener.onGroupHeartbeat(message));
					break;
				case "ack":
					String id = TsgHubJson.str(message, "id");
					if (!id.isEmpty()) dispatch(ws, () -> listener.onAck(id));
					break;
				case "xp.request":
					String skills = TsgHubJson.str(message, "skills");
					if (!skills.isEmpty()) dispatch(ws, () -> listener.onXpRequest(skills));
					break;
				case "admin.revoked":
					adminRevoked(ws);
					break;
				case "announcement":
					String announcement = TsgHubJson.str(message, "text");
					if (!announcement.isEmpty()) dispatch(ws, () -> listener.onAnnouncement(announcement));
					break;
				case "update":
					String version = TsgHubJson.str(message, "version");
					if (!version.isEmpty()) dispatch(ws, () -> listener.onUpdateAvailable(version));
					break;
				case "error":
					String failed = TsgHubJson.str(message, "id");
					int status = TsgHubJson.integer(message, "status", 0);
					if (!failed.isEmpty()) dispatch(ws, () -> listener.onRejected(failed, status));
					String errorTopic = TsgHubJson.str(message, "topic");
					if (!eventId.isEmpty()) rejected(ws, eventId);
					else if ("admin".equals(errorTopic) && status == 401) adminRevoked(ws);
					else if ("group".equals(errorTopic)) dispatch(ws, () -> listener.onGroupRejected(status));
					log.debug("TSG Hub socket error: {}", TsgHubJson.str(message, "message"));
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

	private synchronized void dispatch(WebSocket ws, Runnable action)
	{
		if (ws != socket || executor.isShutdown()) return;
		executor.execute(action);
	}
}
