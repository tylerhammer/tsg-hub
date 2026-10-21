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

import lombok.Data;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyMember;

@Data
class PartyPlayer
{
	private transient PartyMember member;
	private String username = "";
	private Stats stats;
	private GameItem[] inventory = new GameItem[28];
	private GameItem[] equipment = new GameItem[EquipmentInventorySlot.AMMO.getSlotIdx() + 1];
	private Prayers prayers;
	private int stamina;
	private int poison;
	private int disease;
	private int world;
	private String area = "";
	private GameItem[] runesInPouch = new GameItem[0];
	private Quiver quiver = new Quiver(null, false, false);
	// 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus
	private int spellbook = -1;

	PartyPlayer(PartyMember member)
	{
		this.member = member;
	}

	// Call on the client thread.
	PartyPlayer(PartyMember member, Client client, ItemManager itemManager)
	{
		this(member);
		stamina = client.getVarbitValue(VarbitID.STAMINA_DURATION);
		poison = client.getVarpValue(VarPlayerID.POISON);
		disease = client.getVarpValue(VarPlayerID.DISEASE);
		world = client.getWorld();
		updatePlayerInfo(client, itemManager);
	}

	void updatePlayerInfo(Client client, ItemManager itemManager)
	{
		if (client.getLocalPlayer() != null)
		{
			username = client.getLocalPlayer().getName();
			stats = new Stats(client);
			ItemContainer inv = client.getItemContainer(InventoryID.INV);
			if (inv != null)
			{
				inventory = GameItem.of(inv.getItems(), itemManager);
				runesInPouch = GroupTracker.runePouch(client, itemManager);
				quiver.readInventory(inventory);
			}
			ItemContainer worn = client.getItemContainer(InventoryID.WORN);
			if (worn != null)
			{
				equipment = GameItem.of(worn.getItems(), itemManager);
				quiver.readEquipment(equipment);
			}
			if (prayers == null) prayers = new Prayers(client);
		}
		if (quiver.isSlotVisible()) quiver.setQuiverAmmo(Quiver.ammo(client, itemManager));
	}

	int getSkillBoostedLevel(Skill skill)
	{
		return stats == null ? 0 : stats.getBoostedLevels().get(skill);
	}

	int getSkillRealLevel(Skill skill, boolean allowVirtualLevels)
	{
		return stats == null ? 0 : Math.min(stats.getBaseLevels().get(skill), allowVirtualLevels ? 126 : 99);
	}
}
