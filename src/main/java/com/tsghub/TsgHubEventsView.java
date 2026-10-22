package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

final class TsgHubEventsView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private final JTextField codeField = new JTextField();
	private final JButton joinButton = primaryButton("Join event");
	final JLabel joinError = label("", TsgHubTheme.ERROR, smallFont());

	private JsonArray events;
	private JsonObject previewEvent;

	TsgHubEventsView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		codeField.setToolTipText("Team code from an admin");
		placeholder(codeField, "Team code, e.g. DRGN42");
		codeField.addActionListener(e -> submitJoin());
		joinButton.addActionListener(e -> submitJoin());
		joinError.setVisible(false);
	}

	void setEvents(JsonArray events)
	{
		if (Objects.equals(this.events, events)) return;
		this.events = events;
		if (shell.view == View.EVENTS) renderEvents();
		else if (shell.view == View.HOME) shell.homeView.renderHome();
	}

	int liveEvents()
	{
		int live = 0;
		if (events != null)
			for (int i = 0; i < events.size(); i++)
				if ("active".equals(str(events.get(i).getAsJsonObject(), "status"))) live++;
		return live;
	}

	String eventsSummary()
	{
		if (events == null) return "Loading...";
		int live = liveEvents();
		int upcoming = -live;
		for (int i = 0; i < events.size(); i++)
			if (!"ended".equals(str(events.get(i).getAsJsonObject(), "status"))) upcoming++;
		if (live == 0 && upcoming == 0) return "No events right now";
		if (upcoming == 0) return live + " live";
		if (live == 0) return upcoming + " upcoming";
		return live + " live, " + upcoming + " upcoming";
	}

	void openEvents()
	{
		showEventList();
		if (!plugin.live()) plugin.sessions().loadClanEvents();
	}

	void showEventList()
	{
		shell.setView(View.EVENTS);
		renderEvents();
	}

	private void showDropParty(JsonObject event)
	{
		shell.setView(View.DROP_PARTY);
		shell.setHeader(eventName(event), "Custom", true, false);
		shell.page.removeAll();
		JsonObject config = eventConfig(event);
		JPanel card = card();
		JPanel lines = stack();
		String countdown = capitalize(eventRelative(event));
		if (!countdown.isEmpty()) lines.add(label(countdown, "Ended".equals(countdown) ? MUTED : SUCCESS, boldFont()));
		lines.add(gap(GAP_S));
		lines.add(detail("When", eventWhen(event)));
		if (integer(config, "world", 0) > 0) lines.add(detail("World", String.valueOf(integer(config, "world", 0))));
		if (!str(config, "location").isEmpty()) lines.add(detail("Where", str(config, "location")));
		if (!str(config, "host").isEmpty()) lines.add(detail("Host", str(config, "host")));
		JPanel prizes = prizeRow(event, smallFont());
		if (prizes != null)
		{
			lines.add(gap(GAP_S));
			lines.add(prizes);
		}
		card.add(lines, BorderLayout.CENTER);
		shell.page.add(fitHeight(card));
		String notes = str(config, "notes");
		if (!notes.isEmpty())
		{
			shell.page.add(gap(GAP_M));
			shell.page.add(TsgHubSidebarPanel.hint(notes));
		}
		shell.refreshPage();
	}

	private JLabel detail(String label, String value)
	{
		JLabel line = label(html("<font color='" + toHexColor(MUTED) + "'>" + label + ":</font> " + escape(value), TsgHubSidebarPanel.CARD_TEXT_W), TEXT, plainFont());
		line.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		return line;
	}

	void joinFailed(String message)
	{
		joinButton.setEnabled(true);
		joinButton.setText("Join event");
		showError(joinError, message, TsgHubSidebarPanel.TEXT_W);
		codeField.requestFocusInWindow();
		codeField.selectAll();
	}

	private void renderEvents()
	{
		String clan = plugin.clan().name();
		shell.setHeader("Events", "", true, true);
		shell.page.removeAll();
		if (clan.isEmpty())
		{
			shell.page.add(shell.errorPanel("No clan detected", "Join a clan in game to see its events."));
		}
		else if (events == null)
		{
			shell.page.add(caption("Loading events..."));
		}
		else
		{
			List<JsonObject> visible = objects(events);
			visible.removeIf(event -> "ended".equals(str(event, "status")));
			visible.sort(Comparator
				.comparing((JsonObject e) -> !bool(e, "joined"))
				.thenComparing(e -> !"active".equals(str(e, "status")))
				.thenComparing(TsgHubEvents::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
			if (visible.isEmpty())
			{
				shell.page.add(shell.errorPanel("No events yet", "When an admin creates an event, it will show up here."));
			}
			String section = null;
			for (JsonObject event : visible)
			{
				String next = "active".equals(str(event, "status")) ? "Live" : "Upcoming";
				if (!next.equals(section))
				{
					shell.page.add(listHeading(next, section == null));
					section = next;
				}
				addCard(shell.page, eventCard(event));
			}
		}
		shell.refreshPage();
	}

	private JPanel eventCard(JsonObject event)
	{
		boolean joined = bool(event, "joined");
		JPanel card = card();
		if (joined) highlightSelf(card);
		card.add(eventLines(event, plugin::getCoinImage), BorderLayout.CENTER);

		String type = str(event, "type");
		boolean dropParty = "drop-party".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		fullTextTooltip(card, () -> escape(eventWhen(event)));
		clickable(card, () -> {
			String id = str(event, "id");
			if (dropParty) showDropParty(event);
			else if (competition && joined && !plugin.session().get(MEMBER_TOKEN + id).isEmpty()) plugin.sessions().openCompetition(id, true);
			else if (competition) shell.competitionView.showCompetitionPreview(event);
			else if (joined) plugin.sessions().activateEvent(id);
			else showPreview(event);
		});
		return fitHeight(card);
	}

	JPanel eventSummaryCard(JsonObject event, String subject, String goal)
	{
		JPanel card = card();
		JPanel lines = stack();
		lines.add(shrinkable(boldLabel(subject)));
		lines.add(gap(GAP_XS));
		lines.add(wrapped(goal, TEXT, smallFont(), TsgHubSidebarPanel.CARD_TEXT_W));
		lines.add(gap(GAP_XS));
		lines.add(TsgHubSidebarPanel.cardNote(eventWhen(event)));
		String description = str(event, "description").trim();
		if (!description.isEmpty())
		{
			lines.add(gap(GAP_S));
			lines.add(TsgHubSidebarPanel.cardNote(description));
		}
		JPanel prizes = prizeRow(event, smallFont());
		if (prizes != null)
		{
			lines.add(gap(GAP_S));
			lines.add(prizes);
		}
		card.add(lines, BorderLayout.CENTER);
		return fitHeight(card);
	}

	private void showPreview(JsonObject event)
	{
		previewEvent = event;
		shell.setView(View.PREVIEW);
		shell.setEventHeader(event, true, false);
		shell.page.removeAll();
		shell.page.add(eventSummaryCard(event, "Bingo", "Complete tasks with your team for points. Most points wins."));
		shell.page.add(gap(GAP_L));
		shell.page.add(label("Team code", TEXT, smallFont()));
		shell.page.add(gap(GAP_S));
		codeField.setText("");
		shell.page.add(fitHeight(codeField));
		joinError.setVisible(false);
		shell.page.add(joinError);
		shell.page.add(gap(GAP_S));
		joinButton.setEnabled(true);
		joinButton.setText("Join event");
		shell.page.add(fitHeight(joinButton));
		shell.page.add(gap(GAP_S));
		shell.page.add(TsgHubSidebarPanel.hint("Ask an admin for your team's code. Your team can't be changed after you join."));
		shell.refreshPage();
		SwingUtilities.invokeLater(codeField::requestFocusInWindow);
	}

	private void submitJoin()
	{
		String code = codeField.getText().trim();
		if (code.isEmpty())
		{
			joinFailed("Enter your team code.");
			return;
		}
		joinError.setVisible(false);
		joinButton.setEnabled(false);
		joinButton.setText("Joining...");
		plugin.sessions().join(code, str(previewEvent, "id"));
	}
}
