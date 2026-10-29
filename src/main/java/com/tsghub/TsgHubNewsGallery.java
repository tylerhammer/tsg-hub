package com.tsghub;

import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonObject;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.ImageUtil;

final class TsgHubNewsGallery extends JPanel
{
	static final int GAP = 4;
	static final int THUMB_H = 44;
	static final int MAX_VIEW_H = 360;
	private static final int RADIUS = 8;

	private final List<Photo> photos = new ArrayList<>();
	private final int width;
	private final JPanel captionRow = new JPanel(new BorderLayout(GAP_M, 0));
	private int selected;

	static final class Photo
	{
		final String url;
		final String caption;
		final int width;
		final int height;
		BufferedImage image;
		BufferedImage view;
		BufferedImage thumb;

		Photo(JsonObject json)
		{
			url = str(json, "url");
			caption = str(json, "caption");
			width = integer(json, "width", 0);
			height = integer(json, "height", 0);
		}
	}

	TsgHubNewsGallery(List<JsonObject> list, int width, Function<String, CompletableFuture<BufferedImage>> load, Consumer<String> open)
	{
		this.width = width;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		for (JsonObject json : list)
		{
			Photo photo = new Photo(json);
			photos.add(photo);
			load.apply(photo.url).thenAccept(image -> SwingUtilities.invokeLater(() -> {
				photo.image = image;
				repaint();
			}));
		}
		add(new Viewer(viewHeight(photos, width), open));
		captionRow.setOpaque(false);
		captionRow.setAlignmentX(LEFT_ALIGNMENT);
		add(captionRow);
		if (photos.size() > 1)
		{
			add(gap(GAP_S));
			add(new Strip());
		}
		select(0);
	}

	static int viewHeight(List<Photo> photos, int width)
	{
		int h = 0;
		for (Photo p : photos) h = Math.max(h, p.width > 0 && p.height > 0 ? (int) ((long) p.height * width / p.width) : width * 9 / 16);
		return Math.min(MAX_VIEW_H, h);
	}

	static int thumbWidth(int count, int width)
	{
		return Math.min(THUMB_H * 4 / 3, (width - GAP * (count - 1)) / count);
	}

	static Rectangle contain(int imageW, int imageH, int boxW, int boxH)
	{
		double scale = Math.min(1.0 * boxW / imageW, 1.0 * boxH / imageH);
		int w = Math.max(1, (int) Math.round(imageW * scale)), h = Math.max(1, (int) Math.round(imageH * scale));
		return new Rectangle((boxW - w) / 2, (boxH - h) / 2, w, h);
	}

	private void select(int index)
	{
		selected = index;
		Photo photo = photos.get(index);
		captionRow.removeAll();
		String count = photos.size() > 1 ? (index + 1) + " / " + photos.size() : "";
		if (!photo.caption.isEmpty() || !count.isEmpty())
		{
			captionRow.setBorder(BorderFactory.createEmptyBorder(GAP_XS, 0, 0, 0));
			int countW = count.isEmpty() ? 0 : getFontMetrics(smallFont()).stringWidth(count) + GAP_M;
			captionRow.add(wrapped(photo.caption, MUTED, smallFont(), width - countW), BorderLayout.CENTER);
			if (!count.isEmpty()) captionRow.add(label(count, MUTED, smallFont()), BorderLayout.EAST);
		}
		captionRow.setMaximumSize(new Dimension(width, captionRow.getPreferredSize().height));
		revalidate();
		repaint();
	}

	private static Graphics2D smooth(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g2;
	}

	private static JComponent sized(JComponent component, int w, int h)
	{
		Dimension size = new Dimension(w, h);
		component.setPreferredSize(size);
		component.setMaximumSize(size);
		component.setAlignmentX(LEFT_ALIGNMENT);
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return component;
	}

	private final class Viewer extends JComponent
	{
		Viewer(int height, Consumer<String> open)
		{
			sized(this, width, height);
			setToolTipText("Open full size");
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					open.accept(photos.get(selected).url);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = smooth(g);
			RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
			g2.setColor(CARD);
			g2.fill(shape);
			Photo photo = photos.get(selected);
			if (photo.image != null)
			{
				Rectangle at = contain(photo.image.getWidth(), photo.image.getHeight(), getWidth(), getHeight());
				double scale = g2.getTransform().getScaleX();
				int w = (int) Math.ceil(at.width * scale), h = (int) Math.ceil(at.height * scale);
				if (photo.view == null || photo.view.getWidth() != w || photo.view.getHeight() != h) photo.view = ImageUtil.resizeImage(photo.image, w, h);
				g2.setClip(shape);
				g2.drawImage(photo.view, at.x, at.y, at.width, at.height, null);
			}
			g2.dispose();
		}
	}

	private final class Strip extends JComponent
	{
		private final int thumbW = thumbWidth(photos.size(), width);

		Strip()
		{
			sized(this, width, THUMB_H);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					int index = e.getX() / (thumbW + GAP);
					if (index < photos.size() && e.getX() - index * (thumbW + GAP) < thumbW) select(index);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = smooth(g);
			double scale = g2.getTransform().getScaleX();
			for (int i = 0; i < photos.size(); i++)
			{
				Photo photo = photos.get(i);
				int x = i * (thumbW + GAP);
				RoundRectangle2D shape = new RoundRectangle2D.Float(x, 0, thumbW, THUMB_H, RADIUS, RADIUS);
				Graphics2D cell = (Graphics2D) g2.create();
				cell.setColor(CARD);
				cell.fill(shape);
				if (photo.image != null)
				{
					int w = (int) Math.ceil(thumbW * scale), h = (int) Math.ceil(THUMB_H * scale);
					if (photo.thumb == null || photo.thumb.getWidth() != w || photo.thumb.getHeight() != h) photo.thumb = cover(photo.image, w, h);
					if (i != selected) cell.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
					cell.setClip(shape);
					cell.drawImage(photo.thumb, x, 0, thumbW, THUMB_H, null);
				}
				cell.dispose();
				if (i == selected)
				{
					g2.setColor(ACCENT);
					g2.setStroke(new BasicStroke(2f));
					g2.draw(new RoundRectangle2D.Float(x + 1, 1, thumbW - 2, THUMB_H - 2, RADIUS, RADIUS));
				}
			}
			g2.dispose();
		}
	}

	static BufferedImage cover(BufferedImage image, int w, int h)
	{
		int iw = image.getWidth(), ih = image.getHeight();
		int cropW = iw, cropH = ih;
		if ((long) iw * h > (long) ih * w) cropW = (int) Math.max(1, (long) ih * w / h);
		else cropH = (int) Math.max(1, (long) iw * h / w);
		return ImageUtil.resizeImage(image.getSubimage((iw - cropW) / 2, (ih - cropH) / 2, cropW, cropH), w, h);
	}
}
