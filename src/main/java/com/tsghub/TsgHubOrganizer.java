package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

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

	TsgHubOrganizer(TsgHubPlugin plugin, Client client, ClientThread clientThread, ItemManager itemManager, ScheduledExecutorService executor, ExecutorService itemSearchExecutor, Supplier<TsgHubPanel> panel)
	{
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.executor = executor;
		this.itemSearchExecutor = itemSearchExecutor;
		this.panel = panel;
	}

	boolean refreshPending()
	{
		return refreshPending;
	}

	void selectEvent(String eventId)
	{
		if (eventId.trim().isEmpty()) return;
		TsgHubSession.set("organizerEventId", eventId.trim());
		plugin.syncSocketNow();
		status("Loading event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject event = event(eventId.trim());
				SwingUtilities.invokeLater(() -> panel.get().openOrganizerEvent(event));
				status("", Tone.INFO);
			}
			catch (Exception e) { error("Couldn't open event. ", e); }
		});
	}

	void loadManagedEvents()
	{
		if (!plugin.isInHubClan()) return;
		if (!plugin.sharingEnabled()) { status("Turn on sharing in the TSG Hub sidebar first.", Tone.ERROR); return; }
		if (!plugin.canManageOrganizerUi()) return;
		executor.submit(() -> {
			try
			{
				String credential = listCredential();
				if (credential.isEmpty()) throw new IllegalStateException("Join or create an event first");
				JsonObject response = request("GET", "/v1/managed-events", credential, null);
				SwingUtilities.invokeLater(() -> panel.get().setManagedEvents(response.getAsJsonArray("events")));
			}
			catch (Exception e) { error("Couldn't load events. ", e); }
		});
	}

	void createEvent(String name, Instant startsAt, Instant endsAt, boolean hideScores, boolean hidden, String type, JsonObject typeConfig, JsonArray prizes)
	{
		if (!plugin.isInHubClan()) { eventFormFailed("TSG Hub is only for members of the " + plugin.getHubClanName() + " clan."); return; }
		if (!plugin.sharingEnabled()) { eventFormFailed("Turn on sharing in the TSG Hub sidebar first."); return; }
		String credential = plugin.adminKey();
		if (credential.isEmpty())
		{
			eventFormFailed("Creating events needs a hub key with admin access from /hub key in the clan Discord.");
			return;
		}
		if (plugin.getDetectedClanName().isEmpty()) { eventFormFailed("No clan detected. Log in to a character in your clan first."); return; }
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) { eventFormFailed("Log in first so you're recorded as the admin."); return; }
		JsonObject body = new JsonObject();
		String creatorName = client.getLocalPlayer().getName();
		body.addProperty("name", name.trim());
		body.addProperty("clanName", plugin.getDetectedClanName());
		body.addProperty("createdByName", creatorName);
		body.addProperty("clanRank", plugin.getDetectedClanRank());
		addEventTimes(body, startsAt, endsAt);
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		body.addProperty("type", type);
		plugin.addAccountHash(body);
		body.add("config", typeConfig == null ? new JsonObject() : typeConfig);
		body.add("prizes", prizes);
		status("Creating event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject response = request("POST", "/v1/events", credential, body);
				JsonObject result = response.getAsJsonObject("event");
				String id = result.get("id").getAsString();
				TsgHubSession.set("organizerEventId", id);
				TsgHubSession.set("organizerToken:" + id, response.get("organizerToken").getAsString());
				TsgHubSession.set("organizerName:" + id, creatorName);
				SwingUtilities.invokeLater(() -> panel.get().eventCreated(result));
				status("Event created. Add teams next.", Tone.SUCCESS);
				loadManagedEvents();
				plugin.loadClanEvents();
			}
			catch (Exception e) { failed(e, null, this::eventFormFailed); }
		});
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
		String eventId = TsgHubUi.str(event, "id");
		JsonObject body = new JsonObject();
		body.addProperty("name", TsgHubUi.str(event, "name"));
		Instant startsAt = TsgHubUi.eventStart(event);
		if (startsAt == null) { status("Couldn't publish. The event has no start time.", Tone.ERROR); return; }
		addEventTimes(body, startsAt, TsgHubUi.eventEnd(event));
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
		executor.submit(() -> {
			try
			{
				request(method, "/v1/events/" + eventId + suffix, credential, body);
				done.run();
				status(success, Tone.SUCCESS);
				loadManagedEvents();
				plugin.loadClanEvents();
			}
			catch (Exception e) { failed(e, failure, failure == null ? this::eventFormFailed : null); }
		});
	}

	private void failed(Exception e, String failure, Consumer<String> onError)
	{
		if (onError == null) error(failure, e);
		else
		{
			onError.accept(TsgHubUi.friendlyError(e));
			status("", Tone.INFO);
		}
	}

	private void eventFormFailed(String message)
	{
		SwingUtilities.invokeLater(() -> panel.get().eventFormFailed(message));
	}

	void createTeam(String name)
	{
		JsonObject body = new JsonObject(); body.addProperty("name", name.trim());
		eventRequest("POST", "/teams", body, "Team added. Copy its code and share it with the team.", result -> {
			SwingUtilities.invokeLater(() -> panel.get().teamCreated());
			refreshEvent(false);
		}, null);
	}

	void renameTeam(String teamId, String name)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name.trim());
		eventRequest("PATCH", "/teams/" + teamId, body, "Team renamed.", result -> refreshEvent(false), null);
	}

	void saveTask(String taskId, String title, String description, int selectedType, int selectedScope, int selectedKillSignal, int dropCategory, String targetNamesText, JsonArray selectedItems, int dropRuleMode, String targetCount, String points)
	{
		JsonObject body = new JsonObject();
		String type = selectedType == 1 ? "kill" : selectedType == 2 || selectedType == 3 ? "drop" : selectedType == 4 ? "raid" : "manual";
		body.addProperty("title", title.trim());
		body.addProperty("description", description.trim());
		body.addProperty("type", type);
		body.addProperty("scope", selectedScope == 1 ? "individual" : selectedScope == 2 ? "solo" : "team");
		try { body.addProperty("points", Integer.parseInt(points.trim())); }
		catch (NumberFormatException e) { taskFormFailed("Points must be a whole number."); return; }
		if (!"manual".equals(type))
		{
			JsonObject cfg = new JsonObject();
			List<String> targetNames = new ArrayList<>();
			if (selectedType == 2 || selectedType == 3)
			{
				for (JsonObject item : TsgHubUi.objects(selectedItems)) targetNames.add(item.get("name").getAsString());
			}
			else for (String name : targetNamesText.split("\\R")) if (!name.trim().isEmpty()) targetNames.add(name.trim());
			if (targetNames.isEmpty() && !(selectedType == 2 && dropCategory > 0)) { taskFormFailed(selectedType == 2 || selectedType == 3 ? "Search for and add at least one item." : "Enter at least one target name or raid mode."); return; }
			if (selectedType != 3)
			{
				try { cfg.addProperty("targetCount", Integer.parseInt(targetCount.trim())); }
				catch (NumberFormatException e) { taskFormFailed("Target count must be a whole number."); return; }
			}
			if ("kill".equals(type))
			{
				if (targetNames.size() != 1) { taskFormFailed("A boss kill task takes one NPC name."); return; }
				cfg.addProperty("npcName", targetNames.get(0));
				cfg.addProperty("signal", selectedKillSignal == 0 ? "chat" : "loot");
			}
			else if ("raid".equals(type))
			{
				JsonArray modes = new JsonArray();
				for (String mode : targetNames) modes.add(mode.toLowerCase(Locale.ROOT));
				cfg.add("modes", modes);
				cfg.addProperty("clanOnly", true);
			}
			else if (selectedType == 2 && dropCategory > 0)
			{
				cfg.addProperty("itemGroup", dropCategory == 1 ? "jar" : "pet");
			}
			else
			{
				JsonArray itemNames = new JsonArray();
				targetNames.forEach(itemNames::add);
				cfg.add("itemNames", itemNames);
				JsonArray itemIds = new JsonArray();
				for (JsonObject item : TsgHubUi.objects(selectedItems)) itemIds.add(item.get("id").getAsInt());
				cfg.add("itemIds", itemIds);
				// Completing any one set group finishes the task.
				if (selectedType == 3 || dropRuleMode == 1)
				{
					Map<Integer, JsonArray> groupedItems = new TreeMap<>();
					for (JsonObject selected : TsgHubUi.objects(selectedItems))
					{
						JsonArray groupItems = groupedItems.computeIfAbsent(Math.max(0, selected.get("group").getAsInt()), ignored -> new JsonArray());
						JsonObject item = new JsonObject();
						item.addProperty("name", selected.get("name").getAsString());
						item.addProperty("id", selected.get("id").getAsInt());
						groupItems.add(item);
					}
					JsonArray groups = new JsonArray();
					groupedItems.values().forEach(groups::add);
					cfg.add("itemGroups", groups);
				}
			}
			body.add("config", cfg);
		}
		Consumer<JsonObject> done = result -> {
			SwingUtilities.invokeLater(() -> panel.get().finishTaskEdit());
			refreshEvent(false);
		};
		if (taskId == null || taskId.isEmpty())
			eventRequest("POST", "/tasks", body, "Task added for every team.", done, this::taskFormFailed);
		else
			eventRequest("PATCH", "/tasks/" + taskId, body, "Task saved. Existing progress was rechecked.", done, this::taskFormFailed);
	}

	private void taskFormFailed(String message)
	{
		SwingUtilities.invokeLater(() -> panel.get().taskFormFailed(message));
	}

	void deleteTask(String taskId)
	{
		eventRequest("DELETE", "/tasks/" + taskId, null, "Task deleted.", result -> {
			SwingUtilities.invokeLater(() -> panel.get().taskDeleted(taskId));
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
			plugin.forgetEvent(eventId);
			SwingUtilities.invokeLater(() -> panel.get().eventDeleted(eventId));
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
				SwingUtilities.invokeLater(() -> panel.get().confirmReconcile(taskId, result));
				return;
			}
			int count = TsgHubUi.integer(result, "count", 0);
			status("Credited " + count + (count == 1 ? " match" : " matches") + " from the loot log.", Tone.SUCCESS);
			refreshEvent(false);
		}, null);
	}

	void reviewClaim(String claimId, boolean approve)
	{
		JsonObject body = new JsonObject();
		body.addProperty("status", approve ? "approved" : "rejected");
		eventRequest("POST", "/claims/" + claimId + "/review", body, approve ? "Claim approved." : "Claim rejected.", result -> refreshEvent(false), null);
	}

	private void refreshEvent(boolean open)
	{
		String eventId = plugin.getOrganizerEventId();
		if (eventId.isEmpty() || executor == null || executor.isShutdown()) return;
		executor.submit(() -> {
			try
			{
				JsonObject event = event(eventId);
				SwingUtilities.invokeLater(() -> {
					if (open) panel.get().openOrganizerEvent(event);
					else panel.get().showEvent(event);
				});
			}
			catch (Exception e) { error("Couldn't refresh the event. ", e); }
		});
	}

	void liveRefresh()
	{
		refreshPending = false;
		String eventId = plugin.getOrganizerEventId();
		if (eventId.isEmpty() || executor == null || executor.isShutdown()) return;
		executor.submit(() -> {
			try
			{
				JsonObject event = event(eventId);
				SwingUtilities.invokeLater(() -> {
					if (!eventId.equals(plugin.getOrganizerEventId())) return;
					if (panel.get().editingText()) refreshPending = true;
					else panel.get().showEvent(event);
				});
			}
			catch (Exception ignored) { }
		});
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
		status(failure + TsgHubUi.friendlyError(e), Tone.ERROR);
	}

	private void status(String message, Tone tone)
	{
		SwingUtilities.invokeLater(() -> { TsgHubPanel p = panel.get(); if (p != null) p.setStatus(message, tone); });
	}

	private void eventRequest(String method, String suffix, JsonObject payload, String success, Consumer<JsonObject> callback, Consumer<String> onError)
	{
		String eventId = plugin.getOrganizerEventId();
		if (eventId.isEmpty()) { status("Open an event first.", Tone.ERROR); return; }
		String credential = credential(eventId);
		status("Saving...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject result = request(method, "/v1/events/" + eventId + suffix, credential, payload);
				status(success, Tone.SUCCESS);
				callback.accept(result);
			}
			catch (Exception e) { failed(e, "", onError); }
		});
	}

	private JsonObject request(String method, String path, String credential, JsonObject payload) throws Exception
	{
		try { return plugin.api().request(method, path, credential, payload); }
		catch (TsgHubApi.HttpError e)
		{
			if (e.status == 401 && !credential.isEmpty() && credential.equals(plugin.adminKey())) plugin.keyRejected(credential);
			throw e;
		}
	}

	private JsonObject event(String eventId) throws Exception
	{
		return request("GET", "/v1/events/" + eventId + "/organizer", credential(eventId), null);
	}

	String credential(String eventId)
	{
		String ownerToken = TsgHubSession.get("organizerToken:" + eventId);
		String creatorName = TsgHubSession.get("organizerName:" + eventId);
		if (!ownerToken.isEmpty() && !creatorName.isEmpty() && TsgHubUi.samePlayer(creatorName, plugin.getDetectedPlayerName())) return ownerToken;
		String adminToken = plugin.adminKey();
		if (!adminToken.isEmpty()) return adminToken;
		return TsgHubSession.get("token");
	}

	private String listCredential()
	{
		String adminToken = plugin.adminKey();
		if (!adminToken.isEmpty()) return adminToken;
		String eventId = plugin.getOrganizerEventId();
		if (!eventId.isEmpty())
		{
			String credential = credential(eventId);
			if (!credential.isEmpty()) return credential;
		}
		String memberToken = TsgHubSession.get("token");
		if (!memberToken.isEmpty()) return memberToken;
		for (String key : TsgHubSession.keysWithPrefix("organizerToken:"))
		{
			String candidateId = key.substring("organizerToken:".length());
			String creator = TsgHubSession.get("organizerName:" + candidateId);
			if (!creator.isEmpty() && TsgHubUi.samePlayer(creator, plugin.getDetectedPlayerName())) return TsgHubSession.get(key);
		}
		return "";
	}
}
