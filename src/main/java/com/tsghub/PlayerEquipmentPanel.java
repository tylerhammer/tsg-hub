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

import static net.runelite.api.EquipmentInventorySlot.*;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.SpriteID.Wornicons;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;

class PlayerEquipmentPanel extends JPanel
{
	// Slot index, grid x, grid y, placeholder sprite.
	private static final int[][] LAYOUT = {
		{HEAD.getSlotIdx(), 1, 0, Wornicons.HEAD},
		{CAPE.getSlotIdx(), 0, 1, Wornicons.CAPE},
		{AMULET.getSlotIdx(), 1, 1, Wornicons.NECK},
		{AMMO.getSlotIdx(), 2, 1, Wornicons.AMMUNITION},
		{WEAPON.getSlotIdx(), 0, 2, Wornicons.WEAPON},
		{BODY.getSlotIdx(), 1, 2, Wornicons.TORSO},
		{SHIELD.getSlotIdx(), 2, 2, Wornicons.SHIELD},
		{LEGS.getSlotIdx(), 1, 3, Wornicons.LEGS},
		{GLOVES.getSlotIdx(), 0, 4, Wornicons.HANDS},
		{BOOTS.getSlotIdx(), 1, 4, Wornicons.FEET},
		{RING.getSlotIdx(), 2, 4, Wornicons.RING},
		{-1, 2, 0, Wornicons.AMMUNITION},
	};

	private final ItemSlot[] slots = new ItemSlot[AMMO.getSlotIdx() + 1];
	private ItemSlot quiverSlot;

	PlayerEquipmentPanel(GameItem[] items, Quiver quiver, SpriteManager spriteManager, ItemManager itemManager)
	{
		setOpaque(false);
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 8, 3, 8);
		for (int[] spec : LAYOUT)
		{
			ItemSlot slot = new ItemSlot(48, 46, itemManager);
			if (spec[0] < 0) quiverSlot = slot;
			else slots[spec[0]] = slot;
			c.gridx = spec[1];
			c.gridy = spec[2];
			add(slot, c);
			spriteManager.getSpriteAsync(spec[3], 0, img -> SwingUtilities.invokeLater(() -> slot.setPlaceholder(img)));
		}
		update(items, quiver);
	}

	void update(GameItem[] items, Quiver quiver)
	{
		for (int i = 0; i < slots.length; i++)
		{
			if (slots[i] == null) continue;
			GameItem item = items != null && i < items.length ? items[i] : null;
			slots[i].setItem(item, item == null ? null : item.getDisplayName());
		}
		GameItem ammo = quiver == null ? null : quiver.getQuiverAmmo();
		quiverSlot.setVisible(quiver != null && quiver.isSlotVisible());
		quiverSlot.setItem(ammo, ammo == null ? null : ammo.getDisplayName());
	}
}
