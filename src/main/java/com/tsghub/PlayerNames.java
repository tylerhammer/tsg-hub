package com.tsghub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerNames
{
	private PlayerNames()
	{
	}

	static String searchKey(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	public static String key(String name)
	{
		return name == null ? "" : name.replace('\u00A0', ' ').replace('_', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	static List<String> matchNames(List<String> names, String query, int limit)
	{
		String needle = searchKey(query);
		List<String> prefix = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String name : names)
		{
			String key = searchKey(name);
			if (key.startsWith(needle)) prefix.add(name);
			else if (key.contains(needle)) contains.add(name);
		}
		prefix.addAll(contains);
		return prefix.size() > limit ? prefix.subList(0, limit) : prefix;
	}

	static String resolveName(List<String> names, String typed)
	{
		if (typed.isEmpty()) return typed;
		for (String name : names) if (TsgHubUi.samePlayer(name, typed)) return name;
		List<String> matches = matchNames(names, typed, 2);
		return matches.size() == 1 ? matches.get(0) : typed;
	}
}
