package com.tsghub;

import com.tsghub.TsgHubSidebarPanel.*;
import java.util.*;
import javax.swing.*;
import lombok.*;
import net.runelite.client.hiscore.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

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
				? errorPanel("Not on the hiscores", lookupName + " isn't ranked on the " + TsgHubHiscores.endpoint(lookupType).getName() + " hiscores.")
				: errorPanel("Lookup failed", lookupError));
		}
		else if (result == null)
		{
			shell.page.add(caption("Looking up " + lookupName + "..."));
		}
		else
		{
			shell.page.add(listHeading("Skills", true, null));
			shell.page.add(TsgHubHiscores.skillGrid(result));
			addGap(shell.page, GAP_XS, TsgHubHiscores.totalRow(result));
			addScores("Bosses", result, HiscoreSkillType.BOSS);
			addScores("Activities", result, HiscoreSkillType.ACTIVITY);
		}
		shell.refreshPage();
	}

	private void addScores(String heading, HiscoreResult result, HiscoreSkillType type)
	{
		List<HiscoreSkill> scored = TsgHubHiscores.scored(result, type);
		if (scored.isEmpty()) return;
		addGap(shell.page, GAP_L, listHeading(heading + " (" + scored.size() + ")", true, null));
		for (HiscoreSkill skill : scored)
		{
			shell.page.add(TsgHubHiscores.scoreRow(result, skill, plugin.sprites()));
			shell.page.add(gap(2));
		}
	}
}
