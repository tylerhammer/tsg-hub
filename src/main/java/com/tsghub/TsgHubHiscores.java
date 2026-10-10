package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Experience;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.util.ImageUtil;

final class TsgHubHiscores
{
	static final List<HiscoreSkill> SKILL_GRID = Arrays.asList(
		HiscoreSkill.ATTACK, HiscoreSkill.HITPOINTS, HiscoreSkill.MINING,
		HiscoreSkill.STRENGTH, HiscoreSkill.AGILITY, HiscoreSkill.SMITHING,
		HiscoreSkill.DEFENCE, HiscoreSkill.HERBLORE, HiscoreSkill.FISHING,
		HiscoreSkill.RANGED, HiscoreSkill.THIEVING, HiscoreSkill.COOKING,
		HiscoreSkill.PRAYER, HiscoreSkill.CRAFTING, HiscoreSkill.FIREMAKING,
		HiscoreSkill.MAGIC, HiscoreSkill.FLETCHING, HiscoreSkill.WOODCUTTING,
		HiscoreSkill.RUNECRAFT, HiscoreSkill.SLAYER, HiscoreSkill.FARMING,
		HiscoreSkill.CONSTRUCTION, HiscoreSkill.HUNTER, HiscoreSkill.SAILING);
	private static final int ICON = 16;
	private static final Map<HiscoreSkill, BufferedImage> SKILL_ICONS = new EnumMap<>(HiscoreSkill.class);

	private TsgHubHiscores()
	{
	}

	static HiscoreEndpoint endpoint(int accountType)
	{
		switch (accountType)
		{
			case 1: return HiscoreEndpoint.IRONMAN;
			case 2: return HiscoreEndpoint.ULTIMATE_IRONMAN;
			case 3: return HiscoreEndpoint.HARDCORE_IRONMAN;
			default: return HiscoreEndpoint.NORMAL;
		}
	}

	static int level(HiscoreResult result, HiscoreSkill skill)
	{
		Skill entry = result.getSkill(skill);
		return entry == null ? -1 : entry.getLevel();
	}

	static int combatLevel(HiscoreResult result)
	{
		return Experience.getCombatLevel(
			Math.max(1, level(result, HiscoreSkill.ATTACK)),
			Math.max(1, level(result, HiscoreSkill.STRENGTH)),
			Math.max(1, level(result, HiscoreSkill.DEFENCE)),
			Math.max(10, level(result, HiscoreSkill.HITPOINTS)),
			Math.max(1, level(result, HiscoreSkill.MAGIC)),
			Math.max(1, level(result, HiscoreSkill.RANGED)),
			Math.max(1, level(result, HiscoreSkill.PRAYER)));
	}

	static List<HiscoreSkill> scored(HiscoreResult result, HiscoreSkillType type)
	{
		List<HiscoreSkill> out = new ArrayList<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
			if (skill.getType() == type && level(result, skill) > 0) out.add(skill);
		out.sort(Comparator.comparing(TsgHubHiscores::sortName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static String sortName(HiscoreSkill skill)
	{
		String name = skill.getName();
		return name.regionMatches(true, 0, "The ", 0, 4) ? name.substring(4) : name;
	}

	static String subtitle(HiscoreResult result)
	{
		int total = level(result, HiscoreSkill.OVERALL);
		return "Combat " + combatLevel(result) + (total > 0 ? " · Total " + String.format("%,d", total) : "");
	}

	static JPanel skillGrid(HiscoreResult result)
	{
		JPanel grid = new JPanel(new GridLayout(0, 3, GAP_XS, GAP_XS));
		grid.setOpaque(false);
		grid.setAlignmentX(0f);
		for (HiscoreSkill skill : SKILL_GRID) grid.add(skillCell(result, skill));
		return fitHeight(grid);
	}

	private static JPanel skillCell(HiscoreResult result, HiscoreSkill skill)
	{
		JPanel cell = card();
		cell.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		int level = level(result, skill);
		JLabel label = label(level > 0 ? String.valueOf(level) : "--", level >= 99 ? WARNING : TEXT, boldFont());
		label.setIcon(skillIcon(skill));
		label.setIconTextGap(6);
		cell.add(label, BorderLayout.CENTER);
		cell.setToolTipText(skillTip(result.getSkill(skill), skill.getName()));
		return cell;
	}

	static JPanel totalRow(HiscoreResult result)
	{
		JPanel row = card();
		Skill overall = result.getSkill(HiscoreSkill.OVERALL);
		int total = overall == null ? -1 : overall.getLevel();
		JLabel left = label("Total " + (total > 0 ? String.format("%,d", total) : "--"), TEXT, boldFont());
		left.setIcon(skillIcon(HiscoreSkill.OVERALL));
		left.setIconTextGap(6);
		row.add(left, BorderLayout.CENTER);
		if (overall != null && overall.getExperience() > 0)
			row.add(label(String.format("%,d xp", overall.getExperience()), MUTED, smallFont()), BorderLayout.EAST);
		row.setToolTipText(skillTip(overall, "Overall"));
		return fitHeight(row);
	}

	static JPanel scoreRow(HiscoreResult result, HiscoreSkill skill, SpriteManager sprites)
	{
		JPanel row = card();
		row.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
		JLabel name = shrinkable(label(skill.getName(), TEXT, smallFont()));
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ICON, ICON));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (skill.getSpriteId() > 0)
		{
			sprites.getSpriteAsync(skill.getSpriteId(), 0, sprite -> SwingUtilities.invokeLater(() ->
				icon.setIcon(new ImageIcon(ImageUtil.resizeImage(ImageUtil.resizeCanvas(sprite, 25, 25), ICON, ICON)))));
		}
		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(label(String.format("%,d", level(result, skill)), ACCENT, smallFont()), BorderLayout.EAST);
		row.setToolTipText(skillTip(result.getSkill(skill), skill.getName()));
		return fitHeight(row);
	}

	private static String skillTip(Skill skill, String name)
	{
		if (skill == null || skill.getRank() <= 0) return "<html><b>" + escape(name) + "</b><br>Not ranked</html>";
		String detail = skill.getExperience() >= 0 ? "XP: " + String.format("%,d", skill.getExperience()) + "<br>" : "";
		return "<html><b>" + escape(name) + "</b><br>" + detail + "Rank: " + String.format("%,d", skill.getRank()) + "</html>";
	}

	private static synchronized ImageIcon skillIcon(HiscoreSkill skill)
	{
		BufferedImage image = SKILL_ICONS.computeIfAbsent(skill, s -> ImageUtil.loadImageResource(TsgHubHiscores.class, "/skill_icons_small/" + s.name().toLowerCase() + ".png"));
		return image == null ? null : new ImageIcon(image);
	}
}
