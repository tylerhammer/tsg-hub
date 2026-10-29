package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import java.awt.Color;
import javax.swing.text.html.HTML;

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

	private static final java.util.regex.Pattern URL = java.util.regex.Pattern.compile("https?://[^\\s<>\"']+");

	private static final int LINK_TEXT_MAX = 24;
	private static final java.util.regex.Pattern BULLET = java.util.regex.Pattern.compile("[-*•]\\s+(.+)");
	private static final java.util.regex.Pattern NUMBERED = java.util.regex.Pattern.compile("\\d{1,2}[.)]\\s+(.+)");
	private static final java.util.regex.Pattern HEADING = java.util.regex.Pattern.compile("(#{1,3})\\s+(.+)");
	private static final java.util.regex.Pattern RULE = java.util.regex.Pattern.compile("-{3,}|\\*{3,}|_{3,}");
	private static final java.util.regex.Pattern LINK = java.util.regex.Pattern.compile("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)|(https?://[^\\s<>\"']+)");
	private static final java.util.regex.Pattern LEAD_IN = java.util.regex.Pattern.compile("((?:\\*\\*)?[^:/*]{1,28}(?:\\*\\*)?:)(\\s.*)");
	private static final String LI = "<li style='margin-top:3px'>";

	// Escapes plain text for HTML, keeps line breaks, and turns http(s) URLs into links. Long URLs show
	// shortened: Swing can't wrap inside one, and a long one widens the whole label past its wrap width.
	static String linkify(String text)
	{
		String raw = text == null ? "" : text;
		StringBuilder out = new StringBuilder();
		java.util.regex.Matcher matcher = URL.matcher(raw);
		int last = 0;
		while (matcher.find())
		{
			String url = matcher.group().replaceAll("[.,;:!?)]+$", "");
			out.append(escape(raw.substring(last, matcher.start())));
			out.append("<a href='").append(escape(url)).append("' style='color:").append(toHexColor(ACCENT)).append("'>")
				.append(escape(linkText(url))).append("</a>");
			last = matcher.start() + url.length();
		}
		out.append(escape(raw.substring(last)));
		return out.toString().replace("\n", "<br>");
	}

	static String postHtml(String text)
	{
		StringBuilder out = new StringBuilder();
		StringBuilder block = new StringBuilder();
		String kind = "";
		for (String line : (text == null ? "" : text).split("\n", -1))
		{
			String trimmed = line.trim();
			java.util.regex.Matcher bullet = BULLET.matcher(trimmed);
			java.util.regex.Matcher numbered = NUMBERED.matcher(trimmed);
			java.util.regex.Matcher heading = HEADING.matcher(trimmed);
			String next = trimmed.isEmpty() || heading.matches() || RULE.matcher(trimmed).matches() ? ""
				: bullet.matches() ? "ul" : numbered.matches() ? "ol" : trimmed.startsWith(">") ? "quote" : "p";
			if (!next.equals(kind)) flushBlock(out, block, kind);
			kind = next;
			if (heading.matches())
			{
				int level = heading.group(1).length();
				int top = out.length() == 0 ? 0 : 12;
				String size = level == 1 ? "+2" : level == 2 ? "+1" : "";
				out.append("<p style='margin:0;margin-top:").append(top).append("px;color:").append(toHexColor(ACCENT)).append("'>")
					.append(size.isEmpty() ? "" : "<font size='" + size + "'>").append("<b>").append(inline(heading.group(2))).append("</b>")
					.append(size.isEmpty() ? "" : "</font>").append("</p>");
			}
			else if (RULE.matcher(trimmed).matches()) out.append("<p style='margin:0;margin-top:6px;border-top:1px solid ").append(toHexColor(MUTED.darker())).append("'></p>");
			else if (next.equals("ul")) block.append(LI).append(leadIn(bullet.group(1))).append("</li>");
			else if (next.equals("ol")) block.append(LI).append(leadIn(numbered.group(1))).append("</li>");
			else if (!next.isEmpty())
			{
				if (block.length() > 0) block.append("<br>");
				block.append(inline(next.equals("quote") ? trimmed.substring(1).trim() : trimmed));
			}
		}
		flushBlock(out, block, kind);
		return out.toString();
	}

	private static void flushBlock(StringBuilder out, StringBuilder block, String kind)
	{
		if (block.length() == 0) return;
		String top = "margin-top:" + (out.length() == 0 ? 0 : 8) + "px";
		if (kind.equals("ul") || kind.equals("ol"))
			out.append("<").append(kind).append(" style='margin-left:").append(kind.equals("ol") ? 16 : 10).append("px;margin-bottom:0;").append(top).append("'>")
				.append(block).append("</").append(kind).append(">");
		else if (kind.equals("quote"))
			out.append("<p style='margin:0;").append(top).append(";margin-left:4px;padding-left:6px;border-left:2px solid ").append(toHexColor(MUTED))
				.append(";color:").append(toHexColor(MUTED)).append("'>").append(block).append("</p>");
		else out.append("<p style='margin:0;").append(top).append("'>").append(block).append("</p>");
		block.setLength(0);
	}

	private static String leadIn(String item)
	{
		java.util.regex.Matcher lead = LEAD_IN.matcher(item);
		return lead.matches() ? "<b>" + inline(lead.group(1)) + "</b>" + inline(lead.group(2)) : inline(item);
	}

	private static String inline(String raw)
	{
		StringBuilder out = new StringBuilder();
		java.util.regex.Matcher matcher = LINK.matcher(raw);
		int last = 0;
		while (matcher.find())
		{
			String url = matcher.group(2) != null ? matcher.group(2) : matcher.group(3).replaceAll("[.,;:!?)]+$", "");
			String shown = matcher.group(1) != null ? emphasis(escape(matcher.group(1))) : escape(linkText(url));
			out.append(emphasis(escape(raw.substring(last, matcher.start()))));
			out.append("<a href='").append(escape(url)).append("' style='color:").append(toHexColor(ACCENT)).append("'>").append(shown).append("</a>");
			last = matcher.group(2) != null ? matcher.end() : matcher.start() + url.length();
		}
		return out.append(emphasis(escape(raw.substring(last)))).toString();
	}

	private static String emphasis(String text)
	{
		return text.replaceAll("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*", "<b>$1</b>")
			.replaceAll("__(?=\\S)(.+?)(?<=\\S)__", "<u>$1</u>")
			.replaceAll("~~(?=\\S)(.+?)(?<=\\S)~~", "<strike>$1</strike>")
			.replaceAll("(?<![*\\w])\\*(?=\\S)(.+?)(?<=\\S)\\*(?![*\\w])", "<i>$1</i>")
			.replaceAll("(?<![_\\w])_(?=\\S)(.+?)(?<=\\S)_(?![_\\w])", "<i>$1</i>");
	}

	static String linkText(String url)
	{
		String shown = url.replaceFirst("^https?://(www\\.)?", "").replaceAll("/$", "");
		return shown.length() > LINK_TEXT_MAX ? shown.substring(0, LINK_TEXT_MAX - 3) + "..." : shown;
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
		return "<table cellspacing='0' cellpadding='0' width='100%' style='margin-top:2px'><tr><td bgcolor='" + toHexColor(bar) + "' width='2'></td>"
			+ "<td bgcolor='" + toHexColor(CARD_HOVER) + "' style='padding:3px 6px'>" + inner + "</td></tr></table>";
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
