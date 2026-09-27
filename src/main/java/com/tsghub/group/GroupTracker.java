/*
 * Adapted from Party Panel by TheStonedTurtle (https://github.com/TheStonedTurtle/party-panel),
 * BSD 2-Clause License. See THIRD_PARTY_NOTICES.md.
 */
package com.tsghub.group;

import com.google.common.collect.ImmutableSet;
import com.tsghub.group.data.GameItem;
import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.data.PrayerData;
import com.tsghub.group.data.Prayers;
import com.tsghub.group.data.Stats;
import com.tsghub.group.data.events.PartyMiscChange;
import com.tsghub.group.data.events.PartyStatChange;
import com.tsghub.group.data.events.TsgGroupUpdate;
import com.tsghub.group.ui.prayer.PrayerSprites;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.PartyMemberAvatar;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import org.apache.commons.lang3.ArrayUtils;

public final class GroupTracker
{
	private static final int[] RUNEPOUCH_AMOUNT_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};
	private static final int[] RUNEPOUCH_RUNE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};
	public static final int[] RUNEPOUCH_ITEM_IDS = {
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER,
	};
	public static final Set<Integer> DIZANAS_QUIVER_IDS = ImmutableSet.<Integer>builder()
		.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.DIZANAS_QUIVER_CHARGED)))
		.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.DIZANAS_QUIVER_INFINITE)))
		.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.SKILLCAPE_MAX_DIZANAS)))
		.build();
	private static final long IDLE_MINUTES = 30;

	// Member callbacks run on the Swing thread.
	public interface Listener
	{
		void memberUpdated(PartyPlayer player, boolean bannerChanged, boolean self);

		void memberRemoved(PartyPlayer player);

		void membersCleared();

		// Runs on the event thread.
		void partyChanged(String passphrase);
	}

	private final Client client;
	private final ClientThread clientThread;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final ItemManager itemManager;
	private final Predicate<String> isGroupParty;
	private final java.util.function.BooleanSupplier showSelf;
	private final Listener listener;

	private final Map<Long, PartyPlayer> partyMembers = new ConcurrentHashMap<>();
	private PartyPlayer myPlayer;
	private PartyPlayer selfView;
	private volatile Instant lastLogout = Instant.now();
	private TsgGroupUpdate currentChange = new TsgGroupUpdate();

	public GroupTracker(Client client, ClientThread clientThread, PartyService partyService, WSClient wsClient,
		ItemManager itemManager, Predicate<String> isGroupParty, java.util.function.BooleanSupplier showSelf, Listener listener)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.itemManager = itemManager;
		this.isGroupParty = isGroupParty;
		this.showSelf = showSelf;
		this.listener = listener;
	}

	public void start()
	{
		wsClient.registerMessage(TsgGroupUpdate.class);
		lastLogout = Instant.now();
		if (isSharing()) announce();
	}

	public void stop()
	{
		if (isSharing() && myPlayer != null)
		{
			final TsgGroupUpdate clean = partyPlayerAsBatchedChange();
			clean.setI(new int[0]);
			clean.setE(new int[0]);
			clean.setM(Collections.emptySet());
			clean.setS(Collections.emptySet());
			clean.setRp(null);
			clean.setQ(new int[0]);
			partyService.send(clean);
		}
		wsClient.unregisterMessage(TsgGroupUpdate.class);
		partyMembers.clear();
		currentChange = new TsgGroupUpdate();
		myPlayer = null;
		selfView = null;
	}

	public boolean isSharing()
	{
		return partyService.isInParty() && isGroupParty.test(partyService.getPartyPassphrase());
	}

	public void announce()
	{
		clientThread.invokeLater(() ->
		{
			if (!isSharing() || partyService.getLocalMember() == null) return;
			myPlayer = new PartyPlayer(partyService.getLocalMember(), client, itemManager, clientThread);
			partyService.send(new UserSync());
			sendUpdate(partyPlayerAsBatchedChange());
		});
	}

	public boolean idleTooLong()
	{
		return client.getGameState() == GameState.LOGIN_SCREEN && lastLogout != null
			&& lastLogout.isBefore(Instant.now().minus(IDLE_MINUTES, ChronoUnit.MINUTES));
	}

	public List<PartyPlayer> getMembers()
	{
		return new ArrayList<>(partyMembers.values());
	}

	private boolean isLocalPlayer(long id)
	{
		return partyService.getLocalMember() != null && partyService.getLocalMember().getMemberId() == id;
	}

	@Subscribe
	public void onPartyChanged(final PartyChanged event)
	{
		partyMembers.clear();
		myPlayer = null;
		selfView = null;
		currentChange = new TsgGroupUpdate();
		SwingUtilities.invokeLater(listener::membersCleared);
		listener.partyChanged(event.getPassphrase());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged c)
	{
		if (c.getGameState() == GameState.LOGIN_SCREEN) lastLogout = Instant.now();
		if (!isSharing()) return;

		if (myPlayer == null)
		{
			myPlayer = new PartyPlayer(partyService.getLocalMember(), client, itemManager, clientThread);
			sendUpdate(partyPlayerAsBatchedChange());
			return;
		}

		if (c.getGameState() == GameState.LOGGED_IN)
		{
			PartyMiscChange e = new PartyMiscChange(PartyMiscChange.PartyMisc.W, client.getWorld());
			if (myPlayer.getWorld() == e.getV()) return;
			myPlayer.setWorld(e.getV());
			currentChange.getM().add(e);
		}

		if (c.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (myPlayer.getWorld() == 0) return;
			myPlayer.setWorld(0);
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.W, 0));
			sendUpdate(currentChange);
			currentChange = new TsgGroupUpdate();
		}
	}

	@Subscribe
	public void onUserJoin(final UserJoin event)
	{
		// The Party plugin may be off, so request and send state ourselves.
		if (isLocalPlayer(event.getMemberId()) && isSharing()) announce();
	}

	@Subscribe
	public void onUserPart(final UserPart event)
	{
		final PartyPlayer removed = partyMembers.remove(event.getMemberId());
		if (removed != null) SwingUtilities.invokeLater(() -> listener.memberRemoved(removed));
	}

	@Subscribe
	public void onUserSync(final UserSync event)
	{
		if (!isSharing()) return;
		if (myPlayer != null)
		{
			final TsgGroupUpdate c = partyPlayerAsBatchedChange();
			if (c.isValid()) sendUpdate(c);
			return;
		}

		clientThread.invoke(() ->
		{
			myPlayer = new PartyPlayer(partyService.getLocalMember(), client, itemManager, clientThread);
			final TsgGroupUpdate c = partyPlayerAsBatchedChange();
			if (c.isValid()) sendUpdate(c);
		});
	}

	@Subscribe
	public void onGameTick(final GameTick tick)
	{
		if (!isSharing() || client.getLocalPlayer() == null || partyService.getLocalMember() == null) return;

		// Only process changes every few ticks to reduce load.
		if (client.getTickCount() % messageFreq(partyService.getMembers().size()) != 0) return;

		if (myPlayer == null || !Objects.equals(client.getLocalPlayer().getName(), myPlayer.getUsername()))
		{
			myPlayer = new PartyPlayer(partyService.getLocalMember(), client, itemManager, clientThread);
			sendUpdate(partyPlayerAsBatchedChange());
			return;
		}

		if (myPlayer.getStats() == null)
		{
			myPlayer.updatePlayerInfo(client, itemManager);
			for (final Skill s : Skill.values())
			{
				currentChange.getS().add(myPlayer.getStats().createPartyStatChangeForSkill(s));
			}
		}
		else
		{
			final int energy = client.getEnergy() / 100;
			if (myPlayer.getStats().getRunEnergy() != energy)
			{
				myPlayer.getStats().setRunEnergy(energy);
				currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.R, energy));
			}
		}

		boolean prayersChanged = false;
		if (myPlayer.getPrayers() == null)
		{
			myPlayer.setPrayers(new Prayers(client));
			prayersChanged = true;
		}
		else
		{
			for (final PrayerSprites p : PrayerSprites.values())
			{
				prayersChanged = myPlayer.getPrayers().updatePrayerState(p, client) || prayersChanged;
			}
		}
		// Unlisted prayers count as off, so send all three sets together.
		if (prayersChanged) setPrayers(currentChange);

		if (myPlayer.getSpellbook() == -1) updateSpellbook();

		if (currentChange.isValid())
		{
			currentChange.setMemberId(partyService.getLocalMember().getMemberId());
			currentChange.removeDefaults();
			sendUpdate(currentChange);
			currentChange = new TsgGroupUpdate();
		}
	}

	@Subscribe
	public void onStatChanged(final StatChanged event)
	{
		if (myPlayer == null || myPlayer.getStats() == null || !isSharing()) return;

		// Store the real level as virtual so the display setting can change later.
		final Skill s = event.getSkill();
		if (myPlayer.getSkillBoostedLevel(s) == event.getBoostedLevel()
			&& Experience.getLevelForXp(event.getXp()) == myPlayer.getSkillRealLevel(s))
		{
			return;
		}

		final int virtualLvl = Experience.getLevelForXp(event.getXp());
		myPlayer.setSkillsBoostedLevel(s, event.getBoostedLevel());
		myPlayer.setSkillsRealLevel(s, virtualLvl);
		currentChange.getS().add(new PartyStatChange(s.ordinal(), virtualLvl, event.getBoostedLevel()));

		if (myPlayer.getStats().getTotalLevel() != client.getTotalLevel())
		{
			myPlayer.getStats().setTotalLevel(client.getTotalLevel());
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.T, myPlayer.getStats().getTotalLevel()));
		}

		final int oldCombatLevel = myPlayer.getStats().getCombatLevel();
		myPlayer.getStats().recalculateCombatLevel();
		if (myPlayer.getStats().getCombatLevel() != oldCombatLevel)
		{
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.C, myPlayer.getStats().getCombatLevel()));
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged c)
	{
		if (myPlayer == null || !isSharing()) return;

		if (c.getContainerId() == InventoryID.INV)
		{
			final ItemContainer inventory = c.getItemContainer();
			myPlayer.setInventory(GameItem.convertItemsToGameItems(inventory.getItems(), itemManager));
			currentChange.setI(convertItemsToArray(inventory.getItems()));

			if (itemContainerHasRunePouch(inventory))
			{
				final List<Item> runesInPouch = getRunePouchContents(client);
				myPlayer.setRunesInPouch(GameItem.convertItemsToGameItems(runesInPouch.toArray(new Item[0]), itemManager));
				currentChange.setRp(convertRunePouchContentsToPackedInts(runesInPouch));
			}

			boolean hasQuiverInInventory = false;
			for (final Item item : inventory.getItems())
			{
				if (DIZANAS_QUIVER_IDS.contains(item.getId()))
				{
					hasQuiverInInventory = true;
					break;
				}
			}
			myPlayer.getQuiver().setInInventory(hasQuiverInInventory);
			if (hasQuiverInInventory && myPlayer.getQuiver().getQuiverAmmo() == null) updateQuiverAmmo();
		}
		else if (c.getContainerId() == InventoryID.WORN)
		{
			myPlayer.setEquipment(GameItem.convertItemsToGameItems(c.getItemContainer().getItems(), itemManager));
			currentChange.setE(convertItemsToArray(c.getItemContainer().getItems()));

			final Item cape = c.getItemContainer().getItem(EquipmentInventorySlot.CAPE.getSlotIdx());
			boolean isWearingQuiver = cape != null && DIZANAS_QUIVER_IDS.contains(cape.getId());
			myPlayer.getQuiver().setBeingWorn(isWearingQuiver);
			if (isWearingQuiver && myPlayer.getQuiver().getQuiverAmmo() == null) updateQuiverAmmo();
		}
	}

	@Subscribe
	public void onVarbitChanged(final VarbitChanged event)
	{
		if (myPlayer == null || myPlayer.getStats() == null || !isSharing()) return;

		final int specialPercent = client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
		if (specialPercent != myPlayer.getStats().getSpecialPercent())
		{
			myPlayer.getStats().setSpecialPercent(specialPercent);
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.S, specialPercent));
		}

		final int stamina = client.getVarbitValue(VarbitID.STAMINA_DURATION);
		if (stamina != myPlayer.getStamina())
		{
			myPlayer.setStamina(stamina);
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.ST, stamina));
		}

		final int poison = client.getVarpValue(VarPlayerID.POISON);
		if (poison != myPlayer.getPoison())
		{
			myPlayer.setPoison(poison);
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.P, poison));
		}

		final int disease = client.getVarpValue(VarPlayerID.DISEASE);
		if (disease != myPlayer.getDisease())
		{
			myPlayer.setDisease(disease);
			currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.D, disease));
		}

		if (ArrayUtils.contains(RUNEPOUCH_RUNE_VARBITS, event.getVarbitId()) || ArrayUtils.contains(RUNEPOUCH_AMOUNT_VARBITS, event.getVarbitId()))
		{
			final List<Item> runePouchContents = getRunePouchContents(client);
			myPlayer.setRunesInPouch(GameItem.convertItemsToGameItems(runePouchContents.toArray(new Item[0]), itemManager));
			currentChange.setRp(convertRunePouchContentsToPackedInts(runePouchContents));
		}

		if (event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT || event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)
		{
			updateQuiverAmmo();
		}

		if (event.getVarbitId() == VarbitID.SPELLBOOK) updateSpellbook();
	}

	@Subscribe
	public void onTsgGroupUpdate(final TsgGroupUpdate e)
	{
		if (!isSharing() || isLocalPlayer(e.getMemberId())) return;

		// Ignore members RuneLite hasn't announced yet; their UserSync follows.
		final net.runelite.client.party.PartyMember member = partyService.getMemberById(e.getMemberId());
		if (member == null) return;
		final PartyPlayer player = partyMembers.computeIfAbsent(e.getMemberId(), k -> new PartyPlayer(member));
		if (player.getStats() == null && e.hasStatChange()) player.setStats(new Stats());
		if (player.getPrayers() == null && (e.getAp() != null || e.getEp() != null || e.getUp() != null)) player.setPrayers(new Prayers());

		clientThread.invoke(() ->
		{
			e.process(player, itemManager);
			final boolean bannerChanged = e.hasBreakingBannerChange();
			SwingUtilities.invokeLater(() -> listener.memberUpdated(player, bannerChanged, false));
		});
	}

	@Subscribe
	public void onPartyMemberAvatar(final PartyMemberAvatar e)
	{
		final PartyPlayer player = partyMembers.get(e.getMemberId());
		if (isLocalPlayer(e.getMemberId()) || player == null) return;
		player.getMember().setAvatar(e.getImage());
		SwingUtilities.invokeLater(() -> listener.memberUpdated(player, true, false));
	}

	private void sendUpdate(final TsgGroupUpdate update)
	{
		partyService.send(update);
		if (showSelf.getAsBoolean()) applyToSelf(update);
	}

	public void refreshSelf()
	{
		clientThread.invokeLater(() ->
		{
			selfView = null;
			if (isSharing() && myPlayer != null && partyService.getLocalMember() != null) applyToSelf(partyPlayerAsBatchedChange());
		});
	}

	private void applyToSelf(final TsgGroupUpdate update)
	{
		final net.runelite.client.party.PartyMember local = partyService.getLocalMember();
		if (local == null) return;
		if (selfView == null || selfView.getMember().getMemberId() != local.getMemberId()) selfView = new PartyPlayer(local);
		final PartyPlayer self = selfView;
		if (self.getStats() == null && update.hasStatChange()) self.setStats(new Stats());
		if (self.getPrayers() == null && (update.getAp() != null || update.getEp() != null || update.getUp() != null)) self.setPrayers(new Prayers());
		clientThread.invoke(() ->
		{
			update.process(self, itemManager);
			final boolean bannerChanged = update.hasBreakingBannerChange();
			SwingUtilities.invokeLater(() -> listener.memberUpdated(self, bannerChanged, true));
		});
	}

	private GameItem getQuiverAmmo()
	{
		final int quiverAmmoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		final int quiverAmmoCount = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		if (quiverAmmoId == -1 || quiverAmmoCount == 0) return null;
		return new GameItem(quiverAmmoId, quiverAmmoCount, itemManager);
	}

	private void updateQuiverAmmo()
	{
		final GameItem quiverAmmo = getQuiverAmmo();
		if (Objects.equals(quiverAmmo, myPlayer.getQuiver().getQuiverAmmo())) return;
		myPlayer.getQuiver().setQuiverAmmo(quiverAmmo);
		currentChange.setQ(quiverAmmo != null ? new int[]{quiverAmmo.getId(), quiverAmmo.getQty()} : new int[0]);
	}

	private void updateSpellbook()
	{
		final int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		myPlayer.setSpellbook(spellbook);
		currentChange.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.SP, spellbook));
	}

	private static boolean itemContainerHasRunePouch(ItemContainer inventory)
	{
		for (final int id : RUNEPOUCH_ITEM_IDS)
		{
			if (inventory.contains(id)) return true;
		}
		return false;
	}

	private static int[] convertRunePouchContentsToPackedInts(final List<Item> runesInPouch)
	{
		return runesInPouch.stream().mapToInt(TsgGroupUpdate::packRune).toArray();
	}

	public static List<Item> getRunePouchContents(Client client)
	{
		final EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		final List<Item> items = new ArrayList<>();
		for (int i = 0; i < RUNEPOUCH_AMOUNT_VARBITS.length; i++)
		{
			final int amount = client.getVarbitValue(RUNEPOUCH_AMOUNT_VARBITS[i]);
			if (amount <= 0) continue;
			final int runeId = client.getVarbitValue(RUNEPOUCH_RUNE_VARBITS[i]);
			if (runeId == 0) continue;
			items.add(new Item(runepouchEnum.getIntValue(runeId), amount));
		}
		return items;
	}

	private static int[] convertItemsToArray(Item[] items)
	{
		final int[] out = new int[items.length * 2];
		for (int i = 0; i < items.length; i++)
		{
			out[i * 2] = items[i] == null ? -1 : items[i].getId();
			out[i * 2 + 1] = items[i] == null ? 0 : items[i].getQuantity();
		}
		return out;
	}

	private static int[] convertGameItemsToArray(GameItem[] items)
	{
		final int[] out = new int[items.length * 2];
		for (int i = 0; i < items.length; i++)
		{
			out[i * 2] = items[i] == null ? -1 : items[i].getId();
			out[i * 2 + 1] = items[i] == null ? 0 : items[i].getQty();
		}
		return out;
	}

	private void setPrayers(TsgGroupUpdate c)
	{
		final Collection<Prayer> available = new ArrayList<>();
		final Collection<Prayer> enabled = new ArrayList<>();
		final Collection<Prayer> unlocked = new ArrayList<>();
		for (final PrayerSprites p : PrayerSprites.values())
		{
			final PrayerData data = myPlayer.getPrayers().getPrayerData().get(p.getPrayer());
			if (data == null) continue;
			if (data.isAvailable()) available.add(p.getPrayer());
			if (data.isEnabled()) enabled.add(p.getPrayer());
			if (data.isUnlocked()) unlocked.add(p.getPrayer());
		}
		c.setAp(TsgGroupUpdate.pack(available));
		c.setEp(TsgGroupUpdate.pack(enabled));
		c.setUp(TsgGroupUpdate.pack(unlocked));
	}

	private TsgGroupUpdate partyPlayerAsBatchedChange()
	{
		final TsgGroupUpdate c = new TsgGroupUpdate();
		if (myPlayer == null) return c;

		c.setI(convertGameItemsToArray(myPlayer.getInventory()));
		c.setE(convertGameItemsToArray(myPlayer.getEquipment()));

		if (myPlayer.getStats() != null)
		{
			for (final Skill s : Skill.values())
			{
				c.getS().add(myPlayer.getStats().createPartyStatChangeForSkill(s));
			}
			c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.S, myPlayer.getStats().getSpecialPercent()));
			c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.R, myPlayer.getStats().getRunEnergy()));
			c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.C, myPlayer.getStats().getCombatLevel()));
			c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.T, myPlayer.getStats().getTotalLevel()));
		}

		c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.ST, myPlayer.getStamina()));
		c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.P, myPlayer.getPoison()));
		c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.D, myPlayer.getDisease()));
		c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.W, myPlayer.getWorld()));
		c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.SP, myPlayer.getSpellbook()));

		if (myPlayer.getPrayers() != null) setPrayers(c);

		c.getM().add(new PartyMiscChange(PartyMiscChange.PartyMisc.U, myPlayer.getUsername()));

		if (client.isClientThread()) c.setRp(convertRunePouchContentsToPackedInts(getRunePouchContents(client)));

		final GameItem quiverAmmo = myPlayer.getQuiver().getQuiverAmmo();
		c.setQ(quiverAmmo != null ? new int[]{quiverAmmo.getId(), quiverAmmo.getQty()} : new int[0]);

		c.setMemberId(partyService.getLocalMember().getMemberId());
		c.removeDefaults();
		return c;
	}

	private static int messageFreq(int partySize)
	{
		// Sends a lot of data: every 2 ticks, plus one per member past 6.
		return Math.max(2, partySize - 6);
	}
}
