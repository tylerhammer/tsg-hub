package com.tsghub;

import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonObject;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubNewsOverlay extends Overlay implements MouseListener
{
	private static final int WIDTH = 300;
	private static final int BODY_CHARS = 360;
	private static final int IMAGE_H = 140;

	private final TsgHubPlugin plugin;
	private final TsgHubConfig config;
	private volatile BufferedImage card;
	private volatile Rectangle read;
	private volatile Rectangle dismiss;
	private volatile String shownId;

	TsgHubNewsOverlay(TsgHubPlugin plugin, TsgHubConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_HIGH);
	}

	void refresh()
	{
		SwingUtilities.invokeLater(this::rebuild);
	}

	private void rebuild()
	{
		TsgHubAnnouncements news = plugin.announcements();
		List<JsonObject> unread = news == null || news.list() == null || !config.newsOverlay() ? null : TsgHubAnnouncements.unread(news.list(), news.readIds());
		if (unread == null || unread.isEmpty())
		{
			card = null;
			shownId = null;
			return;
		}
		JsonObject announcement = unread.get(0);
		shownId = str(announcement, "id");
		JPanel panel = card();
		panel.setSize(WIDTH, 10);
		int text = WIDTH - 2 * (CARD_PAD_H + 1);
		JPanel lines = stack();
		lines.add(wrapped(str(announcement, "title"), TEXT, boldFont(), text));
		lines.add(gap(GAP_XS));
		lines.add(caption(str(announcement, "postedBy") + " · " + TsgHubFormat.shortDate(str(announcement, "postedAt"), TsgHubFormat.clock.instant(), ZoneId.systemDefault())));
		String body = bodyText(announcement);
		if (!body.isEmpty())
		{
			lines.add(gap(GAP_M));
			lines.add(label(html(postHtml(body.length() > BODY_CHARS ? body.substring(0, BODY_CHARS).trim() + "…" : body), text), TEXT, plainFont()));
		}
		String photo = firstImage(announcement);
		BufferedImage image = photo == null ? null : news.image(photo).getNow(null);
		if (photo != null && image == null) news.image(photo).thenRun(this::refresh);
		if (image != null)
		{
			int w = Math.min(text, image.getWidth() * IMAGE_H / Math.max(1, image.getHeight()));
			int h = image.getHeight() * w / Math.max(1, image.getWidth());
			lines.add(gap(GAP_M));
			lines.add(new JLabel(new ImageIcon(image.getScaledInstance(w, h, Image.SCALE_SMOOTH))));
		}
		JLabel more = label(unread.size() > 1 ? "1 of " + unread.size() : "", MUTED, smallFont());
		JLabel skip = label("Dismiss", MUTED, smallFont());
		JLabel open = label("Read more", ACCENT, smallFont());
		JPanel actions = inline();
		inline(actions, skip);
		actions.add(javax.swing.Box.createHorizontalStrut(GAP_L));
		actions.add(open);
		lines.add(gap(GAP_M));
		lines.add(fitHeight(row(more, actions)));
		panel.add(lines, BorderLayout.CENTER);
		Dimension size = panel.getPreferredSize();
		panel.setSize(WIDTH, size.height);
		layout(panel);
		BufferedImage flat = new BufferedImage(WIDTH, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = flat.createGraphics();
		panel.printAll(g);
		g.dispose();
		BufferedImage out = new BufferedImage(WIDTH, size.height, BufferedImage.TYPE_INT_ARGB);
		g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.fill(new RoundRectangle2D.Float(0, 0, WIDTH, size.height, RADIUS * 2, RADIUS * 2));
		g.setComposite(AlphaComposite.SrcIn);
		g.drawImage(flat, 0, 0, null);
		g.dispose();
		read = bounds(open, panel);
		dismiss = bounds(skip, panel);
		card = out;
	}

	private static String bodyText(JsonObject announcement)
	{
		String body = str(announcement, "body");
		if (!body.isEmpty()) return body;
		for (JsonObject block : objects(array(announcement, "blocks")))
			if ("text".equals(str(block, "type"))) return str(block, "text");
		return "";
	}

	private static String firstImage(JsonObject announcement)
	{
		for (JsonObject block : objects(array(announcement, "blocks")))
			if ("images".equals(str(block, "type")))
				for (JsonObject photo : objects(array(block, "images")))
					if (TsgHubApi.isNewsImage(str(photo, "url"))) return str(photo, "url");
		return null;
	}

	private static void layout(Component c)
	{
		if (!(c instanceof Container)) return;
		((Container) c).doLayout();
		for (Component child : ((Container) c).getComponents()) layout(child);
	}

	private static Rectangle bounds(Component c, Component root)
	{
		Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), root);
		r.grow(4, 4);
		return r;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		BufferedImage image = card;
		if (image == null) return null;
		graphics.drawImage(image, 0, 0, null);
		return new Dimension(image.getWidth(), image.getHeight());
	}

	private Point local(MouseEvent event)
	{
		Rectangle bounds = getBounds();
		if (card == null || bounds == null || !bounds.contains(event.getPoint()) || event.isAltDown()) return null;
		return new Point(event.getX() - bounds.x, event.getY() - bounds.y);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		Point point = local(event);
		if (point == null || !SwingUtilities.isLeftMouseButton(event)) return event;
		event.consume();
		String id = shownId;
		if (id == null) return event;
		if (read != null && read.contains(point))
		{
			plugin.windows().openNews(id);
			plugin.announcements().markRead(id);
		}
		else if (dismiss != null && dismiss.contains(point)) plugin.announcements().markRead(id);
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (local(event) != null) event.consume();
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		if (local(event) != null) event.consume();
		return event;
	}

	@Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
	@Override public MouseEvent mouseExited(MouseEvent event) { return event; }
	@Override public MouseEvent mouseDragged(MouseEvent event) { return event; }
	@Override public MouseEvent mouseMoved(MouseEvent event) { return event; }
}
