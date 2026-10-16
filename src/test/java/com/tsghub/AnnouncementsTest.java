package com.tsghub;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class AnnouncementsTest
{
	private static JsonArray list(String json)
	{
		return new JsonParser().parse(json).getAsJsonArray();
	}

	@Test
	public void unreadSkipsReadIds()
	{
		JsonArray announcements = list("[{\"id\":\"a\",\"title\":\"A\"},{\"id\":\"b\",\"title\":\"B\"},{\"id\":\"c\",\"title\":\"C\"}]");
		List<JsonObject> unread = TsgHubAnnouncements.unread(announcements, new HashSet<>(Arrays.asList("b")));
		assertEquals(2, unread.size());
		assertEquals("A", TsgHubJson.str(unread.get(0), "title"));
		assertEquals("C", TsgHubJson.str(unread.get(1), "title"));
	}

	@Test
	public void chatLinesCapAndSummarize()
	{
		JsonArray announcements = list("[{\"id\":\"1\",\"title\":\"One\"},{\"id\":\"2\",\"title\":\"Two\"},{\"id\":\"3\",\"title\":\"Three\"},{\"id\":\"4\",\"title\":\"Four\"},{\"id\":\"5\",\"title\":\"Five\"}]");
		List<String> lines = TsgHubAnnouncements.chatLines(TsgHubAnnouncements.unread(announcements, Collections.emptySet()));
		assertEquals(Arrays.asList("One", "Two", "Three", "+2 more announcements in the TSG Hub sidebar."), lines);
		assertEquals(Collections.singletonList("One"), TsgHubAnnouncements.chatLines(TsgHubAnnouncements.unread(list("[{\"id\":\"1\",\"title\":\"One\"}]"), Collections.emptySet())));
	}

	@Test
	public void pollsAreMarkedInChatAndReadBack()
	{
		JsonArray announcements = list("[{\"id\":\"1\",\"title\":\"Which day?\",\"poll\":{\"options\":[],\"myVote\":[2,0]}},{\"id\":\"2\",\"title\":\"Plain\"}]");
		List<JsonObject> rows = TsgHubAnnouncements.unread(announcements, Collections.emptySet());
		assertEquals(Arrays.asList("Which day?", "Plain"), TsgHubAnnouncements.chatLines(rows));
		assertEquals(Arrays.asList(2, 0), TsgHubAnnouncements.myVote(TsgHubAnnouncements.poll(rows.get(0))));
		assertEquals(null, TsgHubAnnouncements.poll(rows.get(1)));
	}

	@Test
	public void pollLinesTrimAndDropDuplicates()
	{
		assertEquals(Arrays.asList("Fri", "Sat"), TsgHubNewsEditor.pollLines(" Fri \n\nSat\nfri\n"));
	}

	@Test
	public void draftExpiryReadsHoursOrDays()
	{
		assertEquals(6, TsgHubNewsEditor.draftHours(new JsonParser().parse("{\"expiresInHours\":6}").getAsJsonObject()));
		assertEquals(72, TsgHubNewsEditor.draftHours(new JsonParser().parse("{\"expiresInDays\":3}").getAsJsonObject()));
		assertEquals("6 hours", TsgHubNewsEditor.hours(6));
		assertEquals("3 days", TsgHubNewsEditor.hours(72));
	}

	@Test
	public void publishedDropsDraftsAndExpired()
	{
		JsonArray published = TsgHubAnnouncements.published(list("[{\"id\":\"d\",\"draft\":true},{\"id\":\"a\"},{\"id\":\"x\",\"expired\":true}]"));
		assertEquals(1, published.size());
		assertEquals("a", TsgHubJson.str(published.get(0).getAsJsonObject(), "id"));
	}

	@Test
	public void linkifyWrapsUrlsAndEscapesText()
	{
		assertEquals("See <a href='https://x.com/a?b=1&amp;c=2' style='color:#a98bf0'>x.com/a?b=1&amp;c=2</a>.<br>&lt;b&gt;",
			TsgHubHtml.linkify("See https://x.com/a?b=1&c=2.\n<b>"));
		assertEquals("javascript:alert(1)", TsgHubHtml.linkify("javascript:alert(1)"));
		assertEquals("discord.com/channels/...", TsgHubHtml.linkText("https://discord.com/channels/123456/789012/345678"));
	}

	@Test
	public void postHtmlFormatsBulletsAndParagraphs()
	{
		assertEquals("<p style='margin:0;margin-top:0px'>Intro</p>"
			+ "<ul style='margin-left:10px;margin-bottom:0;margin-top:4px'><li><b>Events:</b> boards</li><li>a &amp; b</li></ul>"
			+ "<p style='margin:0;margin-top:4px'>Line one<br>Line two</p>",
			TsgHubHtml.postHtml("Intro\n\n- Events: boards\n* a & b\n\nLine one\nLine two"));
		assertEquals("<ul style='margin-left:10px;margin-bottom:0;margin-top:0px'><li><a href='https://x.io' style='color:" + net.runelite.client.util.ColorUtil.toHexColor(TsgHubTheme.ACCENT) + "'>x.io</a></li></ul>",
			TsgHubHtml.postHtml("- https://x.io"));
	}

	@Test
	public void postHtmlFormatsMarkdownSubset()
	{
		String accent = net.runelite.client.util.ColorUtil.toHexColor(TsgHubTheme.ACCENT);
		assertEquals("<p style='margin:0;margin-top:0px'><b>bold</b> <i>it</i> <i>it</i> <u>under</u> <strike>gone</strike> snake_case_name 2*3*4</p>",
			TsgHubHtml.postHtml("**bold** *it* _it_ __under__ ~~gone~~ snake_case_name 2*3*4"));
		assertEquals("<p style='margin:0;margin-top:0px'><font size='+1'><b>Title</b></font></p><p style='margin:0;margin-top:6px'><b>Sub</b></p>",
			TsgHubHtml.postHtml("# Title\n## Sub"));
		assertEquals("<ol style='margin-left:16px;margin-bottom:0;margin-top:0px'><li>one</li><li><a href='https://x.io/a_b' style='color:" + accent + "'>the <b>site</b></a></li></ol>",
			TsgHubHtml.postHtml("1. one\n2) [the **site**](https://x.io/a_b)"));
		assertEquals("<p style='margin:0;margin-top:0px'>&lt;b&gt;no&lt;/b&gt;</p>", TsgHubHtml.postHtml("<b>no</b>"));
	}
}
