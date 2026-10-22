package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CofferTest
{
	@Test
	public void firstReadIsSnapshot()
	{
		TsgHubCofferWatch watch = new TsgHubCofferWatch();
		TsgHubCofferWatch.Change change = watch.observe(10_000_000, 1_000);
		assertEquals(TsgHubCofferWatch.Kind.SNAPSHOT, change.kind);
		assertEquals(10_000_000, change.before);
		assertEquals(10_000_000, change.after);
		assertFalse(change.confirmed);
		assertNull(watch.observe(10_000_000, 2_000));
		assertNull(watch.observe(-1, 3_000));
	}

	@Test
	public void ownChatLineConfirmsMatchingChange()
	{
		TsgHubCofferWatch watch = new TsgHubCofferWatch();
		watch.observe(100, 0);
		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 1_000);
		TsgHubCofferWatch.Change deposit = watch.observe(150, 2_000);
		assertEquals(TsgHubCofferWatch.Kind.DEPOSIT, deposit.kind);
		assertEquals(100, deposit.before);
		assertEquals(150, deposit.after);
		assertTrue(deposit.confirmed);

		TsgHubCofferWatch.Change next = watch.observe(200, 3_000);
		assertFalse("one line explains one change", next.confirmed);

		watch.chat("Zezima has withdrawn 80 coins from the clan coffer.", "Zezima", 4_000);
		TsgHubCofferWatch.Change withdrawal = watch.observe(120, 5_000);
		assertEquals(TsgHubCofferWatch.Kind.WITHDRAWAL, withdrawal.kind);
		assertTrue(withdrawal.confirmed);
	}

	@Test
	public void unconfirmedChanges()
	{
		TsgHubCofferWatch watch = new TsgHubCofferWatch();
		watch.observe(100, 0);
		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 1_000);
		assertFalse("too late", watch.observe(150, 1_000 + TsgHubCofferWatch.CHAT_WINDOW_MILLIS + 1).confirmed);

		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 20_000);
		TsgHubCofferWatch.Change wrongWay = watch.observe(120, 21_000);
		assertEquals(TsgHubCofferWatch.Kind.WITHDRAWAL, wrongWay.kind);
		assertFalse("direction must match", wrongWay.confirmed);

		watch.chat("Bob has withdrawn 20 coins from the clan coffer.", "Zezima", 30_000);
		assertFalse("someone else", watch.observe(100, 31_000).confirmed);
	}

	@Test
	public void resetStartsAgain()
	{
		TsgHubCofferWatch watch = new TsgHubCofferWatch();
		watch.observe(100, 0);
		watch.chat("You deposited 50 coins into the coffer.", "Zezima", 1_000);
		watch.reset();
		TsgHubCofferWatch.Change snapshot = watch.observe(150, 2_000);
		assertEquals(TsgHubCofferWatch.Kind.SNAPSHOT, snapshot.kind);
		assertFalse(watch.observe(200, 3_000).confirmed);
	}

	@Test
	public void chatKind()
	{
		assertEquals(TsgHubCofferWatch.Kind.DEPOSIT, TsgHubCofferWatch.chatKind("You've donated 1,000 coins to the clan coffer.", "Zezima"));
		assertEquals(TsgHubCofferWatch.Kind.DEPOSIT, TsgHubCofferWatch.chatKind("Iron Zezima deposited 5 coins into the coffer.", "Iron Zezima"));
		assertEquals(TsgHubCofferWatch.Kind.WITHDRAWAL, TsgHubCofferWatch.chatKind("You withdrew 5 coins from the coffer.", "Zezima"));
		assertNull(TsgHubCofferWatch.chatKind("You deposited 5 coins into the bank.", "Zezima"));
		assertNull(TsgHubCofferWatch.chatKind("You can't withdraw from the coffer.", "Zezima"));
		assertNull(TsgHubCofferWatch.chatKind("Zezima2 deposited 5 coins into the coffer.", "Zezima"));
		assertNull(TsgHubCofferWatch.chatKind("Bob deposited 5 coins into the coffer.", ""));
		assertNull(TsgHubCofferWatch.chatKind(null, "Zezima"));
	}
}
