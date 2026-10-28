package com.tsghub;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("tsghub")
public interface TsgHubConfig extends Config
{
	@ConfigSection(name = "Sharing", description = "What TSG Hub sends to the clan's event service.", position = 0)
	String sharingSection = "sharing";

	@ConfigSection(name = "Chat", description = "TSG Hub messages in your chatbox.", position = 5)
	String chatSection = "chat";

	@ConfigSection(name = "Event countdown", description = "An on-screen countdown for clan events.", position = 6)
	String countdownSection = "countdown";

	@ConfigSection(name = "World map", description = "Clanmates on your world map.", position = 7)
	String mapSection = "map";

	@ConfigSection(name = "Party", description = "How clan parties look in the TSG Hub sidebar.", position = 10)
	String partySection = "party";

	@ConfigSection(name = "Discord", description = "Link your Discord account and unlock the admin site.", position = 20)
	String discordSection = "admin";

	@ConfigItem(keyName = "dataSharingOptIn", position = 0, section = sharingSection, name = "Share game and clan progress", description = "Opt in to send your RuneScape name, detected clan and rank, PvM progress, loot received during events you join, submitted claims, raid, boss and minigame completions with clanmates nearby (including those clanmates' names), and clan chat presence and world (so clanmates see you as online in the Members list), and clan drop, pet and collection log broadcasts to the TSG Hub service. Progress and tile-completion notices are local to your chatbox; no other game chat is sent.")
	default boolean dataSharingOptIn()
	{
		return false;
	}

	@ConfigItem(keyName = "shareLocation", position = 1, section = sharingSection, name = "Share location and activity", description = "Show clanmates your area name and what you're doing (for example Skilling - Mining) in the Members list, and your position on their world map. Your position isn't shared inside instances. When off, you still appear while in clan chat, with just Online and your world, and your position is never sent.")
	default boolean shareLocation()
	{
		return false;
	}

	@ConfigItem(keyName = "shareDropScreenshots", position = 2, section = sharingSection, name = "Share drop screenshots", description = "Take a screenshot when you get a drop the clan Discord announces, such as a pet or raid unique, and send it to post with the announcement. The server picks which drops; screenshots it doesn't ask for are discarded and never leave your computer.")
	default boolean shareDropScreenshots()
	{
		return false;
	}

	@ConfigItem(keyName = "hideScreenshotPms", position = 3, section = sharingSection, name = "Hide private messages", description = "Black out private messages and friends' login notices in drop screenshots before they're sent, both in the chatbox and in split private chat. The rest of your chat stays visible.")
	default boolean hideScreenshotPms()
	{
		return true;
	}

	@ConfigItem(keyName = "mapNoticeShown", name = "", description = "", hidden = true)
	default boolean mapNoticeShown()
	{
		return false;
	}

	@ConfigItem(keyName = "eventAnnouncements", position = 0, section = chatSection, name = "Event announcements", description = "Show clan event announcements in your chatbox, such as a custom event starting soon, or an event starting or ending.")
	default boolean eventAnnouncements()
	{
		return true;
	}

	@ConfigItem(keyName = "clanAnnouncements", position = 1, section = chatSection, name = "Clan announcements", description = "Show new clan announcements from the admins in your chatbox when you log in or when they're posted. They're always listed under News in the sidebar.")
	default boolean clanAnnouncements()
	{
		return true;
	}

	@ConfigItem(keyName = "chatColor", position = 2, section = chatSection, name = "Message color", description = "Color of the TSG Hub tag and highlighted text in TSG Hub chat messages.")
	default Color chatColor()
	{
		return TsgHubTheme.CHAT;
	}

	@ConfigItem(keyName = "teamNameColors", position = 3, section = chatSection, name = "Bingo team colors", description = "During a live bingo, color clanmates' names by their team in clan chat and the TSG Hub Members list, using the colors the admins picked for each team.")
	default boolean teamNameColors()
	{
		return true;
	}

	@ConfigItem(keyName = "eventCountdown", position = 0, section = countdownSection, name = "Show countdown", description = "Show a countdown on screen when a custom event is about to start or is running, and near the start and end of competitions and bingos you've joined. Right-click it to dismiss that event, or hold Alt and drag to move it.")
	default boolean eventCountdown()
	{
		return true;
	}

	@Range(min = 1, max = 1440)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "countdownLeadMinutes", position = 1, section = countdownSection, name = "Show before start", description = "How long before an event starts the countdown appears.")
	default int countdownLeadMinutes()
	{
		return 30;
	}

	@ConfigItem(keyName = "showClanMap", position = 0, section = mapSection, name = "Show clanmates", description = "Show clanmates who share their location as named markers on your world map. Turn off if they get in the way. This only hides them for you; it doesn't change what you share.")
	default boolean showClanMap()
	{
		return true;
	}

	@ConfigItem(keyName = "partyShowSelf", name = "Show yourself", description = "Show your own health, gear, inventory and skills at the top of your party, exactly as your party members see them.", section = partySection, position = 0)
	default boolean partyShowSelf()
	{
		return true;
	}

	@ConfigItem(keyName = "partyExpandMembers", name = "Expand members by default", description = "Open each party member's details (gear, inventory, skills, prayers) instead of showing just their banner.", section = partySection, position = 1)
	default boolean partyExpandMembers()
	{
		return false;
	}

	@ConfigItem(keyName = "partyVirtualLevels", name = "Display virtual levels", description = "Show levels above 99 from experience instead of capping at 99.", section = partySection, position = 2)
	default boolean partyVirtualLevels()
	{
		return true;
	}

	@ConfigItem(keyName = "partyShowWorlds", name = "Display player worlds", description = "Show the world each party member is on.", section = partySection, position = 3)
	default boolean partyShowWorlds()
	{
		return true;
	}

	@ConfigItem(keyName = "hubKey", position = 0, section = discordSection, secret = true, name = "Hub key", description = "Filled in by Link Discord on the home screen, or paste your key from /plugin link in the clan Discord. Links your Discord account to this RuneScape account and unlocks the admin site button if you're a Discord admin.")
	default String hubKey()
	{
		return "";
	}
}
