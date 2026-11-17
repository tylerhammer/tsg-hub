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

import java.awt.*;
import java.util.*;
import javax.swing.*;
import net.runelite.client.game.*;

class PlayerPrayerPanel extends JPanel
{
	private final Map<PrayerSprites, PrayerSlot> slots = new LinkedHashMap<>();
	private int unlocked = PrayerSprites.DEFAULT_UNLOCKED;

	PlayerPrayerPanel(PartyPlayer player, SpriteManager spriteManager)
	{
		setOpaque(false);
		setLayout(new GridLayout(0, 5, 2, 2));
		for (PrayerSprites p : PrayerSprites.all) slots.put(p, new PrayerSlot(p, spriteManager));
		if (player.getPrayers() != null) update(player.getPrayers());
		else updateSlots();
	}

	void update(int[] prayers)
	{
		for (PrayerSlot slot : slots.values()) slot.update(prayers);
		if (!relayout(unlocked, prayers, getComponentCount())) return;
		unlocked = prayers[2];
		updateSlots();
	}

	static boolean relayout(int shown, int[] prayers, int components)
	{
		return shown != prayers[2] || components == 0;
	}

	private void updateSlots()
	{
		removeAll();
		slots.forEach((p, slot) -> { if ((unlocked & PrayerSprites.bit(p.prayer)) != 0) add(slot); });
		revalidate();
		repaint();
	}
}
