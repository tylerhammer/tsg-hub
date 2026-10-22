/*
 * Copyright (c) 2022, TheStonedTurtle <https://github.com/TheStonedTurtle>
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

import static com.tsghub.PartyMiscChange.PartyMisc.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.runelite.api.Prayer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.messages.PartyMemberMessage;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
class TsgGroupUpdate extends PartyMemberMessage
{
	int[] i; // Inventory
	int[] e; // equipment
	Collection<PartyStatChange> s = new ArrayList<>(); // Stat Changes
	Collection<PartyMiscChange> m = new ArrayList<>(); // Misc Changes
	Integer ap; // Available prayers, bit-packed
	Integer ep; // Enabled prayers, bit-packed
	Integer up; // Unlocked prayers (deadeye/vigour), bit-packed
	int[] rp; // Rune pouch item ID and qty
	int[] q; // Quiver itemId and qty

	boolean isValid()
	{
		return i != null || e != null || (s != null && !s.isEmpty()) || (m != null && !m.isEmpty())
			|| hasPrayers() || rp != null || q != null;
	}

	// Unset unneeded variables to minimize payload
	void removeDefaults()
	{
		if (s != null && s.isEmpty()) s = null;
		if (m != null && m.isEmpty()) m = null;
	}

	void process(PartyPlayer player, ItemManager itemManager)
	{
		if (player.getStats() == null && hasStatChange()) player.setStats(new Stats());
		if (player.getPrayers() == null && hasPrayers()) player.setPrayers(new Prayers());
		if (i != null)
		{
			player.setInventory(GameItem.of(i, itemManager));
			player.getQuiver().readInventory(player.getInventory());
		}
		if (e != null)
		{
			player.setEquipment(GameItem.of(e, itemManager));
			player.getQuiver().readEquipment(player.getEquipment());
		}
		if (s != null) s.forEach(change -> change.process(player));
		if (m != null) m.forEach(change -> change.process(player));
		if (hasPrayers())
		{
			player.getPrayers().getPrayerData().forEach((prayer, data) ->
			{
				data.setAvailable(false);
				data.setEnabled(false);
				data.setUnlocked(false);
			});
			for (Prayer p : unpack(ap)) player.getPrayers().getPrayerData().get(p).setAvailable(true);
			for (Prayer p : unpack(ep)) player.getPrayers().getPrayerData().get(p).setEnabled(true);
			for (Prayer p : unpack(up)) player.getPrayers().getPrayerData().get(p).setUnlocked(true);
		}
		if (rp != null) player.setRunesInPouch(Arrays.stream(rp).mapToObj(r -> new GameItem(r & 0x3FFFF, r >>> 18, itemManager)).toArray(GameItem[]::new));
		if (q != null) player.getQuiver().setQuiverAmmo(q.length == 2 ? new GameItem(q[0], q[1], itemManager) : null);
	}

	private boolean hasPrayers()
	{
		return ap != null || ep != null || up != null;
	}

	boolean hasAreaChange()
	{
		return m != null && m.stream().anyMatch(change -> change.t == A);
	}

	boolean hasStatChange()
	{
		return (s != null && !s.isEmpty()) || (m != null && m.stream().anyMatch(change -> change.t == S || change.t == R || change.t == C || change.t == T));
	}

	static <E extends Enum<E>> int pack(Collection<E> items)
	{
		int packed = 0;
		for (E item : items) packed |= 1 << item.ordinal();
		return packed;
	}

	private static List<Prayer> unpack(Integer packed)
	{
		List<Prayer> out = new ArrayList<>();
		// 32-bit packing; stop early since ruinous powers would overflow it.
		for (Prayer p : Prayer.values()) if (packed != null && p.ordinal() < 32 && (packed & (1 << p.ordinal())) != 0) out.add(p);
		return out;
	}

	// Qty (max 16,000) fits in the top 14 bits, item ID in the low 18.
	static int packRune(int itemId, int qty)
	{
		return qty << 18 | itemId;
	}
}
