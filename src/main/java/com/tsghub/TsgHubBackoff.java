package com.tsghub;

import java.util.concurrent.ThreadLocalRandom;

final class TsgHubBackoff
{
	private final long minMillis;
	private final long maxMillis;
	private int attempts;

	TsgHubBackoff(long minMillis, long maxMillis)
	{
		this.minMillis = minMillis;
		this.maxMillis = maxMillis;
	}

	synchronized long next()
	{
		long ceiling = Math.min(maxMillis, minMillis << Math.min(attempts++, 16));
		return ceiling / 2 + ThreadLocalRandom.current().nextLong(ceiling / 2 + 1);
	}

	synchronized void reset()
	{
		attempts = 0;
	}
}
