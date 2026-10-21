/*
 * Adapted from Party Panel by TheStonedTurtle (https://github.com/TheStonedTurtle/party-panel),
 * BSD 2-Clause License. See THIRD_PARTY_NOTICES.md.
 */
package com.tsghub;

import static com.tsghub.PartyMiscChange.PartyMisc.*;

import com.google.common.collect.ImmutableSet;
import com.tsghub.PartyMiscChange.PartyMisc;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
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
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.UserSync;
import org.apache.commons.lang3.ArrayUtils;

final class GroupTracker
{
	private static final int[] RUNEPOUCH_AMOUNT_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};
	private static final int[] RUNEPOUCH_RUNE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};
	static final int[] RUNEPOUCH_ITEM_IDS = {
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER,
	};
	private static final long IDLE_MINUTES = 30;
	static final int KEEPALIVE_SECONDS = 30;
	private static final int PRAYER_REFRESH_TICKS = 100;
	private static final Set<Integer> PRAYER_VARBITS = prayerVarbits();

	// Member callbacks run on the Swing thread.
	interface Listener
	{
		void memberUpdated(PartyPlayer player, boolean self);

		void memberRemoved(PartyPlayer player);

		void membersCleared();

		void areasChanged();

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
	private final java.util.function.Supplier<String> area;
	private final Listener listener;

	private final Map<Long, PartyPlayer> partyMembers = new ConcurrentHashMap<>();
	private volatile PartyPlayer myPlayer;
	private boolean prayersDirty = true;
	private volatile PartyPlayer selfView;
	private volatile Instant lastLogout = Instant.now();
	private TsgGroupUpdate currentChange = new TsgGroupUpdate();

	GroupTracker(Client client, ClientThread clientThread, PartyService partyService, WSClient wsClient,
		ItemManager itemManager, Predicate<String> isGroupParty, java.util.function.BooleanSupplier showSelf,
		java.util.function.Supplier<String> area, Listener listener)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.itemManager = itemManager;
		this.isGroupParty = isGroupParty;
		this.showSelf = showSelf;
		this.area = area;
		this.listener = listener;
	}

	void start()
	{
		wsClient.registerMessage(TsgGroupUpdate.class);
		lastLogout = Instant.now();
		if (isSharing()) announce();
	}

	void stop()
	{
		if (isSharing() && myPlayer != null && partyService.getLocalMember() != null)
		{
			TsgGroupUpdate clean = partyPlayerAsBatchedChange();
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

	boolean isSharing()
	{
		return partyService.isInParty() && isGroupParty.test(partyService.getPartyPassphrase());
	}

	void announce()
	{
		clientThread.invokeLater(() ->
		{
			if (!isSharing() || partyService.getLocalMember() == null) return;
			myPlayer = newSelf();
			partyService.send(new UserSync());
			sendUpdate(partyPlayerAsBatchedChange());
		});
	}

	void keepAlive()
	{
		PartyPlayer me = myPlayer;
		if (!isSharing() || me == null) return;
		TsgGroupUpdate c = new TsgGroupUpdate();
		c.getM().add(new PartyMiscChange(W, me.getWorld()));
		partyService.send(c);
	}

	List<String> areas()
	{
		List<String> areas = new ArrayList<>();
		PartyPlayer me = myPlayer;
		if (me != null) areas.add(me.getArea());
		for (PartyPlayer p : partyMembers.values()) areas.add(p.getArea());
		return areas;
	}

	boolean idleTooLong()
	{
		return client.getGameState() == GameState.LOGIN_SCREEN && lastLogout != null
			&& lastLogout.isBefore(Instant.now().minus(IDLE_MINUTES, ChronoUnit.MINUTES));
	}

	private PartyPlayer newSelf()
	{
		return new PartyPlayer(partyService.getLocalMember(), client, itemManager);
	}

	private boolean isLocalPlayer(long id)
	{
		return partyService.getLocalMember() != null && partyService.getLocalMember().getMemberId() == id;
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		partyMembers.clear();
		clientThread.invoke(() ->
		{
			myPlayer = null;
			selfView = null;
			currentChange = new TsgGroupUpdate();
		});
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
			if (partyService.getLocalMember() == null) return;
			myPlayer = newSelf();
			sendUpdate(partyPlayerAsBatchedChange());
			return;
		}

		if (c.getGameState() == GameState.LOGGED_IN)
		{
			if (myPlayer.getWorld() == client.getWorld()) return;
			trackMisc(W, client.getWorld(), myPlayer.getWorld(), myPlayer::setWorld);
		}

		if (c.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (myPlayer.getWorld() == 0) return;
			trackMisc(W, 0, myPlayer.getWorld(), myPlayer::setWorld);
			sendUpdate(currentChange);
			currentChange = new TsgGroupUpdate();
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		// The Party plugin may be off, so request and send state ourselves.
		if (isLocalPlayer(event.getMemberId()) && isSharing()) announce();
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		PartyPlayer removed = partyMembers.remove(event.getMemberId());
		if (removed != null) SwingUtilities.invokeLater(() -> listener.memberRemoved(removed));
	}

	@Subscribe
	public void onUserSync(UserSync event)
	{
		if (!isSharing()) return;
		clientThread.invoke(() ->
		{
			if (partyService.getLocalMember() == null) return;
			if (myPlayer == null) myPlayer = newSelf();
			TsgGroupUpdate c = partyPlayerAsBatchedChange();
			if (c.isValid()) sendUpdate(c);
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!isSharing() || client.getLocalPlayer() == null || partyService.getLocalMember() == null) return;

		// Only process changes every few ticks to reduce load.
		if (client.getTickCount() % messageFreq(partyService.getMembers().size()) != 0) return;

		if (myPlayer == null || !Objects.equals(client.getLocalPlayer().getName(), myPlayer.getUsername()))
		{
			myPlayer = newSelf();
			sendUpdate(partyPlayerAsBatchedChange());
			return;
		}

		if (myPlayer.getStats() == null)
		{
			myPlayer.updatePlayerInfo(client, itemManager);
			for (Skill s : Skill.values())
			{
				currentChange.getS().add(myPlayer.getStats().createPartyStatChangeForSkill(s));
			}
		}
		else
		{
			trackMisc(R, client.getEnergy() / 100, myPlayer.getStats().getRunEnergy(), myPlayer.getStats()::setRunEnergy);
		}

		boolean prayersChanged = false;
		if (myPlayer.getPrayers() == null)
		{
			myPlayer.setPrayers(new Prayers(client));
			prayersChanged = true;
		}
		else if (prayersDirty || client.getTickCount() % PRAYER_REFRESH_TICKS == 0)
		{
			for (PrayerSprites p : PrayerSprites.values())
			{
				prayersChanged = myPlayer.getPrayers().updatePrayerState(p, client) || prayersChanged;
			}
		}
		prayersDirty = false;
		// Unlisted prayers count as off, so send all three sets together.
		if (prayersChanged) setPrayers(currentChange);

		if (myPlayer.getSpellbook() == -1) updateSpellbook();

		String currentArea = area.get();
		if (!currentArea.equals(myPlayer.getArea()))
		{
			myPlayer.setArea(currentArea);
			currentChange.getM().add(new PartyMiscChange(A, currentArea));
			SwingUtilities.invokeLater(listener::areasChanged);
		}

		if (currentChange.isValid())
		{
			currentChange.setMemberId(partyService.getLocalMember().getMemberId());
			currentChange.removeDefaults();
			sendUpdate(currentChange);
			currentChange = new TsgGroupUpdate();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.PRAYER) prayersDirty = true;
		if (myPlayer == null || myPlayer.getStats() == null || !isSharing()) return;

		// Store the real level as virtual so the display setting can change later.
		Skill s = event.getSkill();
		Stats stats = myPlayer.getStats();
		int virtualLvl = Experience.getLevelForXp(event.getXp());
		if (myPlayer.getSkillBoostedLevel(s) == event.getBoostedLevel() && virtualLvl == myPlayer.getSkillRealLevel(s, false)) return;
		stats.getBoostedLevels().put(s, event.getBoostedLevel());
		stats.getBaseLevels().put(s, virtualLvl);
		currentChange.getS().add(new PartyStatChange(s.ordinal(), virtualLvl, event.getBoostedLevel()));
		trackMisc(T, client.getTotalLevel(), stats.getTotalLevel(), stats::setTotalLevel);
		int oldCombatLevel = stats.getCombatLevel();
		stats.recalculateCombatLevel();
		if (stats.getCombatLevel() != oldCombatLevel) currentChange.getM().add(new PartyMiscChange(C, stats.getCombatLevel()));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged c)
	{
		if (myPlayer == null || !isSharing()) return;

		if (c.getContainerId() == InventoryID.INV)
		{
			myPlayer.setInventory(GameItem.of(c.getItemContainer().getItems(), itemManager));
			currentChange.setI(GameItem.pack(myPlayer.getInventory()));
			if (Arrays.stream(RUNEPOUCH_ITEM_IDS).anyMatch(c.getItemContainer()::contains)) updateRunePouch();
			myPlayer.getQuiver().readInventory(myPlayer.getInventory());
		}
		else if (c.getContainerId() == InventoryID.WORN)
		{
			myPlayer.setEquipment(GameItem.of(c.getItemContainer().getItems(), itemManager));
			currentChange.setE(GameItem.pack(myPlayer.getEquipment()));
			myPlayer.getQuiver().readEquipment(myPlayer.getEquipment());
		}
		else return;
		if (myPlayer.getQuiver().isSlotVisible() && myPlayer.getQuiver().getQuiverAmmo() == null) updateQuiverAmmo();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (PRAYER_VARBITS.contains(event.getVarbitId())) prayersDirty = true;
		if ((event.getVarbitId() == VarbitID.PRAYER_DEADEYE_UNLOCKED || event.getVarbitId() == VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) && myPlayer != null && myPlayer.getPrayers() != null)
		{
			myPlayer.getPrayers().resetPrayerIds();
		}
		if (myPlayer == null || myPlayer.getStats() == null || !isSharing()) return;

		trackMisc(S, client.getVarpValue(VarPlayerID.SA_ENERGY) / 10, myPlayer.getStats().getSpecialPercent(), myPlayer.getStats()::setSpecialPercent);
		trackMisc(ST, client.getVarbitValue(VarbitID.STAMINA_DURATION), myPlayer.getStamina(), myPlayer::setStamina);
		trackMisc(P, client.getVarpValue(VarPlayerID.POISON), myPlayer.getPoison(), myPlayer::setPoison);
		trackMisc(D, client.getVarpValue(VarPlayerID.DISEASE), myPlayer.getDisease(), myPlayer::setDisease);
		if (ArrayUtils.contains(RUNEPOUCH_RUNE_VARBITS, event.getVarbitId()) || ArrayUtils.contains(RUNEPOUCH_AMOUNT_VARBITS, event.getVarbitId())) updateRunePouch();

		if (event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT || event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)
		{
			updateQuiverAmmo();
		}

		if (event.getVarbitId() == VarbitID.SPELLBOOK) updateSpellbook();
	}

	@Subscribe
	public void onTsgGroupUpdate(TsgGroupUpdate e)
	{
		if (!isSharing() || isLocalPlayer(e.getMemberId())) return;

		// Ignore members RuneLite hasn't announced yet; their UserSync follows.
		net.runelite.client.party.PartyMember member = partyService.getMemberById(e.getMemberId());
		if (member == null) return;
		PartyPlayer player = partyMembers.computeIfAbsent(e.getMemberId(), k -> new PartyPlayer(member));

		clientThread.invoke(() ->
		{
			e.process(player, itemManager);
			boolean areaChanged = e.hasAreaChange();
			SwingUtilities.invokeLater(() ->
			{
				if (partyMembers.get(e.getMemberId()) != player) return;
				listener.memberUpdated(player, false);
				if (areaChanged) listener.areasChanged();
			});
		});
	}

	private void sendUpdate(TsgGroupUpdate update)
	{
		partyService.send(update);
		if (showSelf.getAsBoolean()) applyToSelf(update);
	}

	void refreshSelf()
	{
		clientThread.invokeLater(() ->
		{
			selfView = null;
			if (isSharing() && myPlayer != null && partyService.getLocalMember() != null) applyToSelf(partyPlayerAsBatchedChange());
		});
	}

	private void applyToSelf(TsgGroupUpdate update)
	{
		net.runelite.client.party.PartyMember local = partyService.getLocalMember();
		if (local == null) return;
		if (selfView == null || selfView.getMember().getMemberId() != local.getMemberId()) selfView = new PartyPlayer(local);
		PartyPlayer self = selfView;
		clientThread.invoke(() ->
		{
			update.process(self, itemManager);
			SwingUtilities.invokeLater(() -> { if (selfView == self && showSelf.getAsBoolean()) listener.memberUpdated(self, true); });
		});
	}

	private void trackMisc(PartyMisc type, int value, int current, IntConsumer store)
	{
		if (value == current) return;
		store.accept(value);
		currentChange.getM().add(new PartyMiscChange(type, value));
	}

	private void updateQuiverAmmo()
	{
		GameItem quiverAmmo = Quiver.ammo(client, itemManager);
		if (Objects.equals(quiverAmmo, myPlayer.getQuiver().getQuiverAmmo())) return;
		myPlayer.getQuiver().setQuiverAmmo(quiverAmmo);
		currentChange.setQ(Quiver.pack(quiverAmmo));
	}

	private void updateSpellbook()
	{
		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		myPlayer.setSpellbook(spellbook);
		currentChange.getM().add(new PartyMiscChange(SP, spellbook));
	}

	private void updateRunePouch()
	{
		myPlayer.setRunesInPouch(runePouch(client, itemManager));
		currentChange.setRp(packRunes(myPlayer.getRunesInPouch()));
	}

	private static int[] packRunes(GameItem[] runes)
	{
		return Arrays.stream(runes).mapToInt(r -> TsgGroupUpdate.packRune(r.getId(), r.getQty())).toArray();
	}

	static GameItem[] runePouch(Client client, ItemManager itemManager)
	{
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		List<GameItem> items = new ArrayList<>();
		for (int i = 0; i < RUNEPOUCH_AMOUNT_VARBITS.length; i++)
		{
			int amount = client.getVarbitValue(RUNEPOUCH_AMOUNT_VARBITS[i]);
			int runeId = client.getVarbitValue(RUNEPOUCH_RUNE_VARBITS[i]);
			if (amount > 0 && runeId != 0) items.add(new GameItem(runepouchEnum.getIntValue(runeId), amount, itemManager));
		}
		return items.toArray(new GameItem[0]);
	}

	private void setPrayers(TsgGroupUpdate c)
	{
		Collection<Prayer> available = new ArrayList<>();
		Collection<Prayer> enabled = new ArrayList<>();
		Collection<Prayer> unlocked = new ArrayList<>();
		for (PrayerSprites p : PrayerSprites.values())
		{
			PrayerData data = myPlayer.getPrayers().getPrayerData().get(p.getPrayer());
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
		TsgGroupUpdate c = new TsgGroupUpdate();
		if (myPlayer == null) return c;

		c.setI(GameItem.pack(myPlayer.getInventory()));
		c.setE(GameItem.pack(myPlayer.getEquipment()));
		Stats stats = myPlayer.getStats();
		if (stats != null)
		{
			for (Skill s : Skill.values()) c.getS().add(stats.createPartyStatChangeForSkill(s));
			c.getM().add(new PartyMiscChange(S, stats.getSpecialPercent()));
			c.getM().add(new PartyMiscChange(R, stats.getRunEnergy()));
			c.getM().add(new PartyMiscChange(C, stats.getCombatLevel()));
			c.getM().add(new PartyMiscChange(T, stats.getTotalLevel()));
		}
		c.getM().add(new PartyMiscChange(ST, myPlayer.getStamina()));
		c.getM().add(new PartyMiscChange(P, myPlayer.getPoison()));
		c.getM().add(new PartyMiscChange(D, myPlayer.getDisease()));
		c.getM().add(new PartyMiscChange(W, myPlayer.getWorld()));
		c.getM().add(new PartyMiscChange(SP, myPlayer.getSpellbook()));
		c.getM().add(new PartyMiscChange(A, myPlayer.getArea()));
		if (myPlayer.getPrayers() != null) setPrayers(c);
		c.getM().add(new PartyMiscChange(U, myPlayer.getUsername()));
		if (client.isClientThread()) c.setRp(packRunes(runePouch(client, itemManager)));
		c.setQ(Quiver.pack(myPlayer.getQuiver().getQuiverAmmo()));

		c.setMemberId(partyService.getLocalMember().getMemberId());
		c.removeDefaults();
		return c;
	}

	private static Set<Integer> prayerVarbits()
	{
		ImmutableSet.Builder<Integer> varbits = ImmutableSet.builder();
		for (Prayer p : Prayer.values()) varbits.add(p.getVarbit());
		varbits.add(VarbitID.PRAYER_DEADEYE_UNLOCKED, VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED);
		return varbits.build();
	}

	private static int messageFreq(int partySize)
	{
		// Sends a lot of data: every 2 ticks, plus one per member past 6.
		return Math.max(2, partySize - 6);
	}
}
