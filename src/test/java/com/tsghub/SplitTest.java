package com.tsghub;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class SplitTest
{
	private static JsonObject json(String text)
	{
		return new JsonParser().parse(text.replace('\'', '"')).getAsJsonObject();
	}

	private static final String MEMBERS = "'members':[{'displayName':'Alice'},{'displayName':'Bob'},{'displayName':'Carl'}]";

	@Test
	public void noticeOnlyOnceSplitEnds()
	{
		assertEquals("", TsgHubGroups.splitNotice(json("{}"), "Alice"));
		assertEquals("", TsgHubGroups.splitNotice(json("{'active':true," + MEMBERS + "}"), "Alice"));
		assertEquals("", TsgHubGroups.splitNotice(json("{'active':false," + MEMBERS + "}"), "Zezima"));
	}

	@Test
	public void noticeListsOwnTransfers()
	{
		JsonObject split = json("{'active':false,'estimated':false," + MEMBERS + ",'transfers':["
			+ "{'from':'Alice','to':'Bob','amount':400000000},{'from':'Alice','to':'Carl','amount':400000000}]}");
		assertEquals("Loot split: you pay Bob 400,000,000 gp, you pay Carl 400,000,000 gp.", TsgHubGroups.splitNotice(split, "alice"));
		assertEquals("Loot split: Alice pays you 400,000,000 gp.", TsgHubGroups.splitNotice(split, "Bob"));
	}

	@Test
	public void noticeMarksEstimatesAndSquareMembers()
	{
		JsonObject split = json("{'active':false,'estimated':true," + MEMBERS + ",'transfers':[{'from':'Alice','to':'Bob','amount':5}]}");
		assertEquals("Loot split: Alice pays you ~5 gp (estimated until sold).", TsgHubGroups.splitNotice(split, "Bob"));
		assertEquals("Loot split: you're square, nothing to pay or collect (estimated until sold).", TsgHubGroups.splitNotice(split, "Carl"));
	}

	@Test
	public void noticeAfterPayments()
	{
		JsonObject split = json("{'active':false,'estimated':false," + MEMBERS + ",'transfers':[{'from':'Alice','to':'Carl','amount':100}],"
			+ "'payments':[{'from':'Alice','to':'Bob','gp':400}]}");
		assertEquals("Loot split: all settled.", TsgHubGroups.splitNotice(split, "Bob"));
		assertEquals("Loot split: you pay Carl 100 gp.", TsgHubGroups.splitNotice(split, "Alice"));
	}

	@Test
	public void tradePartnerAndDebt()
	{
		assertEquals("Bob Test", TsgHubTrades.partnerName("Trading with:<br>Bob\u00a0Test"));
		assertEquals("Iron Mo", TsgHubTrades.partnerName("<col=ffffff>Trading with: Iron Mo</col>"));
		JsonObject split = json("{'transfers':[{'from':'Alice','to':'Bob','amount':300},{'from':'Carl','to':'Bob','amount':5}]}");
		assertEquals(300, TsgHubTrades.owed(split, "alice", "Bob"));
		assertEquals(0, TsgHubTrades.owed(split, "Bob", "Alice"));
	}

	@Test
	public void parsesGpAmounts()
	{
		assertEquals(1_400_000_000L, TsgHubPartiesView.parseGp("1.4b"));
		assertEquals(350_000_000L, TsgHubPartiesView.parseGp("350m"));
		assertEquals(2_500_000L, TsgHubPartiesView.parseGp("2,500,000"));
		assertEquals(-1, TsgHubPartiesView.parseGp("lots"));
	}

	@Test
	public void sharersIncludeSkipped()
	{
		JsonObject drop = json("{'sharers':['Alice','Carl'],'skipped':['bob']}");
		assertEquals(List.of("Alice", "Carl", "bob"), TsgHubPartiesView.dropSharers(drop, List.of("bob")));
	}
}
