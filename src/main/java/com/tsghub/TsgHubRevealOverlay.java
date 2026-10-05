package com.tsghub;

import com.google.gson.JsonObject;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubRevealOverlay extends Overlay implements MouseListener
{
	static final int ENTER_MS = 350;
	static final int FIRST_TILE_MS = 450;
	static final int TILE_MS = 950;
	static final int DROP_MS = 260;
	static final int LIFT_MS = 300;
	static final int SPLAT_MS = 120;
	static final int POP_MS = 280;
	static final int FLASH_MS = 130;
	static final int SPRAY_MS = 300;
	static final int RING_MS = 380;
	static final int FLOAT_MS = 900;
	static final int STRIKE_MS = 420;
	static final int WAVE_STEP_MS = 110;
	static final int WAVE_POP_MS = 300;
	static final int STAMP_MS = 240;
	static final int STAMP_HOLD_MS = 1600;
	static final int HOLD_MS = 900;
	static final int EXIT_MS = 350;
	private static final int MAX_QUEUED = 6;
	private static final long MAX_HELD_MS = 10 * 60 * 1000L;
	private static final int GAP = 4;
	private static final int PAD = 14;
	private static final int HEADER = 28;
	private static final int FOOTER = 34;
	private static final Set<WorldType> PVP_WORLDS = EnumSet.of(WorldType.DEADMAN, WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING);
	private static final Color INK = new Color(200, 28, 36);
	private static final Color STAMP = new Color(214, 44, 58);

	private static final class Held
	{
		final TsgHubReveals.Moment moment;
		final long at;

		Held(TsgHubReveals.Moment moment, long at)
		{
			this.moment = moment;
			this.at = at;
		}
	}

	private final Client client;
	private final TsgHubTileIcons icons;
	private final Queue<Held> queue = new ConcurrentLinkedQueue<>();
	private volatile TsgHubReveals.Moment current;
	private volatile long startedAt;
	private volatile long pausedAt;
	private volatile Rectangle panelBounds = new Rectangle();

	TsgHubRevealOverlay(Client client, TsgHubTileIcons icons)
	{
		this.client = client;
		this.icons = icons;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_HIGHEST);
	}

	void reveal(TsgHubReveals.Moment moment)
	{
		if (moment != null && queue.size() < MAX_QUEUED) queue.add(new Held(moment, System.currentTimeMillis()));
	}

	void clear()
	{
		queue.clear();
		current = null;
	}

	static int strikesAt(TsgHubReveals.Moment moment)
	{
		return FIRST_TILE_MS + moment.tiles.size() * TILE_MS;
	}

	static int lineMs(TsgHubReveals.Moment moment)
	{
		return (moment.board.size - 1) * WAVE_STEP_MS + WAVE_POP_MS / 2 + STRIKE_MS;
	}

	static int lineAt(TsgHubReveals.Moment moment, int line)
	{
		return strikesAt(moment) + line * lineMs(moment);
	}

	static int strikeAt(TsgHubReveals.Moment moment, int line)
	{
		return lineAt(moment, line) + (moment.board.size - 1) * WAVE_STEP_MS + WAVE_POP_MS / 2;
	}

	static int stampAt(TsgHubReveals.Moment moment)
	{
		return strikesAt(moment) + moment.lines.size() * lineMs(moment);
	}

	static int exitAt(TsgHubReveals.Moment moment)
	{
		return moment.lines.isEmpty() ? strikesAt(moment) + HOLD_MS : stampAt(moment) + STAMP_HOLD_MS;
	}

	static int duration(TsgHubReveals.Moment moment)
	{
		return exitAt(moment) + EXIT_MS;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (current == null && queue.isEmpty()) return null;
		long now = System.currentTimeMillis();
		if (paused(now)) return null;
		if (pausedAt != 0)
		{
			startedAt += now - pausedAt;
			pausedAt = 0;
		}
		TsgHubReveals.Moment moment = current;
		if (moment == null || now - startedAt >= duration(moment))
		{
			Held next = queue.poll();
			while (next != null && now - next.at > MAX_HELD_MS) next = queue.poll();
			moment = next == null ? null : next.moment;
			current = moment;
			startedAt = now;
			if (moment == null) return null;
		}
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		if (width < 200 || height < 200) return null;
		Graphics2D g = (Graphics2D) graphics.create();
		paint(g, moment, now - startedAt, width, height);
		g.dispose();
		return null;
	}

	private boolean paused(long now)
	{
		GameState state = client.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			if (pausedAt == 0) pausedAt = now;
			return true;
		}
		if (state == GameState.LOGGED_IN && !dangerous()) return false;
		TsgHubReveals.Moment playing = current;
		if (playing != null) queue.add(new Held(playing, now));
		current = null;
		pausedAt = 0;
		return true;
	}

	private boolean dangerous()
	{
		if (client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1 || client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1) return true;
		for (WorldType type : client.getWorldType()) if (PVP_WORLDS.contains(type)) return true;
		return false;
	}

	void paint(Graphics2D g, TsgHubReveals.Moment moment, long t, int width, int height)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int n = moment.board.size;
		int side = (int) Math.min(Math.min(height * 0.6, width * 0.5), n * 72 + GAP * (n - 1));
		int cell = Math.max(26, (side - GAP * (n - 1)) / n);
		int gridW = cell * n + GAP * (n - 1);
		int panelW = gridW + PAD * 2;
		int panelH = gridW + PAD * 2 + HEADER + FOOTER;

		int exitAt = exitAt(moment);
		double enter = Math.min(1, t / (double) ENTER_MS);
		double exit = t < exitAt ? 0 : Math.min(1, (t - exitAt) / (double) EXIT_MS);
		g.setColor(new Color(0, 0, 0, (int) (110 * Math.min(1, enter * 1.5) * (1 - exit))));
		g.fillRect(0, 0, width, height);

		double scale = (0.85 + 0.15 * easeOutBack(enter)) * (1 - 0.08 * exit);
		double cx = width / 2.0 + shake(moment, t) * cell / 20.0;
		double cy = height / 2.0;
		panelBounds = new Rectangle((int) (cx - panelW * scale / 2), (int) (cy - panelH * scale / 2), (int) (panelW * scale), (int) (panelH * scale));

		Graphics2D p = (Graphics2D) g.create();
		p.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, enter * 2) * (1 - exit))));
		p.translate(cx, cy);
		p.scale(scale, scale);
		p.translate(-panelW / 2.0, -panelH / 2.0);
		paintPanel(p, moment, t, panelW, panelH, cell, gridW);
		p.dispose();
	}

	private void paintPanel(Graphics2D g, TsgHubReveals.Moment moment, long t, int panelW, int panelH, int cell, int gridW)
	{
		TsgHubBingoBoard board = moment.board;
		int n = board.size;
		g.setColor(new Color(24, 24, 24, 240));
		g.fill(new RoundRectangle2D.Double(0, 0, panelW, panelH, 16, 16));
		g.setStroke(new BasicStroke(2f));
		g.setColor(TsgHubBoardGrid.GOLD);
		g.draw(new RoundRectangle2D.Double(1, 1, panelW - 2, panelH - 2, 16, 16));

		Font title = FontManager.getRunescapeBoldFont().deriveFont(18f);
		centered(g, fit(g, moment.eventName, title, gridW), title, TsgHubBoardGrid.GOLD, panelW / 2.0, PAD + 16);

		int left = PAD;
		int top = PAD + HEADER;
		Font small = FontManager.getRunescapeSmallFont();
		boolean names = cell >= 56;
		for (int row = 0; row < n; row++)
		{
			for (int col = 0; col < n; col++)
			{
				int x = left + col * (cell + GAP);
				int y = top + row * (cell + GAP);
				String id = board.taskAt(row, col);
				if (id == null)
				{
					TsgHubBoardGrid.paintEmpty(g, x, y, cell, false, false);
					continue;
				}
				JsonObject task = moment.tasks.get(id);
				int index = moment.tiles.indexOf(id);
				long since = index >= 0 && t >= FIRST_TILE_MS + (long) index * TILE_MS + DROP_MS ? t - (FIRST_TILE_MS + (long) index * TILE_MS + DROP_MS) : -1;
				boolean daubed = moment.before.contains(id) || since >= 0;
				TsgHubBoardGrid.Tile tile = new TsgHubBoardGrid.Tile(task, daubed ? null : moment.progress.get(id), false, false, false, false, icons == null ? null : icons.icon(task));
				double cx = x + cell / 2.0;
				double cy = y + cell / 2.0;
				long wave = wave(moment, row, col, t);
				double pop = 1;
				if (since >= 0 && since < POP_MS) pop *= 1 + 0.14 * Math.sin(Math.PI * since / (double) POP_MS);
				if (wave >= 0 && wave < WAVE_POP_MS) pop *= 1 + 0.16 * Math.sin(Math.PI * wave / (double) WAVE_POP_MS);
				Graphics2D s = (Graphics2D) g.create();
				if (pop != 1)
				{
					s.translate(cx, cy);
					s.scale(pop, pop);
					s.translate(-cx, -cy);
				}
				TsgHubBoardGrid.paintTile(s, x, y, cell, tile, small, names);
				if (daubed) paintDot(s, cx, cy, cell, since < 0 ? 1 : Math.min(1, since / (double) SPLAT_MS), id.hashCode());
				if (since >= 0 && since < FLASH_MS)
				{
					s.setColor(new Color(255, 255, 255, (int) (190 * (1 - since / (double) FLASH_MS))));
					s.fill(new RoundRectangle2D.Double(x, y, cell, cell, 6, 6));
				}
				if (wave >= 0) paintCelebrated(s, x, y, cell, wave);
				s.dispose();
			}
		}

		for (int i = 0; i < moment.tiles.size(); i++)
		{
			long since = t - (FIRST_TILE_MS + (long) i * TILE_MS + DROP_MS);
			int[] at = find(board, moment.tiles.get(i));
			if (since < 0) continue;
			if (at == null)
			{
				if (since < FLOAT_MS) paintFloat(g, panelW / 2.0, panelH - PAD - 6, cell, since / (double) FLOAT_MS, TsgHubUi.integer(moment.tasks.get(moment.tiles.get(i)), "points", 1));
				continue;
			}
			double cx = left + at[1] * (cell + GAP) + cell / 2.0;
			double cy = top + at[0] * (cell + GAP) + cell / 2.0;
			paintSpray(g, cx, cy, cell, since, moment.tiles.get(i).hashCode());
			if (since < RING_MS) paintRing(g, cx, cy, cell, since / (double) RING_MS);
			if (since < FLOAT_MS) paintFloat(g, cx, cy, cell, since / (double) FLOAT_MS, TsgHubUi.integer(moment.tasks.get(moment.tiles.get(i)), "points", 1));
		}

		for (int i = 0; i < moment.lines.size(); i++)
		{
			long start = strikeAt(moment, i);
			if (t < start) break;
			paintStrike(g, left, top, cell, n, moment.lines.get(i), Math.min(1, (t - start) / (double) STRIKE_MS));
		}

		for (int i = 0; i < moment.tiles.size(); i++)
		{
			long start = FIRST_TILE_MS + (long) i * TILE_MS;
			if (t < start || t > start + DROP_MS + LIFT_MS) continue;
			int[] at = find(board, moment.tiles.get(i));
			if (at == null) continue;
			paintDauber(g, left + at[1] * (cell + GAP) + cell / 2.0, top + at[0] * (cell + GAP) + cell / 2.0, cell, t - start);
		}

		paintCaption(g, moment, t, panelW, panelH);
		int stampAt = stampAt(moment);
		if (!moment.lines.isEmpty() && t >= stampAt) paintStamp(g, moment, Math.min(1, (t - stampAt) / (double) STAMP_MS), panelW / 2.0, top + gridW / 2.0, cell);
	}

	private static long wave(TsgHubReveals.Moment moment, int row, int col, long t)
	{
		int n = moment.board.size;
		long best = -1;
		for (int i = 0; i < moment.lines.size(); i++)
		{
			TsgHubBingoBoard.Line line = moment.lines.get(i);
			for (int k = 0; k < n; k++)
			{
				int[] at = line.cell(k, n);
				if (at[0] != row || at[1] != col) continue;
				long since = t - (lineAt(moment, i) + (long) k * WAVE_STEP_MS);
				if (since >= 0 && (best < 0 || since < best)) best = since;
			}
		}
		return best;
	}

	private static void paintCelebrated(Graphics2D g, int x, int y, int cell, long wave)
	{
		Color gold = TsgHubBoardGrid.GOLD;
		if (wave < WAVE_POP_MS)
		{
			double p = wave / (double) WAVE_POP_MS;
			double glow = Math.sin(Math.PI * p);
			for (int i = 3; i >= 1; i--)
			{
				g.setColor(new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), (int) (60 * glow / i)));
				g.setStroke(new BasicStroke(i * 3f));
				g.draw(new RoundRectangle2D.Double(x - i, y - i, cell + i * 2, cell + i * 2, 8, 8));
			}
			g.setColor(new Color(255, 245, 210, (int) (110 * glow)));
			g.fill(new RoundRectangle2D.Double(x, y, cell, cell, 6, 6));
			g.setColor(new Color(255, 240, 200, (int) (230 * (1 - p))));
			double spread = cell * (0.5 + 0.35 * p);
			double size = cell * 0.05 * (1 - p * 0.5);
			for (int i = 0; i < 4; i++)
			{
				double a = Math.PI / 4 + i * Math.PI / 2;
				g.fill(spark(x + cell / 2.0 + Math.cos(a) * spread, y + cell / 2.0 + Math.sin(a) * spread, size));
			}
		}
		g.setColor(gold);
		g.setStroke(new BasicStroke(2.5f));
		g.draw(new RoundRectangle2D.Double(x + 1, y + 1, cell - 2, cell - 2, 6, 6));
	}

	private static Shape spark(double x, double y, double r)
	{
		Path2D star = new Path2D.Double();
		star.moveTo(x, y - r * 2);
		star.quadTo(x, y, x + r * 2, y);
		star.quadTo(x, y, x, y + r * 2);
		star.quadTo(x, y, x - r * 2, y);
		star.quadTo(x, y, x, y - r * 2);
		return star;
	}

	private static double shake(TsgHubReveals.Moment moment, long t)
	{
		for (int i = 0; i < moment.tiles.size(); i++)
		{
			long impact = FIRST_TILE_MS + (long) i * TILE_MS + DROP_MS;
			if (t >= impact && t < impact + 180) return Math.sin((t - impact) / 11.0) * 5 * (1 - (t - impact) / 180.0);
		}
		long stamp = stampAt(moment) + STAMP_MS / 2;
		if (!moment.lines.isEmpty() && t >= stamp && t < stamp + 200) return Math.sin((t - stamp) / 12.0) * 5 * (1 - (t - stamp) / 200.0);
		return 0;
	}

	private static int[] find(TsgHubBingoBoard board, String id)
	{
		for (int row = 0; row < board.size; row++)
			for (int col = 0; col < board.size; col++)
				if (id.equals(board.taskAt(row, col))) return new int[]{row, col};
		return null;
	}

	private static void paintDauber(Graphics2D g, double tx, double ty, double cell, long t)
	{
		double offset;
		float alpha = 1f;
		if (t < DROP_MS)
		{
			double p = t / (double) DROP_MS;
			offset = (1 - p * p) * cell * 1.7;
			g.setColor(new Color(0, 0, 0, (int) (90 * p)));
			double r = cell * (0.18 + 0.12 * p);
			g.fill(new Ellipse2D.Double(tx - r, ty - r * 0.55, r * 2, r * 1.1));
		}
		else
		{
			double p = Math.min(1, (t - DROP_MS) / (double) LIFT_MS);
			offset = easeOutCubic(p) * cell * 1.2;
			alpha = (float) (1 - p);
		}
		double w = cell * 0.42;
		double h = cell * 0.95;
		double tip = h * 0.12;
		Graphics2D d = (Graphics2D) g.create();
		d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
		d.translate(tx, ty - offset);
		d.rotate(Math.toRadians(-14));
		d.setColor(INK.darker());
		d.fill(new Ellipse2D.Double(-w * 0.42, -tip, w * 0.84, tip * 1.4));
		d.setPaint(new GradientPaint((float) (-w / 2), 0, INK, (float) (w / 2), 0, INK.darker().darker()));
		d.fill(new RoundRectangle2D.Double(-w / 2, -h + tip, w, h - tip * 1.2, w * 0.45, w * 0.45));
		d.setColor(new Color(245, 240, 230));
		d.fill(new Rectangle2D.Double(-w / 2, -h * 0.62, w, h * 0.2));
		d.setColor(INK.darker());
		d.setFont(FontManager.getRunescapeSmallFont().deriveFont((float) Math.max(9, w * 0.32)));
		FontMetrics metrics = d.getFontMetrics();
		d.drawString("TSG", (float) (-metrics.stringWidth("TSG") / 2.0), (float) (-h * 0.52 + metrics.getAscent() / 2.5));
		d.setColor(new Color(40, 40, 40));
		d.fill(new RoundRectangle2D.Double(-w * 0.55, -h * 1.08, w * 1.1, h * 0.3, w * 0.3, w * 0.3));
		d.setColor(new Color(255, 255, 255, 60));
		d.fill(new RoundRectangle2D.Double(-w * 0.32, -h * 0.95, w * 0.14, h * 0.75, w * 0.1, w * 0.1));
		d.dispose();
	}

	private static void paintDot(Graphics2D g, double cx, double cy, double cell, double grow, int seed)
	{
		double r = cell * 0.34 * (0.35 + 0.65 * easeOutBack(grow));
		Area ink = new Area(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		for (int i = 0; i < 9; i++)
		{
			double variance = ((seed >>> (i * 3)) & 7) / 7.0;
			double a = i * Math.PI * 2 / 9 + variance * 0.4;
			double d = r * (0.8 + 0.1 * variance);
			double lobe = r * (0.22 + 0.14 * variance);
			ink.add(new Area(new Ellipse2D.Double(cx + Math.cos(a) * d - lobe, cy + Math.sin(a) * d - lobe, lobe * 2, lobe * 2)));
		}
		g.setColor(new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), 175));
		g.fill(ink);
		g.setColor(new Color(255, 120, 110, 70));
		g.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.62, r * 0.6, r * 0.4));
	}

	private static void paintSpray(Graphics2D g, double cx, double cy, double cell, long since, int seed)
	{
		double p = easeOutCubic(Math.min(1, since / (double) SPRAY_MS));
		g.setColor(new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), 175));
		for (int i = 0; i < 9; i++)
		{
			double variance = ((seed >>> (i * 3 + 1)) & 7) / 7.0;
			double a = i * Math.PI * 2 / 9 + variance;
			double d = cell * (0.42 + 0.22 * variance) * p;
			double size = cell * (0.03 + 0.03 * variance) * (1.4 - 0.4 * p);
			g.fill(new Ellipse2D.Double(cx + Math.cos(a) * d - size, cy + Math.sin(a) * d - size, size * 2, size * 2));
		}
	}

	private static void paintRing(Graphics2D g, double cx, double cy, double cell, double p)
	{
		double r = cell * (0.35 + 0.55 * easeOutCubic(p));
		Color gold = TsgHubBoardGrid.GOLD;
		g.setColor(new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), (int) (220 * (1 - p))));
		g.setStroke(new BasicStroke((float) (1 + 3 * (1 - p))));
		g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
	}

	private static void paintFloat(Graphics2D g, double cx, double cy, double cell, double p, int points)
	{
		Graphics2D f = (Graphics2D) g.create();
		f.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (p < 0.6 ? 1 : 1 - (p - 0.6) / 0.4)));
		double scale = p < 0.15 ? 0.6 + 0.4 * easeOutBack(p / 0.15) : 1;
		f.translate(cx, cy - cell * 0.35 - easeOutCubic(p) * cell * 0.45);
		f.scale(scale, scale);
		centered(f, "+" + points, FontManager.getRunescapeBoldFont().deriveFont((float) Math.max(18, cell * 0.36)), TsgHubBoardGrid.GOLD, 0, 0);
		f.dispose();
	}

	private static void paintStrike(Graphics2D g, int left, int top, int cell, int n, TsgHubBingoBoard.Line line, double p)
	{
		int[] first = line.cell(0, n);
		int[] last = line.cell(n - 1, n);
		double x1 = left + first[1] * (cell + GAP) + cell / 2.0;
		double y1 = top + first[0] * (cell + GAP) + cell / 2.0;
		double x2 = left + last[1] * (cell + GAP) + cell / 2.0;
		double y2 = top + last[0] * (cell + GAP) + cell / 2.0;
		double e = easeOutCubic(p);
		Line2D stroke = new Line2D.Double(x1, y1, x1 + (x2 - x1) * e, y1 + (y2 - y1) * e);
		Color gold = TsgHubBoardGrid.GOLD;
		Graphics2D s = (Graphics2D) g.create();
		s.setColor(new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 70));
		s.setStroke(new BasicStroke(cell * 0.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		s.draw(stroke);
		s.setColor(new Color(gold.getRed(), gold.getGreen(), gold.getBlue(), 230));
		s.setStroke(new BasicStroke(cell * 0.12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		s.draw(stroke);
		s.dispose();
	}

	private static void paintCaption(Graphics2D g, TsgHubReveals.Moment moment, long t, int panelW, int panelH)
	{
		for (int i = moment.tiles.size() - 1; i >= 0; i--)
		{
			if (t < FIRST_TILE_MS + (long) i * TILE_MS + DROP_MS) continue;
			JsonObject task = moment.tasks.get(moment.tiles.get(i));
			int points = TsgHubUi.integer(task, "points", 1);
			String text = TsgHubUi.str(task, "title") + "  +" + points + (points == 1 ? " pt" : " pts");
			Font font = FontManager.getRunescapeBoldFont().deriveFont(16f);
			centered(g, fit(g, text, font, panelW - PAD * 2), font, TsgHubUi.TEXT, panelW / 2.0, panelH - PAD - 6);
			return;
		}
	}

	private static void paintStamp(Graphics2D g, TsgHubReveals.Moment moment, double p, double cx, double cy, int cell)
	{
		double scale = 2.1 - 1.1 * easeOutCubic(p);
		Graphics2D s = (Graphics2D) g.create();
		s.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, p * 1.6)));
		s.translate(cx, cy);
		s.rotate(Math.toRadians(-9));
		s.scale(scale, scale);
		Font big = FontManager.getRunescapeBoldFont().deriveFont((float) Math.max(26, cell * 0.62));
		Font subFont = FontManager.getRunescapeBoldFont().deriveFont((float) Math.max(15, cell * 0.28));
		int bonus = moment.bonus();
		String sub = bonus > 0 ? "+" + bonus + " bonus pts" : moment.lines.size() == 1 ? "Line complete" : moment.lines.size() + " lines";
		FontMetrics bigMetrics = s.getFontMetrics(big);
		FontMetrics subMetrics = s.getFontMetrics(subFont);
		double w = Math.max(bigMetrics.stringWidth("BINGO!"), subMetrics.stringWidth(sub)) + 36;
		double h = bigMetrics.getAscent() + subMetrics.getAscent() + 30;
		s.setColor(new Color(20, 20, 20, 215));
		s.fill(new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 14, 14));
		s.setColor(STAMP);
		s.setStroke(new BasicStroke(4f));
		s.draw(new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 14, 14));
		s.setStroke(new BasicStroke(1.5f));
		s.draw(new RoundRectangle2D.Double(-w / 2 + 6, -h / 2 + 6, w - 12, h - 12, 10, 10));
		double baseline = -h / 2 + 10 + bigMetrics.getAscent();
		centered(s, "BINGO!", big, STAMP, 0, baseline);
		centered(s, sub, subFont, TsgHubBoardGrid.GOLD, 0, baseline + subMetrics.getAscent() + 6);
		s.dispose();
	}

	private static String fit(Graphics2D g, String text, Font font, int width)
	{
		FontMetrics metrics = g.getFontMetrics(font);
		String fitted = text;
		while (metrics.stringWidth(fitted) > width && fitted.length() > 2) fitted = fitted.substring(0, fitted.length() - 2) + "…";
		return fitted;
	}

	private static void centered(Graphics2D g, String text, Font font, Color color, double cx, double baseline)
	{
		g.setFont(font);
		FontMetrics metrics = g.getFontMetrics();
		float x = (float) (cx - metrics.stringWidth(text) / 2.0);
		float y = (float) baseline;
		g.setColor(new Color(0, 0, 0, 200));
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static double easeOutCubic(double p)
	{
		return 1 - Math.pow(1 - p, 3);
	}

	private static double easeOutBack(double p)
	{
		double c = 1.70158;
		return 1 + (c + 1) * Math.pow(p - 1, 3) + c * Math.pow(p - 1, 2);
	}

	@Override public MouseEvent mousePressed(MouseEvent event)
	{
		TsgHubReveals.Moment moment = current;
		if (moment != null && panelBounds.contains(event.getPoint()))
		{
			long now = System.currentTimeMillis();
			if (now - startedAt < exitAt(moment)) startedAt = now - exitAt(moment);
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
