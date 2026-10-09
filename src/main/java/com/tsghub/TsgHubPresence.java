package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Actor;
import net.runelite.api.GameState;
import net.runelite.api.IconID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.Text;

final class TsgHubPresence
{
	static final int REFRESH_SECONDS = 30;
	private static final long HEARTBEAT_MILLIS = 30_000;
	private static final long MIN_GAP_MILLIS = 5_000;
	private static final int SLAYER_BOSS_TARGET = 98;
	private static final IconID[] ACCOUNT_ICONS = {null, IconID.IRONMAN, IconID.ULTIMATE_IRONMAN, IconID.HARDCORE_IRONMAN, IconID.GROUP_IRONMAN, IconID.HARDCORE_GROUP_IRONMAN, IconID.UNRANKED_GROUP_IRONMAN};

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ChatIconManager chatIcons;
	private final SpriteManager sprites;
	private final Map<Integer, BufferedImage> rankIcons = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> accountIcons = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> nameIcons = new ConcurrentHashMap<>();
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubSidebarPanel> sidebar;
	private final TsgHubSocket socket;
	private final Supplier<String> key;
	private final ActivityDetector activity = new ActivityDetector();
	private volatile JsonObject listed;
	private volatile JsonArray notes;
	private volatile long sentAt;
	private volatile AreaNames.Area area;
	private boolean slayerDirty = true;

	TsgHubPresence(TsgHubPlugin plugin, Client client, ClientThread clientThread, ChatIconManager chatIcons, SpriteManager sprites, ScheduledExecutorService executor,
		Supplier<TsgHubApi> api, Supplier<TsgHubSidebarPanel> sidebar, TsgHubSocket socket, Supplier<String> key)
	{
		this.key = key;
		this.socket = socket;
		this.plugin = plugin;
		this.client = client;
		this.clientThread = clientThread;
		this.chatIcons = chatIcons;
		this.sprites = sprites;
		this.executor = executor;
		this.api = api;
		this.sidebar = sidebar;
	}

	void onXp(Skill skill, int xp)
	{
		activity.onXp(skill, xp, System.currentTimeMillis());
	}

	void onVarbitChanged(int varp, int varbit)
	{
		if (varp == VarPlayerID.SLAYER_COUNT || varp == VarPlayerID.SLAYER_TARGET || varbit == VarbitID.SLAYER_TARGET_BOSSID) slayerDirty = true;
	}

	void onGameTick()
	{
		area = locate();
		trackOpponents();
		trackSlayerTask();
		JsonObject next = snapshot();
		if (next == null)
		{
			leave();
			return;
		}
		long now = System.currentTimeMillis();
		JsonObject current = listed;
		boolean changed = current == null || !next.equals(current);
		boolean due = !socket.isLive() && now - sentAt >= HEARTBEAT_MILLIS;
		if ((changed && now - sentAt >= MIN_GAP_MILLIS) || due) send(next, now);
	}

	void onLoggedOut()
	{
		leave();
		activity.reset();
		slayerDirty = true;
	}

	void leave()
	{
		JsonObject body = leaveBody();
		if (body == null || socket.presence(null) || executor.isShutdown()) return;
		executor.submit(() -> post("/v1/presence/leave", body));
	}

	void shutDown()
	{
		JsonObject body = leaveBody();
		if (body == null) return;
		Thread thread = new Thread(() -> post("/v1/presence/leave", body), "tsg-hub-presence-leave");
		thread.setDaemon(true);
		thread.start();
	}

	void loadMembers(boolean quiet)
	{
		if (!canUse() || executor.isShutdown()) return;
		String clan = encode(plugin.getDetectedClanName());
		if (!quiet) ui(s -> s.setBusy(true));
		executor.submit(() -> {
			try
			{
				String token = key.get();
				JsonArray members = TsgHubUi.array(api.get().request("GET", "/v1/presence?clanName=" + clan, token, null), "members");
				JsonArray loaded = loadNotes(clan, token);
				if (loaded != null) notes = loaded;
				JsonArray known = notes;
				clientThread.invokeLater(() -> {
					JsonArray[] roster = roster(members, chatWorlds(), clanNames(), known == null ? new JsonArray() : known);
					addRanks(roster[0]);
					addRanks(roster[1]);
					loadAccountIcons(roster[0]);
					loadAccountIcons(roster[1]);
					ui(s -> s.setMembers(roster[0], roster[1], known != null));
				});
			}
			catch (Exception e) { if (!quiet) ui(s -> s.setStatus("Couldn't load members. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
			finally { if (!quiet) ui(s -> s.setBusy(false)); }
		});
	}

	private JsonArray loadNotes(String clan, String token)
	{
		try { return TsgHubUi.array(api.get().request("GET", "/v1/members?clanName=" + clan, token, null), "members"); }
		catch (Exception e) { return null; }
	}

	private Map<String, Integer> chatWorlds()
	{
		Map<String, Integer> worlds = new LinkedHashMap<>();
		ClanChannel channel = client.getClanChannel();
		if (channel == null) return worlds;
		for (ClanChannelMember member : channel.getMembers())
		{
			if (member != null && member.getName() != null) worlds.put(Text.toJagexName(member.getName()), member.getWorld());
		}
		return worlds;
	}

	private List<String> clanNames()
	{
		List<String> names = new ArrayList<>();
		ClanSettings settings = client.getClanSettings();
		if (settings == null) return names;
		for (ClanMember member : settings.getMembers())
		{
			if (member != null && member.getName() != null) names.add(Text.toJagexName(member.getName()));
		}
		return names;
	}

	static JsonArray[] roster(JsonArray presence, Map<String, Integer> chatWorlds, List<String> clanNames, JsonArray notes)
	{
		Map<String, JsonObject> byName = new HashMap<>();
		for (int i = 0; i < notes.size(); i++)
		{
			if (!notes.get(i).isJsonObject()) continue;
			JsonObject note = notes.get(i).getAsJsonObject();
			byName.put(TsgHubUi.playerKey(TsgHubUi.str(note, "displayName")), note);
		}
		List<JsonObject> online = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < presence.size(); i++)
		{
			if (!presence.get(i).isJsonObject()) continue;
			JsonObject member = presence.get(i).getAsJsonObject();
			if (seen.add(TsgHubUi.playerKey(TsgHubUi.str(member, "displayName")))) online.add(member);
		}
		for (Map.Entry<String, Integer> entry : chatWorlds.entrySet())
		{
			if (!seen.add(TsgHubUi.playerKey(entry.getKey()))) continue;
			JsonObject member = new JsonObject();
			member.addProperty("displayName", entry.getKey());
			if (entry.getValue() > 0) member.addProperty("world", entry.getValue());
			online.add(member);
		}
		List<JsonObject> offline = new ArrayList<>();
		for (String name : clanNames)
		{
			if (!seen.add(TsgHubUi.playerKey(name))) continue;
			JsonObject member = new JsonObject();
			member.addProperty("displayName", name);
			offline.add(member);
		}
		return new JsonArray[] {sorted(online, byName), sorted(offline, byName)};
	}

	private static JsonArray sorted(List<JsonObject> members, Map<String, JsonObject> notes)
	{
		members.sort(Comparator.comparing(m -> TsgHubUi.str(m, "displayName").toLowerCase(Locale.ROOT)));
		JsonArray out = new JsonArray();
		for (JsonObject member : members)
		{
			JsonObject note = notes.get(TsgHubUi.playerKey(TsgHubUi.str(member, "displayName")));
			if (note != null)
			{
				for (String field : new String[] {"altOf", "alts", "note", "lastSeenAt", "warnings", "accountType", "previousNames"})
				{
					if (note.has(field)) member.add(field, note.get(field));
				}
			}
			out.add(member);
		}
		return out;
	}

	void saveNote(String displayName, String altOf, String note)
	{
		JsonObject body = adminBody();
		body.addProperty("displayName", displayName);
		body.addProperty("altOf", altOf);
		body.addProperty("note", note);
		adminWrite("PUT", "/v1/members/notes", body, "Saved " + displayName + ".");
	}

	void addWarning(String displayName, String reason, int expiresInDays)
	{
		JsonObject body = adminBody();
		body.addProperty("displayName", displayName);
		body.addProperty("reason", reason);
		body.addProperty("expiresInDays", expiresInDays);
		adminWrite("POST", "/v1/members/warnings", body, "Warned " + displayName + ".");
	}

	void revokeWarning(String displayName, String warningId, String reason)
	{
		JsonObject body = adminBody();
		body.addProperty("reason", reason);
		adminWrite("POST", "/v1/members/warnings/" + encode(warningId) + "/revoke", body, "Revoked a warning for " + displayName + ".");
	}

	private JsonObject adminBody()
	{
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.getDetectedClanName());
		return body;
	}

	private void adminWrite(String method, String path, JsonObject body, String success)
	{
		String token = key.get();
		if (token.isEmpty() || executor.isShutdown()) return;
		ui(s -> s.setBusy(true));
		executor.submit(() -> {
			try
			{
				api.get().request(method, path, token, body);
				ui(s -> s.setStatus(success, Tone.SUCCESS));
				loadMembers(true);
			}
			catch (Exception e) { ui(s -> s.setStatus("Couldn't save. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
			finally { ui(s -> s.setBusy(false)); }
		});
	}

	void autoRefresh()
	{
		TsgHubSidebarPanel s = sidebar.get();
		if (s != null && s.wantsMembers()) loadMembers(true);
	}

	private void addRanks(JsonArray members)
	{
		ClanSettings settings = client.getClanSettings();
		if (settings == null) return;
		for (int i = 0; i < members.size(); i++)
		{
			JsonObject member = members.get(i).getAsJsonObject();
			ClanMember found = settings.findMember(TsgHubUi.str(member, "displayName"));
			if (found == null || found.getRank() == null) continue;
			ClanTitle title = settings.titleForRank(found.getRank());
			if (title == null) continue;
			if (title.getName() != null) member.addProperty("rank", title.getName());
			member.addProperty("rankId", title.getId());
			BufferedImage icon = chatIcons.getRankImage(title);
			if (icon != null) rankIcons.put(title.getId(), icon);
		}
	}

	private void loadAccountIcons(JsonArray members)
	{
		for (int i = 0; i < members.size(); i++)
		{
			int type = accountType(members.get(i).getAsJsonObject());
			if (type == 0 || accountIcons.containsKey(type)) continue;
			BufferedImage icon = sprites.getSprite(SpriteID.MOD_ICONS, ACCOUNT_ICONS[type].getIndex());
			if (icon != null) accountIcons.put(type, icon);
		}
	}

	BufferedImage nameIcon(JsonObject member)
	{
		BufferedImage rank = member.has("rankId") ? rankIcons.get(TsgHubUi.integer(member, "rankId", 0)) : null;
		int type = accountType(member);
		BufferedImage account = accountIcons.get(type);
		if (account == null) return rank;
		if (rank == null) return account;
		return nameIcons.computeIfAbsent(TsgHubUi.integer(member, "rankId", 0) + ":" + type, k -> sideBySide(rank, account));
	}

	private static BufferedImage sideBySide(BufferedImage left, BufferedImage right)
	{
		BufferedImage out = new BufferedImage(left.getWidth() + 2 + right.getWidth(), Math.max(left.getHeight(), right.getHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(left, 0, (out.getHeight() - left.getHeight()) / 2, null);
		g.drawImage(right, left.getWidth() + 2, (out.getHeight() - right.getHeight()) / 2, null);
		g.dispose();
		return out;
	}

	static int accountType(JsonObject member)
	{
		int type = TsgHubUi.integer(member, "accountType", 0);
		return type > 0 && type < ACCOUNT_ICONS.length ? type : 0;
	}

	private JsonObject snapshot()
	{
		if (!plugin.sharingEnabled() || !plugin.isInHubClan() || !plugin.inClanChat()) return null;
		if (client.getGameState() != GameState.LOGGED_IN) return null;
		Player player = client.getLocalPlayer();
		long hash = client.getAccountHash();
		String name = plugin.getDetectedPlayerName();
		if (player == null || player.getLocalLocation() == null || hash == -1 || name.isEmpty()) return null;
		boolean detailed = plugin.locationSharingEnabled();
		AreaNames.Area area = this.area;
		JsonObject payload = new JsonObject();
		payload.addProperty("clanName", plugin.getDetectedClanName());
		payload.addProperty("displayName", name);
		payload.addProperty("accountHash", Long.toString(hash));
		payload.addProperty("world", client.getWorld());
		payload.addProperty("accountType", client.getVarbitValue(VarbitID.IRONMAN));
		payload.addProperty("area", area == null ? "" : area.name);
		payload.addProperty("activity", detailed ? activity.activity(area, System.currentTimeMillis()) : "Online");
		return payload;
	}

	String area()
	{
		AreaNames.Area current = area;
		return current == null ? "" : current.name;
	}

	private AreaNames.Area locate()
	{
		if (!plugin.locationSharingEnabled() || client.getGameState() != GameState.LOGGED_IN) return null;
		Player player = client.getLocalPlayer();
		if (player == null || player.getLocalLocation() == null) return null;
		return AreaNames.at(WorldPoint.fromLocalInstance(client, player.getLocalLocation()));
	}

	private void trackOpponents()
	{
		if (!plugin.locationSharingEnabled() || client.getGameState() != GameState.LOGGED_IN) return;
		Player player = client.getLocalPlayer();
		if (player == null) return;
		long now = System.currentTimeMillis();
		Actor target = player.getInteracting();
		if (target instanceof NPC) activity.onOpponent(target.getName(), now);
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null) return;
		for (NPC npc : worldView.npcs())
		{
			if (npc != null && npc.getInteracting() == player) activity.onOpponent(npc.getName(), now);
		}
	}

	private void trackSlayerTask()
	{
		if (!slayerDirty || client.getGameState() != GameState.LOGGED_IN) return;
		int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		int target = remaining > 0 ? client.getVarpValue(VarPlayerID.SLAYER_TARGET) : 0;
		int boss = target == SLAYER_BOSS_TARGET ? client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID) : 0;
		String name = remaining > 0 ? slayerTaskName(target, boss) : null;
		activity.onSlayerTask(((long) target << 32) | boss, remaining, name, System.currentTimeMillis());
		slayerDirty = activity.needsSlayerTaskName();
	}

	private String slayerTaskName(int target, int boss)
	{
		Object row;
		if (target == SLAYER_BOSS_TARGET)
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, boss);
			row = rows.isEmpty() ? null : first(client.getDBTableField(rows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0));
		}
		else
		{
			List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, target);
			row = rows.isEmpty() ? null : rows.get(0);
		}
		if (!(row instanceof Integer)) return null;
		Object name = first(client.getDBTableField((Integer) row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0));
		return name instanceof String && !((String) name).isEmpty() ? (String) name : null;
	}

	private static Object first(Object[] values)
	{
		return values == null || values.length == 0 ? null : values[0];
	}

	private void send(JsonObject payload, long now)
	{
		listed = payload;
		sentAt = now;
		if (!socket.presence(payload) && !executor.isShutdown()) executor.submit(() -> post("/v1/presence", payload));
	}

	private JsonObject leaveBody()
	{
		JsonObject current = listed;
		if (current == null) return null;
		listed = null;
		sentAt = 0;
		JsonObject body = new JsonObject();
		body.add("clanName", current.get("clanName"));
		body.add("accountHash", current.get("accountHash"));
		return body;
	}

	private void post(String path, JsonObject body)
	{
		try { api.get().request("POST", path, null, body); }
		catch (Exception ignored) { }
	}

	private boolean canUse()
	{
		return plugin.isInHubClan() && plugin.sharingEnabled() && !plugin.getDetectedPlayerName().isEmpty();
	}

	private static String encode(String value)
	{
		try { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name()); }
		catch (java.io.UnsupportedEncodingException e) { return ""; }
	}

	private void ui(Consumer<TsgHubSidebarPanel> action)
	{
		SwingUtilities.invokeLater(() -> {
			TsgHubSidebarPanel s = sidebar.get();
			if (s != null) action.accept(s);
		});
	}
}
