package com.tsghub;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

@RequiredArgsConstructor
class TsgHubSession
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
	static final String DROP_TYPE = "dropType";
	static final String DROP_VALUE = "dropValue";
	static final String ANNOUNCEMENTS_READ = "announcementsRead";
	static final String ANNOUNCEMENTS_ALERTED = "announcementsAlerted";

	private static final String GROUP = "tsghubsession";
	private final ConfigManager configManager;

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

	String get(String key)
	{
		ConfigManager manager = configManager;
		if (manager == null) return "";
		String value = manager.getRSProfileConfiguration(GROUP, encode(key));
		return value == null ? "" : value;
	}

	void set(String key, String value)
	{
		ConfigManager manager = configManager;
		if (manager == null) return;
		if (value == null || value.isEmpty()) manager.unsetRSProfileConfiguration(GROUP, encode(key));
		else manager.setRSProfileConfiguration(GROUP, encode(key), value);
	}

	List<String> keysWithPrefix(String prefix)
	{
		ConfigManager manager = configManager;
		List<String> keys = new ArrayList<>();
		if (manager == null) return keys;
		String profile = manager.getRSProfileKey();
		if (profile == null) return keys;
		for (String key : manager.getRSProfileConfigurationKeys(GROUP, profile, encode(prefix))) keys.add(decode(key));
		return keys;
	}

	String memberToken(String eventId)
	{
		return eventValue(MEMBER_TOKEN, eventId, TOKEN);
	}

	String memberName(String eventId)
	{
		return eventValue(MEMBER_NAME, eventId, DISPLAY_NAME);
	}

	private String eventValue(String prefix, String eventId, String activeKey)
	{
		String value = get(prefix + eventId);
		return value.isEmpty() && eventId.equals(get(EVENT_ID)) ? get(activeKey) : value;
	}

	void clear(String... keys)
	{
		for (String key : keys) set(key, "");
	}

	void removePrefix(String prefix)
	{
		for (String key : keysWithPrefix(prefix)) set(key, "");
	}
}
