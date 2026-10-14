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
		assertEquals("A", TsgHubUi.str(unread.get(0), "title"));
		assertEquals("C", TsgHubUi.str(unread.get(1), "title"));
	}

	@Test
	public void chatLinesCapAndSummarize()
	{
		JsonArray announcements = list("[{\"id\":\"1\",\"title\":\"One\"},{\"id\":\"2\",\"title\":\"Two\"},{\"id\":\"3\",\"title\":\"Three\"},{\"id\":\"4\",\"title\":\"Four\"},{\"id\":\"5\",\"title\":\"Five\"}]");
		List<String> lines = TsgHubAnnouncements.chatLines(TsgHubAnnouncements.unread(announcements, Collections.emptySet()));
		assertEquals(Arrays.asList("Clan announcement: One", "Clan announcement: Two", "Clan announcement: Three", "+2 more announcements in the TSG Hub sidebar."), lines);
		assertEquals(Collections.singletonList("Clan announcement: One"), TsgHubAnnouncements.chatLines(TsgHubAnnouncements.unread(list("[{\"id\":\"1\",\"title\":\"One\"}]"), Collections.emptySet())));
	}

	@Test
	public void publishedDropsDrafts()
	{
		JsonArray published = TsgHubAnnouncements.published(list("[{\"id\":\"d\",\"draft\":true},{\"id\":\"a\"}]"));
		assertEquals(1, published.size());
		assertEquals("a", TsgHubUi.str(published.get(0).getAsJsonObject(), "id"));
	}

	@Test
	public void linkifyWrapsUrlsAndEscapesText()
	{
		assertEquals("See <a href='https://x.com/a?b=1&amp;c=2' style='color:#a98bf0'>x.com/a?b=1&amp;c=2</a>.<br>&lt;b&gt;",
			TsgHubUi.linkify("See https://x.com/a?b=1&c=2.\n<b>"));
		assertEquals("javascript:alert(1)", TsgHubUi.linkify("javascript:alert(1)"));
		assertEquals("discord.com/channels/...", TsgHubUi.linkText("https://discord.com/channels/123456/789012/345678"));
	}
}
