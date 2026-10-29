package com.tsghub;

import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

final class TsgHubNewsWindow extends JPanel
{
	static final int TEXT_W = 460;
	static final int MAX_TEXT_W = 760;
	private static final int PAD_H = GAP_L + 4;
	private static final int MAX_PHOTOS = 10;

	private final TsgHubNewsPoll polls;
	private final Function<String, CompletableFuture<BufferedImage>> images;
	private final Consumer<String> open;
	private final JPanel page = new WidthTrackingPanel();
	private final JScrollPane scroll = scroll(page);
	private JsonArray announcements;
	private String id = "";
	private int width = TEXT_W;

	TsgHubNewsWindow(TsgHubNewsPoll polls, Function<String, CompletableFuture<BufferedImage>> images, Consumer<String> open)
	{
		super(new BorderLayout());
		this.polls = polls;
		this.images = images;
		this.open = open;
		setBackground(BACKGROUND);
		page.setBorder(BorderFactory.createEmptyBorder(GAP_L, PAD_H, GAP_L, PAD_H));
		add(scroll, BorderLayout.CENTER);
		scroll.getViewport().addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				int next = textWidth(scroll.getViewport().getWidth());
				if (next == width) return;
				width = next;
				render();
			}
		});
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
		if (isShowing()) render();
	}

	void render()
	{
		page.removeAll();
		JsonObject announcement = find();
		if (announcement == null)
		{
			page.add(wrapped("This announcement is no longer available.", MUTED, plainFont(), width));
		}
		else
		{
			page.add(wrapped(str(announcement, "title"), TEXT, headingFont(), width));
			page.add(gap(GAP_XS));
			page.add(wrapped(str(announcement, "postedBy") + " · " + TsgHubFormat.shortDate(str(announcement, "postedAt"), clock.instant(), ZoneId.systemDefault()), MUTED, smallFont(), width));
			JsonArray blocks = array(announcement, "blocks");
			String body = str(announcement, "body");
			if (blocks.size() > 0 || !body.isEmpty()) page.add(gap(GAP_L));
			if (blocks.size() == 0 && !body.isEmpty()) page.add(linkedHtml(postHtml(body), TEXT, plainFont(), width));
			for (JsonObject block : objects(blocks))
			{
				String type = str(block, "type");
				if (type.equals("text")) page.add(linkedHtml(postHtml(str(block, "text")), TEXT, plainFont(), width));
				else if (type.equals("divider")) page.add(divider());
				else if (type.equals("images")) addImages(array(block, "images"));
			}
			JsonObject poll = TsgHubAnnouncements.poll(announcement);
			if (poll != null)
			{
				JPanel card = card();
				card.add(polls.block(id, poll, width - 2 * CARD_PAD_H), BorderLayout.CENTER);
				page.add(gap(GAP_L));
				page.add(fitHeight(card));
			}
		}
		page.revalidate();
		page.repaint();
	}

	private void addImages(JsonArray list)
	{
		List<JsonObject> photos = new ArrayList<>();
		for (JsonObject photo : objects(list)) if (TsgHubApi.isNewsImage(str(photo, "url")) && photos.size() < MAX_PHOTOS) photos.add(photo);
		if (photos.isEmpty()) return;
		page.add(gap(GAP_S));
		page.add(new TsgHubNewsGallery(photos, width, images, open));
		page.add(gap(GAP_M));
	}

	private static JPanel divider()
	{
		JPanel line = new JPanel();
		line.setOpaque(false);
		line.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(GAP_L, 0, GAP_L - 1, 0), BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER)));
		return fitHeight(line);
	}

	static int textWidth(int viewport)
	{
		return Math.max(TEXT_W, Math.min(MAX_TEXT_W, viewport - 2 * PAD_H));
	}

	private JsonObject find()
	{
		return byId(announcements, id);
	}
}
