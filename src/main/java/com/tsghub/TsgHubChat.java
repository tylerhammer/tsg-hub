package com.tsghub;

import static com.tsghub.TsgHubSession.EVENT_ID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@RequiredArgsConstructor
final class TsgHubChat
{
	private final TsgHubPlugin plugin;
	private final TsgHubConfig config;
	private final ConfigManager configManager;
	private final ChatMessageManager chatMessageManager;
	private final Set<String> localMessages = ConcurrentHashMap.newKeySet();
	private volatile String notifiedUpdate = "";
	private volatile boolean mapNoticeChecked;

	boolean isOwnEcho(String message)
	{
		return localMessages.remove(Text.removeTags(message));
	}

	void onLoggedOut()
	{
		mapNoticeChecked = false;
	}

	void reset()
	{
		localMessages.clear();
		notifiedUpdate = "";
		mapNoticeChecked = false;
	}

	void reply(JsonObject message)
	{
		String eventId = TsgHubJson.str(message, "eventId");
		switch (TsgHubJson.str(message, "type"))
		{
			case "progress":
				try
				{
					long gained = Long.parseLong(TsgHubJson.str(message, "gained"));
					String rank = TsgHubJson.str(message, "rank");
					Long parsedRank = rank.isEmpty() ? null : Long.parseLong(rank);
					plugin.ui(s -> s.competitionView.competitionProgress(eventId, gained, parsedRank));
				}
				catch (NumberFormatException ignored) { /* Malformed progress waits for the next report. */ }
				break;
			case "claimed":
				progress(TsgHubJson.str(message, "taskTitle"), TsgHubJson.str(message, "progress"), TsgHubJson.str(message, "complete"));
				if (!plugin.live() && eventId.equals(plugin.session().get(EVENT_ID))) plugin.sessions().refreshBoard();
				break;
			case "notice":
				String text = TsgHubJson.str(message, "text");
				if (!text.isEmpty()) plugin.memberStatus(text, "error".equals(TsgHubJson.str(message, "tone")) ? Tone.ERROR : Tone.INFO);
				break;
			default:
		}
	}

	boolean notifications(JsonArray notifications)
	{
		if (notifications == null) return false;
		for (JsonObject notification : TsgHubJson.objects(notifications))
		{
			if (!"task-completed".equals(TsgHubJson.str(notification, "type"))) continue;
			local(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL).append(TsgHubJson.str(notification, "actorName") + " completed ")
				.append(ChatColorType.HIGHLIGHT).append(TsgHubJson.str(notification, "taskTitle"))
				.append(ChatColorType.NORMAL).append(" for " + TsgHubJson.str(notification, "teamName") + "!"));
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

	void clanAnnouncement(String text)
	{
		if (!config.clanAnnouncements()) return;
		clan("Announcement", new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	void updateAvailable(String version)
	{
		if (version.equals(notifiedUpdate)) return;
		notifiedUpdate = version;
		plugin.ui(s -> s.homeView.setUpdate(version));
		local(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("TSG Hub " + Text.escapeJagex(version) + " is available. Restart RuneLite to update."));
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
		String message = new ChatMessageBuilder().append(config.chatColor(), "[TSG Hub] ").build() + body.build();
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
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CLAN_MESSAGE).sender(clan.name()).runeLiteFormattedMessage(message).build());
	}
}
