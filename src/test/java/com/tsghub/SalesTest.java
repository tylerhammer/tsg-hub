package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.BeforeClass;
import org.junit.Test;

public class SalesTest
{
	private static final int TBOW = 20997;

	@BeforeClass
	public static void rules()
	{
		TsgHubRules.loadBundled(new Gson());
	}

	@Test
	public void onlyRaidUniques()
	{
		assertTrue(TsgHubSales.isRaidUnique("Twisted bow"));
		assertTrue(TsgHubSales.isRaidUnique("Sanguinesti staff (uncharged)"));
		assertTrue(TsgHubSales.isRaidUnique("Osmumten's fang"));
		assertFalse(TsgHubSales.isRaidUnique("Abyssal whip"));
		assertFalse(TsgHubSales.isRaidUnique("Olmlet"));
		assertFalse(TsgHubSales.isRaidUnique(null));
	}

	@Test
	public void serverRulesSetRaidUniques()
	{
		try
		{
			TsgHubRules.apply(new JsonParser().parse("{\"raidUniques\":[\"Abyssal whip\"]}").getAsJsonObject());
			assertTrue(TsgHubSales.isRaidUnique("Abyssal whip"));
			assertFalse(TsgHubSales.isRaidUnique("Twisted bow"));
			TsgHubRules.apply(new JsonObject());
			assertFalse("older servers without the list send no sales", TsgHubSales.isRaidUnique("Twisted bow"));
		}
		finally { TsgHubRules.loadBundled(new Gson()); }
	}

	@Test
	public void receivedIsAfterCappedTax()
	{
		assertEquals(9_800_000, TsgHubSales.received(10_000_000, 1));
		assertEquals(1_500_000_000L - 5_000_000, TsgHubSales.received(1_500_000_000L, 1));
		assertEquals(2 * (300_000_000L - 5_000_000), TsgHubSales.received(600_000_000L, 2));
		assertEquals(49, TsgHubSales.received(49, 1));
	}

	@Test
	public void partialFillsKeepOneIdAndCumulativeTotals()
	{
		Map.Entry<String, JsonObject> placed = TsgHubSales.update("", 26219, GrandExchangeOfferState.SELLING, 3, 20_000_000, 0, 0, "a");
		assertNull(placed.getValue());

		Map.Entry<String, JsonObject> first = TsgHubSales.update(placed.getKey(), 26219, GrandExchangeOfferState.SELLING, 3, 20_000_000, 1, 21_000_000, "b");
		assertSale(first.getValue(), "a", 26219, 1, 21_000_000 - 420_000, false);

		Map.Entry<String, JsonObject> rest = TsgHubSales.update(first.getKey(), 26219, GrandExchangeOfferState.SOLD, 3, 20_000_000, 3, 61_000_000, "c");
		assertSale(rest.getValue(), "a", 26219, 3, 61_000_000 - 3 * 406_666, true);
	}

	@Test
	public void loginReplayOfUnchangedOfferSendsNothing()
	{
		Map.Entry<String, JsonObject> sold = TsgHubSales.update("", TBOW, GrandExchangeOfferState.SOLD, 1, 1_400_000_000, 1, 1_450_000_000, "a");
		assertSale(sold.getValue(), "a", TBOW, 1, 1_445_000_000, true);

		Map.Entry<String, JsonObject> replay = TsgHubSales.update(sold.getKey(), TBOW, GrandExchangeOfferState.SOLD, 1, 1_400_000_000, 1, 1_450_000_000, "b");
		assertNull(replay.getValue());
		assertEquals(sold.getKey(), replay.getKey());
	}

	@Test
	public void fillWhileLoggedOutIsSentOnReplay()
	{
		Map.Entry<String, JsonObject> listed = TsgHubSales.update("", TBOW, GrandExchangeOfferState.SELLING, 1, 1_400_000_000, 0, 0, "a");
		Map.Entry<String, JsonObject> replay = TsgHubSales.update(listed.getKey(), TBOW, GrandExchangeOfferState.SOLD, 1, 1_400_000_000, 1, 1_400_000_000, "b");
		assertSale(replay.getValue(), "a", TBOW, 1, 1_395_000_000, true);
	}

	@Test
	public void cancelledSellWithFillsIsDone()
	{
		Map.Entry<String, JsonObject> partial = TsgHubSales.update("", 22325, GrandExchangeOfferState.SELLING, 2, 500_000_000, 1, 500_000_000, "a");
		Map.Entry<String, JsonObject> cancelled = TsgHubSales.update(partial.getKey(), 22325, GrandExchangeOfferState.CANCELLED_SELL, 2, 500_000_000, 1, 500_000_000, "b");
		assertSale(cancelled.getValue(), "a", 22325, 1, 495_000_000, true);

		Map.Entry<String, JsonObject> unsold = TsgHubSales.update("", 22325, GrandExchangeOfferState.CANCELLED_SELL, 2, 500_000_000, 0, 0, "c");
		assertNull(unsold.getValue());
	}

	@Test
	public void newOfferInSlotGetsNewId()
	{
		Map.Entry<String, JsonObject> sold = TsgHubSales.update("", TBOW, GrandExchangeOfferState.SOLD, 1, 1_400_000_000, 1, 1_400_000_000, "a");
		Map.Entry<String, JsonObject> collected = TsgHubSales.update(sold.getKey(), 0, GrandExchangeOfferState.EMPTY, 0, 0, 0, 0, "b");
		assertEquals("", collected.getKey());
		Map.Entry<String, JsonObject> again = TsgHubSales.update(collected.getKey(), TBOW, GrandExchangeOfferState.SOLD, 1, 1_400_000_000, 1, 1_400_000_000, "c");
		assertEquals("c", again.getValue().get("id").getAsString());

		Map.Entry<String, JsonObject> other = TsgHubSales.update(sold.getKey(), TBOW, GrandExchangeOfferState.SELLING, 1, 1_300_000_000, 1, 1_300_000_000, "d");
		assertEquals("d", other.getValue().get("id").getAsString());
	}

	@Test
	public void buysAreIgnored()
	{
		for (GrandExchangeOfferState state : new GrandExchangeOfferState[] {GrandExchangeOfferState.BUYING, GrandExchangeOfferState.BOUGHT, GrandExchangeOfferState.CANCELLED_BUY})
		{
			Map.Entry<String, JsonObject> buy = TsgHubSales.update("", TBOW, state, 1, 1_400_000_000, 1, 1_400_000_000, "a");
			assertNull(buy.getValue());
			assertEquals("", buy.getKey());
		}
	}

	private static void assertSale(JsonObject sale, String id, int itemId, int quantity, long gp, boolean done)
	{
		assertEquals(id, sale.get("id").getAsString());
		assertEquals(itemId, sale.get("itemId").getAsInt());
		assertEquals(quantity, sale.get("quantity").getAsInt());
		assertEquals(gp, sale.get("gp").getAsLong());
		assertEquals(done, sale.get("done").getAsBoolean());
	}
}
