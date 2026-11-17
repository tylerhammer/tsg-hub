package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import org.junit.Test;

public class CofferTest
{
	@Test
	public void firstReadIsSnapshot()
	{
		TsgHubCoffer watch = new TsgHubCoffer(null, null, null);
		JsonObject change = watch.observe(10_000_000, 1_000);
		assertEquals("snapshot", change.get("kind").getAsString());
		assertEquals(10_000_000, change.get("before").getAsLong());
		assertEquals(10_000_000, change.get("after").getAsLong());
		assertFalse(change.get("confirmed").getAsBoolean());
		assertNull(watch.observe(10_000_000, 2_000));
		assertNull(watch.observe(-1, 3_000));
	}

	@Test
	public void ownChatLineConfirmsMatchingChange()
	{
		TsgHubCoffer watch = new TsgHubCoffer(null, null, null);
		watch.observe(100, 0);
		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 1_000);
		JsonObject deposit = watch.observe(150, 2_000);
		assertEquals("deposit", deposit.get("kind").getAsString());
		assertEquals(100, deposit.get("before").getAsLong());
		assertEquals(150, deposit.get("after").getAsLong());
		assertTrue(deposit.get("confirmed").getAsBoolean());

		JsonObject next = watch.observe(200, 3_000);
		assertFalse("one line explains one change", next.get("confirmed").getAsBoolean());

		watch.chat("Zezima has withdrawn 80 coins from the clan coffer.", "Zezima", 4_000);
		JsonObject withdrawal = watch.observe(120, 5_000);
		assertEquals("withdrawal", withdrawal.get("kind").getAsString());
		assertTrue(withdrawal.get("confirmed").getAsBoolean());
	}

	@Test
	public void unconfirmedChanges()
	{
		TsgHubCoffer watch = new TsgHubCoffer(null, null, null);
		watch.observe(100, 0);
		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 1_000);
		assertFalse("too late", watch.observe(150, 1_000 + TsgHubCoffer.CHAT_WINDOW_MILLIS + 1).get("confirmed").getAsBoolean());

		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 20_000);
		JsonObject wrongWay = watch.observe(120, 21_000);
		assertEquals("withdrawal", wrongWay.get("kind").getAsString());
		assertFalse("direction must match", wrongWay.get("confirmed").getAsBoolean());

		watch.chat("Bob has withdrawn 20 coins from the clan coffer.", "Zezima", 30_000);
		assertFalse("someone else", watch.observe(100, 31_000).get("confirmed").getAsBoolean());
	}

	@Test
	public void resetStartsAgain()
	{
		TsgHubCoffer watch = new TsgHubCoffer(null, null, null);
		watch.observe(100, 0);
		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 1_000);
		watch.reset(false);
		JsonObject snapshot = watch.observe(150, 2_000);
		assertEquals("snapshot", snapshot.get("kind").getAsString());
		assertFalse(watch.observe(200, 3_000).get("confirmed").getAsBoolean());
	}

	@Test
	public void chatKind()
	{
		assertEquals("deposit", TsgHubCoffer.chatKind("You've donated 1,000 coins to the clan coffer.", "Zezima"));
		assertEquals("deposit", TsgHubCoffer.chatKind("Iron Zezima deposited 5 coins into the coffer.", "Iron Zezima"));
		assertEquals("withdrawal", TsgHubCoffer.chatKind("You withdrew 5 coins from the coffer.", "Zezima"));
		assertNull(TsgHubCoffer.chatKind("You deposited 5 coins into the bank.", "Zezima"));
		assertNull(TsgHubCoffer.chatKind("You can't withdraw from the coffer.", "Zezima"));
		assertNull(TsgHubCoffer.chatKind("Zezima2 deposited 5 coins into the coffer.", "Zezima"));
		assertNull(TsgHubCoffer.chatKind("Bob deposited 5 coins into the coffer.", ""));
		assertNull(TsgHubCoffer.chatKind(null, "Zezima"));
	}
}
