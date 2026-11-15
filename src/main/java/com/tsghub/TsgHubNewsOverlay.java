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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubNewsOverlay extends Overlay implements MouseListener, MouseWheelListener
{
	private static final int WIDTH = 300;
	private static final int READER_W = TsgHubNewsWindow.TEXT_W + 2 * (GAP_L + 4);
	private static final int CLOSE = 22;

	private final TsgHubPlugin plugin;
	private final TsgHubConfig config;
	private final Client client;
	private final Timer ticker = new Timer(300, e -> renderReader());
	private TsgHubNewsWindow reader;
	private volatile BufferedImage page;
	private volatile int scrollY;
	private volatile int viewH;
	private volatile Rectangle area;
	private volatile BufferedImage card;
	private volatile Rectangle read;
	private volatile Rectangle dismiss;
	private volatile String shownId;

	TsgHubNewsOverlay(TsgHubPlugin plugin, TsgHubConfig config, Client client)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(Overlay.PRIORITY_HIGHEST);
	}

	void openReader(String id)
	{
		SwingUtilities.invokeLater(() -> {
			TsgHubAnnouncements news = plugin.announcements();
			if (reader == null) reader = new TsgHubNewsWindow(news.polls(), news::image, news::openImage);
			reader.setAnnouncements(news.list());
			reader.show(id);
			scrollY = 0;
			renderReader();
			ticker.start();
			news.markRead(id);
		});
	}

	void closeReader()
	{
		SwingUtilities.invokeLater(() -> {
			ticker.stop();
			page = null;
		});
	}

	private void renderReader()
	{
		if (reader == null) return;
		JPanel content = reader.page();
		content.setSize(READER_W, 10);
		content.setSize(READER_W, content.getPreferredSize().height);
		layout(content);
		BufferedImage out = new BufferedImage(READER_W, Math.max(1, content.getHeight()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		content.printAll(g);
		g.dispose();
		page = out;
	}

	void refresh()
	{
		SwingUtilities.invokeLater(() -> {
			rebuild();
			if (page == null || reader == null) return;
			reader.setAnnouncements(plugin.announcements().list());
			reader.render();
			renderReader();
		});
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
		JPanel lines = stack();
		lines.add(wrapped(str(announcement, "title"), TEXT, boldFont(), WIDTH - 2 * (CARD_PAD_H + 1)));
		JLabel more = label(unread.size() > 1 ? "1 of " + unread.size() : "", MUTED, smallFont());
		JLabel skip = label("Dismiss", MUTED, smallFont());
		JLabel open = label("Read more", ACCENT, smallFont());
		JPanel actions = inline();
		inline(actions, skip);
		actions.add(javax.swing.Box.createHorizontalStrut(GAP_L));
		actions.add(open);
		lines.add(gap(GAP_S));
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
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			area = null;
			return null;
		}
		BufferedImage full = page;
		if (full != null) return renderReader(graphics, full);
		BufferedImage image = card;
		if (image == null)
		{
			area = null;
			return null;
		}
		int x = (client.getCanvasWidth() - image.getWidth()) / 2;
		area = new Rectangle(x, GAP_L * 2, image.getWidth(), image.getHeight());
		graphics.drawImage(image, x, GAP_L * 2, null);
		return null;
	}

	private Dimension renderReader(Graphics2D graphics, BufferedImage full)
	{
		int height = Math.min(full.getHeight(), Math.max(160, client.getCanvasHeight() - 120));
		int top = Math.max(0, Math.min(scrollY, full.getHeight() - height));
		scrollY = top;
		viewH = height;
		int x = (client.getCanvasWidth() - READER_W) / 2, y = (client.getCanvasHeight() - height) / 2;
		area = new Rectangle(x, y, READER_W, height);
		Graphics2D g = (Graphics2D) graphics.create();
		g.translate(x, y);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		RoundRectangle2D frame = new RoundRectangle2D.Float(0, 0, READER_W, height, RADIUS * 2, RADIUS * 2);
		g.setClip(frame);
		g.drawImage(full.getSubimage(0, top, READER_W, height), 0, 0, null);
		if (full.getHeight() > height)
		{
			int thumb = Math.max(24, height * height / full.getHeight());
			int thumbY = (height - thumb) * top / (full.getHeight() - height);
			g.setColor(BORDER_HOVER);
			g.fillRoundRect(READER_W - 6, thumbY + 2, 4, thumb - 4, 4, 4);
		}
		g.setClip(null);
		g.setColor(BORDER_HOVER);
		g.draw(new RoundRectangle2D.Float(.5f, .5f, READER_W - 1, height - 1, RADIUS * 2, RADIUS * 2));
		g.setColor(RAISED);
		g.fillRoundRect(READER_W - CLOSE - 6, 6, CLOSE, CLOSE, RADIUS, RADIUS);
		g.setColor(TEXT);
		int c = READER_W - CLOSE / 2 - 6, m = 6 + CLOSE / 2;
		g.drawLine(c - 4, m - 4, c + 4, m + 4);
		g.drawLine(c - 4, m + 4, c + 4, m - 4);
		g.dispose();
		return null;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		Rectangle bounds = area;
		if (page == null || bounds == null || !bounds.contains(event.getPoint())) return event;
		scrollY += event.getWheelRotation() * 48;
		event.consume();
		return event;
	}

	private void click(Point point)
	{
		if (point.x >= READER_W - CLOSE - 6 && point.y <= CLOSE + 6)
		{
			closeReader();
			return;
		}
		int x = point.x, y = point.y + scrollY;
		SwingUtilities.invokeLater(() -> {
			if (reader == null) return;
			JPanel content = reader.page();
			Component target = SwingUtilities.getDeepestComponentAt(content, x, y);
			if (target == null) return;
			Point at = SwingUtilities.convertPoint(content, x, y, target);
			long now = System.currentTimeMillis();
			for (int id : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED})
				target.dispatchEvent(new MouseEvent(target, id, now, 0, at.x, at.y, 1, false, MouseEvent.BUTTON1));
			renderReader();
		});
	}

	private Point local(MouseEvent event)
	{
		Rectangle bounds = area;
		if (card == null && page == null || bounds == null || !bounds.contains(event.getPoint())) return null;
		return new Point(event.getX() - bounds.x, event.getY() - bounds.y);
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		Point point = local(event);
		if (point == null || !SwingUtilities.isLeftMouseButton(event)) return event;
		event.consume();
		if (page != null)
		{
			click(point);
			return event;
		}
		String id = shownId;
		if (id == null) return event;
		if (read != null && read.contains(point))
		{
			openReader(id);
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
