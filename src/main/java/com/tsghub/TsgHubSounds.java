package com.tsghub;

import java.util.concurrent.*;
import java.util.function.*;
import lombok.extern.slf4j.*;
import net.runelite.client.audio.*;

@Slf4j
final class TsgHubSounds
{
	enum Sound
	{
		BOARD_IN("board-in", 4f),
		DAUBER_DROP("dauber-drop", -4f),
		DAUBER_HIT("dauber-hit", -4f),
		POINTS("points", -5f),
		LINE_NOTE("line-note", -3f),
		STRIKE("strike", -1f),
		BINGO_STAMP("bingo-stamp", -3f);

		private final String file;
		private final float trim;

		Sound(String file, float trim)
		{
			this.file = file;
			this.trim = trim;
		}

		String path(int step)
		{
			return "sounds/" + file + (step == 0 ? "" : "-" + step) + ".wav";
		}
	}

	private final AudioPlayer audioPlayer;
	private final BooleanSupplier enabled;
	private final IntSupplier volume;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "tsg-hub-sounds");
		thread.setDaemon(true);
		return thread;
	});

	TsgHubSounds(AudioPlayer audioPlayer, BooleanSupplier enabled, IntSupplier volume)
	{
		this.audioPlayer = audioPlayer;
		this.enabled = enabled;
		this.volume = volume;
	}

	void play(Sound sound, int step)
	{
		if (!enabled.getAsBoolean() || volume.getAsInt() <= 0 || executor.isShutdown()) return;
		executor.execute(() -> {
			try
			{
				audioPlayer.play(TsgHubSounds.class, sound.path(step), gain(sound));
			}
			catch (Exception e)
			{
				log.debug("TSG Hub could not play sound {}", sound.file, e);
			}
		});
	}

	private float gain(Sound sound)
	{
		double level = Math.max(1, Math.min(100, volume.getAsInt())) / 100.0;
		return (float) (20 * Math.log10(level)) + sound.trim;
	}

	void shutdown()
	{
		executor.shutdownNow();
	}
}
