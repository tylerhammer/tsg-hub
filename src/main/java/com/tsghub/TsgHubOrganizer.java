package com.tsghub;

import static com.tsghub.TsgHubSession.ORGANIZER_EVENT_ID;
import static com.tsghub.TsgHubSession.ORGANIZER_NAME;
import static com.tsghub.TsgHubSession.ORGANIZER_TOKEN;
import static com.tsghub.TsgHubSession.TOKEN;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

@RequiredArgsConstructor
final class TsgHubOrganizer
{
	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executor;
	private final ExecutorService itemSearchExecutor;
	private final Supplier<TsgHubPanel> panel;
	private volatile boolean refreshPending;

	boolean refreshPending()
	{
		return refreshPending;
	}

	void selectEvent(String eventId)
	{
		if (eventId.trim().isEmpty()) return;
		plugin.session().set(ORGANIZER_EVENT_ID, eventId.trim());
		plugin.liveSync().syncNow();
		status("Loading event...", Tone.INFO);
		event(eventId.trim()).whenCompleteAsync(TsgHubApi.handle(event ->
		{
			ui(p -> p.openOrganizerEvent(event));
			status("", Tone.INFO);
		}, e -> error("Couldn't open event. ", e)), executor);
	}

	void loadManagedEvents()
	{
		if (!plugin.clan().isHubClan()) return;
		if (!plugin.sharingEnabled()) { status("Turn on sharing in the TSG Hub sidebar first.", Tone.ERROR); return; }
		if (!plugin.canManageOrganizerUi()) return;
		String credential = listCredential();
		if (credential.isEmpty()) { status("Couldn't load events. Join or create an event first.", Tone.ERROR); return; }
		request("GET", "/v1/managed-events", credential, null).whenCompleteAsync((response, error) -> {
			try { showManagedEvents(TsgHubApi.result(response, error).getAsJsonArray("events")); }
			catch (Exception e) { error("Couldn't load events. ", e); }
		}, executor);
	}

	void createEvent(String name, Instant startsAt, Instant endsAt, boolean hideScores, boolean hidden, String type, JsonObject typeConfig, JsonArray prizes)
	{
		if (!plugin.clan().isHubClan()) { eventFormFailed("TSG Hub is only for members of the " + plugin.clan().hubName() + " clan."); return; }
		if (!plugin.sharingEnabled()) { eventFormFailed("Turn on sharing in the TSG Hub sidebar first."); return; }
		String credential = plugin.keys().adminKey();
		if (credential.isEmpty())
		{
			eventFormFailed("Creating events needs a hub key with admin access from /hub key in the clan Discord.");
			return;
		}
		if (plugin.clan().name().isEmpty()) { eventFormFailed("No clan detected. Log in to a character in your clan first."); return; }
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) { eventFormFailed("Log in first so you're recorded as the admin."); return; }
		JsonObject body = new JsonObject();
		String creatorName = client.getLocalPlayer().getName();
		body.addProperty("name", name.trim());
		body.addProperty("clanName", plugin.clan().name());
		body.addProperty("createdByName", creatorName);
		body.addProperty("clanRank", plugin.clan().rank());
		addEventTimes(body, startsAt, endsAt);
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		body.addProperty("type", type);
		plugin.clan().addAccountHash(body);
		body.add("config", typeConfig == null ? new JsonObject() : typeConfig);
		body.add("prizes", prizes);
		status("Creating event...", Tone.INFO);
		request("POST", "/v1/events", credential, body).whenCompleteAsync((created, error) -> {
			try
			{
				JsonObject response = TsgHubApi.result(created, error);
				JsonObject result = response.getAsJsonObject("event");
				String id = result.get("id").getAsString();
				plugin.session().set(ORGANIZER_EVENT_ID, id);
				plugin.session().set(ORGANIZER_TOKEN + id, response.get("organizerToken").getAsString());
				plugin.session().set(ORGANIZER_NAME + id, creatorName);
				ui(p -> p.eventCreated(result));
				status("Event created. Add teams next.", Tone.SUCCESS);
				refreshListsUnlessLive();
			}
			catch (Exception e) { failed(e, null, this::eventFormFailed); }
		}, executor);
	}

	private static void addEventTimes(JsonObject body, Instant startsAt, Instant endsAt)
	{
		body.addProperty("startsAt", startsAt.toString());
		if (endsAt != null) body.addProperty("endsAt", endsAt.toString());
	}

	void updateEvent(String eventId, String name, Instant startsAt, Instant endsAt, boolean hideScores, boolean hidden, JsonObject typeConfig, JsonArray prizes)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name.trim());
		addEventTimes(body, startsAt, endsAt);
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		if (typeConfig != null) body.add("config", typeConfig);
		body.add("prizes", prizes);
		eventAction(eventId, "PATCH", "", body, "Saving...", "Event saved.", null, () -> refreshEvent(true));
	}

	void publishEvent(JsonObject event)
	{
		String eventId = TsgHubJson.str(event, "id");
		JsonObject body = new JsonObject();
		body.addProperty("name", TsgHubJson.str(event, "name"));
		Instant startsAt = TsgHubEvents.eventStart(event);
		if (startsAt == null) { status("Couldn't publish. The event has no start time.", Tone.ERROR); return; }
		addEventTimes(body, startsAt, TsgHubEvents.eventEnd(event));
		body.addProperty("hidden", false);
		eventAction(eventId, "PATCH", "", body, "Publishing...", "Event published. Players can see it now.", "Couldn't publish. ", () -> refreshEvent(true));
	}

	void endEvent(String eventId)
	{
		eventAction(eventId, "POST", "/end", null, "Ending event...", "Event ended.", "Couldn't end the event. ", () -> refreshEvent(true));
	}

	private void eventAction(String eventId, String method, String suffix, JsonObject body, String pending, String success, String failure, Runnable done)
	{
		String credential = credential(eventId);
		status(pending, Tone.INFO);
		request(method, "/v1/events/" + eventId + suffix, credential, body).whenCompleteAsync(TsgHubApi.handle(response ->
		{
			done.run();
			status(success, Tone.SUCCESS);
			refreshListsUnlessLive();
		}, e -> failed(e, failure, failure == null ? this::eventFormFailed : null)), executor);
	}

	private void refreshListsUnlessLive()
	{
		if (plugin.live()) return;
		loadManagedEvents();
		plugin.sessions().loadClanEvents();
	}

	private void failed(Exception e, String failure, Consumer<String> onError)
	{
		if (onError == null) error(failure, e);
		else
		{
			onError.accept(TsgHubFormat.friendlyError(e));
			status("", Tone.INFO);
		}
	}

	private void eventFormFailed(String message)
	{
		ui(p -> p.eventForm.eventFormFailed(message));
	}

	void createTeam(String name)
	{
		JsonObject body = new JsonObject(); body.addProperty("name", name.trim());
		eventRequest("POST", "/teams", body, "Team added. Copy its code and share it with the team.", result -> {
			ui(p -> p.teamsView.teamCreated());
			refreshEvent(false);
		}, null);
	}

	void renameTeam(String teamId, String name)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name.trim());
		eventRequest("PATCH", "/teams/" + teamId, body, "Team renamed.", result -> refreshEvent(false), null);
	}

	void saveTask(String taskId, JsonObject body)
	{
		Consumer<JsonObject> done = result -> {
			ui(p -> p.taskEditor.finishTaskEdit());
			refreshEvent(false);
		};
		if (taskId == null || taskId.isEmpty())
			eventRequest("POST", "/tasks", body, "Task added for every team.", done, this::taskFormFailed);
		else
			eventRequest("PATCH", "/tasks/" + taskId, body, "Task saved. Existing progress was rechecked.", done, this::taskFormFailed);
	}

	private void ui(Consumer<TsgHubPanel> action)
	{
		SwingUtilities.invokeLater(() -> action.accept(panel.get()));
	}

	private void taskFormFailed(String message)
	{
		ui(p -> p.taskEditor.taskFormFailed(message));
	}

	void deleteTask(String taskId)
	{
		eventRequest("DELETE", "/tasks/" + taskId, null, "Task deleted.", result -> {
			ui(p -> p.tasksView.taskDeleted(taskId));
			refreshEvent(false);
		}, null);
	}

	void deleteTeam(String teamId)
	{
		eventRequest("DELETE", "/teams/" + teamId, null, "Team deleted.", result -> refreshEvent(false), null);
	}

	void deleteEvent(String eventId)
	{
		eventAction(eventId, "DELETE", "", null, "Deleting event...", "Event deleted.", "Couldn't delete the event. ", () -> {
			plugin.sessions().forgetEvent(eventId);
			ui(p -> p.eventDeleted(eventId));
		});
	}

	void completeTask(String taskId, String teamId, String memberId, String note)
	{
		JsonObject body = new JsonObject(); body.addProperty("teamId", teamId); body.addProperty("note", note.trim());
		if (memberId != null && !memberId.isEmpty()) body.addProperty("memberId", memberId);
		eventRequest("POST", "/tasks/" + taskId + "/complete", body, "Marked complete.", result -> refreshEvent(false), null);
	}

	void reconcileTask(String taskId, boolean dryRun)
	{
		JsonObject body = new JsonObject();
		body.addProperty("dryRun", dryRun);
		eventRequest("POST", "/tasks/" + taskId + "/reconcile", body, "", result -> {
			if (dryRun)
			{
				ui(p -> p.tasksView.confirmReconcile(taskId, result));
				return;
			}
			int count = TsgHubJson.integer(result, "count", 0);
			status("Credited " + count + (count == 1 ? " match" : " matches") + " from the loot log.", Tone.SUCCESS);
			refreshEvent(false);
		}, null);
	}

	private void refreshEvent(boolean open)
	{
		String eventId = plugin.sessions().organizerEventId();
		if (eventId.isEmpty() || executor == null || executor.isShutdown()) return;
		event(eventId).whenCompleteAsync(TsgHubApi.handle(event ->
		{
			SwingUtilities.invokeLater(() -> {
				if (open) panel.get().openOrganizerEvent(event);
				else panel.get().showEvent(event);
			});
		}, e -> error("Couldn't refresh the event. ", e)), executor);
	}

	void liveRefresh()
	{
		refreshPending = false;
		String eventId = plugin.sessions().organizerEventId();
		if (eventId.isEmpty() || executor == null || executor.isShutdown()) return;
		event(eventId).thenAccept(event -> showLive(eventId, event));
	}

	void showLive(String eventId, JsonObject event)
	{
		SwingUtilities.invokeLater(() -> {
			if (!eventId.equals(plugin.sessions().organizerEventId())) return;
			if (panel.get().editingText()) refreshPending = true;
			else panel.get().showEvent(event);
		});
	}

	void showManagedEvents(JsonArray events)
	{
		ui(p -> p.eventListView.setManagedEvents(events));
	}

	void searchItems(String query, Consumer<List<ItemSuggestion>> callback)
	{
		if (itemSearchExecutor == null || itemSearchExecutor.isShutdown())
		{
			SwingUtilities.invokeLater(() -> callback.accept(Collections.emptyList()));
			return;
		}
		itemSearchExecutor.submit(() -> {
			List<ItemPrice> matches;
			try
			{
				matches = itemManager.search(query);
			}
			catch (RuntimeException e)
			{
				error("Item search failed. ", e);
				SwingUtilities.invokeLater(() -> callback.accept(Collections.emptyList()));
				return;
			}

			// canonicalize() calls getItemDefinition, which must run on the client thread.
			clientThread.invokeLater(() -> {
				List<ItemSuggestion> suggestions = new ArrayList<>();
				try
				{
					for (ItemPrice result : matches)
					{
						if (result.getName() == null || result.getName().trim().isEmpty()) continue;
						int id = itemManager.canonicalize(result.getId());
						if (suggestions.stream().noneMatch(item -> item.id == id)) suggestions.add(new ItemSuggestion(id, result.getName().trim()));
						if (suggestions.size() >= 30) break;
					}
				}
				catch (RuntimeException e) { error("Item search failed. ", e); }
				List<ItemSuggestion> result = suggestions;
				SwingUtilities.invokeLater(() -> callback.accept(result));
			});
		});
	}

	private void error(String failure, Exception e)
	{
		status(failure + TsgHubFormat.friendlyError(e), Tone.ERROR);
	}

	private void status(String message, Tone tone)
	{
		SwingUtilities.invokeLater(() -> { TsgHubPanel p = panel.get(); if (p != null) p.setStatus(message, tone); });
	}

	private void eventRequest(String method, String suffix, JsonObject payload, String success, Consumer<JsonObject> callback, Consumer<String> onError)
	{
		String eventId = plugin.sessions().organizerEventId();
		if (eventId.isEmpty()) { status("Open an event first.", Tone.ERROR); return; }
		String credential = credential(eventId);
		status("Saving...", Tone.INFO);
		request(method, "/v1/events/" + eventId + suffix, credential, payload).whenCompleteAsync(TsgHubApi.handle(result ->
		{
			status(success, Tone.SUCCESS);
			callback.accept(result);
		}, e -> failed(e, "", onError)), executor);
	}

	private CompletableFuture<JsonObject> request(String method, String path, String credential, JsonObject payload)
	{
		return plugin.api().call(method, path, credential, payload).whenComplete((response, error) -> {
			Throwable cause = error instanceof CompletionException ? error.getCause() : error;
			if (cause instanceof TsgHubApi.HttpError && ((TsgHubApi.HttpError) cause).status == 401 && !credential.isEmpty() && credential.equals(plugin.keys().adminKey())) plugin.keys().rejected(credential);
		});
	}

	private CompletableFuture<JsonObject> event(String eventId)
	{
		return request("GET", "/v1/events/" + eventId + "/organizer", credential(eventId), null);
	}

	String credential(String eventId)
	{
		String ownerToken = plugin.session().get(ORGANIZER_TOKEN + eventId);
		String creatorName = plugin.session().get(ORGANIZER_NAME + eventId);
		if (!ownerToken.isEmpty() && !creatorName.isEmpty() && PlayerNames.samePlayer(creatorName, plugin.clan().playerName())) return ownerToken;
		String adminToken = plugin.keys().adminKey();
		if (!adminToken.isEmpty()) return adminToken;
		return plugin.session().get(TOKEN);
	}

	private String listCredential()
	{
		String adminToken = plugin.keys().adminKey();
		if (!adminToken.isEmpty()) return adminToken;
		String eventId = plugin.sessions().organizerEventId();
		if (!eventId.isEmpty())
		{
			String credential = credential(eventId);
			if (!credential.isEmpty()) return credential;
		}
		String memberToken = plugin.session().get(TOKEN);
		if (!memberToken.isEmpty()) return memberToken;
		for (String key : plugin.session().keysWithPrefix(ORGANIZER_TOKEN))
		{
			String candidateId = key.substring(ORGANIZER_TOKEN.length());
			String creator = plugin.session().get(ORGANIZER_NAME + candidateId);
			if (!creator.isEmpty() && PlayerNames.samePlayer(creator, plugin.clan().playerName())) return plugin.session().get(key);
		}
		return "";
	}
}
