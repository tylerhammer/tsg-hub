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

import com.tsghub.group.data.PrayerData;
import com.tsghub.group.ui.PartyStyle;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import lombok.Getter;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.Text;

import static com.tsghub.group.data.Prayers.isUnlockedByDefault;

public class PrayerSlot extends JComponent
{
	private static final Dimension SIZE = new Dimension(38, 34);

	private BufferedImage unavailableImage;
	private BufferedImage availableImage;

	@Getter
	private PrayerData data;

	public PrayerSlot(final PrayerSprites sprites, final SpriteManager spriteManager)
	{
		data = new PrayerData(sprites.getPrayer(), false, false, isUnlockedByDefault(sprites.getPrayer()));
		spriteManager.getSpriteAsync(sprites.getUnavailable(), 0, img -> SwingUtilities.invokeLater(() -> {
			unavailableImage = img;
			repaint();
		}));
		spriteManager.getSpriteAsync(sprites.getAvailable(), 0, img -> SwingUtilities.invokeLater(() -> {
			availableImage = img;
			repaint();
		}));
		setToolTipText(Text.titleCase(sprites.getPrayer()));
		setPreferredSize(SIZE);
		setMinimumSize(SIZE);
	}

	public void updatePrayerData(final PrayerData updatedData)
	{
		if (!data.getPrayer().equals(updatedData.getPrayer())) return;
		data = updatedData;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		Graphics2D g = (Graphics2D) g0.create();
		boolean active = data.isEnabled();
		g.setColor(active ? PartyStyle.PRAYER_ACTIVE : PartyStyle.SLOT);
		g.fillRect(0, 0, getWidth(), getHeight());
		if (active)
		{
			g.setColor(PartyStyle.PRAYER_ACTIVE_BORDER);
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
		else g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
		BufferedImage icon = data.isAvailable() || active ? availableImage : unavailableImage;
		if (icon != null) g.drawImage(icon, (getWidth() - icon.getWidth()) / 2, (getHeight() - icon.getHeight()) / 2, null);
		g.dispose();
	}
}
