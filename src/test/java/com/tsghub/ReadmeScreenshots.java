package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tsghub.group.GroupMembersPanel;
import com.tsghub.group.GroupViewSettings;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.ui.laf.RuneLiteLAF;

// Renders the plugin's panels from fixture JSON into README screenshots: ./gradlew readmeScreenshots
public class ReadmeScreenshots
{
	private static final int SCALE = 2;
	private static final int SIDEBAR_W = 242;
	private static JsonObject data;
	private static File out;

	public static void main(String[] args) throws Exception
	{
		data = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8)).getAsJsonObject();
		out = new File(args[1]);
		out.mkdirs();
		SwingUtilities.invokeAndWait(() -> {
			try
			{
				run();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		System.exit(0);
	}

	private static void run() throws Exception
	{
		RuneLiteLAF.setup();
		TsgHubPlugin plugin = new TsgHubPlugin();
		set(plugin, "config", stub(TsgHubConfig.class));
		set(plugin, "detectedClanName", "TSGaming");
		set(plugin, "detectedClanRank", 100);
		set(plugin, "groups", new TsgHubGroups(plugin, null, (PartyService) unsafe().allocateInstance(PartyService.class), null, null, null));

		TsgHubSidebarPanel sidebar = new TsgHubSidebarPanel(plugin, new GroupMembersPanel(new GroupViewSettings() {}, null, null));
		sidebar.setIdentity("Crab Legs", 50);

		sidebar.showSharingOff();
		shoot(sidebar, SIDEBAR_W, 210, "sharing");

		JsonArray events = data.getAsJsonObject("eventList").getAsJsonArray("events");
		for (int i = 0; i < events.size(); i++)
		{
			JsonObject event = events.get(i).getAsJsonObject();
			String id = TsgHubUi.str(event, "id");
			event.addProperty("joined", id.equals(str("bingo")) || id.equals(str("skill")));
		}
		sidebar.setEvents(events);
		sidebar.showEventList();
		BufferedImage eventList = shoot(sidebar, SIDEBAR_W, 480, "events");

		sidebar.showBoard(data.getAsJsonObject("board").getAsJsonObject("event"), "Crab Legs", true);
		BufferedImage board = shoot(sidebar, SIDEBAR_W, 712, "board-tasks");
		selectTab(sidebar, "Scores");
		shoot(sidebar, SIDEBAR_W, 330, "board-scores");
		selectTab(sidebar, "Team");
		shoot(sidebar, SIDEBAR_W, 400, "board-team");

		sidebar.showCompetition(data.getAsJsonObject("skillBoard").getAsJsonObject("event"), "Crab Legs", true);
		shoot(sidebar, SIDEBAR_W, 400, "competition");

		sidebar.setGroups(data.getAsJsonObject("groupsList").getAsJsonArray("groups"));
		click(sidebar, "Parties");
		BufferedImage parties = shoot(sidebar, SIDEBAR_W, 480, "parties");

		TsgHubPanel organizer = new TsgHubPanel(plugin);
		organizer.setManagedEvents(data.getAsJsonObject("managed").getAsJsonArray("events"));
		organizer.openOrganizerEvent(data.getAsJsonObject("organizer"));
		shoot(organizer, 860, 300, "organizer-teams");
		selectTab(organizer, "Tasks");
		shoot(organizer, 860, 580, "organizer-tasks");
		selectTab(organizer, "Claims");
		shoot(organizer, 860, 240, "organizer-claims");

		hero(480, eventList, board, parties);
	}

	private static String str(String key)
	{
		return data.get(key).getAsString();
	}

	// Sidebar shots side by side, cropped to one height.
	private static void hero(int height, BufferedImage... shots) throws Exception
	{
		int gap = 24;
		int width = gap;
		for (BufferedImage shot : shots) width += shot.getWidth() / SCALE + gap;
		BufferedImage image = new BufferedImage(width * SCALE, (height + gap * 2) * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new java.awt.Color(0x1b1b1b));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		int x = gap * SCALE;
		for (BufferedImage shot : shots)
		{
			g.drawImage(shot.getSubimage(0, 0, shot.getWidth(), height * SCALE), x, gap * SCALE, null);
			x += shot.getWidth() + gap * SCALE;
		}
		g.dispose();
		ImageIO.write(image, "png", new File(out, "hero.png"));
	}

	private static BufferedImage shoot(JComponent panel, int width, int height, String name) throws Exception
	{
		JFrame frame = new JFrame();
		frame.setUndecorated(true);
		frame.setContentPane(panel);
		panel.setPreferredSize(new Dimension(width, height));
		frame.pack();
		// Container.paintComponents skips hidden windows, so show it (transparent, off screen).
		frame.setOpacity(0f);
		frame.setLocation(-4000, -4000);
		frame.setVisible(true);
		for (int i = 0; i < 3; i++)
		{
			invalidateAll(panel);
			frame.validate();
		}
		BufferedImage image = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.scale(SCALE, SCALE);
		panel.paint(g);
		g.dispose();
		ImageIO.write(image, "png", new File(out, name + ".png"));
		frame.setContentPane(new JComponent() {});
		frame.dispose();
		return image;
	}

	private static void invalidateAll(Component c)
	{
		c.invalidate();
		if (c instanceof Container) for (Component child : ((Container) c).getComponents()) invalidateAll(child);
	}

	private static sun.misc.Unsafe unsafe() throws Exception
	{
		Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		f.setAccessible(true);
		return (sun.misc.Unsafe) f.get(null);
	}

	private static void selectTab(Container root, String text)
	{
		Component tab = find(root, text, MaterialTab.class);
		if (tab == null) throw new IllegalStateException("No tab " + text);
		((MaterialTabGroup) tab.getParent()).select((MaterialTab) tab);
	}

	private static void click(Container root, String text)
	{
		Component button = find(root, text, AbstractButton.class);
		if (button == null) throw new IllegalStateException("No button " + text);
		((AbstractButton) button).doClick();
	}

	private static Component find(Container root, String text, Class<?> type)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				String label = c instanceof MaterialTab ? ((MaterialTab) c).getText() : ((AbstractButton) c).getText();
				if (label != null && label.startsWith(text)) return c;
			}
			if (c instanceof Container)
			{
				Component found = find((Container) c, text, type);
				if (found != null) return found;
			}
		}
		return null;
	}

	// Interface stub returning false, 0 or null.
	@SuppressWarnings("unchecked")
	private static <T> T stub(Class<T> type)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
			Class<?> r = method.getReturnType();
			if (r == boolean.class) return false;
			if (r == int.class) return 0;
			if (r == long.class) return 0L;
			return null;
		});
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}
}
