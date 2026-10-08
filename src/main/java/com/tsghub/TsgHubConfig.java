package com.tsghub;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("tsghub")
public interface TsgHubConfig extends Config
{
	@ConfigSection(name = "Sharing", description = "What TSG Hub sends to the clan's event service.", position = 0)
	String sharingSection = "sharing";

	@ConfigSection(name = "Chat", description = "TSG Hub messages in your chatbox.", position = 5)
	String chatSection = "chat";

	@ConfigSection(name = "Party", description = "How clan parties look in the TSG Hub sidebar.", position = 10)
	String partySection = "party";

	@ConfigSection(name = "Discord", description = "Link your Discord account and unlock admin tools.", position = 20)
	String discordSection = "admin";

	@ConfigItem(keyName = "dataSharingOptIn", position = 0, section = sharingSection, name = "Share game and clan progress", description = "Opt in to send your RuneScape name, detected clan and rank, PvM progress, loot received during events you join, submitted claims, and clan chat presence and world (so clanmates see you as online in the Members list), and clan drop, pet and collection log broadcasts to the TSG Hub service. Progress and tile-completion notices are local to your chatbox; no other game chat is sent.")
	default boolean dataSharingOptIn()
	{
		return false;
	}

	@ConfigItem(keyName = "shareLocation", position = 1, section = sharingSection, name = "Share location and activity", description = "Show clanmates your area name and what you're doing (for example Skilling - Mining) in the Members list. When off, you still appear while in clan chat, with just Online and your world. Your exact tile is never sent.")
	default boolean shareLocation()
	{
		return false;
	}

	@ConfigItem(keyName = "eventAnnouncements", position = 0, section = chatSection, name = "Event announcements", description = "Show clan event announcements in your chatbox, such as a custom event starting soon, or an event starting or ending.")
	default boolean eventAnnouncements()
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

	@ConfigItem(keyName = "hubKey", position = 0, section = discordSection, secret = true, name = "Hub key", description = "Your key from /hub key in the clan Discord. Links your Discord account to this RuneScape account and unlocks the admin tools if you're a Discord admin.")
	default String hubKey()
	{
		return "";
	}
}
