package com.tsghub;

import java.awt.*;
import java.util.List;
import javax.swing.text.html.*;
import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

final class TsgHubHtml
{
	private TsgHubHtml()
	{
	}

	static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// Swing HTML treats px as 1/72in but fonts render at 96dpi, so scale by 3/4.
	static String html(String innerHtml, int widthPx)
	{
		return "<html><body style='width:" + Math.round(widthPx * 0.75f) + "px'>" + innerHtml + "</body></html>";
	}

	private static final String[] MEDALS = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"};

	static String medals(String html)
	{
		for (int i = 0; i < MEDALS.length; i++)
			if (html.contains(MEDALS[i])) html = html.replace(MEDALS[i], "<img src='" + TsgHubHtml.class.getResource("icons/medal" + (i + 1) + ".png") + "' width='12' height='14' hspace='2'>");
		return html;
	}

	static String nameHtml(String name, String self)
	{
		return samePlayer(name, self) ? selfName(escape(name)) : escape(name);
	}

	static String tipSection(boolean first, String heading)
	{
		return "<div style='margin-top:" + (first ? 0 : 6) + "px'>" + heading + "</div>";
	}

	static String tipCard(Color bar, String inner)
	{
		boolean marked = bar != null;
		return "<table cellspacing='0' cellpadding='0' width='100%' style='margin-top:3px'><tr>" + (marked ? "<td bgcolor='" + toHexColor(bar) + "' width='2'></td>" : "")
			+ "<td bgcolor='" + toHexColor(SLOT) + "' style='padding:4px 6px'>" + inner + "</td></tr></table>";
	}

	static String tipRows(List<String[]> rows)
	{
		StringBuilder out = new StringBuilder("<table cellspacing='0' cellpadding='0' width='100%'>");
		for (String[] row : rows) out.append("<tr><td>").append(row[0]).append("</td><td align='right'>").append(tipLine(MUTED, row[1])).append("</td></tr>");
		return out.append("</table>").toString();
	}

	static String withCorner(String body, String corner)
	{
		if (corner.isEmpty()) return body;
		String side = "<span style='font-family:\"" + smallFont().getFamily() + "\"; font-size:" + smallFont().getSize() + "pt; color:" + toHexColor(MUTED) + "'>" + corner + "</span>";
		if (body.isEmpty()) return side;
		int end = firstLineEnd(body);
		String rest = body.substring(end);
		if (rest.startsWith("<br>")) rest = rest.substring(4);
		return "<table cellspacing='0' cellpadding='0' width='100%'><tr><td valign='top'>" + body.substring(0, end) + "</td>"
			+ "<td align='right' valign='top' nowrap>" + side + "</td></tr></table>" + rest;
	}

	private static int firstLineEnd(String body)
	{
		if (body.startsWith("<div")) return body.indexOf("</div>") + 6;
		int end = body.length();
		for (String mark : new String[] {"<br>", "<div", "<table"})
		{
			int at = body.indexOf(mark);
			if (at > 0) end = Math.min(end, at);
		}
		return end;
	}

	static String tipLine(Color color, String html)
	{
		return "<font color='" + toHexColor(color) + "'>" + html + "</font>";
	}
}
