package com.tsghub;

import static com.tsghub.TsgHubEvents.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubHtml.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanRank;

final class TsgHubEventForm
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	private final JLabel eventFormTitle = sectionTitle("New event");
	private final JLabel detectedClan = caption("");
	final JTextField eventName = new JTextField();
	private JPanel nameRow;
	private final TsgHubDatePicker eventStart = new TsgHubDatePicker();
	private final TsgHubDatePicker eventEnd = new TsgHubDatePicker();
	private final JTextField eventStartTime = new JTextField();
	private final JTextField eventEndTime = new JTextField();
	private final JLabel eventZoneHint = caption("");
	private final JCheckBox eventHideScores = new JCheckBox("Hide scores from players");
	private final JCheckBox eventHidden = new JCheckBox("Hide event from players");
	private static final String[] EVENT_TYPES = {"bingo", "skill", "boss", "drop-party"};
	private final JComboBox<String> eventType = new JComboBox<>(new String[] {"Bingo", "Skill of the Week", "Boss of the Week", "Custom"});
	private final JComboBox<String> eventSkill = new JComboBox<>();
	private final TsgHubBossPicker eventBoss = new TsgHubBossPicker("Boss", null);
	private final JRadioButton eventSignalKc = new JRadioButton("Kill count message (recommended)", true);
	private final JRadioButton eventSignalLoot = new JRadioButton("Loot drop (bosses without a KC message)");
	private final JTextField partyWorld = new JTextField();
	private final JTextField partyLocation = new JTextField();
	private final JTextField partyHost = new JTextField();
	private final JTextField partyNotes = new JTextField();
	private final JTextField[] prizeFields = {new JTextField(), new JTextField(), new JTextField()};
	private final JLabel eventTypeHint = caption("");
	private JPanel skillRow;
	private JPanel bossRow2;
	private JPanel signalRow2;
	private JPanel partyRows;
	private JPanel hideScoresRow;
	private JLabel endTimeCaption;
	private final JLabel eventFormError = label("", TsgHubTheme.ERROR, smallFont());
	private final JButton eventFormSave = primaryButton("Create event");
	private boolean editingEvent;

	TsgHubEventForm(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
	}

	JComponent buildEventForm()
	{
		JPanel form = stack();
		form.add(eventFormTitle);
		form.add(detectedClan);
		form.add(Box.createVerticalStrut(GAP_M));
		JPanel typeField = field("Event type", eventType);
		typeField.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
		form.add(typeField);
		eventTypeHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		form.add(eventTypeHint);
		nameRow = field("Event name", eventName);
		form.add(nameRow);
		JPanel dates = panel(new GridLayout(2, 2, 10, 0));
		JPanel endTimeField = field("End time (HH:MM)", eventEndTime);
		endTimeCaption = (JLabel) endTimeField.getComponent(0);
		// Moving the start past the end moves the end along.
		eventStart.onChange(() -> { if (eventEnd.getDate().isBefore(eventStart.getDate())) eventEnd.setDate(eventStart.getDate()); });
		dates.add(field("Start date", eventStart));
		dates.add(field("Start time (HH:MM)", eventStartTime));
		dates.add(field("End date", eventEnd));
		dates.add(endTimeField);
		form.add(fitHeight(dates));
		eventZoneHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		form.add(eventZoneHint);

		for (Skill skill : Skill.values())
			if (!"OVERALL".equals(skill.name())) eventSkill.addItem(skillName(skill.name()));
		skillRow = field("Skill", eventSkill);
		form.add(skillRow);
		bossRow2 = eventBoss;
		form.add(bossRow2);
		signalRow2 = TsgHubPanel.radioField("Count kills using", eventSignalKc, eventSignalLoot);
		form.add(signalRow2);

		partyRows = stack();
		partyRows.add(field("World", partyWorld));
		partyRows.add(field("Location", partyLocation));
		partyRows.add(field("Host", partyHost));
		partyRows.add(field("Notes (optional)", partyNotes));
		form.add(partyRows);

		JPanel prizeGrid = panel(new GridLayout(1, prizeFields.length, 10, 0));
		for (int i = 0; i < prizeFields.length; i++)
		{
			prizeGrid.add(field(place(i + 1) + " prize (M)", prizeFields[i]));
			placeholder(prizeFields[i], i == 0 ? "e.g. 20" : "optional");
		}
		form.add(fitHeight(prizeGrid));
		JLabel prizeHint = caption("In millions of GP: 20 is 20M, 1500 is 1.5B, 0.5 is 500K. Leave blank for no prize.");
		prizeHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		form.add(prizeHint);

		hideScoresRow = stack();
		hideScoresRow.add(plain(eventHideScores, TEXT));
		hideScoresRow.add(wrapped("Players still see their own progress, but not other players' or teams' scores and ranks. You can change this any time.", MUTED, smallFont(), 480));
		form.add(hideScoresRow);
		form.add(Box.createVerticalStrut(GAP_S));
		form.add(plain(eventHidden, TEXT));
		form.add(wrapped("Only admins can see and join it, so you can set it up and test it first. Publish it when it's ready. Players who already joined keep access.", MUTED, smallFont(), 480));
		form.add(Box.createVerticalStrut(GAP_L));
		eventType.addActionListener(e -> {
			if (!editingEvent) defaultEventEnd();
			updateEventFormType();
		});
		placeholder(eventStartTime, "e.g. 19:30");
		placeholder(eventEndTime, "e.g. 21:00");
		placeholder(partyWorld, "e.g. 330");
		placeholder(partyLocation, "e.g. Falador Park");
		placeholder(partyNotes, "e.g. Bring an empty inventory");
		eventFormError.setVisible(false);
		form.add(eventFormError);
		form.add(Box.createVerticalStrut(GAP_L));

		JButton cancel = button("Cancel");
		cancel.addActionListener(e -> {
			if (editingEvent && shell.currentEvent != null) shell.detailLayout.show(shell.detail, "event");
			else shell.detailLayout.show(shell.detail, shell.currentEvent == null ? "empty" : "event");
		});
		placeholder(eventName, "e.g. Autumn Bingo");
		eventFormSave.addActionListener(e -> submitEventForm());
		eventName.addActionListener(e -> submitEventForm());
		form.add(TsgHubPanel.footer(cancel, eventFormSave));
		return narrowPage(form);
	}

	void setDetectedClanName(String clanName, int clanRank)
	{
		if (clanName == null || clanName.trim().isEmpty())
		{
			detectedClan.setText("No clan detected. Log in to a clan member character.");
			return;
		}
		String role = clanRank >= ClanRank.OWNER.getRank() ? "Owner"
			: clanRank >= ClanRank.DEPUTY_OWNER.getRank() ? "Deputy Owner"
			: clanRank >= ClanRank.ADMINISTRATOR.getRank() ? "Administrator"
			: "Member";
		detectedClan.setText("For " + clanName.trim() + " · you are " + role);
	}

	void eventFormFailed(String message)
	{
		showError(eventFormError, message, TsgHubPanel.DETAIL_TEXT_W);
	}

	private void eventFormFailed(String message, JComponent focus)
	{
		eventFormFailed(message);
		focus.requestFocusInWindow();
	}

	void beginCreateEvent()
	{
		editingEvent = false;
		eventFormTitle.setText("New event");
		detectedClan.setVisible(true);
		eventFormSave.setText("Create event");
		eventName.setText("");
		ZonedDateTime start = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1);
		setEventTimes(start, start.plusDays(7));
		eventHideScores.setSelected(false);
		eventHidden.setSelected(false);
		eventType.setEnabled(true);
		eventType.setSelectedIndex(0);
		eventSkill.setSelectedIndex(0);
		eventBoss.setText("");
		eventSignalKc.setSelected(true);
		partyWorld.setText("");
		partyLocation.setText("");
		partyHost.setText(plugin.clan().playerName());
		partyNotes.setText("");
		for (JTextField prize : prizeFields) prize.setText("");
		showEventForm();
	}

	private void showEventForm()
	{
		updateEventFormType();
		eventFormError.setVisible(false);
		shell.detailLayout.show(shell.detail, "event-form");
		eventName.requestFocusInWindow();
	}

	void beginEditEvent()
	{
		if (shell.currentEvent == null) return;
		editingEvent = true;
		eventFormTitle.setText("Edit event");
		detectedClan.setVisible(false);
		eventFormSave.setText("Save changes");
		eventName.setText(str(shell.currentEvent, "name"));
		Instant start = TsgHubEvents.eventStart(shell.currentEvent);
		Instant end = TsgHubEvents.eventEnd(shell.currentEvent);
		setEventTimes(start == null ? null : start.atZone(ZoneId.systemDefault()), end == null ? null : end.atZone(ZoneId.systemDefault()));
		eventHideScores.setSelected(bool(shell.currentEvent, "hideScores"));
		eventHidden.setSelected(bool(shell.currentEvent, "hidden"));
		String type = str(shell.currentEvent, "type");
		eventType.setSelectedIndex(Math.max(0, Arrays.asList(EVENT_TYPES).indexOf(type.isEmpty() ? "bingo" : type)));
		eventType.setEnabled(false);
		JsonObject config = eventConfig(shell.currentEvent);
		eventSkill.setSelectedItem(skillName(str(config, "skill")));
		eventBoss.setText(str(config, "npcName"));
		if ("loot".equals(str(config, "signal"))) eventSignalLoot.setSelected(true);
		else eventSignalKc.setSelected(true);
		partyWorld.setText(integer(config, "world", 0) > 0 ? String.valueOf(integer(config, "world", 0)) : "");
		partyLocation.setText(str(config, "location"));
		partyHost.setText(str(config, "host"));
		partyNotes.setText(str(config, "notes"));
		List<Long> prizes = eventPrizes(shell.currentEvent);
		for (int i = 0; i < prizeFields.length; i++) prizeFields[i].setText(i < prizes.size() ? millions(prizes.get(i)) : "");
		showEventForm();
	}

	private void submitEventForm()
	{
		String type = EVENT_TYPES[Math.max(0, eventType.getSelectedIndex())];
		String name = "skill".equals(type) ? "Skill of the Week" : "boss".equals(type) ? "Boss of the Week" : eventName.getText().trim();
		if (name.isEmpty()) { eventFormFailed("Give the event a name.", eventName); return; }
		boolean dropParty = "drop-party".equals(type);
		Instant start = eventInstant(eventStart, eventStartTime);
		if (start == null) { eventFormFailed("Start time must look like 19:30.", eventStartTime); return; }
		Instant end = null;
		if (!dropParty || !eventEndTime.getText().trim().isEmpty())
		{
			end = eventInstant(eventEnd, eventEndTime);
			if (end == null) { eventFormFailed("End time must look like 21:00.", eventEndTime); return; }
			if (!end.isAfter(start)) { eventFormFailed("The end must be after the start.", eventEndTime); return; }
		}
		JsonObject config = new JsonObject();
		if ("skill".equals(type)) config.addProperty("skill", String.valueOf(eventSkill.getSelectedItem()).toUpperCase(Locale.ROOT).replace(' ', '_'));
		if ("boss".equals(type))
		{
			String boss = eventBoss.getText();
			if (boss.isEmpty()) { eventFormFailed("Choose or type the boss.", eventBoss.field()); return; }
			config.addProperty("npcName", boss);
			config.addProperty("signal", eventSignalLoot.isSelected() ? "loot" : "chat");
		}
		if (dropParty)
		{
			String world = partyWorld.getText().trim();
			if (!world.isEmpty())
			{
				if (shell.taskEditor.positiveInt(world) < 0) { eventFormFailed("World must be a number, like 330.", partyWorld); return; }
				config.addProperty("world", shell.taskEditor.positiveInt(world));
			}
			config.addProperty("location", partyLocation.getText().trim());
			config.addProperty("host", partyHost.getText().trim());
			config.addProperty("notes", partyNotes.getText().trim());
		}
		JsonArray prizes = new JsonArray();
		for (int i = 0; i < prizeFields.length; i++)
		{
			long gp = parseMillions(prizeFields[i].getText());
			if (gp < 0) { eventFormFailed(place(i + 1) + " prize must be a number of millions, like 20 or 2.5.", prizeFields[i]); return; }
			if (gp > 0 && prizes.size() < i) { eventFormFailed("Fill in the prizes in order, starting with 1st.", prizeFields[i]); return; }
			if (gp > 0) prizes.add(gp);
		}
		eventFormError.setVisible(false);
		if (editingEvent && shell.currentEvent != null) plugin.organizer().updateEvent(str(shell.currentEvent, "id"), name, start, end, eventHideScores.isSelected(), eventHidden.isSelected(), "bingo".equals(type) ? null : config, prizes);
		else plugin.organizer().createEvent(name, start, end, eventHideScores.isSelected(), eventHidden.isSelected(), type, config, prizes);
	}

	private void setEventTimes(ZonedDateTime start, ZonedDateTime end)
	{
		eventStart.setDate(start == null ? LocalDate.now() : start.toLocalDate());
		eventStartTime.setText(start == null ? "" : start.format(TsgHubFormat.CLOCK_24));
		eventEnd.setDate(end == null ? eventStart.getDate() : end.toLocalDate());
		eventEndTime.setText(end == null ? "" : end.format(TsgHubFormat.CLOCK_24));
	}

	private void defaultEventEnd()
	{
		boolean dropParty = "drop-party".equals(EVENT_TYPES[Math.max(0, eventType.getSelectedIndex())]);
		if (dropParty)
		{
			eventEnd.setDate(eventStart.getDate());
			eventEndTime.setText("");
		}
		else if (eventEndTime.getText().trim().isEmpty())
		{
			eventEnd.setDate(eventStart.getDate().plusDays(7));
			eventEndTime.setText(eventStartTime.getText());
		}
	}

	private static Instant eventInstant(TsgHubDatePicker date, JTextField time)
	{
		try { return date.getDate().atTime(LocalTime.parse(time.getText().trim())).atZone(ZoneId.systemDefault()).toInstant(); }
		catch (DateTimeParseException e) { return null; }
	}

	private void updateEventFormType()
	{
		String type = EVENT_TYPES[Math.max(0, eventType.getSelectedIndex())];
		boolean dropParty = "drop-party".equals(type);
		skillRow.setVisible("skill".equals(type));
		bossRow2.setVisible("boss".equals(type));
		signalRow2.setVisible("boss".equals(type));
		partyRows.setVisible(dropParty);
		nameRow.setVisible(!"skill".equals(type) && !"boss".equals(type));
		hideScoresRow.setVisible(!dropParty);
		endTimeCaption.setText(dropParty ? "End time (optional)" : "End time (HH:MM)");
		eventZoneHint.setText(html(escape("Times are in your time zone, " + zoneLabel(ZoneId.systemDefault(), clock.instant())
			+ ". Players see them in theirs." + (dropParty ? " Leave the end time empty if there's no set end." : "")), 480));
		placeholder(eventName, dropParty ? "e.g. Halloween drop party, clan trip" : "e.g. Autumn Bingo");
		eventName.repaint();
		eventTypeHint.setText("skill".equals(type) ? "Players join with one click; most XP gained in the skill wins."
			: "boss".equals(type) ? "Players join with one click; most kills of the boss wins."
			: dropParty ? "Anything else: an announcement with the time and place. Nothing to join or track."
			: "Teams join with invite codes and complete tiles.");
		shell.revalidate();
		shell.repaint();
	}
}
