package com.tsghub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

final class AreaNames
{
	enum Type { BOSSES, RAIDS, MINIGAMES, DUNGEONS, CITIES, REGIONS }

	static final class Area
	{
		final String name;
		final Type type;

		Area(String name, Type type)
		{
			this.name = name;
			this.type = type;
		}
	}

	private static final Map<Integer, Area> BY_REGION = load();

	private AreaNames() {}

	static Area forRegion(int region)
	{
		return BY_REGION.get(region);
	}

	static Area at(WorldPoint point)
	{
		Area named = forRegion(point.getRegionID());
		if (named != null && (named.type == Type.BOSSES || named.type == Type.RAIDS)) return named;
		int level = wildernessLevel(point);
		return level > 0 ? new Area("Wilderness lvl " + level, Type.REGIONS) : named;
	}

	static int wildernessLevel(WorldPoint point)
	{
		int x = point.getX();
		int y = point.getY();
		if (x < 2944 || x > 3391) return 0;
		if (y >= 3520 && y <= 3967) return (y - 3520) / 8 + 1;
		if (y >= 9920 && y <= 10367) return (y - 9920) / 8 + 1;
		return 0;
	}

	private static Map<Integer, Area> load()
	{
		Map<Integer, Area> areas = new HashMap<>();
		InputStream in = AreaNames.class.getResourceAsStream("areas.tsv");
		if (in == null) return areas;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] parts = line.split("\t");
				if (parts.length < 3) continue;
				Area area = new Area(parts[1], Type.valueOf(parts[0]));
				for (String id : parts[2].split(",")) areas.putIfAbsent(Integer.parseInt(id.trim()), area);
			}
		}
		catch (IOException | IllegalArgumentException ignored) { }
		return areas;
	}
}
