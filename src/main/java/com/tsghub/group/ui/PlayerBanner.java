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

import com.tsghub.TsgHubTheme;
import com.google.common.base.Strings;
import com.tsghub.group.data.PartyPlayer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

public class PlayerBanner extends JPanel
{
	private static final int STAT_ICON = 16;
	private static final String SPRITE_KEY = "tsghub.sprite";

	private final SpriteManager spriteManager;
	private final JLabel nameLabel = new JLabel();
	private final JLabel worldLabel = new JLabel();
	private final JLabel areaLabel = new JLabel();
	private final JLabel spellbookLabel = new JLabel();
	private final JLabel hpLabel = statLabel("Hitpoints");
	private final JLabel prayerLabel = statLabel("Prayer");
	private final JLabel specLabel = statLabel("Special attack");
	private final JLabel runLabel = statLabel("Run energy");

	private PartyPlayer player;
	private boolean displayWorld;
	private BufferedImage currentHeart;
	private boolean heartSet;
	private Boolean usingStamIcon;
	private int spellbook = Integer.MIN_VALUE;

	public PlayerBanner(PartyPlayer player, boolean displayWorld, SpriteManager spriteManager)
	{
		this.player = player;
		this.displayWorld = displayWorld;
		this.spriteManager = spriteManager;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		nameLabel.setFont(TsgHubTheme.boldFont());
		nameLabel.setMinimumSize(new Dimension(0, 0));
		nameLabel.putClientProperty("html.disable", Boolean.TRUE);
		worldLabel.setFont(TsgHubTheme.smallFont());
		worldLabel.setForeground(TsgHubTheme.MUTED);
		areaLabel.setFont(TsgHubTheme.smallFont());
		areaLabel.setMinimumSize(new Dimension(0, 0));
		areaLabel.putClientProperty("html.disable", Boolean.TRUE);

		JPanel top = row();
		top.add(nameLabel, BorderLayout.CENTER);
		top.add(worldLabel, BorderLayout.EAST);
		JPanel bottom = row();
		bottom.add(areaLabel, BorderLayout.CENTER);
		bottom.add(spellbookLabel, BorderLayout.EAST);
		JPanel stats = new JPanel(new GridLayout(1, 4, 2, 0));
		stats.setOpaque(false);
		stats.add(hpLabel);
		stats.add(prayerLabel);
		stats.add(specLabel);
		stats.add(runLabel);

		stats.setAlignmentX(LEFT_ALIGNMENT);
		add(top);
		add(Box.createVerticalStrut(TsgHubTheme.GAP_XS));
		add(bottom);
		add(Box.createVerticalStrut(TsgHubTheme.GAP_S));
		add(stats);

		setSprite(prayerLabel, SpriteID.Staticons.PRAYER);
		setSprite(specLabel, SpriteID.OVERLAY_MULTIWAY);
		update(player);
	}

	public void setPlayer(PartyPlayer player)
	{
		this.player = player;
	}

	public void update(PartyPlayer player)
	{
		this.player = player;
		boolean online = !Strings.isNullOrEmpty(player.getUsername());
		nameLabel.setText(online ? player.getUsername() : "Not logged in");
		nameLabel.setForeground(online ? TsgHubTheme.TEXT : TsgHubTheme.MUTED);
		nameLabel.setToolTipText(online && player.getStats() != null ? "Combat level " + player.getStats().getCombatLevel() : null);
		updateWorld(player, displayWorld);
		refreshStats();
		updateSpellbookIcon(player.getSpellbook());
	}

	public void refreshStats()
	{
		int hp = player.getSkillBoostedLevel(Skill.HITPOINTS);
		int prayer = player.getSkillBoostedLevel(Skill.PRAYER);
		hpLabel.setText(String.valueOf(hp));
		hpLabel.setForeground(PartyStyle.levelColor(hp, player.getSkillRealLevel(Skill.HITPOINTS)));
		prayerLabel.setText(String.valueOf(prayer));
		prayerLabel.setForeground(PartyStyle.levelColor(prayer, player.getSkillRealLevel(Skill.PRAYER)));
		specLabel.setText(player.getStats() == null ? "0" : String.valueOf(player.getStats().getSpecialPercent()));
		runLabel.setText(player.getStats() == null ? "0" : String.valueOf(player.getStats().getRunEnergy()));
	}

	public void updateWorld(PartyPlayer player, boolean displayWorlds)
	{
		displayWorld = displayWorlds;
		boolean online = !Strings.isNullOrEmpty(player.getUsername());
		boolean inGame = online && player.getWorld() > 0;
		worldLabel.setText(displayWorlds && inGame ? "W" + player.getWorld() : "");
		String area = Strings.nullToEmpty(player.getArea());
		String text = !online ? "Last seen stats" : !inGame ? "Not logged in" : area;
		areaLabel.setText(text);
		areaLabel.setForeground(inGame && !area.isEmpty() ? TsgHubTheme.SUCCESS : TsgHubTheme.MUTED);
		areaLabel.setToolTipText(area.isEmpty() ? null : PartyStyle.plainTooltip(area));
	}

	public void setCurrentHeart(BufferedImage img)
	{
		if (heartSet && img == currentHeart) return;
		heartSet = true;
		currentHeart = img;
		if (img == null)
		{
			setSprite(hpLabel, SpriteID.Staticons.HITPOINTS);
			return;
		}
		hpLabel.putClientProperty(SPRITE_KEY, null);
		hpLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(img, STAT_ICON, STAT_ICON)));
	}

	public void setUsingStamIcon(boolean stamina)
	{
		if (usingStamIcon != null && usingStamIcon == stamina) return;
		usingStamIcon = stamina;
		setSprite(runLabel, stamina ? SpriteID.OrbIcon.RUN_ICON_SLOWED_DEPLETION : SpriteID.OrbIcon.RUN);
	}

	private void updateSpellbookIcon(int book)
	{
		if (book == spellbook) return;
		spellbook = book;
		int spriteID;
		String name;
		switch (book)
		{
			case 3:
				spriteID = SpriteID.SideIcons.SPELLBOOK_ARCEUUS;
				name = "Arceuus spellbook";
				break;
			case 2:
				spriteID = SpriteID.SideIcons.SPELLBOOK_LUNAR;
				name = "Lunar spellbook";
				break;
			case 1:
				spriteID = SpriteID.SideIcons.SPELLBOOK_ANCIENT_MAGICKS;
				name = "Ancient spellbook";
				break;
			default:
				spriteID = SpriteID.SideIcons.MAGIC;
				name = "Standard spellbook";
		}
		setSprite(spellbookLabel, spriteID);
		spellbookLabel.setToolTipText(name);
	}

	private void setSprite(JLabel label, int spriteID)
	{
		label.putClientProperty(SPRITE_KEY, spriteID);
		spriteManager.getSpriteAsync(spriteID, 0, img -> SwingUtilities.invokeLater(() -> {
			if (Integer.valueOf(spriteID).equals(label.getClientProperty(SPRITE_KEY)))
				label.setIcon(new ImageIcon(ImageUtil.resizeImage(img, STAT_ICON, STAT_ICON)));
		}));
	}

	private static JLabel statLabel(String tooltip)
	{
		JLabel label = new JLabel();
		label.setFont(TsgHubTheme.plainFont());
		label.setForeground(TsgHubTheme.TEXT);
		label.setIconTextGap(3);
		label.setToolTipText(tooltip);
		return label;
	}

	private static JPanel row()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
