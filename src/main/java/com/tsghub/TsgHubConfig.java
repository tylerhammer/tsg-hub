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
	String WRAP = "<div style='width:220px'>";
	String END = "</div>";

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

	@ConfigItem(keyName = "dataSharingOptIn", position = 0, section = sharingSection, name = "Share game and clan progress", description = WRAP + "Opt in to send the TSG Hub service your RuneScape name, clan and rank, PvM progress, event loot, claims, raid, boss and minigame completions (with nearby clanmates' names), clan chat presence and world, and clan drop, pet and collection log broadcasts.<br><br>No other game chat is sent." + END)
	default boolean dataSharingOptIn()
	{
		return false;
	}

	@ConfigItem(keyName = "shareLocation", position = 1, section = sharingSection, name = "Share location and activity", description = WRAP + "Show clanmates your area, activity and world map position. Your position isn't shared in instances.<br><br>When off, clanmates only see that you're online and your world." + END)
	default boolean shareLocation()
	{
		return false;
	}

	@ConfigItem(keyName = "shareDropScreenshots", position = 2, section = sharingSection, name = "Share drop screenshots", description = WRAP + "Screenshot drops the clan Discord announces, like pets and raid uniques, to post with the announcement. Screenshots the server doesn't ask for never leave your computer." + END)
	default boolean shareDropScreenshots()
	{
		return false;
	}

	@ConfigItem(keyName = "hideScreenshotPms", position = 3, section = sharingSection, name = "Hide private messages", description = WRAP + "Black out private messages and login notices in drop screenshots." + END)
	default boolean hideScreenshotPms()
	{
		return true;
	}

	@ConfigItem(keyName = "mapNoticeShown", name = "", description = "", hidden = true)
	default boolean mapNoticeShown()
	{
		return false;
	}

	@ConfigItem(keyName = "eventAnnouncements", position = 0, section = chatSection, name = "Event announcements", description = WRAP + "Show clan event announcements in your chatbox, such as a custom event starting soon, or an event starting or ending." + END)
	default boolean eventAnnouncements()
	{
		return true;
	}

	@ConfigItem(keyName = "clanAnnouncements", position = 1, section = chatSection, name = "Clan announcements", description = WRAP + "Show new admin announcements in your chatbox. They're always under News in the sidebar." + END)
	default boolean clanAnnouncements()
	{
		return true;
	}

	@ConfigItem(keyName = "chatColor", position = 2, section = chatSection, name = "Message color", description = WRAP + "Color of the TSG Hub tag and highlighted text in TSG Hub chat messages." + END)
	default Color chatColor()
	{
		return TsgHubTheme.CHAT;
	}

	@ConfigItem(keyName = "teamNameColors", position = 3, section = chatSection, name = "Bingo team colors", description = WRAP + "During a live bingo, color clanmates' names by team in clan chat and the Members list." + END)
	default boolean teamNameColors()
	{
		return true;
	}

	@ConfigItem(keyName = "eventCountdown", position = 0, section = countdownSection, name = "Show countdown", description = WRAP + "Show an on-screen countdown for upcoming and running clan events you've joined.<br><br>Right-click to dismiss, Alt+drag to move." + END)
	default boolean eventCountdown()
	{
		return true;
	}

	@Range(min = 1, max = 1440)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "countdownLeadMinutes", position = 1, section = countdownSection, name = "Show before start", description = WRAP + "How long before an event starts the countdown appears." + END)
	default int countdownLeadMinutes()
	{
		return 30;
	}

	@ConfigItem(keyName = "showClanMap", position = 0, section = mapSection, name = "Show clanmates", description = WRAP + "Show clanmates who share their location on your world map. This only affects what you see." + END)
	default boolean showClanMap()
	{
		return true;
	}

	@ConfigItem(keyName = "partyShowSelf", name = "Show yourself", description = WRAP + "Show your own health, gear, inventory and skills at the top of your party, exactly as your party members see them." + END, section = partySection, position = 0)
	default boolean partyShowSelf()
	{
		return true;
	}

	@ConfigItem(keyName = "partyExpandMembers", name = "Expand members by default", description = WRAP + "Open each party member's details (gear, inventory, skills, prayers) instead of showing just their banner." + END, section = partySection, position = 1)
	default boolean partyExpandMembers()
	{
		return false;
	}

	@ConfigItem(keyName = "partyVirtualLevels", name = "Display virtual levels", description = WRAP + "Show levels above 99 from experience instead of capping at 99." + END, section = partySection, position = 2)
	default boolean partyVirtualLevels()
	{
		return true;
	}

	@ConfigItem(keyName = "partyShowWorlds", name = "Display player worlds", description = WRAP + "Show the world each party member is on." + END, section = partySection, position = 3)
	default boolean partyShowWorlds()
	{
		return true;
	}

	@ConfigItem(keyName = "hubKey", position = 0, section = discordSection, secret = true, name = "Hub key", description = WRAP + "Filled in by Link Discord, or paste your key from /plugin link in Discord. Unlocks the admin site button for Discord admins." + END)
	default String hubKey()
	{
		return "";
	}
}
