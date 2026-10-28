package com.tsghub;

import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

@RequiredArgsConstructor
final class TsgHubRecords
{
	static final String PB_GROUP = "personalbest";
	static final int MAX_PBS = 300;

	private final TsgHubPlugin plugin;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private String synced = "";

	synchronized void syncPbs()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null || !plugin.sharingInClan()) return;
		JsonObject times = new JsonObject();
		for (String boss : configManager.getRSProfileConfigurationKeys(PB_GROUP, profile, ""))
		{
			double seconds = seconds(configManager.getRSProfileConfiguration(PB_GROUP, boss));
			if (seconds > 0 && times.size() < MAX_PBS) times.addProperty(boss, seconds);
		}
		String key = profile + times;
		if (key.equals(synced) || !send(times, true)) return;
		synced = key;
	}

	synchronized void onConfigChanged(ConfigChanged event)
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null || !profile.equals(event.getProfile()) || !plugin.sharingInClan()) return;
		JsonObject times = new JsonObject();
		times.addProperty(event.getKey(), Math.max(0, seconds(event.getNewValue())));
		send(times, false);
	}

	synchronized void reset()
	{
		synced = "";
	}

	private boolean send(JsonObject times, boolean full)
	{
		String name = plugin.clan().playerName();
		String hash = plugin.clan().accountHash();
		if (name.isEmpty() || hash.isEmpty()) return false;
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.clan().name());
		body.addProperty("displayName", name);
		body.addProperty("accountHash", hash);
		body.addProperty("full", full);
		body.add("pbs", times);
		plugin.tracker().report("pbs", "/v1/pbs", body);
		return true;
	}

	static double seconds(String value)
	{
		if (value == null) return 0;
		try
		{
			double seconds = Double.parseDouble(value.trim());
			return Double.isFinite(seconds) ? seconds : 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	void load()
	{
		if (!plugin.clan().isHubClan() || !plugin.sharingEnabled() || executor.isShutdown()) return;
		plugin.sidebarBusy(true);
		api.get().call("GET", "/v1/records?clanName=" + TsgHubApi.encode(plugin.clan().name()), null, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject records = TsgHubApi.result(response, error);
				plugin.ui(s -> s.recordsView.setRecords(records));
			}
			catch (Exception e)
			{
				boolean missing = e instanceof TsgHubApi.HttpError && ((TsgHubApi.HttpError) e).status == 404;
				plugin.ui(s -> s.recordsView.failed(missing));
				if (!missing) plugin.memberError("Couldn't load records. ", e);
			}
			finally
			{
				plugin.sidebarBusy(false);
			}
		}, executor);
	}
}
