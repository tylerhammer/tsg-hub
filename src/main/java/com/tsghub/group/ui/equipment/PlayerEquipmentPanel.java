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
package com.tsghub.group.ui.equipment;

import com.google.common.collect.ImmutableMap;
import com.tsghub.group.data.GameItem;
import com.tsghub.group.data.Quiver;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

public class PlayerEquipmentPanel extends JPanel
{
	private static final ImmutableMap<EquipmentInventorySlot, Integer> EQUIPMENT_SLOT_SPRITE_MAP = new ImmutableMap.Builder<EquipmentInventorySlot, Integer>()
		.put(EquipmentInventorySlot.HEAD, SpriteID.Wornicons.HEAD)
		.put(EquipmentInventorySlot.CAPE, SpriteID.Wornicons.CAPE)
		.put(EquipmentInventorySlot.AMULET, SpriteID.Wornicons.NECK)
		.put(EquipmentInventorySlot.WEAPON, SpriteID.Wornicons.WEAPON)
		.put(EquipmentInventorySlot.RING, SpriteID.Wornicons.RING)
		.put(EquipmentInventorySlot.BODY, SpriteID.Wornicons.TORSO)
		.put(EquipmentInventorySlot.SHIELD, SpriteID.Wornicons.SHIELD)
		.put(EquipmentInventorySlot.LEGS, SpriteID.Wornicons.LEGS)
		.put(EquipmentInventorySlot.GLOVES, SpriteID.Wornicons.HANDS)
		.put(EquipmentInventorySlot.BOOTS, SpriteID.Wornicons.FEET)
		.put(EquipmentInventorySlot.AMMO, SpriteID.Wornicons.AMMUNITION)
		.build();

	@Getter
	private final Map<EquipmentInventorySlot, EquipmentPanelSlot> panelMap = new HashMap<>();
	private final EquipmentPanelSlot quiverSlot = new EquipmentPanelSlot();
	private final ItemManager itemManager;

	public PlayerEquipmentPanel(final GameItem[] items, final Quiver quiver, final SpriteManager spriteManager, final ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setOpaque(false);
		setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 8, 3, 8);
		place(c, 1, 0, EquipmentInventorySlot.HEAD);
		c.gridx = 2;
		add(quiverSlot, c);
		place(c, 0, 1, EquipmentInventorySlot.CAPE);
		place(c, 1, 1, EquipmentInventorySlot.AMULET);
		place(c, 2, 1, EquipmentInventorySlot.AMMO);
		place(c, 0, 2, EquipmentInventorySlot.WEAPON);
		place(c, 1, 2, EquipmentInventorySlot.BODY);
		place(c, 2, 2, EquipmentInventorySlot.SHIELD);
		place(c, 1, 3, EquipmentInventorySlot.LEGS);
		place(c, 0, 4, EquipmentInventorySlot.GLOVES);
		place(c, 1, 4, EquipmentInventorySlot.BOOTS);
		place(c, 2, 4, EquipmentInventorySlot.RING);

		for (Map.Entry<EquipmentInventorySlot, Integer> entry : EQUIPMENT_SLOT_SPRITE_MAP.entrySet())
		{
			EquipmentPanelSlot slot = panelMap.get(entry.getKey());
			spriteManager.getSpriteAsync(entry.getValue(), 0, img -> SwingUtilities.invokeLater(() -> slot.setPlaceholder(img)));
		}
		spriteManager.getSpriteAsync(SpriteID.Wornicons.AMMUNITION, 0, img -> SwingUtilities.invokeLater(() -> quiverSlot.setPlaceholder(img)));
		quiverSlot.setToolTipText("Quiver");

		update(items, quiver);
	}

	private void place(GridBagConstraints c, int x, int y, EquipmentInventorySlot slot)
	{
		EquipmentPanelSlot panel = new EquipmentPanelSlot();
		panelMap.put(slot, panel);
		c.gridx = x;
		c.gridy = y;
		add(panel, c);
	}

	public void update(final GameItem[] items, final Quiver quiver)
	{
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			EquipmentPanelSlot panel = panelMap.get(slot);
			if (panel == null) continue;
			GameItem item = items != null && slot.getSlotIdx() < items.length ? items[slot.getSlotIdx()] : null;
			setItem(panel, item);
		}
		quiverSlot.setVisible(quiver != null && quiver.isSlotVisible());
		setItem(quiverSlot, quiver == null ? null : quiver.getQuiverAmmo());
	}

	private void setItem(EquipmentPanelSlot panel, GameItem item)
	{
		if (item == null || itemManager == null)
		{
			panel.setGameItem(null, null);
			return;
		}
		AsyncBufferedImage img = itemManager.getImage(item.getId(), item.getQty(), item.isStackable());
		panel.setGameItem(item, img);
		img.onLoaded(() -> panel.setGameItem(item, img));
	}
}
