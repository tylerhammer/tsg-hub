package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.Timer;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

final class TsgHubBoardGrid extends JComponent
{
	interface SelectListener { void selected(String taskId, int row, int col); }
	interface SwapListener { void swapped(int row, int col, int otherRow, int otherCol); }

	static final Color GOLD = new Color(232, 183, 91);
	private static final int GAP = 3;
	private static final int FLIP_MS = 520;
	private static final int SHINE_MS = 420;
	private static final int STAGGER_MS = 140;
	private static final int LINE_MS = 380;
	private static final int BONUS_MS = 1100;
	private static final int PET_ICON = 12646;
	private static final int JAR_ICON = 12936;

	private final IntFunction<AsyncBufferedImage> images;
	private int fixedCell;
	private final Timer timer = new Timer(16, e -> tick());
	private final Map<String, Long> flips = new HashMap<>();
	private final Map<String, Long> lineStarts = new HashMap<>();
	private final Map<Integer, AsyncBufferedImage> icons = new HashMap<>();
	private final JLabel repainter = new JLabel()
	{
		@Override public void repaint()
		{
			TsgHubBoardGrid.this.repaint();
		}
	};
	private TsgHubBingoBoard board;
	private Map<String, JsonObject> tasks = Collections.emptyMap();
	private Map<String, JsonObject> progress = Collections.emptyMap();
	private Set<String> done = Collections.emptySet();
	private List<TsgHubBingoBoard.Line> lines = Collections.emptyList();
	private String dataKey;
	private Set<String> lastDone;
	private Set<String> lastLines;
	private String selectedTask = "";
	private int[] picked;
	private int[] hover;
	private SelectListener selectListener;
	private SwapListener swapListener;

	TsgHubBoardGrid(IntFunction<AsyncBufferedImage> images, int fixedCell)
	{
		this.images = images;
		this.fixedCell = fixedCell;
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("");
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override public void mouseMoved(MouseEvent e) { setHover(cellAt(e.getPoint())); }
			@Override public void mouseExited(MouseEvent e) { setHover(null); }
			@Override public void mouseReleased(MouseEvent e)
			{
				int[] at = cellAt(e.getPoint());
				if (at != null) click(at);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setCellSize(int cell)
	{
		fixedCell = cell;
		revalidate();
	}

	void onSelect(SelectListener listener)
	{
		selectListener = listener;
	}

	void onSwap(SwapListener listener)
	{
		swapListener = listener;
	}

	void setSelectedTask(String taskId)
	{
		selectedTask = taskId == null ? "" : taskId;
		repaint();
	}

	String selectedTask()
	{
		return selectedTask;
	}

	TsgHubBingoBoard board()
	{
		return board;
	}

	void setData(String key, TsgHubBingoBoard board, JsonArray taskList, JsonArray progressRows)
	{
		this.board = board;
		Map<String, JsonObject> byId = new HashMap<>();
		for (int i = 0; i < taskList.size(); i++)
		{
			JsonObject task = taskList.get(i).getAsJsonObject();
			byId.put(TsgHubUi.str(task, "id"), task);
		}
		Map<String, JsonObject> rows = new HashMap<>();
		Set<String> nowDone = new HashSet<>();
		for (int i = 0; i < progressRows.size(); i++)
		{
			JsonObject row = progressRows.get(i).getAsJsonObject();
			rows.put(TsgHubUi.str(row, "taskId"), row);
			if (TsgHubUi.bool(row, "completed")) nowDone.add(TsgHubUi.str(row, "taskId"));
		}
		tasks = byId;
		progress = rows;
		done = nowDone;
		lines = board.completedLines(nowDone);
		Set<String> lineKeys = new HashSet<>();
		for (TsgHubBingoBoard.Line line : lines) lineKeys.add(line.key());
		if (!key.equals(dataKey))
		{
			flips.clear();
			lineStarts.clear();
			picked = null;
		}
		else if (lastDone != null)
		{
			long start = System.currentTimeMillis();
			for (String id : orderedCells())
			{
				if (!nowDone.contains(id) || lastDone.contains(id)) continue;
				flips.put(id, start);
				start += STAGGER_MS;
			}
			for (String line : lineKeys) if (!lastLines.contains(line)) lineStarts.put(line, start + FLIP_MS);
		}
		dataKey = key;
		lastDone = nowDone;
		lastLines = lineKeys;
		if (!flips.isEmpty() || !lineStarts.isEmpty()) timer.start();
		revalidate();
		repaint();
	}

	void clearPick()
	{
		picked = null;
		repaint();
	}

	private List<String> orderedCells()
	{
		List<String> ids = new ArrayList<>();
		for (int row = 0; row < board.size; row++)
			for (int col = 0; col < board.size; col++)
				if (board.taskAt(row, col) != null) ids.add(board.taskAt(row, col));
		return ids;
	}

	private void tick()
	{
		long now = System.currentTimeMillis();
		flips.values().removeIf(start -> now - start > FLIP_MS + SHINE_MS);
		lineStarts.values().removeIf(start -> now - start > LINE_MS + BONUS_MS);
		if (flips.isEmpty() && lineStarts.isEmpty()) timer.stop();
		repaint();
	}

	private void click(int[] at)
	{
		String id = board.taskAt(at[0], at[1]);
		if (swapListener != null)
		{
			if (picked == null)
			{
				if (id == null) return;
				picked = at;
			}
			else
			{
				int[] from = picked;
				picked = null;
				if (from[0] != at[0] || from[1] != at[1]) swapListener.swapped(from[0], from[1], at[0], at[1]);
			}
			repaint();
			return;
		}
		if (id == null) return;
		selectedTask = id.equals(selectedTask) ? "" : id;
		repaint();
		if (selectListener != null) selectListener.selected(selectedTask, at[0], at[1]);
	}

	private void setHover(int[] at)
	{
		boolean same = at == null ? hover == null : hover != null && hover[0] == at[0] && hover[1] == at[1];
		if (same) return;
		hover = at;
		repaint();
	}

	private int cell()
	{
		if (fixedCell > 0) return fixedCell;
		int n = board == null ? TsgHubBingoBoard.MIN_SIZE : board.size;
		return Math.max(12, (getWidth() - GAP * (n - 1)) / n);
	}

	private int offsetX()
	{
		int n = board.size;
		return Math.max(0, (getWidth() - (cell() * n + GAP * (n - 1))) / 2);
	}

	private int[] cellAt(Point point)
	{
		if (board == null) return null;
		int step = cell() + GAP;
		int x = point.x - offsetX();
		if (x < 0 || point.y < 0) return null;
		int col = x / step;
		int row = point.y / step;
		if (row >= board.size || col >= board.size || x % step >= cell() || point.y % step >= cell()) return null;
		return new int[]{row, col};
	}

	@Override
	public Dimension getPreferredSize()
	{
		int n = board == null ? TsgHubBingoBoard.MIN_SIZE : board.size;
		if (fixedCell > 0)
		{
			int side = fixedCell * n + GAP * (n - 1);
			return new Dimension(side, side);
		}
		int width = 205;
		if (getParent() != null && getParent().getWidth() > 0)
		{
			Insets insets = getParent().getInsets();
			width = getParent().getWidth() - insets.left - insets.right;
		}
		int cell = Math.max(12, (width - GAP * (n - 1)) / n);
		return new Dimension(width, cell * n + GAP * (n - 1));
	}

	@Override
	public Dimension getMaximumSize()
	{
		return fixedCell > 0 ? getPreferredSize() : new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		int[] at = cellAt(event.getPoint());
		if (at == null) return null;
		String id = board.taskAt(at[0], at[1]);
		if (id == null) return swapListener != null ? "Empty cell" : null;
		JsonObject task = tasks.get(id);
		if (task == null) return null;
		JsonObject row = progress.get(id);
		StringBuilder text = new StringBuilder("<html><b>").append(TsgHubUi.escape(TsgHubUi.str(task, "title"))).append("</b>");
		text.append("<br>").append(TsgHubUi.integer(task, "points", 1)).append(" pts · ").append(TsgHubUi.escape(TsgHubUi.taskTypeLabel(task)));
		if (row != null && !swapListener())
		{
			if (TsgHubUi.bool(row, "completed")) text.append("<br>Done");
			else if (TsgHubUi.integer(row, "target", 1) > 1) text.append("<br>").append(TsgHubUi.integer(row, "progress", 0)).append(" / ").append(TsgHubUi.integer(row, "target", 1));
			if (TsgHubUi.bool(row, "pending") && !TsgHubUi.bool(row, "completed")) text.append("<br>Awaiting admin review");
		}
		return text.append("</html>").toString();
	}

	private boolean swapListener()
	{
		return swapListener != null;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		if (board == null) return;
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int cell = cell();
		int left = offsetX();
		long now = System.currentTimeMillis();
		Set<String> inLines = cellsInLines(board, lines);
		Font font = FontManager.getRunescapeSmallFont();
		boolean names = cell >= 56;
		for (int row = 0; row < board.size; row++)
		{
			for (int col = 0; col < board.size; col++)
			{
				int x = left + col * (cell + GAP);
				int y = row * (cell + GAP);
				String id = board.taskAt(row, col);
				boolean hovered = hover != null && hover[0] == row && hover[1] == col;
				boolean isPicked = picked != null && picked[0] == row && picked[1] == col;
				if (id == null)
				{
					paintEmpty(g, x, y, cell, hovered && swapListener != null, isPicked);
					continue;
				}
				JsonObject task = tasks.get(id);
				JsonObject taskProgress = progress.get(id);
				boolean complete = done.contains(id);
				Tile tile = new Tile(task, taskProgress, complete, inLines.contains(row + ":" + col), id.equals(selectedTask) || isPicked, hovered, icon(task));
				Long flip = flips.get(id);
				if (flip == null || now < flip)
				{
					paintTile(g, x, y, cell, flip == null ? tile : tile.faceDown(), font, names);
					continue;
				}
				double t = Math.min(1, (now - flip) / (double) FLIP_MS);
				if (t < 1)
				{
					Graphics2D flipped = (Graphics2D) g.create();
					double scale = Math.max(0.04, Math.abs(Math.cos(Math.PI * t)));
					flipped.translate(x + cell / 2.0, 0);
					flipped.scale(scale, 1);
					flipped.translate(-(x + cell / 2.0), 0);
					paintTile(flipped, x, y, cell, t < 0.5 ? tile.faceDown() : tile, font, names);
					flipped.dispose();
				}
				else
				{
					paintTile(g, x, y, cell, tile, font, names);
					paintShine(g, x, y, cell, (now - flip - FLIP_MS) / (double) SHINE_MS);
				}
			}
		}
		for (TsgHubBingoBoard.Line line : lines)
		{
			Long start = lineStarts.get(line.key());
			if (start != null && now < start) continue;
			double fraction = start == null ? 1 : Math.min(1, (now - start) / (double) LINE_MS);
			paintLine(g, left, 0, cell, GAP, board.size, line, fraction);
		}
		for (TsgHubBingoBoard.Line line : lines)
		{
			Long start = lineStarts.get(line.key());
			int bonus = board.bonusFor(line);
			if (start == null || bonus <= 0 || now < start + LINE_MS) continue;
			paintBonus(g, left, cell, line, "+" + bonus, (now - start - LINE_MS) / (double) BONUS_MS);
		}
		g.dispose();
	}

	private AsyncBufferedImage icon(JsonObject task)
	{
		int itemId = iconItem(task);
		if (itemId <= 0 || images == null) return null;
		return icons.computeIfAbsent(itemId, id -> {
			AsyncBufferedImage image = images.apply(id);
			if (image != null) image.addTo(repainter);
			return image;
		});
	}

	static int iconItem(JsonObject task)
	{
		if (task == null) return 0;
		JsonObject config = task.has("config") && task.get("config").isJsonObject() ? task.getAsJsonObject("config") : new JsonObject();
		switch (TsgHubUi.str(task, "type"))
		{
			case "drop":
				JsonArray groups = TsgHubUi.array(config, "itemGroups");
				if (groups.size() > 0 && groups.get(0).isJsonArray() && groups.get(0).getAsJsonArray().size() > 0)
					return TsgHubUi.integer(groups.get(0).getAsJsonArray().get(0).getAsJsonObject(), "id", 0);
				if ("pet".equals(TsgHubUi.str(config, "itemGroup"))) return PET_ICON;
				if ("jar".equals(TsgHubUi.str(config, "itemGroup"))) return JAR_ICON;
				JsonArray ids = TsgHubUi.array(config, "itemIds");
				for (int i = 0; i < ids.size(); i++) if (ids.get(i).isJsonPrimitive() && ids.get(i).getAsInt() > 0) return ids.get(i).getAsInt();
				return 0;
			default:
				return 0;
		}
	}

	static final class Tile
	{
		final JsonObject task;
		final JsonObject progress;
		final boolean complete;
		final boolean inLine;
		final boolean selected;
		final boolean hovered;
		final BufferedImage image;

		Tile(JsonObject task, JsonObject progress, boolean complete, boolean inLine, boolean selected, boolean hovered, BufferedImage image)
		{
			this.task = task;
			this.progress = progress;
			this.complete = complete;
			this.inLine = inLine;
			this.selected = selected;
			this.hovered = hovered;
			this.image = image;
		}

		Tile faceDown()
		{
			return new Tile(task, progress, false, false, selected, hovered, image);
		}
	}

	static Set<String> cellsInLines(TsgHubBingoBoard board, List<TsgHubBingoBoard.Line> lines)
	{
		Set<String> cells = new HashSet<>();
		for (TsgHubBingoBoard.Line line : lines)
		{
			for (int i = 0; i < board.size; i++)
			{
				int[] at = line.cell(i, board.size);
				cells.add(at[0] + ":" + at[1]);
			}
		}
		return cells;
	}

	static void paintEmpty(Graphics2D g, int x, int y, int cell, boolean hovered, boolean picked)
	{
		Shape shape = new RoundRectangle2D.Double(x + 0.5, y + 0.5, cell - 1, cell - 1, 6, 6);
		g.setColor(hovered ? TsgHubUi.CARD_HOVER : new Color(0, 0, 0, 40));
		g.fill(shape);
		g.setColor(picked ? Color.WHITE : TsgHubUi.BORDER);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f));
		g.draw(shape);
	}

	static void paintTile(Graphics2D g, int x, int y, int cell, Tile tile, Font font, boolean names)
	{
		Shape shape = new RoundRectangle2D.Double(x + 0.5, y + 0.5, cell - 1, cell - 1, 6, 6);
		Color base = tile.hovered ? TsgHubUi.CARD_HOVER : TsgHubUi.CARD;
		g.setColor(tile.complete ? blend(base, TsgHubUi.SUCCESS, 0.32) : base);
		g.fill(shape);

		int value = TsgHubUi.integer(tile.progress, "progress", 0);
		int target = Math.max(1, TsgHubUi.integer(tile.progress, "target", 1));
		boolean pending = TsgHubUi.bool(tile.progress, "pending");
		Graphics2D content = (Graphics2D) g.create();
		content.clip(shape);
		if (tile.complete) content.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
		int textTop = y + 3;
		if (tile.image != null)
		{
			int max = Math.min(names ? cell / 2 : cell - 10, Math.max(tile.image.getWidth(), 32));
			double scale = Math.min(max / (double) tile.image.getWidth(), max / (double) tile.image.getHeight());
			int w = (int) Math.round(tile.image.getWidth() * scale);
			int h = (int) Math.round(tile.image.getHeight() * scale);
			int iy = names ? y + 4 : y + (cell - h) / 2 - 1;
			content.drawImage(tile.image, x + (cell - w) / 2, iy, w, h, null);
			textTop = iy + h + 1;
		}
		if (tile.image == null || names)
		{
			content.setFont(font);
			content.setColor(tile.complete ? TsgHubUi.MUTED : TsgHubUi.TEXT);
			drawWrapped(content, TsgHubUi.str(tile.task, "title"), x + 3, textTop, cell - 6, y + cell - 5);
		}
		content.dispose();

		boolean flagged = pending && !tile.complete;
		if (!tile.complete && value > 0 && target > 1)
		{
			int track = cell - 8 - (flagged ? 9 : 0);
			int barW = (int) Math.round(track * Math.min(1.0, value / (double) target));
			g.setColor(new Color(0, 0, 0, 120));
			g.fillRoundRect(x + 4, y + cell - 6, track, 3, 3, 3);
			g.setColor(TsgHubUi.ACCENT);
			g.fillRoundRect(x + 4, y + cell - 6, barW, 3, 3, 3);
		}
		if (flagged)
		{
			g.setColor(TsgHubUi.WARNING);
			g.fillOval(x + cell - 10, y + cell - 8, 6, 6);
		}
		if (tile.complete) paintCheck(g, x + cell - 13, y + cell - 13, 10);

		g.setStroke(new BasicStroke(tile.selected || tile.inLine ? 2f : 1f));
		g.setColor(tile.selected ? Color.WHITE : tile.inLine ? GOLD : tile.complete ? TsgHubUi.SUCCESS.darker() : TsgHubUi.BORDER);
		g.draw(new RoundRectangle2D.Double(x + 1, y + 1, cell - 2, cell - 2, 6, 6));
	}

	private static void paintCheck(Graphics2D g, int x, int y, int size)
	{
		g.setColor(TsgHubUi.SUCCESS);
		g.fillOval(x, y, size, size);
		g.setColor(TsgHubUi.CARD);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Path2D tick = new Path2D.Double();
		tick.moveTo(x + size * 0.25, y + size * 0.53);
		tick.lineTo(x + size * 0.43, y + size * 0.71);
		tick.lineTo(x + size * 0.75, y + size * 0.32);
		g.draw(tick);
	}

	private static void paintShine(Graphics2D g, int x, int y, int cell, double t)
	{
		if (t < 0 || t >= 1) return;
		Graphics2D shine = (Graphics2D) g.create();
		shine.clip(new RoundRectangle2D.Double(x, y, cell, cell, 6, 6));
		float alpha = (float) (0.55 * Math.sin(Math.PI * t));
		int band = cell / 2;
		int center = (int) (x - band + (cell + band * 2) * t);
		shine.setPaint(new GradientPaint(center - band / 2f, y, new Color(255, 236, 190, 0), center, y + cell / 2f, new Color(255, 236, 190, (int) (255 * alpha)), true));
		shine.fillRect(x, y, cell, cell);
		shine.dispose();
	}

	static void paintLine(Graphics2D g, int left, int top, int cell, int gap, int size, TsgHubBingoBoard.Line line, double fraction)
	{
		int[] first = line.cell(0, size);
		int[] last = line.cell(size - 1, size);
		double x1 = left + first[1] * (cell + gap) + cell / 2.0;
		double y1 = top + first[0] * (cell + gap) + cell / 2.0;
		double x2 = left + last[1] * (cell + gap) + cell / 2.0;
		double y2 = top + last[0] * (cell + gap) + cell / 2.0;
		Graphics2D stroke = (Graphics2D) g.create();
		stroke.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 120));
		stroke.setStroke(new BasicStroke(Math.max(3f, cell / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		stroke.draw(new java.awt.geom.Line2D.Double(x1, y1, x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction));
		stroke.dispose();
	}

	private void paintBonus(Graphics2D g, int left, int cell, TsgHubBingoBoard.Line line, String text, double t)
	{
		if (t >= 1) return;
		int[] first = line.cell(0, board.size);
		int[] last = line.cell(board.size - 1, board.size);
		double cx = left + (first[1] + last[1]) / 2.0 * (cell + GAP) + cell / 2.0;
		double cy = (first[0] + last[0]) / 2.0 * (cell + GAP) + cell / 2.0 - t * 14;
		float alpha = (float) (t < 0.6 ? 1 : 1 - (t - 0.6) / 0.4);
		Graphics2D label = (Graphics2D) g.create();
		label.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
		label.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = label.getFontMetrics();
		int w = metrics.stringWidth(text) + 10;
		int h = metrics.getHeight() + 2;
		int x = (int) Math.round(cx - w / 2.0);
		int y = (int) Math.round(cy - h / 2.0);
		label.setColor(new Color(20, 20, 20, 230));
		label.fillRoundRect(x, y, w, h, h, h);
		label.setColor(GOLD);
		label.drawRoundRect(x, y, w, h, h, h);
		label.drawString(text, x + 5, y + metrics.getAscent() + 1);
		label.dispose();
	}

	static void drawWrapped(Graphics2D g, String text, int x, int top, int width, int bottom)
	{
		FontMetrics metrics = g.getFontMetrics();
		int lineHeight = metrics.getHeight() - 2;
		int maxLines = Math.max(1, (bottom - top) / lineHeight);
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+"))
		{
			String next = line.length() == 0 ? word : line + " " + word;
			if (metrics.stringWidth(next) <= width || line.length() == 0)
			{
				line.setLength(0);
				line.append(next);
			}
			else
			{
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0) lines.add(line.toString());
		int shown = Math.min(lines.size(), maxLines);
		int y = top + metrics.getAscent() - 1;
		for (int i = 0; i < shown; i++)
		{
			String part = lines.get(i);
			if (i == shown - 1 && shown < lines.size()) part = part + "…";
			while (metrics.stringWidth(part) > width && part.length() > 1) part = part.substring(0, part.length() - 2) + "…";
			g.drawString(part, x + Math.max(0, (width - metrics.stringWidth(part)) / 2), y);
			y += lineHeight;
		}
	}

	private static Color blend(Color a, Color b, double amount)
	{
		return new Color(
			(int) (a.getRed() + (b.getRed() - a.getRed()) * amount),
			(int) (a.getGreen() + (b.getGreen() - a.getGreen()) * amount),
			(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * amount));
	}
}
