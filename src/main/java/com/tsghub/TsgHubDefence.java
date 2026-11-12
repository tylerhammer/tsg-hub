package com.tsghub;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubDefenceTarget.Boss;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.specialcounter.SpecialCounterPlugin;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@Slf4j
final class TsgHubDefence
{
	private static final int PARTY_DEDUPE_TICKS = 3;
	private static final Map<Integer, String> BY_ID = new HashMap<>();

	static
	{
		BY_ID.put(NpcID.TFA_OCELLUS_INTRO_MET, "ocellus p1");
		BY_ID.put(NpcID.TFA_OCELLUS_P1, "ocellus p1");
		BY_ID.put(NpcID.TFA_OCELLUS_P2_SWORD_HITBOX, "ocellus p2");
		BY_ID.put(NpcID.TFA_OCELLUS_P3, "ocellus p3");
	}

	private final Client client;
	private final ClientThread clientThread;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final PluginManager pluginManager;
	private final InfoBoxManager infoBoxManager;
	private final OverlayManager overlayManager;
	private final TsgHubConfig config;
	private final Map<String, Boss> bosses;
	private final Consumer<String> chat;
	private final Map<NPC, TsgHubDefenceTarget> targets = new IdentityHashMap<>();
	private final Map<Boss, TsgHubDefenceTarget> retained = new HashMap<>();
	private final Map<String, Integer> partySeen = new HashMap<>();
	private final TsgHubDefenceOverlay overlay;
	private final TsgHubDefencePanel panel;
	private final InfoBox infoBox;
	private NPC focus;
	private SpecialWeapon held;

	private int specPercent = -1;
	private int hpXp = -1;
	private int hpCycle = -1;
	private SpecialWeapon weapon;
	private NPC target;
	private int hitTick = -1;
	private boolean hpOnSpec;
	private final List<Integer> hitsplats = new ArrayList<>();

	TsgHubDefence(Plugin plugin, Client client, ClientThread clientThread, PartyService partyService, WSClient wsClient,
		PluginManager pluginManager, InfoBoxManager infoBoxManager, OverlayManager overlayManager, SkillIconManager skillIcons,
		TsgHubConfig config, Gson gson, Consumer<String> chat)
	{
		this.chat = chat;
		this.client = client;
		this.clientThread = clientThread;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.pluginManager = pluginManager;
		this.infoBoxManager = infoBoxManager;
		this.overlayManager = overlayManager;
		this.config = config;
		this.bosses = load(gson);
		this.overlay = new TsgHubDefenceOverlay(this, config);
		this.panel = new TsgHubDefencePanel(plugin, this, config);
		this.infoBox = new InfoBox(skillIcons.getSkillImage(Skill.DEFENCE), plugin)
		{
			@Override
			public String getText()
			{
				TsgHubDefenceTarget t = focused();
				return t == null ? "" : String.valueOf(t.def);
			}

			@Override
			public Color getTextColor()
			{
				TsgHubDefenceTarget t = focused();
				return t != null && t.atFloor() ? TsgHubTheme.CAPPED : Color.WHITE;
			}

			@Override
			public String getTooltip()
			{
				TsgHubDefenceTarget t = focused();
				return t == null ? null : t.boss.name + "</br>Defence: " + t.def + " / " + t.startDef + "</br>Magic: " + t.magic + "</br>Magic def bonus: " + t.magicDef;
			}

			@Override
			public boolean render()
			{
				return config.defenceInfoBox() && focused() != null;
			}
		};
	}

	static Map<String, Boss> load(Gson gson)
	{
		Map<String, Boss> out = new HashMap<>();
		InputStream in = TsgHubDefence.class.getResourceAsStream("defence.json");
		if (in == null) return out;
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			for (Map.Entry<String, JsonElement> e : gson.fromJson(reader, JsonObject.class).entrySet())
			{
				JsonArray v = e.getValue().getAsJsonArray();
				out.put(e.getKey().toLowerCase(Locale.ROOT), new Boss(e.getKey(), v.get(0).getAsInt(), v.get(1).getAsInt(),
					v.get(2).getAsInt(), v.get(3).getAsInt(), v.get(4).getAsInt(), v.get(5).getAsInt(), v.size() > 6 ? v.get(6).getAsString() : ""));
			}
		}
		catch (Exception e) { log.debug("TSG Hub defence table unreadable", e); }
		return out;
	}

	static Boss boss(Map<String, Boss> bosses, NPC npc)
	{
		String byId = BY_ID.get(npc.getId());
		return byId != null ? bosses.get(byId) : boss(bosses, npc.getName());
	}

	static Boss boss(Map<String, Boss> bosses, String npcName)
	{
		if (npcName == null) return null;
		String name = Text.removeTags(npcName).trim().toLowerCase(Locale.ROOT);
		Boss exact = bosses.get(name);
		if (exact != null) return exact;
		int space = name.lastIndexOf(' ');
		while (space > 0)
		{
			Boss prefix = bosses.get(name.substring(0, space));
			if (prefix != null) return prefix;
			space = name.lastIndexOf(' ', space - 1);
		}
		return null;
	}

	void start()
	{
		wsClient.registerMessage(SpecialCounterUpdate.class);
		overlayManager.add(overlay);
		overlayManager.add(panel);
		infoBoxManager.addInfoBox(infoBox);
	}

	void stop()
	{
		if (!specialCounterActive()) wsClient.unregisterMessage(SpecialCounterUpdate.class);
		overlayManager.remove(overlay);
		overlayManager.remove(panel);
		infoBoxManager.removeInfoBox(infoBox);
		clear();
		specPercent = -1;
	}

	Map<NPC, TsgHubDefenceTarget> targets()
	{
		return Collections.unmodifiableMap(targets);
	}

	TsgHubDefenceTarget focused()
	{
		return focus == null ? null : targets.get(focus);
	}

	private void clear()
	{
		targets.clear();
		retained.clear();
		partySeen.clear();
		focus = null;
		held = null;
		clearPending();
	}

	private void clearPending()
	{
		weapon = null;
		target = null;
		hitTick = -1;
		hpOnSpec = false;
		hitsplats.clear();
	}

	private void apply(NPC npc, SpecialWeapon used, int hit, String who)
	{
		Boss boss = boss(bosses, npc);
		if (boss == null) return;
		TsgHubDefenceTarget t = targets.get(npc);
		if (t == null || t.boss != boss) t = retained.remove(boss);
		if (t != null) targets.put(npc, t);
		else
		{
			boolean inCox = client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
			t = new TsgHubDefenceTarget(boss, inCox, client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED),
				client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) == 1);
			targets.put(npc, t);
		}
		boolean missDrain = hit == 0 && (used == SpecialWeapon.DRAGON_WARHAMMER || used == SpecialWeapon.ELDER_MAUL)
			&& used.computeDrainPercent(0, npc) > 0;
		int[] before = {t.def, t.magic, t.magicDef};
		t.drain(used, hit, missDrain);
		focus = npc;
		chat.accept(message(who, used, hit, boss.name, before, new int[]{t.def, t.magic, t.magicDef}) + capNote(t, used));
	}

	private static final String[] STATS = {"Def", "Magic", "Magic def"};

	static String message(String who, SpecialWeapon used, int hit, String boss, int[] before, int[] after)
	{
		StringBuilder out = new StringBuilder(who).append(": ").append(shortName(used)).append(hit > 0 ? " hit " : " missed ").append(boss);
		boolean changed = false;
		for (int i = 0; i < STATS.length; i++)
		{
			if (before[i] == after[i]) continue;
			out.append(", ").append(STATS[i]).append(' ').append(before[i]).append(" to ").append(after[i]);
			changed = true;
		}
		if (!changed) out.append(", ").append(STATS[stat(used)]).append(' ').append(after[stat(used)]);
		return out.toString();
	}

	static String capNote(TsgHubDefenceTarget t, SpecialWeapon used)
	{
		if (t.floor <= 0 || stat(used) != 0) return "";
		if (t.atFloor()) return ", Def capped";
		int left = t.toCap(used);
		return left <= 0 ? "" : ", " + left + " more " + unit(used, left) + " to cap";
	}

	static String capLabel(TsgHubDefenceTarget t, SpecialWeapon held)
	{
		if (held == null || t.floor <= 0 || t.atFloor() || stat(held) != 0) return null;
		int left = t.toCap(held);
		return left <= 0 ? null : shortName(held) + " " + left + (TsgHubDefenceTarget.damageBased(held) ? " dmg" : held == SpecialWeapon.TONALZTICS_OF_RALOS ? " glaive" + (left == 1 ? "" : "s") : "") + " to cap";
	}

	private static String unit(SpecialWeapon used, int left)
	{
		if (TsgHubDefenceTarget.damageBased(used)) return shortName(used) + " damage";
		if (used == SpecialWeapon.TONALZTICS_OF_RALOS) return left == 1 ? "Ralos glaive" : "Ralos glaives";
		return shortName(used);
	}

	SpecialWeapon held()
	{
		return held;
	}

	private static int stat(SpecialWeapon used)
	{
		return used == SpecialWeapon.SEERCULL ? 1 : used == SpecialWeapon.EYE_OF_AYAK ? 2 : 0;
	}

	static String shortName(SpecialWeapon used)
	{
		switch (used)
		{
			case DRAGON_WARHAMMER: return "DWH";
			case ELDER_MAUL: return "Maul";
			case BANDOS_GODSWORD: return "BGS";
			case TONALZTICS_OF_RALOS: return "Ralos";
			case BARRELCHEST_ANCHOR: return "Anchor";
			case DORGESHUUN_CROSSBOW: return "Dorg xbow";
			case ACCURSED_SCEPTRE: return "Sceptre";
			case EYE_OF_AYAK: return "Ayak";
			default: return used.getName();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			clear();
			specPercent = -1;
			hpXp = -1;
		}
		WorldView view = client.getTopLevelWorldView();
		if (state == GameState.LOGGED_IN && view != null && !view.isInstance()) retained.clear();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		restore(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		TsgHubDefenceTarget t = targets.get(npc);
		if (t != null && t.boss != boss(bosses, npc)) targets.remove(npc);
		restore(npc);
	}

	private void restore(NPC npc)
	{
		if (retained.isEmpty() || targets.containsKey(npc)) return;
		Boss boss = boss(bosses, npc);
		TsgHubDefenceTarget t = boss == null ? null : retained.remove(boss);
		if (t != null) targets.put(npc, t);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		TsgHubDefenceTarget t = targets.remove(npc);
		if (t != null && t.boss.has('P')) retained.put(t.boss, t);
		if (npc == focus) focus = null;
		if (npc == target) clearPending();
	}

	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event)
	{
		if (event.getSkill() == Skill.HITPOINTS) hpCycle = client.getGameCycle();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.HITPOINTS) return;
		if (event.getXp() > hpXp) hpCycle = client.getGameCycle();
		hpXp = event.getXp();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() != VarPlayerID.SA_ENERGY) return;
		int current = event.getValue();
		boolean spent = specPercent != -1 && current < specPercent;
		specPercent = current;
		if (!spent) return;
		int tick = client.getTickCount();
		clientThread.invokeLater(() -> arm(tick));
	}

	private void arm(int tick)
	{
		SpecialWeapon used = wielded();
		if (used == null) return;
		Player local = client.getLocalPlayer();
		Actor interacting = local == null ? null : local.getInteracting();
		if (!(interacting instanceof NPC) || boss(bosses, (NPC) interacting) == null) return;
		weapon = used;
		target = (NPC) interacting;
		hpOnSpec = hpCycle == client.getGameCycle();
		hitTick = tick + hitDelay(used, local, target);
		hitsplats.clear();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (weapon == null || event.getActor() != target) return;
		Hitsplat splat = event.getHitsplat();
		if (splat.isMine() && client.getTickCount() == hitTick) hitsplats.add(splat.getAmount());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		held = wielded();
		if (!partySeen.isEmpty()) partySeen.values().removeIf(seen -> tick - seen > PARTY_DEDUPE_TICKS);
		if (weapon == null) return;
		if (weapon == SpecialWeapon.ELDER_MAUL)
		{
			record(weapon, hpOnSpec ? 1 : 0, target);
			clearPending();
			return;
		}
		if ((tick == hitTick || tick == hitTick + 1) && !hitsplats.isEmpty())
		{
			int hit = resolve(weapon, hitsplats);
			if (hit >= 0)
			{
				record(weapon, hit, target);
				clearPending();
				return;
			}
		}
		if (tick > hitTick) clearPending();
	}

	static int resolve(SpecialWeapon used, List<Integer> splats)
	{
		if (used == SpecialWeapon.TONALZTICS_OF_RALOS)
		{
			if (splats.size() < 2) return -1;
			return (splats.get(0) > 0 ? 1 : 0) + (splats.get(1) > 0 ? 1 : 0);
		}
		return splats.get(used.getHitDelay(1) > 1 ? 0 : splats.size() - 1);
	}

	private void record(SpecialWeapon used, int hit, NPC npc)
	{
		Player local = client.getLocalPlayer();
		apply(npc, used, hit, local == null || local.getName() == null ? "You" : local.getName());
		if (local != null && partyService.isInParty() && !specialCounterActive())
		{
			partyService.send(new SpecialCounterUpdate(npc.getIndex(), used, hit, client.getWorld(), local.getId()));
		}
	}

	@Subscribe
	public void onSpecialCounterUpdate(SpecialCounterUpdate event)
	{
		if (!partyService.isInParty() || !TsgHubDefenceTarget.drains(event.getWeapon())) return;
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == event.getMemberId()) return;
		clientThread.invoke(() ->
		{
			if (event.getWorld() != client.getWorld()) return;
			String key = event.getMemberId() + ":" + event.getNpcIndex() + ":" + event.getWeapon();
			Integer seen = partySeen.get(key);
			int tick = client.getTickCount();
			if (seen != null && tick - seen <= PARTY_DEDUPE_TICKS) return;
			partySeen.put(key, tick);
			WorldView view = client.getTopLevelWorldView();
			NPC npc = view == null ? null : view.npcs().byIndex(event.getNpcIndex());
			PartyMember sender = partyService.getMemberById(event.getMemberId());
			if (npc != null && sender != null) apply(npc, event.getWeapon(), event.getHit(), sender.getDisplayName());
		});
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() instanceof SpecialCounterPlugin && !event.isLoaded()) wsClient.registerMessage(SpecialCounterUpdate.class);
	}

	private SpecialWeapon wielded()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item item = worn == null ? null : worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (item == null) return null;
		for (SpecialWeapon candidate : SpecialWeapon.values())
		{
			if (!TsgHubDefenceTarget.drains(candidate)) continue;
			for (int id : candidate.getItemID()) if (id == item.getId()) return candidate;
		}
		return null;
	}

	private static int hitDelay(SpecialWeapon used, Player local, NPC npc)
	{
		WorldPoint at = local.getWorldLocation();
		WorldArea area = npc.getWorldArea();
		return at == null || area == null ? 1 : used.getHitDelay(area.distanceTo(at));
	}

	private boolean specialCounterActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof SpecialCounterPlugin && pluginManager.isPluginActive(plugin)) return true;
		}
		return false;
	}
}
