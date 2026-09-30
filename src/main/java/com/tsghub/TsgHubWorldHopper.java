package com.tsghub;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.WorldResult;

final class TsgHubWorldHopper
{
	private static final int MAX_OPEN_ATTEMPTS = 5;

	private final Client client;
	private final ClientThread clientThread;
	private final WorldService worldService;
	private World target;
	private int attempts;

	TsgHubWorldHopper(Client client, ClientThread clientThread, WorldService worldService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.worldService = worldService;
	}

	void hop(int worldId)
	{
		clientThread.invoke(() -> {
			GameState state = client.getGameState();
			if (worldId <= 0 || client.getWorld() == worldId) return;
			if (state != GameState.LOGGED_IN && state != GameState.LOGIN_SCREEN) return;
			WorldResult worlds = worldService.getWorlds();
			net.runelite.http.api.worlds.World found = worlds == null ? null : worlds.findWorld(worldId);
			if (found == null) return;
			World world = client.createWorld();
			world.setActivity(found.getActivity());
			world.setAddress(found.getAddress());
			world.setId(found.getId());
			world.setPlayerCount(found.getPlayers());
			world.setLocation(found.getLocation());
			world.setTypes(WorldUtil.toWorldTypes(found.getTypes()));
			if (state == GameState.LOGIN_SCREEN)
			{
				client.changeWorld(world);
				return;
			}
			target = world;
			attempts = 0;
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (target == null) return;
		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();
			if (++attempts >= MAX_OPEN_ATTEMPTS) target = null;
			return;
		}
		client.hopToWorld(target);
		target = null;
	}
}
