package com.tsghub;

import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import com.google.gson.JsonObject;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.geom.RoundRectangle2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.util.ImageUtil;

final class TsgHubNewsGallery extends JComponent
{
	static final int GAP = 4;
	private static final int MAX_SINGLE_H = 360;
	private static final int RADIUS = 8;

	private final List<Tile> tiles = new ArrayList<>();

	private static final class Tile
	{
		final String url;
		final String caption;
		final Rectangle cell;
		BufferedImage image;
		BufferedImage scaled;

		Tile(String url, String caption, Rectangle cell)
		{
			this.url = url;
			this.caption = caption;
			this.cell = cell;
		}
	}

	TsgHubNewsGallery(List<JsonObject> photos, int width, Function<String, CompletableFuture<BufferedImage>> load, Consumer<String> open)
	{
		List<Rectangle> cells = layout(photos.size(), width, integer(photos.get(0), "width", 0), integer(photos.get(0), "height", 0));
		for (int i = 0; i < photos.size(); i++)
		{
			Tile tile = new Tile(str(photos.get(i), "url"), str(photos.get(i), "caption"), cells.get(i));
			tiles.add(tile);
			load.apply(tile.url).thenAccept(image -> SwingUtilities.invokeLater(() -> {
				tile.image = image;
				repaint();
			}));
		}
		Rectangle last = cells.get(cells.size() - 1);
		Dimension size = new Dimension(width, last.y + last.height);
		setPreferredSize(size);
		setMaximumSize(size);
		setAlignmentX(LEFT_ALIGNMENT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("");
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				Tile tile = at(e);
				if (tile != null) open.accept(tile.url);
			}
		});
	}

	static List<Rectangle> layout(int count, int width, int firstW, int firstH)
	{
		List<Rectangle> cells = new ArrayList<>();
		if (count == 1)
		{
			int h = firstW > 0 && firstH > 0 ? Math.min(MAX_SINGLE_H, (int) ((long) firstH * Math.min(width, firstW) / firstW)) : width / 2;
			int w = firstW > 0 && firstH > 0 ? Math.min(width, (int) ((long) firstW * h / firstH)) : width;
			cells.add(new Rectangle(0, 0, w, h));
			return cells;
		}
		List<Integer> rows = new ArrayList<>();
		if (count == 4)
		{
			rows.add(2);
			rows.add(2);
		}
		else
		{
			if (count % 3 != 0) rows.add(count % 3);
			for (int i = 0; i < count / 3; i++) rows.add(3);
		}
		int y = 0;
		for (int perRow : rows)
		{
			int cellW = (width - GAP * (perRow - 1)) / perRow;
			int cellH = perRow == 1 ? width / 2 : perRow == 2 ? cellW * 3 / 4 : cellW;
			for (int i = 0; i < perRow; i++) cells.add(new Rectangle(i * (cellW + GAP), y, i == perRow - 1 ? width - i * (cellW + GAP) : cellW, cellH));
			y += cellH + GAP;
		}
		return cells;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		Tile tile = at(e);
		return tile == null || tile.caption.isEmpty() ? null : tile.caption;
	}

	private Tile at(MouseEvent e)
	{
		for (Tile tile : tiles) if (tile.cell.contains(e.getPoint())) return tile;
		return null;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		double scale = g2.getTransform().getScaleX();
		for (Tile tile : tiles)
		{
			Rectangle c = tile.cell;
			g2.setClip(new RoundRectangle2D.Float(c.x, c.y, c.width, c.height, RADIUS, RADIUS));
			if (tile.image == null)
			{
				g2.setColor(CARD);
				g2.fill(c);
				continue;
			}
			int w = (int) Math.ceil(c.width * scale), h = (int) Math.ceil(c.height * scale);
			if (tile.scaled == null || tile.scaled.getWidth() != w || tile.scaled.getHeight() != h) tile.scaled = cover(tile.image, w, h);
			g2.drawImage(tile.scaled, c.x, c.y, c.width, c.height, null);
		}
		g2.dispose();
	}

	static BufferedImage cover(BufferedImage image, int w, int h)
	{
		int iw = image.getWidth(), ih = image.getHeight();
		int cropW = iw, cropH = ih;
		if ((long) iw * h > (long) ih * w) cropW = (int) Math.max(1, (long) ih * w / h);
		else cropH = (int) Math.max(1, (long) iw * h / w);
		BufferedImage crop = image.getSubimage((iw - cropW) / 2, (ih - cropH) / 2, cropW, cropH);
		return ImageUtil.resizeImage(crop, w, h);
	}
}
