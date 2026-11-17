package com.tsghub;

import java.awt.image.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import net.runelite.api.clan.*;
import net.runelite.client.game.*;

@RequiredArgsConstructor
final class TsgHubRankIcons
{
	private final ChatIconManager chatIcons;
	private final Map<Integer, BufferedImage> icons = new ConcurrentHashMap<>();

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
