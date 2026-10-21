/*
 * Copyright (c) 2020, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tsghub;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;

@Getter
@Setter
class Stats
{
	private final Map<Skill, Integer> baseLevels = new HashMap<>();
	private final Map<Skill, Integer> boostedLevels = new HashMap<>();
	private int specialPercent;
	private int runEnergy;
	private int combatLevel;
	private int totalLevel;

	Stats()
	{
		for (Skill s : Skill.values())
		{
			baseLevels.put(s, s == Skill.HITPOINTS ? 10 : 1);
			boostedLevels.put(s, s == Skill.HITPOINTS ? 10 : 1);
		}
		combatLevel = 3;
	}

	Stats(Client client)
	{
		int[] bases = client.getSkillExperiences();
		int[] boosts = client.getBoostedSkillLevels();
		for (Skill s : Skill.values())
		{
			baseLevels.put(s, Experience.getLevelForXp(bases[s.ordinal()]));
			boostedLevels.put(s, boosts[s.ordinal()]);
		}

		recalculateCombatLevel();

		specialPercent = client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
		totalLevel = client.getTotalLevel();
		runEnergy = client.getEnergy() / 100;
	}

	void recalculateCombatLevel()
	{
		combatLevel = Experience.getCombatLevel(capped(Skill.ATTACK), capped(Skill.STRENGTH), capped(Skill.DEFENCE),
			capped(Skill.HITPOINTS), capped(Skill.MAGIC), capped(Skill.RANGED), capped(Skill.PRAYER));
	}

	private int capped(Skill skill)
	{
		return Math.min(baseLevels.get(skill), 99);
	}

	PartyStatChange createPartyStatChangeForSkill(Skill s)
	{
		return new PartyStatChange(s.ordinal(), baseLevels.get(s), boostedLevels.get(s));
	}
}
