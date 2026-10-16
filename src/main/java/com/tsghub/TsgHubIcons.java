package com.tsghub;

import static com.tsghub.TsgHubTheme.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.Timer;

final class TsgHubIcons
{
	private TsgHubIcons()
	{
	}

	abstract static class HoverIcon implements Icon
	{
		private final int size;
		HoverIcon(int size) { this.size = size; }
		@Override public int getIconWidth() { return size; }
		@Override public int getIconHeight() { return size; }

		@Override
		public final void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			boolean hovered = component instanceof AbstractButton && ((AbstractButton) component).getModel().isRollover();
			boolean enabled = component == null || component.isEnabled();
			g.setColor(!enabled ? BORDER : hovered ? hoverColor() : MUTED);
			g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			paint(g, component);
			g.dispose();
		}

		abstract void paint(Graphics2D g, Component component);

		Color hoverColor() { return ACCENT; }
	}

	static final class TrashIcon extends HoverIcon
	{
		TrashIcon() { super(16); }
		@Override Color hoverColor() { return ERROR; }
		@Override void paint(Graphics2D g, Component c)
		{
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(2, 4, 14, 4);
			g.drawLine(6, 4, 6, 2);
			g.drawLine(6, 2, 10, 2);
			g.drawLine(10, 2, 10, 4);
			g.drawRect(4, 4, 8, 10);
			g.drawLine(7, 7, 7, 11);
			g.drawLine(9, 7, 9, 11);
		}
	}

	static final class BackIcon extends HoverIcon
	{
		BackIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.drawLine(10, 3, 5, 8);
			g.drawLine(5, 8, 10, 13);
		}
	}

	static final class RefreshIcon extends HoverIcon
	{
		private float angle;
		private Timer timer;

		RefreshIcon() { super(16); }

		void setSpinning(boolean spinning, Component owner)
		{
			if (spinning)
			{
				if (timer == null)
				{
					timer = new Timer(30, e -> { angle = (angle + 18) % 360; owner.repaint(); });
				}
				if (!timer.isRunning()) timer.start();
			}
			else if (timer != null)
			{
				timer.stop();
				angle = 0;
				owner.repaint();
			}
		}

		@Override void paint(Graphics2D g, Component c)
		{
			g.rotate(Math.toRadians(angle), 8, 8);
			g.drawArc(2, 2, 12, 12, 48, 292);
			g.fillPolygon(new int[] {12, 16, 11}, new int[] {1, 5, 6}, 3);
		}
	}

	static final class OrganizerIcon extends HoverIcon
	{
		OrganizerIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.setStroke(new BasicStroke(1.4f));
			g.drawRect(2, 2, 12, 12);
			g.drawLine(2, 6, 14, 6);
			g.drawLine(6, 6, 6, 14);
		}
	}

	static final class PinIcon extends HoverIcon
	{
		PinIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(5, 2, 11, 2);
			g.drawLine(6, 2, 6, 7);
			g.drawLine(10, 2, 10, 7);
			g.drawLine(3, 10, 13, 10);
			g.drawLine(6, 7, 3, 10);
			g.drawLine(10, 7, 13, 10);
			g.drawLine(8, 10, 8, 15);
		}
	}

	abstract static class TileIcon implements Icon
	{
		@Override public int getIconWidth() { return 24; }
		@Override public int getIconHeight() { return 24; }

		@Override
		public final void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.scale(1.5, 1.5);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ACCENT);
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			paint(g);
			g.dispose();
		}

		abstract void paint(Graphics2D g);
	}

	static final class CalendarIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			g.drawRoundRect(2, 3, 12, 11, 2, 2);
			g.drawLine(2, 7, 14, 7);
			g.drawLine(5, 1, 5, 4);
			g.drawLine(11, 1, 11, 4);
			g.fillRect(5, 9, 2, 2);
			g.fillRect(9, 9, 2, 2);
		}
	}

	static final class PartyIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			g.drawOval(3, 2, 5, 5);
			g.drawArc(0, 9, 11, 10, 0, 180);
			g.drawOval(10, 3, 4, 4);
			g.drawArc(9, 9, 7, 8, 20, 160);
		}
	}

	static final class MembersIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			for (int y = 3; y <= 13; y += 5)
			{
				g.fillOval(1, y - 1, 3, 3);
				g.drawLine(7, y, 15, y);
			}
		}
	}

	static final class DropsIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			g.drawRoundRect(2, 6, 12, 8, 2, 2);
			g.drawLine(2, 9, 14, 9);
			g.drawLine(8, 1, 8, 5);
			g.drawLine(6, 3, 8, 5);
			g.drawLine(10, 3, 8, 5);
		}
	}

	static final class NewsIcon extends TileIcon
	{
		@Override void paint(Graphics2D g)
		{
			Path2D horn = new Path2D.Double();
			horn.moveTo(2, 6);
			horn.lineTo(6, 6);
			horn.lineTo(13, 2);
			horn.lineTo(13, 14);
			horn.lineTo(6, 10);
			horn.lineTo(2, 10);
			horn.closePath();
			g.draw(horn);
			g.drawLine(4, 10, 5, 14);
		}
	}

	static final class CogIcon extends HoverIcon
	{
		CogIcon() { super(16); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.drawOval(3, 3, 10, 10);
			g.drawOval(6, 6, 4, 4);
			g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
			for (int i = 0; i < 8; i++)
			{
				double a = Math.PI / 4 * i;
				g.draw(new Line2D.Double(8 + 5 * Math.cos(a), 8 + 5 * Math.sin(a), 8 + 7.5 * Math.cos(a), 8 + 7.5 * Math.sin(a)));
			}
		}
	}

	static final class DiscordIcon extends HoverIcon
	{
		private static final Color BLURPLE = new Color(88, 101, 242);

		DiscordIcon() { super(20); }
		@Override Color hoverColor() { return BLURPLE; }
		@Override void paint(Graphics2D g, Component c)
		{
			Path2D body = new Path2D.Double();
			body.moveTo(5, 3.5);
			body.quadTo(10, 2.5, 15, 3.5);
			body.curveTo(18, 7, 19.5, 11, 19, 15);
			body.quadTo(17, 17, 14.5, 17.5);
			body.lineTo(13.5, 15.5);
			body.quadTo(10, 16.8, 6.5, 15.5);
			body.lineTo(5.5, 17.5);
			body.quadTo(3, 17, 1, 15);
			body.curveTo(0.5, 11, 2, 7, 5, 3.5);
			body.closePath();
			Area face = new Area(body);
			face.subtract(new Area(new Ellipse2D.Double(5.5, 8.5, 3, 3.5)));
			face.subtract(new Area(new Ellipse2D.Double(11.5, 8.5, 3, 3.5)));
			g.fill(face);
		}
	}

	static final class DotIcon implements Icon
	{
		private final Color color;

		DotIcon(Color color) { this.color = color; }
		@Override public int getIconWidth() { return 7; }
		@Override public int getIconHeight() { return 7; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(color);
			g.fillOval(x, y, 7, 7);
			g.dispose();
		}
	}

	static final class CheckIcon implements Icon
	{
		@Override public int getIconWidth() { return 14; }
		@Override public int getIconHeight() { return 14; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(SUCCESS);
			g.fillOval(0, 0, 14, 14);
			g.setColor(CARD);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D tick = new Path2D.Double();
			tick.moveTo(3.5, 7.5);
			tick.lineTo(6, 10);
			tick.lineTo(10.5, 4.5);
			g.draw(tick);
			g.dispose();
		}
	}

	static final class PinnedIcon implements Icon
	{
		@Override public int getIconWidth() { return 12; }
		@Override public int getIconHeight() { return 12; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ACCENT);
			g.fillRect(4, 1, 4, 5);
			g.fillPolygon(new int[] {1, 11, 8, 4}, new int[] {8, 8, 5, 5}, 4);
			g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(3, 1, 9, 1);
			g.drawLine(6, 8, 6, 11);
			g.dispose();
		}
	}

	static final class UnreadIcon implements Icon
	{
		@Override public int getIconWidth() { return 8; }
		@Override public int getIconHeight() { return 8; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(SUCCESS);
			g.fillOval(x, y, 8, 8);
			g.dispose();
		}
	}

	static final class WarningIcon implements Icon
	{
		@Override public int getIconWidth() { return 12; }
		@Override public int getIconHeight() { return 11; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ERROR);
			g.fillPolygon(new int[] {6, 12, 0}, new int[] {0, 11, 11}, 3);
			g.setColor(CARD);
			g.fillRect(5, 3, 2, 4);
			g.fillRect(5, 8, 2, 2);
			g.dispose();
		}
	}

	static final class NoteIcon implements Icon
	{
		@Override public int getIconWidth() { return 11; }
		@Override public int getIconHeight() { return 13; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(WARNING);
			g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawPolygon(new int[] {1, 7, 10, 10, 1}, new int[] {1, 1, 4, 12, 12}, 5);
			g.drawLine(3, 6, 8, 6);
			g.drawLine(3, 9, 8, 9);
			g.dispose();
		}
	}

	static final class ScopeIcon implements Icon
	{
		private final int people;

		ScopeIcon(int people) { this.people = people; }
		@Override public int getIconWidth() { return (int) Math.ceil((8 + (people - 1) * 5) * 0.75); }
		@Override public int getIconHeight() { return 9; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.scale(0.75, 0.75);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (int i = people - 1; i >= 0; i--)
			{
				int left = i * 5;
				g.setColor(CARD);
				g.fillOval(left - 1, -1, 6, 6);
				g.fillArc(left - 2, 5, 12, 13, 0, 180);
				g.setColor(MUTED);
				g.fillOval(left + 2, 0, 4, 4);
				g.fillArc(left, 6, 8, 10, 0, 180);
			}
			g.dispose();
		}
	}

	static final class LockIcon implements Icon
	{
		@Override public int getIconWidth() { return 10; }
		@Override public int getIconHeight() { return 12; }
		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(MUTED);
			g.setStroke(new BasicStroke(1.5f));
			g.drawArc(2, 1, 6, 8, 0, 180);
			g.drawLine(2, 5, 2, 6);
			g.drawLine(8, 5, 8, 6);
			g.fillRoundRect(0, 6, 10, 6, 2, 2);
			g.dispose();
		}
	}
}
