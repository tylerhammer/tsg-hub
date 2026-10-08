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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.ui.PartyStyle;
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
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

import static net.runelite.api.Skill.AGILITY;
import static net.runelite.api.Skill.ATTACK;
import static net.runelite.api.Skill.CONSTRUCTION;
import static net.runelite.api.Skill.COOKING;
import static net.runelite.api.Skill.CRAFTING;
import static net.runelite.api.Skill.DEFENCE;
import static net.runelite.api.Skill.FARMING;
import static net.runelite.api.Skill.FIREMAKING;
import static net.runelite.api.Skill.FISHING;
import static net.runelite.api.Skill.FLETCHING;
import static net.runelite.api.Skill.HERBLORE;
import static net.runelite.api.Skill.HITPOINTS;
import static net.runelite.api.Skill.HUNTER;
import static net.runelite.api.Skill.MAGIC;
import static net.runelite.api.Skill.MINING;
import static net.runelite.api.Skill.PRAYER;
import static net.runelite.api.Skill.RANGED;
import static net.runelite.api.Skill.RUNECRAFT;
import static net.runelite.api.Skill.SAILING;
import static net.runelite.api.Skill.SLAYER;
import static net.runelite.api.Skill.SMITHING;
import static net.runelite.api.Skill.STRENGTH;
import static net.runelite.api.Skill.THIEVING;
import static net.runelite.api.Skill.WOODCUTTING;

public class PlayerSkillsPanel extends JPanel
{
	private static final List<Skill> SKILLS = ImmutableList.of(
		ATTACK, HITPOINTS, MINING,
		STRENGTH, AGILITY, SMITHING,
		DEFENCE, HERBLORE, FISHING,
		RANGED, THIEVING, COOKING,
		PRAYER, CRAFTING, FIREMAKING,
		MAGIC, FLETCHING, WOODCUTTING,
		RUNECRAFT, SLAYER, FARMING,
		CONSTRUCTION, HUNTER, SAILING
	);

	private static final ImmutableMap<Skill, Integer> SPRITE_MAP = ImmutableMap.<Skill, Integer>builder()
		.put(Skill.ATTACK, SpriteID.Staticons.ATTACK)
		.put(Skill.STRENGTH, SpriteID.Staticons.STRENGTH)
		.put(Skill.DEFENCE, SpriteID.Staticons.DEFENCE)
		.put(Skill.RANGED, SpriteID.Staticons.RANGED)
		.put(Skill.PRAYER, SpriteID.Staticons.PRAYER)
		.put(Skill.MAGIC, SpriteID.Staticons.MAGIC)
		.put(Skill.HITPOINTS, SpriteID.Staticons.HITPOINTS)
		.put(Skill.AGILITY, SpriteID.Staticons.AGILITY)
		.put(Skill.HERBLORE, SpriteID.Staticons.HERBLORE)
		.put(Skill.THIEVING, SpriteID.Staticons.THIEVING)
		.put(Skill.CRAFTING, SpriteID.Staticons.CRAFTING)
		.put(Skill.FLETCHING, SpriteID.Staticons.FLETCHING)
		.put(Skill.MINING, SpriteID.Staticons.MINING)
		.put(Skill.SMITHING, SpriteID.Staticons.SMITHING)
		.put(Skill.FISHING, SpriteID.Staticons.FISHING)
		.put(Skill.COOKING, SpriteID.Staticons.COOKING)
		.put(Skill.FIREMAKING, SpriteID.Staticons.FIREMAKING)
		.put(Skill.WOODCUTTING, SpriteID.Staticons.WOODCUTTING)
		.put(Skill.RUNECRAFT, SpriteID.Staticons2.RUNECRAFT)
		.put(Skill.SLAYER, SpriteID.Staticons2.SLAYER)
		.put(Skill.FARMING, SpriteID.Staticons2.FARMING)
		.put(Skill.CONSTRUCTION, SpriteID.Staticons2.CONSTRUCTION)
		.put(Skill.HUNTER, SpriteID.Staticons2.HUNTER)
		.put(Skill.SAILING, SpriteID.Staticons2.SAILING)
		.build();

	private final Map<Skill, SkillPanelSlot> panelMap = new HashMap<>();
	private final JLabel totalLabel = new JLabel();

	public PlayerSkillsPanel(final PartyPlayer player, final boolean displayVirtualLevels, final SpriteManager spriteManager)
	{
		setOpaque(false);
		setLayout(new BorderLayout(0, 6));

		JPanel grid = new JPanel(new GridLayout(0, 3, 2, 2));
		grid.setOpaque(false);
		for (Skill skill : SKILLS)
		{
			SkillPanelSlot slot = new SkillPanelSlot(skill.getName());
			panelMap.put(skill, slot);
			grid.add(slot);
			spriteManager.getSpriteAsync(SPRITE_MAP.get(skill), 0, img -> SwingUtilities.invokeLater(() -> slot.setSkillIcon(img)));
		}

		JLabel totalCaption = new JLabel("Total level");
		totalCaption.setFont(FontManager.getRunescapeSmallFont());
		totalCaption.setForeground(PartyStyle.MUTED);
		totalLabel.setFont(FontManager.getRunescapeSmallFont());
		totalLabel.setForeground(PartyStyle.TEXT);
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
		int total = 0;
		for (Skill skill : SKILLS)
		{
			int base = player.getSkillRealLevel(skill, displayVirtualLevels);
			total += base;
			panelMap.get(skill).setLevels(player.getSkillBoostedLevel(skill), base);
		}
		totalLabel.setText(total > 0 ? QuantityFormatter.formatNumber(total) : "");
	}
}
