package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubBoardOverlay extends Overlay
{
	private static final int PAD = 8;
	private static final int GAP = 3;
	private static final int HEADER = 20;
	private static final int FOOTER = 16;
	private static final int FLIP_MS = 520;
	private static final int STAGGER_MS = 140;

	private static final class Snapshot
	{
		final String key;
		final String name;
		final TsgHubBingoBoard board;
		final Map<String, JsonObject> tasks;
		final Map<String, JsonObject> progress;
		final Set<String> done;
		final List<TsgHubBingoBoard.Line> lines;
		final int points;

		Snapshot(String key, String name, TsgHubBingoBoard board, Map<String, JsonObject> tasks, Map<String, JsonObject> progress, Set<String> done, List<TsgHubBingoBoard.Line> lines, int points)
		{
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
	}

	void setEvent(JsonObject event, String teamId)
	{
		if (event == null || teamId == null || teamId.isEmpty())
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
		Set<String> inLines = TsgHubBoardGrid.cellsInLines(board, data.lines);
		Font small = FontManager.getRunescapeSmallFont();
		int top = PAD + HEADER;
		boolean flipping = false;
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
				TsgHubBoardGrid.Tile tile = new TsgHubBoardGrid.Tile(task, data.progress.get(id), data.done.contains(id), inLines.contains(row + ":" + col), false, false, icons.icon(task));
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
}
