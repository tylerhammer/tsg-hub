package com.tsghub;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ColorUtil;

public final class TsgHubTheme
{
	public static final Color BACKGROUND = new Color(0x18171D);
	public static final Color CARD = new Color(0x201E26);
	public static final Color CARD_HOVER = new Color(0x2A2731);
	public static final Color BORDER = new Color(0x2C2A33);
	public static final Color ACCENT = new Color(0xA98BF0);
	public static final Color TEXT = new Color(0xF2EFF5);
	public static final Color MUTED = new Color(0xA7A1B1);
	public static final Color DIM = new Color(0x7C7785);
	public static final Color SUCCESS = new Color(0x6BD68A);
	public static final Color ERROR = new Color(0xF08A8A);
	public static final Color WARNING = new Color(0xE8BE5A);
	public static final Color CAPPED = new Color(0xFFD700);
	public static final Color DISCORD = new Color(88, 101, 242);
	public static final Color CHAT = new Color(0x3F2575);
	public static final Color CHAT_SUCCESS = new Color(0x1A5028);

	public static final Color SELF_CARD = new Color(0x2A2535);
	public static final Color SELF_CARD_HOVER = new Color(0x332D41);
	public static final Color SELF_BORDER = new Color(0x4A3D6E);
	public static final Color SELF_TEXT = ACCENT;
	public static final Color LOW = new Color(255, 90, 74);
	public static final Color SLOT = new Color(0x141318);
	public static final Color PRAYER_ACTIVE = new Color(90, 67, 18);
	public static final Color PRAYER_ACTIVE_BORDER = new Color(200, 144, 42);

	public static final int GAP_XS = 3;
	public static final int GAP_S = 4;
	public static final int LIST_GAP = 6;
	public static final int GAP_M = 8;
	public static final int GAP_L = 12;

	public static final int CARD_PAD_V = 6;
	public static final int CARD_PAD_H = 8;
	public static final int RADIUS = 6;

	private TsgHubTheme()
	{
	}

	public static Font smallFont()
	{
		return FontManager.getRunescapeSmallFont();
	}

	public static Font plainFont()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font boldFont()
	{
		return FontManager.getRunescapeBoldFont();
	}

	public static Font headingFont()
	{
		return boldFont().deriveFont(18f);
	}

	private static final Border CARD_EDGE = new RoundEdge(new Insets(CARD_PAD_V + 1, CARD_PAD_H + 1, CARD_PAD_V, CARD_PAD_H + 1));
	private static final Border SELF_EDGE = CARD_EDGE;

	static final class RoundEdge extends AbstractBorder
	{
		private final Insets insets;

		RoundEdge(Insets insets)
		{
			this.insets = insets;
		}

		@Override
		public Insets getBorderInsets(Component c, Insets into)
		{
			into.set(insets.top, insets.left, insets.bottom, insets.right);
			return into;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			RoundRectangle2D shape = new RoundRectangle2D.Float(x + .5f, y + .5f, w - 1, h - 1, RADIUS * 2, RADIUS * 2);
			Area corners = new Area(new Rectangle(x, y, w, h));
			corners.subtract(new Area(new RoundRectangle2D.Float(x, y, w, h, RADIUS * 2, RADIUS * 2)));
			g2.setColor(behind(c));
			g2.fill(corners);
			Color bg = c.getBackground();
			g2.setColor(SELF_CARD.equals(bg) || SELF_CARD_HOVER.equals(bg) ? SELF_BORDER : BORDER);
			g2.draw(shape);
			g2.dispose();
		}

		private static Color behind(Component c)
		{
			for (Component p = c.getParent(); p != null; p = p.getParent())
				if (p.isOpaque()) return p.getBackground();
			return BACKGROUND;
		}
	}

	public static Border cardBorder()
	{
		return CARD_EDGE;
	}

	public static Border selfBorder()
	{
		return SELF_EDGE;
	}

	public static boolean truncated(JLabel label)
	{
		String text = label.getText();
		if (text == null || text.isEmpty() || text.startsWith("<html") || label.getWidth() <= 0) return false;
		Icon icon = label.getIcon();
		int needed = label.getFontMetrics(label.getFont()).stringWidth(text) + (icon == null ? 0 : icon.getIconWidth() + label.getIconTextGap());
		return needed > label.getWidth();
	}

	public static JLabel fullTextLabel()
	{
		JLabel label = new JLabel()
		{
			@Override
			public String getToolTipText(MouseEvent event)
			{
				String extra = super.getToolTipText(event);
				if (!truncated(this)) return extra;
				return "<html><b>" + TsgHubHtml.escape(getText()) + "</b>" + (extra == null ? "" : "<br>" + TsgHubHtml.escape(extra)) + "</html>";
			}
		};
		ToolTipManager.sharedInstance().registerComponent(label);
		return label;
	}

	public static void highlightSelf(JComponent card)
	{
		card.setBackground(SELF_CARD);
		card.setBorder(selfBorder());
	}

	public static String selfName(String escapedName)
	{
		return "<font color='" + ColorUtil.toHexColor(SELF_TEXT) + "'>" + escapedName + "</font>";
	}

	public static MaterialTab themedTab(String name, MaterialTabGroup group, JComponent content)
	{
		return new MaterialTab(name, group, content)
		{
			@Override
			public boolean select()
			{
				if (!super.select()) return false;
				setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ACCENT),
					BorderFactory.createEmptyBorder(5, 10, 4, 10)));
				setForeground(TEXT);
				return true;
			}

			@Override
			public void unselect()
			{
				super.unselect();
				setForeground(MUTED);
			}
		};
	}
}
