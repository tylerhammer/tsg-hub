package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.time.ZoneId;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

final class TsgHubNewsWindow extends JPanel
{
	static final int TEXT_W = 460;

	private final TsgHubNewsPoll polls;
	private final JPanel page = new WidthTrackingPanel();
	private final JScrollPane scroll = scroll(page);
	private JsonArray announcements;
	private String id = "";

	TsgHubNewsWindow(TsgHubNewsPoll polls)
	{
		super(new BorderLayout());
		this.polls = polls;
		setBackground(BACKGROUND);
		page.setBorder(BorderFactory.createEmptyBorder(GAP_L, GAP_L + 4, GAP_L, GAP_L + 4));
		add(scroll, BorderLayout.CENTER);
	}

	void show(String id)
	{
		this.id = id;
		render();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
	}

	void setAnnouncements(JsonArray announcements)
	{
		this.announcements = announcements;
		render();
	}

	void render()
	{
		page.removeAll();
		JsonObject announcement = find();
		if (announcement == null)
		{
			page.add(wrapped("This announcement is no longer available.", MUTED, plainFont(), TEXT_W));
		}
		else
		{
			page.add(wrapped(str(announcement, "title"), TEXT, headingFont(), TEXT_W));
			page.add(Box.createVerticalStrut(GAP_XS));
			page.add(wrapped(str(announcement, "postedBy") + " · " + TsgHubRoster.warningDate(str(announcement, "postedAt"), clock.instant(), ZoneId.systemDefault()), MUTED, smallFont(), TEXT_W));
			String body = str(announcement, "body");
			if (!body.isEmpty())
			{
				page.add(Box.createVerticalStrut(GAP_L));
				page.add(linkedHtml(postHtml(body), TEXT, plainFont(), TEXT_W));
			}
			JsonObject poll = TsgHubAnnouncements.poll(announcement);
			if (poll != null)
			{
				JPanel card = card();
				card.add(polls.block(id, poll, TEXT_W - 2 * CARD_PAD_H), BorderLayout.CENTER);
				page.add(Box.createVerticalStrut(GAP_L));
				page.add(fitHeight(card));
			}
		}
		page.revalidate();
		page.repaint();
	}

	private JsonObject find()
	{
		if (announcements == null) return null;
		for (JsonObject announcement : objects(announcements))
			if (id.equals(str(announcement, "id"))) return announcement;
		return null;
	}
}
