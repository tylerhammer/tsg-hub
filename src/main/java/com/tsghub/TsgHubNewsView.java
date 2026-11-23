package com.tsghub;

import com.google.gson.*;
import com.tsghub.TsgHubSidebarPanel.*;
import java.awt.*;
import java.time.*;
import javax.swing.*;
import lombok.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

@RequiredArgsConstructor
final class TsgHubNewsView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private JsonArray announcements;

	void setAnnouncements(JsonArray announcements)
	{
		this.announcements = announcements;
		if (shell.view == View.NEWS)
		{
			renderNews();
			plugin.announcements().markRead();
		}
		else if (shell.view == View.HOME)
		{
			shell.homeView.renderHome();
			shell.homeView.renderPolls();
		}
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
			shell.page.add(errorPanel("No announcements", "When the admins post news for the clan, it shows up here."));
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
		if (TsgHubAnnouncements.pollOnly(announcement)) return pollCard(announcement, now, zone);
		JPanel card = card();
		card.add(fillWidth(heading(announcement, now, zone, TsgHubAnnouncements.poll(announcement) == null ? "" : " · Poll")), BorderLayout.CENTER);
		String id = str(announcement, "id");
		clickable(card, () -> plugin.openNews(id));
		return fitHeight(card);
	}

	JPanel pollCard(JsonObject announcement, Instant now, ZoneId zone)
	{
		JPanel lines = heading(announcement, now, zone, "");
		lines.add(plugin.announcements().polls().block(str(announcement, "id"), TsgHubAnnouncements.poll(announcement), CARD_TEXT_W));
		JPanel card = card();
		card.add(fillWidth(lines), BorderLayout.CENTER);
		return fitHeight(card);
	}

	private JPanel heading(JsonObject announcement, Instant now, ZoneId zone, String suffix)
	{
		JPanel lines = stack();
		JPanel icons = newsIcons(announcement);
		lines.add(row(htmlLabel(html(escape(str(announcement, "title")), CARD_TEXT_W - icons.getPreferredSize().width - 6), TEXT, boldFont()), north(icons)));
		addGap(lines, GAP_XS, cardNote(str(announcement, "postedBy") + " · " + shortDate(str(announcement, "postedAt"), now, zone) + suffix));
		return lines;
	}
}
