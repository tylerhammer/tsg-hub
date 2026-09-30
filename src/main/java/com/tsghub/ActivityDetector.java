package com.tsghub;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

final class ActivityDetector
{
	static final long RECENT_MILLIS = 90_000;
	private static final Set<Skill> COMBAT = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.HITPOINTS);

	private final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
	private Skill lastSkill;
	private long lastSkillAt;
	private long lastCombatAt;

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

	void reset()
	{
		xp.clear();
		lastSkill = null;
		lastSkillAt = 0;
		lastCombatAt = 0;
	}

	String activity(AreaNames.Area area, long now)
	{
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
}
