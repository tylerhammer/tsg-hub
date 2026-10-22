package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.DROP_TYPE;
import static com.tsghub.TsgHubSession.DROP_VALUE;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import lombok.RequiredArgsConstructor;
import net.runelite.client.util.AsyncBufferedImage;

@RequiredArgsConstructor
final class TsgHubDropsView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;
	final JPanel dropsPanel = new JPanel(new BorderLayout(0, GAP_M));

	private final WidthTrackingPanel dropsPage = new WidthTrackingPanel();
	private final JScrollPane dropsScroll = scroll(dropsPage);
	private final JComboBox<String> dropType = new JComboBox<>(new String[] {"All types", "Mine", "Raids", "Pets", "Log"});
	private final JComboBox<String> dropValue = new JComboBox<>(new String[] {"Any value", "100k+", "1m+", "10m+"});
	private volatile int dropTypeIndex;
	private volatile int dropValueIndex;
	private JsonArray drops;
	private static final int DROP_ICON_W = 40;
	private static final String[] DROP_KINDS = {"", "", "raid", "pet", "log"};
	private static final long[] DROP_VALUES = {0, 100_000, 1_000_000, 10_000_000};
	private static final Color TAG_DEFAULT = new Color(195, 140, 255);
	private static final Map<String, Color> TAG_COLORS = Map.of(
		"Log", new Color(55, 240, 70),
		"CoX", new Color(111, 179, 255),
		"ToB", new Color(255, 122, 122),
		"ToA", new Color(224, 180, 76),
		"Pet", new Color(255, 138, 216),
		"Dupe pet", new Color(255, 138, 216),
		"Spooned", new Color(255, 215, 64),
		"Dry", new Color(214, 160, 105));
	void setDrops(JsonArray drops)
	{
		if (Objects.equals(this.drops, drops)) return;
		this.drops = drops;
		if (shell.shows(View.DROPS)) renderDrops();
	}

	JPanel buildDropsView()
	{
		JPanel filters = panel(new java.awt.GridLayout(1, 2, GAP_S, 0));
		dropTypeIndex = sessionIndex(DROP_TYPE, dropType.getItemCount());
		dropValueIndex = sessionIndex(DROP_VALUE, dropValue.getItemCount());
		dropType.setSelectedIndex(dropTypeIndex);
		dropValue.setSelectedIndex(dropValueIndex);
		dropType.setToolTipText("Drop type");
		dropValue.setToolTipText("Minimum value");
		dropType.addActionListener(e -> changeDropFilter());
		dropValue.addActionListener(e -> changeDropFilter());
		filters.add(dropType);
		filters.add(dropValue);
		dropsPanel.setOpaque(false);
		dropsPanel.add(filters, BorderLayout.NORTH);
		dropsPanel.add(dropsScroll, BorderLayout.CENTER);
		return dropsPanel;
	}

	private int sessionIndex(String key, int size)
	{
		try
		{
			int index = Integer.parseInt(plugin.session().get(key));
			return index >= 0 && index < size ? index : 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private void changeDropFilter()
	{
		if (dropTypeIndex == dropType.getSelectedIndex() && dropValueIndex == dropValue.getSelectedIndex()) return;
		dropTypeIndex = Math.max(0, dropType.getSelectedIndex());
		dropValueIndex = Math.max(0, dropValue.getSelectedIndex());
		plugin.session().set(DROP_TYPE, dropTypeIndex == 0 ? "" : String.valueOf(dropTypeIndex));
		plugin.session().set(DROP_VALUE, dropValueIndex == 0 ? "" : String.valueOf(dropValueIndex));
		if (shell.shows(View.DROPS)) renderDrops();
		plugin.drops().load(false);
	}

	String dropsQuery()
	{
		String query = "";
		String kind = DROP_KINDS[dropTypeIndex];
		if (!kind.isEmpty()) query += "&kind=" + kind;
		if (dropTypeIndex == 1) query += "&player=" + TsgHubApi.encode(plugin.clan().playerName());
		if (dropValueIndex > 0) query += "&minValue=" + DROP_VALUES[dropValueIndex];
		return query;
	}

	void showDrops()
	{
		if (shell.pinned == View.DROPS)
		{
			shell.dock.expand();
			plugin.drops().load(false);
			return;
		}
		shell.setView(View.DROPS);
		renderDrops();
		plugin.drops().load(false);
	}

	void renderDrops()
	{
		shell.viewHeader(View.DROPS, "Drops", "", "");
		dropsPage.removeAll();
		if (drops == null)
		{
			dropsPage.add(caption("Loading drops..."));
		}
		else if (drops.size() == 0 && dropTypeIndex == 0 && dropValueIndex == 0)
		{
			dropsPage.add(shell.errorPanel("No drops yet", "Clan broadcasts for drops, raid loot, pets and collection log items show up here."));
		}
		else
		{
			Instant now = clock.instant();
			ZoneId zone = ZoneId.systemDefault();
			String self = plugin.clan().playerName();
			JsonArray shown = new JsonArray();
			for (int i = 0; i < drops.size(); i++)
			{
				JsonObject drop = drops.get(i).getAsJsonObject();
				if (TsgHubDrops.dropMatches(drop, dropTypeIndex, DROP_VALUES[dropValueIndex], self)) shown.add(drop);
			}
			if (shown.size() == 0) dropsPage.add(shell.errorPanel("No matching drops", "Nothing recent matches this type and value."));
			String day = null;
			for (int i = 0; i < shown.size(); i++)
			{
				JsonObject drop = shown.get(i).getAsJsonObject();
				String nextDay = TsgHubDrops.dropDay(str(drop, "receivedAt"), now, zone);
				if (!nextDay.equals(day))
				{
					dropsPage.add(listHeading(nextDay, i == 0));
					day = nextDay;
				}
				addCard(dropsPage, dropCard(drop, now, zone));
			}
		}
		refresh(dropsPage);
	}

	private JPanel dropCard(JsonObject drop, Instant now, ZoneId zone)
	{
		String player = str(drop, "player");
		boolean self = samePlayer(player, plugin.clan().playerName());
		JPanel card = card();
		if (self) highlightSelf(card);
		JLabel icon = dropIcon(integer(drop, "itemId", 0));
		if (icon != null) card.add(icon, BorderLayout.WEST);

		JLabel name = shrinkable(boldLabel(TsgHubDrops.dropItem(drop)));
		JPanel extras = panel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		for (String tag : TsgHubDrops.dropTags(drop)) extras.add(badge(tag, TAG_COLORS.getOrDefault(tag, TAG_DEFAULT)));
		long value = drop.has("value") ? drop.get("value").getAsLong() : 0;
		if (value > 0) extras.add(label(formatGp(value), coinColor(value), smallFont()));
		card.add(TsgHubSidebarPanel.fillWidth(twoLines(
			row(name, caption(TsgHubDrops.dropWhen(str(drop, "receivedAt"), now, zone))),
			row(shrinkable(label(player, ACCENT, smallFont())), extras))), BorderLayout.CENTER);
		fullTextTooltip(card, () -> TsgHubDrops.dropTooltip(drop));
		return fitHeight(card);
	}

	private JLabel dropIcon(int itemId)
	{
		if (itemId <= 0) return null;
		AsyncBufferedImage image = plugin.getItemImage(itemId);
		if (image == null) return null;
		JLabel label = new JLabel();
		label.setVerticalAlignment(JLabel.CENTER);
		label.setPreferredSize(new Dimension(DROP_ICON_W, 32));
		image.addTo(label);
		return label;
	}
}
