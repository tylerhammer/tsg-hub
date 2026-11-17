package com.tsghub;

import com.google.gson.*;
import com.tsghub.TsgHubUi.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import net.runelite.api.*;
import net.runelite.client.chat.*;
import net.runelite.client.config.*;
import net.runelite.client.util.*;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubJson.*;

@RequiredArgsConstructor
final class TsgHubChat
{
	private final TsgHubPlugin plugin;
	private final TsgHubConfig config;
	private final ConfigManager configManager;
	private final ChatMessageManager chatMessageManager;
	private static final int MAX_ECHOES = 20;
	private final Queue<String> localMessages = new ConcurrentLinkedDeque<>();
	private volatile String notifiedUpdate = "";
	private volatile boolean mapNoticeChecked;
	private final TsgHubChatQueue queued = new TsgHubChatQueue();

	boolean isOwnEcho(String message)
	{
		return localMessages.remove(Text.removeTags(message));
	}

	void onLoggedOut()
	{
		mapNoticeChecked = false;
		queued.loggedOut();
	}

	void reset()
	{
		localMessages.clear();
		queued.loggedOut();
		notifiedUpdate = "";
		mapNoticeChecked = false;
	}

	void reply(JsonObject message)
	{
		String eventId = str(message, "eventId");
		switch (str(message, "type"))
		{
			case "progress":
				try
				{
					long gained = Long.parseLong(str(message, "gained"));
					String rank = str(message, "rank");
					Long parsedRank = rank.isEmpty() ? null : Long.parseLong(rank);
					plugin.ui(s -> s.competitionView.competitionProgress(eventId, gained, parsedRank));
				}
				catch (NumberFormatException ignored) { /* Malformed progress waits for the next report. */ }
				break;
			case "claimed":
				progress(str(message, "taskTitle"), str(message, "progress"), str(message, "complete"));
				if (!plugin.live() && eventId.equals(plugin.session().get(EVENT_ID))) plugin.sessions().refreshBoard();
				break;
			case "notice":
				String text = str(message, "text");
				if (!text.isEmpty()) plugin.memberStatus(text, "error".equals(str(message, "tone")) ? Tone.ERROR : Tone.INFO);
				break;
			default:
		}
	}

	boolean notifications(JsonArray notifications)
	{
		if (notifications == null) return false;
		for (JsonObject notification : objects(notifications))
		{
			if (!"task-completed".equals(str(notification, "type"))) continue;
			local(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL).append(str(notification, "actorName") + " completed ")
				.append(ChatColorType.HIGHLIGHT).append(str(notification, "taskTitle"))
				.append(ChatColorType.NORMAL).append(" for " + str(notification, "teamName") + "!"));
		}
		return notifications.size() > 0;
	}

	private void progress(String title, String count, String complete)
	{
		if (title.isEmpty() || count.isEmpty()) return;
		ChatMessageBuilder message = new ChatMessageBuilder();
		if (!complete.isEmpty())
		{
			message.append(TsgHubTheme.CHAT_SUCCESS, "task".equals(complete) ? "Task complete: " : "Your part is done: ")
				.append(ChatColorType.HIGHLIGHT).append(title)
				.append(ChatColorType.NORMAL).append(" (" + count + ")");
		}
		else message.append(ChatColorType.HIGHLIGHT).append(title).append(ChatColorType.NORMAL).append(": " + count);
		local(message);
	}

	void announce(String text)
	{
		if (!config.eventAnnouncements()) return;
		clan("Event", new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	void clanAnnouncement(String tag, String text)
	{
		if (!config.clanAnnouncements()) return;
		clan(tag, new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	void splitNotice(String text)
	{
		local(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	void defenceSpec(String text)
	{
		if (config.defenceChat()) local(new ChatMessageBuilder().append(ChatColorType.NORMAL).append(Text.escapeJagex(text)));
	}

	void updateAvailable(String version)
	{
		if (version.equals(notifiedUpdate)) return;
		notifiedUpdate = version;
		plugin.ui(s -> s.homeView.setUpdate(version));
		local(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("TSG Hub " + Text.escapeJagex(version) + " is available. Restart RuneLite to update."));
	}

	void message(String tag, String text)
	{
		queued.add(tag, text);
	}

	void onGameTick()
	{
		for (TsgHubChatQueue.Line line : queued.tick())
		{
			local(line.getTag(), new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(line.getText())));
		}
	}

	void mapNotice()
	{
		if (mapNoticeChecked) return;
		mapNoticeChecked = true;
		if (config.mapNoticeShown()) return;
		configManager.setConfiguration("tsghub", "mapNoticeShown", true);
		if (plugin.locationSharingEnabled()) local(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("Your position now shows on clanmates' world maps. Turn off Share location and activity to stop."));
	}

	private void local(ChatMessageBuilder body)
	{
		local(TsgHubChatQueue.DEFAULT_TAG, body);
	}

	private void local(String tag, ChatMessageBuilder body)
	{
		String message = new ChatMessageBuilder().append(config.chatColor(), "[" + Text.escapeJagex(tag) + "] ").build() + body.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
	}

	private void clan(String tag, ChatMessageBuilder body)
	{
		TsgHubClan clan = plugin.clan();
		if (!clan.isHubClan())
		{
			local(body);
			return;
		}
		String message = new ChatMessageBuilder().append(config.chatColor(), "[" + tag + "] ").build() + body.build().replace("<colHIGHLIGHT>", ColorUtil.colorTag(config.chatColor()));
		localMessages.add(Text.removeTags(message));
		while (localMessages.size() > MAX_ECHOES) localMessages.poll();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CLAN_MESSAGE).sender(clan.name()).runeLiteFormattedMessage(message).build());
	}
}
