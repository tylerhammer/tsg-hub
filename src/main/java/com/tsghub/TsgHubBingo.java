package com.tsghub;

import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.client.audio.AudioPlayer;
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
	private volatile TsgHubBingoView view;
	private volatile String listEventId = "";

	TsgHubBingo(Client client, AudioPlayer audioPlayer, TsgHubConfig config, ConfigManager configManager, OverlayManager overlayManager, MouseManager mouseManager, TsgHubTileIcons icons)
	{
		this.config = config;
		this.configManager = configManager;
		this.overlayManager = overlayManager;
		this.mouseManager = mouseManager;
		this.icons = icons;
		sounds = new TsgHubSounds(audioPlayer, config::bingoSounds, config::bingoVolume);
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

	boolean wantsBoard(String eventId)
	{
		return (config.bingoReveal() || config.bingoOverlay()) && !eventId.equals(listEventId);
	}

	TsgHubBingoView viewFor(JsonObject event, String teamId)
	{
		TsgHubBingoView current = view;
		return current != null && current.event == event ? current : TsgHubBingoView.of(event, teamId);
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
		listEventId = TsgHubBingoBoard.hasBoard(event) ? "" : TsgHubJson.str(event, "id");
		String teamId = TsgHubEvents.teamIdFor(event, displayName);
		TsgHubBingoView next = teamId == null || teamId.isEmpty() ? null : TsgHubBingoView.of(event, teamId);
		view = next;
		boardOverlay.setView(next);
		TsgHubReveals.Moment moment = reveals.update(next, displayName == null ? "" : displayName);
		if (config.bingoReveal()) revealOverlay.reveal(moment);
	}

	void resetReveals()
	{
		reveals.reset();
		revealOverlay.clear();
	}

	void clear()
	{
		view = null;
		listEventId = "";
		resetReveals();
		boardOverlay.clear();
	}

	void configChanged(String key)
	{
		if ("bingoOverlay".equals(key)) boardOverlay.setVisible(config.bingoOverlay());
		if ("bingoReveal".equals(key)) resetReveals();
	}
}
