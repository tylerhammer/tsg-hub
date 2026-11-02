package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class UsageTest
{
	private final AtomicBoolean sharing = new AtomicBoolean(true);
	private final AtomicReference<JsonObject> who = new AtomicReference<>(identity());
	private final List<JsonObject> sent = new ArrayList<>();
	private final TsgHubUsage usage = new TsgHubUsage(sharing::get, who::get, sent::add, () -> Arrays.asList("setting.bingo-overlay"));

	private static JsonObject identity()
	{
		JsonObject body = new JsonObject();
		body.addProperty("clanName", "TSGaming");
		body.addProperty("accountHash", "123");
		return body;
	}

	@Test
	public void sendsCountsAndSettingsOnce()
	{
		usage.count("view.drops");
		usage.count("view.drops");
		usage.count("button.refresh");
		usage.flush();
		assertEquals(1, sent.size());
		JsonObject body = sent.get(0);
		assertEquals("123", body.get("accountHash").getAsString());
		assertEquals("TSGaming", body.get("clanName").getAsString());
		JsonObject features = body.getAsJsonObject("features");
		assertEquals(2, features.get("view.drops").getAsInt());
		assertEquals(1, features.get("button.refresh").getAsInt());
		assertEquals(1, features.get("setting.bingo-overlay").getAsInt());

		usage.flush();
		assertEquals("nothing new, nothing sent", 1, sent.size());
		usage.count("view.news");
		usage.flush();
		assertEquals(2, sent.size());
		assertTrue(!sent.get(1).getAsJsonObject("features").has("setting.bingo-overlay"));
	}

	@Test
	public void countsNothingWithSharingOff()
	{
		sharing.set(false);
		usage.count("view.drops");
		sharing.set(true);
		usage.flush();
		assertEquals(1, sent.size());
		assertTrue(!sent.get(0).getAsJsonObject("features").has("view.drops"));
	}

	@Test
	public void waitsForLoginBeforeSending()
	{
		who.set(null);
		usage.count("view.drops");
		usage.flush();
		assertEquals(0, sent.size());
		who.set(identity());
		usage.flush();
		assertEquals(2, sent.get(0).getAsJsonObject("features").size());
	}

	@Test
	public void splitsLargeReportsAndCapsCounts()
	{
		for (int i = 0; i < TsgHubUsage.MAX_FEATURES + 5; i++) usage.count("view.f" + i);
		for (int i = 0; i < TsgHubUsage.MAX_COUNT + 5; i++) usage.count("button.refresh");
		usage.flush();
		assertEquals(2, sent.size());
		assertEquals(TsgHubUsage.MAX_FEATURES, sent.get(0).getAsJsonObject("features").size());
		int total = 0;
		for (JsonObject body : sent)
		{
			JsonObject features = body.getAsJsonObject("features");
			total += features.size();
			if (features.has("button.refresh")) assertEquals(TsgHubUsage.MAX_COUNT, features.get("button.refresh").getAsInt());
		}
		assertEquals(TsgHubUsage.MAX_FEATURES + 7, total);
	}

	@Test
	public void namesMatchTheServerPattern()
	{
		assertEquals("setting.share-drop-screenshots", TsgHubUsage.settingName("shareDropScreenshots"));
		assertEquals("view.competition-preview", TsgHubUsage.viewName(TsgHubSidebarPanel.View.COMPETITION_PREVIEW));
		for (String name : TsgHubUsage.enabledSettings(new TsgHubConfig() {}))
			assertTrue(name, name.matches("[a-z][a-z0-9._-]{0,63}") && !name.contains("data-sharing"));
	}
}
