package com.tsghub;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("tsghub")
public interface TsgHubConfig extends Config
{
	@ConfigSection(name = "Party", description = "How clan parties look in the TSG Hub sidebar.", position = 10)
	String partySection = "party";

	@ConfigItem(keyName = "dataSharingOptIn", position = 0, name = "Share game and clan progress", description = "Opt in to send your RuneScape name, detected clan and rank, PvM progress, loot received during events you join, submitted claims, clan chat presence, and your world, area name and current activity while you're in clan chat to the TSG Hub service. Progress and tile-completion notices are local to your chatbox; no game chat is sent.")
	default boolean dataSharingOptIn()
	{
		return false;
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
}
