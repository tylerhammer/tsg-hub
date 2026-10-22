package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JPanel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class TsgHubCompetitionView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	JsonObject competitionEvent;
	String competitionName = "";
	volatile String openCompetitionId;
	private final JButton competitionJoinButton = primaryButton("Join");

	String openCompetitionId()
	{
		return shell.active ? openCompetitionId : null;
	}

	private String unit()
	{
		return "skill".equals(str(competitionEvent, "type")) ? "XP" : "kills";
	}

	void showCompetitionPreview(JsonObject event)
	{
		competitionEvent = event;
		shell.setView(View.COMPETITION_PREVIEW);
		shell.setEventHeader(event, true, false);
		shell.page.removeAll();
		JsonObject config = eventConfig(event);
		boolean skill = "skill".equals(str(event, "type"));
		String subject = skill ? skillName(str(config, "skill")) : str(config, "npcName");
		shell.page.add(shell.eventsView.eventSummaryCard(event, subject, skill ? "Most " + subject + " XP gained wins." : "Most " + subject + " kills wins."));
		shell.page.add(gap(GAP_L));
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		for (ActionListener l : competitionJoinButton.getActionListeners()) competitionJoinButton.removeActionListener(l);
		competitionJoinButton.addActionListener(e -> {
			competitionJoinButton.setEnabled(false);
			competitionJoinButton.setText("Joining...");
			shell.eventsView.joinError.setVisible(false);
			plugin.sessions().participate(str(event, "id"));
		});
		shell.page.add(fitHeight(competitionJoinButton));
		shell.eventsView.joinError.setVisible(false);
		shell.page.add(shell.eventsView.joinError);
		shell.page.add(gap(GAP_S));
		shell.page.add(TsgHubSidebarPanel.hint(skill
			? "Your XP counts from the first time TSG Hub sees you after joining. Gains on other devices count the next time you log in with it."
			: !"loot".equals(str(config, "signal"))
				? "Kills count from your kill count message, so kills while TSG Hub isn't running still count at your next kill."
				: "Each kill counts when its loot drops while TSG Hub is running."));
		shell.refreshPage();
	}

	void competitionJoinFailed(String message)
	{
		competitionJoinButton.setEnabled(true);
		competitionJoinButton.setText("Join");
		showError(shell.eventsView.joinError, message, TsgHubSidebarPanel.TEXT_W);
	}

	void showCompetition(JsonObject event, String displayName, boolean open)
	{
		if (!open && shell.view != View.COMPETITION) return;
		if (!open && event.equals(competitionEvent) && Objects.equals(displayName == null ? "" : displayName, competitionName)) return;
		competitionEvent = event;
		competitionName = displayName == null ? "" : displayName;
		shell.setView(View.COMPETITION);
		openCompetitionId = str(event, "id");
		shell.refresh.setToolTipText("Refresh (updated " + LocalTime.now().format(TsgHubSidebarPanel.TIME) + ")");
		shell.setEventHeader(event, true, true);
		shell.page.removeAll();
		JsonObject config = eventConfig(event);
		boolean skill = "skill".equals(str(event, "type"));
		boolean hidden = bool(event, "hideScores");
		JsonArray rows = array(event, "leaderboard");
		JsonObject mine = null;
		for (JsonObject row : objects(rows)) if (samePlayer(str(row, "displayName"), competitionName)) mine = row;

		boolean started = !"scheduled".equals(str(event, "status"));
		boolean tracking = mine != null && bool(mine, "tracking");
		JPanel summary = card();
		JPanel top = row();
		top.add(shrinkable(boldLabel(skill ? skillName(str(config, "skill")) : str(config, "npcName"))), BorderLayout.CENTER);
		if (started && mine != null && mine.has("rank") && !hidden)
		{
			int rank = integer(mine, "rank", 0);
			top.add(label("#" + rank + " of " + integer(event, "participants", rows.size()), rank == 1 ? ACCENT : MUTED, smallFont()), BorderLayout.EAST);
		}
		JPanel lines = stack();
		lines.add(top);
		lines.add(gap(GAP_XS));
		if (!started)
		{
			String start = eventStartWhen(event);
			lines.add(TsgHubSidebarPanel.cardNote(start.isEmpty() ? "Counting starts when the event begins." : "Counting starts " + start + "."));
		}
		else
		{
			lines.add(label("+" + TsgHubFormat.number(mine == null ? 0 : integer(mine, "gained", 0)) + " " + unit(), SUCCESS, boldFont()));
			if (!tracking)
			{
				lines.add(gap(GAP_XS));
				lines.add(TsgHubSidebarPanel.cardNote(skill ? "Starts counting the next time you gain XP." : "Starts counting at your next kill."));
			}
		}
		JPanel prizes = prizeRow(event, smallFont());
		if (prizes != null)
		{
			lines.add(gap(GAP_S));
			lines.add(prizes);
		}
		summary.add(lines, BorderLayout.CENTER);
		shell.page.add(fitHeight(summary));
		shell.page.add(gap(GAP_M));

		if (hidden)
		{
			shell.page.add(shell.errorPanel("Scores are hidden", "The admins are keeping the leaderboard secret for now."));
		}
		else
		{
			shell.page.add(caption(integer(event, "participants", rows.size()) + (started ? " taking part" : " signed up")));
			shell.page.add(gap(GAP_S));
			for (int i = 0; i < rows.size(); i++)
			{
				JsonObject row = rows.get(i).getAsJsonObject();
				boolean me = samePlayer(str(row, "displayName"), competitionName);
				int rank = integer(row, "rank", i + 1);
				JPanel card = card();
				card.setLayout(new BorderLayout(8, 0));
				if (me) highlightSelf(card);
				if (started) card.add(rankLabel(rank, boldFont(), 18), BorderLayout.WEST);
				card.add(wrapped(str(row, "displayName"), TEXT, plainFont(), 110), BorderLayout.CENTER);
				String gained = bool(row, "tracking") ? TsgHubFormat.number(integer(row, "gained", 0)) : "-";
				if (started) card.add(label(gained, rank == 1 ? ACCENT : TEXT, boldFont()), BorderLayout.EAST);
				addCard(shell.page, fitHeight(card));
			}
		}
		shell.page.add(gap(GAP_L));
		JButton leave = dangerButton("Leave competition");
		leave.addActionListener(e -> {
			if (confirm(shell, "Leave competition", "Leave \"" + eventName(event) + "\"?\nYou'll drop off the leaderboard on this device. You can rejoin while it's running.", "Leave", true))
				plugin.sessions().leaveEvent(str(event, "id"));
		});
		shell.page.add(fitHeight(leave));
		shell.refreshPage();
	}

	void competitionProgress(String eventId, long gained, Long rank)
	{
		if (shell.view != View.COMPETITION || competitionEvent == null || !eventId.equals(openCompetitionId)) return;
		List<JsonObject> rows = new ArrayList<>(objects(array(competitionEvent, "leaderboard")));
		JsonObject mine = null;
		for (JsonObject row : rows) if (samePlayer(str(row, "displayName"), competitionName)) mine = row;
		if (mine == null) return;
		mine.addProperty("gained", gained);
		mine.addProperty("tracking", true);
		if (rank != null)
		{
			rows.sort(Comparator.comparingLong((JsonObject row) -> -integer(row, "gained", 0)).thenComparing(row -> str(row, "displayName")));
			JsonArray sorted = new JsonArray();
			for (int i = 0; i < rows.size(); i++)
			{
				rows.get(i).addProperty("rank", i + 1);
				sorted.add(rows.get(i));
			}
			mine.addProperty("rank", rank);
			competitionEvent.add("leaderboard", sorted);
		}
		showCompetition(competitionEvent, competitionName, false);
	}
}
