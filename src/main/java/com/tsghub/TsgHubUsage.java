package com.tsghub;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigItem;

/**
 * Counts how often features are used, so the clan can see which ones members rely on before
 * removing any. Only feature names and counts are sent, with the account hash the server uses to
 * count each member once a month; the server keeps totals, not who used what. Nothing is counted
 * or sent unless "Share game and clan progress" is on.
 */
final class TsgHubUsage
{
	static final long FLUSH_SECONDS = 5 * 60;
	static final int MAX_FEATURES = 64;
	static final int MAX_COUNT = 10_000;

	private final BooleanSupplier enabled;
	private final Supplier<JsonObject> identity;
	private final Consumer<JsonObject> send;
	private final Supplier<List<String>> settings;
	private final Map<String, Integer> counts = new LinkedHashMap<>();
	private boolean settingsSent;

	/**
	 * @param identity the report's clanName and accountHash, or null while logged out
	 * @param settings the feature names of settings that are on, sent once per session
	 */
	TsgHubUsage(BooleanSupplier enabled, Supplier<JsonObject> identity, Consumer<JsonObject> send, Supplier<List<String>> settings)
	{
		this.enabled = enabled;
		this.identity = identity;
		this.send = send;
		this.settings = settings;
	}

	void count(String feature)
	{
		if (!enabled.getAsBoolean()) return;
		synchronized (this)
		{
			counts.merge(feature, 1, (a, b) -> Math.min(a + b, MAX_COUNT));
		}
	}

	void flush()
	{
		if (!enabled.getAsBoolean())
		{
			clear();
			return;
		}
		JsonObject who = identity.get();
		if (who == null) return;
		Map<String, Integer> taken;
		synchronized (this)
		{
			if (!settingsSent)
			{
				for (String setting : settings.get()) counts.putIfAbsent(setting, 1);
				settingsSent = true;
			}
			if (counts.isEmpty()) return;
			taken = new LinkedHashMap<>(counts);
			counts.clear();
		}
		Iterator<Map.Entry<String, Integer>> it = taken.entrySet().iterator();
		while (it.hasNext())
		{
			JsonObject features = new JsonObject();
			while (it.hasNext() && features.size() < MAX_FEATURES)
			{
				Map.Entry<String, Integer> e = it.next();
				features.addProperty(e.getKey(), e.getValue());
			}
			JsonObject body = who.deepCopy();
			body.add("features", features);
			send.accept(body);
		}
	}

	synchronized void clear()
	{
		counts.clear();
	}

	/** The boolean settings that are on, as setting.<key>, read from the config interface. */
	static List<String> enabledSettings(TsgHubConfig config)
	{
		List<String> on = new ArrayList<>();
		for (Method method : TsgHubConfig.class.getMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item == null || item.hidden() || item.secret() || method.getReturnType() != boolean.class || method.getParameterCount() != 0) continue;
			if ("dataSharingOptIn".equals(item.keyName())) continue;
			try
			{
				if ((boolean) method.invoke(config)) on.add(settingName(item.keyName()));
			}
			catch (ReflectiveOperationException ignored)
			{
			}
		}
		return on;
	}

	static String settingName(String key)
	{
		return "setting." + key.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
	}

	static String viewName(TsgHubSidebarPanel.View view)
	{
		return "view." + view.name().toLowerCase().replace('_', '-');
	}
}
