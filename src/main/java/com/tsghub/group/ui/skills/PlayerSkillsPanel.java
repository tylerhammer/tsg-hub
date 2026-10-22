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

import com.tsghub.TsgHubTheme;
import com.google.common.collect.ImmutableList;
import com.tsghub.group.data.PartyPlayer;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.QuantityFormatter;

public class PlayerSkillsPanel extends JPanel
{
	public static final List<Skill> SKILL_ORDER = ImmutableList.of(
		Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
		Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
		Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
		Skill.RANGED, Skill.THIEVING, Skill.COOKING,
		Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
		Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
		Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
		Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
	);

	private final Map<Skill, SkillPanelSlot> panelMap = new HashMap<>();
	private final JLabel totalLabel = new JLabel();

	public PlayerSkillsPanel(final PartyPlayer player, final boolean displayVirtualLevels, final SpriteManager spriteManager)
	{
		setOpaque(false);
		setLayout(new BorderLayout(0, 6));

		JPanel grid = new JPanel(new GridLayout(0, 3, 2, 2));
		grid.setOpaque(false);
		for (Skill skill : SKILL_ORDER)
		{
			SkillPanelSlot slot = new SkillPanelSlot(skill.getName());
			panelMap.put(skill, slot);
			grid.add(slot);
			spriteManager.getSpriteAsync(HiscoreSkill.valueOf(skill.name()).getSpriteId(), 0, img -> SwingUtilities.invokeLater(() -> slot.setSkillIcon(img)));
		}

		JLabel totalCaption = new JLabel("Total level");
		totalCaption.setFont(TsgHubTheme.smallFont());
		totalCaption.setForeground(TsgHubTheme.MUTED);
		totalLabel.setFont(TsgHubTheme.smallFont());
		totalLabel.setForeground(TsgHubTheme.TEXT);
		JPanel total = new JPanel(new BorderLayout());
		total.setOpaque(false);
		total.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
		total.add(totalCaption, BorderLayout.CENTER);
		total.add(totalLabel, BorderLayout.EAST);

		add(grid, BorderLayout.CENTER);
		add(total, BorderLayout.SOUTH);
		update(player, displayVirtualLevels);
	}

	public void update(PartyPlayer player, boolean displayVirtualLevels)
	{
		if (player.getStats() == null)
		{
			for (SkillPanelSlot slot : panelMap.values()) slot.clear();
			totalLabel.setText("");
			return;
		}
		int total = 0;
		for (Skill skill : SKILL_ORDER)
		{
			int base = player.getSkillRealLevel(skill, displayVirtualLevels);
			total += base;
			panelMap.get(skill).setLevels(player.getSkillBoostedLevel(skill), base);
		}
		totalLabel.setText(total > 0 ? QuantityFormatter.formatNumber(total) : "");
	}
}
