package com.tsghub;

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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.runelite.api.Client;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class TsgHubRevealOverlay extends Overlay implements MouseListener
{
	static final int ENTER_MS = 450;
	static final int CHARGE_MS = 1050;
	static final int FLIP_MS = 1450;
	static final int BURST_MS = 2300;
	static final int EXIT_MS = 3900;
	static final int END_MS = 4350;
	private static final int MAX_QUEUED = 10;
	private static final int CARD_W = 200;
	private static final int CARD_H = 280;
	private static final Color GOLD = new Color(232, 183, 91);
	private static final Color GOLD_LIGHT = new Color(255, 226, 150);
	private static final Color INK = new Color(18, 14, 10);

	private final Client client;
	private final TsgHubTileIcons icons;
	private final Queue<TsgHubReveals.Reveal> queue = new ConcurrentLinkedQueue<>();
	private volatile TsgHubReveals.Reveal current;
	private volatile long startedAt;
	private volatile Rectangle cardBounds = new Rectangle();

	TsgHubRevealOverlay(Client client, TsgHubTileIcons icons)
	{
		this.client = client;
		this.icons = icons;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_HIGHEST);
	}

	void reveal(List<TsgHubReveals.Reveal> reveals)
	{
		for (TsgHubReveals.Reveal reveal : reveals) if (queue.size() < MAX_QUEUED) queue.add(reveal);
	}

	void clear()
	{
		queue.clear();
		current = null;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		long now = System.currentTimeMillis();
		TsgHubReveals.Reveal reveal = current;
		if (reveal == null || now - startedAt >= END_MS)
		{
			reveal = queue.poll();
			current = reveal;
			startedAt = now;
			if (reveal == null) return null;
		}
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		if (width < 200 || height < 200) return null;
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		paint(g, reveal, now - startedAt, width, height, reveal.task == null ? null : icons.icon(reveal.task));
		g.dispose();
		return null;
	}

	void paint(Graphics2D g, TsgHubReveals.Reveal reveal, long t, int width, int height, BufferedImage icon)
	{
		double scale = Math.max(0.7, Math.min(1.25, height / 560.0));
		double cx = width / 2.0;
		double cy = height / 2.0;
		double exit = t < EXIT_MS ? 0 : Math.min(1, (t - EXIT_MS) / (double) (END_MS - EXIT_MS));
		double enter = Math.min(1, t / (double) ENTER_MS);
		g.setColor(new Color(0, 0, 0, (int) (130 * Math.min(enter * 1.5, 1) * (1 - exit))));
		g.fillRect(0, 0, width, height);

		if (t >= FLIP_MS) paintRays(g, cx, cy, scale, t, exit, reveal.line);

		double cardScale = scale * (0.6 + 0.4 * easeOutBack(enter));
		double rise = (1 - easeOutCubic(enter)) * 140 * scale - easeInCubic(exit) * 46 * scale;
		double rotation = 0;
		if (t >= ENTER_MS && t < CHARGE_MS)
		{
			double p = (t - ENTER_MS) / (double) (CHARGE_MS - ENTER_MS);
			rotation = Math.toRadians(Math.sin(t / 26.0) * 4 * p);
			cardScale *= 1 + 0.03 * p;
		}
		double flip = 1;
		boolean front = t >= FLIP_MS;
		if (t >= CHARGE_MS && t < FLIP_MS)
		{
			double p = (t - CHARGE_MS) / (double) (FLIP_MS - CHARGE_MS);
			flip = Math.max(0.03, Math.abs(Math.cos(Math.PI * p)));
			front = p >= 0.5;
			cardScale *= 1.03 + 0.07 * Math.sin(Math.PI * p);
		}
		else if (t >= FLIP_MS && t < BURST_MS)
		{
			double p = (t - FLIP_MS) / (double) (BURST_MS - FLIP_MS);
			cardScale *= 1 + 0.1 * (1 - easeOutCubic(p));
		}

		float alpha = (float) (Math.min(1, enter * 2) * (1 - exit));
		int w = (int) Math.round(CARD_W * cardScale);
		int h = (int) Math.round(CARD_H * cardScale);
		cardBounds = new Rectangle((int) (cx - w / 2.0), (int) (cy - h / 2.0 + rise), w, h);

		if (t >= ENTER_MS && t < FLIP_MS) paintCharge(g, cx, cy + rise, w, h, t);

		Graphics2D card = (Graphics2D) g.create();
		card.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
		card.translate(cx, cy + rise);
		card.rotate(rotation);
		card.scale(flip * cardScale, cardScale);
		card.translate(-CARD_W / 2.0, -CARD_H / 2.0);
		if (front) paintFront(card, reveal, t, icon);
		else paintBack(card);
		card.dispose();

		if (t >= FLIP_MS && t < BURST_MS + 400) paintSparkles(g, cx, cy + rise, scale, (t - FLIP_MS) / (double) (BURST_MS + 400 - FLIP_MS), reveal.line);
	}

	private void paintRays(Graphics2D g, double cx, double cy, double scale, long t, double exit, boolean line)
	{
		double burst = Math.min(1, (t - FLIP_MS) / (double) (BURST_MS - FLIP_MS));
		double strength = (1 - burst) * 0.6 + 0.22;
		Graphics2D rays = (Graphics2D) g.create();
		rays.translate(cx, cy);
		rays.rotate(t / 2400.0);
		double length = 330 * scale * (0.6 + 0.4 * easeOutCubic(burst));
		Color base = line ? new Color(255, 170, 70) : GOLD_LIGHT;
		int count = 14;
		for (int i = 0; i < count; i++)
		{
			double a = i * Math.PI * 2 / count;
			Path2D ray = new Path2D.Double();
			ray.moveTo(0, 0);
			ray.lineTo(Math.cos(a - 0.09) * length, Math.sin(a - 0.09) * length);
			ray.lineTo(Math.cos(a + 0.09) * length, Math.sin(a + 0.09) * length);
			ray.closePath();
			rays.setPaint(new java.awt.RadialGradientPaint(0f, 0f, (float) length, new float[]{0f, 1f},
				new Color[]{alpha(base, (int) (150 * strength * (1 - exit))), alpha(base, 0)}));
			rays.fill(ray);
		}
		rays.dispose();
	}

	private void paintCharge(Graphics2D g, double cx, double cy, int w, int h, long t)
	{
		double p = Math.min(1, (t - ENTER_MS) / (double) (FLIP_MS - ENTER_MS));
		double pulse = 0.5 + 0.5 * Math.sin(t / 70.0);
		int glow = (int) (12 + 22 * p + 6 * pulse);
		Graphics2D halo = (Graphics2D) g.create();
		for (int i = glow; i > 0; i -= 3)
		{
			halo.setColor(alpha(GOLD_LIGHT, (int) (10 * p + 4)));
			halo.fill(new RoundRectangle2D.Double(cx - w / 2.0 - i, cy - h / 2.0 - i, w + i * 2, h + i * 2, 24 + i, 24 + i));
		}
		halo.dispose();
	}

	private void paintSparkles(Graphics2D g, double cx, double cy, double scale, double p, boolean line)
	{
		Graphics2D sparks = (Graphics2D) g.create();
		int count = 30;
		for (int i = 0; i < count; i++)
		{
			double variance = ((i * 37) % 11) / 11.0;
			double a = i * Math.PI * 2 / count + variance * 0.4;
			double distance = (110 + 120 * variance) * scale * easeOutCubic(p) + 40 * scale;
			double size = (2 + 3 * ((i * 13) % 5) / 4.0) * scale * (1 - p * 0.6);
			double x = cx + Math.cos(a) * distance;
			double y = cy + Math.sin(a) * distance * 0.85;
			Color color = i % 3 == 0 ? Color.WHITE : line ? new Color(255, 170, 70) : GOLD_LIGHT;
			sparks.setColor(alpha(color, (int) (255 * Math.max(0, 1 - p))));
			sparks.fill(star(x, y, size));
		}
		sparks.dispose();
	}

	private static Shape star(double x, double y, double r)
	{
		Path2D star = new Path2D.Double();
		star.moveTo(x, y - r * 2);
		star.quadTo(x, y, x + r * 2, y);
		star.quadTo(x, y, x, y + r * 2);
		star.quadTo(x, y, x - r * 2, y);
		star.quadTo(x, y, x, y - r * 2);
		return star;
	}

	private static void paintBack(Graphics2D g)
	{
		Shape outline = new RoundRectangle2D.Double(0, 0, CARD_W, CARD_H, 18, 18);
		g.setPaint(new GradientPaint(0, 0, new Color(58, 42, 26), 0, CARD_H, new Color(22, 16, 11)));
		g.fill(outline);
		Graphics2D lattice = (Graphics2D) g.create();
		lattice.clip(new RoundRectangle2D.Double(10, 10, CARD_W - 20, CARD_H - 20, 12, 12));
		lattice.setColor(alpha(GOLD, 38));
		lattice.setStroke(new BasicStroke(1.2f));
		for (int i = -CARD_H; i < CARD_W + CARD_H; i += 16)
		{
			lattice.drawLine(i, 0, i + CARD_H, CARD_H);
			lattice.drawLine(i, CARD_H, i + CARD_H, 0);
		}
		lattice.dispose();
		g.setStroke(new BasicStroke(3f));
		g.setColor(GOLD);
		g.draw(new RoundRectangle2D.Double(1.5, 1.5, CARD_W - 3, CARD_H - 3, 18, 18));
		g.setStroke(new BasicStroke(1f));
		g.setColor(alpha(GOLD, 150));
		g.draw(new RoundRectangle2D.Double(9, 9, CARD_W - 18, CARD_H - 18, 12, 12));

		double r = 46;
		double cx = CARD_W / 2.0;
		double cy = CARD_H / 2.0;
		g.setColor(INK);
		g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		g.setStroke(new BasicStroke(3f));
		g.setColor(GOLD);
		g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		g.setStroke(new BasicStroke(1f));
		g.draw(new Ellipse2D.Double(cx - r + 6, cy - r + 6, r * 2 - 12, r * 2 - 12));
		Font big = FontManager.getRunescapeBoldFont().deriveFont(28f);
		centered(g, "TSG", big, GOLD_LIGHT, cx, cy + 9);
		centered(g, "BINGO", FontManager.getRunescapeSmallFont().deriveFont(16f), GOLD, cx, cy + r + 26);
	}

	private void paintFront(Graphics2D g, TsgHubReveals.Reveal reveal, long t, BufferedImage icon)
	{
		Color frame = reveal.line ? new Color(255, 160, 60) : GOLD;
		Shape outline = new RoundRectangle2D.Double(0, 0, CARD_W, CARD_H, 18, 18);
		g.setPaint(reveal.line
			? new GradientPaint(0, 0, new Color(92, 44, 18), 0, CARD_H, new Color(34, 16, 8))
			: new GradientPaint(0, 0, new Color(30, 72, 48), 0, CARD_H, new Color(12, 30, 20)));
		g.fill(outline);
		g.setStroke(new BasicStroke(3f));
		g.setColor(frame);
		g.draw(new RoundRectangle2D.Double(1.5, 1.5, CARD_W - 3, CARD_H - 3, 18, 18));

		g.setColor(alpha(INK, 200));
		g.fill(new RoundRectangle2D.Double(10, 10, CARD_W - 20, 30, 10, 10));
		centered(g, reveal.line ? "BINGO!" : "TILE COMPLETE", FontManager.getRunescapeBoldFont().deriveFont(18f), frame, CARD_W / 2.0, 31);

		Rectangle art = new Rectangle(16, 48, CARD_W - 32, 112);
		g.setPaint(new java.awt.RadialGradientPaint((float) art.getCenterX(), (float) art.getCenterY(), art.width * 0.7f, new float[]{0f, 1f},
			new Color[]{alpha(frame, 110), alpha(INK, 230)}));
		g.fill(new RoundRectangle2D.Double(art.x, art.y, art.width, art.height, 10, 10));
		g.setColor(alpha(frame, 170));
		g.setStroke(new BasicStroke(1.5f));
		g.draw(new RoundRectangle2D.Double(art.x, art.y, art.width, art.height, 10, 10));
		if (reveal.line) centered(g, reveal.title, FontManager.getRunescapeBoldFont().deriveFont(34f), Color.WHITE, art.getCenterX(), art.getCenterY() + 12);
		else if (icon != null && icon.getWidth() > 0) paintIcon(g, icon, art);
		else paintTick(g, art.getCenterX(), art.getCenterY(), 30);

		Font titleFont = FontManager.getRunescapeBoldFont().deriveFont(17f);
		int textTop = 172;
		if (!reveal.line)
		{
			List<String> lines = TsgHubBoardGrid.wrapLines(g.getFontMetrics(titleFont), reveal.title, CARD_W - 28, 2);
			for (String line : lines)
			{
				centered(g, line, titleFont, Color.WHITE, CARD_W / 2.0, textTop + 14);
				textTop += 19;
			}
		}
		else textTop += 8;

		int points = reveal.points;
		if (reveal.line && t < BURST_MS) points = (int) Math.round(points * Math.max(0, (t - FLIP_MS) / (double) (BURST_MS - FLIP_MS)));
		String badge = reveal.line ? (reveal.points > 0 ? "+" + points + " bonus pts" : "Line complete") : "+" + points + (points == 1 ? " pt" : " pts");
		Font badgeFont = FontManager.getRunescapeBoldFont().deriveFont(16f);
		FontMetrics metrics = g.getFontMetrics(badgeFont);
		int bw = metrics.stringWidth(badge) + 22;
		int by = reveal.line ? 184 : Math.max(textTop + 6, 214);
		g.setColor(alpha(INK, 220));
		g.fill(new RoundRectangle2D.Double((CARD_W - bw) / 2.0, by, bw, 24, 24, 24));
		g.setColor(frame);
		g.draw(new RoundRectangle2D.Double((CARD_W - bw) / 2.0, by, bw, 24, 24, 24));
		centered(g, badge, badgeFont, GOLD_LIGHT, CARD_W / 2.0, by + 18);
		if (!reveal.detail.isEmpty()) centered(g, reveal.detail, FontManager.getRunescapeSmallFont().deriveFont(15f), new Color(220, 220, 220), CARD_W / 2.0, CARD_H - 16);

		if (t >= FLIP_MS && t < FLIP_MS + 650) paintShine(g, (t - FLIP_MS) / 650.0);
	}

	private static void paintIcon(Graphics2D g, BufferedImage icon, Rectangle art)
	{
		int max = Math.min(art.width - 24, art.height - 20);
		int factor = Math.max(1, Math.min(max / icon.getWidth(), max / icon.getHeight()));
		int w = icon.getWidth() * factor;
		int h = icon.getHeight() * factor;
		Graphics2D image = (Graphics2D) g.create();
		image.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		image.drawImage(icon, (int) (art.getCenterX() - w / 2.0), (int) (art.getCenterY() - h / 2.0), w, h, null);
		image.dispose();
	}

	private static void paintTick(Graphics2D g, double cx, double cy, double r)
	{
		g.setColor(TsgHubUi.SUCCESS);
		g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		g.setColor(INK);
		g.setStroke(new BasicStroke((float) (r / 4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Path2D tick = new Path2D.Double();
		tick.moveTo(cx - r * 0.45, cy + r * 0.05);
		tick.lineTo(cx - r * 0.1, cy + r * 0.4);
		tick.lineTo(cx + r * 0.5, cy - r * 0.35);
		g.draw(tick);
	}

	private static void paintShine(Graphics2D g, double p)
	{
		Graphics2D shine = (Graphics2D) g.create();
		shine.clip(new RoundRectangle2D.Double(0, 0, CARD_W, CARD_H, 18, 18));
		double x = -CARD_W + (CARD_W * 3) * p;
		int a = (int) (170 * Math.sin(Math.PI * p));
		shine.setPaint(new GradientPaint((float) x, 0, new Color(255, 245, 210, 0), (float) (x + 60), 60, new Color(255, 245, 210, a), true));
		shine.fillRect(0, 0, CARD_W, CARD_H);
		shine.dispose();
	}

	private static void centered(Graphics2D g, String text, Font font, Color color, double cx, double baseline)
	{
		g.setFont(font);
		FontMetrics metrics = g.getFontMetrics();
		int x = (int) Math.round(cx - metrics.stringWidth(text) / 2.0);
		int y = (int) Math.round(baseline);
		g.setColor(new Color(0, 0, 0, 200));
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	private static Color alpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
	}

	private static double easeOutCubic(double p)
	{
		return 1 - Math.pow(1 - p, 3);
	}

	private static double easeInCubic(double p)
	{
		return p * p * p;
	}

	private static double easeOutBack(double p)
	{
		double c = 1.70158;
		return 1 + (c + 1) * Math.pow(p - 1, 3) + c * Math.pow(p - 1, 2);
	}

	@Override public MouseEvent mousePressed(MouseEvent event)
	{
		if (current != null && cardBounds.contains(event.getPoint()))
		{
			long elapsed = System.currentTimeMillis() - startedAt;
			if (elapsed < EXIT_MS) startedAt = System.currentTimeMillis() - EXIT_MS;
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
