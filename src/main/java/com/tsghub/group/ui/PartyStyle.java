package com.tsghub.group.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;

public final class PartyStyle
{
	public static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	public static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;
	public static final Color SELF_CARD = new Color(43, 36, 22);
	public static final Color SELF_CARD_HOVER = new Color(53, 45, 28);
	public static final Color SELF_BORDER = new Color(122, 82, 8);
	public static final Color TEXT = Color.WHITE;
	public static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	public static final Color SUCCESS = ColorScheme.PROGRESS_COMPLETE_COLOR;
	public static final Color LOW = new Color(255, 90, 74);
	public static final Color MID = new Color(255, 194, 61);
	public static final Color SLOT = new Color(20, 20, 20);
	public static final Color DIVIDER = new Color(58, 48, 32);
	public static final Color PRAYER_ACTIVE = new Color(90, 67, 18);
	public static final Color PRAYER_ACTIVE_BORDER = new Color(200, 144, 42);

	private PartyStyle()
	{
	}

	public static Border cardBorder()
	{
		return BorderFactory.createEmptyBorder(7, 8, 7, 8);
	}

	public static Border selfBorder()
	{
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(SELF_BORDER), BorderFactory.createEmptyBorder(6, 7, 6, 7));
	}

	public static String plainTooltip(String text)
	{
		return "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>";
	}

	public static Color levelColor(int current, int max)
	{
		if (max <= 0) return TEXT;
		double ratio = (double) current / max;
		return ratio <= 0.25 ? LOW : ratio <= 0.5 ? MID : TEXT;
	}
}
