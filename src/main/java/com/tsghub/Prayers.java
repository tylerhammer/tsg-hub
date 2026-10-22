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
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.Prayer;

class Prayers
{
	static final int PRAYER_IS_AVAILABLE = 464;

	@Getter
	private final Map<Prayer, PrayerData> prayerData = new HashMap<>();
	private int[] prayerIds = new int[0];

	Prayers()
	{
		for (Prayer p : Prayer.values())
			prayerData.put(p, new PrayerData(p, p.ordinal() == 0, false, isUnlockedByDefault(p)));
	}

	Prayers(Client client)
	{
		if (client.getLocalPlayer() == null) prayerIds = client.getEnum(EnumID.PRAYERS_NORMAL).getIntVals();
		else for (PrayerSprites p : PrayerSprites.values()) updatePrayerState(p, client);
	}

	boolean updatePrayerState(PrayerSprites p, Client client)
	{
		if (prayerIds.length == 0) prayerIds = client.getEnum(EnumID.PRAYERS_NORMAL).getIntVals();
		boolean unlocked = p.isUnlocked(client);
		boolean available = false;
		if (unlocked)
		{
			client.runScript(PRAYER_IS_AVAILABLE, prayerIds[p.getScriptIndex()]);
			available = client.getIntStack()[0] > 0;
		}
		boolean enabled = unlocked && client.getVarbitValue(p.getPrayer().getVarbit()) == 1;
		PrayerData data = prayerData.get(p.getPrayer());
		if (data == null)
		{
			prayerData.put(p.getPrayer(), new PrayerData(p.getPrayer(), available, enabled, unlocked));
			return true;
		}
		boolean changed = data.isAvailable() != available || data.isEnabled() != enabled || data.isUnlocked() != unlocked;
		data.setAvailable(available);
		data.setEnabled(enabled);
		data.setUnlocked(unlocked);
		return changed;
	}

	void resetPrayerIds()
	{
		prayerIds = new int[0];
	}

	static boolean isUnlockedByDefault(Prayer p)
	{
		return !p.name().equals("DEADEYE") && !p.name().equals("MYSTIC_VIGOUR");
	}
}
