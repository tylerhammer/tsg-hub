package com.tsghub;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.WorldResult;

class TsgHubHopper
{
	private static final int MAX_OPEN_ATTEMPTS = 5;

	private final Client client;
	private final ClientThread clientThread;
	private final WorldService worldService;
	private World target;
	private int openAttempts;

	TsgHubHopper(Client client, ClientThread clientThread, WorldService worldService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.worldService = worldService;
	}

	void hop(int worldId)
	{
		clientThread.invoke(() -> {
			if (worldId == client.getWorld()) return;
			WorldResult result = worldService.getWorlds();
			net.runelite.http.api.worlds.World world = result == null ? null : result.findWorld(worldId);
			if (world == null) return;
			World rsWorld = client.createWorld();
			rsWorld.setActivity(world.getActivity());
			rsWorld.setAddress(world.getAddress());
			rsWorld.setId(world.getId());
			rsWorld.setPlayerCount(world.getPlayers());
			rsWorld.setLocation(world.getLocation());
			rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
			if (client.getGameState() == GameState.LOGIN_SCREEN)
			{
				client.changeWorld(rsWorld);
				return;
			}
			target = rsWorld;
			openAttempts = 0;
		});
	}

	void onGameTick()
	{
		if (target == null) return;
		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();
			if (++openAttempts >= MAX_OPEN_ATTEMPTS) target = null;
			return;
		}
		client.hopToWorld(target);
		target = null;
	}
}
