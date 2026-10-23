package com.tsghub;

import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.MEMBER_NAME;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubSession.TOKEN;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;

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
		socket = new TsgHubSocket(http, gson, serviceUrl, executor, new TsgHubSocket.Listener()
		{
			@Override public void onReady(String rulesVersion)
			{
				refreshRules(rulesVersion);
				plugin.signals().resend();
				plugin.reports().resend();
			}
			@Override public void onChanged(String topic, String eventId) { changed(topic, eventId); }
			@Override public void onAdminRevoked(String token) { plugin.keys().recheck(token); }
			@Override public void onAnnouncement(String text) { plugin.chat().announce(text); }
			@Override public void onUpdateAvailable(String version) { plugin.chat().updateAvailable(version); }
			@Override public void onSnapshot(JsonObject message) { snapshot(message); }
			@Override public void onConnection(TsgHubSocket.State state) { plugin.ui(s -> s.setConnection(state)); }
			@Override public void onAck(String id)
			{
				plugin.signals().acked(id);
				plugin.reports().acked(id);
			}
			@Override public void onRejected(String id, int status) { plugin.reports().failed(id, status); }
			@Override public void onSubscribed() { plugin.signals().resend(); }
			@Override public void onReply(JsonObject message) { plugin.chat().reply(message); }
			@Override public void onXpRequest(String skills) { plugin.tracker().reportXp(skills); }
			@Override public void onGroupHeartbeat(JsonObject message) { plugin.groups().beaten(message); }
			@Override public void onGroupRejected(int status) { plugin.groups().beatRejected(status); }
		});
		topics.put("groups", new Topic((data, eventId) -> plugin.groups().apply(TsgHubJson.array(data, "groups")), eventId -> plugin.groups().autoRefresh()));
		topics.put("presence", new Topic(this::presence, eventId -> plugin.presence().autoRefresh()));
		topics.put("positions", new Topic((data, eventId) -> plugin.memberMap().apply(TsgHubJson.array(data, "members")), eventId -> { }));
		topics.put("drops", new Topic((data, eventId) -> plugin.drops().onSnapshot(TsgHubJson.array(data, "drops")), eventId -> plugin.drops().autoRefresh()));
		topics.put("announcements", new Topic((data, eventId) -> plugin.announcements().apply(TsgHubJson.array(data, "announcements")), eventId -> plugin.announcements().load()));
		topics.put("events", new Topic((data, eventId) -> plugin.sessions().applyClanEvents(TsgHubJson.array(data, "events")), eventId -> plugin.sessions().loadClanEvents()));
		topics.put("notifications", new Topic((data, eventId) -> {
			if (isActiveEvent(eventId)) plugin.chat().notifications(TsgHubJson.array(data, "notifications"));
		}, eventId -> {
			if (isActiveEvent(eventId)) plugin.sessions().pollNotifications();
		}));
		topics.put("event", new Topic(this::eventSnapshot, this::eventChanged));
	}

	TsgHubSocket socket()
	{
		return socket;
	}

	boolean live()
	{
		return socket.isLive();
	}

	Runnable unlessLive(Runnable poll)
	{
		return () -> {
			if (!socket.isLive()) poll.run();
		};
	}

	void syncNow()
	{
		if (executor.isShutdown()) return;
		executor.execute(this::tick);
	}

	void tick()
	{
		TsgHubReports reports = plugin.reports();
		if (!plugin.sharingEnabled()) reports.clear();
		reports.flush();
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
		if ((sidebar.wantsAutoRefresh() || plugin.bingo().wantsBoard()) && !eventId.isEmpty()) watched.add("event:" + eventId);
		if (competitionId != null) watched.add("event:" + competitionId);
		return watched;
	}

	private void snapshot(JsonObject message)
	{
		Topic topic = topics.get(TsgHubJson.str(message, "topic"));
		if (topic != null) topic.snapshot.accept(TsgHubJson.object(message, "data"), TsgHubJson.str(message, "eventId"));
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
			plugin.presence().applyDelta(data.has("presence") ? TsgHubJson.object(data, "presence") : null, data.has("members") ? TsgHubJson.object(data, "members") : null);
			return;
		}
		if (!data.has("presence") || !data.get("presence").isJsonObject()) return;
		JsonObject notes = data.has("members") && data.get("members").isJsonObject() ? data.getAsJsonObject("members") : null;
		plugin.presence().apply(TsgHubJson.array(data.getAsJsonObject("presence"), "members"), notes == null ? null : TsgHubJson.array(notes, "members"));
	}

	private void eventSnapshot(JsonObject data, String eventId)
	{
		if (!data.has("event") || !data.get("event").isJsonObject()) return;
		JsonObject event = data.getAsJsonObject("event");
		TsgHubSidebarPanel sidebar = plugin.sidebar();
		if (sidebar == null) return;
		TsgHubSession session = plugin.session();
		if (isActiveEvent(eventId) && (sidebar.wantsAutoRefresh() || plugin.bingo().wantsBoard())) plugin.sessions().applyBoard(eventId, session.get(TOKEN), event, false);
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
		if (isActiveEvent(eventId) && (sidebar.wantsAutoRefresh() || plugin.bingo().wantsBoard())) plugin.sessions().refreshBoard();
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
