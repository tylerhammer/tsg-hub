package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ChatQueueTest
{
	private static List<TsgHubChatQueue.Line> settle(TsgHubChatQueue queue)
	{
		for (int i = 0; i < TsgHubChatQueue.DELAY_TICKS; i++) assertTrue(queue.tick().isEmpty());
		return queue.tick();
	}

	@Test
	public void waitsForLoginTicks()
	{
		TsgHubChatQueue queue = new TsgHubChatQueue();
		queue.add("Hub", "Turn on location sharing");
		assertEquals(List.of(new TsgHubChatQueue.Line("Hub", "Turn on location sharing")), settle(queue));
		assertTrue(queue.tick().isEmpty());
	}

	@Test
	public void sendsRightAwayOnceSettled()
	{
		TsgHubChatQueue queue = new TsgHubChatQueue();
		settle(queue);
		queue.add(null, "Hello");
		assertEquals(List.of(new TsgHubChatQueue.Line(TsgHubChatQueue.DEFAULT_TAG, "Hello")), queue.tick());
	}

	@Test
	public void logoutDropsPendingAndRestartsDelay()
	{
		TsgHubChatQueue queue = new TsgHubChatQueue();
		settle(queue);
		queue.add("Hub", "Stale");
		queue.loggedOut();
		queue.add("Hub", "Fresh");
		assertEquals(List.of(new TsgHubChatQueue.Line("Hub", "Fresh")), settle(queue));
	}

	@Test
	public void cleansAndLimits()
	{
		TsgHubChatQueue queue = new TsgHubChatQueue();
		queue.add("  ", "   ");
		queue.add(" A\nlong   tag that goes past the limit ", "two\n\nlines");
		for (int i = 0; i < TsgHubChatQueue.MAX_PENDING + 5; i++) queue.add("Hub", "x" + i);
		List<TsgHubChatQueue.Line> lines = settle(queue);
		assertEquals(TsgHubChatQueue.MAX_PENDING, lines.size());
		assertEquals("A long tag that goes pas", lines.get(0).getTag());
		assertEquals("two lines", lines.get(0).getText());
	}
}
