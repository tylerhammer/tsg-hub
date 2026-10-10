package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.util.Text;

final class TsgHubClaims
{
	private static final Set<Integer> PVM_PET_ITEM_IDS = Set.of(11995, 12643, 12644, 12645, 12646, 12647, 12648, 12649, 12650, 12651, 12652, 12654, 12655, 12703, 12816, 12921, 12939, 12940, 13178, 13179, 13180, 13181, 13182, 13225, 13247, 13262, 20693, 20851, 21273, 21291, 21748, 21992, 22318, 22319, 22376, 22378, 22380, 22382, 22384, 22473, 22663, 22746, 22748, 22750, 22752, 23495, 23757, 23759, 23760, 24491, 25748, 25749, 25750, 25751, 25752, 25836, 25842, 25843, 26348, 27352, 27386, 27590, 27592, 27593, 27649, 27650, 27651, 28246, 28248, 28250, 28252, 28801, 28960, 29836, 30152, 30154, 30622, 30888, 31130, 31285, 31287);
	private static final int PET_CHECK_TICKS = 5;
	private static final int MAX_LOOT_LOG_ITEMS = 50;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Set<String> attemptedXpClaims = ConcurrentHashMap.newKeySet();
	private final Set<String> attemptedKillCountClaims = ConcurrentHashMap.newKeySet();
	private final Map<String, Integer> recentLootEvents = new ConcurrentHashMap<>();
	private volatile List<XpTask> xpTasks = Collections.emptyList();
	private volatile List<PvmTask> pvmTasks = Collections.emptyList();
	private volatile String taskEventId = "";
	private int petCheckTicks;
	private Set<Integer> inventoryPets = Collections.emptySet();

	TsgHubClaims(TsgHubPlugin plugin, Client client, ItemManager itemManager, ScheduledExecutorService executor, Supplier<TsgHubApi> api)
	{
		this.plugin = plugin;
		this.client = client;
		this.itemManager = itemManager;
		this.executor = executor;
		this.api = api;
	}

	void setTasks(String eventId, JsonObject event)
	{
		cacheXpTasks(event);
		cachePvmTasks(event);
		taskEventId = eventId;
	}

	void clear()
	{
		taskEventId = "";
		xpTasks = Collections.emptyList();
		pvmTasks = Collections.emptyList();
		attemptedXpClaims.clear();
		attemptedKillCountClaims.clear();
	}

	void onXp(String skill, int xp)
	{
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		for (XpTask task : xpTasks)
		{
			if (!skill.equalsIgnoreCase(task.skill) || xp < task.targetXp) continue;
			String key = task.id + ":" + task.targetXp;
			if (!attemptedXpClaims.add(key)) continue;
			executor.submit(() -> submitXpClaim(eventId, task, skill, xp, key));
		}
	}

	void onChat(String message)
	{
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		submitKillCountSignals(eventId, message);
		submitCollectionLogSignals(eventId, message);
		if (isPetMessage(message) && hasPetTask()) petCheckTicks = PET_CHECK_TICKS;
		String mode = detectRaidMode(message);
		if (mode == null) return;
		List<String> visiblePlayers = new ArrayList<>();
		List<String> nonClanPlayers = new ArrayList<>();
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView != null && worldView.players() != null)
		{
			for (Player player : worldView.players())
			{
				if (player == null || player.getName() == null) continue;
				String name = player.getName();
				if (visiblePlayers.stream().noneMatch(existing -> TsgHubUi.samePlayer(existing, name))) visiblePlayers.add(name);
				if (!player.isClanMember() && nonClanPlayers.stream().noneMatch(existing -> TsgHubUi.samePlayer(existing, name))) nonClanPlayers.add(name);
			}
		}
		for (PvmTask task : pvmTasks)
		{
			if (!"raid".equals(task.type) || !task.targetNames.contains(mode)) continue;
			submitRaidClaim(eventId, task, mode, visiblePlayers, nonClanPlayers);
		}
	}

	void onTick()
	{
		if (!hasPetTask())
		{
			petCheckTicks = 0;
			inventoryPets = Collections.emptySet();
			return;
		}
		if (petCheckTicks <= 0)
		{
			inventoryPets = inventoryPetIds();
			return;
		}
		petCheckTicks--;
		int petId = newPetId();
		if (petId <= 0) return;
		petCheckTicks = 0;
		inventoryPets = inventoryPetIds();
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		String petName = itemManager.getItemComposition(petId).getName();
		for (PvmTask task : pvmTasks)
		{
			if ("drop".equals(task.type) && "pet".equals(task.itemGroup)) submitPvmClaim(eventId, task, "drop", petName, 1, petId);
		}
	}

	private static boolean isPetMessage(String message)
	{
		return message.contains("you have a funny feeling like you're being followed")
			|| message.contains("you feel something weird sneaking into your backpack");
	}

	private boolean hasPetTask()
	{
		for (PvmTask task : pvmTasks) if ("drop".equals(task.type) && "pet".equals(task.itemGroup)) return true;
		return false;
	}

	private Set<Integer> inventoryPetIds()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null) return Collections.emptySet();
		Set<Integer> pets = new HashSet<>();
		for (Item item : inventory.getItems())
		{
			int itemId = itemManager.canonicalize(item.getId());
			if (PVM_PET_ITEM_IDS.contains(itemId)) pets.add(itemId);
		}
		return pets;
	}

	private int newPetId()
	{
		for (int itemId : inventoryPetIds()) if (!inventoryPets.contains(itemId)) return itemId;
		NPC follower = client.getFollower();
		String followerName = follower == null ? null : petName(follower.getName());
		if (followerName == null || followerName.isEmpty()) return -1;
		for (int itemId : PVM_PET_ITEM_IDS)
		{
			if (followerName.equals(petName(itemManager.getItemComposition(itemId).getName()))) return itemId;
		}
		return -1;
	}

	private static String petName(String name)
	{
		if (name == null) return null;
		String normalized = Text.removeTags(name).trim().toLowerCase(Locale.ROOT);
		return normalized.startsWith("pet ") ? normalized.substring(4) : normalized;
	}

	private void submitCollectionLogSignals(String eventId, String message)
	{
		if (!message.contains("collection log") || !(message.contains("new item") || message.contains("new entry"))) return;
		for (PvmTask task : pvmTasks)
		{
			if (!"drop".equals(task.type) || !task.requireAllItems) continue;
			for (int i = 0; i < task.targetNames.size(); i++)
			{
				String itemName = task.targetNames.get(i);
				if (!message.contains(itemName.toLowerCase(Locale.ROOT))) continue;
				int itemId = i < task.targetItemIds.size() ? task.targetItemIds.get(i) : 0;
				submitPvmClaim(eventId, task, "drop", itemName, 1, itemId);
			}
		}
	}

	private void submitKillCountSignals(String eventId, String message)
	{
		int count = TsgHubCompetitionTracker.killCount(message);
		if (count < 0) return;
		for (PvmTask task : pvmTasks)
		{
			if (!"kill".equals(task.type) || !task.chatKillCount || task.targetNames.isEmpty()) continue;
			String bossName = task.targetNames.get(0);
			if (!message.contains(bossName.toLowerCase(Locale.ROOT))) continue;
			String key = task.id + ":" + count;
			if (!attemptedKillCountClaims.add(key)) continue;
			JsonObject evidence = new JsonObject();
			evidence.addProperty("name", bossName);
			evidence.addProperty("quantity", 1);
			evidence.addProperty("killCount", count);
			postClaim(eventId, claim(task.id, "kc-" + task.id + "-" + count, "kill", evidence), "Kill count sync failed. ", () -> attemptedKillCountClaims.remove(key));
		}
	}

	private String detectRaidMode(String message)
	{
		if (message.contains("theatre of blood") && message.contains("completion time"))
		{
			if (message.contains("hard mode")) return "tob_hm";
			if (message.contains("entry mode")) return "tob_entry";
			return "tob";
		}
		if (message.contains("tombs of amascut") && message.contains("completion time"))
		{
			if (message.contains("expert")) return "toa_expert";
			if (message.contains("entry mode")) return "toa_entry";
			return "toa";
		}
		if (message.contains("your completed chambers of xeric challenge mode count is")) return "cox_cm";
		if (message.contains("your completed chambers of xeric count is")) return "cox";
		return null;
	}

	private void submitRaidClaim(String eventId, PvmTask task, String mode, List<String> players, List<String> nonClanPlayers)
	{
		JsonObject evidence = new JsonObject();
		evidence.addProperty("mode", mode);
		JsonArray playerNames = new JsonArray();
		players.forEach(playerNames::add);
		evidence.add("players", playerNames);
		JsonArray outsiders = new JsonArray();
		nonClanPlayers.forEach(outsiders::add);
		evidence.add("nonClanPlayers", outsiders);
		postClaim(eventId, claim(task.id, "raid-" + UUID.randomUUID(), "raid", evidence), "Raid progress not credited. ", null);
	}

	void onLoot(String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
		if (sourceName == null || items == null || isDuplicateLootEvent(sourceType, sourceName, items)) return;
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		String lootId = "loot-" + UUID.randomUUID();
		submitLootLog(eventId, lootId, sourceType, sourceName, items, amount);
		for (PvmTask task : pvmTasks)
		{
			if ("kill".equals(task.type) && !task.chatKillCount && "NPC".equals(sourceType) && !task.targetNames.isEmpty() && sourceName.equalsIgnoreCase(task.targetNames.get(0)))
			{
				submitPvmClaim(eventId, task, "kill", sourceName, Math.max(1, amount), -1, lootId);
			}
			else if ("drop".equals(task.type))
			{
				for (ItemStack item : items)
				{
					int itemId = itemManager.canonicalize(item.getId());
					String itemName = client.getItemDefinition(itemId).getName();
					boolean itemMatch = "jar".equals(task.itemGroup) && itemName != null
						&& itemName.toLowerCase(Locale.ROOT).startsWith("jar of ")
						|| "pet".equals(task.itemGroup) && PVM_PET_ITEM_IDS.contains(itemId);
					for (int i = 0; i < task.targetNames.size(); i++)
					{
						int configuredId = i < task.targetItemIds.size() ? task.targetItemIds.get(i) : 0;
						if (configuredId > 0 ? configuredId == itemId : itemName != null && itemName.equalsIgnoreCase(task.targetNames.get(i))) itemMatch = true;
					}
					if (itemName != null && itemMatch)
						submitPvmClaim(eventId, task, "drop", itemName, item.getQuantity(), itemId, lootId);
				}
			}
		}
	}

	private void submitLootLog(String eventId, String lootId, String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
		JsonArray lootItems = new JsonArray();
		for (ItemStack item : items)
		{
			if (lootItems.size() >= MAX_LOOT_LOG_ITEMS) break;
			int itemId = itemManager.canonicalize(item.getId());
			String itemName = client.getItemDefinition(itemId).getName();
			if (itemName == null || item.getQuantity() < 1) continue;
			JsonObject entry = new JsonObject();
			entry.addProperty("itemId", itemId);
			entry.addProperty("name", itemName);
			entry.addProperty("quantity", item.getQuantity());
			lootItems.add(entry);
		}
		if (lootItems.size() == 0) return;
		JsonObject loot = new JsonObject();
		loot.addProperty("lootId", lootId);
		loot.addProperty("sourceType", sourceType);
		loot.addProperty("sourceName", sourceName);
		loot.addProperty("kills", Math.max(1, amount));
		loot.add("items", lootItems);
		executor.submit(() -> {
			try { api.get().request("POST", "/v1/events/" + eventId + "/loot", TsgHubSession.memberToken(eventId), loot); }
			catch (Exception ignored) { }
		});
	}

	private boolean isDuplicateLootEvent(String sourceType, String sourceName, Collection<ItemStack> items)
	{
		int tick = client.getTickCount();
		recentLootEvents.entrySet().removeIf(entry -> tick - entry.getValue() > 3);
		List<String> stacks = new ArrayList<>();
		for (ItemStack item : items) stacks.add(itemManager.canonicalize(item.getId()) + "x" + item.getQuantity());
		Collections.sort(stacks);
		String key = tick + ":" + sourceType + ":" + sourceName.toLowerCase(Locale.ROOT) + ":" + String.join(",", stacks);
		return recentLootEvents.putIfAbsent(key, tick) != null;
	}

	private String claimEventId()
	{
		String eventId = TsgHubSession.get("eventId");
		if (eventId.isEmpty() || !eventId.equals(taskEventId) || TsgHubSession.memberToken(eventId).isEmpty()) return "";
		return eventId;
	}

	private void submitPvmClaim(String eventId, PvmTask task, String source, String name, int quantity, int itemId)
	{
		submitPvmClaim(eventId, task, source, name, quantity, itemId, null);
	}

	private void submitPvmClaim(String eventId, PvmTask task, String source, String name, int quantity, int itemId, String lootId)
	{
		JsonObject evidence = new JsonObject();
		evidence.addProperty("name", name);
		evidence.addProperty("quantity", quantity);
		if (itemId > 0) evidence.addProperty("itemId", itemId);
		if (lootId != null) evidence.addProperty("lootId", lootId);
		postClaim(eventId, claim(task.id, source + "-" + UUID.randomUUID(), source, evidence), "PVM progress sync failed. ", null);
	}

	static JsonObject claim(String taskId, String evidenceId, String source, JsonObject evidence)
	{
		JsonObject claim = new JsonObject();
		claim.addProperty("taskId", taskId);
		claim.addProperty("evidenceId", evidenceId);
		claim.addProperty("source", source);
		claim.add("evidence", evidence);
		return claim;
	}

	private void postClaim(String eventId, JsonObject claim, String failure, Runnable onFailure)
	{
		executor.submit(() -> {
			try
			{
				plugin.showProgressMessage(api.get().request("POST", "/v1/events/" + eventId + "/claims", TsgHubSession.memberToken(eventId), claim));
				SwingUtilities.invokeLater(plugin::refreshBoard);
			}
			catch (Exception e)
			{
				if (onFailure != null) onFailure.run();
				plugin.memberError(failure, e);
			}
		});
	}

	private void submitXpClaim(String eventId, XpTask task, String skill, int xp, String key)
	{
		JsonObject evidence = new JsonObject();
		evidence.addProperty("skill", skill);
		evidence.addProperty("xp", xp);
		try { plugin.showProgressMessage(api.get().request("POST", "/v1/events/" + eventId + "/claims", TsgHubSession.memberToken(eventId), claim(task.id, "xp-" + key, "xp", evidence))); }
		catch (Exception e)
		{
			attemptedXpClaims.remove(key);
			plugin.memberError("Progress sync paused. ", e);
		}
	}

	private void cacheXpTasks(JsonObject event)
	{
		List<XpTask> found = new ArrayList<>();
		for (JsonObject task : TsgHubUi.objects(event.getAsJsonArray("tasks")))
		{
			if (!"xp".equals(task.get("type").getAsString())) continue;
			JsonObject config = task.getAsJsonObject("config");
			found.add(new XpTask(task.get("id").getAsString(), config.get("skill").getAsString(), config.get("targetXp").getAsInt()));
		}
		xpTasks = found;
	}

	private void cachePvmTasks(JsonObject event)
	{
		List<PvmTask> found = new ArrayList<>();
		for (JsonObject task : TsgHubUi.objects(event.getAsJsonArray("tasks")))
		{
			String type = task.get("type").getAsString();
			if (!"kill".equals(type) && !"drop".equals(type) && !"raid".equals(type)) continue;
			JsonObject config = task.getAsJsonObject("config");
			List<String> targetNames = new ArrayList<>();
			if ("kill".equals(type)) targetNames.add(config.get("npcName").getAsString());
			else if ("raid".equals(type) && config.has("modes"))
			{
				for (JsonElement mode : config.getAsJsonArray("modes")) targetNames.add(mode.getAsString().toLowerCase(Locale.ROOT));
			}
			else if (config.has("itemNames") && config.get("itemNames").isJsonArray())
			{
				for (JsonElement name : config.getAsJsonArray("itemNames")) targetNames.add(name.getAsString());
			}
			else if (config.has("itemName")) targetNames.add(config.get("itemName").getAsString());
			List<Integer> targetItemIds = new ArrayList<>();
			for (JsonElement id : TsgHubUi.array(config, "itemIds")) targetItemIds.add(id.getAsInt());
			found.add(new PvmTask(task.get("id").getAsString(), type, targetNames, targetItemIds, "chat".equals(TsgHubUi.str(config, "signal")),
				TsgHubUi.bool(config, "requireAllItems") || config.has("itemGroups"), TsgHubUi.str(config, "itemGroup")));
		}
		pvmTasks = found;
	}

	private static final class XpTask
	{
		private final String id;
		private final String skill;
		private final int targetXp;
		private XpTask(String id, String skill, int targetXp) { this.id = id; this.skill = skill; this.targetXp = targetXp; }
	}

	private static final class PvmTask
	{
		private final String id;
		private final String type;
		private final List<String> targetNames;
		private final List<Integer> targetItemIds;
		private final boolean chatKillCount;
		private final boolean requireAllItems;
		private final String itemGroup;
		private PvmTask(String id, String type, List<String> targetNames, List<Integer> targetItemIds, boolean chatKillCount, boolean requireAllItems, String itemGroup)
		{
			this.id = id;
			this.type = type;
			this.targetNames = targetNames;
			this.targetItemIds = targetItemIds;
			this.chatKillCount = chatKillCount;
			this.requireAllItems = requireAllItems;
			this.itemGroup = itemGroup;
		}
	}
}
