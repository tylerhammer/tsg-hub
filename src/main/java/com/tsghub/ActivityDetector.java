package com.tsghub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.client.util.Text;

final class ActivityDetector
{
	static final long RECENT_MILLIS = 90_000;
	private static final Set<Skill> COMBAT = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.HITPOINTS);
	private static final Map<String, String> BOSSES = loadBosses();

	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private Skill lastSkill;
	private long lastSkillAt;
	private long lastCombatAt;
	private String lastBoss;
	private long lastBossAt;

	static String bossFor(String npcName)
	{
		return npcName == null ? null : BOSSES.get(Text.removeTags(npcName).trim().toLowerCase(Locale.ROOT));
	}

	void onXp(Skill skill, int value, long now)
	{
		Integer previous = xp.put(skill, value);
		if (previous == null || value <= previous) return;
		if (skill == Skill.HITPOINTS) lastCombatAt = now;
		else if (!COMBAT.contains(skill))
		{
			lastSkill = skill;
			lastSkillAt = now;
		}
	}

	void onOpponent(String npcName, long now)
	{
		String boss = bossFor(npcName);
		if (boss == null) return;
		lastBoss = boss;
		lastBossAt = now;
	}

	void reset()
	{
		xp.clear();
		lastSkill = null;
		lastSkillAt = 0;
		lastCombatAt = 0;
		lastBoss = null;
		lastBossAt = 0;
	}

	String activity(AreaNames.Area area, long now)
	{
		if (lastBoss != null && now - lastBossAt < RECENT_MILLIS) return "Bossing - " + lastBoss;
		if (area != null)
		{
			if (area.type == AreaNames.Type.BOSSES) return "Bossing - " + area.name;
			if (area.type == AreaNames.Type.RAIDS) return "Raiding - " + area.name;
			if (area.type == AreaNames.Type.MINIGAMES) return "Minigame - " + area.name;
		}
		if (lastCombatAt > 0 && now - lastCombatAt < RECENT_MILLIS) return "Combat";
		if (lastSkill != null && now - lastSkillAt < RECENT_MILLIS) return "Skilling - " + lastSkill.getName();
		return "Idle";
	}

	private static Map<String, String> loadBosses()
	{
		Map<String, String> bosses = new HashMap<>();
		InputStream in = ActivityDetector.class.getResourceAsStream("bosses.tsv");
		if (in == null) return bosses;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\t");
				if (parts.length < 2) continue;
				for (String npc : parts[1].split(",")) bosses.put(npc.trim().toLowerCase(Locale.ROOT), parts[0]);
			}
		}
		catch (IOException ignored) { }
		return bosses;
	}
}
