package com.tsghub;

import static com.tsghub.TsgHubSession.DISPLAY_NAME;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.MEMBER_NAME;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubSession.SIGNUP_NOTE;
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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class TsgHubSessions
{
	private final TsgHubPlugin plugin;
	private final ScheduledExecutorService executor;
	private final Map<String, Integer> syncedClanRanks = new ConcurrentHashMap<>();
	private volatile List<String> joinedCompetitions = Collections.emptyList();
	private volatile String syncedIdentity = "";

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

	private String joinBlocker(TsgHubClan clan)
	{
		if (!clan.isHubClan()) return "TSG Hub is only for members of the " + clan.hubName() + " clan.";
		if (!plugin.sharingEnabled()) return "Turn on sharing first.";
		if (clan.playerName().isEmpty()) return "Log in to RuneScape before joining.";
		if (clan.name().isEmpty()) return "No clan detected. Log in to a character in your clan first.";
		return null;
	}

	void join(String code, String expectedEventId)
	{
		TsgHubClan clan = plugin.clan();
		String blocker = joinBlocker(clan);
		if (blocker != null)
		{
			joinFailed(blocker);
			return;
		}
		JsonObject payload = clan.memberBody(clan.playerName());
		payload.addProperty("code", code.trim());
		if (expectedEventId != null && !expectedEventId.trim().isEmpty()) payload.addProperty("eventId", expectedEventId.trim());
		api().call("POST", "/v1/join", null, payload).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			String joinedEventId = result.getAsJsonObject("member").get("eventId").getAsString();
			String memberToken = result.get("token").getAsString();
			String memberName = result.getAsJsonObject("member").get("displayName").getAsString();
			session().set(MEMBER_TOKEN + joinedEventId, memberToken);
			session().set(MEMBER_NAME + joinedEventId, memberName);
			setActiveEvent(joinedEventId, memberToken, memberName);
			plugin.memberStatus("You're in! Progress now counts for your team.", Tone.SUCCESS);
			refreshBoard(true);
		}, e -> joinFailed(TsgHubFormat.friendlyError(e))), executor);
	}

	private void joinFailed(String message)
	{
		plugin.ui(s -> s.eventsView.joinFailed(message));
	}

	void signup(String eventId, String note)
	{
		TsgHubClan clan = plugin.clan();
		String blocker = joinBlocker(clan);
		if (blocker != null)
		{
			signupFailed(blocker);
			return;
		}
		JsonObject payload = clan.memberBody(clan.playerName());
		payload.addProperty("note", note.trim());
		api().call("POST", "/v1/events/" + eventId + "/signup", null, payload).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			JsonObject member = result.getAsJsonObject("member");
			String memberToken = result.get("token").getAsString();
			String memberName = member.get("displayName").getAsString();
			session().set(MEMBER_TOKEN + eventId, memberToken);
			session().set(MEMBER_NAME + eventId, memberName);
			session().set(SIGNUP_NOTE + eventId, TsgHubJson.str(member, "note"));
			setActiveEvent(eventId, memberToken, memberName);
			plugin.memberStatus("Signed up! An admin will put you on a team.", Tone.SUCCESS);
			applyBoard(eventId, memberToken, result.getAsJsonObject("event"), true);
		}, e -> signupFailed(TsgHubFormat.friendlyError(e))), executor);
	}

	void editNote(String eventId, String note)
	{
		String token = session().memberToken(eventId);
		if (token.isEmpty()) return;
		JsonObject payload = new JsonObject();
		payload.addProperty("note", note.trim());
		api().call("PATCH", "/v1/events/" + eventId + "/signup", token, payload).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			session().set(SIGNUP_NOTE + eventId, TsgHubJson.str(result.getAsJsonObject("member"), "note"));
			plugin.memberStatus("Note saved.", Tone.SUCCESS);
			applyBoard(eventId, token, result.getAsJsonObject("event"), true);
		}, e ->
		{
			signupFailed(TsgHubFormat.friendlyError(e));
			refreshBoard();
		}), executor);
	}

	private void signupFailed(String message)
	{
		plugin.ui(s -> s.eventsView.signupFailed(message));
	}

	void withdraw(String eventId)
	{
		String token = session().memberToken(eventId);
		if (token.isEmpty()) return;
		plugin.sidebarBusy(true);
		api().call("DELETE", "/v1/events/" + eventId + "/signup", token, null).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			forgetEvent(eventId);
			plugin.memberStatus("You've left the sign-ups.", Tone.SUCCESS);
			loadClanEvents();
		}, e ->
		{
			signupFailed(TsgHubFormat.friendlyError(e));
			refreshBoard();
		}, () -> plugin.sidebarBusy(false)), executor);
	}

	void leaveEvent(String eventId)
	{
		String revokeToken = session().memberToken(eventId);
		if (!revokeToken.isEmpty()) disconnect(eventId, revokeToken);
		session().clear(MEMBER_TOKEN + eventId, MEMBER_NAME + eventId, SIGNUP_NOTE + eventId);
		if (eventId.equals(session().get(EVENT_ID))) session().clear(TOKEN, EVENT_ID);
		plugin.memberStatus("Disconnected. Rejoin anytime with your team code.", Tone.SUCCESS);
		plugin.ui(s -> s.eventsView.showEventList());
		loadClanEvents();
	}

	void clearAll()
	{
		joinedCompetitions = Collections.emptyList();
		revokeRemote();
		TsgHubSession session = session();
		session.clear(TOKEN, EVENT_ID);
		session.removePrefix(MEMBER_TOKEN);
		session.removePrefix(MEMBER_NAME);
		session.removePrefix(SIGNUP_NOTE);
		syncedClanRanks.clear();
		plugin.bingo().clear();
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
		api().call("GET", "/v1/events/" + eventId + "?sets=1", token, null).whenCompleteAsync(TsgHubApi.handle(response ->
		{
			JsonObject event = response.getAsJsonObject("event");
			applyBoard(eventId, token, event, open);
		}, e -> eventLoadFailed(eventId, e, "Couldn't update the board. "), () -> plugin.sidebarBusy(false)), executor);
	}

	boolean claimTile(String eventId, String taskId, String note)
	{
		return sendTileClaim("tile.claim", "POST", eventId, taskId, note);
	}

	boolean dropTileClaim(String eventId, String taskId)
	{
		return sendTileClaim("tile.unclaim", "DELETE", eventId, taskId, null);
	}

	private boolean sendTileClaim(String type, String method, String eventId, String taskId, String note)
	{
		String token = session().get(TOKEN);
		if (!plugin.sharingInClan() || token.isEmpty() || !eventId.equals(session().get(EVENT_ID)) || executor.isShutdown()) return false;
		JsonObject body = TsgHubTileClaims.body(eventId, taskId, note);
		executor.execute(() -> plugin.tracker().report(type, method, TsgHubTileClaims.path(eventId, taskId), token, body));
		return true;
	}

	void applyBoard(String eventId, String token, JsonObject event, boolean open)
	{
		syncClanRank(eventId, token, event);
		String name = session().get(DISPLAY_NAME);
		if (TsgHubEvents.awaitingTeam(event, name))
		{
			String note = session().get(SIGNUP_NOTE + eventId);
			plugin.ui(s -> s.eventsView.showSignedUp(event, note, open));
			return;
		}
		session().clear(SIGNUP_NOTE + eventId);
		plugin.ui(s ->
		{
			boolean placed = s.view == TsgHubSidebarPanel.View.SIGNUP;
			if (placed) s.setStatus("You're on a team! Progress now counts for your team.", Tone.SUCCESS);
			s.boardView.showBoard(event, name, open || placed);
			if (eventId.equals(session().get(EVENT_ID))) plugin.bingo().apply(event, name);
		});
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
		if (sidebar.wantsAutoRefresh() || plugin.bingo().wantsBoard()) refreshBoard();
		String competitionId = sidebar.competitionView.openCompetitionId();
		if (competitionId != null) openCompetition(competitionId);
	}

	void participate(String eventId)
	{
		TsgHubClan clan = plugin.clan();
		if (!plugin.sharingInClan() || clan.playerName().isEmpty()) return;
		JsonObject body = clan.memberBody(clan.playerName());
		plugin.sidebarBusy(true);
		api().call("POST", "/v1/events/" + eventId + "/participate", null, body).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			session().set(MEMBER_TOKEN + eventId, result.get("token").getAsString());
			session().set(MEMBER_NAME + eventId, result.getAsJsonObject("member").get("displayName").getAsString());
			plugin.liveSync().syncNow();
			plugin.memberStatus("You're in! Your progress counts from now.", Tone.SUCCESS);
			loadClanEvents();
			openCompetition(eventId, true);
		}, e -> plugin.ui(s -> s.competitionView.competitionJoinFailed(TsgHubFormat.friendlyError(e))), () -> plugin.sidebarBusy(false)), executor);
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
		api().call("GET", "/v1/events/" + eventId, token, null).whenCompleteAsync(TsgHubApi.handle(response ->
		{
			JsonObject event = response.getAsJsonObject("event");
			plugin.ui(s -> s.competitionView.showCompetition(event, name, open));
		}, e -> eventLoadFailed(eventId, e, "Couldn't load the leaderboard. "), () -> plugin.sidebarBusy(false)), executor);
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
		for (String key : session.keysWithPrefix(MEMBER_TOKEN)) memberTokens.add(session.get(key));
		if (!session.get(TOKEN).isEmpty()) memberTokens.add(session.get(TOKEN));
		for (String key : session.keysWithPrefix(MEMBER_NAME)) renameStored(key, name);
		renameStored(DISPLAY_NAME, name);
		JsonObject body = new JsonObject();
		body.addProperty("displayName", name);
		body.addProperty("clanName", clan.name());
		body.addProperty("accountHash", hash);
		body.add("memberTokens", memberTokens);
		api().call("POST", "/v1/identity", null, body).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			if (TsgHubJson.bool(result, "updated") && !session().get(EVENT_ID).isEmpty() && !plugin.live()) refreshBoard();
		}, e -> syncedIdentity = ""), executor);
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
			plugin.ui(s -> s.eventsView.setEvents(new JsonArray()));
			return;
		}
		plugin.sidebarBusy(true);
		api().call("GET", "/v1/events?clanName=" + TsgHubApi.encode(clanName), null, null).whenCompleteAsync(TsgHubApi.handle(response ->
		{
			applyClanEvents(response.getAsJsonArray("events"));
		}, e -> plugin.memberError("Couldn't load events. ", e), () -> plugin.sidebarBusy(false)), executor);
	}

	void applyClanEvents(JsonArray events)
	{
		if (events == null) return;
		TsgHubSession session = session();
		for (JsonObject event : TsgHubJson.objects(events))
		{
			String eventId = event.get("id").getAsString();
			event.addProperty("joined", !session.memberToken(eventId).isEmpty());
		}
		joinedCompetitions = joinedCompetitionIds(events);
		plugin.ui(s -> s.eventsView.setEvents(events));
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
		body.addProperty("eventId", eventId);
		body.addProperty("displayName", displayName);
		body.addProperty("clanName", clan.name());
		body.addProperty("clanRank", rank);
		syncedClanRanks.put(key, rank);
		plugin.tracker().report("clan-rank", "/v1/events/" + eventId + "/clan-rank", token, body);
	}

	void forgetEvent(String eventId)
	{
		TsgHubSession session = session();
		session.clear(MEMBER_TOKEN + eventId, MEMBER_NAME + eventId, SIGNUP_NOTE + eventId);
		if (eventId.equals(session.get(EVENT_ID)))
		{
			session.clear(TOKEN, EVENT_ID);
			plugin.bingo().clear();
			plugin.ui(s -> s.boardView.closeBoard());
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
