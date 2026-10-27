package com.tsghub;

import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@RequiredArgsConstructor
final class TsgHubTeamColors
{
	private static final Pattern WORLD = Pattern.compile("W\\d+");

	private final Client client;
	private final ClientThread clientThread;
	private final TsgHubConfig config;
	private final Map<Widget, Integer> original = new IdentityHashMap<>();
	private volatile Map<String, Color> colors = Collections.emptyMap();

	void apply(JsonObject teamColors)
	{
		Map<String, Color> next = parse(teamColors);
		if (next.equals(colors)) return;
		colors = next;
		clientThread.invoke(this::repaint);
	}

	void shutDown()
	{
		colors = Collections.emptyMap();
		clientThread.invoke(this::restore);
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

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.CLAN_SIDEPANEL_DRAW) return;
		original.clear();
		paint();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("tsghub".equals(event.getGroup()) && "teamNameColors".equals(event.getKey())) clientThread.invoke(this::repaint);
	}

	private void repaint()
	{
		restore();
		paint();
	}

	private void restore()
	{
		original.forEach(Widget::setTextColor);
		original.clear();
	}

	private void paint()
	{
		Widget list = client.getWidget(InterfaceID.ClansSidepanel.PLAYERLIST);
		Widget[] rows = list == null ? null : list.getDynamicChildren();
		if (rows == null) return;
		for (Widget row : rows)
		{
			String text = row.getText();
			if (text == null || text.isEmpty() || WORLD.matcher(text).matches()) continue;
			Color color = colorOf(text);
			if (color == null) continue;
			original.putIfAbsent(row, row.getTextColor());
			row.setTextColor(color.getRGB() & 0xFFFFFF);
		}
	}
}
