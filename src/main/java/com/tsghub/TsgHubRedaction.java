package com.tsghub;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class TsgHubRedaction
{
	private static final Pattern PM_PREFIX = Pattern.compile("^(?:\\[[^\\]]*\\] ?)?(?:From|To) [^:]{1,20}:");
	private static final Pattern LOGIN_NOTICE = Pattern.compile(" has logged (?:in|out)\\.?$");
	private static final Set<ChatMessageType> PRIVATE = EnumSet.of(ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.MODPRIVATECHAT, ChatMessageType.LOGINLOGOUTNOTIFICATION);

	private TsgHubRedaction()
	{
	}

	static List<Rectangle> privateAreas(Client client)
	{
		List<Rectangle> out = new ArrayList<>();
		for (Map.Entry<Rectangle, String> line : lines(client.getWidget(InterfaceID.PmChat.CONTAINER), new ArrayList<>())) out.add(line.getKey());
		Widget chat = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
		if (chat == null || chat.isHidden()) return out;
		List<String> bodies = new ArrayList<>();
		for (MessageNode node : client.getMessages())
		{
			if (node != null && PRIVATE.contains(node.getType())) bodies.add(plain(node.getValue()));
		}
		Rectangle view = chat.getBounds();
		for (Rectangle row : privateRows(lines(chat, new ArrayList<>()), bodies))
		{
			Rectangle visible = row.intersection(view);
			if (!visible.isEmpty()) out.add(visible);
		}
		return out;
	}

	private static List<Map.Entry<Rectangle, String>> lines(Widget widget, List<Map.Entry<Rectangle, String>> out)
	{
		if (widget == null || widget.isHidden()) return out;
		String text = plain(widget.getText());
		if (!text.isEmpty()) out.add(new AbstractMap.SimpleImmutableEntry<>(widget.getBounds(), text));
		for (Widget[] children : new Widget[][] { widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren() })
		{
			if (children != null) for (Widget child : children) lines(child, out);
		}
		return out;
	}

	static List<Rectangle> privateRows(List<Map.Entry<Rectangle, String>> lines, Collection<String> bodies)
	{
		Set<Integer> rows = new HashSet<>();
		for (Map.Entry<Rectangle, String> line : lines) if (privateLine(line.getValue(), bodies)) rows.add(line.getKey().y);
		List<Rectangle> out = new ArrayList<>();
		for (Map.Entry<Rectangle, String> line : lines) if (rows.contains(line.getKey().y)) out.add(line.getKey());
		return out;
	}

	static boolean privateLine(String text, Collection<String> bodies)
	{
		if (PM_PREFIX.matcher(text).find() || LOGIN_NOTICE.matcher(text).find()) return true;
		return text.length() >= 3 && bodies.stream().anyMatch(body -> body.contains(text));
	}

	static BufferedImage blackOut(BufferedImage image, List<Rectangle> areas, int canvasWidth, int canvasHeight)
	{
		double sx = canvasWidth > 0 ? image.getWidth() / (double) canvasWidth : 1;
		double sy = canvasHeight > 0 ? image.getHeight() / (double) canvasHeight : 1;
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLACK);
		for (Rectangle area : areas)
		{
			int x = (int) Math.floor(area.x * sx);
			int y = (int) Math.floor(area.y * sy);
			Rectangle scaled = new Rectangle(x, y, (int) Math.ceil((area.x + area.width) * sx) - x, (int) Math.ceil((area.y + area.height) * sy) - y);
			scaled.grow(1, 1);
			g.fill(scaled);
		}
		g.dispose();
		return image;
	}

	private static String plain(String text)
	{
		return text == null ? "" : Text.removeTags(text).replace(' ', ' ').trim();
	}
}
