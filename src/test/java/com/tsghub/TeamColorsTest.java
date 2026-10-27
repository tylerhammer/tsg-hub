package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;

public class TeamColorsTest
{
	private boolean enabled = true;

	private TsgHubTeamColors colors()
	{
		ClientThread clientThread = new ClientThread()
		{
			@Override public void invoke(Runnable r) { }
		};
		return new TsgHubTeamColors(null, clientThread, new TsgHubConfig()
		{
			@Override public boolean teamNameColors() { return enabled; }
		});
	}

	private static JsonObject json(String text)
	{
		return new JsonParser().parse(text).getAsJsonObject();
	}

	@Test
	public void matchesNamesLikeTheGame()
	{
		TsgHubTeamColors colors = colors();
		colors.apply(json("{\"Iron Zez\":\"#ff0000\",\"Bob\":\"blue\",\"Cara\":\"#00FF00\"}"));
		assertEquals(new Color(0xff0000), colors.colorOf("<img=2>Iron Zez"));
		assertEquals(new Color(0x00ff00), colors.colorOf("cara"));
		assertNull(colors.colorOf("Bob"));
		assertNull(colors.colorOf("Dave"));
	}

	@Test
	public void toggleAndMissingMapTurnColorsOff()
	{
		TsgHubTeamColors colors = colors();
		colors.apply(json("{\"Alice\":\"#123456\"}"));
		enabled = false;
		assertNull(colors.colorOf("Alice"));
		enabled = true;
		colors.apply(new JsonObject());
		assertNull(colors.colorOf("Alice"));
	}
}
