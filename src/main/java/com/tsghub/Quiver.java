/*
 * Copyright (c) 2025, TheStonedTurtle <https://github.com/TheStonedTurtle>
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

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

@AllArgsConstructor
@Data
class Quiver
{
	private static final Set<Integer> IDS = ImmutableSet.<Integer>builder()
		.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.DIZANAS_QUIVER_CHARGED)))
		.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.DIZANAS_QUIVER_INFINITE)))
		.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.SKILLCAPE_MAX_DIZANAS)))
		.build();
	private static final int CAPE = EquipmentInventorySlot.CAPE.getSlotIdx();

	private GameItem quiverAmmo;
	private boolean beingWorn;
	private boolean inInventory;

	boolean isSlotVisible()
	{
		return beingWorn || inInventory;
	}

	static boolean isQuiver(int itemId)
	{
		return IDS.contains(itemId);
	}

	static GameItem ammo(Client client, ItemManager itemManager)
	{
		int id = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		int count = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		return id == -1 || count == 0 ? null : new GameItem(id, count, itemManager);
	}

	static int[] pack(GameItem ammo)
	{
		return ammo != null ? new int[]{ammo.getId(), ammo.getQty()} : new int[0];
	}

	void readInventory(GameItem[] items)
	{
		inInventory = false;
		for (GameItem item : items) if (item != null && isQuiver(item.getId())) inInventory = true;
	}

	void readEquipment(GameItem[] items)
	{
		GameItem cape = items.length > CAPE ? items[CAPE] : null;
		beingWorn = cape != null && isQuiver(cape.getId());
	}
}
