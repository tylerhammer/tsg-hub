package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonObject;
import javax.swing.Box;

final class TsgHubDetailsTab
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	final WidthTrackingPanel detailsTab = new WidthTrackingPanel();

	TsgHubDetailsTab(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	void renderDetails()
	{
		detailsTab.removeAll();
		if (!"drop-party".equals(str(shell.currentEvent, "type"))) return;
		JsonObject config = eventConfig(shell.currentEvent);
		String countdown = capitalize(eventRelative(shell.currentEvent));
		if (!countdown.isEmpty()) detailsTab.add(label(countdown, "Ended".equals(countdown) ? MUTED : SUCCESS, boldFont()));
		detailsTab.add(Box.createVerticalStrut(GAP_S));
		detailsTab.add(wrapped("When: " + eventWhen(shell.currentEvent), TEXT, plainFont(), TsgHubPanel.DETAIL_TEXT_W));
		if (integer(config, "world", 0) > 0) detailsTab.add(label("World: " + integer(config, "world", 0), TEXT, plainFont()));
		if (!str(config, "location").isEmpty()) detailsTab.add(label("Where: " + str(config, "location"), TEXT, plainFont()));
		if (!str(config, "host").isEmpty()) detailsTab.add(label("Host: " + str(config, "host"), TEXT, plainFont()));
		if (!str(config, "notes").isEmpty()) detailsTab.add(wrapped("Notes: " + str(config, "notes"), MUTED, plainFont(), TsgHubPanel.DETAIL_TEXT_W));
		detailsTab.add(Box.createVerticalStrut(GAP_M));
		detailsTab.add(caption("Players see this in the TSG Hub sidebar. Use Edit event to change it."));
		refresh(detailsTab);
	}
}
