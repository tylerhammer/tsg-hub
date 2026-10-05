package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.input.MouseListener;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubBoardOverlay extends Overlay implements MouseListener
{
	private static final Color GOLD = new Color(232, 183, 91);
	private static final Color MUTED = new Color(190, 198, 207);
	private static final Color CARD = new Color(38, 43, 51, 248);
	private volatile JsonObject event;
	private volatile String memberTeamId = "";
	private volatile boolean visible;
	private volatile java.awt.Rectangle closeButton = new java.awt.Rectangle();
	private final Client client;
	private JsonObject boardSource;
	private TsgHubBingoBoard cachedBoard;

	TsgHubBoardOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(Overlay.PRIORITY_HIGHEST);
	}

	void setEvent(JsonObject event, String memberTeamId)
	{
		this.event = event;
		this.memberTeamId = memberTeamId == null ? "" : memberTeamId;
	}

	void setVisible(boolean visible)
	{
		this.visible = visible;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		JsonObject snapshot = event;
		if (!visible || snapshot == null) return null;
		int canvasWidth = client.getCanvasWidth();
		int canvasHeight = client.getCanvasHeight();
		int width = Math.min(760, canvasWidth - 36);
		int height = Math.min(620, canvasHeight - 36);
		if (width < 300 || height < 260) return null;
		int x = (canvasWidth - width) / 2;
		int y = (canvasHeight - height) / 2;
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0, 0, 0, 155));
		g.fillRect(0, 0, canvasWidth, canvasHeight);
		g.setColor(CARD);
		g.fillRoundRect(x, y, width, height, 18, 18);
		g.setColor(new Color(89, 99, 112));
		g.drawRoundRect(x, y, width, height, 18, 18);

		g.setColor(GOLD);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
		g.drawString(TsgHubUi.str(snapshot, "name"), x + 26, y + 42);
		g.setColor(MUTED);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		String clan = TsgHubUi.str(snapshot, "clanName");
		String dates = TsgHubUi.eventSchedule(snapshot);
		g.drawString((clan.isEmpty() ? "Clan" : clan) + "  ·  " + dates + "  ·  " + TsgHubUi.str(snapshot, "status").toUpperCase(), x + 26, y + 67);
		closeButton = new java.awt.Rectangle(x + width - 43, y + 14, 28, 28);
		g.setColor(new Color(59, 65, 73));
		g.fillRoundRect(closeButton.x, closeButton.y, closeButton.width, closeButton.height, 8, 8);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
		g.drawString("×", closeButton.x + 8, closeButton.y + 20);

		int contentY = y + 102;
		g.setColor(GOLD);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		g.drawString("TEAM SCOREBOARD", x + 26, contentY);
		JsonArray teams = TsgHubUi.array(snapshot, "teams");
		JsonArray scores = TsgHubUi.array(snapshot, "teamScores");
		JsonArray tasks = TsgHubUi.array(snapshot, "tasks");
		List<JsonObject> sorted = new ArrayList<>();
		for (int i = 0; i < teams.size(); i++) sorted.add(teams.get(i).getAsJsonObject());
		sorted.sort(Comparator.comparingInt((JsonObject team) -> TsgHubUi.integer(TsgHubUi.scoreFor(scores, TsgHubUi.str(team, "id")), "points", 0)).reversed());
		int rowY = contentY + 30;
		for (int i = 0; i < sorted.size(); i++)
		{
			JsonObject team = sorted.get(i);
			JsonObject score = TsgHubUi.scoreFor(scores, TsgHubUi.str(team, "id"));
			g.setColor(i == 0 ? new Color(68, 59, 36) : new Color(48, 54, 63));
			g.fillRoundRect(x + 22, rowY - 19, width - 44, 34, 8, 8);
			g.setColor(i == 0 ? GOLD : Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
			g.drawString((i + 1) + ".  " + TsgHubUi.str(team, "name"), x + 36, rowY + 3);
			g.setColor(MUTED);
			String scoreText = score.has("points")
				? TsgHubUi.integer(score, "points", 0) + " pts     " + TsgHubUi.integer(score, "completedTasks", 0) + "/" + tasks.size() + " tasks"
					+ (TsgHubUi.integer(score, "lines", 0) > 0 ? "     " + TsgHubUi.integer(score, "lines", 0) + (TsgHubUi.integer(score, "lines", 0) == 1 ? " line" : " lines") : "")
				: "Hidden";
			int scoreWidth = g.getFontMetrics().stringWidth(scoreText);
			g.drawString(scoreText, x + width - scoreWidth - 38, rowY + 3);
			rowY += 42;
		}
		JsonArray ownTasks = TsgHubUi.array(TsgHubUi.scoreFor(scores, memberTeamId), "tasks");
		TsgHubBingoBoard board = board(snapshot);
		g.setColor(GOLD);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		if (!board.auto && tasks.size() > 0)
		{
			g.drawString("YOUR TEAM BOARD", x + 26, rowY + 13);
			drawBoard(g, board, tasks, ownTasks, x + 26, rowY + 26, width - 52, y + height - rowY - 46);
			g.dispose();
			return null;
		}
		g.drawString("YOUR TEAM TASKS", x + 26, rowY + 13);
		int availableRows = Math.max(0, Math.min(tasks.size(), (y + height - rowY - 54) / 28));
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		for (int i = 0; i < availableRows; i++)
		{
			JsonObject task = tasks.get(i).getAsJsonObject();
			JsonObject progress = TsgHubUi.progressFor(ownTasks, TsgHubUi.str(task, "id"));
			boolean completed = TsgHubUi.bool(progress, "completed");
			String completedBy = TsgHubUi.str(progress, "completedBy");
			String line = TsgHubUi.str(task, "title") + "  ·  "
				+ (completed ? "by " + (completedBy.isEmpty() ? "team" : completedBy)
				: TsgHubUi.bool(progress, "pending") ? "IN REVIEW"
				: TsgHubUi.integer(progress, "progress", 0) + "/" + TsgHubUi.integer(progress, "target", 1));
			g.setColor(completed ? new Color(139, 220, 165) : MUTED);
			g.drawString(line, x + 29, rowY + 42 + i * 26);
		}
		g.dispose();
		return null;
	}

	private TsgHubBingoBoard board(JsonObject snapshot)
	{
		if (snapshot != boardSource)
		{
			cachedBoard = TsgHubBingoBoard.of(snapshot);
			boardSource = snapshot;
		}
		return cachedBoard;
	}

	private static void drawBoard(Graphics2D g, TsgHubBingoBoard board, JsonArray tasks, JsonArray progressRows, int left, int top, int width, int height)
	{
		int n = board.size;
		int gap = 4;
		int cell = Math.min((width - gap * (n - 1)) / n, (height - gap * (n - 1)) / n);
		if (cell < 14) return;
		int x0 = left + (width - (cell * n + gap * (n - 1))) / 2;
		Map<String, JsonObject> byId = new HashMap<>();
		for (int i = 0; i < tasks.size(); i++) byId.put(TsgHubUi.str(tasks.get(i).getAsJsonObject(), "id"), tasks.get(i).getAsJsonObject());
		Set<String> done = new HashSet<>();
		for (int i = 0; i < progressRows.size(); i++)
		{
			JsonObject row = progressRows.get(i).getAsJsonObject();
			if (TsgHubUi.bool(row, "completed")) done.add(TsgHubUi.str(row, "taskId"));
		}
		List<TsgHubBingoBoard.Line> lines = board.completedLines(done);
		Set<String> inLines = TsgHubBoardGrid.cellsInLines(board, lines);
		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, cell >= 80 ? 13 : 11);
		for (int row = 0; row < n; row++)
		{
			for (int col = 0; col < n; col++)
			{
				int cx = x0 + col * (cell + gap);
				int cy = top + row * (cell + gap);
				String id = board.taskAt(row, col);
				if (id == null)
				{
					TsgHubBoardGrid.paintEmpty(g, cx, cy, cell, false, false);
					continue;
				}
				TsgHubBoardGrid.Tile tile = new TsgHubBoardGrid.Tile(byId.get(id), TsgHubUi.progressFor(progressRows, id), done.contains(id), inLines.contains(row + ":" + col), false, false, null);
				TsgHubBoardGrid.paintTile(g, cx, cy, cell, tile, font, true);
			}
		}
		for (TsgHubBingoBoard.Line line : lines) TsgHubBoardGrid.paintLine(g, x0, top, cell, gap, n, line, 1);
	}

	@Override public MouseEvent mousePressed(MouseEvent event)
	{
		if (visible && closeButton.contains(event.getPoint()))
		{
			visible = false;
			event.consume();
		}
		return event;
	}
	@Override public MouseEvent mouseClicked(MouseEvent event) { return event; }
	@Override public MouseEvent mouseReleased(MouseEvent event) { return event; }
	@Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
	@Override public MouseEvent mouseExited(MouseEvent event) { return event; }
	@Override public MouseEvent mouseDragged(MouseEvent event) { return event; }
	@Override public MouseEvent mouseMoved(MouseEvent event) { return event; }
}
