package com.tsghub;

import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class TsgHubNewsView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private JsonArray announcements;

	TsgHubNewsView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	void setAnnouncements(JsonArray announcements)
	{
		this.announcements = announcements;
		if (shell.view == View.NEWS)
		{
			renderNews();
			plugin.announcements().markRead();
		}
		else if (shell.view == View.HOME) shell.homeView.renderHome();
	}

	void showNews()
	{
		shell.setView(View.NEWS);
		renderNews();
		plugin.announcements().markRead();
		if (!plugin.live()) plugin.announcements().load();
	}

	String newsSummary()
	{
		if (announcements == null) return "Loading...";
		int unread = plugin.announcements().unreadCount();
		if (unread > 0) return unread + " new";
		return announcements.size() == 0 ? "No announcements" : plural(announcements.size(), "announcement");
	}

	private JPanel newsIcons(JsonObject announcement)
	{
		JPanel icons = panel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
		if (bool(announcement, "pinned"))
		{
			JLabel pinned = new JLabel(icon("pinned", ACCENT));
			pinned.setToolTipText("Pinned");
			icons.add(pinned);
		}
		if (plugin.announcements().isUnread(str(announcement, "id")))
		{
			JLabel unread = new JLabel(icon("dot", SUCCESS));
			unread.setToolTipText("New");
			icons.add(unread);
		}
		return icons;
	}

	private void renderNews()
	{
		shell.setHeader("News", "", true, true);
		shell.page.removeAll();
		if (announcements == null)
		{
			shell.page.add(caption("Loading announcements..."));
		}
		else if (announcements.size() == 0)
		{
			shell.page.add(shell.errorPanel("No announcements", "When the admins post news for the clan, it shows up here."));
		}
		else
		{
			Instant now = clock.instant();
			ZoneId zone = ZoneId.systemDefault();
			for (JsonObject announcement : objects(announcements))
			{
				addCard(shell.page, newsCard(announcement, now, zone));
			}
		}
		shell.refreshPage();
	}

	private JPanel newsCard(JsonObject announcement, Instant now, ZoneId zone)
	{
		JPanel card = card();
		JPanel lines = stack();
		JPanel icons = newsIcons(announcement);
		lines.add(row(label(html(escape(str(announcement, "title")), TsgHubSidebarPanel.CARD_TEXT_W - icons.getPreferredSize().width - 6), TEXT, boldFont()), north(icons)));
		String poll = TsgHubAnnouncements.poll(announcement) == null ? "" : " · Poll";
		lines.add(gap(GAP_XS));
		lines.add(TsgHubSidebarPanel.cardNote(str(announcement, "postedBy") + " · " + TsgHubFormat.shortDate(str(announcement, "postedAt"), now, zone) + poll));
		card.add(TsgHubSidebarPanel.fillWidth(lines), BorderLayout.CENTER);
		String id = str(announcement, "id");
		clickable(card, () -> plugin.windows().openNews(id));
		return fitHeight(card);
	}
}
