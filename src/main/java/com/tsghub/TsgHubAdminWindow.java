package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

final class TsgHubAdminWindow extends JPanel
{
	TsgHubAdminWindow(TsgHubPanel events, Runnable loadEvents, TsgHubNewsEditor news, Runnable loadNews)
	{
		super(new BorderLayout());
		setBackground(BACKGROUND);
		JPanel display = panel(new BorderLayout());
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 0));
		tabs.setOpaque(false);
		MaterialTab eventsTab = themedTab("Events", tabs, events);
		eventsTab.setOnSelectEvent(() -> {
			loadEvents.run();
			return true;
		});
		MaterialTab newsTab = themedTab("Announcements", tabs, news);
		newsTab.setOnSelectEvent(() -> {
			loadNews.run();
			return true;
		});
		tabs.addTab(eventsTab);
		tabs.addTab(newsTab);
		tabs.select(eventsTab);

		JPanel tabRow = panel(new BorderLayout());
		tabRow.setBackground(CARD);
		tabRow.setOpaque(true);
		tabRow.setBorder(BorderFactory.createCompoundBorder(bottomRule(), BorderFactory.createEmptyBorder(6, 4, 0, 4)));
		tabRow.add(tabs, BorderLayout.WEST);
		add(tabRow, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}
}
