package com.tsghub;

import java.util.Locale;

public final class PlayerNames
{
	private PlayerNames()
	{
	}

	public static String normalize(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}
}
