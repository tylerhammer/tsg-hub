package com.tsghub;

import com.google.gson.*;
import com.tsghub.TsgHubUi.*;
import java.util.concurrent.*;
import javax.swing.*;
import lombok.*;
import net.runelite.client.config.*;
import static com.tsghub.TsgHubJson.*;

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
		plugin.api().call("POST", "/v1/auth/start", null, obj("clanName", plugin.clan().name()), response -> {
			String code = str(response, "deviceCode");
			String command = str(response, "command");
			if (code.isEmpty() || command.isEmpty()) throw new IllegalStateException("The server didn't return a link code.");
			linkDeadline = System.currentTimeMillis() + Math.max(60, integer(response, "expiresIn", 600)) * 1000L;
			deviceCode = code;
			scheduleLinkPoll(code, Math.max(2, integer(response, "interval", 5)));
			SwingUtilities.invokeLater(() -> TsgHubUi.copyToClipboard(command));
			plugin.memberStatus("Copied " + command + ". Paste it in the clan Discord to link.", Tone.SUCCESS);
			plugin.joinDiscord(e -> plugin.memberError("Copied " + command + ", but couldn't open the Discord invite. ", e));
		}, e -> plugin.memberError("Couldn't start linking. ", e), () -> {
			linkStarting = false;
			plugin.discordLinkChanged();
		});
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
		plugin.api().call("POST", "/v1/auth/poll", null, obj("deviceCode", code), result -> {
			if (!code.equals(deviceCode)) return;
			String status = str(result, "status");
			if ("complete".equals(status))
			{
				stopLink();
				String key = str(result, "key");
				if (key.isEmpty()) plugin.memberStatus("Linking didn't finish. Click Link Discord to try again.", Tone.ERROR);
				else configManager.setConfiguration("tsghub", "hubKey", key);
				plugin.discordLinkChanged();
			}
			else if ("expired".equals(status)) linkExpired();
			else scheduleLinkPoll(code, seconds);
		}, e -> {
			if (code.equals(deviceCode)) scheduleLinkPoll(code, seconds);
		});
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
		plugin.api().call("GET", query, key, null, result -> {
			if (!identity.equals(checkedIdentity)) return;
			backoff.reset();
			boolean admin = grantsAdmin(result);
			if (admin != verified) setVerified(admin);
			String linkError = str(result, "linkError");
			boolean linked = bool(result, "linked");
			if (!linkError.isEmpty()) plugin.memberStatus(linkError, Tone.ERROR);
			else if (announce && linked) plugin.memberStatus(admin ? "Hub key accepted. Your Discord account is linked and the admin site button is unlocked." : "Hub key accepted. Your Discord account is linked.", Tone.SUCCESS);
			if (linked || !linkError.isEmpty()) announce = false;
			if (!linked && hash.isEmpty()) retry(identity);
		}, e -> {
			if (TsgHubApi.status(e) == 401) rejected(key);
			else retry(identity);
		});
	}

	static boolean grantsAdmin(JsonObject me)
	{
		String role = str(me, "role");
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
