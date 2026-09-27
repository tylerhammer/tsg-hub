package com.tsghub;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigManager;

final class TsgHubSession
{
	private static final String GROUP = "tsghubsession";
	private static volatile ConfigManager configManager;

	private TsgHubSession()
	{
	}

	static void init(ConfigManager manager)
	{
		configManager = manager;
	}

	// ConfigManager rejects ':' in keys, so ':' is stored as '_'.
	// Event IDs, clan keys and prefixes never contain '_'.
	private static String encode(String key)
	{
		return key.replace(':', '_');
	}

	private static String decode(String key)
	{
		return key.replace('_', ':');
	}

	static String get(String key)
	{
		ConfigManager manager = configManager;
		if (manager == null) return "";
		String value = manager.getConfiguration(GROUP, encode(key));
		return value == null ? "" : value;
	}

	static void set(String key, String value)
	{
		ConfigManager manager = configManager;
		if (manager == null) return;
		if (value == null || value.isEmpty()) manager.unsetConfiguration(GROUP, encode(key));
		else manager.setConfiguration(GROUP, encode(key), value);
	}

	static List<String> keysWithPrefix(String prefix)
	{
		ConfigManager manager = configManager;
		List<String> keys = new ArrayList<>();
		if (manager == null) return keys;
		// ConfigManager returns full "group.key" names.
		String full = GROUP + "." + encode(prefix);
		for (String key : manager.getConfigurationKeys(full)) keys.add(decode(key.substring(GROUP.length() + 1)));
		return keys;
	}

	static void removePrefix(String prefix)
	{
		for (String key : keysWithPrefix(prefix)) set(key, "");
	}
}
