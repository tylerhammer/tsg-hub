package com.tsghub;

import static com.tsghub.TsgHubSession.DISPLAY_NAME;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.MEMBER_NAME;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubSession.ORGANIZER_EVENT_ID;
import static com.tsghub.TsgHubSession.ORGANIZER_NAME;
import static com.tsghub.TsgHubSession.ORGANIZER_TOKEN;
import static com.tsghub.TsgHubSession.TOKEN;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

final class TsgHubSessions
{
	private final TsgHubPlugin plugin;
	private final ScheduledExecutorService executor;
	private final Map<String, Integer> syncedClanRanks = new ConcurrentHashMap<>();
	private volatile List<String> joinedCompetitions = Collections.emptyList();
	private volatile String syncedIdentity = "";

	TsgHubSessions(TsgHubPlugin plugin, ScheduledExecutorService executor)
	{
		this.plugin = plugin;
		this.executor = executor;
	}

	private TsgHubSession session()
	{
		return plugin.session();
	}

	private TsgHubApi api()
	{
		return plugin.api();
	}

	List<String> joinedCompetitions()
	{
		return joinedCompetitions;
	}

	String organizerEventId()
	{
		return session().get(ORGANIZER_EVENT_ID);
	}

	void profileChanged()
	{
		syncedClanRanks.clear();
		syncedIdentity = "";
		joinedCompetitions = Collections.emptyList();
	}

	void loggedOut()
	{
		joinedCompetitions = Collections.emptyList();
	}

	void reset()
	{
		syncedClanRanks.clear();
		syncedIdentity = "";
		joinedCompetitions = Collections.emptyList();
	}

	void join(String code, String expectedEventId)
	{
		TsgHubClan clan = plugin.clan();
		if (!clan.isHubClan()) { joinFailed("TSG Hub is only for members of the " + clan.hubName() + " clan."); return; }
		if (!plugin.sharingEnabled()) { joinFailed("Turn on sharing first."); return; }
		if (clan.playerName().isEmpty())
		{
			joinFailed("Log in to RuneScape before joining.");
			return;
		}
		if (clan.name().isEmpty())
		{
			joinFailed("No clan detected. Log in to a character in your clan first.");
			return;
		}
		JsonObject payload = clan.memberBody(clan.playerName());
		payload.addProperty("code", code.trim());
		if (expectedEventId != null && !expectedEventId.trim().isEmpty()) payload.addProperty("eventId", expectedEventId.trim());
		api().call("POST", "/v1/join", plugin.keys().adminKey(), payload).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				String joinedEventId = result.getAsJsonObject("member").get("eventId").getAsString();
				String memberToken = result.get("token").getAsString();
				String memberName = result.getAsJsonObject("member").get("displayName").getAsString();
				session().set(MEMBER_TOKEN + joinedEventId, memberToken);
				session().set(MEMBER_NAME + joinedEventId, memberName);
				setActiveEvent(joinedEventId, memberToken, memberName);
				plugin.memberStatus("You're in! Progress now counts for your team.", Tone.SUCCESS);
				refreshBoard(true);
			}
			catch (Exception e) { joinFailed(TsgHubFormat.friendlyError(e)); }
		}, executor);
	}

	private void joinFailed(String message)
	{
		plugin.ui(s -> s.joinFailed(message));
	}

	void leaveEvent(String eventId)
	{
		String revokeToken = session().memberToken(eventId);
		if (!revokeToken.isEmpty()) disconnect(eventId, revokeToken);
		session().clear(MEMBER_TOKEN + eventId, MEMBER_NAME + eventId);
		if (eventId.equals(session().get(EVENT_ID))) session().clear(TOKEN, EVENT_ID);
		plugin.memberStatus("Disconnected. Rejoin anytime with your team code.", Tone.SUCCESS);
		plugin.ui(s -> s.showEventList());
		loadClanEvents();
	}

	void clearAll()
	{
		joinedCompetitions = Collections.emptyList();
		revokeRemote();
		TsgHubSession session = session();
		session.clear(TOKEN, EVENT_ID, ORGANIZER_EVENT_ID);
		session.removePrefix(MEMBER_TOKEN);
		session.removePrefix(MEMBER_NAME);
		session.removePrefix(ORGANIZER_TOKEN);
		session.removePrefix(ORGANIZER_NAME);
		syncedClanRanks.clear();
	}

	private void revokeRemote()
	{
		if (executor.isShutdown()) return;
		Map<String, String> sessions = new HashMap<>();
		for (String key : session().keysWithPrefix(MEMBER_TOKEN))
		{
			String eventId = key.substring(MEMBER_TOKEN.length());
			sessions.put(eventId, session().get(key));
		}
		String activeEventId = session().get(EVENT_ID);
		String activeToken = session().get(TOKEN);
		if (!activeEventId.isEmpty() && !activeToken.isEmpty()) sessions.putIfAbsent(activeEventId, activeToken);
		sessions.forEach(this::disconnect);
	}

	private void disconnect(String eventId, String token)
	{
		// Local disconnect still succeeds while the service is unavailable.
		api().call("POST", "/v1/events/" + eventId + "/disconnect", token, new JsonObject());
	}

	void refreshBoard()
	{
		refreshBoard(false);
	}

	private void refreshBoard(boolean open)
	{
		if (!plugin.sharingInClan()) return;
		String eventId = session().get(EVENT_ID);
		String token = session().get(TOKEN);
		if (eventId.isEmpty() || token.isEmpty()) return;
		plugin.sidebarBusy(true);
		api().call("GET", "/v1/events/" + eventId, token, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject event = TsgHubApi.result(response, error).getAsJsonObject("event");
				applyBoard(eventId, token, event, open);
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't update the board. "); }
			finally { plugin.sidebarBusy(false); }
		}, executor);
	}

	void applyBoard(String eventId, String token, JsonObject event, boolean open)
	{
		syncClanRank(eventId, token, event);
		plugin.ui(s -> s.showBoard(event, session().get(DISPLAY_NAME), open));
	}

	private void eventLoadFailed(String eventId, Exception e, String failure)
	{
		String message = e.getMessage() == null ? "" : e.getMessage();
		if (message.startsWith("Event not found") || message.startsWith("Connect to this event first"))
		{
			// Event or team was deleted: forget it instead of failing every refresh.
			forgetEvent(eventId);
			plugin.memberStatus("That event is no longer available.", Tone.ERROR);
			loadClanEvents();
		}
		else plugin.memberError(failure, e);
	}

	void autoRefreshBoard(TsgHubSidebarPanel sidebar)
	{
		if (sidebar == null) return;
		if (sidebar.wantsAutoRefresh()) refreshBoard();
		String competitionId = sidebar.openCompetitionId();
		if (competitionId != null) openCompetition(competitionId);
	}

	void participate(String eventId)
	{
		TsgHubClan clan = plugin.clan();
		if (!plugin.sharingInClan() || clan.playerName().isEmpty()) return;
		JsonObject body = clan.memberBody(clan.playerName());
		plugin.sidebarBusy(true);
		api().call("POST", "/v1/events/" + eventId + "/participate", plugin.keys().adminKey(), body).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				session().set(MEMBER_TOKEN + eventId, result.get("token").getAsString());
				session().set(MEMBER_NAME + eventId, result.getAsJsonObject("member").get("displayName").getAsString());
				plugin.liveSync().syncNow();
				plugin.memberStatus("You're in! Your progress counts from now.", Tone.SUCCESS);
				loadClanEvents();
				openCompetition(eventId, true);
			}
			catch (Exception e) { plugin.ui(s -> s.competitionJoinFailed(TsgHubFormat.friendlyError(e))); }
			finally { plugin.sidebarBusy(false); }
		}, executor);
	}

	void openCompetition(String eventId)
	{
		openCompetition(eventId, false);
	}

	void openCompetition(String eventId, boolean open)
	{
		String token = session().get(MEMBER_TOKEN + eventId);
		if (token.isEmpty()) return;
		String name = session().get(MEMBER_NAME + eventId);
		plugin.sidebarBusy(true);
		api().call("GET", "/v1/events/" + eventId, token, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject event = TsgHubApi.result(response, error).getAsJsonObject("event");
				plugin.ui(s -> s.showCompetition(event, name, open));
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't load the leaderboard. "); }
			finally { plugin.sidebarBusy(false); }
		}, executor);
	}

	void syncIdentity()
	{
		TsgHubClan clan = plugin.clan();
		String name = clan.playerName();
		String hash = clan.accountHash();
		if (name.isEmpty() || hash.isEmpty() || clan.name().isEmpty()) return;
		String identity = hash + ":" + name;
		if (identity.equals(syncedIdentity)) return;
		syncedIdentity = identity;
		TsgHubSession session = session();
		JsonArray memberTokens = new JsonArray();
		JsonArray organizerTokens = new JsonArray();
		for (String key : session.keysWithPrefix(MEMBER_TOKEN)) memberTokens.add(session.get(key));
		if (!session.get(TOKEN).isEmpty()) memberTokens.add(session.get(TOKEN));
		for (String key : session.keysWithPrefix(ORGANIZER_TOKEN)) organizerTokens.add(session.get(key));
		for (String key : session.keysWithPrefix(MEMBER_NAME)) renameStored(key, name);
		for (String key : session.keysWithPrefix(ORGANIZER_NAME)) renameStored(key, name);
		renameStored(DISPLAY_NAME, name);
		JsonObject body = new JsonObject();
		body.addProperty("displayName", name);
		body.addProperty("clanName", clan.name());
		body.addProperty("accountHash", hash);
		body.add("memberTokens", memberTokens);
		body.add("organizerTokens", organizerTokens);
		api().call("POST", "/v1/identity", null, body).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				if (TsgHubJson.bool(result, "updated") && !session().get(EVENT_ID).isEmpty()) refreshBoard();
			}
			catch (Exception e) { syncedIdentity = ""; }
		}, executor);
	}

	private void renameStored(String key, String name)
	{
		String stored = session().get(key);
		if (!stored.isEmpty() && !PlayerNames.samePlayer(stored, name)) session().set(key, name);
	}

	void loadClanEvents()
	{
		if (!plugin.sharingInClan()) return;
		String clanName = plugin.clan().name();
		if (clanName.trim().isEmpty())
		{
			plugin.ui(s -> s.setEvents(new JsonArray()));
			return;
		}
		plugin.sidebarBusy(true);
		api().call("GET", "/v1/events?clanName=" + TsgHubApi.encode(clanName), plugin.keys().adminKey(), null).whenCompleteAsync((response, error) -> {
			try
			{
				applyClanEvents(TsgHubApi.result(response, error).getAsJsonArray("events"));
			}
			catch (Exception e) { plugin.memberError("Couldn't load events. ", e); }
			finally { plugin.sidebarBusy(false); }
		}, executor);
	}

	void applyClanEvents(JsonArray events)
	{
		if (events == null) return;
		TsgHubSession session = session();
		for (JsonObject event : TsgHubJson.objects(events))
		{
			String eventId = event.get("id").getAsString();
			String savedToken = session.get(MEMBER_TOKEN + eventId);
			if (savedToken.isEmpty() && eventId.equals(session.get(EVENT_ID)))
			{
				savedToken = session.get(TOKEN);
				if (!savedToken.isEmpty())
				{
					session.set(MEMBER_TOKEN + eventId, savedToken);
					session.set(MEMBER_NAME + eventId, session.get(DISPLAY_NAME));
				}
			}
			event.addProperty("joined", !savedToken.isEmpty());
		}
		joinedCompetitions = joinedCompetitionIds(events);
		plugin.ui(s -> s.setEvents(events));
	}

	private List<String> joinedCompetitionIds(JsonArray events)
	{
		List<String> ids = new ArrayList<>();
		for (JsonObject event : TsgHubJson.objects(events))
		{
			String type = TsgHubJson.str(event, "type");
			if (!("skill".equals(type) || "boss".equals(type)) || "ended".equals(TsgHubJson.str(event, "status"))) continue;
			String id = TsgHubJson.str(event, "id");
			if (!session().get(MEMBER_TOKEN + id).isEmpty()) ids.add(id);
		}
		return ids;
	}

	void activateEvent(String eventId)
	{
		activateEvent(eventId, true);
	}

	void activateEvent(String eventId, boolean open)
	{
		String token = session().memberToken(eventId);
		if (token.isEmpty())
		{
			plugin.memberStatus("Join this event with your team code first.", Tone.ERROR);
			return;
		}
		setActiveEvent(eventId, token, session().memberName(eventId));
		refreshBoard(open);
	}

	private void setActiveEvent(String eventId, String token, String displayName)
	{
		session().set(TOKEN, token);
		session().set(EVENT_ID, eventId);
		session().set(DISPLAY_NAME, displayName);
		plugin.liveSync().syncNow();
	}

	private void syncClanRank(String eventId, String token, JsonObject event)
	{
		TsgHubClan clan = plugin.clan();
		String displayName = clan.playerName();
		String memberName = session().get(DISPLAY_NAME);
		String eventClan = TsgHubJson.str(event, "clanName");
		if (token.isEmpty() || displayName.isEmpty() || !PlayerNames.samePlayer(displayName, memberName)
			|| eventClan.isEmpty() || !eventClan.equalsIgnoreCase(clan.name())) return;
		int rank = clan.rank();
		String key = eventId + ":" + PlayerNames.key(displayName);
		Integer synced = syncedClanRanks.get(key);
		if (synced != null && synced == rank) return;
		JsonObject body = new JsonObject();
		body.addProperty("displayName", displayName);
		body.addProperty("clanName", clan.name());
		body.addProperty("clanRank", rank);
		api().call("POST", "/v1/events/" + eventId + "/clan-rank", token, body).thenRun(() -> syncedClanRanks.put(key, rank));
	}

	void forgetEvent(String eventId)
	{
		TsgHubSession session = session();
		session.clear(ORGANIZER_TOKEN + eventId, ORGANIZER_NAME + eventId, MEMBER_TOKEN + eventId, MEMBER_NAME + eventId);
		if (eventId.equals(session.get(ORGANIZER_EVENT_ID))) session.clear(ORGANIZER_EVENT_ID);
		if (eventId.equals(session.get(EVENT_ID)))
		{
			session.clear(TOKEN, EVENT_ID);
			plugin.ui(s -> s.closeBoard());
		}
	}

	void pollNotifications()
	{
		TsgHubClan clan = plugin.clan();
		if (!plugin.sharingInClan() || executor.isShutdown()) return;
		String token = session().get(TOKEN);
		String eventId = session().get(EVENT_ID);
		if (token.isEmpty() || eventId.isEmpty()) return;
		String path = "/v1/events/" + eventId + "/notifications?inClanChat=" + clan.inClanChat() + "&clanName=" + TsgHubApi.encode(clan.name());
		api().call("GET", path, token, null).thenAcceptAsync(result -> {
			if (plugin.chat().notifications(result.getAsJsonArray("notifications"))) refreshBoard();
		}, executor);
	}
}
