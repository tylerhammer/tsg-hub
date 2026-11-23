package com.tsghub;

import com.google.gson.*;
import java.awt.*;
import java.text.*;
import java.util.List;
import java.util.function.*;
import java.util.stream.*;
import javax.swing.*;
import net.runelite.client.util.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

final class TsgHubPanelView
{
	private TsgHubPanelView()
	{
	}

	static void render(Container page, JsonObject panel, String self, Component parent, BiConsumer<String, JsonObject> run, Consumer<String> failed)
	{
		JPanel lines = stack();
		if (!str(panel, "title").isEmpty()) lines.add(row(boldLabel(str(panel, "title")), label(str(panel, "right"), tone(panel), smallFont())));
		for (JsonObject section : objects(array(panel, "sections"))) addSection(lines, section, self, parent, run, failed);
		if (lines.getComponentCount() > 0)
		{
			JPanel card = card();
			card.add(lines, BorderLayout.CENTER);
			addCard(page, fitHeight(card));
		}
		JPanel buttons = TsgHubUi.panel(new GridLayout(1, 0, 4, 0));
		for (JsonObject action : actions(panel, "buttons", self))
		{
			JButton button = button(str(action, "label"));
			button.setToolTipText(str(action, "tooltip").isEmpty() ? null : TsgHubHtml.plainTip(str(action, "tooltip")));
			button.addActionListener(e -> {
				if (perform(parent, action, run, failed)) button.setEnabled(false);
			});
			buttons.add(button);
		}
		if (buttons.getComponentCount() == 0) return;
		page.add(fitHeight(buttons));
		page.add(gap(GAP_L));
	}

	private static void addSection(JPanel lines, JsonObject section, String self, Component parent, BiConsumer<String, JsonObject> run, Consumer<String> failed)
	{
		List<JsonObject> rows = objects(array(section, "rows"));
		String title = str(section, "title");
		boolean first = lines.getComponentCount() == 0;
		if (!title.isEmpty())
		{
			String side = str(section, "caption");
			if (side.isEmpty() && rows.stream().anyMatch(r -> !actions(r, "actions", self).isEmpty())) side = str(section, "hint");
			JComponent head = side.isEmpty() ? caption(title) : row(caption(title), caption(side));
			if (first) lines.add(head);
			else addGap(lines, GAP_M, head);
		}
		else if (!first) lines.add(gap(GAP_S));
		for (int i = 0; i < rows.size(); i++)
		{
			JComponent line = rowView(rows.get(i), self, parent, run, failed);
			if (title.isEmpty() && i == 0) lines.add(line);
			else addGap(lines, array(rows.get(i), "notes").size() > 0 ? GAP_S : GAP_XS, line);
		}
	}

	private static JComponent rowView(JsonObject row, String self, Component parent, BiConsumer<String, JsonObject> run, Consumer<String> failed)
	{
		String right = str(row, "right");
		JPanel line = row();
		line.add(shrinkable(label(str(row, "left"), right.isEmpty() ? tone(row) : TEXT, smallFont())), BorderLayout.CENTER);
		if (!right.isEmpty()) line.add(label(right, tone(row), smallFont()), BorderLayout.EAST);
		if (bool(row, "indent")) line.setBorder(BorderFactory.createEmptyBorder(0, GAP_M, 0, 0));
		JPanel view = line;
		if (array(row, "notes").size() > 0)
		{
			view = stack();
			view.add(line);
			for (String note : strings(array(row, "notes"))) view.add(shrinkable(caption(note)));
		}
		JPopupMenu menu = new JPopupMenu();
		for (JsonObject action : actions(row, "actions", self)) item(menu, str(action, "label"), () -> perform(parent, action, run, failed));
		if (menu.getComponentCount() > 0) withPopup(view, menu);
		return view;
	}

	static List<JsonObject> actions(JsonObject owner, String key, String self)
	{
		return objects(array(owner, key)).stream().filter(a -> visible(a, self)).collect(Collectors.toList());
	}

	static boolean visible(JsonObject action, String self)
	{
		if (!TsgHubGroups.PANEL_OPS.containsKey(str(action, "op"))) return false;
		JsonObject prompt = object(action, "prompt");
		if (prompt.size() > 0 && !str(prompt, "kind").equals("gp")) return false;
		List<String> who = strings(array(action, "who"));
		return who.isEmpty() || who.stream().anyMatch(n -> PlayerNames.samePlayer(n, self));
	}

	private static boolean perform(Component parent, JsonObject action, BiConsumer<String, JsonObject> run, Consumer<String> failed)
	{
		JsonObject ask = object(action, "confirm");
		if (ask.size() > 0 && !confirm(parent, str(ask, "title"), str(ask, "text"), str(ask, "yes"), false)) return false;
		JsonObject args = object(action, "args").deepCopy();
		JsonObject prompt = object(action, "prompt");
		if (prompt.size() > 0)
		{
			String initial = str(prompt, "initial");
			Object input = JOptionPane.showInputDialog(parent, str(prompt, "text"), str(prompt, "title"), JOptionPane.PLAIN_MESSAGE, null, null, initial.isEmpty() ? null : initial);
			if (input == null) return false;
			long gp = parseGp(input.toString());
			if (gp <= 0)
			{
				failed.accept("Enter an amount like 1.4b, 350m or 2500000.");
				return false;
			}
			args.addProperty("gp", gp);
		}
		run.accept(str(action, "op"), args);
		return true;
	}

	static long parseGp(String text)
	{
		try { return QuantityFormatter.parseQuantity(text.trim().replace(",", "").replace(" ", "")); }
		catch (ParseException | RuntimeException e) { return -1; }
	}

	private static Color tone(JsonObject owner)
	{
		switch (str(owner, "tone"))
		{
			case "muted": return MUTED;
			case "success": return SUCCESS;
			case "warning": return WARNING;
			case "accent": return ACCENT;
			default: return TEXT;
		}
	}
}
