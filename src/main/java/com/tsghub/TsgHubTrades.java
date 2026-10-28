package com.tsghub;

import static com.tsghub.TsgHubJson.*;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@RequiredArgsConstructor
final class TsgHubTrades
{
	static final long PLATINUM_GP = 1000;
	private static final int ACCEPT_TICKS = 2;

	private final TsgHubPlugin plugin;
	private final Client client;
	private String partner = "";
	private long gp;
	private int closedTick = -1;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.TRADECONFIRM) return;
		Widget opponent = client.getWidget(InterfaceID.Tradeconfirm.TRADEOPPONENT);
		partner = opponent == null ? "" : partnerName(opponent.getText());
		gp = coins(client.getItemContainer(InventoryID.TRADEOFFER));
		closedTick = -1;
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.TRADECONFIRM) closedTick = client.getTickCount();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		partner = "";
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (partner.isEmpty() || !"Accepted trade.".equals(Text.removeTags(event.getMessage()).trim())) return;
		String to = partner;
		long paid = gp;
		boolean recent = closedTick < 0 || client.getTickCount() - closedTick <= ACCEPT_TICKS;
		partner = "";
		if (!recent || paid <= 0 || !plugin.sharingInClan()) return;
		JsonObject group = plugin.groups().currentGroup();
		if (group == null || owed(object(group, "split"), plugin.session().get(TsgHubSession.GROUP_PLAYER), to) <= 0) return;
		JsonObject payment = new JsonObject();
		payment.addProperty("to", to);
		payment.addProperty("gp", paid);
		plugin.signals().send("payment", payment);
	}

	static String partnerName(String text)
	{
		String plain = Text.removeTags(text == null ? "" : text.replace("<br>", " ")).trim();
		int colon = plain.lastIndexOf(':');
		return Text.toJagexName(colon < 0 ? plain : plain.substring(colon + 1).trim());
	}

	static long coins(ItemContainer offer)
	{
		if (offer == null) return 0;
		long total = 0;
		for (Item item : offer.getItems())
		{
			if (item.getId() == ItemID.COINS) total += item.getQuantity();
			else if (item.getId() == ItemID.PLATINUM) total += item.getQuantity() * PLATINUM_GP;
		}
		return total;
	}

	static long owed(JsonObject split, String self, String to)
	{
		long total = 0;
		for (JsonObject transfer : objects(array(split, "transfers")))
		{
			if (PlayerNames.samePlayer(str(transfer, "from"), self) && PlayerNames.samePlayer(str(transfer, "to"), to)) total += longValue(transfer, "amount", 0);
		}
		return total;
	}
}
