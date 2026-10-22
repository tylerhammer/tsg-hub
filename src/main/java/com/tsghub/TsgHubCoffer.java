package com.tsghub;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

@RequiredArgsConstructor
final class TsgHubCoffer
{
	private static final int FIRST_READ_TICKS = 3;
	private static final int READ_EVERY_TICKS = 2;
	private static final Set<ChatMessageType> PLAYER_CHAT = EnumSet.of(ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
		ChatMessageType.AUTOTYPER, ChatMessageType.MODAUTOTYPER, ChatMessageType.PRIVATECHAT, ChatMessageType.MODPRIVATECHAT,
		ChatMessageType.PRIVATECHATOUT, ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT,
		ChatMessageType.CLAN_GIM_CHAT);

	private final TsgHubPlugin plugin;
	private final Client client;
	private final Consumer<JsonObject> report;
	private final TsgHubCofferWatch watch = new TsgHubCofferWatch();
	private boolean open;
	private int openTicks;

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
		watch.chat(sender.isEmpty() ? line : sender + " " + line, selfName(), System.currentTimeMillis());
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!open) return;
		if (!plugin.sharingInClan())
		{
			openTicks = 0;
			watch.reset();
			return;
		}
		openTicks++;
		if (openTicks < FIRST_READ_TICKS || (openTicks - FIRST_READ_TICKS) % READ_EVERY_TICKS != 0) return;
		TsgHubCofferWatch.Change change = watch.observe(readCoins(), System.currentTimeMillis());
		if (change != null) send(change);
	}

	private void reset(boolean opened)
	{
		open = opened;
		openTicks = 0;
		watch.reset();
	}

	private long readCoins()
	{
		Widget root = client.getWidget(InterfaceID.ClansStorageMain.ITEMS);
		if (root == null || root.isHidden()) return -1;
		return coins(root);
	}

	static long coins(Widget root)
	{
		Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<Widget> queue = new ArrayDeque<>();
		queue.add(root);
		long total = 0;
		while (!queue.isEmpty())
		{
			Widget widget = queue.poll();
			if (!seen.add(widget)) continue;
			if (widget.getItemId() == ItemID.COINS) total += Math.max(0, widget.getItemQuantity());
			for (Widget[] children : new Widget[][]{widget.getChildren(), widget.getDynamicChildren(), widget.getStaticChildren(), widget.getNestedChildren()})
			{
				if (children == null) continue;
				for (Widget child : children) if (child != null) queue.add(child);
			}
		}
		return total;
	}

	private String selfName()
	{
		Player self = client.getLocalPlayer();
		return self == null || self.getName() == null ? "" : Text.toJagexName(self.getName());
	}

	private void send(TsgHubCofferWatch.Change change)
	{
		long hash = client.getAccountHash();
		String name = plugin.clan().playerName().isEmpty() ? selfName() : plugin.clan().playerName();
		if (hash == -1 || name.isEmpty()) return;
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.clan().name());
		body.addProperty("displayName", name);
		body.addProperty("accountHash", Long.toString(hash));
		body.addProperty("kind", change.kind.wire());
		body.addProperty("before", change.before);
		body.addProperty("after", change.after);
		body.addProperty("confirmed", change.confirmed);
		report.accept(body);
	}
}
