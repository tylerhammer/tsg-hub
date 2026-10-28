package com.tsghub;

import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@RequiredArgsConstructor
final class TsgHubTeamColors
{
	private final TsgHubConfig config;
	private final Runnable changed;
	private volatile Map<String, Color> colors = Collections.emptyMap();

	void apply(JsonObject teamColors)
	{
		Map<String, Color> next = parse(teamColors);
		if (next.equals(colors)) return;
		colors = next;
		changed.run();
	}

	static Map<String, Color> parse(JsonObject teamColors)
	{
		Map<String, Color> out = new HashMap<>();
		if (teamColors == null) return out;
		for (String name : teamColors.keySet())
		{
			String hex = TsgHubJson.str(teamColors, name);
			if (hex.matches("#[0-9a-fA-F]{6}")) out.put(PlayerNames.key(name), ColorUtil.fromHex(hex));
		}
		return out;
	}

	Color colorOf(String name)
	{
		return config.teamNameColors() && name != null ? colors.get(PlayerNames.key(Text.removeTags(name))) : null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.CLAN_CHAT) return;
		Color color = colorOf(event.getName());
		if (color == null) return;
		MessageNode node = event.getMessageNode();
		node.setName(ColorUtil.wrapWithColorTag(node.getName(), color));
	}
}
