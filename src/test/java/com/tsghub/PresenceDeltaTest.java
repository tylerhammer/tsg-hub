package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

public class PresenceDeltaTest
{
	private static JsonArray rows(String json)
	{
		return new Gson().fromJson(json, JsonArray.class);
	}

	private static JsonObject delta(String json)
	{
		return new Gson().fromJson(json, JsonObject.class);
	}

	@Test
	public void upsertsReplaceAndAppendRows()
	{
		JsonArray merged = TsgHubPresence.merge(rows("[{\"displayName\":\"Alice\",\"area\":\"Varrock\"},{\"displayName\":\"Bob\"}]"),
			delta("{\"upsert\":[{\"displayName\":\"alice\",\"area\":\"Lumbridge\"},{\"displayName\":\"Carol\"}],\"remove\":[]}"));
		assertEquals(3, merged.size());
		assertEquals("Lumbridge", TsgHubJson.str(merged.get(0).getAsJsonObject(), "area"));
		assertEquals("Carol", TsgHubJson.str(merged.get(2).getAsJsonObject(), "displayName"));
	}

	@Test
	public void removesDropRowsByName()
	{
		JsonArray merged = TsgHubPresence.merge(rows("[{\"displayName\":\"Alice\"},{\"displayName\":\"Bob\"}]"), delta("{\"upsert\":[],\"remove\":[\"Alice\"]}"));
		assertEquals(1, merged.size());
		assertEquals("Bob", TsgHubJson.str(merged.get(0).getAsJsonObject(), "displayName"));
	}

	@Test
	public void missingSectionKeepsRows()
	{
		JsonArray base = rows("[{\"displayName\":\"Alice\"}]");
		assertSame(base, TsgHubPresence.merge(base, null));
		assertEquals(1, TsgHubPresence.merge(null, delta("{\"upsert\":[{\"displayName\":\"Alice\"}]}")).size());
	}
}
