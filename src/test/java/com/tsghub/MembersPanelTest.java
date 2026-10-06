package com.tsghub;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MembersPanelTest
{
	@Test
	public void activityDetail()
	{
		assertEquals("Mining · Motherlode Mine", TsgHubSidebarPanel.activityDetail("Skilling - Mining", "Motherlode Mine"));
		assertEquals("Nex", TsgHubSidebarPanel.activityDetail("Bossing - Nex", "Nex"));
		assertEquals("Idle · Grand Exchange", TsgHubSidebarPanel.activityDetail("Idle", "Grand Exchange"));
		assertEquals("Combat", TsgHubSidebarPanel.activityDetail("Combat", ""));
		assertEquals("Online", TsgHubSidebarPanel.activityDetail("", ""));
	}
}
