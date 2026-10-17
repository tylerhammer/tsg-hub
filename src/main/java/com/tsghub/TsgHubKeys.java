package com.tsghub;

import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.client.config.ConfigManager;

final class TsgHubKeys
{
	private final TsgHubPlugin plugin;
	private final TsgHubConfig config;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executor;
	private final TsgHubBackoff backoff = new TsgHubBackoff(30_000, 300_000);
	private volatile boolean verified;
	private volatile String checkedIdentity = "";
	private volatile String rejected = "";
	private volatile boolean announce;
	private ScheduledFuture<?> retry;

	TsgHubKeys(TsgHubPlugin plugin, TsgHubConfig config, ConfigManager configManager, ScheduledExecutorService executor)
	{
		this.plugin = plugin;
		this.config = config;
		this.configManager = configManager;
		this.executor = executor;
	}

	boolean verified()
	{
		return verified;
	}

	String adminKey()
	{
		return verified ? configured() : "";
	}

	private String configured()
	{
		String key = config.hubKey();
		return key == null ? "" : key.trim();
	}

	void migrate()
	{
		String legacy = configManager.getConfiguration("tsghub", "adminKey");
		if (legacy == null) return;
		if (!legacy.trim().isEmpty() && configured().isEmpty()) configManager.setConfiguration("tsghub", "hubKey", legacy.trim());
		configManager.unsetConfiguration("tsghub", "adminKey");
	}

	void check()
	{
		String key = configured();
		TsgHubClan clan = plugin.clan();
		String name = clan.playerName();
		if (key.isEmpty())
		{
			checkedIdentity = "";
			if (verified) setVerified(false);
			return;
		}
		if (name.isEmpty() || !plugin.sharingInClan()) return;
		String hash = clan.accountHash();
		String identity = key + "\n" + name + "\n" + hash;
		if (identity.equals(checkedIdentity) || key.equals(rejected) || executor.isShutdown()) return;
		checkedIdentity = identity;
		String query = "/v1/me?displayName=" + TsgHubApi.encode(name) + (hash.isEmpty() ? "" : "&accountHash=" + hash);
		plugin.api().call("GET", query, key, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				if (!identity.equals(checkedIdentity)) return;
				backoff.reset();
				boolean admin = grantsAdmin(result);
				if (admin != verified) setVerified(admin);
				if (admin) plugin.sessions().loadClanEvents();
				String linkError = TsgHubJson.str(result, "linkError");
				boolean linked = TsgHubJson.bool(result, "linked");
				if (!linkError.isEmpty()) plugin.memberStatus(linkError, Tone.ERROR);
				else if (announce && linked) plugin.memberStatus(admin ? "Hub key accepted. Your Discord account is linked and admin tools are unlocked." : "Hub key accepted. Your Discord account is linked.", Tone.SUCCESS);
				if (linked || !linkError.isEmpty()) announce = false;
				if (!linked && hash.isEmpty()) retry(identity);
			}
			catch (TsgHubApi.HttpError e)
			{
				if (e.status == 401) rejected(key);
				else retry(identity);
			}
			catch (Exception e) { retry(identity); }
		}, executor);
	}

	static boolean grantsAdmin(JsonObject me)
	{
		String role = TsgHubJson.str(me, "role");
		return role.isEmpty() || "admin".equals(role);
	}

	private void retry(String identity)
	{
		if (!identity.equals(checkedIdentity)) return;
		checkedIdentity = "";
		if (!executor.isShutdown()) retry = executor.schedule(this::check, backoff.next(), TimeUnit.MILLISECONDS);
	}

	void recheck(String key)
	{
		if (!key.equals(configured())) return;
		checkedIdentity = "";
		if (verified) setVerified(false);
		check();
	}

	void rejected(String key)
	{
		if (!key.equals(configured())) return;
		rejected = key;
		announce = false;
		setVerified(false);
		plugin.memberStatus("Your hub key wasn't accepted. It may have expired or been revoked. Run /hub key in the clan Discord and paste the new key in the plugin settings.", Tone.ERROR);
	}

	void keyChanged()
	{
		checkedIdentity = "";
		rejected = "";
		backoff.reset();
		announce = !configured().isEmpty();
		if (verified) setVerified(false);
		check();
	}

	void revoke()
	{
		checkedIdentity = "";
		if (verified) setVerified(false);
	}

	void cancelRetry()
	{
		ScheduledFuture<?> pending = retry;
		if (pending != null) pending.cancel(false);
		retry = null;
	}

	void loggedOut()
	{
		verified = false;
		checkedIdentity = "";
	}

	void reset()
	{
		verified = false;
		checkedIdentity = "";
		rejected = "";
		backoff.reset();
		announce = false;
	}

	private void setVerified(boolean value)
	{
		verified = value;
		plugin.accessChanged();
	}
}
