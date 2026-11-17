package com.tsghub;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.*;
import net.runelite.client.util.*;
import okhttp3.*;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.MEMBER_NAME;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubSession.TOKEN;
import static com.tsghub.TsgHubJson.*;

final class TsgHubLiveSync
{
	@AllArgsConstructor
	private static final class Topic
	{
		final BiConsumer<JsonObject, String> snapshot;
		final Consumer<String> changed;
	}

	private final TsgHubPlugin plugin;
	private final ScheduledExecutorService executor;
	private final Gson gson;
	private final TsgHubSocket socket;
	private final Map<String, Topic> topics = new HashMap<>();
	private volatile Filepath rulesCache;

	TsgHubLiveSync(TsgHubPlugin plugin, OkHttpClient http, Gson gson, String serviceUrl, ScheduledExecutorService executor)
	{
		this.plugin = plugin;
		this.executor = executor;
		this.gson = gson;
		Map<String, Consumer<JsonObject>> handlers = new HashMap<>();
		handlers.put("ready", m -> {
			refreshRules(str(m, "rules"));
			plugin.outbox().resend();
		});
		handlers.put("rules", m -> refreshRules(str(m, "rules")));
		handlers.put("changed", m -> changed(str(m, "topic"), str(m, "eventId")));
		handlers.put("state", this::snapshot);
		handlers.put("admin.revoked", m -> plugin.keys().recheck(str(m, "token")));
		handlers.put("announcement", when("text", text -> plugin.chat().announce(text)));
		handlers.put("update", when("version", version -> plugin.chat().updateAvailable(version)));
		handlers.put("chat.message", m -> {
			String text = str(m, "text");
			if (!text.isEmpty()) plugin.chat().message(str(m, "tag"), text);
		});
		handlers.put("ack", m -> plugin.outbox().acked(str(m, "id")));
		handlers.put("subscribed", m -> plugin.outbox().resendSignals());
		handlers.put("xp.request", when("skills", skills -> plugin.tracker().reportXp(skills)));
		handlers.put("drop.screenshot", m -> {
			String id = str(m, "id");
			String token = str(m, "token");
			if (!id.isEmpty() && !token.isEmpty()) plugin.drops().sendScreenshot(id, token);
		});
		handlers.put("error", m -> {
			String id = str(m, "id");
			int status = integer(m, "status", 0);
			if (!id.isEmpty()) plugin.outbox().failed(id, status, str(m, "message"));
			else if ("group".equals(str(m, "topic"))) plugin.groups().beatRejected(status);
		});
		for (String type : new String[] {"progress", "claimed", "notice"}) handlers.put(type, m -> plugin.chat().reply(m));
		for (String type : new String[] {"group.heartbeat", "group.split"}) handlers.put(type, m -> plugin.groups().beaten(m));
		socket = new TsgHubSocket(http, gson, serviceUrl, executor, handlers, state -> plugin.ui(s -> s.setConnection(state)));
		topics.put("groups", new Topic((data, eventId) -> plugin.groups().apply(array(data, "groups")), eventId -> plugin.groups().autoRefresh()));
		topics.put("presence", new Topic(this::presence, eventId -> plugin.presence().autoRefresh()));
		topics.put("positions", new Topic((data, eventId) -> plugin.memberMap().apply(array(data, "members")), eventId -> { }));
		topics.put("drops", new Topic((data, eventId) -> plugin.drops().onSnapshot(array(data, "drops")), eventId -> plugin.drops().autoRefresh()));
		topics.put("announcements", new Topic((data, eventId) -> plugin.announcements().apply(array(data, "announcements")), eventId -> plugin.announcements().load()));
		topics.put("events", new Topic((data, eventId) -> plugin.sessions().applyClanEvents(data), eventId -> plugin.sessions().loadClanEvents()));
		topics.put("notifications", new Topic((data, eventId) -> {
			if (isActiveEvent(eventId)) plugin.chat().notifications(array(data, "notifications"));
		}, eventId -> {
			if (isActiveEvent(eventId)) plugin.sessions().pollNotifications();
		}));
		topics.put("event", new Topic(this::eventSnapshot, this::eventChanged));
	}

	private static Consumer<JsonObject> when(String key, Consumer<String> action)
	{
		return m -> {
			String value = str(m, key);
			if (!value.isEmpty()) action.accept(value);
		};
	}

	TsgHubSocket socket()
	{
		return socket;
	}

	boolean live()
	{
		return socket.isLive();
	}

	void syncNow()
	{
		if (executor.isShutdown()) return;
		executor.execute(this::tick);
	}

	void tick()
	{
		TsgHubOutbox outbox = plugin.outbox();
		if (!plugin.sharingEnabled()) outbox.clear();
		outbox.flush();
		if (!plugin.sharingInClan())
		{
			socket.disconnect();
			return;
		}
		TsgHubClan clan = plugin.clan();
		TsgHubSession session = plugin.session();
		TsgHubSidebarPanel sidebar = plugin.sidebar();
		socket.connect(clan.name());
		Map<String, String> tokens = new HashMap<>();
		String eventId = session.get(EVENT_ID);
		String token = session.get(TOKEN);
		if (!eventId.isEmpty() && !token.isEmpty()) tokens.put(eventId, token);
		String competitionId = sidebar == null ? null : sidebar.competitionView.openCompetitionId();
		String memberToken = competitionId == null ? "" : session.get(MEMBER_TOKEN + competitionId);
		if (!memberToken.isEmpty()) tokens.putIfAbsent(competitionId, memberToken);
		for (String joinedId : plugin.sessions().joinedCompetitions())
		{
			String joinedToken = session.get(MEMBER_TOKEN + joinedId);
			if (!joinedToken.isEmpty()) tokens.putIfAbsent(joinedId, joinedToken);
		}
		socket.sync(tokens, clan.inClanChat());
		socket.admin(plugin.keys().adminKey());
		socket.watch(watched(sidebar, competitionId));
		plugin.signals().flush(tokens);
	}

	private Set<String> watched(TsgHubSidebarPanel sidebar, String competitionId)
	{
		Set<String> watched = new HashSet<>();
		watched.add("events");
		watched.add("announcements");
		watched.add("notifications");
		if (sidebar == null) return watched;
		TsgHubMemberMap memberMap = plugin.memberMap();
		if (memberMap != null && memberMap.isOpen()) watched.add("positions");
		if (sidebar.wantsGroups()) watched.add("groups");
		if (sidebar.wantsMembers()) watched.add("presence");
		if (sidebar.wantsDrops()) watched.add("drops");
		String eventId = plugin.session().get(EVENT_ID);
		if (!eventId.isEmpty() && plugin.sessions().wantsBoardUpdates(sidebar)) watched.add("event:" + eventId);
		if (competitionId != null) watched.add("event:" + competitionId);
		return watched;
	}

	private void snapshot(JsonObject message)
	{
		Topic topic = topics.get(str(message, "topic"));
		if (topic != null) topic.snapshot.accept(object(message, "data"), str(message, "eventId"));
	}

	private void changed(String name, String eventId)
	{
		Topic topic = topics.get(name);
		if (topic != null) topic.changed.accept(eventId);
	}

	private void presence(JsonObject data, String eventId)
	{
		if (data.has("delta"))
		{
			plugin.presence().applyDelta(data.has("presence") ? object(data, "presence") : null, data.has("members") ? object(data, "members") : null);
			return;
		}
		if (!data.has("presence") || !data.get("presence").isJsonObject()) return;
		JsonObject notes = data.has("members") && data.get("members").isJsonObject() ? data.getAsJsonObject("members") : null;
		plugin.presence().apply(array(data.getAsJsonObject("presence"), "members"), notes == null ? null : array(notes, "members"));
	}

	private void eventSnapshot(JsonObject data, String eventId)
	{
		if (!data.has("event") || !data.get("event").isJsonObject()) return;
		JsonObject event = data.getAsJsonObject("event");
		TsgHubSidebarPanel sidebar = plugin.sidebar();
		if (sidebar == null) return;
		TsgHubSession session = plugin.session();
		if (isActiveEvent(eventId) && plugin.sessions().wantsBoardUpdates(sidebar)) plugin.sessions().applyBoard(eventId, session.get(TOKEN), event, false, true);
		if (eventId.equals(sidebar.competitionView.openCompetitionId()))
		{
			String name = session.get(MEMBER_NAME + eventId);
			plugin.ui(s -> s.competitionView.showCompetition(event, name, false));
		}
	}

	private void eventChanged(String eventId)
	{
		TsgHubSidebarPanel sidebar = plugin.sidebar();
		if (sidebar == null) return;
		if (isActiveEvent(eventId) && plugin.sessions().wantsBoardUpdates(sidebar)) plugin.sessions().refreshBoard();
		if (eventId.equals(sidebar.competitionView.openCompetitionId())) plugin.sessions().openCompetition(eventId);
	}

	private boolean isActiveEvent(String eventId)
	{
		return eventId.equals(plugin.session().get(EVENT_ID));
	}

	private void refreshRules(String version)
	{
		if (version.isEmpty() || version.equals(TsgHubRules.get().version)) return;
		plugin.api().call("GET", "/v1/rules", null, null).thenAcceptAsync(rules -> TsgHubRules.save(rules, rulesCache), executor);
	}

	void loadCachedRules(Filepath cache)
	{
		rulesCache = cache;
		TsgHubRules.loadCached(cache, gson);
	}
}
