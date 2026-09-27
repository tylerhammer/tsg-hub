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
import java.util.List;
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
		g.drawString(snapshot.get("name").getAsString(), x + 26, y + 42);
		g.setColor(MUTED);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		String clan = snapshot.has("clanName") && !snapshot.get("clanName").isJsonNull() ? snapshot.get("clanName").getAsString() : "Clan";
		String dates = (snapshot.has("startDate") && !snapshot.get("startDate").isJsonNull() ? snapshot.get("startDate").getAsString() : "")
			+ " to " + (snapshot.has("endDate") && !snapshot.get("endDate").isJsonNull() ? snapshot.get("endDate").getAsString() : "");
		g.drawString(clan + "  ·  " + dates + "  ·  " + snapshot.get("status").getAsString().toUpperCase(), x + 26, y + 67);
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
		JsonArray teams = snapshot.getAsJsonArray("teams");
		JsonArray scores = snapshot.getAsJsonArray("teamScores");
		JsonArray tasks = snapshot.getAsJsonArray("tasks");
		List<JsonObject> sorted = new ArrayList<>();
		for (int i = 0; i < teams.size(); i++) sorted.add(teams.get(i).getAsJsonObject());
		sorted.sort(Comparator.comparingInt((JsonObject team) -> scoreFor(scores, team.get("id").getAsString()).get("points").getAsInt()).reversed());
		int rowY = contentY + 30;
		for (int i = 0; i < sorted.size(); i++)
		{
			JsonObject team = sorted.get(i);
			JsonObject score = scoreFor(scores, team.get("id").getAsString());
			g.setColor(i == 0 ? new Color(68, 59, 36) : new Color(48, 54, 63));
			g.fillRoundRect(x + 22, rowY - 19, width - 44, 34, 8, 8);
			g.setColor(i == 0 ? GOLD : Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
			g.drawString((i + 1) + ".  " + team.get("name").getAsString(), x + 36, rowY + 3);
			g.setColor(MUTED);
			String scoreText = score.get("points").getAsInt() + " pts     " + score.get("completedTasks").getAsInt() + "/" + tasks.size() + " tasks";
			int scoreWidth = g.getFontMetrics().stringWidth(scoreText);
			g.drawString(scoreText, x + width - scoreWidth - 38, rowY + 3);
			rowY += 42;
		}
		g.setColor(GOLD);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		g.drawString("YOUR TEAM TASKS", x + 26, rowY + 13);
		JsonObject ownScore = scoreFor(scores, memberTeamId);
		JsonArray ownTasks = ownScore.has("tasks") ? ownScore.getAsJsonArray("tasks") : new JsonArray();
		int availableRows = Math.max(0, Math.min(tasks.size(), (y + height - rowY - 54) / 28));
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		for (int i = 0; i < availableRows; i++)
		{
			JsonObject task = tasks.get(i).getAsJsonObject();
			JsonObject progress = taskProgress(ownTasks, task.get("id").getAsString());
			String line = task.get("title").getAsString() + "  ·  "
				+ (progress.get("completed").getAsBoolean()
					? "by " + (progress.has("completedBy") && !progress.get("completedBy").isJsonNull() ? progress.get("completedBy").getAsString() : "team")
					: progress.get("pending").getAsBoolean() ? "IN REVIEW"
				: progress.get("progress").getAsInt() + "/" + progress.get("target").getAsInt());
			g.setColor(progress.get("completed").getAsBoolean() ? new Color(139, 220, 165) : MUTED);
			g.drawString(line, x + 29, rowY + 42 + i * 26);
		}
		g.dispose();
		return null;
	}

	private JsonObject scoreFor(JsonArray scores, String teamId)
	{
		for (int i = 0; i < scores.size(); i++)
		{
			JsonObject score = scores.get(i).getAsJsonObject();
			if (score.get("teamId").getAsString().equals(teamId)) return score;
		}
		return new JsonObject();
	}

	private JsonObject taskProgress(JsonArray progress, String taskId)
	{
		for (int i = 0; i < progress.size(); i++)
		{
			JsonObject row = progress.get(i).getAsJsonObject();
			if (row.get("taskId").getAsString().equals(taskId)) return row;
		}
		JsonObject empty = new JsonObject();
		empty.addProperty("points", 0);
		empty.addProperty("completedTasks", 0);
		empty.add("tasks", new JsonArray());
		empty.addProperty("completed", false);
		empty.addProperty("pending", false);
		empty.addProperty("progress", 0);
		empty.addProperty("target", 1);
		return empty;
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
