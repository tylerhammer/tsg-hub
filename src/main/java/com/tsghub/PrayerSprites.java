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

import com.google.gson.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.gameval.*;

@Slf4j
@RequiredArgsConstructor
final class PrayerSprites
{
	static final int PRAYER_IS_AVAILABLE = 10154;
	static final int DEFAULT_UNLOCKED = ~(bit(Prayer.DEADEYE) | bit(Prayer.MYSTIC_VIGOUR));
	static volatile List<PrayerSprites> all = Collections.emptyList();

	final Prayer prayer;
	final int available;
	final int unavailable;
	final int itemId;

	static void load(Gson gson)
	{
		if (!all.isEmpty()) return;
		List<PrayerSprites> out = new ArrayList<>();
		InputStream in = PrayerSprites.class.getResourceAsStream("prayers.json");
		if (in == null) return;
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			for (Map.Entry<String, JsonElement> e : gson.fromJson(reader, JsonObject.class).entrySet())
			{
				JsonArray v = e.getValue().getAsJsonArray();
				out.add(new PrayerSprites(Prayer.valueOf(e.getKey()), v.get(0).getAsInt(), v.get(1).getAsInt(), v.get(2).getAsInt()));
			}
		}
		catch (Exception e) { log.debug("TSG Hub prayer table unreadable", e); }
		all = Collections.unmodifiableList(out);
	}

	static int bit(Prayer prayer)
	{
		return 1 << prayer.ordinal();
	}

	static boolean unlocked(Prayer prayer, Client client)
	{
		switch (prayer)
		{
			case EAGLE_EYE: return !unlocked(Prayer.DEADEYE, client);
			case MYSTIC_MIGHT: return !unlocked(Prayer.MYSTIC_VIGOUR, client);
			case DEADEYE: return client.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) != 0 && client.getVarbitValue(VarbitID.BR_INGAME) == 0;
			case MYSTIC_VIGOUR: return client.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0 && client.getVarbitValue(VarbitID.BR_INGAME) == 0;
			default: return true;
		}
	}

	static int[] read(Client client)
	{
		int available = 0;
		int enabled = 0;
		int unlocked = 0;
		for (PrayerSprites p : all)
		{
			if (!unlocked(p.prayer, client)) continue;
			int bit = bit(p.prayer);
			unlocked |= bit;
			client.runScript(PRAYER_IS_AVAILABLE, p.itemId);
			if (client.getIntStack()[0] > 0) available |= bit;
			if (client.getVarbitValue(p.prayer.getVarbit()) == 1) enabled |= bit;
		}
		return new int[]{available, enabled, unlocked};
	}

	static boolean canRunScripts(Client client)
	{
		return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}
}
