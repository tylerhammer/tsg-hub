package com.tsghub;

import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;

class TsgHubClan
{
	private static final int CHECK_TICKS = 50;

	private final Client client;
	private final String hubName;
	private volatile String clanName = "";
	private volatile String playerName = "";
	private volatile int rank = -1;
	private volatile boolean inClanChat;
	private volatile int checkTicks;

	TsgHubClan(Client client, String hubName)
	{
		this.client = client;
		this.hubName = hubName;
	}

	String name() { return clanName; }
	String playerName() { return playerName; }
	int rank() { return rank; }
	boolean inClanChat() { return inClanChat; }
	String hubName() { return hubName; }

	boolean isHubClan()
	{
		return PlayerNames.samePlayer(clanName, hubName);
	}

	boolean pending()
	{
		return clanName.isEmpty() && checkTicks > 0;
	}

	void loggedIn()
	{
		if (clanName.isEmpty()) checkTicks = CHECK_TICKS;
	}

	boolean tick()
	{
		if (checkTicks <= 0) return false;
		checkTicks--;
		if (client.getClanSettings() != null) checkTicks = 0;
		return checkTicks == 0;
	}

	void detect()
	{
		ClanSettings settings = client.getClanSettings();
		String player = client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null ? "" : client.getLocalPlayer().getName();
		playerName = player;
		String name = settings == null ? "" : settings.getName();
		clanName = name == null ? "" : name.trim();
		if (!clanName.isEmpty()) checkTicks = 0;
		int detected = -1;
		if (settings != null && !player.isEmpty())
		{
			ClanMember member = settings.findMember(player);
			if (member != null && member.getRank() != null) detected = member.getRank().getRank();
		}
		rank = detected;
		ClanChannel channel = client.getClanChannel();
		inClanChat = channel != null && channel.getName() != null && channel.getName().trim().equalsIgnoreCase(clanName);
	}

	void reset()
	{
		playerName = "";
		clanName = "";
		rank = -1;
		inClanChat = false;
		checkTicks = 0;
	}

	String accountHash()
	{
		long hash = client.getAccountHash();
		return hash == -1 ? "" : Long.toString(hash);
	}

	void addAccountHash(JsonObject body)
	{
		String hash = accountHash();
		if (!hash.isEmpty()) body.addProperty("accountHash", hash);
	}

	JsonObject memberBody(String displayName)
	{
		JsonObject body = new JsonObject();
		body.addProperty("displayName", displayName);
		body.addProperty("clanName", clanName);
		body.addProperty("clanRank", rank);
		addAccountHash(body);
		return body;
	}
}
