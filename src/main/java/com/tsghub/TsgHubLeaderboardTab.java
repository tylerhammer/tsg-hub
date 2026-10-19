package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import javax.swing.Box;
import javax.swing.JPanel;

final class TsgHubLeaderboardTab
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	final WidthTrackingPanel leaderboardTab = new WidthTrackingPanel();

	TsgHubLeaderboardTab(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	void renderLeaderboard()
	{
		leaderboardTab.removeAll();
		String type = str(shell.currentEvent, "type");
		if (!"skill".equals(type) && !"boss".equals(type)) return;
		JsonObject config = eventConfig(shell.currentEvent);
		String unit = "skill".equals(type) ? "XP" : "kills";
		boolean started = !"scheduled".equals(str(shell.currentEvent, "status"));
		int participants = integer(shell.currentEvent, "participants", 0);
		String what = "skill".equals(type) ? skillName(str(config, "skill")) + " XP gained" : str(config, "npcName") + " kills";
		leaderboardTab.add(caption(started ? what + " · " + participants + " taking part"
			: participants + " signed up · counting starts " + eventStartWhen(shell.currentEvent)));
		leaderboardTab.add(Box.createVerticalStrut(GAP_M));
		JsonArray rows = array(shell.currentEvent, "leaderboard");
		if (rows.size() == 0) leaderboardTab.add(wrapped("Nobody has joined yet. Players join with one click from the TSG Hub sidebar.", MUTED, plainFont(), TsgHubPanel.DETAIL_TEXT_W));
		for (int i = 0; i < rows.size(); i++)
		{
			JsonObject row = rows.get(i).getAsJsonObject();
			int rank = integer(row, "rank", i + 1);
			JPanel card = card();
			card.setLayout(new BorderLayout(10, 0));
			if (started)
			{
				card.add(rankLabel(rank, boldFont(), 28), BorderLayout.WEST);
			}
			card.add(boldLabel(str(row, "displayName")), BorderLayout.CENTER);
			if (started)
			{
				boolean tracking = bool(row, "tracking");
				String gained = tracking ? "+" + TsgHubFormat.number(integer(row, "gained", 0)) + " " + unit : "Waiting for first " + ("XP".equals(unit) ? "XP" : "kill");
				card.add(label(gained, !tracking ? MUTED : rank == 1 ? ACCENT : TEXT, tracking ? boldFont() : smallFont()), BorderLayout.EAST);
			}
			leaderboardTab.add(fitHeight(card));
			leaderboardTab.add(Box.createVerticalStrut(LIST_GAP));
		}
		refresh(leaderboardTab);
	}
}
