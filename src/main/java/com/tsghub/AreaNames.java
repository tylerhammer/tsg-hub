package com.tsghub;

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

	private AreaNames() {}

	static Area forRegion(int region)
	{
		return TsgHubRules.get().areas.get(region);
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
}
