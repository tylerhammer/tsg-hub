package com.tsghub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JTextField;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

final class TsgHubBossPicker extends TsgHubSearchField
{
	static final List<String> BOSSES = bossNames();
	private static final String DEFAULT_HINT = "Type to search bosses, or enter any name shown in its kill count message.";

	private final JLabel hint = TsgHubUi.caption(DEFAULT_HINT);
	private final Runnable onChosen;

	TsgHubBossPicker(String caption, Runnable onChosen)
	{
		super(new JTextField());
		this.onChosen = onChosen;
		add(TsgHubUi.caption(caption));
		add(Box.createVerticalStrut(TsgHubTheme.GAP_XS));
		addSearch("e.g. Vorkath");
		add(hint);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
	}

	@Override
	boolean isExact(String query)
	{
		return BOSSES.stream().anyMatch(b -> b.equalsIgnoreCase(query));
	}

	@Override
	List<String> matches(String query)
	{
		String lower = query.toLowerCase(Locale.ROOT);
		List<String> prefix = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String boss : BOSSES)
		{
			String name = boss.toLowerCase(Locale.ROOT);
			if (name.startsWith(lower) || name.contains(" " + lower)) prefix.add(boss);
			else if (name.contains(lower)) contains.add(boss);
		}
		prefix.addAll(contains);
		return prefix;
	}

	@Override
	void resultsChanged(String query, int matches)
	{
		if (matches > 0) hint.setText("Click a boss (or press Enter) to pick it.");
		else if (!query.isEmpty() && isExact(query)) hint.setText("Counts \"" + query + "\" kill count messages.");
		else if (matches == 0 && !query.isEmpty()) hint.setText("Not in RuneLite's boss list. It will still count if this name appears in the kill count message.");
		else hint.setText(DEFAULT_HINT);
	}

	@Override
	void chosen()
	{
		if (onChosen != null) onChosen.run();
	}

	private static List<String> bossNames()
	{
		List<String> names = new ArrayList<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getType() != HiscoreSkillType.BOSS) continue;
			String name = skill.getName();
			if (name.startsWith("Chambers of Xeric") || name.startsWith("Theatre of Blood") || name.startsWith("Tombs of Amascut")) continue;
			names.add(name);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}
}
