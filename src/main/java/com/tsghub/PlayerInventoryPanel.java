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

import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.ArrayUtils;

class PlayerInventoryPanel extends JPanel
{
	private final ItemSlot[] slots = new ItemSlot[28];

	PlayerInventoryPanel(GameItem[] items, GameItem[] runePouchContents, ItemManager itemManager)
	{
		setOpaque(false);
		setLayout(new GridLayout(7, 4, 2, 2));
		for (int i = 0; i < slots.length; i++) add(slots[i] = new ItemSlot(48, 36, itemManager));
		updateInventory(items, runePouchContents);
	}

	void updateInventory(GameItem[] items, GameItem[] runePouchContents)
	{
		for (int i = 0; i < slots.length; i++)
		{
			GameItem item = items != null && i < items.length ? items[i] : null;
			slots[i].setItem(item, item == null ? null : ArrayUtils.contains(GroupTracker.RUNEPOUCH_ITEM_IDS, item.getId())
				? runePouchText(item, runePouchContents)
				: item.getDisplayName());
		}
	}

	private static String runePouchText(GameItem runePouch, GameItem[] contents)
	{
		String names = Arrays.stream(contents).filter(Objects::nonNull).map(GameItem::getDisplayName).collect(Collectors.joining("<br>"));
		return names.isEmpty() ? runePouch.getDisplayName() : "<html>" + runePouch.getDisplayName() + "<br><br>" + names + "</html>";
	}
}
