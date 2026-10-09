package com.tsghub;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ColorUtil;

public final class TsgHubTheme
{
	public static final Color BACKGROUND = new Color(0x28262B);
	public static final Color CARD = new Color(0x1F1D22);
	public static final Color CARD_HOVER = new Color(0x2E2B33);
	public static final Color BORDER = new Color(0x3B3741);
	public static final Color ACCENT = new Color(0xA98BF0);
	public static final Color TEXT = new Color(0xF2EFF5);
	public static final Color MUTED = new Color(0xA29CAB);
	public static final Color DIM = new Color(0x6C6774);
	public static final Color SUCCESS = new Color(0x6BD68A);
	public static final Color ERROR = new Color(0xE86060);
	public static final Color WARNING = new Color(0xE8BE5A);

	public static final Color SELF_CARD = new Color(0x2A2238);
	public static final Color SELF_CARD_HOVER = new Color(0x342A45);
	public static final Color SELF_BORDER = new Color(0x664C9E);
	public static final Color SELF_TEXT = ACCENT;

	public static final int GAP_XS = 3;
	public static final int GAP_S = 4;
	public static final int LIST_GAP = 6;
	public static final int GAP_M = 8;
	public static final int GAP_L = 12;

	public static final int CARD_PAD_V = 7;
	public static final int CARD_PAD_H = 8;
	private static final int SELECTED_BAR = 3;

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

	public static Font titleFont()
	{
		return boldFont().deriveFont(20f);
	}

	public static Border cardBorder()
	{
		return BorderFactory.createEmptyBorder(CARD_PAD_V, CARD_PAD_H, CARD_PAD_V, CARD_PAD_H);
	}

	public static Border selfBorder()
	{
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(SELF_BORDER),
			BorderFactory.createEmptyBorder(CARD_PAD_V - 1, CARD_PAD_H - 1, CARD_PAD_V - 1, CARD_PAD_H - 1));
	}

	public static Border selectedBorder(boolean selected, Color idle)
	{
		return BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, SELECTED_BAR, 0, 0, selected ? ACCENT : idle),
			BorderFactory.createEmptyBorder(CARD_PAD_V, CARD_PAD_H - SELECTED_BAR, CARD_PAD_V, CARD_PAD_H));
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
				return "<html><b>" + escape(getText()) + "</b>" + (extra == null ? "" : "<br>" + escape(extra)) + "</html>";
			}
		};
		ToolTipManager.sharedInstance().registerComponent(label);
		return label;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
