package com.tsghub;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
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

@Slf4j
final class TsgHubRules
{
	final String version;
	final List<String> chat;
	final List<String> players;
	final List<String> dropKeywords;
	final Set<Integer> pets;
	final Set<Integer> rewardInterfaces;
	final Map<String, String> bosses;
	final Map<Integer, AreaNames.Area> areas;

	private static volatile TsgHubRules current = bundled();

	private TsgHubRules(JsonObject json)
	{
		version = TsgHubUi.str(json, "version");
		chat = strings(json, "chat");
		players = strings(json, "players");
		dropKeywords = strings(json, "dropKeywords");
		pets = ints(json, "pets");
		rewardInterfaces = ints(json, "rewardInterfaces");
		Map<String, String> bossNames = new HashMap<>();
		for (JsonObject boss : TsgHubUi.objects(TsgHubUi.array(json, "bosses")))
		{
			String name = TsgHubUi.str(boss, "name");
			for (JsonElement npc : TsgHubUi.array(boss, "npcs")) bossNames.put(npc.getAsString().trim().toLowerCase(Locale.ROOT), name);
		}
		bosses = Collections.unmodifiableMap(bossNames);
		Map<Integer, AreaNames.Area> regions = new HashMap<>();
		for (JsonObject area : TsgHubUi.objects(TsgHubUi.array(json, "areas")))
		{
			AreaNames.Type type;
			try { type = AreaNames.Type.valueOf(TsgHubUi.str(area, "type")); }
			catch (IllegalArgumentException e) { continue; }
			AreaNames.Area named = new AreaNames.Area(TsgHubUi.str(area, "name"), type);
			for (JsonElement region : TsgHubUi.array(area, "regions")) regions.putIfAbsent(region.getAsInt(), named);
		}
		areas = Collections.unmodifiableMap(regions);
	}

	static TsgHubRules get()
	{
		return current;
	}

	static void apply(JsonObject json)
	{
		try { current = new TsgHubRules(json); }
		catch (RuntimeException e) { log.debug("TSG Hub rules rejected", e); }
	}

	private static TsgHubRules bundled()
	{
		InputStream in = TsgHubRules.class.getResourceAsStream("rules.json");
		if (in == null) return new TsgHubRules(new JsonObject());
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return new TsgHubRules(new JsonParser().parse(reader).getAsJsonObject());
		}
		catch (Exception e)
		{
			log.debug("TSG Hub bundled rules unreadable", e);
			return new TsgHubRules(new JsonObject());
		}
	}

	private static List<String> strings(JsonObject json, String key)
	{
		List<String> out = new ArrayList<>();
		for (JsonElement value : TsgHubUi.array(json, key)) out.add(value.getAsString().toLowerCase(Locale.ROOT));
		return Collections.unmodifiableList(out);
	}

	private static Set<Integer> ints(JsonObject json, String key)
	{
		Set<Integer> out = new HashSet<>();
		for (JsonElement value : TsgHubUi.array(json, key)) out.add(value.getAsInt());
		return Collections.unmodifiableSet(out);
	}
}
