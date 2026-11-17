package com.tsghub;

import com.google.gson.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.*;
import static com.tsghub.TsgHubJson.*;

final class TsgHubBoardOverlay extends Overlay
{
	private static final int PAD = 8;
	private static final int GAP = 3;
	private static final int HEADER = 20;
	private static final int FOOTER = 16;
	private static final int PEEK_MS = 280;
	private static final int PEEK_HOLD_MS = 4000;

	private final TsgHubTileIcons icons;
	private final Map<String, Long> flips = new ConcurrentHashMap<>();
	private volatile TsgHubBingoView snapshot;
	private volatile Set<Integer> inLines = Collections.emptySet();
	private volatile boolean visible;
	private volatile int layoutCell;
	private volatile int layoutSize;
	private volatile String peekId;
	private volatile long peekOpened;
	private volatile long peekClosing;
	private volatile int[] hover;
	private volatile int version;
	private BufferedImage cache;
	private int cacheVersion;

	private final Runnable onPeek;

	TsgHubBoardOverlay(TsgHubTileIcons icons, Runnable onPeek)
	{
		this.icons = icons;
		this.onPeek = onPeek;
		icons.onLoaded(() -> version++);
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_LOW);
	}

	void setVisible(boolean visible)
	{
		this.visible = visible;
	}

	void clear()
	{
		snapshot = null;
		inLines = Collections.emptySet();
		flips.clear();
		peekId = null;
		hover = null;
		version++;
	}

	void setView(TsgHubBingoView view)
	{
		if (view == null)
		{
			clear();
			return;
		}
		TsgHubBingoView previous = snapshot;
		if (previous == null || !previous.key.equals(view.key)) flips.clear();
		else TsgHubBoardGrid.queueFlips(flips, view.board, previous.done, view.done);
		inLines = TsgHubBoardGrid.cellsInLines(view.board, view.lines);
		snapshot = view;
		version++;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TsgHubBingoView data = snapshot;
		if (!visible || data == null || data.tasks.isEmpty()) return null;
		int n = data.board.size;
		int cell = n <= 4 ? 44 : n <= 5 ? 38 : n <= 7 ? 32 : 26;
		int gridW = cell * n + GAP * (n - 1);
		layoutCell = cell;
		layoutSize = n;
		int width = gridW + PAD * 2;
		int height = HEADER + gridW + FOOTER + PAD * 2;
		long now = System.currentTimeMillis();
		Graphics2D g = TsgHubTheme.smooth(graphics);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		if (flips.isEmpty())
		{
			int v = version;
			if (cache == null || cacheVersion != v || cache.getWidth() != width || cache.getHeight() != height)
			{
				cache = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				Graphics2D c = cache.createGraphics();
				c.setRenderingHints(g.getRenderingHints());
				paintBoard(c, data, cell, gridW, width, height, now);
				c.dispose();
				cacheVersion = v;
			}
			g.drawImage(cache, 0, 0, null);
		}
		else paintBoard(g, data, cell, gridW, width, height, now);
		paintPeek(g, data, now, cell, PAD + HEADER, gridW, FontManager.getRunescapeSmallFont());
		g.dispose();
		return new Dimension(width, height);
	}

	private void paintBoard(Graphics2D g, TsgHubBingoView data, int cell, int gridW, int width, int height, long now)
	{
		int n = data.board.size;
		Set<Integer> lit = inLines;
		g.setColor(new Color(20, 20, 20, 215));
		g.fill(new RoundRectangle2D.Double(0, 0, width, height, 10, 10));
		g.setColor(new Color(90, 90, 90, 200));
		g.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, 10, 10));

		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = g.getFontMetrics();
		g.setColor(TsgHubBoardGrid.GOLD);
		g.drawString(TsgHubBoardGrid.ellipsize(metrics, data.name, gridW), PAD, PAD + metrics.getAscent() - 2);

		Font small = FontManager.getRunescapeSmallFont();
		int top = PAD + HEADER;
		boolean[] flipping = {false};
		int[] hovered = hover;
		TsgHubBoardGrid.paintCells(g, data.board, PAD, top, cell, GAP, (row, col, x, y, id) -> {
			JsonObject task = data.tasks.get(id);
			TsgHubBoardGrid.Tile tile = new TsgHubBoardGrid.Tile(task, data.progress.get(id), data.done.contains(id), lit.contains(row * n + col), false, hovered != null && hovered[0] == row && hovered[1] == col, icons.icon(task));
			if (TsgHubBoardGrid.paintFlip(g, x, y, cell, tile, small, false, flips.get(id), now)) flipping[0] = true;
			else flips.remove(id);
		});
		if (!flipping[0]) for (TsgHubBingoBoard.Line line : data.lines) TsgHubBoardGrid.paintLine(g, PAD, top, cell, GAP, n, line, 1, false);

		g.setFont(small);
		metrics = g.getFontMetrics();
		g.setColor(TsgHubTheme.MUTED);
		g.drawString(data.left() + " left · " + TsgHubFormat.plural(data.lines.size(), "line"), PAD, height - PAD - 2);
		String points = data.points + " pts";
		g.setColor(TsgHubTheme.TEXT);
		g.drawString(points, width - PAD - metrics.stringWidth(points), height - PAD - 2);
	}

	private double peekProgress(long now)
	{
		if (peekClosing == 0 && now - peekOpened > PEEK_MS + PEEK_HOLD_MS) peekClosing = now;
		if (peekClosing != 0)
		{
			double p = 1 - Math.min(1, (now - peekClosing) / (double) PEEK_MS);
			if (p <= 0) peekId = null;
			return p;
		}
		return Math.min(1, (now - peekOpened) / (double) PEEK_MS);
	}

	private Rectangle2D.Double peekRect(String id, TsgHubBingoView data, int cell, int top, int gridW, double p)
	{
		int[] at = data.board.cellOf(id);
		if (at == null) return null;
		double x = PAD + at[1] * (cell + GAP);
		double y = top + at[0] * (cell + GAP);
		double size = Math.min(gridW, Math.max(cell * 2.6, 112));
		double cx = Math.max(PAD + size / 2, Math.min(PAD + gridW - size / 2, x + cell / 2.0));
		double cy = Math.max(top + size / 2, Math.min(top + gridW - size / 2, y + cell / 2.0));
		double e = TsgHubRevealOverlay.easeOutCubic(p);
		double w = cell + (size - cell) * e;
		double mx = (x + cell / 2.0) + (cx - (x + cell / 2.0)) * e;
		double my = (y + cell / 2.0) + (cy - (y + cell / 2.0)) * e;
		return new Rectangle2D.Double(mx - w / 2, my - w / 2, w, w);
	}

	private void paintPeek(Graphics2D g, TsgHubBingoView data, long now, int cell, int top, int gridW, Font small)
	{
		String id = peekId;
		if (id == null) return;
		double p = peekProgress(now);
		Rectangle2D.Double rect = peekRect(id, data, cell, top, gridW, p);
		if (rect == null || peekId == null) return;
		JsonObject task = data.tasks.get(id);
		double turn = Math.max(0.04, Math.abs(Math.cos(Math.PI * p)));
		Graphics2D card = (Graphics2D) g.create();
		card.translate(rect.getCenterX(), rect.getCenterY());
		card.scale(turn, 1);
		card.translate(-rect.getCenterX(), -rect.getCenterY());
		if (p < 0.5)
		{
			double scale = rect.width / cell;
			card.translate(rect.x, rect.y);
			card.scale(scale, scale);
			TsgHubBoardGrid.Tile tile = new TsgHubBoardGrid.Tile(task, data.progress.get(id), data.done.contains(id), false, false, false, icons.icon(task));
			TsgHubBoardGrid.paintTile(card, 0, 0, cell, tile, small, false);
		}
		else paintBack(card, rect, task, data.progress.get(id), data.done.contains(id));
		card.dispose();
	}

	private static void paintBack(Graphics2D g, Rectangle2D.Double rect, JsonObject task, JsonObject progress, boolean done)
	{
		RoundRectangle2D.Double shape = new RoundRectangle2D.Double(rect.x, rect.y, rect.width, rect.height, 8, 8);
		g.setColor(new Color(16, 16, 16, 120));
		g.fill(new RoundRectangle2D.Double(rect.x + 2, rect.y + 3, rect.width, rect.height, 8, 8));
		g.setColor(new Color(34, 34, 34));
		g.fill(shape);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(done ? TsgHubTheme.SUCCESS : TsgHubBoardGrid.GOLD);
		g.draw(shape);
		Graphics2D text = (Graphics2D) g.create();
		text.clip(shape);
		int inset = 6;
		int width = (int) rect.width - inset * 2;
		int x = (int) rect.x + inset;
		int y = (int) rect.y + inset;
		Font bold = FontManager.getRunescapeBoldFont();
		Font small = FontManager.getRunescapeSmallFont();
		text.setFont(bold);
		FontMetrics metrics = text.getFontMetrics();
		for (String line : TsgHubBoardGrid.wrapLines(metrics, str(task, "title"), width, 3))
		{
			y += metrics.getAscent();
			text.setColor(TsgHubTheme.TEXT);
			text.drawString(line, x, y);
			y += metrics.getDescent() - 2;
		}
		text.setFont(small);
		metrics = text.getFontMetrics();
		y += 2;
		int value = integer(progress, "progress", 0);
		int target = Math.max(1, integer(progress, "target", 1));
		String status = TsgHubBoardGrid.status(progress, done);
		y += metrics.getHeight();
		text.setColor(done ? TsgHubTheme.SUCCESS : bool(progress, "pending") ? TsgHubTheme.WARNING : TsgHubTheme.TEXT);
		text.drawString(status, x, y);
		if (!done && target > 1)
		{
			int barY = y + 4;
			text.setColor(new Color(0, 0, 0, 140));
			text.fillRoundRect(x, barY, width, 4, 4, 4);
			text.setColor(TsgHubTheme.ACCENT);
			text.fillRoundRect(x, barY, (int) Math.round(width * Math.min(1.0, value / (double) target)), 4, 4, 4);
		}
		String points = integer(task, "points", 1) + " pts";
		text.setColor(TsgHubBoardGrid.GOLD);
		text.drawString(points, (int) (rect.x + rect.width) - inset - metrics.stringWidth(points), (int) (rect.y + rect.height) - inset);
		text.dispose();
	}

	private Point local(MouseEvent event)
	{
		Rectangle bounds = getBounds();
		if (bounds == null || !bounds.contains(event.getPoint())) return null;
		return new Point(event.getX() - bounds.x, event.getY() - bounds.y);
	}

	private int[] tileAt(Point point)
	{
		return TsgHubBoardGrid.cellAt(point.x - PAD, point.y - PAD - HEADER, layoutCell, GAP, layoutSize);
	}

	private boolean active(MouseEvent event)
	{
		return visible && snapshot != null && !event.isAltDown() && SwingUtilities.isLeftMouseButton(event);
	}

	final MouseAdapter mouse = new MouseAdapter()
	{
		@Override public MouseEvent mousePressed(MouseEvent event)
		{
			TsgHubBingoView data = snapshot;
			if (data == null || !active(event)) return event;
			Point point = local(event);
			if (point == null) return event;
			event.consume();
			long now = System.currentTimeMillis();
			String open = peekId;
			if (open != null && peekClosing == 0)
			{
				Rectangle2D.Double rect = peekRect(open, data, layoutCell, PAD + HEADER, layoutCell * layoutSize + GAP * (layoutSize - 1), 1);
				if (rect != null && rect.contains(point))
				{
					peekClosing = now;
					return event;
				}
			}
			int[] at = tileAt(point);
			String id = at == null ? null : data.board.taskAt(at[0], at[1]);
			if (id == null) return event;
			onPeek.run();
			peekId = id;
			peekOpened = now;
			peekClosing = 0;
			return event;
		}

		@Override public MouseEvent mouseReleased(MouseEvent event)
		{
			if (active(event) && local(event) != null) event.consume();
			return event;
		}

		@Override public MouseEvent mouseClicked(MouseEvent event)
		{
			if (active(event) && local(event) != null) event.consume();
			return event;
		}

		@Override public MouseEvent mouseMoved(MouseEvent event)
		{
			Point point = visible ? local(event) : null;
			setHover(point == null ? null : tileAt(point));
			return event;
		}

		@Override public MouseEvent mouseExited(MouseEvent event)
		{
			setHover(null);
			return event;
		}
	};

	private void setHover(int[] at)
	{
		if (Arrays.equals(at, hover)) return;
		hover = at;
		version++;
	}
}
