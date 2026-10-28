package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import lombok.RequiredArgsConstructor;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

@RequiredArgsConstructor
final class TsgHubRecordsView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private JsonObject records;
	private boolean unavailable;
	private boolean loadFailed;

	void showRecords()
	{
		shell.setView(View.RECORDS);
		renderRecords();
		plugin.records().load();
	}

	void setRecords(JsonObject records)
	{
		this.records = records;
		unavailable = false;
		loadFailed = false;
		if (shell.view == View.RECORDS) renderRecords();
	}

	void failed(boolean missing)
	{
		unavailable = missing;
		loadFailed = !missing;
		if (shell.view == View.RECORDS) renderRecords();
	}

	private void renderRecords()
	{
		shell.setHeader("Records", "Clan bests", true, true);
		shell.page.removeAll();
		JsonArray clues = array(records, "clues");
		JsonArray bosses = array(records, "bosses");
		String self = plugin.clan().playerName();
		if (unavailable)
		{
			shell.page.add(shell.errorPanel("Records aren't ready", "The TSG Hub service doesn't have clan records yet. Try again after its next update."));
		}
		else if (records == null && loadFailed)
		{
			shell.page.add(shell.errorPanel("Couldn't load records", "Click refresh to try again."));
		}
		else if (records == null)
		{
			shell.page.add(caption("Loading records..."));
		}
		else if (clues.size() == 0 && bosses.size() == 0)
		{
			shell.page.add(shell.errorPanel("No records yet", "Kill and clue counts come from the hiscores and fill in over a day. PBs come from RuneLite's Chat Commands plugin."));
		}
		else
		{
			if (clues.size() > 0) shell.page.add(listHeading("Clue scrolls", true));
			for (JsonObject clue : objects(clues)) addCard(shell.page, clueCard(clue, self));
			if (bosses.size() > 0) shell.page.add(listHeading("Bosses", clues.size() == 0));
			for (JsonObject boss : objects(bosses)) addCard(shell.page, bossCard(boss, self));
		}
		shell.refreshPage();
	}

	private JPanel clueCard(JsonObject clue, String self)
	{
		JsonArray top = array(clue, "top");
		JsonObject first = top.size() > 0 ? top.get(0).getAsJsonObject() : null;
		JPanel card = card();
		card.add(title(TsgHubHiscores.clueTier(str(clue, "name")), str(clue, "name")), BorderLayout.CENTER);
		card.add(first == null ? caption("--") : label(str(first, "player") + "  " + count(first), ACCENT, smallFont()), BorderLayout.EAST);
		if (leads(first, self)) highlightSelf(card);
		fullTextTooltip(card, () -> standings("TOP " + top.size(), top, true, self));
		return fitHeight(card);
	}

	private JPanel bossCard(JsonObject boss, String self)
	{
		JsonArray kc = array(boss, "kc");
		JsonArray pb = array(boss, "pb");
		JPanel lines = stack();
		lines.add(title(TsgHubHiscores.named(str(boss, "name"), HiscoreSkillType.BOSS), str(boss, "name")));
		JsonObject most = kc.size() > 0 ? kc.get(0).getAsJsonObject() : null;
		JsonObject fastest = pb.size() > 0 ? pb.get(0).getAsJsonObject() : null;
		if (most != null) lines.add(leader(most, count(most) + " KC"));
		if (fastest != null) lines.add(leader(fastest, "PB " + str(fastest, "time")));
		JPanel card = card();
		card.add(TsgHubSidebarPanel.fillWidth(lines), BorderLayout.CENTER);
		if (leads(most, self) || leads(fastest, self)) highlightSelf(card);
		fullTextTooltip(card, () -> standings("MOST KC", kc, true, self) + standings("FASTEST", pb, kc.size() == 0, self));
		return fitHeight(card);
	}

	private JPanel title(HiscoreSkill skill, String name)
	{
		JPanel title = row();
		title.add(TsgHubHiscores.spriteIcon(skill, plugin.sprites()), BorderLayout.WEST);
		title.add(shrinkable(boldLabel(name)), BorderLayout.CENTER);
		return title;
	}

	private static JPanel leader(JsonObject entry, String value)
	{
		JPanel line = row(shrinkable(label(str(entry, "player"), ACCENT, smallFont())), caption(value));
		line.setBorder(BorderFactory.createEmptyBorder(GAP_XS, 0, 0, 0));
		return line;
	}

	private static String count(JsonObject entry)
	{
		return TsgHubFormat.number(longValue(entry, "score", 0));
	}

	static String standings(String heading, JsonArray rows, boolean first, String self)
	{
		if (rows.size() == 0) return "";
		List<String> lines = new ArrayList<>();
		for (JsonObject row : objects(rows))
		{
			String value = row.has("time") ? escape(str(row, "time")) : count(row);
			lines.add((lines.size() + 1) + ". " + nameHtml(str(row, "player"), self) + tipLine(MUTED, " " + value));
		}
		return tipSection(first, tipLine(MUTED, "<b>" + heading + "</b>")) + tipCard(ACCENT, String.join("<br>", lines));
	}

	private static boolean leads(JsonObject entry, String self)
	{
		return entry != null && samePlayer(str(entry, "player"), self);
	}
}
