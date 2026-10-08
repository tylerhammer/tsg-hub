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
package com.tsghub.group.ui.skills;

import com.tsghub.group.ui.PartyStyle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

public class SkillPanelSlot extends JPanel
{
	private static final Color BOOSTED = new Color(76, 217, 100);

	private final String name;
	private final JLabel levelLabel = new JLabel();
	private final JLabel baseLabel = new JLabel();
	private int boosted = -1;
	private int base = -1;

	SkillPanelSlot(String name)
	{
		this.name = name;
		setOpaque(true);
		setBackground(PartyStyle.SLOT);
		setBorder(new EmptyBorder(3, 3, 3, 1));
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		levelLabel.setFont(FontManager.getRunescapeFont());
		levelLabel.setForeground(PartyStyle.TEXT);
		levelLabel.setIconTextGap(3);
		baseLabel.setFont(FontManager.getRunescapeSmallFont());
		baseLabel.setForeground(PartyStyle.MUTED);
		add(levelLabel);
		add(baseLabel);
		add(Box.createHorizontalGlue());
		setToolTipText(name);
	}

	void setSkillIcon(BufferedImage icon)
	{
		levelLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(icon, 16, 16)));
	}

	void setLevels(int boostedLevel, int baseLevel)
	{
		if (boostedLevel == boosted && baseLevel == base) return;
		boosted = boostedLevel;
		base = baseLevel;
		boolean changed = boostedLevel != baseLevel;
		levelLabel.setText(String.valueOf(boostedLevel));
		levelLabel.setForeground(!changed ? PartyStyle.TEXT : boostedLevel > baseLevel ? BOOSTED : PartyStyle.LOW);
		baseLabel.setText(changed ? "/" + baseLevel : "");
		setToolTipText(name + " " + boostedLevel + "/" + baseLevel);
	}
}
