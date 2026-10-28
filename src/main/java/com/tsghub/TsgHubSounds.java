package com.tsghub;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import lombok.extern.slf4j.Slf4j;

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
	}

	private static final class Sample
	{
		final AudioFormat format;
		final byte[] data;

		Sample(AudioFormat format, byte[] data)
		{
			this.format = format;
			this.data = data;
		}
	}

	private final Map<Sound, Sample> samples = new EnumMap<>(Sound.class);
	private final Map<String, byte[]> pitched = new HashMap<>();
	private final BooleanSupplier enabled;
	private final IntSupplier volume;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "tsg-hub-sounds");
		thread.setDaemon(true);
		return thread;
	});

	TsgHubSounds(BooleanSupplier enabled, IntSupplier volume)
	{
		this.enabled = enabled;
		this.volume = volume;
		executor.execute(this::load);
	}

	private void load()
	{
		for (Sound sound : Sound.values())
		{
			InputStream resource = TsgHubSounds.class.getResourceAsStream("sounds/" + sound.file + ".wav");
			if (resource == null) continue;
			try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(resource)))
			{
				byte[] data = in.readAllBytes();
				synchronized (samples)
				{
					samples.put(sound, new Sample(in.getFormat(), data));
				}
			}
			catch (Exception e)
			{
				log.debug("TSG Hub could not load sound {}", sound.file, e);
			}
		}
	}

	void play(Sound sound, double pitch)
	{
		if (!enabled.getAsBoolean() || volume.getAsInt() <= 0 || executor.isShutdown()) return;
		executor.execute(() -> playNow(sound, pitch));
	}

	private void playNow(Sound sound, double pitch)
	{
		Sample sample;
		synchronized (samples)
		{
			sample = samples.get(sound);
		}
		if (sample == null) return;
		byte[] data = pitch == 1 ? sample.data : pitched.computeIfAbsent(sound + ":" + pitch, k -> repitch(sample, pitch));
		Clip clip = null;
		try
		{
			clip = AudioSystem.getClip();
			clip.open(sample.format, data, 0, data.length);
			if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
			{
				FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
				gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), gain(sound))));
			}
			Clip playing = clip;
			clip.addLineListener(event -> {
				if (event.getType() == LineEvent.Type.STOP) playing.close();
			});
			clip.start();
		}
		catch (Exception e)
		{
			if (clip != null) clip.close();
			log.debug("TSG Hub could not play sound {}", sound.file, e);
		}
	}

	static byte[] repitch(byte[] data, int channels, boolean bigEndian, double pitch)
	{
		int frames = data.length / (2 * channels);
		int out = (int) (frames / pitch);
		byte[] result = new byte[out * 2 * channels];
		for (int frame = 0; frame < out; frame++)
		{
			double position = frame * pitch;
			int index = (int) position;
			double fraction = position - index;
			for (int channel = 0; channel < channels; channel++)
			{
				int a = sample(data, Math.min(index, frames - 1), channel, channels, bigEndian);
				int b = sample(data, Math.min(index + 1, frames - 1), channel, channels, bigEndian);
				int value = (int) Math.round(a + (b - a) * fraction);
				int at = (frame * channels + channel) * 2;
				result[at + (bigEndian ? 1 : 0)] = (byte) value;
				result[at + (bigEndian ? 0 : 1)] = (byte) (value >> 8);
			}
		}
		return result;
	}

	private static int sample(byte[] data, int frame, int channel, int channels, boolean bigEndian)
	{
		int at = (frame * channels + channel) * 2;
		int low = data[at + (bigEndian ? 1 : 0)] & 0xff;
		int high = data[at + (bigEndian ? 0 : 1)];
		return (high << 8) | low;
	}

	private static byte[] repitch(Sample sample, double pitch)
	{
		AudioFormat format = sample.format;
		if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || format.getSampleSizeInBits() != 16) return sample.data;
		return repitch(sample.data, format.getChannels(), format.isBigEndian(), pitch);
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
