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
package com.tsghub.group.ui.prayer;

import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.data.PrayerData;
import com.tsghub.group.data.Prayers;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import lombok.Getter;
import net.runelite.api.Prayer;
import net.runelite.client.game.SpriteManager;

public class PlayerPrayerPanel extends JPanel
{
	@Getter
	private final Map<Prayer, PrayerSlot> slotMap = new HashMap<>();

	public PlayerPrayerPanel(final PartyPlayer player, final SpriteManager spriteManager)
	{
		setOpaque(false);
		setLayout(new GridLayout(0, 5, 2, 2));
		for (PrayerSprites p : PrayerSprites.values()) slotMap.put(p.getPrayer(), new PrayerSlot(p, spriteManager));
		if (player.getPrayers() != null) update(player.getPrayers());
		else updateSlots();
	}

	public void update(Prayers prayers)
	{
		boolean unlockChanged = false;
		for (Map.Entry<Prayer, PrayerSlot> entry : slotMap.entrySet())
		{
			PrayerData data = prayers.getPrayerData().get(entry.getKey());
			if (data == null) continue;
			unlockChanged |= data.isUnlocked() != entry.getValue().getData().isUnlocked();
			entry.getValue().updatePrayerData(data);
		}
		if (unlockChanged || getComponentCount() == 0) updateSlots();
	}

	private void updateSlots()
	{
		removeAll();
		for (PrayerSprites p : PrayerSprites.values())
		{
			PrayerSlot slot = slotMap.get(p.getPrayer());
			if (slot.getData().isUnlocked()) add(slot);
		}
		revalidate();
		repaint();
	}
}
