package com.tsghub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// IDs are as-dropped item IDs so loot and collection log unlocks match.
final class TsgHubItemSets
{
	static final class ItemSet
	{
		final String name;
		final List<ItemSuggestion> items;

		private ItemSet(String name, Object... idsAndNames)
		{
			this.name = name;
			List<ItemSuggestion> list = new ArrayList<>();
			for (int i = 0; i < idsAndNames.length; i += 2)
				list.add(new ItemSuggestion((Integer) idsAndNames[i], (String) idsAndNames[i + 1]));
			this.items = Collections.unmodifiableList(list);
		}
	}

	static final class Preset
	{
		final String label;
		final List<ItemSet> sets;

		private Preset(String label, ItemSet... sets)
		{
			this.label = label;
			this.sets = Arrays.asList(sets);
		}

		@Override public String toString() { return label; }
	}

	private static final ItemSet AHRIM = new ItemSet("Ahrim's", 4708, "Ahrim's hood", 4710, "Ahrim's staff", 4712, "Ahrim's robetop", 4714, "Ahrim's robeskirt");
	private static final ItemSet DHAROK = new ItemSet("Dharok's", 4716, "Dharok's helm", 4718, "Dharok's greataxe", 4720, "Dharok's platebody", 4722, "Dharok's platelegs");
	private static final ItemSet GUTHAN = new ItemSet("Guthan's", 4724, "Guthan's helm", 4726, "Guthan's warspear", 4728, "Guthan's platebody", 4730, "Guthan's chainskirt");
	private static final ItemSet KARIL = new ItemSet("Karil's", 4732, "Karil's coif", 4734, "Karil's crossbow", 4736, "Karil's leathertop", 4738, "Karil's leatherskirt");
	private static final ItemSet TORAG = new ItemSet("Torag's", 4745, "Torag's helm", 4747, "Torag's hammers", 4749, "Torag's platebody", 4751, "Torag's platelegs");
	private static final ItemSet VERAC = new ItemSet("Verac's", 4753, "Verac's helm", 4755, "Verac's flail", 4757, "Verac's brassard", 4759, "Verac's plateskirt");

	private static final ItemSet BLOOD_MOON = new ItemSet("Blood Moon", 28997, "Dual macuahuitl", 29028, "Blood moon helm", 29022, "Blood moon chestplate", 29025, "Blood moon tassets");
	private static final ItemSet BLUE_MOON = new ItemSet("Blue Moon", 28988, "Blue moon spear", 29019, "Blue moon helm", 29013, "Blue moon chestplate", 29016, "Blue moon tassets");
	private static final ItemSet ECLIPSE_MOON = new ItemSet("Eclipse Moon", 29000, "Eclipse atlatl", 29010, "Eclipse moon helm", 29004, "Eclipse moon chestplate", 29007, "Eclipse moon tassets");

	private static final ItemSet ODIUM = new ItemSet("Odium ward", 11928, "Odium shard 1", 11929, "Odium shard 2", 11930, "Odium shard 3");
	private static final ItemSet MALEDICTION = new ItemSet("Malediction ward", 11931, "Malediction shard 1", 11932, "Malediction shard 2", 11933, "Malediction shard 3");

	private static final ItemSet DK_RINGS = new ItemSet("Dagannoth rings", 6737, "Berserker ring", 6733, "Archers ring", 6731, "Seers ring", 6735, "Warrior ring");
	private static final ItemSet BANDOS = new ItemSet("Bandos armour", 11832, "Bandos chestplate", 11834, "Bandos tassets", 11836, "Bandos boots");
	private static final ItemSet ARMADYL = new ItemSet("Armadyl armour", 11826, "Armadyl helmet", 11828, "Armadyl chestplate", 11830, "Armadyl chainskirt");
	private static final ItemSet ANCESTRAL = new ItemSet("Ancestral robes", 21018, "Ancestral hat", 21021, "Ancestral robe top", 21024, "Ancestral robe bottom");
	private static final ItemSet JUSTICIAR = new ItemSet("Justiciar armour", 22326, "Justiciar faceguard", 22327, "Justiciar chestguard", 22328, "Justiciar legguards");
	private static final ItemSet INQUISITOR = new ItemSet("Inquisitor's armour", 24419, "Inquisitor's great helm", 24420, "Inquisitor's hauberk", 24421, "Inquisitor's plateskirt", 24417, "Inquisitor's mace");
	private static final ItemSet MASORI = new ItemSet("Masori armour", 27226, "Masori mask", 27229, "Masori body", 27232, "Masori chaps");
	private static final ItemSet VIRTUS = new ItemSet("Virtus robes", 26241, "Virtus mask", 26243, "Virtus robe top", 26245, "Virtus robe bottom");
	private static final ItemSet TORVA = new ItemSet("Torva armour", 26376, "Torva full helm (damaged)", 26378, "Torva platebody (damaged)", 26380, "Torva platelegs (damaged)");

	private static final ItemSet COX_LOG = new ItemSet("CoX green log",
		21034, "Dexterous prayer scroll", 21079, "Arcane prayer scroll", 21000, "Twisted buckler", 21012, "Dragon hunter crossbow",
		21015, "Dinh's bulwark", 21018, "Ancestral hat", 21021, "Ancestral robe top", 21024, "Ancestral robe bottom",
		13652, "Dragon claws", 21003, "Elder maul", 21043, "Kodai insignia", 20997, "Twisted bow");
	private static final ItemSet TOB_LOG = new ItemSet("ToB green log",
		22477, "Avernic defender hilt", 22324, "Ghrazi rapier", 22481, "Sanguinesti staff (uncharged)",
		22326, "Justiciar faceguard", 22327, "Justiciar chestguard", 22328, "Justiciar legguards", 22486, "Scythe of vitur (uncharged)");
	private static final ItemSet TOA_LOG = new ItemSet("ToA green log",
		26219, "Osmumten's fang", 25975, "Lightbearer", 25985, "Elidinis' ward",
		27226, "Masori mask", 27229, "Masori body", 27232, "Masori chaps", 27277, "Tumeken's shadow (uncharged)");
	private static final Preset COX_LOG_PRESET = new Preset("Raid green log: Chambers of Xeric", COX_LOG);
	private static final Preset TOB_LOG_PRESET = new Preset("Raid green log: Theatre of Blood", TOB_LOG);
	private static final Preset TOA_LOG_PRESET = new Preset("Raid green log: Tombs of Amascut", TOA_LOG);

	static final List<Preset> PRESETS = Collections.unmodifiableList(Arrays.asList(
		new Preset("Any Barrows set (6 options)", AHRIM, DHAROK, GUTHAN, KARIL, TORAG, VERAC),
		new Preset("Barrows: Ahrim's", AHRIM),
		new Preset("Barrows: Dharok's", DHAROK),
		new Preset("Barrows: Guthan's", GUTHAN),
		new Preset("Barrows: Karil's", KARIL),
		new Preset("Barrows: Torag's", TORAG),
		new Preset("Barrows: Verac's", VERAC),
		new Preset("Any Moons of Peril set (3 options)", BLOOD_MOON, BLUE_MOON, ECLIPSE_MOON),
		new Preset("Moons: Blood Moon", BLOOD_MOON),
		new Preset("Moons: Blue Moon", BLUE_MOON),
		new Preset("Moons: Eclipse Moon", ECLIPSE_MOON),
		COX_LOG_PRESET,
		TOB_LOG_PRESET,
		TOA_LOG_PRESET,
		new Preset("Any wilderness ward (2 options)", ODIUM, MALEDICTION),
		new Preset("Wilderness: Odium ward", ODIUM),
		new Preset("Wilderness: Malediction ward", MALEDICTION),
		new Preset("Dagannoth rings", DK_RINGS),
		new Preset("Bandos armour", BANDOS),
		new Preset("Armadyl armour", ARMADYL),
		new Preset("Ancestral robes", ANCESTRAL),
		new Preset("Justiciar armour", JUSTICIAR),
		new Preset("Inquisitor's armour", INQUISITOR),
		new Preset("Masori armour", MASORI),
		new Preset("Virtus robes", VIRTUS),
		new Preset("Torva armour", TORVA)
	));

	private static final List<ItemSet> ALL_SETS = Arrays.asList(AHRIM, DHAROK, GUTHAN, KARIL, TORAG, VERAC,
		BLOOD_MOON, BLUE_MOON, ECLIPSE_MOON, COX_LOG, TOB_LOG, TOA_LOG, ODIUM, MALEDICTION, DK_RINGS, BANDOS, ARMADYL, ANCESTRAL, JUSTICIAR, INQUISITOR, MASORI, VIRTUS, TORVA);

	private TsgHubItemSets()
	{
	}

	static Preset greenLogFor(String raidName)
	{
		if ("Chambers of Xeric".equals(raidName)) return COX_LOG_PRESET;
		if ("Theatre of Blood".equals(raidName)) return TOB_LOG_PRESET;
		if ("Tombs of Amascut".equals(raidName)) return TOA_LOG_PRESET;
		return null;
	}

	static String nameFor(Collection<Integer> itemIds)
	{
		Set<Integer> wanted = new HashSet<>(itemIds);
		for (ItemSet set : ALL_SETS)
		{
			Set<Integer> ids = new HashSet<>();
			for (ItemSuggestion item : set.items) ids.add(item.id);
			if (ids.equals(wanted)) return set.name;
		}
		return "";
	}
}
