package com.tsghub;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

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

	static final class Sample
	{
		final byte[] wav;
		final int channels;
		final int rate;
		final int bits;
		final int dataStart;
		final int dataLength;

		Sample(byte[] wav, int channels, int rate, int bits, int dataStart, int dataLength)
		{
			this.wav = wav;
			this.channels = channels;
			this.rate = rate;
			this.bits = bits;
			this.dataStart = dataStart;
			this.dataLength = dataLength;
		}
	}

	private final Map<Sound, Sample> samples = new EnumMap<>(Sound.class);
	private final Map<String, byte[]> pitched = new HashMap<>();
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
		executor.execute(this::load);
	}

	private void load()
	{
		for (Sound sound : Sound.values())
		{
			try (InputStream resource = TsgHubSounds.class.getResourceAsStream("sounds/" + sound.file + ".wav"))
			{
				if (resource == null) continue;
				Sample sample = parse(resource.readAllBytes());
				if (sample == null) continue;
				synchronized (samples)
				{
					samples.put(sound, sample);
				}
			}
			catch (Exception e)
			{
				log.debug("TSG Hub could not load sound {}", sound.file, e);
			}
		}
	}

	static Sample parse(byte[] wav)
	{
		if (wav.length < 12) return null;
		ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
		int channels = 0;
		int rate = 0;
		int bits = 0;
		int at = 12;
		while (at + 8 <= wav.length)
		{
			String id = new String(wav, at, 4, StandardCharsets.US_ASCII);
			int length = buffer.getInt(at + 4);
			int body = at + 8;
			if (length < 0 || body + length > wav.length) length = wav.length - body;
			if (id.equals("fmt ") && length >= 16)
			{
				if (buffer.getShort(body) != 1) return null;
				channels = buffer.getShort(body + 2);
				rate = buffer.getInt(body + 4);
				bits = buffer.getShort(body + 14);
			}
			else if (id.equals("data"))
			{
				return channels > 0 ? new Sample(wav, channels, rate, bits, body, length) : null;
			}
			at = body + length + (length & 1);
		}
		return null;
	}

	static byte[] wav(byte[] data, int channels, int rate, int bits)
	{
		int block = channels * bits / 8;
		ByteBuffer buffer = ByteBuffer.allocate(44 + data.length).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + data.length).put(new byte[]{'W', 'A', 'V', 'E'});
		buffer.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) channels).putInt(rate).putInt(rate * block).putShort((short) block).putShort((short) bits);
		buffer.put(new byte[]{'d', 'a', 't', 'a'}).putInt(data.length).put(data);
		return buffer.array();
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
		byte[] wav = pitch == 1 ? sample.wav : pitched.computeIfAbsent(sound + ":" + pitch, k -> repitch(sample, pitch));
		try
		{
			audioPlayer.play(new ByteArrayInputStream(wav), gain(sound));
		}
		catch (Exception e)
		{
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
		if (sample.bits != 16) return sample.wav;
		byte[] data = Arrays.copyOfRange(sample.wav, sample.dataStart, sample.dataStart + sample.dataLength);
		return wav(repitch(data, sample.channels, false, pitch), sample.channels, sample.rate, 16);
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
