package com.tsghub;

import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

final class TsgHubHomeView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	boolean homeBuilt;
	private String update;
	private final List<Section> sections;
	private static final int MOVED_HOLD_MS = 1500;
	private static final int MOVED_FADE_MS = 600;

	private final JPanel polls = stack();
	private final Set<String> shownPolls = new HashSet<>();
	private final Map<String, Fade> movedPolls = new HashMap<>();

	TsgHubHomeView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		sections = Arrays.asList(
			new Section("Events", icon("calendar", ACCENT), () -> shell.eventsView.openEvents(), () -> shell.eventsView.eventsSummary(), () -> shell.eventsView.liveEvents() > 0),
			new Section("Parties", icon("party", ACCENT), () -> shell.partiesView.showParties(), () -> shell.partiesView.partiesSummary(), () -> plugin.groups().inGroup()),
			new Section("Members", icon("members", ACCENT), () -> shell.membersView.showMembers(), () -> shell.membersView.membersSummary(), () -> shell.membersView.onlineCount() > 0),
			new Section("Drops", icon("drops", ACCENT), () -> shell.dropsView.showDrops(), () -> "", () -> false),
			new Section("Records", icon("records", ACCENT), () -> shell.recordsView.showRecords(), () -> "PBs, KC and clues", () -> false),
			new Section("News", icon("news", ACCENT), () -> shell.newsView.showNews(), () -> shell.newsView.newsSummary(), () -> plugin.announcements().unreadCount() > 0));
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
		plugin.groups().open();
		plugin.presence().open(true);
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
			addCard(shell.page, fitHeight(row));
		}
		shell.page.add(polls);
		shownPolls.clear();
		renderPolls();
		if (update != null)
		{
			shell.page.add(gap(GAP_S));
			JLabel notice = label("Update available. Restart RuneLite.", WARNING, smallFont());
			notice.setHorizontalAlignment(JLabel.CENTER);
			notice.setToolTipText("Version " + update);
			shell.page.add(fitHeight(notice));
		}
		shell.refreshPage();
		homeBuilt = true;
	}

	void renderPolls()
	{
		polls.removeAll();
		Set<String> shown = new HashSet<>();
		JsonArray announcements = plugin.announcements().list();
		if (announcements != null)
		{
			Instant now = TsgHubFormat.clock.instant();
			ZoneId zone = ZoneId.systemDefault();
			for (JsonObject announcement : objects(announcements))
			{
				String id = str(announcement, "id");
				if (TsgHubAnnouncements.homePoll(announcement))
				{
					shown.add(id);
					addCard(polls, shell.newsView.pollCard(announcement, now, zone));
				}
				else if (movedPolls.containsKey(id) || shownPolls.contains(id) && TsgHubAnnouncements.pollOnly(announcement))
				{
					addCard(polls, movedPolls.computeIfAbsent(id, this::moved));
				}
			}
		}
		shownPolls.clear();
		shownPolls.addAll(shown);
		refresh(polls);
	}

	private Fade moved(String id)
	{
		JPanel card = card();
		JLabel note = label("Moved to News", MUTED, smallFont());
		note.setIcon(icon("check", SUCCESS));
		note.setHorizontalAlignment(JLabel.CENTER);
		card.add(note, BorderLayout.CENTER);
		Fade fade = new Fade();
		fade.add(card, BorderLayout.CENTER);
		long start = System.currentTimeMillis();
		Timer timer = new Timer(40, null);
		timer.addActionListener(e -> {
			long fading = System.currentTimeMillis() - start - MOVED_HOLD_MS;
			fade.alpha = Math.max(0f, Math.min(1f, 1f - fading / (float) MOVED_FADE_MS));
			fade.repaint();
			if (fade.alpha > 0f) return;
			timer.stop();
			movedPolls.remove(id);
			renderPolls();
		});
		timer.start();
		return fade;
	}

	private static final class Fade extends JPanel
	{
		private float alpha = 1f;

		Fade()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		@Override
		public void paint(Graphics g)
		{
			Graphics2D faded = (Graphics2D) g.create();
			faded.setComposite(AlphaComposite.SrcOver.derive(alpha));
			super.paint(faded);
			faded.dispose();
		}
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
		tile.add(gap(GAP_M));
		tile.add(name);
		tile.add(gap(GAP_XS));
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
