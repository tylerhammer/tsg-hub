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

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.common.base.Strings;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

class PlayerBanner extends JPanel
{
	private static final int STAT_ICON = 16;
	private static final String SPRITE_KEY = "tsghub.sprite";
	private static final int[] SPELLBOOK_SPRITES = {SpriteID.SideIcons.MAGIC, SpriteID.SideIcons.SPELLBOOK_ANCIENT_MAGICKS, SpriteID.SideIcons.SPELLBOOK_LUNAR, SpriteID.SideIcons.SPELLBOOK_ARCEUUS};
	private static final String[] SPELLBOOKS = {"Standard", "Ancient", "Lunar", "Arceuus"};

	private final SpriteManager spriteManager;
	private final JLabel nameLabel = fullTextLabel();
	private final JLabel worldLabel = new JLabel();
	private final JLabel areaLabel = fullTextLabel();
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

	PlayerBanner(PartyPlayer player, boolean displayWorld, SpriteManager spriteManager)
	{
		this.player = player;
		this.displayWorld = displayWorld;
		this.spriteManager = spriteManager;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		nameLabel.setFont(boldFont());
		nameLabel.setMinimumSize(new Dimension(0, 0));
		nameLabel.putClientProperty("html.disable", Boolean.TRUE);
		worldLabel.setFont(smallFont());
		worldLabel.setForeground(MUTED);
		areaLabel.setFont(smallFont());
		areaLabel.setMinimumSize(new Dimension(0, 0));
		areaLabel.putClientProperty("html.disable", Boolean.TRUE);

		JPanel stats = panel(new GridLayout(1, 4, 2, 0));
		for (JLabel stat : new JLabel[]{hpLabel, prayerLabel, specLabel, runLabel}) stats.add(stat);
		add(row(nameLabel, worldLabel));
		add(gap(GAP_XS));
		add(row(areaLabel, spellbookLabel));
		add(gap(GAP_S));
		add(stats);

		setSprite(prayerLabel, SpriteID.Staticons.PRAYER);
		setSprite(specLabel, SpriteID.OVERLAY_MULTIWAY);
		update(player);
	}

	void update(PartyPlayer player)
	{
		this.player = player;
		boolean online = !Strings.isNullOrEmpty(player.getUsername());
		nameLabel.setText(online ? player.getUsername() : "Not logged in");
		nameLabel.setForeground(online ? TEXT : MUTED);
		nameLabel.setToolTipText(online && player.getStats() != null ? "Combat level " + player.getStats().getCombatLevel() : null);
		updateWorld(player, displayWorld);
		refreshStats();
		updateSpellbookIcon(player.getSpellbook());
	}

	void refreshStats()
	{
		int hp = player.getSkillBoostedLevel(Skill.HITPOINTS);
		int prayer = player.getSkillBoostedLevel(Skill.PRAYER);
		hpLabel.setText(String.valueOf(hp));
		hpLabel.setForeground(levelColor(hp, player.getSkillRealLevel(Skill.HITPOINTS, false)));
		prayerLabel.setText(String.valueOf(prayer));
		prayerLabel.setForeground(levelColor(prayer, player.getSkillRealLevel(Skill.PRAYER, false)));
		specLabel.setText(player.getStats() == null ? "0" : String.valueOf(player.getStats().getSpecialPercent()));
		runLabel.setText(player.getStats() == null ? "0" : String.valueOf(player.getStats().getRunEnergy()));
	}

	void updateWorld(PartyPlayer player, boolean displayWorlds)
	{
		displayWorld = displayWorlds;
		boolean online = !Strings.isNullOrEmpty(player.getUsername());
		boolean inGame = online && player.getWorld() > 0;
		worldLabel.setText(displayWorlds && inGame ? "W" + player.getWorld() : "");
		String area = Strings.nullToEmpty(player.getArea());
		String text = !online ? "Last seen stats" : !inGame ? "Not logged in" : area;
		areaLabel.setText(text);
		areaLabel.setForeground(inGame && !area.isEmpty() ? SUCCESS : MUTED);
	}

	void setCurrentHeart(BufferedImage img)
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

	void setUsingStamIcon(boolean stamina)
	{
		if (usingStamIcon != null && usingStamIcon == stamina) return;
		usingStamIcon = stamina;
		setSprite(runLabel, stamina ? SpriteID.OrbIcon.RUN_ICON_SLOWED_DEPLETION : SpriteID.OrbIcon.RUN);
	}

	private void updateSpellbookIcon(int book)
	{
		if (book == spellbook) return;
		spellbook = book;
		int i = book >= 1 && book <= 3 ? book : 0;
		setSprite(spellbookLabel, SPELLBOOK_SPRITES[i]);
		spellbookLabel.setToolTipText(SPELLBOOKS[i] + " spellbook");
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
		JLabel label = label("", TEXT, plainFont());
		label.setIconTextGap(3);
		label.setToolTipText(tooltip);
		return label;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
