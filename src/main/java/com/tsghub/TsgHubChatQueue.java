package com.tsghub;

import java.util.*;
import lombok.*;

// Holds server chat messages until the player has been logged in for a few ticks, so they land after
// the login and clan chat lines instead of among them.
final class TsgHubChatQueue
{
	static final String DEFAULT_TAG = "TSG Hub";
	static final int DELAY_TICKS = 5;
	static final int MAX_PENDING = 10;
	static final int MAX_TAG = 24;
	static final int MAX_TEXT = 300;

	@Value
	static class Line
	{
		String tag;
		String text;
	}

	private final Deque<Line> pending = new ArrayDeque<>();
	private int ticks;

	synchronized void add(String tag, String text)
	{
		String body = clean(text, MAX_TEXT);
		if (body.isEmpty() || pending.size() >= MAX_PENDING) return;
		String label = clean(tag, MAX_TAG);
		pending.add(new Line(label.isEmpty() ? DEFAULT_TAG : label, body));
	}

	// Called once per game tick, which only fires while logged in.
	synchronized List<Line> tick()
	{
		if (ticks < DELAY_TICKS)
		{
			ticks++;
			return List.of();
		}
		List<Line> ready = new ArrayList<>(pending);
		pending.clear();
		return ready;
	}

	synchronized void loggedOut()
	{
		ticks = 0;
		pending.clear();
	}

	private static String clean(String value, int limit)
	{
		String flat = value == null ? "" : value.replaceAll("\\s+", " ").trim();
		return flat.length() > limit ? flat.substring(0, limit).trim() : flat;
	}
}
