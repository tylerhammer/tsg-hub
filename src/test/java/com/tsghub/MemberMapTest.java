package com.tsghub;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class MemberMapTest
{
	private static final Function<TsgHubMemberMap.Member, BufferedImage> MARKER = m -> new BufferedImage(60, 14, BufferedImage.TYPE_INT_ARGB);

	private static TsgHubMemberMap.Member member(String name, int x, int y)
	{
		return new TsgHubMemberMap.Member(name, name.toLowerCase(), 420, new WorldPoint(x, y, 0));
	}

	@Test
	public void groupsMembersWhoseMarkersOverlap()
	{
		List<TsgHubMemberMap.Member> members = Arrays.asList(member("Alice", 3222, 3218), member("Bob", 3225, 3219), member("Carol", 3300, 3218));
		List<List<TsgHubMemberMap.Member>> groups = TsgHubMemberMap.group(members, 4, MARKER);
		assertEquals(2, groups.size());
		assertEquals(2, groups.get(0).size());
		assertEquals("Carol", groups.get(1).get(0).name);
	}

	@Test
	public void splitsGroupsWhenZoomedIn()
	{
		List<TsgHubMemberMap.Member> members = Arrays.asList(member("Alice", 3222, 3218), member("Bob", 3225, 3219));
		assertEquals(1, TsgHubMemberMap.group(members, 4, MARKER).size());
		assertEquals(2, TsgHubMemberMap.group(members, 32, MARKER).size());
	}

	@Test
	public void stackedVerticallyDoesNotGroupWhenApart()
	{
		List<TsgHubMemberMap.Member> members = Arrays.asList(member("Alice", 3222, 3218), member("Bob", 3222, 3214));
		assertEquals(2, TsgHubMemberMap.group(members, 4, MARKER).size());
	}
}
