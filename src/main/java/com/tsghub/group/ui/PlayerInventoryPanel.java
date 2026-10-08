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
package com.tsghub.group.ui;

import com.tsghub.group.GroupTracker;
import com.tsghub.group.data.GameItem;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import org.apache.commons.lang3.ArrayUtils;

public class PlayerInventoryPanel extends JPanel
{
	private static final Dimension SLOT_SIZE = new Dimension(48, 36);
	private static final int SLOTS = 28;

	private final ItemManager itemManager;
	private final JLabel[] slots = new JLabel[SLOTS];

	public PlayerInventoryPanel(final GameItem[] items, final GameItem[] runePouchContents, final ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setOpaque(false);
		setLayout(new GridLayout(7, 4, 2, 2));
		for (int i = 0; i < SLOTS; i++)
		{
			JLabel label = new JLabel();
			label.setOpaque(true);
			label.setBackground(PartyStyle.SLOT);
			label.setPreferredSize(SLOT_SIZE);
			label.setHorizontalAlignment(JLabel.CENTER);
			label.setVerticalAlignment(JLabel.CENTER);
			slots[i] = label;
			add(label);
		}
		updateInventory(items, runePouchContents);
	}

	public void updateInventory(final GameItem[] items, final GameItem[] runePouchContents)
	{
		for (int i = 0; i < SLOTS; i++)
		{
			JLabel label = slots[i];
			GameItem item = items != null && i < items.length ? items[i] : null;
			if (item == null)
			{
				label.setIcon(null);
				label.setToolTipText(null);
				continue;
			}
			label.setToolTipText(ArrayUtils.contains(GroupTracker.RUNEPOUCH_ITEM_IDS, item.getId())
				? getRunePouchHoverText(item, runePouchContents)
				: item.getDisplayName());
			if (itemManager != null) itemManager.getImage(item.getId(), item.getQty(), item.isStackable()).addTo(label);
		}
	}

	public String getRunePouchHoverText(final GameItem runePouch, final GameItem[] contents)
	{
		final String contentNames = Arrays.stream(contents)
			.filter(Objects::nonNull)
			.map(GameItem::getDisplayName)
			.collect(Collectors.joining("<br>"));

		if (contentNames.isEmpty())
		{
			return runePouch.getDisplayName();
		}

		return "<html>"
			+ runePouch.getDisplayName()
			+ "<br><br>"
			+ contentNames
			+ "</html>";
	}
}
