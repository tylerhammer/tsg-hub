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
import com.tsghub.group.GroupViewSettings;
import com.tsghub.group.data.PartyPlayer;
import com.tsghub.group.ui.equipment.PlayerEquipmentPanel;
import com.tsghub.group.ui.prayer.PlayerPrayerPanel;
import com.tsghub.group.ui.skills.PlayerSkillsPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

public class PlayerPanel extends JPanel
{
	private static final int VENOM_THRESHOLD = 1000000;
	private static final BufferedImage HEART_DISEASE = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.DISEASE_HEART);
	private static final BufferedImage HEART_POISON = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.POISON_HEART);
	private static final BufferedImage HEART_VENOM = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.VENOM_HEART);

	private enum Tab
	{
		ITEMS, GEAR, PRAYER, SKILLS
	}

	@Getter
	private PartyPlayer player;
	private final GroupViewSettings config;
	@Getter
	private final PlayerBanner banner;
	private final PlayerInventoryPanel inventoryPanel;
	private final PlayerEquipmentPanel equipmentPanel;
	private final PlayerSkillsPanel skillsPanel;
	private final PlayerPrayerPanel prayersPanel;
	private final JPanel details = new JPanel();
	private Tab selected = Tab.ITEMS;
	private boolean self;
	private boolean hovered;

	@Getter
	@Setter
	private boolean showInfo;

	public PlayerPanel(PartyPlayer player, GroupViewSettings config, SpriteManager spriteManager, ItemManager itemManager)
	{
		this.player = player;
		this.config = config;
		this.showInfo = config.autoExpandMembers();
		this.banner = new PlayerBanner(player, config.displayPlayerWorlds(), spriteManager);
		this.inventoryPanel = new PlayerInventoryPanel(player.getInventory(), player.getRunesInPouch(), itemManager);
		this.equipmentPanel = new PlayerEquipmentPanel(player.getEquipment(), player.getQuiver(), spriteManager, itemManager);
		this.skillsPanel = new PlayerSkillsPanel(player, config.displayVirtualLevels(), spriteManager);
		this.prayersPanel = new PlayerPrayerPanel(player, spriteManager);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		banner.setAlignmentX(LEFT_ALIGNMENT);
		add(banner);
		buildDetails();
		details.setAlignmentX(LEFT_ALIGNMENT);
		add(details);

		banner.setToolTipText("Show inventory, gear, prayers and skills");
		addBannerListener(new MouseAdapter()
		{
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger() || e.isControlDown() || !e.getComponent().contains(e.getPoint())) return;
				showInfo = !showInfo;
				updatePanel();
				if (showInfo) updatePlayerData(PlayerPanel.this.player, false);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				hovered = true;
				paintState();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hovered = false;
				paintState();
			}
		});
		setCursorDeep(banner, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		paintState();
		updatePanel();
	}

	public void setSelf(boolean self)
	{
		this.self = self;
		paintState();
	}

	public void addBannerListener(MouseListener listener)
	{
		addListenerDeep(banner, listener);
	}

	private void buildDetails()
	{
		details.setOpaque(false);
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

		JComponent rule = new JPanel();
		rule.setBackground(PartyStyle.DIVIDER);
		rule.setMinimumSize(new Dimension(0, 1));
		rule.setPreferredSize(new Dimension(10, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

		JComponent[] contents = {inventoryPanel, equipmentPanel, prayersPanel, skillsPanel};
		JPanel display = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension size = super.getPreferredSize();
				for (JComponent content : contents) size.height = Math.max(size.height, content.getPreferredSize().height);
				return size;
			}
		};
		display.setOpaque(false);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		tabs.setOpaque(false);
		addTab(tabs, "Items", inventoryPanel, Tab.ITEMS);
		addTab(tabs, "Gear", equipmentPanel, Tab.GEAR);
		addTab(tabs, "Prayer", prayersPanel, Tab.PRAYER);
		addTab(tabs, "Skills", skillsPanel, Tab.SKILLS);

		for (JComponent c : new JComponent[]{rule, tabs, display}) c.setAlignmentX(LEFT_ALIGNMENT);
		details.add(Box.createVerticalStrut(TsgHubTheme.GAP_M));
		details.add(rule);
		details.add(Box.createVerticalStrut(TsgHubTheme.GAP_XS));
		details.add(tabs);
		details.add(Box.createVerticalStrut(TsgHubTheme.GAP_S));
		details.add(display);
	}

	private void addTab(MaterialTabGroup group, String name, JComponent content, Tab tab)
	{
		MaterialTab materialTab = new MaterialTab(name, group, content);
		materialTab.setFont(TsgHubTheme.smallFont());
		materialTab.setOnSelectEvent(() -> {
			selected = tab;
			updatePlayerData(player, false);
			return true;
		});
		group.addTab(materialTab);
		if (tab == selected) group.select(materialTab);
	}

	public void updatePlayerData(PartyPlayer newPlayer, boolean hasBreakingBannerChange)
	{
		player = newPlayer;
		banner.update(player);

		BufferedImage heart = null;
		if (player.getPoison() >= VENOM_THRESHOLD) heart = HEART_VENOM;
		else if (player.getPoison() > 0) heart = HEART_POISON;
		else if (player.getDisease() > 0) heart = HEART_DISEASE;
		banner.setCurrentHeart(heart);
		banner.setUsingStamIcon(player.getStamina() > 0);

		if (!showInfo) return;
		switch (selected)
		{
			case ITEMS:
				inventoryPanel.updateInventory(player.getInventory(), player.getRunesInPouch());
				break;
			case GEAR:
				equipmentPanel.update(player.getEquipment(), player.getQuiver());
				break;
			case PRAYER:
				if (player.getPrayers() != null) prayersPanel.update(player.getPrayers());
				break;
			case SKILLS:
				if (player.getStats() != null) skillsPanel.update(player, config.displayVirtualLevels());
				break;
		}
	}

	public void updatePanel()
	{
		details.setVisible(showInfo);
		banner.setToolTipText(showInfo ? "Hide details" : "Show inventory, gear, prayers and skills");
		revalidate();
		repaint();
	}

	public void updateDisplayVirtualLevels()
	{
		if (player.getStats() != null) skillsPanel.update(player, config.displayVirtualLevels());
	}

	public void updateDisplayPlayerWorlds()
	{
		banner.updateWorld(player, config.displayPlayerWorlds());
	}

	private void paintState()
	{
		Color background = self ? (hovered ? TsgHubTheme.SELF_CARD_HOVER : TsgHubTheme.SELF_CARD) : (hovered ? TsgHubTheme.CARD_HOVER : TsgHubTheme.CARD);
		setBackground(background);
		setBorder(self ? TsgHubTheme.selfBorder() : TsgHubTheme.cardBorder());
		repaint();
	}

	private static void addListenerDeep(Component component, MouseListener listener)
	{
		component.addMouseListener(listener);
		if (component instanceof Container)
			for (Component child : ((Container) component).getComponents()) addListenerDeep(child, listener);
	}

	private static void setCursorDeep(Component component, Cursor cursor)
	{
		component.setCursor(cursor);
		if (component instanceof Container)
			for (Component child : ((Container) component).getComponents()) setCursorDeep(child, cursor);
	}
}
