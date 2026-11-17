package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
	public void publishedDropsDraftsAndExpired()
	{
		JsonArray published = TsgHubAnnouncements.published(list("[{\"id\":\"d\",\"draft\":true},{\"id\":\"a\"},{\"id\":\"x\",\"expired\":true}]"));
		assertEquals(1, published.size());
		assertEquals("a", TsgHubJson.str(published.get(0).getAsJsonObject(), "id"));
	}

	@Test
	public void newsImagesOnlyLoadFromTheHub()
	{
		assertTrue(TsgHubApi.isNewsImage("/v1/announcements/images/aB3_-x"));
		assertFalse(TsgHubApi.isNewsImage("https://evil.example/x.png"));
		assertFalse(TsgHubApi.isNewsImage("/v1/announcements/images/../me"));
		assertFalse(TsgHubApi.isNewsImage("//evil.example/v1/announcements/images/a"));
	}

	@Test
	public void galleryFitsPhotosWhole()
	{
		JsonObject wide = new JsonParser().parse("{\"url\":\"/v1/announcements/images/a\",\"width\":1600,\"height\":900}").getAsJsonObject();
		JsonObject tall = new JsonParser().parse("{\"url\":\"/v1/announcements/images/b\",\"width\":400,\"height\":1200}").getAsJsonObject();
		assertEquals(225, TsgHubNewsGallery.viewHeight(Collections.singletonList(new TsgHubNewsGallery.Photo(wide)), 400));
		assertEquals(TsgHubNewsGallery.MAX_VIEW_H, TsgHubNewsGallery.viewHeight(Arrays.asList(new TsgHubNewsGallery.Photo(wide), new TsgHubNewsGallery.Photo(tall)), 400));
		assertEquals(new java.awt.Rectangle(140, 0, 120, 360), TsgHubNewsGallery.contain(400, 1200, 400, 360));
		assertEquals(58, TsgHubNewsGallery.thumbWidth(2, 400));
		assertEquals(36, TsgHubNewsGallery.thumbWidth(10, 400));
	}

	@Test
	public void homeShowsPinnedPollsUntilVoted()
	{
		JsonArray announcements = list("[{\"id\":\"1\",\"pinned\":true,\"pollOnly\":true,\"poll\":{\"options\":[]}},{\"id\":\"2\",\"pollOnly\":true,\"poll\":{\"options\":[]}},{\"id\":\"3\",\"pinned\":true,\"poll\":{\"options\":[]}},{\"id\":\"4\",\"pinned\":true,\"pollOnly\":true},{\"id\":\"5\",\"pinned\":true,\"pollOnly\":true,\"poll\":{\"options\":[],\"myVote\":[0]}}]");
		List<JsonObject> home = TsgHubAnnouncements.homePolls(announcements);
		assertEquals(1, home.size());
		assertEquals("1", TsgHubJson.str(home.get(0), "id"));
	}
}
