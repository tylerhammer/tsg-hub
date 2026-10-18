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
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;

final class TsgHubLiveSync
{
	private interface Topic
	{
		void snapshot(JsonObject data, String eventId);

		void changed(String eventId);
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
				resyncAdmin();
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
		topics.put("groups", topic((data, eventId) -> plugin.groups().apply(TsgHubJson.array(data, "groups")), eventId -> plugin.groups().autoRefresh()));
		topics.put("presence", topic(this::presence, eventId -> plugin.presence().autoRefresh()));
		topics.put("positions", topic((data, eventId) -> plugin.memberMap().apply(TsgHubJson.array(data, "members")), eventId -> { }));
		topics.put("drops", topic((data, eventId) -> plugin.drops().onSnapshot(TsgHubJson.array(data, "drops")), eventId -> plugin.drops().autoRefresh()));
		topics.put("announcements", topic((data, eventId) -> plugin.announcements().apply(TsgHubJson.array(data, "announcements")), eventId -> plugin.announcements().load()));
		topics.put("events", topic((data, eventId) -> {
			plugin.sessions().applyClanEvents(TsgHubJson.array(data, "events"));
			reloadManagedEvents();
		}, eventId -> {
			plugin.sessions().loadClanEvents();
			reloadManagedEvents();
		}));
		topics.put("notifications", topic((data, eventId) -> {
			if (isActiveEvent(eventId)) plugin.chat().notifications(TsgHubJson.array(data, "notifications"));
		}, eventId -> {
			if (isActiveEvent(eventId)) plugin.sessions().pollNotifications();
		}));
		topics.put("event", topic(this::eventSnapshot, this::eventChanged));
	}

	private static Topic topic(BiConsumer<JsonObject, String> snapshot, Consumer<String> changed)
	{
		return new Topic()
		{
			@Override public void snapshot(JsonObject data, String eventId) { snapshot.accept(data, eventId); }
			@Override public void changed(String eventId) { changed.accept(eventId); }
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
		TsgHubOrganizer organizer = plugin.organizer();
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
		Map<String, String> memberTokens = new HashMap<>(tokens);
		boolean adminOpen = plugin.windows().adminOpen();
		String organizerEventId = adminOpen ? plugin.sessions().organizerEventId() : "";
		String organizerToken = organizerEventId.isEmpty() ? "" : organizer.credential(organizerEventId);
		if (!organizerToken.isEmpty()) tokens.putIfAbsent(organizerEventId, organizerToken);
		if (organizer.refreshPending() && adminOpen) organizer.liveRefresh();
		socket.sync(tokens, clan.inClanChat());
		socket.admin(plugin.keys().adminKey());
		socket.watch(watched(sidebar, competitionId));
		plugin.signals().flush(memberTokens);
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
		if (sidebar.wantsAutoRefresh() && !eventId.isEmpty()) watched.add("event:" + eventId);
		if (competitionId != null) watched.add("event:" + competitionId);
		return watched;
	}

	private void snapshot(JsonObject message)
	{
		Topic topic = topics.get(TsgHubJson.str(message, "topic"));
		if (topic != null) topic.snapshot(TsgHubJson.object(message, "data"), TsgHubJson.str(message, "eventId"));
	}

	private void changed(String name, String eventId)
	{
		Topic topic = topics.get(name);
		if (topic != null) topic.changed(eventId);
	}

	private void presence(JsonObject data, String eventId)
	{
		if (!data.has("presence") || !data.get("presence").isJsonObject()) return;
		JsonObject notes = data.has("members") && data.get("members").isJsonObject() ? data.getAsJsonObject("members") : null;
		plugin.presence().apply(TsgHubJson.array(data.getAsJsonObject("presence"), "members"), notes == null ? null : TsgHubJson.array(notes, "members"));
	}

	private void eventSnapshot(JsonObject data, String eventId)
	{
		if (!data.has("event") || !data.get("event").isJsonObject()) return;
		JsonObject event = data.getAsJsonObject("event");
		refreshOrganizerEvent(eventId);
		TsgHubSidebarPanel sidebar = plugin.sidebar();
		if (sidebar == null) return;
		TsgHubSession session = plugin.session();
		if (isActiveEvent(eventId) && sidebar.wantsAutoRefresh()) plugin.sessions().applyBoard(eventId, session.get(TOKEN), event, false);
		if (eventId.equals(sidebar.competitionView.openCompetitionId()))
		{
			String name = session.get(MEMBER_NAME + eventId);
			plugin.ui(s -> s.competitionView.showCompetition(event, name, false));
		}
	}

	private void eventChanged(String eventId)
	{
		refreshOrganizerEvent(eventId);
		TsgHubSidebarPanel sidebar = plugin.sidebar();
		if (sidebar == null) return;
		if (isActiveEvent(eventId) && sidebar.wantsAutoRefresh()) plugin.sessions().refreshBoard();
		if (eventId.equals(sidebar.competitionView.openCompetitionId())) plugin.sessions().openCompetition(eventId);
	}

	private boolean isActiveEvent(String eventId)
	{
		return eventId.equals(plugin.session().get(EVENT_ID));
	}

	private void refreshOrganizerEvent(String eventId)
	{
		if (plugin.windows().adminOpen() && eventId.equals(plugin.sessions().organizerEventId())) plugin.organizer().liveRefresh();
	}

	private void reloadManagedEvents()
	{
		if (plugin.windows().adminOpen()) plugin.organizer().loadManagedEvents();
	}

	private void resyncAdmin()
	{
		if (!plugin.windows().adminOpen()) return;
		plugin.organizer().loadManagedEvents();
		plugin.organizer().liveRefresh();
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
