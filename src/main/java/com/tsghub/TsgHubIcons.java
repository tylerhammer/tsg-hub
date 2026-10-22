package com.tsghub;

import static com.tsghub.TsgHubTheme.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.Timer;
import net.runelite.client.util.ImageUtil;

final class TsgHubIcons
{
	private TsgHubIcons()
	{
	}

	static Glyph icon(String name, Color color)
	{
		return icon(name, color, 0);
	}

	static Glyph icon(String name, Color color, int height)
	{
		BufferedImage mask = mask(name);
		return new Glyph(ImageUtil.fillImage(mask, color), null, null, height, mask);
	}

	static Glyph hover(String name)
	{
		return hover(name, ACCENT);
	}

	static Glyph hover(String name, Color hover)
	{
		BufferedImage mask = mask(name);
		return new Glyph(ImageUtil.fillImage(mask, MUTED), ImageUtil.fillImage(mask, hover), ImageUtil.fillImage(mask, BORDER), 0, mask);
	}

	private static BufferedImage mask(String name)
	{
		return ImageUtil.loadImageResource(TsgHubIcons.class, "icons/" + name + ".png");
	}

	static final class Glyph implements Icon
	{
		private final BufferedImage normal;
		private final BufferedImage hover;
		private final BufferedImage disabled;
		private final int width;
		private final int height;
		double angle;
		private Timer timer;

		private Glyph(BufferedImage normal, BufferedImage hover, BufferedImage disabled, int height, BufferedImage mask)
		{
			this.normal = normal;
			this.hover = hover;
			this.disabled = disabled;
			this.height = height > 0 ? height : mask.getHeight() / 2;
			this.width = mask.getWidth() * this.height / mask.getHeight();
		}

		@Override public int getIconWidth() { return width; }
		@Override public int getIconHeight() { return height; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			BufferedImage image = normal;
			if (hover != null && component != null && !component.isEnabled()) image = disabled;
			else if (hover != null && component instanceof AbstractButton && ((AbstractButton) component).getModel().isRollover()) image = hover;
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.rotate(angle, width / 2.0, height / 2.0);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.drawImage(image, 0, 0, width, height, null);
			g.dispose();
		}

		void setSpinning(boolean spinning, Component owner)
		{
			if (spinning)
			{
				if (timer == null) timer = new Timer(30, e -> { angle = (angle + Math.PI / 10) % (2 * Math.PI); owner.repaint(); });
				if (!timer.isRunning()) timer.start();
			}
			else if (timer != null)
			{
				timer.stop();
				angle = 0;
				owner.repaint();
			}
		}
	}
}
