package com.tsghub;

import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class TsgHubHomeView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	boolean homeBuilt;
	private String update;
	private final List<Section> sections;

	TsgHubHomeView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		sections = Arrays.asList(
			new Section("Events", new CalendarIcon(), () -> shell.eventsView.openEvents(), () -> shell.eventsView.eventsSummary(), () -> shell.eventsView.liveEvents() > 0),
			new Section("Parties", new PartyIcon(), () -> shell.partiesView.showParties(), () -> shell.partiesView.partiesSummary(), () -> plugin.groups().inGroup()),
			new Section("Members", new MembersIcon(), () -> shell.membersView.showMembers(), () -> shell.membersView.membersSummary(), () -> shell.membersView.onlineCount() > 0),
			new Section("Drops", new DropsIcon(), () -> shell.dropsView.showDrops(), () -> "", () -> false),
			new Section("News", new NewsIcon(), () -> shell.newsView.showNews(), () -> shell.newsView.newsSummary(), () -> plugin.announcements().unreadCount() > 0));
	}

	private static final class Section
	{
		private final String name;
		private final Icon icon;
		private final Runnable open;
		private final Supplier<String> summary;
		private final BooleanSupplier live;
		private JLabel summaryLabel;
		private boolean half;

		Section(String name, Icon icon, Runnable open, Supplier<String> summary, BooleanSupplier live)
		{
			this.name = name;
			this.icon = icon;
			this.open = open;
			this.summary = summary;
			this.live = live;
		}
	}

	void showHome()
	{
		shell.setView(View.HOME);
		renderHome();
		plugin.groups().refresh();
		plugin.presence().loadMembers(true);
	}

	void renderHome()
	{
		if (homeBuilt)
		{
			for (Section section : sections) summarize(section);
			return;
		}
		shell.setHeader("TSG Hub", plugin.clan().playerName(), false, true);
		shell.page.removeAll();
		for (int i = 0; i < sections.size(); i += 2)
		{
			boolean pair = i + 1 < sections.size();
			JPanel row = panel(new GridLayout(1, pair ? 2 : 1, 6, 0));
			row.add(sectionTile(sections.get(i), pair));
			if (pair) row.add(sectionTile(sections.get(i + 1), true));
			shell.page.add(fitHeight(row));
			shell.page.add(Box.createVerticalStrut(LIST_GAP));
		}
		if (update != null)
		{
			shell.page.add(Box.createVerticalStrut(GAP_S));
			JLabel notice = label("Update available. Restart RuneLite.", WARNING, smallFont());
			notice.setHorizontalAlignment(JLabel.CENTER);
			notice.setToolTipText("Version " + update);
			shell.page.add(fitHeight(notice));
		}
		shell.refreshPage();
		homeBuilt = true;
	}

	void setUpdate(String version)
	{
		update = version;
		homeBuilt = false;
		if (shell.view == View.HOME) renderHome();
	}

	private JPanel sectionTile(Section section, boolean half)
	{
		JPanel tile = new JPanel();
		tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
		tile.setBackground(CARD);
		tile.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 6));
		JLabel icon = new JLabel(section.icon);
		JLabel name = boldLabel(section.name);
		JLabel summary = label("", MUTED, smallFont());
		section.summaryLabel = summary;
		section.half = half;
		summarize(section);
		tile.add(icon);
		tile.add(Box.createVerticalStrut(GAP_M));
		tile.add(name);
		tile.add(Box.createVerticalStrut(GAP_XS));
		tile.add(summary);
		for (Component part : tile.getComponents())
		{
			((JComponent) part).setAlignmentX(Component.CENTER_ALIGNMENT);
			if (part instanceof JLabel) ((JLabel) part).setHorizontalAlignment(JLabel.CENTER);
		}
		clickable(tile, section.open);
		return tile;
	}

	private void summarize(Section section)
	{
		String text = html("<div style='text-align:center'>" + escape(section.summary.get()) + "</div>", section.half ? 80 : 180);
		if (!text.equals(section.summaryLabel.getText())) section.summaryLabel.setText(text);
		section.summaryLabel.setForeground(section.live.getAsBoolean() ? SUCCESS : MUTED);
	}
}
