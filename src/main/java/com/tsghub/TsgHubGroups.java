package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import com.tsghub.group.GroupTracker;
import com.tsghub.group.data.PartyPlayer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.party.PartyService;

final class TsgHubGroups implements GroupTracker.Listener
{
	// The service drops members after 3 minutes without a check-in.
	static final int HEARTBEAT_SECONDS = 60;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final PartyService partyService;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private volatile GroupTracker tracker;
	private volatile JsonObject group;

	TsgHubGroups(TsgHubPlugin plugin, Client client, PartyService partyService, ScheduledExecutorService executor,
		Supplier<TsgHubApi> api, Supplier<TsgHubSidebarPanel> sidebar)
	{
		this.plugin = plugin;
		this.client = client;
		this.partyService = partyService;
		this.executor = executor;
		this.api = api;
		this.sidebar = sidebar;
	}

	void setTracker(GroupTracker tracker)
	{
		this.tracker = tracker;
	}

	boolean isGroupParty(String passphrase)
	{
		String saved = TsgHubSession.get("groupPassphrase");
		return plugin.sharingEnabled() && passphrase != null && !saved.isEmpty() && saved.equals(passphrase);
	}

	boolean inGroup()
	{
		return !TsgHubSession.get("groupToken").isEmpty();
	}

	JsonObject currentGroup()
	{
		return group;
	}

	boolean inOtherParty()
	{
		return partyService.isInParty() && !isGroupParty(partyService.getPartyPassphrase());
	}

	void loadGroups()
	{
		loadGroups(false);
	}

	private void loadGroups(boolean quiet)
	{
		if (!canUse() || executor.isShutdown()) return;
		String clan = encode(plugin.getDetectedClanName());
		if (!quiet) busy(true);
		executor.submit(() -> {
			try
			{
				JsonArray groups = api.get().request("GET", "/v1/groups?clanName=" + clan, null, null).getAsJsonArray("groups");
				ui(s -> s.setGroups(groups));
			}
			catch (Exception e) { if (!quiet) status("Couldn't load parties. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
			finally { if (!quiet) busy(false); }
		});
	}

	void refresh()
	{
		if (inGroup() && !executor.isShutdown()) executor.submit(this::heartbeat);
		loadGroups();
	}

	void create(String activity)
	{
		if (!canUse()) { ui(s -> s.groupActionFailed("Log in to a " + plugin.getHubClanName() + " character first.")); return; }
		JsonObject payload = identity();
		payload.addProperty("activity", activity.trim());
		enter("POST", "/v1/groups", payload, "Created");
	}

	void join(String groupId)
	{
		if (!canUse()) { ui(s -> s.groupActionFailed("Log in to a " + plugin.getHubClanName() + " character first.")); return; }
		enter("POST", "/v1/groups/" + groupId + "/join", identity(), "Joined");
	}

	private void enter(String method, String path, JsonObject payload, String verb)
	{
		String previousToken = TsgHubSession.get("groupToken");
		executor.submit(() -> {
			try
			{
				JsonObject result = api.get().request(method, path, null, payload);
				JsonObject joined = result.getAsJsonObject("group");
				String passphrase = result.get("passphrase").getAsString();
				// The service already removed us from the previous group.
				if (!previousToken.isEmpty()) forget();
				// Save before switching so the tracker recognises the new party.
				TsgHubSession.set("groupToken", result.get("token").getAsString());
				TsgHubSession.set("groupPassphrase", passphrase);
				TsgHubSession.set("groupPlayer", plugin.getDetectedPlayerName());
				group = joined;
				partyService.changeParty(passphrase);
				status(verb + " " + TsgHubUi.str(joined, "activity") + " party.", Tone.SUCCESS);
				ui(s -> s.showGroup(joined));
				loadGroups(true);
			}
			catch (TsgHubApi.HttpError e) { when404(e); }
			catch (Exception e) { ui(s -> s.groupActionFailed(TsgHubUi.friendlyError(e))); }
		});
	}

	void leave()
	{
		String token = TsgHubSession.get("groupToken");
		// Compare passphrases directly: sharing may already be off.
		String passphrase = TsgHubSession.get("groupPassphrase");
		boolean ours = partyService.isInParty() && !passphrase.isEmpty() && passphrase.equals(partyService.getPartyPassphrase());
		forget();
		if (ours) partyService.changeParty(null);
		ui(TsgHubSidebarPanel::showGroupList);
		if (token.isEmpty() || executor.isShutdown()) return;
		executor.submit(() -> {
			try { api.get().request("POST", "/v1/groups/leave", token, null); }
			catch (Exception ignored) { /* The service drops idle members on its own. */ }
			loadGroups();
		});
	}

	void heartbeat()
	{
		String token = TsgHubSession.get("groupToken");
		if (token.isEmpty()) return;
		if (!plugin.sharingEnabled()) { leave(); return; }
		GroupTracker t = tracker;
		if (t != null && t.idleTooLong()) { status("Left your party after 30 minutes logged out.", Tone.INFO); leave(); return; }
		String name = plugin.getDetectedPlayerName();
		if (!name.isEmpty() && !TsgHubUi.samePlayer(name, TsgHubSession.get("groupPlayer"))) { leave(); return; }
		JsonObject payload = new JsonObject();
		if (client.getGameState() == GameState.LOGGED_IN) payload.addProperty("world", client.getWorld());
		try
		{
			JsonObject joined = api.get().request("POST", "/v1/groups/heartbeat", token, payload).getAsJsonObject("group");
			group = joined;
			// Still listed after a client restart but not connected.
			String passphrase = TsgHubSession.get("groupPassphrase");
			if (!passphrase.equals(partyService.getPartyPassphrase())) partyService.changeParty(passphrase);
			ui(s -> s.groupRefreshed(joined));
			loadGroups(true);
		}
		catch (TsgHubApi.HttpError e)
		{
			if (e.status != 404) return;
			status("Your party has closed.", Tone.INFO);
			leave();
		}
		catch (Exception ignored) { /* Try again next beat. */ }
	}

	private void when404(TsgHubApi.HttpError e)
	{
		if (e.status == 404) loadGroups();
		ui(s -> s.groupActionFailed(TsgHubUi.friendlyError(e)));
	}

	void onSharingDisabled()
	{
		if (inGroup()) leave();
	}

	private void forget()
	{
		group = null;
		TsgHubSession.set("groupToken", "");
		TsgHubSession.set("groupPassphrase", "");
		TsgHubSession.set("groupPlayer", "");
	}

	@Override
	public void memberUpdated(PartyPlayer player, boolean bannerChanged, boolean self)
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null) s.groupMemberUpdated(player, bannerChanged, self);
	}

	@Override
	public void memberRemoved(PartyPlayer player)
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null) s.groupMemberRemoved(player);
	}

	@Override
	public void membersCleared()
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null) s.groupMembersCleared();
	}

	@Override
	public void partyChanged(String passphrase)
	{
		// Switching parties elsewhere also leaves the group.
		if (inGroup() && !isGroupParty(passphrase)) executor.submit(this::leave);
	}

	private boolean canUse()
	{
		return plugin.isInHubClan() && plugin.sharingEnabled() && !plugin.getDetectedPlayerName().isEmpty();
	}

	private JsonObject identity()
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("displayName", plugin.getDetectedPlayerName());
		payload.addProperty("clanName", plugin.getDetectedClanName());
		payload.addProperty("world", client.getWorld());
		return payload;
	}

	private static String encode(String value)
	{
		try { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name()); }
		catch (java.io.UnsupportedEncodingException e) { return ""; }
	}

	private void ui(java.util.function.Consumer<TsgHubSidebarPanel> action)
	{
		SwingUtilities.invokeLater(() -> {
			TsgHubSidebarPanel s = sidebar.get();
			if (s != null) action.accept(s);
		});
	}

	private void status(String message, Tone tone)
	{
		ui(s -> s.setStatus(message, tone));
	}

	private void busy(boolean isBusy)
	{
		ui(s -> s.setBusy(isBusy));
	}
}
