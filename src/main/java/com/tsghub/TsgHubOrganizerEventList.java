package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

final class TsgHubOrganizerEventList
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	private final JPanel eventList = stack();
	String selectedEventId = "";
	JsonArray managedEvents = new JsonArray();

	TsgHubOrganizerEventList(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	JComponent buildEventListColumn()
	{
		JPanel column = new JPanel(new BorderLayout(0, 8));
		column.setBackground(CARD);
		column.setPreferredSize(new Dimension(TsgHubPanel.LIST_W, 100));
		column.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

		JButton refresh = iconButton(new RefreshIcon(), "Refresh events");
		refresh.addActionListener(e -> plugin.organizer().loadManagedEvents());
		JPanel header = row(boldLabel("Events"), refresh);

		JButton create = primaryButton("New event");
		create.addActionListener(e -> shell.eventForm.beginCreateEvent());

		JPanel top = stack();
		top.add(fitHeight(header));
		top.add(Box.createVerticalStrut(GAP_M));
		top.add(fitHeight(create));
		column.add(top, BorderLayout.NORTH);

		WidthTrackingPanel listWrap = new WidthTrackingPanel();
		listWrap.add(eventList);
		column.add(scroll(listWrap), BorderLayout.CENTER);
		return column;
	}

	void setManagedEvents(JsonArray events)
	{
		managedEvents = events;
		eventList.removeAll();
		List<JsonObject> sorted = objects(events);
		sorted.sort(Comparator
			.comparingInt((JsonObject e) -> statusOrder(str(e, "status")))
			.thenComparing(TsgHubEvents::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
		String section = null;
		for (JsonObject event : sorted)
		{
			String id = str(event, "id");
			String next = statusLabel(str(event, "status"));
			if (!next.equals(section))
			{
				eventList.add(listHeading(next, section == null));
				section = next;
			}
			JPanel card = card();
			boolean selected = id.equals(selectedEventId);
			if (selected) card.setBorder(selectedBorder(true, CARD_HOVER));
			card.setBackground(selected ? CARD_HOVER : BACKGROUND);
			card.add(eventLines(event, plugin::getCoinImage), BorderLayout.CENTER);
			fullTextTooltip(card, () -> escape(eventWhen(event)));
			clickable(card, () -> {
				selectedEventId = id;
				setManagedEvents(managedEvents);
				plugin.organizer().selectEvent(id);
			});
			eventList.add(fitHeight(card));
			eventList.add(Box.createVerticalStrut(LIST_GAP));
		}
		if (sorted.isEmpty())
		{
			eventList.add(wrapped("No events yet. Create one to get started.", MUTED, smallFont(), TsgHubPanel.LIST_W - 30));
		}
		refresh(eventList);
	}

	private static int statusOrder(String status)
	{
		return "active".equals(status) ? 0 : "ended".equals(status) ? 2 : 1;
	}
}
