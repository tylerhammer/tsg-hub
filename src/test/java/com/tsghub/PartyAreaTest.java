package com.tsghub;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PartyAreaTest
{
	@Test
	public void wildernessLevel()
	{
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(3200, 3519, 0)));
		assertEquals(1, AreaNames.wildernessLevel(new WorldPoint(3200, 3520, 0)));
		assertEquals(56, AreaNames.wildernessLevel(new WorldPoint(3200, 3967, 0)));
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(3200, 3968, 0)));
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(2943, 3700, 0)));
		assertEquals(1, AreaNames.wildernessLevel(new WorldPoint(3200, 9920, 0)));
		assertEquals(0, AreaNames.wildernessLevel(new WorldPoint(3200, 9919, 0)));
	}

	@Test
	public void partyTitle()
	{
		assertEquals("Alice's party", TsgHubSidebarPanel.partyTitle(group("", "", "")));
		assertEquals("Black drags", TsgHubSidebarPanel.partyTitle(group("Black drags", "Edgeville")));
		assertEquals("Wilderness lvl 40", TsgHubSidebarPanel.partyTitle(group("", "Wilderness lvl 40", "Wilderness lvl 40")));
		assertEquals("Wilderness lvl 40-42", TsgHubSidebarPanel.partyTitle(group("", "Wilderness lvl 42", "Wilderness lvl 40")));
		assertEquals("Wilderness lvl 40", TsgHubSidebarPanel.partyTitle(group("", "Wilderness lvl 40", "Wilderness lvl 40", "Edgeville")));
		assertEquals(2, (int) TsgHubSidebarPanel.areaSummary(members("Wilderness lvl 40", "Wilderness lvl 40", "Edgeville")).getValue());
	}

	private static JsonObject group(String title, String... areas)
	{
		JsonObject group = new JsonObject();
		group.addProperty("activity", title);
		group.addProperty("leaderName", "Alice");
		group.add("members", members(areas));
		return group;
	}

	private static JsonArray members(String... areas)
	{
		JsonArray array = new JsonArray();
		for (String area : areas)
		{
			JsonObject member = new JsonObject();
			member.addProperty("area", area);
			array.add(member);
		}
		return array;
	}
}
