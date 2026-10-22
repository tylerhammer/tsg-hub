package com.tsghub;

import static com.tsghub.TsgHubJson.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

// IDs are as-dropped item IDs so loot and collection log unlocks match.
@Slf4j
final class TsgHubItemSets
{
	static final class ItemSet
	{
		String name;
		List<ItemSuggestion> items;
	}

	private static final Map<String, ItemSet> SETS = new LinkedHashMap<>();

	private TsgHubItemSets()
	{
	}

	static void load(Gson gson)
	{
		if (!SETS.isEmpty()) return;
		try (InputStreamReader reader = new InputStreamReader(TsgHubItemSets.class.getResourceAsStream("item-sets.json"), StandardCharsets.UTF_8))
		{
			JsonObject json = gson.fromJson(reader, JsonObject.class);
			for (JsonElement set : array(json, "sets"))
			{
				ItemSet parsed = gson.fromJson(set, ItemSet.class);
				SETS.put(parsed.name, parsed);
			}
		}
		catch (Exception e) { log.debug("TSG Hub item sets unreadable", e); }
	}

	static String nameFor(Collection<Integer> itemIds)
	{
		HashSet<Integer> wanted = new HashSet<>(itemIds);
		for (ItemSet set : SETS.values())
		{
			HashSet<Integer> ids = new HashSet<>();
			for (ItemSuggestion item : set.items) ids.add(item.id);
			if (ids.equals(wanted)) return set.name;
		}
		return "";
	}
}
