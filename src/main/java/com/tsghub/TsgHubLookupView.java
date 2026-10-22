package com.tsghub;

import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.tsghub.TsgHubSidebarPanel.View;
import java.util.List;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;

@RequiredArgsConstructor
final class TsgHubLookupView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private String lookupName = "";
	View lookupReturn = View.MEMBERS;
	private int lookupType;
	private HiscoreResult lookupResult;
	private String lookupError = "";
	private int lookupSeq;

	void showLookup(String name, int accountType)
	{
		lookupName = name;
		lookupType = accountType;
		if (shell.view != View.LOOKUP) lookupReturn = shell.view;
		shell.setView(View.LOOKUP);
		loadLookup();
	}

	void loadLookup()
	{
		int seq = ++lookupSeq;
		lookupResult = null;
		lookupError = "";
		renderLookup();
		shell.setBusy(true);
		plugin.lookupHiscores(lookupName, lookupType).whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
			shell.setBusy(false);
			if (seq != lookupSeq) return;
			lookupResult = result;
			lookupError = error != null ? friendlyError(error) : result == null ? "Not found" : "";
			if (shell.view == View.LOOKUP) renderLookup();
		}));
	}

	private void renderLookup()
	{
		HiscoreResult result = lookupResult;
		shell.setHeader(lookupName, result == null ? TsgHubHiscores.endpoint(lookupType).getName() + " hiscores" : TsgHubHiscores.subtitle(result), true, true);
		shell.page.removeAll();
		if (!lookupError.isEmpty())
		{
			shell.page.add("Not found".equals(lookupError)
				? shell.errorPanel("Not on the hiscores", lookupName + " isn't ranked on the " + TsgHubHiscores.endpoint(lookupType).getName() + " hiscores.")
				: shell.errorPanel("Lookup failed", lookupError));
		}
		else if (result == null)
		{
			shell.page.add(caption("Looking up " + lookupName + "..."));
		}
		else
		{
			shell.page.add(shell.sectionHeading("Skills", null));
			shell.page.add(TsgHubHiscores.skillGrid(result));
			shell.page.add(gap(GAP_XS));
			shell.page.add(TsgHubHiscores.totalRow(result));
			addScores("Bosses", result, HiscoreSkillType.BOSS);
			addScores("Activities", result, HiscoreSkillType.ACTIVITY);
		}
		shell.refreshPage();
	}

	private void addScores(String heading, HiscoreResult result, HiscoreSkillType type)
	{
		List<HiscoreSkill> scored = TsgHubHiscores.scored(result, type);
		if (scored.isEmpty()) return;
		shell.page.add(gap(GAP_L));
		shell.page.add(shell.sectionHeading(heading + " (" + scored.size() + ")", null));
		for (HiscoreSkill skill : scored)
		{
			shell.page.add(TsgHubHiscores.scoreRow(result, skill, plugin.sprites()));
			shell.page.add(gap(2));
		}
	}
}
