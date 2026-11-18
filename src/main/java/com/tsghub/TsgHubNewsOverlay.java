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
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubNewsOverlay extends Overlay implements MouseListener, MouseWheelListener, KeyListener
{
	private static final int WIDTH = 300;
	private static final int READER_W = TsgHubNewsWindow.TEXT_W + 2 * (GAP_L + 4);
	private static final int CLOSE = 22;
	private static final int RECENT_DAYS = 3;
	private static final int INSET = 12;
	private static final int TRACK_TOP = CLOSE + INSET * 2;

	private final TsgHubPlugin plugin;
	private final TsgHubConfig config;
	private final Client client;
	private final Timer ticker = new Timer(300, e -> tick());
	private volatile boolean inGame;
	private int ticks;
	private TsgHubNewsWindow reader;
	private volatile BufferedImage page;
	private volatile int scrollY;
	private volatile int viewH;
	private volatile int headerH;
	private volatile int drawnHead;
	private volatile int maxTop;
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
			ticks = 0;
			ticker.setDelay(300);
			ticker.restart();
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

	boolean inGame()
	{
		return inGame;
	}

	private void tick()
	{
		renderReader();
		ticker.setDelay(++ticks >= 20 ? 1500 : 300);
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
		headerH = reader.headerHeight();
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
			ticks = 0;
			ticker.setDelay(300);
			ticker.restart();
		});
	}

	private void rebuild()
	{
		TsgHubAnnouncements news = plugin.announcements();
		List<JsonObject> unread = news == null || news.list() == null || !config.newsOverlay() ? null : recent(TsgHubAnnouncements.unread(news.list(), news.readIds()));
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
		lines.add(caption(unread.size() > 1 ? "NEW ANNOUNCEMENTS · 1 OF " + unread.size() : "NEW ANNOUNCEMENT"));
		lines.add(gap(GAP_S));
		lines.add(wrapped(str(announcement, "title"), TEXT, boldFont(), WIDTH - 2 * (CARD_PAD_H + 1)));
		lines.add(gap(GAP_XS));
		lines.add(caption(str(announcement, "postedBy") + " · " + TsgHubFormat.shortDate(str(announcement, "postedAt"), TsgHubFormat.clock.instant(), ZoneId.systemDefault())));
		JLabel skip = label("Dismiss", MUTED, smallFont());
		JLabel open = label("Read more", ACCENT, smallFont());
		JPanel actions = inline();
		inline(actions, skip);
		actions.add(javax.swing.Box.createHorizontalStrut(GAP_L));
		actions.add(open);
		lines.add(gap(GAP_S));
		lines.add(fitHeight(row(new JLabel(), actions)));
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

	private static List<JsonObject> recent(List<JsonObject> unread)
	{
		Instant cutoff = TsgHubFormat.clock.instant().minus(Duration.ofDays(RECENT_DAYS));
		unread.removeIf(a -> {
			Instant at = instant(str(a, "postedAt"));
			return at == null || at.isBefore(cutoff);
		});
		return unread;
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
		Widget welcome = client.getWidget(InterfaceID.WELCOME_SCREEN, 0);
		inGame = client.getGameState() == GameState.LOGGED_IN && (welcome == null || welcome.isHidden());
		if (!inGame)
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
		int head = Math.max(0, Math.min(headerH, height - 40));
		drawnHead = head;
		maxTop = full.getHeight() - height;
		if (head > 0) g.drawImage(full.getSubimage(0, 0, READER_W, head), 0, 0, null);
		g.drawImage(full.getSubimage(0, head + top, READER_W, height - head), 0, head, null);
		if (top > 0)
		{
			g.setColor(BORDER);
			g.drawLine(0, head, READER_W, head);
		}
		if (full.getHeight() > height)
		{
			int trackTop = Math.max(TRACK_TOP, head + 4);
			int track = height - trackTop - 6;
			int thumb = Math.max(24, track * (height - head) / (full.getHeight() - head));
			int thumbY = trackTop + (track - thumb) * top / (full.getHeight() - height);
			g.setColor(BORDER_HOVER);
			g.fillRoundRect(READER_W - 7, thumbY, 4, thumb, 4, 4);
		}
		g.setClip(null);
		g.setColor(BORDER_HOVER);
		g.draw(new RoundRectangle2D.Float(.5f, .5f, READER_W - 1, height - 1, RADIUS * 2, RADIUS * 2));
		g.setColor(RAISED);
		g.fillRoundRect(READER_W - CLOSE - INSET, INSET, CLOSE, CLOSE, RADIUS, RADIUS);
		g.setColor(TEXT);
		int c = READER_W - CLOSE / 2 - INSET, m = INSET + CLOSE / 2;
		g.drawLine(c - 4, m - 4, c + 4, m + 4);
		g.drawLine(c - 4, m + 4, c + 4, m - 4);
		g.dispose();
		return null;
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (event.getKeyCode() != KeyEvent.VK_ESCAPE || page == null) return;
		event.consume();
		closeReader();
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		Rectangle bounds = area;
		if (page == null || bounds == null || !bounds.contains(event.getPoint())) return event;
		scrollY = Math.max(0, Math.min(maxTop, scrollY + event.getWheelRotation() * 48));
		event.consume();
		return event;
	}

	private void click(Point point)
	{
		if (point.x >= READER_W - CLOSE - INSET && point.y <= CLOSE + INSET)
		{
			closeReader();
			return;
		}
		int x = point.x, y = point.y < drawnHead ? point.y : point.y + Math.min(scrollY, maxTop);
		SwingUtilities.invokeLater(() -> {
			if (reader == null) return;
			JPanel content = reader.page();
			Component target = SwingUtilities.getDeepestComponentAt(content, x, y);
			while (target != null && target != content && !press(target, SwingUtilities.convertPoint(content, x, y, target))) target = target.getParent();
			if (target == null || target == content) return;
			renderReader();
			ticks = 0;
			ticker.setDelay(300);
			ticker.restart();
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
		if (point == null) return event;
		event.consume();
		if (!SwingUtilities.isLeftMouseButton(event)) return event;
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
	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		if (local(event) != null) event.consume();
		return event;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent event)
	{
		if (local(event) != null) event.consume();
		return event;
	}
}
