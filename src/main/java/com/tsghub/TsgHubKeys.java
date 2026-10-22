package com.tsghub;

import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

@RequiredArgsConstructor
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
	private volatile String deviceCode = "";
	private volatile long linkDeadline;
	private volatile boolean linkStarting;
	private volatile ScheduledFuture<?> linkPoll;

	boolean verified()
	{
		return verified;
	}

	String adminKey()
	{
		return verified ? configured() : "";
	}

	boolean needsLink()
	{
		String key = configured();
		return key.isEmpty() || key.equals(rejected);
	}

	long linkDeadline()
	{
		return deviceCode.isEmpty() ? 0 : linkDeadline;
	}

	boolean linkStarting()
	{
		return linkStarting;
	}

	void startLink()
	{
		if (linkStarting || linkDeadline() > System.currentTimeMillis() || executor.isShutdown()) return;
		stopLink();
		linkStarting = true;
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.clan().name());
		plugin.api().call("POST", "/v1/auth/start", null, body).whenCompleteAsync(TsgHubApi.handle(response -> {
			String code = TsgHubJson.str(response, "deviceCode");
			String command = TsgHubJson.str(response, "command");
			if (code.isEmpty() || command.isEmpty()) throw new IllegalStateException("The server didn't return a link code.");
			linkDeadline = System.currentTimeMillis() + Math.max(60, TsgHubJson.integer(response, "expiresIn", 600)) * 1000L;
			deviceCode = code;
			scheduleLinkPoll(code, Math.max(2, TsgHubJson.integer(response, "interval", 5)));
			SwingUtilities.invokeLater(() -> TsgHubUi.copyToClipboard(command));
			plugin.memberStatus("Copied " + command + ". Paste it in the clan Discord to link.", Tone.SUCCESS);
			plugin.joinDiscord(e -> plugin.memberError("Copied " + command + ", but couldn't open the Discord invite. ", e));
		}, e -> plugin.memberError("Couldn't start linking. ", e), () -> {
			linkStarting = false;
			plugin.discordLinkChanged();
		}), executor);
	}

	void cancelLink()
	{
		stopLink();
		plugin.discordLinkChanged();
	}

	private void stopLink()
	{
		deviceCode = "";
		ScheduledFuture<?> pending = linkPoll;
		if (pending != null) pending.cancel(false);
		linkPoll = null;
	}

	private void scheduleLinkPoll(String code, int seconds)
	{
		if (!executor.isShutdown()) linkPoll = executor.schedule(() -> pollLink(code, seconds), seconds, TimeUnit.SECONDS);
	}

	private void pollLink(String code, int seconds)
	{
		if (!code.equals(deviceCode)) return;
		if (System.currentTimeMillis() > linkDeadline)
		{
			linkExpired();
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("deviceCode", code);
		plugin.api().call("POST", "/v1/auth/poll", null, body).whenCompleteAsync((response, error) -> {
			if (!code.equals(deviceCode)) return;
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				String status = TsgHubJson.str(result, "status");
				if ("complete".equals(status))
				{
					stopLink();
					String key = TsgHubJson.str(result, "key");
					if (key.isEmpty()) plugin.memberStatus("Linking didn't finish. Click Link Discord to try again.", Tone.ERROR);
					else configManager.setConfiguration("tsghub", "hubKey", key);
					plugin.discordLinkChanged();
					return;
				}
				if ("expired".equals(status))
				{
					linkExpired();
					return;
				}
			}
			catch (Exception ignored) { }
			scheduleLinkPoll(code, seconds);
		}, executor);
	}

	private void linkExpired()
	{
		stopLink();
		plugin.memberStatus("The link code expired. Click Link Discord to try again.", Tone.ERROR);
		plugin.discordLinkChanged();
	}

	private String configured()
	{
		String key = config.hubKey();
		return key == null ? "" : key.trim();
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
				String linkError = TsgHubJson.str(result, "linkError");
				boolean linked = TsgHubJson.bool(result, "linked");
				if (!linkError.isEmpty()) plugin.memberStatus(linkError, Tone.ERROR);
				else if (announce && linked) plugin.memberStatus(admin ? "Hub key accepted. Your Discord account is linked and the admin site button is unlocked." : "Hub key accepted. Your Discord account is linked.", Tone.SUCCESS);
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
		plugin.discordLinkChanged();
		plugin.memberStatus("Your hub key wasn't accepted. It may have expired or been revoked. Click Link Discord on the home screen to link again.", Tone.ERROR);
	}

	void keyChanged()
	{
		if (!configured().isEmpty()) stopLink();
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
		stopLink();
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
