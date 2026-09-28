package com.tsghub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
