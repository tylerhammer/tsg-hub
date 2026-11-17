package com.tsghub;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

public class RevealSoundsTest
{
	@Test
	public void shortLinesClimbToTheOctave()
	{
		assertArrayEquals(new int[]{0, 7, 12}, TsgHubRevealOverlay.noteSteps(3));
		assertArrayEquals(new int[]{0, 4, 7, 12}, TsgHubRevealOverlay.noteSteps(4));
		assertArrayEquals(new int[]{0, 4, 7, 9, 12}, TsgHubRevealOverlay.noteSteps(5));
		assertArrayEquals(new int[]{0, 2, 4, 5, 7, 9}, TsgHubRevealOverlay.noteSteps(6));
		assertArrayEquals(new int[]{0, 2, 4, 5, 7, 9, 11, 12, 14}, TsgHubRevealOverlay.noteSteps(9));
	}

	@Test
	public void tileStampCues()
	{
		List<TsgHubRevealOverlay.Cue> cues = TsgHubRevealOverlay.cues(moment(3, Collections.singletonList("t4"), false));
		assertEquals(Arrays.asList(TsgHubSounds.Sound.BOARD_IN, TsgHubSounds.Sound.DAUBER_DROP, TsgHubSounds.Sound.DAUBER_HIT, TsgHubSounds.Sound.POINTS), sounds(cues));
		assertEquals(Arrays.asList(0, 450, 710, 810), times(cues));
	}

	@Test
	public void lineClearCuesMatchTheAnimation()
	{
		TsgHubReveals.Moment moment = moment(3, Collections.singletonList("t4"), true);
		List<TsgHubRevealOverlay.Cue> cues = TsgHubRevealOverlay.cues(moment);
		List<TsgHubRevealOverlay.Cue> notes = new ArrayList<>();
		for (TsgHubRevealOverlay.Cue cue : cues) if (cue.sound == TsgHubSounds.Sound.LINE_NOTE) notes.add(cue);
		assertEquals(3, notes.size());
		assertEquals(TsgHubRevealOverlay.lineAt(moment, 0), notes.get(0).at);
		assertEquals(TsgHubRevealOverlay.lineAt(moment, 0) + 2 * TsgHubRevealOverlay.WAVE_STEP_MS, notes.get(2).at);
		assertEquals(0, notes.get(0).step);
		assertEquals(7, notes.get(1).step);
		assertEquals(12, notes.get(2).step);
		TsgHubRevealOverlay.Cue last = cues.get(cues.size() - 1);
		assertEquals(TsgHubSounds.Sound.BINGO_STAMP, last.sound);
		assertEquals(TsgHubRevealOverlay.stampAt(moment) + TsgHubRevealOverlay.STAMP_MS / 2 - TsgHubRevealOverlay.STAMP_SOUND_LEAD_MS, last.at);
		assertTrue(times(cues).contains(TsgHubRevealOverlay.strikeAt(moment, 0)));
		for (int i = 1; i < cues.size(); i++) assertTrue(cues.get(i - 1).at <= cues.get(i).at);
	}

	@Test
	public void offBoardTilesOnlyPlayThePoints()
	{
		List<TsgHubRevealOverlay.Cue> cues = TsgHubRevealOverlay.cues(moment(3, Collections.singletonList("extra"), false));
		assertEquals(Arrays.asList(TsgHubSounds.Sound.BOARD_IN, TsgHubSounds.Sound.POINTS), sounds(cues));
	}

	@Test
	public void bundledSoundsArePlayableWav() throws Exception
	{
		List<String> paths = new ArrayList<>();
		for (TsgHubSounds.Sound sound : TsgHubSounds.Sound.values()) paths.add(sound.path(0));
		for (int step : TsgHubRevealOverlay.noteSteps(9)) paths.add(TsgHubSounds.Sound.LINE_NOTE.path(step));
		for (String path : paths)
		{
			try (InputStream in = TsgHubSounds.class.getResourceAsStream(path); AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(in)))
			{
				assertEquals(44100, audio.getFormat().getSampleRate(), 0);
				assertEquals(16, audio.getFormat().getSampleSizeInBits());
			}
		}
	}

	private static List<TsgHubSounds.Sound> sounds(List<TsgHubRevealOverlay.Cue> cues)
	{
		List<TsgHubSounds.Sound> sounds = new ArrayList<>();
		for (TsgHubRevealOverlay.Cue cue : cues) sounds.add(cue.sound);
		return sounds;
	}

	private static List<Integer> times(List<TsgHubRevealOverlay.Cue> cues)
	{
		List<Integer> times = new ArrayList<>();
		for (TsgHubRevealOverlay.Cue cue : cues) times.add(cue.at);
		return times;
	}

	private static TsgHubReveals.Moment moment(int size, List<String> tiles, boolean line)
	{
		JsonObject event = new JsonObject();
		JsonArray list = new JsonArray();
		Map<String, JsonObject> tasks = new HashMap<>();
		for (int i = 0; i < size * size; i++)
		{
			JsonObject task = new JsonObject();
			task.addProperty("id", "t" + i);
			task.addProperty("points", 1);
			list.add(task);
			tasks.put("t" + i, task);
		}
		event.add("tasks", list);
		JsonObject board = new JsonObject();
		board.addProperty("size", size);
		JsonArray cells = new JsonArray();
		for (int i = 0; i < size * size; i++)
		{
			JsonObject tile = new JsonObject();
			tile.addProperty("taskId", "t" + i);
			tile.addProperty("row", i / size);
			tile.addProperty("col", i % size);
			cells.add(tile);
		}
		board.add("tiles", cells);
		event.add("board", board);
		List<TsgHubBingoBoard.Line> lines = line ? Collections.singletonList(new TsgHubBingoBoard.Line(TsgHubBingoBoard.Direction.ROW, 1)) : Collections.emptyList();
		return new TsgHubReveals.Moment("", "Test", TsgHubBingoBoard.of(event), tasks, new HashMap<>(), new HashSet<>(), tiles, lines, Collections.emptyList(), Collections.emptyList());
	}
}
