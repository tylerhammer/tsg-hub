package com.tsghub;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.*;
import net.runelite.client.game.*;
import net.runelite.client.plugins.loottracker.*;
import net.runelite.client.util.*;

@RequiredArgsConstructor
final class TsgHubGameTracker
{
	private static final int PET_CHECK_TICKS = 5;
	private static final int MAX_LOOT_LOG_ITEMS = 50;

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executor;
	private final Map<String, Integer> recentLootEvents = new ConcurrentHashMap<>();
	private int petCheckTicks;
	private int recentPet;
	private int recentPetTick;
	private String lastGroupContent = "";
	private int lastGroupContentTick = -1;
	private Set<Integer> inventoryPets = Collections.emptySet();
	private final TsgHubCaskets caskets = new TsgHubCaskets();

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		rewardInterface(event.getGroupId(), true);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		rewardInterface(event.getGroupId(), false);
	}

	private void rewardInterface(int groupId, boolean open)
	{
		if (plugin.sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(groupId)) plugin.liveSync().socket().rewardInterface(groupId, open, client.getTickCount());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!plugin.clan().isHubClan() || event.getComposition() == null) return;
		String name = Text.removeTags(event.getComposition().getName());
		plugin.drops().onLoot(name, itemNames(event.getItems()));
		processLoot("NPC", name, event.getItems(), 1);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!plugin.clan().isHubClan()) return;
		if (event.getType() == null || "PLAYER".equals(event.getType().name()) || "NPC".equals(event.getType().name())) return;
		plugin.drops().onLoot(event.getName(), itemNames(event.getItems()));
		processLoot(event.getType().name(), event.getName(), event.getItems(), event.getAmount());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event == null || event.getMessage() == null) return;
		if (event.getType() == ChatMessageType.CLAN_MESSAGE && plugin.chat().isOwnEcho(event.getMessage())) return;
		if (!plugin.clan().isHubClan() || client.getLocalPlayer() == null) return;
		plugin.drops().onChatMessage(event);
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
		if (!plugin.sharingInClan()) return;
		String plain = Text.removeTags(event.getMessage());
		String message = plain.toLowerCase(Locale.ROOT);
		if (isPetMessage(message))
		{
			if (recentPet > 0 && client.getTickCount() - recentPetTick <= 2) sendPet(recentPet);
			else petCheckTicks = PET_CHECK_TICKS;
			recentPet = 0;
		}
		if (TsgHubRules.get().groupContent.stream().anyMatch(message::contains)) reportGroupContent(plain.trim(), message);
		TsgHubSignals signals = plugin.signals();
		if (!signals.wantsChat(message)) return;
		JsonObject chat = new JsonObject();
		chat.addProperty("text", message);
		if (signals.wantsPlayers(message)) addNearbyPlayers(chat);
		signals.send("chat", chat);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!plugin.sharingInClan())
		{
			petCheckTicks = 0;
			return;
		}
		if (petCheckTicks <= 0) return;
		petCheckTicks--;
		int petId = followerPetId();
		if (petId > 0) sendPet(petId);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || !plugin.sharingInClan()) return;
		trackCaskets(event.getItemContainer());
		Set<Integer> pets = inventoryPetIds();
		int added = 0;
		for (int itemId : pets) if (!inventoryPets.contains(itemId)) added = itemId;
		inventoryPets = pets;
		if (added <= 0) return;
		if (petCheckTicks > 0) sendPet(added);
		else
		{
			recentPet = added;
			recentPetTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN) caskets.reset();
	}

	private void trackCaskets(ItemContainer inventory)
	{
		if (inventory == null) return;
		Map<String, Integer> casketCounts = new HashMap<>();
		Map<String, Integer> clueCounts = new HashMap<>();
		for (Item item : inventory.getItems())
		{
			if (item.getId() <= 0) continue;
			String name = itemManager.getItemComposition(item.getId()).getName();
			String casket = TsgHubCaskets.casketTier(name);
			if (casket != null) casketCounts.merge(casket, item.getQuantity(), Integer::sum);
			String clue = TsgHubCaskets.clueTier(name);
			if (clue != null) clueCounts.merge(clue, item.getQuantity(), Integer::sum);
		}
		for (String tier : caskets.update(casketCounts, clueCounts, client.getTickCount(), storageOpen()))
		{
			JsonObject casket = new JsonObject();
			casket.addProperty("tier", tier);
			plugin.signals().send("casket", casket);
		}
	}

	private boolean storageOpen()
	{
		for (int id : new int[]{InterfaceID.Bankmain.UNIVERSE, InterfaceID.SharedBank.UNIVERSE})
		{
			Widget widget = client.getWidget(id);
			if (widget != null && !widget.isHidden()) return true;
		}
		return false;
	}

	void reportXp(String skills)
	{
		if (!plugin.sharingInClan()) return;
		clientThread.invokeLater(() -> {
			if (client.getLocalPlayer() == null) return;
			for (String name : skills.split(","))
			{
				Skill skill;
				try { skill = Skill.valueOf(name.trim()); }
				catch (IllegalArgumentException e) { continue; }
				plugin.signals().xp(skill.name(), client.getSkillExperience(skill), client.getTickCount());
			}
		});
	}

	void report(String type, String method, String path, String token, JsonObject body)
	{
		TsgHubOutbox outbox = plugin.outbox();
		if (outbox == null || outbox.report(type, method, path, token, body) || executor.isShutdown()) return;
		if (client.isClientThread()) executor.execute(outbox::flush);
		else outbox.flush();
	}

	void reset()
	{
		recentLootEvents.clear();
		petCheckTicks = 0;
		recentPet = 0;
		recentPetTick = 0;
		lastGroupContent = "";
		lastGroupContentTick = -1;
		inventoryPets = Collections.emptySet();
		caskets.reset();
	}

	private void reportGroupContent(String text, String key)
	{
		int tick = client.getTickCount();
		if (tick == lastGroupContentTick && key.equals(lastGroupContent)) return;
		lastGroupContentTick = tick;
		lastGroupContent = key;
		long hash = client.getAccountHash();
		Player self = client.getLocalPlayer();
		WorldView worldView = client.getTopLevelWorldView();
		if (hash == -1 || self == null || worldView == null || worldView.players() == null || executor.isShutdown()) return;
		List<String> nearby = new ArrayList<>();
		for (Player player : worldView.players())
		{
			if (player != null && player != self && player.getName() != null && player.isClanMember()) nearby.add(Text.toJagexName(player.getName()));
		}
		List<String> clanmates = clanmates(nearby, self.getName());
		if (clanmates.isEmpty()) return;
		TsgHubClan clan = plugin.clan();
		JsonObject body = clan.identity(clan.playerName().isEmpty() ? Text.toJagexName(self.getName()) : clan.playerName());
		body.addProperty("message", text);
		JsonArray names = new JsonArray();
		clanmates.forEach(names::add);
		body.add("clanmates", names);
		report("group", "POST", "/v1/group-content", null, body);
	}

	static List<String> clanmates(List<String> names, String self)
	{
		List<String> out = new ArrayList<>();
		for (String name : names)
		{
			if (name == null || name.trim().isEmpty() || PlayerNames.samePlayer(name, self)) continue;
			if (out.stream().noneMatch(existing -> PlayerNames.samePlayer(existing, name))) out.add(name);
		}
		return out;
	}

	private void addNearbyPlayers(JsonObject chat)
	{
		List<String> visiblePlayers = new ArrayList<>();
		List<String> nonClanPlayers = new ArrayList<>();
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView != null && worldView.players() != null)
		{
			for (Player player : worldView.players())
			{
				if (player == null || player.getName() == null) continue;
				String name = player.getName();
				if (visiblePlayers.stream().noneMatch(existing -> PlayerNames.samePlayer(existing, name))) visiblePlayers.add(name);
				if (!player.isClanMember() && nonClanPlayers.stream().noneMatch(existing -> PlayerNames.samePlayer(existing, name))) nonClanPlayers.add(name);
			}
		}
		JsonArray players = new JsonArray();
		visiblePlayers.forEach(players::add);
		chat.add("players", players);
		JsonArray outsiders = new JsonArray();
		nonClanPlayers.forEach(outsiders::add);
		chat.add("nonClanPlayers", outsiders);
	}

	private void sendPet(int petId)
	{
		petCheckTicks = 0;
		recentPet = 0;
		JsonObject pet = new JsonObject();
		pet.addProperty("itemId", petId);
		pet.addProperty("name", itemManager.getItemComposition(petId).getName());
		plugin.signals().send("pet", pet);
	}

	private static boolean isPetMessage(String message)
	{
		for (String pattern : TsgHubRules.get().petMessages) if (message.contains(pattern)) return true;
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
			if (TsgHubRules.get().pets.contains(itemId)) pets.add(itemId);
		}
		return pets;
	}

	private int followerPetId()
	{
		NPC follower = client.getFollower();
		String followerName = follower == null ? null : petName(follower.getName());
		if (followerName == null || followerName.isEmpty()) return -1;
		for (int itemId : TsgHubRules.get().pets)
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

	private List<String> itemNames(Collection<ItemStack> items)
	{
		List<String> names = new ArrayList<>();
		if (items == null) return names;
		for (ItemStack item : items)
		{
			String name = client.getItemDefinition(itemManager.canonicalize(item.getId())).getName();
			if (name != null) names.add(name);
		}
		return names;
	}

	private void processLoot(String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
		if (!plugin.sharingInClan() || client.getLocalPlayer() == null
			|| sourceName == null || items == null || isDuplicateLootEvent(sourceType, sourceName, items)) return;
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
		JsonObject loot = new JsonObject();
		loot.addProperty("sourceType", sourceType);
		loot.addProperty("sourceName", sourceName);
		loot.addProperty("kills", Math.max(1, amount));
		loot.add("items", lootItems);
		plugin.signals().send("loot", loot);
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
}
