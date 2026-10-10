package com.tsghub;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigManager;

final class TsgHubSession
{
	static final String EVENT_ID = "eventId";
	static final String TOKEN = "token";
	static final String DISPLAY_NAME = "displayName";
	static final String ORGANIZER_EVENT_ID = "organizerEventId";
	static final String MEMBER_TOKEN = "memberToken:";
	static final String MEMBER_NAME = "memberName:";
	static final String ORGANIZER_TOKEN = "organizerToken:";
	static final String ORGANIZER_NAME = "organizerName:";
	static final String GROUP_TOKEN = "groupToken";
	static final String GROUP_PASSPHRASE = "groupPassphrase";
	static final String GROUP_PLAYER = "groupPlayer";
	static final String HIDE_COMPLETED = "hideCompleted";
	static final String SHOW_OFFLINE = "showOffline";
	static final String DOCK_VIEW = "dockView";
	static final String DOCK_SPLIT = "dockSplit";
	static final String DOCK_COLLAPSED = "dockCollapsed";

	private static final String GROUP = "tsghubsession";
	private static final String PROFILE_PREFIX = GROUP + "." + ConfigManager.RSPROFILE_GROUP + ".";
	private static volatile ConfigManager configManager;

	private TsgHubSession()
	{
	}

	static void init(ConfigManager manager)
	{
		configManager = manager;
		clearLegacyKeys(manager);
	}

	private static void clearLegacyKeys(ConfigManager manager)
	{
		for (String key : manager.getConfigurationKeys(GROUP + "."))
		{
			if (!key.startsWith(PROFILE_PREFIX))
			{
				manager.unsetConfiguration(GROUP, key.substring(GROUP.length() + 1));
				continue;
			}
			int split = key.indexOf('.', PROFILE_PREFIX.length());
			if (split < 0) continue;
			String name = key.substring(split + 1);
			if (name.startsWith(encode("clanAdminToken:")) || name.startsWith(encode("clanAdminExpiresAt:")))
				manager.unsetConfiguration(GROUP, key.substring(GROUP.length() + 1, split), name);
		}
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
		String value = manager.getRSProfileConfiguration(GROUP, encode(key));
		return value == null ? "" : value;
	}

	static void set(String key, String value)
	{
		ConfigManager manager = configManager;
		if (manager == null) return;
		if (value == null || value.isEmpty()) manager.unsetRSProfileConfiguration(GROUP, encode(key));
		else manager.setRSProfileConfiguration(GROUP, encode(key), value);
	}

	static List<String> keysWithPrefix(String prefix)
	{
		ConfigManager manager = configManager;
		List<String> keys = new ArrayList<>();
		if (manager == null) return keys;
		String profile = manager.getRSProfileKey();
		if (profile == null) return keys;
		for (String key : manager.getRSProfileConfigurationKeys(GROUP, profile, encode(prefix))) keys.add(decode(key));
		return keys;
	}

	static String memberToken(String eventId)
	{
		return eventValue(MEMBER_TOKEN, eventId, TOKEN);
	}

	static String memberName(String eventId)
	{
		return eventValue(MEMBER_NAME, eventId, DISPLAY_NAME);
	}

	private static String eventValue(String prefix, String eventId, String activeKey)
	{
		String value = get(prefix + eventId);
		return value.isEmpty() && eventId.equals(get(EVENT_ID)) ? get(activeKey) : value;
	}

	static void clear(String... keys)
	{
		for (String key : keys) set(key, "");
	}

	static void removePrefix(String prefix)
	{
		for (String key : keysWithPrefix(prefix)) set(key, "");
	}
}
