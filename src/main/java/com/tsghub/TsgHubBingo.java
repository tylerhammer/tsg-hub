package com.tsghub;

import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;

final class TsgHubBingo
{
	private final TsgHubConfig config;
	private final ConfigManager configManager;
	private final OverlayManager overlayManager;
	private final MouseManager mouseManager;
	private final TsgHubTileIcons icons;
	private final TsgHubReveals reveals = new TsgHubReveals();
	private final TsgHubSounds sounds;
	private final TsgHubBoardOverlay boardOverlay;
	private final TsgHubRevealOverlay revealOverlay;

	TsgHubBingo(Client client, TsgHubConfig config, ConfigManager configManager, OverlayManager overlayManager, MouseManager mouseManager, TsgHubTileIcons icons)
	{
		this.config = config;
		this.configManager = configManager;
		this.overlayManager = overlayManager;
		this.mouseManager = mouseManager;
		this.icons = icons;
		sounds = new TsgHubSounds(config::bingoSounds, config::bingoVolume);
		boardOverlay = new TsgHubBoardOverlay(icons);
		revealOverlay = new TsgHubRevealOverlay(client, icons, sounds);
	}

	void start()
	{
		boardOverlay.setVisible(config.bingoOverlay());
		overlayManager.add(boardOverlay);
		mouseManager.registerMouseListener(boardOverlay);
		overlayManager.add(revealOverlay);
		mouseManager.registerMouseListener(revealOverlay);
	}

	void stop()
	{
		clear();
		boardOverlay.setVisible(false);
		overlayManager.remove(boardOverlay);
		mouseManager.unregisterMouseListener(boardOverlay);
		overlayManager.remove(revealOverlay);
		mouseManager.unregisterMouseListener(revealOverlay);
		sounds.shutdown();
	}

	TsgHubTileIcons icons()
	{
		return icons;
	}

	boolean wantsBoard()
	{
		return config.bingoReveal() || config.bingoOverlay();
	}

	boolean overlayEnabled()
	{
		return config.bingoOverlay();
	}

	void setOverlayEnabled(boolean enabled)
	{
		configManager.setConfiguration("tsghub", "bingoOverlay", enabled);
	}

	void apply(JsonObject event, String displayName)
	{
		String teamId = TsgHubEvents.teamIdFor(event, displayName);
		boardOverlay.setEvent(event, teamId);
		TsgHubReveals.Moment moment = reveals.update(event, teamId, displayName == null ? "" : displayName);
		if (config.bingoReveal()) revealOverlay.reveal(moment);
	}

	void resetReveals()
	{
		reveals.reset();
		revealOverlay.clear();
	}

	void clear()
	{
		resetReveals();
		boardOverlay.clear();
	}

	void configChanged(String key)
	{
		if ("bingoOverlay".equals(key)) boardOverlay.setVisible(config.bingoOverlay());
		if ("bingoReveal".equals(key)) resetReveals();
	}
}
