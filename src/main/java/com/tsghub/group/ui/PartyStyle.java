package com.tsghub.group.ui;

import com.tsghub.TsgHubTheme;
import java.awt.Color;

public final class PartyStyle
{
	public static final Color LOW = new Color(255, 90, 74);
	public static final Color SLOT = new Color(20, 20, 20);
	public static final Color DIVIDER = new Color(58, 48, 32);
	public static final Color PRAYER_ACTIVE = new Color(90, 67, 18);
	public static final Color PRAYER_ACTIVE_BORDER = new Color(200, 144, 42);

	private PartyStyle()
	{
	}

	public static Color levelColor(int current, int max)
	{
		if (max <= 0) return TsgHubTheme.TEXT;
		double ratio = (double) current / max;
		return ratio <= 0.25 ? LOW : ratio <= 0.5 ? TsgHubTheme.WARNING : TsgHubTheme.TEXT;
	}
}
