package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubBoardOverlay extends Overlay implements MouseListener
{
	private static final int PAD = 8;
	private static final int GAP = 3;
	private static final int HEADER = 20;
	private static final int FOOTER = 16;
	private static final int FLIP_MS = 520;
	private static final int STAGGER_MS = 140;
	private static final int PEEK_MS = 280;
	private static final int PEEK_HOLD_MS = 4000;

	private static final class Snapshot
	{
		final String key;
		final String name;
		final TsgHubBingoBoard board;
		final Map<String, JsonObject> tasks;
		final Map<String, JsonObject> progress;
		final Set<String> done;
		final List<TsgHubBingoBoard.Line> lines;
		final Set<Integer> inLines;
		final int points;

		Snapshot(String key, String name, TsgHubBingoBoard board, Map<String, JsonObject> tasks, Map<String, JsonObject> progress, Set<String> done, List<TsgHubBingoBoard.Line> lines, int points)
		{
			this.inLines = TsgHubBoardGrid.cellsInLines(board, lines);
			this.key = key;
			this.name = name;
			this.board = board;
			this.tasks = tasks;
			this.progress = progress;
			this.done = done;
			this.lines = lines;
			this.points = points;
		}
	}

	private final TsgHubTileIcons icons;
	private final Map<String, Long> flips = new ConcurrentHashMap<>();
	private volatile Snapshot snapshot;
	private volatile boolean visible;
	private volatile int layoutCell;
	private volatile int layoutSize;
	private volatile String peekId;
	private volatile long peekOpened;
	private volatile long peekClosing;
	private volatile int[] hover;

	TsgHubBoardOverlay(TsgHubTileIcons icons)
	{
		this.icons = icons;
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
		flips.clear();
		peekId = null;
		hover = null;
	}

	void setEvent(JsonObject event, String teamId)
	{
		if (event == null || teamId == null || teamId.isEmpty() || !TsgHubBingoBoard.hasBoard(event))
		{
			clear();
			return;
		}
		JsonArray taskList = TsgHubUi.array(event, "tasks");
		JsonObject score = TsgHubUi.scoreFor(TsgHubUi.array(event, "teamScores"), teamId);
		JsonArray rows = TsgHubUi.array(score, "tasks");
		Map<String, JsonObject> tasks = new HashMap<>();
		for (int i = 0; i < taskList.size(); i++) tasks.put(TsgHubUi.str(taskList.get(i).getAsJsonObject(), "id"), taskList.get(i).getAsJsonObject());
		Map<String, JsonObject> progress = new HashMap<>();
		Set<String> done = new HashSet<>();
		for (int i = 0; i < rows.size(); i++)
		{
			JsonObject row = rows.get(i).getAsJsonObject();
			progress.put(TsgHubUi.str(row, "taskId"), row);
			if (TsgHubUi.bool(row, "completed")) done.add(TsgHubUi.str(row, "taskId"));
		}
		TsgHubBingoBoard board = TsgHubBingoBoard.of(event);
		String key = TsgHubUi.str(event, "id") + ":" + teamId;
		Snapshot previous = snapshot;
		if (previous == null || !previous.key.equals(key)) flips.clear();
		else
		{
			long start = System.currentTimeMillis();
			for (int row = 0; row < board.size; row++)
			{
				for (int col = 0; col < board.size; col++)
				{
					String id = board.taskAt(row, col);
					if (id == null || !done.contains(id) || previous.done.contains(id)) continue;
					flips.put(id, start);
					start += STAGGER_MS;
				}
			}
		}
		snapshot = new Snapshot(key, TsgHubUi.str(event, "name"), board, tasks, progress, Collections.unmodifiableSet(done), board.completedLines(done), TsgHubUi.integer(score, "points", 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Snapshot data = snapshot;
		if (!visible || data == null || data.tasks.isEmpty()) return null;
		TsgHubBingoBoard board = data.board;
		int n = board.size;
		int cell = n <= 4 ? 44 : n <= 5 ? 38 : n <= 7 ? 32 : 26;
		int gridW = cell * n + GAP * (n - 1);
		layoutCell = cell;
		layoutSize = n;
		int width = gridW + PAD * 2;
		int height = HEADER + gridW + FOOTER + PAD * 2;
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setColor(new Color(20, 20, 20, 215));
		g.fill(new RoundRectangle2D.Double(0, 0, width, height, 10, 10));
		g.setColor(new Color(90, 90, 90, 200));
		g.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, 10, 10));

		Font bold = FontManager.getRunescapeBoldFont();
		g.setFont(bold);
		FontMetrics metrics = g.getFontMetrics();
		String name = data.name;
		while (metrics.stringWidth(name) > gridW && name.length() > 3) name = name.substring(0, name.length() - 2) + "…";
		g.setColor(TsgHubBoardGrid.GOLD);
		g.drawString(name, PAD, PAD + metrics.getAscent() - 2);

		long now = System.currentTimeMillis();
		Font small = FontManager.getRunescapeSmallFont();
		int top = PAD + HEADER;
		boolean flipping = false;
		int[] hovered = hover;
		for (int row = 0; row < n; row++)
		{
			for (int col = 0; col < n; col++)
			{
				int x = PAD + col * (cell + GAP);
				int y = top + row * (cell + GAP);
				String id = board.taskAt(row, col);
				if (id == null)
				{
					TsgHubBoardGrid.paintEmpty(g, x, y, cell, false, false);
					continue;
				}
				JsonObject task = data.tasks.get(id);
				TsgHubBoardGrid.Tile tile = new TsgHubBoardGrid.Tile(task, data.progress.get(id), data.done.contains(id), data.inLines.contains(row * n + col), false, hovered != null && hovered[0] == row && hovered[1] == col, icons.icon(task));
				Long flip = flips.get(id);
				if (flip == null)
				{
					TsgHubBoardGrid.paintTile(g, x, y, cell, tile, small, false);
					continue;
				}
				flipping = true;
				double t = (now - flip) / (double) FLIP_MS;
				if (t < 0) TsgHubBoardGrid.paintTile(g, x, y, cell, tile.faceDown(), small, false);
				else if (t >= 1)
				{
					TsgHubBoardGrid.paintTile(g, x, y, cell, tile, small, false);
					TsgHubBoardGrid.paintShine(g, x, y, cell, (t - 1) * FLIP_MS / 420.0);
					if (t > 1 + 420.0 / FLIP_MS) flips.remove(id);
				}
				else
				{
					Graphics2D flipped = (Graphics2D) g.create();
					flipped.translate(x + cell / 2.0, 0);
					flipped.scale(Math.max(0.04, Math.abs(Math.cos(Math.PI * t))), 1);
					flipped.translate(-(x + cell / 2.0), 0);
					TsgHubBoardGrid.paintTile(flipped, x, y, cell, t < 0.5 ? tile.faceDown() : tile, small, false);
					flipped.dispose();
				}
			}
		}
		if (!flipping) for (TsgHubBingoBoard.Line line : data.lines) TsgHubBoardGrid.paintLine(g, PAD, top, cell, GAP, n, line, 1);
		paintPeek(g, data, now, cell, top, gridW, small);

		g.setFont(small);
		metrics = g.getFontMetrics();
		int left = data.tasks.size() - data.done.size();
		String status = left + " left · " + data.lines.size() + (data.lines.size() == 1 ? " line" : " lines");
		g.setColor(TsgHubUi.MUTED);
		g.drawString(status, PAD, height - PAD - 2);
		String points = data.points + " pts";
		g.setColor(TsgHubUi.TEXT);
		g.drawString(points, width - PAD - metrics.stringWidth(points), height - PAD - 2);
		g.dispose();
		return new Dimension(width, height);
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

	private Rectangle2D.Double peekRect(String id, TsgHubBingoBoard board, int cell, int top, int gridW, double p)
	{
		int[] at = cellOf(board, id);
		if (at == null) return null;
		double x = PAD + at[1] * (cell + GAP);
		double y = top + at[0] * (cell + GAP);
		double size = Math.min(gridW, Math.max(cell * 2.6, 112));
		double cx = Math.max(PAD + size / 2, Math.min(PAD + gridW - size / 2, x + cell / 2.0));
		double cy = Math.max(top + size / 2, Math.min(top + gridW - size / 2, y + cell / 2.0));
		double e = 1 - Math.pow(1 - p, 3);
		double w = cell + (size - cell) * e;
		double mx = (x + cell / 2.0) + (cx - (x + cell / 2.0)) * e;
		double my = (y + cell / 2.0) + (cy - (y + cell / 2.0)) * e;
		return new Rectangle2D.Double(mx - w / 2, my - w / 2, w, w);
	}

	private void paintPeek(Graphics2D g, Snapshot data, long now, int cell, int top, int gridW, Font small)
	{
		String id = peekId;
		if (id == null) return;
		double p = peekProgress(now);
		Rectangle2D.Double rect = peekRect(id, data.board, cell, top, gridW, p);
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
		g.setColor(done ? TsgHubUi.SUCCESS : TsgHubBoardGrid.GOLD);
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
		for (String line : TsgHubBoardGrid.wrapLines(metrics, TsgHubUi.str(task, "title"), width, 3))
		{
			y += metrics.getAscent();
			text.setColor(TsgHubUi.TEXT);
			text.drawString(line, x, y);
			y += metrics.getDescent() - 2;
		}
		text.setFont(small);
		metrics = text.getFontMetrics();
		y += metrics.getAscent() + 2;
		text.setColor(TsgHubUi.MUTED);
		text.drawString(TsgHubBoardGrid.wrapLines(metrics, TsgHubUi.taskTypeLabel(task), width, 1).get(0), x, y);
		int value = TsgHubUi.integer(progress, "progress", 0);
		int target = Math.max(1, TsgHubUi.integer(progress, "target", 1));
		String status = done ? "Done" : TsgHubUi.bool(progress, "pending") ? "Awaiting review" : target > 1 ? value + " / " + target : "Not done yet";
		y += metrics.getHeight();
		text.setColor(done ? TsgHubUi.SUCCESS : TsgHubUi.bool(progress, "pending") ? TsgHubUi.WARNING : TsgHubUi.TEXT);
		text.drawString(status, x, y);
		if (!done && target > 1)
		{
			int barY = y + 4;
			text.setColor(new Color(0, 0, 0, 140));
			text.fillRoundRect(x, barY, width, 4, 4, 4);
			text.setColor(TsgHubUi.ACCENT);
			text.fillRoundRect(x, barY, (int) Math.round(width * Math.min(1.0, value / (double) target)), 4, 4, 4);
		}
		String points = TsgHubUi.integer(task, "points", 1) + " pts";
		text.setColor(TsgHubBoardGrid.GOLD);
		text.drawString(points, (int) (rect.x + rect.width) - inset - metrics.stringWidth(points), (int) (rect.y + rect.height) - inset);
		text.dispose();
	}

	private static int[] cellOf(TsgHubBingoBoard board, String id)
	{
		for (int row = 0; row < board.size; row++)
			for (int col = 0; col < board.size; col++)
				if (id.equals(board.taskAt(row, col))) return new int[]{row, col};
		return null;
	}

	private Point local(MouseEvent event)
	{
		Rectangle bounds = getBounds();
		if (bounds == null || !bounds.contains(event.getPoint())) return null;
		return new Point(event.getX() - bounds.x, event.getY() - bounds.y);
	}

	private int[] tileAt(Point point)
	{
		int cell = layoutCell;
		int n = layoutSize;
		if (cell <= 0 || n <= 0) return null;
		int x = point.x - PAD;
		int y = point.y - PAD - HEADER;
		int step = cell + GAP;
		if (x < 0 || y < 0 || x % step >= cell || y % step >= cell) return null;
		int col = x / step;
		int row = y / step;
		return row < n && col < n ? new int[]{row, col} : null;
	}

	private boolean active(MouseEvent event)
	{
		return visible && snapshot != null && !event.isAltDown() && SwingUtilities.isLeftMouseButton(event);
	}

	@Override public MouseEvent mousePressed(MouseEvent event)
	{
		if (!active(event)) return event;
		Point point = local(event);
		if (point == null) return event;
		event.consume();
		Snapshot data = snapshot;
		long now = System.currentTimeMillis();
		String open = peekId;
		if (open != null && peekClosing == 0)
		{
			Rectangle2D.Double rect = peekRect(open, data.board, layoutCell, PAD + HEADER, layoutCell * layoutSize + GAP * (layoutSize - 1), 1);
			if (rect != null && rect.contains(point))
			{
				peekClosing = now;
				return event;
			}
		}
		int[] at = tileAt(point);
		String id = at == null ? null : data.board.taskAt(at[0], at[1]);
		if (id == null) return event;
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
		hover = point == null ? null : tileAt(point);
		return event;
	}

	@Override public MouseEvent mouseExited(MouseEvent event)
	{
		hover = null;
		return event;
	}

	@Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
	@Override public MouseEvent mouseDragged(MouseEvent event) { return event; }
}
