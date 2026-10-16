package com.tsghub;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

@Slf4j
final class TsgHubRules
{
	final String version;
	final List<String> chat;
	final List<String> players;
	final List<String> dropKeywords;
	final List<String> petMessages;
	final List<String> dropContext;
	final List<String> groupContent;
	final int dropDelayMs;
	final int dropContextTicks;
	final int dropContextKeep;
	final int skillingDrops;
	final long skillingWindowMillis;
	final Set<Integer> pets;
	final Set<Integer> rewardInterfaces;
	final Map<String, String> bosses;
	final Map<Integer, AreaNames.Area> areas;

	private static final TsgHubRules EMPTY = new TsgHubRules(new JsonObject());
	private static volatile TsgHubRules current = EMPTY;

	private TsgHubRules(JsonObject json)
	{
		version = TsgHubJson.str(json, "version");
		chat = strings(json, "chat");
		players = strings(json, "players");
		dropKeywords = strings(json, "dropKeywords");
		petMessages = strings(json, "petMessages");
		dropContext = strings(json, "dropContext");
		groupContent = strings(json, "groupContent");
		dropDelayMs = TsgHubJson.integer(json, "dropDelayMs", 1200);
		dropContextTicks = TsgHubJson.integer(json, "dropContextTicks", 50);
		dropContextKeep = TsgHubJson.integer(json, "dropContextKeep", 16);
		JsonObject skilling = TsgHubJson.object(json, "skilling");
		skillingDrops = TsgHubJson.integer(skilling, "minDrops", 3);
		skillingWindowMillis = TsgHubJson.longValue(skilling, "windowMillis", 120_000);
		pets = ints(json, "pets");
		rewardInterfaces = ints(json, "rewardInterfaces");
		Map<String, String> bossNames = new HashMap<>();
		for (JsonObject boss : TsgHubJson.objects(TsgHubJson.array(json, "bosses")))
		{
			String name = TsgHubJson.str(boss, "name");
			for (JsonElement npc : TsgHubJson.array(boss, "npcs")) bossNames.put(npc.getAsString().trim().toLowerCase(Locale.ROOT), name);
		}
		bosses = Collections.unmodifiableMap(bossNames);
		Map<Integer, AreaNames.Area> regions = new HashMap<>();
		for (JsonObject area : TsgHubJson.objects(TsgHubJson.array(json, "areas")))
		{
			AreaNames.Type type;
			try { type = AreaNames.Type.valueOf(TsgHubJson.str(area, "type")); }
			catch (IllegalArgumentException e) { continue; }
			AreaNames.Area named = new AreaNames.Area(TsgHubJson.str(area, "name"), type);
			for (JsonElement region : TsgHubJson.array(area, "regions")) regions.putIfAbsent(region.getAsInt(), named);
		}
		areas = Collections.unmodifiableMap(regions);
	}

	static TsgHubRules get()
	{
		return current;
	}

	static boolean apply(JsonObject json)
	{
		try
		{
			current = new TsgHubRules(json);
			return true;
		}
		catch (RuntimeException e)
		{
			log.debug("TSG Hub rules rejected", e);
			return false;
		}
	}

	static void reset()
	{
		current = EMPTY;
	}

	static void loadCached(Filepath cache, Gson gson)
	{
		if (cache == null || !cache.exists()) return;
		try (Reader reader = cache.openReader())
		{
			JsonObject saved = gson.fromJson(reader, JsonObject.class);
			if (TsgHubVersion.VERSION.equals(TsgHubJson.str(saved, "plugin")) && saved.has("rules") && saved.get("rules").isJsonObject()) apply(saved.getAsJsonObject("rules"));
		}
		catch (Exception e) { log.debug("TSG Hub cached rules unreadable", e); }
	}

	static void save(JsonObject json, Filepath cache)
	{
		if (!apply(json) || cache == null) return;
		try
		{
			cache.getParent().createDirectories();
			JsonObject saved = new JsonObject();
			saved.addProperty("plugin", TsgHubVersion.VERSION);
			saved.add("rules", json);
			cache.write(saved.toString());
		}
		catch (Exception e) { log.debug("TSG Hub rules cache not written", e); }
	}

	static void loadBundled(Gson gson)
	{
		InputStream in = TsgHubRules.class.getResourceAsStream("rules.json");
		if (in == null) return;
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			apply(gson.fromJson(reader, JsonObject.class));
		}
		catch (Exception e) { log.debug("TSG Hub bundled rules unreadable", e); }
	}

	private static List<String> strings(JsonObject json, String key)
	{
		List<String> out = new ArrayList<>();
		for (JsonElement value : TsgHubJson.array(json, key)) out.add(value.getAsString().toLowerCase(Locale.ROOT));
		return Collections.unmodifiableList(out);
	}

	private static Set<Integer> ints(JsonObject json, String key)
	{
		Set<Integer> out = new HashSet<>();
		for (JsonElement value : TsgHubJson.array(json, key)) out.add(value.getAsInt());
		return Collections.unmodifiableSet(out);
	}
}
