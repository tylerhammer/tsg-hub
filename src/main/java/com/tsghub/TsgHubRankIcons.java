package com.tsghub;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.game.ChatIconManager;

final class TsgHubRankIcons
{
	private final ChatIconManager chatIcons;
	private final Map<Integer, BufferedImage> icons = new ConcurrentHashMap<>();

	TsgHubRankIcons(ChatIconManager chatIcons)
	{
		this.chatIcons = chatIcons;
	}

	void load(ClanTitle title)
	{
		if (title == null || icons.containsKey(title.getId())) return;
		BufferedImage icon = chatIcons.getRankImage(title);
		if (icon != null) icons.put(title.getId(), icon);
	}

	BufferedImage get(int titleId)
	{
		return icons.get(titleId);
	}
}
