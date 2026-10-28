package com.tsghub;

import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.GROUP_PASSPHRASE;
import static com.tsghub.TsgHubSession.GROUP_PLAYER;
import static com.tsghub.TsgHubSession.GROUP_TOKEN;
import static com.tsghub.TsgHubSession.SPLIT_NOTICE;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.party.PartyService;

final class TsgHubGroups implements GroupTracker.Listener
{
	private static final String WILDERNESS = "Wilderness lvl ";
	// The service drops members after 3 minutes without a check-in.
	static final int HEARTBEAT_SECONDS = 60;
	static final int REFRESH_SECONDS = 30;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final PartyService partyService;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private final TsgHubSocket socket;
	private volatile GroupTracker tracker;
	private volatile JsonObject group;
	private volatile boolean awaitingBeat;

	TsgHubGroups(TsgHubPlugin plugin, Client client, PartyService partyService, ScheduledExecutorService executor,
		Supplier<TsgHubApi> api, Supplier<TsgHubSidebarPanel> sidebar, TsgHubSocket socket)
	{
		this.plugin = plugin;
		this.socket = socket;
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
		String saved = plugin.session().get(GROUP_PASSPHRASE);
		return plugin.sharingEnabled() && passphrase != null && !saved.isEmpty() && saved.equals(passphrase);
	}

	boolean inGroup()
	{
		return !plugin.session().get(GROUP_TOKEN).isEmpty();
	}

	JsonObject currentGroup()
	{
		return group;
	}

	boolean isLeader()
	{
		JsonObject current = group;
		return inGroup() && current != null && PlayerNames.samePlayer(TsgHubJson.str(current, "leaderName"), plugin.session().get(GROUP_PLAYER));
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
		if (!plugin.canUseHub() || executor.isShutdown()) return;
		String clan = TsgHubApi.encode(plugin.clan().name());
		if (!quiet) busy(true);
		api.get().call("GET", "/v1/groups?clanName=" + clan, null, null).whenCompleteAsync(TsgHubApi.handle(response ->
		{
			apply(response.getAsJsonArray("groups"));
		}, e -> { if (!quiet) status("Couldn't load parties. " + TsgHubFormat.friendlyError(e), Tone.ERROR); }, () -> { if (!quiet) busy(false); }), executor);
	}

	void apply(JsonArray groups)
	{
		if (groups == null) return;
		JsonObject current = group;
		if (current != null && inGroup())
		{
			JsonObject next = TsgHubJson.byId(groups, TsgHubJson.str(current, "id"));
			if (next != null) adopt(next);
			if ((next == null || !memberNames(next).equals(memberNames(current))) && !executor.isShutdown()) executor.submit(this::heartbeat);
		}
		plugin.ui(s -> s.partiesView.setGroups(groups));
	}

	void open()
	{
		if (socket.isLive()) plugin.liveSync().syncNow();
		else refresh();
	}

	void refresh()
	{
		if (inGroup() && !executor.isShutdown()) executor.submit(this::heartbeat);
		loadGroups();
	}

	void create()
	{
		if (!plugin.canUseHub()) { plugin.ui(s -> s.partiesView.groupActionFailed("Log in to a " + plugin.clan().hubName() + " character first.")); return; }
		enter("POST", "/v1/groups", identity(), true);
	}

	void setTitle(String title)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("activity", title.trim());
		update("/v1/groups/title", payload);
	}

	void kick(String name)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("displayName", name);
		update("/v1/groups/kick", payload);
		if (!executor.isShutdown()) executor.submit(this::heartbeat);
	}

	void setLocked(boolean locked)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("locked", locked);
		update("/v1/groups/lock", payload);
	}

	void setSplit(boolean active)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("active", active);
		if (!socket.group("group.split", plugin.session().get(GROUP_TOKEN), payload)) update("/v1/groups/split", payload);
	}

	void setSplitValue(String dropId, long gp)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("dropId", dropId);
		payload.addProperty("gp", gp);
		update("/v1/groups/split/value", payload);
	}

	void setSplitShare(String dropId, String name, boolean share)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("dropId", dropId);
		payload.addProperty("displayName", name);
		payload.addProperty("share", share);
		update("/v1/groups/split/share", payload);
	}

	private void adopt(JsonObject next)
	{
		group = next;
		String notice = splitNotice(object(next, "split"), plugin.session().get(GROUP_PLAYER));
		if (notice.isEmpty()) return;
		String key = str(object(next, "split"), "startedAt") + "|" + notice;
		if (key.equals(plugin.session().get(SPLIT_NOTICE))) return;
		plugin.session().set(SPLIT_NOTICE, key);
		plugin.chat().splitNotice(notice);
	}

	static String splitNotice(JsonObject split, String self)
	{
		if (split.size() == 0 || bool(split, "active") || self.isEmpty()) return "";
		boolean member = false;
		for (JsonObject row : objects(array(split, "members"))) member |= PlayerNames.samePlayer(str(row, "displayName"), self);
		if (!member) return "";
		String approx = bool(split, "estimated") ? "~" : "";
		List<String> parts = new ArrayList<>();
		for (JsonObject transfer : objects(array(split, "transfers")))
		{
			String amount = approx + TsgHubFormat.number(longValue(transfer, "amount", 0)) + " gp";
			if (PlayerNames.samePlayer(str(transfer, "from"), self)) parts.add("you pay " + str(transfer, "to") + " " + amount);
			else if (PlayerNames.samePlayer(str(transfer, "to"), self)) parts.add(str(transfer, "from") + " pays you " + amount);
		}
		String text = parts.isEmpty() ? "you're square, nothing to pay or collect" : String.join(", ", parts);
		return "Loot split: " + text + (approx.isEmpty() ? "." : " (estimated until sold).");
	}

	private void update(String path, JsonObject payload)
	{
		String token = plugin.session().get(GROUP_TOKEN);
		if (token.isEmpty() || executor.isShutdown()) return;
		api.get().call("POST", path, token, payload).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject joined = TsgHubApi.result(response, error).getAsJsonObject("group");
				adopt(joined);
				plugin.ui(s -> s.partiesView.groupRefreshed(joined));
				refreshUnlessLive();
			}
			catch (TsgHubApi.HttpError e) { when404(e); }
			catch (Exception e) { plugin.ui(s -> s.partiesView.groupActionFailed(TsgHubFormat.friendlyError(e))); }
		}, executor);
	}

	void join(String groupId)
	{
		if (!plugin.canUseHub()) { plugin.ui(s -> s.partiesView.groupActionFailed("Log in to a " + plugin.clan().hubName() + " character first.")); return; }
		enter("POST", "/v1/groups/" + groupId + "/join", identity(), false);
	}

	private void enter(String method, String path, JsonObject payload, boolean created)
	{
		String previousToken = plugin.session().get(GROUP_TOKEN);
		api.get().call(method, path, null, payload).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				JsonObject joined = result.getAsJsonObject("group");
				String passphrase = result.get("passphrase").getAsString();
				// The service already removed us from the previous group.
				if (!previousToken.isEmpty()) forget();
				// Save before switching so the tracker recognises the new party.
				plugin.session().set(GROUP_TOKEN, result.get("token").getAsString());
				plugin.session().set(GROUP_PASSPHRASE, passphrase);
				plugin.session().set(GROUP_PLAYER, plugin.clan().playerName());
				adopt(joined);
				partyService.changeParty(passphrase);
				status(created ? "Started a party." : "Joined " + partyTitle(joined) + ".", Tone.SUCCESS);
				plugin.ui(s -> s.partiesView.showGroup(joined));
				refreshUnlessLive();
			}
			catch (TsgHubApi.HttpError e) { when404(e); }
			catch (Exception e) { plugin.ui(s -> s.partiesView.groupActionFailed(TsgHubFormat.friendlyError(e))); }
		}, executor);
	}

	void leave()
	{
		String token = plugin.session().get(GROUP_TOKEN);
		// Compare passphrases directly: sharing may already be off.
		String passphrase = plugin.session().get(GROUP_PASSPHRASE);
		boolean ours = partyService.isInParty() && !passphrase.isEmpty() && passphrase.equals(partyService.getPartyPassphrase());
		forget();
		if (ours) partyService.changeParty(null);
		plugin.ui(s -> s.partiesView.showGroupList());
		if (token.isEmpty() || executor.isShutdown()) return;
		// The service drops idle members on its own if this fails.
		api.get().call("POST", "/v1/groups/leave", token, null).whenCompleteAsync((response, error) -> refreshUnlessLive(), executor);
	}

	void heartbeat()
	{
		String token = plugin.session().get(GROUP_TOKEN);
		if (token.isEmpty()) return;
		if (!plugin.sharingEnabled()) { leave(); return; }
		GroupTracker t = tracker;
		if (t != null && t.idleTooLong()) { status("Left your party after 30 minutes logged out.", Tone.INFO); leave(); return; }
		String name = plugin.clan().playerName();
		if (!name.isEmpty() && !PlayerNames.samePlayer(name, plugin.session().get(GROUP_PLAYER))) { leave(); return; }
		JsonObject payload = new JsonObject();
		if (client.getGameState() == GameState.LOGGED_IN) payload.addProperty("world", client.getWorld());
		payload.addProperty("area", plugin.currentArea());
		if (!awaitingBeat && socket.group("group.heartbeat", token, payload))
		{
			awaitingBeat = true;
			return;
		}
		awaitingBeat = false;
		api.get().call("POST", "/v1/groups/heartbeat", token, payload).whenCompleteAsync((response, error) -> {
			try { beaten(TsgHubApi.result(response, error)); }
			catch (TsgHubApi.HttpError e) { beatRejected(e.status); }
			catch (Exception ignored) { /* Try again next beat. */ }
		}, executor);
	}

	void beaten(JsonObject result)
	{
		awaitingBeat = false;
		if (!inGroup() || !result.has("group") || !result.get("group").isJsonObject()) return;
		JsonObject joined = result.getAsJsonObject("group");
		adopt(joined);
		String rotated = str(result, "passphrase");
		if (!rotated.isEmpty()) plugin.session().set(GROUP_PASSPHRASE, rotated);
		// Still listed after a client restart but not connected.
		String passphrase = plugin.session().get(GROUP_PASSPHRASE);
		if (!passphrase.equals(partyService.getPartyPassphrase())) partyService.changeParty(passphrase);
		plugin.ui(s -> s.partiesView.groupRefreshed(joined));
	}

	void beatRejected(int status)
	{
		awaitingBeat = false;
		if (status != 404 || !inGroup()) return;
		status("Your party has closed.", Tone.INFO);
		leave();
	}

	private void refreshUnlessLive()
	{
		if (!socket.isLive()) loadGroups(true);
	}

	private static List<String> memberNames(JsonObject group)
	{
		List<String> names = new ArrayList<>();
		for (JsonObject member : objects(array(group, "members"))) names.add(PlayerNames.key(str(member, "displayName")));
		return names;
	}

	private void when404(TsgHubApi.HttpError e)
	{
		if (e.status == 404) loadGroups();
		plugin.ui(s -> s.partiesView.groupActionFailed(TsgHubFormat.friendlyError(e)));
	}

	void onSharingDisabled()
	{
		if (inGroup()) leave();
	}

	private void forget()
	{
		group = null;
		plugin.session().set(GROUP_TOKEN, "");
		plugin.session().set(GROUP_PASSPHRASE, "");
		plugin.session().set(GROUP_PLAYER, "");
	}

	@Override public void memberUpdated(PartyPlayer player, boolean self) { parties(v -> v.groupMemberUpdated(player, self)); }

	@Override public void memberRemoved(PartyPlayer player) { parties(v -> v.groupMemberRemoved(player)); }

	List<String> liveAreas()
	{
		GroupTracker t = tracker;
		return t == null ? java.util.Collections.emptyList() : t.areas();
	}

	void autoRefresh()
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null && s.wantsGroups()) loadGroups(true);
	}

	@Override public void areasChanged() { parties(v -> v.partyAreasChanged()); }

	@Override public void membersCleared() { parties(v -> v.groupMembersCleared()); }

	private void parties(Consumer<TsgHubPartiesView> action)
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null) action.accept(s.partiesView);
	}

	@Override
	public void partyChanged(String passphrase)
	{
		// Switching parties elsewhere also leaves the group.
		if (inGroup() && !isGroupParty(passphrase)) executor.submit(this::leave);
	}

	private JsonObject identity()
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("displayName", plugin.clan().playerName());
		payload.addProperty("clanName", plugin.clan().name());
		payload.addProperty("world", client.getWorld());
		payload.addProperty("area", plugin.currentArea());
		return payload;
	}

	private void status(String message, Tone tone)
	{
		plugin.ui(s -> s.setStatus(message, tone));
	}

	private void busy(boolean isBusy)
	{
		plugin.ui(s -> s.setBusy(isBusy));
	}

	static String partyTitle(JsonObject group)
	{
		String title = str(group, "activity");
		if (!title.isEmpty()) return title;
		Map.Entry<String, Integer> area = areaSummary(array(group, "members"));
		if (area != null) return area.getKey();
		String leader = str(group, "leaderName");
		return leader.isEmpty() ? "Party" : leader + "'s party";
	}

	static Map.Entry<String, Integer> areaSummary(JsonArray members)
	{
		List<String> areas = new ArrayList<>();
		for (JsonObject member : objects(members)) areas.add(str(member, "area"));
		return areaSummary(areas);
	}

	static Map.Entry<String, Integer> areaSummary(List<String> areas)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		int low = Integer.MAX_VALUE;
		int high = 0;
		for (String area : areas)
		{
			if (area == null || area.isEmpty()) continue;
			if (area.startsWith(WILDERNESS))
			{
				try
				{
					int level = Integer.parseInt(area.substring(WILDERNESS.length()).trim());
					low = Math.min(low, level);
					high = Math.max(high, level);
					area = "Wilderness";
				}
				catch (NumberFormatException ignored) { }
			}
			counts.merge(area, 1, Integer::sum);
		}
		if (counts.isEmpty()) return null;
		Map.Entry<String, Integer> top = Collections.max(counts.entrySet(), Map.Entry.comparingByValue());
		String name = top.getKey();
		if (name.equals("Wilderness") && high > 0) name += " lvl " + (low == high ? String.valueOf(low) : low + "-" + high);
		return new AbstractMap.SimpleImmutableEntry<>(name, top.getValue());
	}
}
