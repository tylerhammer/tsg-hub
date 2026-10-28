package com.tsghub;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@RequiredArgsConstructor
final class TsgHubSales
{
	static final long TAX_CAP = 5_000_000;
	private static final Pattern SUFFIX = Pattern.compile("(\\s*\\([^)]*\\))+$");
	static final class Update
	{
		final String saved;
		final JsonObject sale;

		Update(String saved, JsonObject sale)
		{
			this.saved = saved;
			this.sale = sale;
		}
	}

	private final TsgHubPlugin plugin;
	private final Client client;
	private final ItemManager itemManager;
	private boolean rescan = true;

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY && client.getGameState() != GameState.LOGGED_IN) return;
		if (!plugin.sharingInClan())
		{
			rescan = true;
			return;
		}
		offer(event.getSlot(), offer);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!rescan || !plugin.sharingInClan()) return;
		rescan = false;
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null) return;
		for (int slot = 0; slot < offers.length; slot++) if (offers[slot] != null) offer(slot, offers[slot]);
	}

	private void offer(int slot, GrandExchangeOffer offer)
	{
		TsgHubSession session = plugin.session();
		String key = TsgHubSession.GE_OFFER + slot;
		boolean unique = offer.getState() != GrandExchangeOfferState.EMPTY && isRaidUnique(itemManager.getItemComposition(offer.getItemId()).getName());
		String saved = session.get(key);
		Update update = unique ? update(saved, offer.getItemId(), offer.getState(), offer.getTotalQuantity(), offer.getPrice(), offer.getQuantitySold(), offer.getSpent(), TsgHubSignals.newId()) : new Update("", null);
		if (!update.saved.equals(saved)) session.set(key, update.saved);
		if (update.sale != null) plugin.signals().send("sale", update.sale);
	}

	static boolean isRaidUnique(String name)
	{
		return name != null && TsgHubRules.get().raidUniques.contains(SUFFIX.matcher(name.trim()).replaceAll("").toLowerCase(Locale.ROOT));
	}

	static Update update(String saved, int itemId, GrandExchangeOfferState state, int total, long price, int sold, long spent, String freshId)
	{
		boolean done = state == GrandExchangeOfferState.SOLD || state == GrandExchangeOfferState.CANCELLED_SELL;
		if (!done && state != GrandExchangeOfferState.SELLING) return new Update("", null);
		String[] parts = saved == null ? new String[0] : saved.split(";");
		String offer = itemId + ";" + total + ";" + price;
		String progress = sold + ";" + spent + ";" + done;
		boolean same = parts.length == 7 && offer.equals(parts[1] + ";" + parts[2] + ";" + parts[3]);
		String id = same ? parts[0] : freshId;
		String next = id + ";" + offer + ";" + progress;
		if (sold <= 0 || same && progress.equals(parts[4] + ";" + parts[5] + ";" + parts[6])) return new Update(next, null);
		JsonObject sale = new JsonObject();
		sale.addProperty("id", id);
		sale.addProperty("itemId", itemId);
		sale.addProperty("quantity", sold);
		sale.addProperty("gp", received(spent, sold));
		sale.addProperty("done", done);
		return new Update(next, sale);
	}

	// getSpent() on a sell offer is the gross coins traded, before tax; RuneLite's GE plugin and the
	// flipping plugins treat it that way. The seller keeps that minus 2% tax per item, capped at 5m each.
	static long received(long spent, int sold)
	{
		if (sold <= 0) return spent;
		return spent - Math.min(spent / sold * 2 / 100, TAX_CAP) * sold;
	}
}
