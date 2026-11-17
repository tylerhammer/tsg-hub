package com.tsghub;

import com.google.gson.*;
import com.tsghub.TsgHubUi.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.*;
import net.runelite.api.*;
import net.runelite.client.party.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.GROUP_PASSPHRASE;
import static com.tsghub.TsgHubSession.GROUP_PLAYER;
import static com.tsghub.TsgHubSession.GROUP_TOKEN;
import static com.tsghub.TsgHubSession.SPLIT_NOTICE;
import static com.tsghub.TsgHubUi.*;

@RequiredArgsConstructor
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
	private volatile GroupTracker tracker;
	private volatile JsonObject group;
	private volatile boolean awaitingBeat;

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
		return inGroup() && current != null && PlayerNames.samePlayer(str(current, "leaderName"), plugin.session().get(GROUP_PLAYER));
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
		plugin.api().call("GET", "/v1/groups?clanName=" + clan, null, null, response -> apply(response.getAsJsonArray("groups")),
			e -> { if (!quiet) status("Couldn't load parties. " + TsgHubFormat.friendlyError(e), Tone.ERROR); }, () -> { if (!quiet) busy(false); });
	}

	void apply(JsonArray groups)
	{
		if (groups == null) return;
		JsonObject current = group;
		if (current != null && inGroup())
		{
			JsonObject next = byId(groups, str(current, "id"));
			if (next != null) adopt(next);
			if ((next == null || !memberNames(next).equals(memberNames(current))) && !executor.isShutdown()) executor.submit(this::heartbeat);
		}
		plugin.ui(s -> s.partiesView.setGroups(groups));
	}

	void open()
	{
		if (plugin.live()) plugin.liveSync().syncNow();
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
		update("/v1/groups/title", obj("activity", title.trim()));
	}

	void kick(String name)
	{
		update("/v1/groups/kick", obj("displayName", name));
		if (!executor.isShutdown()) executor.submit(this::heartbeat);
	}

	void setLocked(boolean locked)
	{
		update("/v1/groups/lock", obj("locked", locked));
	}

	void setSplit(boolean active)
	{
		String token = plugin.session().get(GROUP_TOKEN);
		if (token.isEmpty() || plugin.outbox().request("group.split", obj("active", active, "token", token), this::splitRejected)) return;
		update("/v1/groups/split", obj("active", active));
	}

	void splitRejected(int status, String message)
	{
		failed(new TsgHubApi.HttpError(status, message));
	}

	void setSplitValue(String dropId, long gp)
	{
		update("/v1/groups/split/value", obj("dropId", dropId, "gp", gp));
	}

	void setSplitShare(String dropId, String name, boolean share)
	{
		update("/v1/groups/split/share", obj("dropId", dropId, "displayName", name, "share", share));
	}

	void setSplitSkip(String name, boolean skip)
	{
		update("/v1/groups/split/skip", obj("displayName", name, "skip", skip));
	}

	void markPaid(String from, String to, long gp)
	{
		update("/v1/groups/split/paid", obj("from", from, "to", to, "gp", gp));
	}

	void unpay(String paymentId)
	{
		update("/v1/groups/split/unpay", obj("paymentId", paymentId));
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
		boolean paid = objects(array(split, "payments")).stream().anyMatch(p -> PlayerNames.samePlayer(str(p, "from"), self) || PlayerNames.samePlayer(str(p, "to"), self));
		String text = !parts.isEmpty() ? String.join(", ", parts) : paid ? "all settled" : "you're square, nothing to pay or collect";
		return "Loot split: " + text + (approx.isEmpty() ? "." : " (estimated until sold).");
	}

	private void update(String path, JsonObject payload)
	{
		String token = plugin.session().get(GROUP_TOKEN);
		if (token.isEmpty() || executor.isShutdown()) return;
		plugin.api().call("POST", path, token, payload, response -> {
			refreshed(response.getAsJsonObject("group"));
			refreshUnlessLive();
		}, this::failed);
	}

	void join(String groupId)
	{
		if (!plugin.canUseHub()) { plugin.ui(s -> s.partiesView.groupActionFailed("Log in to a " + plugin.clan().hubName() + " character first.")); return; }
		enter("POST", "/v1/groups/" + groupId + "/join", identity(), false);
	}

	private void enter(String method, String path, JsonObject payload, boolean created)
	{
		String previousToken = plugin.session().get(GROUP_TOKEN);
		plugin.api().call(method, path, null, payload, result -> {
			JsonObject joined = result.getAsJsonObject("group");
			String passphrase = result.get("passphrase").getAsString();
			if (!previousToken.isEmpty()) forget();
			plugin.session().set(GROUP_TOKEN, result.get("token").getAsString());
			plugin.session().set(GROUP_PASSPHRASE, passphrase);
			plugin.session().set(GROUP_PLAYER, plugin.clan().playerName());
			adopt(joined);
			partyService.changeParty(passphrase);
			status(created ? "Started a party." : "Joined " + partyTitle(joined) + ".", Tone.SUCCESS);
			plugin.ui(s -> s.partiesView.showGroup(joined));
			refreshUnlessLive();
		}, this::failed);
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
		plugin.api().call("POST", "/v1/groups/leave", token, null, response -> { }, e -> { }, this::refreshUnlessLive);
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
		JsonObject payload = obj("area", plugin.currentArea());
		if (client.getGameState() == GameState.LOGGED_IN) payload.addProperty("world", client.getWorld());
		if (!awaitingBeat && plugin.liveSync().socket().group("group.heartbeat", token, payload))
		{
			awaitingBeat = true;
			return;
		}
		awaitingBeat = false;
		plugin.api().call("POST", "/v1/groups/heartbeat", token, payload, this::beaten, e -> beatRejected(TsgHubApi.status(e)));
	}

	void beaten(JsonObject result)
	{
		awaitingBeat = false;
		if (!inGroup() || !result.has("group") || !result.get("group").isJsonObject()) return;
		String rotated = str(result, "passphrase");
		if (!rotated.isEmpty()) plugin.session().set(GROUP_PASSPHRASE, rotated);
		String passphrase = plugin.session().get(GROUP_PASSPHRASE);
		if (!passphrase.equals(partyService.getPartyPassphrase())) partyService.changeParty(passphrase);
		refreshed(result.getAsJsonObject("group"));
	}

	private void refreshed(JsonObject joined)
	{
		JsonObject previous = group;
		adopt(joined);
		if (!joined.equals(previous)) plugin.ui(s -> s.partiesView.groupRefreshed(joined));
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
		if (!plugin.live()) loadGroups(true);
	}

	private static List<String> memberNames(JsonObject group)
	{
		List<String> names = new ArrayList<>();
		for (JsonObject member : objects(array(group, "members"))) names.add(PlayerNames.key(str(member, "displayName")));
		return names;
	}

	private void failed(Exception e)
	{
		if (TsgHubApi.status(e) == 404) loadGroups();
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

	@Override public void memberUpdated(PartyPlayer player, boolean self) { parties(s -> s.groupMembers.update(player, self)); }

	@Override
	public void memberRemoved(PartyPlayer player)
	{
		parties(s -> {
			s.groupMembers.remove(player);
			s.partiesView.partyAreasChanged();
		});
	}

	List<String> liveAreas()
	{
		GroupTracker t = tracker;
		return t == null ? java.util.Collections.emptyList() : t.areas();
	}

	void autoRefresh()
	{
		TsgHubSidebarPanel s = plugin.sidebar();
		if (s != null && s.wantsGroups()) loadGroups(true);
	}

	@Override public void areasChanged() { parties(s -> s.partiesView.partyAreasChanged()); }

	@Override public void membersCleared() { parties(s -> s.groupMembers.clear()); }

	private void parties(Consumer<TsgHubSidebarPanel> action)
	{
		TsgHubSidebarPanel s = plugin.sidebar();
		if (s != null) action.accept(s);
	}

	@Override
	public void partyChanged(String passphrase)
	{
		// Switching parties elsewhere also leaves the group.
		if (inGroup() && !isGroupParty(passphrase)) executor.submit(this::leave);
	}

	private JsonObject identity()
	{
		return obj("displayName", plugin.clan().playerName(), "clanName", plugin.clan().name(), "world", client.getWorld(), "area", plugin.currentArea());
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
