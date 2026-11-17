package com.tsghub;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;
import lombok.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.util.*;

@RequiredArgsConstructor
final class TsgHubCoffer
{
	static final long CHAT_WINDOW_MILLIS = 15_000;
	private static final int FIRST_READ_TICKS = 3;
	private static final int READ_EVERY_TICKS = 2;
	private static final Set<ChatMessageType> PLAYER_CHAT = EnumSet.of(ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
		ChatMessageType.AUTOTYPER, ChatMessageType.MODAUTOTYPER, ChatMessageType.PRIVATECHAT, ChatMessageType.MODPRIVATECHAT,
		ChatMessageType.PRIVATECHATOUT, ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.CLAN_GIM_CHAT);

	private final TsgHubPlugin plugin;
	private final Client client;
	private final Consumer<JsonObject> report;
	private boolean open;
	private int openTicks;
	private long balance = -1;
	private String chatKind;
	private long chatAt;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.CLANS_STORAGE_MAIN) reset(true);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.CLANS_STORAGE_MAIN) reset(false);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN) reset(false);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!open || event.getMessage() == null || PLAYER_CHAT.contains(event.getType())) return;
		String sender = event.getName() == null ? "" : Text.removeTags(event.getName()).trim();
		String line = Text.removeTags(event.getMessage()).trim();
		chat(sender.isEmpty() ? line : sender + " " + line, selfName(), System.currentTimeMillis());
	}

	void chat(String line, String self, long now)
	{
		String kind = chatKind(line, self);
		if (kind == null) return;
		chatKind = kind;
		chatAt = now;
	}

	JsonObject observe(long coins, long now)
	{
		if (coins < 0 || coins == balance) return null;
		String kind = balance < 0 ? "snapshot" : coins > balance ? "deposit" : "withdrawal";
		JsonObject change = new JsonObject();
		change.addProperty("kind", kind);
		change.addProperty("before", balance < 0 ? coins : balance);
		change.addProperty("after", coins);
		change.addProperty("confirmed", balance >= 0 && kind.equals(chatKind) && now >= chatAt && now - chatAt <= CHAT_WINDOW_MILLIS);
		if (balance >= 0)
		{
			chatKind = null;
			chatAt = 0;
		}
		balance = coins;
		return change;
	}

	static String chatKind(String line, String self)
	{
		if (line == null) return null;
		String text = PlayerNames.key(line);
		if (!text.contains("coffer")) return null;
		String name = PlayerNames.key(self);
		boolean mine = text.startsWith("you ") || text.startsWith("you've ") || (!name.isEmpty() && text.startsWith(name + " "));
		if (!mine) return null;
		if (text.contains("deposited") || text.contains("donated")) return "deposit";
		if (text.contains("withdrew") || text.contains("withdrawn")) return "withdrawal";
		return null;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!open) return;
		if (!plugin.sharingInClan())
		{
			reset(true);
			return;
		}
		openTicks++;
		if (openTicks < FIRST_READ_TICKS || (openTicks - FIRST_READ_TICKS) % READ_EVERY_TICKS != 0) return;
		JsonObject change = observe(readCoins(), System.currentTimeMillis());
		if (change != null) send(change);
	}

	void reset(boolean opened)
	{
		open = opened;
		openTicks = 0;
		balance = -1;
		chatKind = null;
		chatAt = 0;
	}

	private long readCoins()
	{
		Widget root = client.getWidget(InterfaceID.ClansStorageMain.ITEMS);
		if (root == null || root.isHidden()) return -1;
		return coins(root);
	}

	static long coins(Widget root)
	{
		long[] total = {0};
		TsgHubRedaction.walk(root, false, w -> { if (w.getItemId() == ItemID.COINS) total[0] += Math.max(0, w.getItemQuantity()); });
		return total[0];
	}

	private String selfName()
	{
		Player self = client.getLocalPlayer();
		return self == null || self.getName() == null ? "" : Text.toJagexName(self.getName());
	}

	private void send(JsonObject body)
	{
		String name = plugin.clan().playerName().isEmpty() ? selfName() : plugin.clan().playerName();
		if (client.getAccountHash() == -1 || name.isEmpty()) return;
		JsonObject identity = plugin.clan().identity(name);
		body.entrySet().forEach(e -> identity.add(e.getKey(), e.getValue()));
		report.accept(identity);
	}
}
