package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanRank;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

final class TsgHubPanel extends JPanel
{
	private static final int LIST_W = 270;
	private static final int DETAIL_TEXT_W = 500;
	private static final int MAX_RECONCILE_LINES = 10;

	// Order matches the index TsgHubPlugin#saveTask expects.
	private enum TaskType
	{
		MANUAL("Manual (admin reviews proof)"),
		KILL("Boss kill count"),
		DROP("Item drop"),
		ITEM_SET("Complete a set"),
		RAID("Raid completion");

		private final String label;
		TaskType(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	private static final String[][] RAID_MODES = {
		{"Chambers of Xeric", "cox", "Normal", "cox_cm", "Challenge Mode"},
		{"Theatre of Blood", "tob_entry", "Entry", "tob", "Normal", "tob_hm", "Hard Mode"},
		{"Tombs of Amascut", "toa_entry", "Entry", "toa", "Normal", "toa_expert", "Expert"},
	};

	private final TsgHubPlugin plugin;

	private final JPanel eventList = TsgHubUi.stack();
	private final CardLayout detailLayout = new CardLayout();
	private final JPanel detail = new JPanel(detailLayout);
	private final TsgHubUi.StatusLine status = new TsgHubUi.StatusLine(DETAIL_TEXT_W);

	private final JLabel eventTitle = TsgHubUi.label(" ", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont().deriveFont(20f));
	private final JLabel eventMeta = TsgHubUi.caption(" ");
	private final JLabel eventSchedule = TsgHubUi.caption(" ");
	private final TsgHubUi.WidthTrackingPanel teamsTab = new TsgHubUi.WidthTrackingPanel();
	private final TsgHubUi.WidthTrackingPanel tasksTab = new TsgHubUi.WidthTrackingPanel();
	private final TsgHubUi.WidthTrackingPanel claimsTab = new TsgHubUi.WidthTrackingPanel();
	private MaterialTabGroup tabs;
	private MaterialTab teamsTabButton;
	private MaterialTab tasksTabButton;
	private MaterialTab claimsTabButton;
	private MaterialTab leaderboardTabButton;
	private MaterialTab detailsTabButton;
	private final TsgHubUi.WidthTrackingPanel leaderboardTab = new TsgHubUi.WidthTrackingPanel();
	private final TsgHubUi.WidthTrackingPanel detailsTab = new TsgHubUi.WidthTrackingPanel();
	private final JTextField newTeamName = new JTextField();

	private final JLabel eventFormTitle = TsgHubUi.sectionTitle("New event");
	private final JLabel detectedClan = TsgHubUi.caption("");
	private final JTextField eventName = new JTextField();
	private JPanel nameRow;
	private final TsgHubDatePicker eventStart = new TsgHubDatePicker();
	private final TsgHubDatePicker eventEnd = new TsgHubDatePicker();
	private final JTextField eventStartTime = new JTextField();
	private final JTextField eventEndTime = new JTextField();
	private final JLabel eventZoneHint = TsgHubUi.caption("");
	private final JCheckBox eventHideScores = new JCheckBox("Hide scores from players");
	private final JCheckBox eventHidden = new JCheckBox("Hide event from players");
	private final JButton publishEvent = TsgHubUi.primaryButton("Publish");
	private final JButton endEvent = TsgHubUi.button("End event");
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
	private final JLabel eventTypeHint = TsgHubUi.caption("");
	private JPanel skillRow, bossRow2, signalRow2, partyRows, hideScoresRow;
	private JLabel endTimeCaption;
	private final JLabel eventFormError = TsgHubUi.label("", TsgHubUi.ERROR, FontManager.getRunescapeSmallFont());
	private final JButton eventFormSave = TsgHubUi.primaryButton("Create event");
	private boolean editingEvent;

	private final JLabel taskEditorTitle = TsgHubUi.sectionTitle("New task");
	private final JTextField taskTitle = new JTextField();
	private final JTextField taskDescription = new JTextField();
	private final JComboBox<TaskType> taskType = new JComboBox<>(TaskType.values());
	private final JRadioButton scopeTeam = new JRadioButton("Team: pooled progress", true);
	private final JRadioButton scopeIndividual = new JRadioButton("Everyone: each member completes it");
	private final JRadioButton scopeSolo = new JRadioButton("Solo: one member, alone");
	private final TsgHubBossPicker taskBoss = new TsgHubBossPicker("Boss", () -> this.targetCount.requestFocusInWindow());
	private final JRadioButton signalKc = new JRadioButton("Kill count message (recommended)", true);
	private final JRadioButton signalLoot = new JRadioButton("Loot drop (bosses without a KC message)");
	private final JComboBox<String> raidChoice = new JComboBox<>();
	private final JPanel raidModeChoices = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	// "" means any mode of that raid.
	private final Map<String, JRadioButton> raidModeButtons = new LinkedHashMap<>();
	private final JRadioButton dropSpecific = new JRadioButton("Specific items", true);
	private final JRadioButton dropJar = new JRadioButton("Any jar");
	private final JRadioButton dropPet = new JRadioButton("Any boss pet");
	private final JComboBox<Object> presetChoice = new JComboBox<>();
	private final JTextField itemSearch = new JTextField();
	private final DefaultListModel<TsgHubPlugin.ItemSuggestion> itemResults = new DefaultListModel<>();
	private final JList<TsgHubPlugin.ItemSuggestion> itemResultList = new JList<>(itemResults);
	private final JLabel itemSearchHint = TsgHubUi.caption("");
	private final JComboBox<String> activeGroup = new JComboBox<>();
	private final JPanel selectedItemsPanel = TsgHubUi.stack();
	private final List<List<TsgHubPlugin.ItemSuggestion>> itemGroups = new ArrayList<>();
	private final JTextField targetCount = new JTextField("1");
	private final JLabel targetCountCaption = TsgHubUi.caption("How many");
	private final JTextField taskPoints = new JTextField("1");
	private final JLabel taskFormError = TsgHubUi.label("", TsgHubUi.ERROR, FontManager.getRunescapeSmallFont());
	private final JButton taskSave = TsgHubUi.primaryButton("Add task");
	private final Map<Integer, ImageIcon> itemIcons = new HashMap<>();
	private final Timer searchDebounce = new Timer(300, e -> runItemSearch());
	private final JPanel numbersRow = TsgHubUi.panel(new GridLayout(1, 2, 10, 0));
	private JPanel pointsRow;
	private JPanel scopeRow, bossRow, signalRow, raidRow, dropKindRow, presetRow, groupRow, itemPickerRow, countRow;
	private long itemSearchRequestId;
	private String editingTaskId = "";

	private JsonObject currentEvent;
	private String selectedEventId = "";
	private String viewedTeamId = "";
	private JsonArray managedEvents = new JsonArray();

	TsgHubPanel(TsgHubPlugin plugin)
	{
		this.plugin = plugin;
		itemGroups.add(new ArrayList<>());
		setLayout(new BorderLayout());
		setBackground(TsgHubUi.BACKGROUND);

		add(buildEventListColumn(), BorderLayout.WEST);

		detail.setOpaque(false);
		detail.add(TsgHubUi.north(TsgHubUi.emptyState("Pick an event", "Choose an event on the left, or create a new one.", 320)), "empty");
		detail.add(buildEventForm(), "event-form");
		detail.add(buildEventWorkspace(), "event");
		detail.add(buildTaskEditor(), "task-editor");

		JPanel right = TsgHubUi.panel(new BorderLayout());
		right.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		right.add(detail, BorderLayout.CENTER);
		right.add(status, BorderLayout.SOUTH);
		add(right, BorderLayout.CENTER);

		setDetectedClanName(plugin.getDetectedClanName(), plugin.getDetectedClanRank());
		setManagedEvents(new JsonArray());
	}

	private JComponent buildEventListColumn()
	{
		JPanel column = new JPanel(new BorderLayout(0, 8));
		column.setBackground(TsgHubUi.CARD);
		column.setPreferredSize(new Dimension(LIST_W, 100));
		column.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

		JButton refresh = TsgHubUi.iconButton(new TsgHubUi.RefreshIcon(), "Refresh events");
		refresh.addActionListener(e -> plugin.loadManagedEvents());
		JPanel header = TsgHubUi.row(TsgHubUi.label("Events", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), refresh);

		JButton create = TsgHubUi.primaryButton("New event");
		create.addActionListener(e -> beginCreateEvent());

		JPanel top = TsgHubUi.stack();
		top.add(TsgHubUi.fitHeight(header));
		top.add(Box.createVerticalStrut(8));
		top.add(TsgHubUi.fitHeight(create));
		column.add(top, BorderLayout.NORTH);

		TsgHubUi.WidthTrackingPanel listWrap = new TsgHubUi.WidthTrackingPanel();
		listWrap.add(eventList);
		column.add(TsgHubUi.scroll(listWrap), BorderLayout.CENTER);
		return column;
	}

	private JComponent buildEventForm()
	{
		JPanel form = TsgHubUi.stack();
		form.add(eventFormTitle);
		form.add(detectedClan);
		form.add(Box.createVerticalStrut(8));
		JPanel typeField = field("Event type", eventType);
		typeField.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
		form.add(typeField);
		eventTypeHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		form.add(eventTypeHint);
		nameRow = field("Event name", eventName);
		form.add(nameRow);
		JPanel dates = TsgHubUi.panel(new GridLayout(2, 2, 10, 0));
		JPanel endTimeField = field("End time (HH:MM)", eventEndTime);
		endTimeCaption = (JLabel) endTimeField.getComponent(0);
		// Moving the start past the end moves the end along.
		eventStart.onChange(() -> { if (eventEnd.getDate().isBefore(eventStart.getDate())) eventEnd.setDate(eventStart.getDate()); });
		dates.add(field("Start date", eventStart));
		dates.add(field("Start time (HH:MM)", eventStartTime));
		dates.add(field("End date", eventEnd));
		dates.add(endTimeField);
		form.add(TsgHubUi.fitHeight(dates));
		eventZoneHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		form.add(eventZoneHint);

		for (Skill skill : Skill.values())
			if (!"OVERALL".equals(skill.name())) eventSkill.addItem(TsgHubUi.skillName(skill.name()));
		skillRow = field("Skill", eventSkill);
		form.add(skillRow);
		bossRow2 = eventBoss;
		form.add(bossRow2);
		signalRow2 = radioField("Count kills using", eventSignalKc, eventSignalLoot);
		form.add(signalRow2);

		partyRows = TsgHubUi.stack();
		partyRows.add(field("World", partyWorld));
		partyRows.add(field("Location", partyLocation));
		partyRows.add(field("Host", partyHost));
		partyRows.add(field("Notes (optional)", partyNotes));
		form.add(partyRows);

		JPanel prizeGrid = TsgHubUi.panel(new GridLayout(1, prizeFields.length, 10, 0));
		for (int i = 0; i < prizeFields.length; i++)
		{
			prizeGrid.add(field(TsgHubUi.place(i + 1) + " prize (M)", prizeFields[i]));
			placeholder(prizeFields[i], i == 0 ? "e.g. 20" : "optional");
		}
		form.add(TsgHubUi.fitHeight(prizeGrid));
		JLabel prizeHint = TsgHubUi.caption("In millions of GP: 20 is 20M, 1500 is 1.5B, 0.5 is 500K. Leave blank for no prize.");
		prizeHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		form.add(prizeHint);

		hideScoresRow = TsgHubUi.stack();
		hideScoresRow.add(TsgHubUi.plain(eventHideScores, TsgHubUi.TEXT));
		hideScoresRow.add(TsgHubUi.wrapped("Players still see their own progress, but not other players' or teams' scores and ranks. You can change this any time.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), 480));
		form.add(hideScoresRow);
		form.add(Box.createVerticalStrut(6));
		form.add(TsgHubUi.plain(eventHidden, TsgHubUi.TEXT));
		form.add(TsgHubUi.wrapped("Only admins can see and join it, so you can set it up and test it first. Publish it when it's ready. Players who already joined keep access.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), 480));
		form.add(Box.createVerticalStrut(10));
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
		form.add(Box.createVerticalStrut(10));

		JButton cancel = TsgHubUi.button("Cancel");
		cancel.addActionListener(e -> {
			if (editingEvent && currentEvent != null) detailLayout.show(detail, "event");
			else detailLayout.show(detail, currentEvent == null ? "empty" : "event");
		});
		placeholder(eventName, "e.g. Autumn Bingo");
		eventFormSave.addActionListener(e -> submitEventForm());
		eventName.addActionListener(e -> submitEventForm());
		form.add(footer(cancel, eventFormSave));
		return narrowPage(form);
	}

	private JComponent buildEventWorkspace()
	{
		JPanel workspace = TsgHubUi.panel(new BorderLayout(0, 8));
		JPanel header = TsgHubUi.panel(new BorderLayout(10, 0));
		JPanel titles = TsgHubUi.stack();
		titles.add(eventTitle);
		titles.add(Box.createVerticalStrut(2));
		titles.add(eventMeta);
		titles.add(Box.createVerticalStrut(2));
		titles.add(eventSchedule);
		header.add(titles, BorderLayout.CENTER);
		JButton edit = TsgHubUi.button("Edit event");
		edit.addActionListener(e -> beginEditEvent());
		JButton deleteEvent = TsgHubUi.iconButton(new TsgHubUi.TrashIcon(), "Delete event");
		deleteEvent.addActionListener(e -> confirmDeleteEvent());
		publishEvent.setToolTipText("Let players see and join this event");
		publishEvent.addActionListener(e -> { if (currentEvent != null) plugin.publishEvent(currentEvent); });
		JPanel editButtons = TsgHubUi.panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		endEvent.setToolTipText("Mark this event as finished and let the clan know");
		endEvent.addActionListener(e -> confirmEndEvent());
		editButtons.add(publishEvent);
		editButtons.add(endEvent);
		editButtons.add(edit);
		editButtons.add(deleteEvent);
		header.add(TsgHubUi.north(editButtons), BorderLayout.EAST);

		JPanel display = TsgHubUi.panel(new BorderLayout());
		tabs = new MaterialTabGroup(display);
		tabs.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 0));
		tabs.setOpaque(false);
		teamsTabButton = tab("Teams", teamsTab);
		tasksTabButton = tab("Tasks", tasksTab);
		claimsTabButton = tab("Claims", claimsTab);
		leaderboardTabButton = tab("Leaderboard", leaderboardTab);
		detailsTabButton = tab("Details", detailsTab);
		tabs.select(teamsTabButton);

		JPanel north = TsgHubUi.stack();
		north.add(TsgHubUi.fitHeight(header));
		north.add(Box.createVerticalStrut(10));
		JPanel tabRow = TsgHubUi.panel(new BorderLayout());
		tabRow.setBorder(TsgHubUi.bottomRule());
		tabRow.add(tabs, BorderLayout.WEST);
		north.add(TsgHubUi.fitHeight(tabRow));
		workspace.add(north, BorderLayout.NORTH);
		display.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		workspace.add(display, BorderLayout.CENTER);

		newTeamName.addActionListener(e -> addTeam());
		placeholder(newTeamName, "e.g. Dragon Slayers");
		return workspace;
	}

	private MaterialTab tab(String name, JComponent content)
	{
		MaterialTab tab = new MaterialTab(name, tabs, TsgHubUi.scroll(content));
		tabs.addTab(tab);
		return tab;
	}

	private JComponent buildTaskEditor()
	{
		JPanel form = TsgHubUi.stack();
		form.add(taskEditorTitle);
		form.add(field("Title", taskTitle));
		form.add(field("Description (optional)", taskDescription));
		form.add(field("Type", taskType));

		scopeRow = radioField("Progress", scopeTeam, scopeIndividual, scopeSolo);
		scopeTeam.setToolTipText("Everyone's progress adds up. 50 kills can be split across the team.");
		scopeIndividual.setToolTipText("Each member has to reach the target on their own. The tile completes when all of them have.");
		scopeSolo.setToolTipText("Members don't share progress. The tile completes when any one member reaches the target alone.");
		form.add(scopeRow);

		bossRow = taskBoss;
		form.add(bossRow);
		signalRow = radioField("Track kills using", signalKc, signalLoot);
		form.add(signalRow);

		raidRow = buildRaidPicker();
		form.add(raidRow);

		dropKindRow = radioField("What counts", dropSpecific, dropJar, dropPet);
		form.add(dropKindRow);
		activeGroup.addActionListener(e -> { if (groupRow != null && groupRow.isVisible()) renderSelectedItems(); });
		presetRow = buildPresetPicker();
		form.add(presetRow);

		JButton newGroup = TsgHubUi.button("New set");
		newGroup.addActionListener(e -> {
			itemGroups.add(new ArrayList<>());
			refreshGroupChoices(itemGroups.size() - 1);
			renderSelectedItems();
			itemSearch.requestFocusInWindow();
		});
		groupRow = field("Or build your own: search adds items to", TsgHubUi.row(activeGroup, newGroup));

		itemPickerRow = buildItemPicker();
		form.add(groupRow);
		form.add(itemPickerRow);

		countRow = TsgHubUi.panel(new BorderLayout(0, 3));
		countRow.add(targetCountCaption, BorderLayout.NORTH);
		countRow.add(targetCount, BorderLayout.CENTER);
		countRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		pointsRow = field("Points", taskPoints);
		numbersRow.add(countRow);
		numbersRow.add(pointsRow);
		form.add(TsgHubUi.fitHeight(numbersRow));

		taskFormError.setVisible(false);
		form.add(taskFormError);
		form.add(Box.createVerticalStrut(10));
		JButton cancel = TsgHubUi.button("Cancel");
		cancel.addActionListener(e -> closeTaskEditor());
		taskSave.addActionListener(e -> submitTask());
		form.add(footer(cancel, taskSave));

		placeholder(taskTitle, "e.g. Kill Vorkath 50 times");
		placeholder(taskDescription, "Shown to players under the title");
		placeholder(itemSearch, "Search items, e.g. Dragon warhammer");
		taskType.addActionListener(e -> updateTaskVisibility());
		for (JRadioButton radio : new JRadioButton[] {dropSpecific, dropJar, dropPet})
			radio.addActionListener(e -> updateTaskVisibility());
		updateTaskVisibility();
		return narrowPage(form);
	}

	private JPanel buildPresetPicker()
	{
		presetChoice.addItem("Choose a set...");
		for (TsgHubItemSets.Preset preset : TsgHubItemSets.PRESETS) presetChoice.addItem(preset);
		JButton add = TsgHubUi.button("Add");
		add.addActionListener(e -> {
			if (presetChoice.getSelectedItem() instanceof TsgHubItemSets.Preset) addPreset((TsgHubItemSets.Preset) presetChoice.getSelectedItem());
		});
		JPanel row = field("Sets that count", TsgHubUi.row(presetChoice, add));
		row.add(TsgHubUi.caption("Completing any one set finishes the task. Teammates can each contribute pieces."), BorderLayout.SOUTH);
		return TsgHubUi.fitHeight(row);
	}

	private void addPreset(TsgHubItemSets.Preset preset)
	{
		if (itemGroups.stream().allMatch(List::isEmpty)) itemGroups.clear();
		int added = 0;
		for (TsgHubItemSets.ItemSet set : preset.sets)
		{
			boolean duplicate = itemGroups.stream().anyMatch(group -> TsgHubItemSets.nameFor(ids(group)).equals(set.name));
			if (duplicate) continue;
			itemGroups.add(new ArrayList<>(set.items));
			added++;
		}
		if (itemGroups.isEmpty()) itemGroups.add(new ArrayList<>());
		refreshGroupChoices(itemGroups.size() - 1);
		presetChoice.setSelectedIndex(0);
		itemSearchHint.setText(added == 0 ? "Those sets are already added." : "Added " + preset.label + ".");
		if (taskTitle.getText().trim().isEmpty())
		{
			String base = preset.label.replaceAll(" \\(.*\\)", "");
			taskTitle.setText(preset.sets.size() > 1
				? "Complete " + Character.toLowerCase(base.charAt(0)) + base.substring(1)
				: "Complete " + (preset.sets.get(0).name.endsWith("log") ? "the " + preset.sets.get(0).name : preset.sets.get(0).name + " set"));
		}
		renderSelectedItems();
	}

	private static List<Integer> ids(List<TsgHubPlugin.ItemSuggestion> items)
	{
		List<Integer> ids = new ArrayList<>();
		for (TsgHubPlugin.ItemSuggestion item : items) ids.add(item.id);
		return ids;
	}

	private JPanel buildRaidPicker()
	{
		for (String[] raid : RAID_MODES) raidChoice.addItem(raid[0]);
		raidChoice.addActionListener(e -> rebuildRaidModes(""));
		raidModeChoices.setOpaque(false);
		rebuildRaidModes("");

		JPanel picker = TsgHubUi.stack();
		picker.add(field("Raid", raidChoice));
		picker.add(field("Mode", raidModeChoices));
		picker.add(TsgHubUi.caption("Only raids with clan members count."));
		picker.add(Box.createVerticalStrut(8));
		JButton greenLog = TsgHubUi.button("Make a green log task for this raid instead");
		greenLog.setToolTipText("Switches to Complete a set with every unique from this raid");
		greenLog.addActionListener(e -> switchToGreenLog());
		JPanel greenLogRow = TsgHubUi.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		greenLogRow.add(greenLog);
		picker.add(TsgHubUi.fitHeight(greenLogRow));
		picker.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return TsgHubUi.fitHeight(picker);
	}

	private void rebuildRaidModes(String selectKey)
	{
		String[] raid = RAID_MODES[Math.max(0, raidChoice.getSelectedIndex())];
		raidModeChoices.removeAll();
		raidModeButtons.clear();
		ButtonGroup group = new ButtonGroup();
		JRadioButton any = modeRadio("Any mode", group);
		raidModeButtons.put("", any);
		for (int i = 1; i < raid.length; i += 2) raidModeButtons.put(raid[i], modeRadio(raid[i + 1], group));
		JRadioButton selected = raidModeButtons.getOrDefault(selectKey, any);
		selected.setSelected(true);
		raidModeButtons.values().forEach(radio -> {
			raidModeChoices.add(radio);
			raidModeChoices.add(Box.createHorizontalStrut(10));
		});
		raidModeChoices.revalidate();
		raidModeChoices.repaint();
	}

	private static JRadioButton modeRadio(String label, ButtonGroup group)
	{
		JRadioButton radio = TsgHubUi.plain(new JRadioButton(label), TsgHubUi.TEXT);
		group.add(radio);
		return radio;
	}

	private void selectRaidModes(List<String> modes)
	{
		int raidIndex = 0;
		for (int r = 0; r < RAID_MODES.length; r++)
			for (int i = 1; i < RAID_MODES[r].length; i += 2)
				if (!modes.isEmpty() && RAID_MODES[r][i].equals(modes.get(0))) raidIndex = r;
		raidChoice.setSelectedIndex(raidIndex);
		rebuildRaidModes(modes.size() == 1 ? modes.get(0) : "");
	}

	private void switchToGreenLog()
	{
		String raid = (String) raidChoice.getSelectedItem();
		TsgHubItemSets.Preset preset = TsgHubItemSets.greenLogFor(raid);
		if (preset == null) return;
		taskType.setSelectedItem(TaskType.ITEM_SET);
		resetItems();
		String previousTitle = taskTitle.getText().trim();
		taskTitle.setText("");
		addPreset(preset);
		if (!previousTitle.isEmpty() && !previousTitle.toLowerCase(Locale.ROOT).contains("complete")) taskTitle.setText(previousTitle);
	}

	private JPanel buildItemPicker()
	{
		JPanel picker = TsgHubUi.stack();
		picker.add(TsgHubUi.caption("Items"));
		picker.add(Box.createVerticalStrut(3));
		itemSearch.setToolTipText("Type at least 2 letters to search RuneLite's item catalog");
		itemSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { searchDebounce.restart(); }
			@Override public void removeUpdate(DocumentEvent e) { searchDebounce.restart(); }
			@Override public void changedUpdate(DocumentEvent e) { searchDebounce.restart(); }
		});
		searchDebounce.setRepeats(false);
		itemSearch.addActionListener(e -> {
			if (!itemResults.isEmpty()) addItem(itemResults.get(Math.max(0, itemResultList.getSelectedIndex())));
		});
		picker.add(TsgHubUi.fitHeight(itemSearch));
		picker.add(itemSearchHint);

		itemResultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		itemResultList.setVisibleRowCount(5);
		itemResultList.setBackground(TsgHubUi.CARD);
		itemResultList.setCellRenderer((list, value, index, selected, focus) -> {
			JLabel row = TsgHubUi.listRow(value == null ? "" : value.name, selected);
			if (value != null) row.setIcon(iconFor(value.id, list));
			return row;
		});
		itemResultList.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e)
			{
				int index = itemResultList.locationToIndex(e.getPoint());
				if (index >= 0 && itemResultList.getCellBounds(index, index).contains(e.getPoint())) addItem(itemResults.get(index));
			}
		});
		JScrollPane resultsScroll = new JScrollPane(itemResultList);
		resultsScroll.setBorder(BorderFactory.createLineBorder(TsgHubUi.BORDER));
		resultsScroll.setVisible(false);
		resultsScroll.setName("results");
		picker.add(resultsScroll);
		picker.add(Box.createVerticalStrut(6));
		picker.add(selectedItemsPanel);
		picker.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return picker;
	}

	void setStatus(String text, TsgHubUi.Tone tone)
	{
		status.show(text, tone);
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

	void setManagedEvents(JsonArray events)
	{
		managedEvents = events;
		eventList.removeAll();
		List<JsonObject> sorted = TsgHubUi.objects(events);
		sorted.sort(Comparator
			.comparingInt((JsonObject e) -> statusOrder(TsgHubUi.str(e, "status")))
			.thenComparing(TsgHubUi::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
		String section = null;
		for (JsonObject event : sorted)
		{
			String id = TsgHubUi.str(event, "id");
			String next = TsgHubUi.statusLabel(TsgHubUi.str(event, "status"));
			if (!next.equals(section))
			{
				eventList.add(TsgHubUi.listHeading(next, section == null));
				section = next;
			}
			JPanel card = TsgHubUi.card();
			boolean selected = id.equals(selectedEventId);
			if (selected) card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, TsgHubUi.ACCENT), BorderFactory.createEmptyBorder(6, 5, 6, 7)));
			card.setBackground(selected ? TsgHubUi.CARD_HOVER : TsgHubUi.BACKGROUND);
			card.add(TsgHubUi.eventLines(event, plugin::getCoinImage), BorderLayout.CENTER);
			card.setToolTipText(TsgHubUi.eventWhen(event));
			TsgHubUi.clickable(card, () -> {
				selectedEventId = id;
				setManagedEvents(managedEvents);
				plugin.selectEvent(id);
			});
			eventList.add(TsgHubUi.fitHeight(card));
			eventList.add(Box.createVerticalStrut(6));
		}
		if (sorted.isEmpty())
		{
			eventList.add(TsgHubUi.wrapped("No events yet. Create one to get started.", TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), LIST_W - 30));
		}
		eventList.revalidate();
		eventList.repaint();
	}

	void openOrganizerEvent(JsonObject event)
	{
		boolean sameEvent = currentEvent != null && TsgHubUi.str(currentEvent, "id").equals(TsgHubUi.str(event, "id"));
		selectedEventId = TsgHubUi.str(event, "id");
		showEvent(event);
		if (!sameEvent)
		{
			viewedTeamId = "";
			String type = TsgHubUi.str(event, "type");
			tabs.select("skill".equals(type) || "boss".equals(type) ? leaderboardTabButton : "drop-party".equals(type) ? detailsTabButton : teamsTabButton);
		}
		detailLayout.show(detail, "event");
		setManagedEvents(managedEvents);
	}

	boolean editingText()
	{
		return hasFocusedTextComponent(this);
	}

	private boolean hasFocusedTextComponent(Component component)
	{
		if (component instanceof JTextComponent && component.isFocusOwner()) return true;
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents()) if (hasFocusedTextComponent(child)) return true;
		}
		return false;
	}

	void showEvent(JsonObject event)
	{
		currentEvent = event.deepCopy();
		eventTitle.setText(TsgHubUi.eventName(event));
		String state = TsgHubUi.str(event, "status");
		String type = TsgHubUi.str(event, "type");
		boolean bingo = type.isEmpty() || "bingo".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		String counts = bingo ? TsgHubUi.array(event, "teams").size() + " teams · " + TsgHubUi.array(event, "tasks").size() + " tasks"
			: competition ? TsgHubUi.integer(event, "participants", 0) + ("scheduled".equals(state) ? " signed up" : " taking part") : "";
		String relative = TsgHubUi.eventRelative(event);
		String detail = bingo ? "Bingo" : TsgHubUi.eventDetail(event);
		List<Long> prizes = TsgHubUi.eventPrizes(event);
		eventMeta.setText((detail.isEmpty() ? "" : detail + " · ") + (relative.isEmpty() ? TsgHubUi.statusLabel(state) : TsgHubUi.capitalize(relative))
			+ (counts.isEmpty() ? "" : " · " + counts)
			+ (TsgHubUi.bool(event, "hideScores") ? " · Scores hidden from players" : "")
			+ (TsgHubUi.bool(event, "hidden") ? " · Hidden from players" : ""));
		eventSchedule.setText(TsgHubUi.eventWhen(event) + (prizes.isEmpty() ? "" : " · Prize pool " + TsgHubUi.formatGp(TsgHubUi.prizeTotal(prizes)) + " (" + TsgHubUi.prizeSummary(prizes) + ")"));
		publishEvent.setVisible(TsgHubUi.bool(event, "hidden"));
		endEvent.setVisible("drop-party".equals(type) && !"ended".equals(state));
		teamsTabButton.setVisible(bingo);
		tasksTabButton.setVisible(bingo);
		claimsTabButton.setVisible(bingo);
		leaderboardTabButton.setVisible(competition);
		detailsTabButton.setVisible("drop-party".equals(type));
		renderTeams();
		renderTasks();
		renderClaims();
		renderLeaderboard();
		renderDetails();
	}

	void eventCreated(JsonObject event)
	{
		eventName.setText("");
		openOrganizerEvent(event);
	}

	void teamCreated()
	{
		newTeamName.setText("");
		newTeamName.requestFocusInWindow();
	}

	void eventFormFailed(String message)
	{
		showFormError(eventFormError, message);
	}

	private void eventFormFailed(String message, JComponent focus)
	{
		eventFormFailed(message);
		focus.requestFocusInWindow();
	}

	void taskFormFailed(String message)
	{
		showFormError(taskFormError, message);
	}

	void taskDeleted(String taskId)
	{
		if (taskId.equals(editingTaskId)) finishTaskEdit();
	}

	void eventDeleted(String eventId)
	{
		if (currentEvent != null && TsgHubUi.str(currentEvent, "id").equals(eventId))
		{
			currentEvent = null;
			selectedEventId = "";
			viewedTeamId = "";
			detailLayout.show(detail, "empty");
		}
	}

	private void confirmEndEvent()
	{
		if (currentEvent == null) return;
		String message = "End \"" + TsgHubUi.eventName(currentEvent) + "\" now?\n\n"
			+ "It moves to ended and online clanmates get an announcement that it's over.";
		if (TsgHubUi.confirmDelete(this, "End event", message, "End event")) plugin.endEvent(TsgHubUi.str(currentEvent, "id"));
	}

	private void confirmDeleteEvent()
	{
		if (currentEvent == null) return;
		int teams = TsgHubUi.array(currentEvent, "teams").size();
		int tasks = TsgHubUi.array(currentEvent, "tasks").size();
		int members = TsgHubUi.array(currentEvent, "members").size();
		String message = "Delete \"" + TsgHubUi.eventName(currentEvent) + "\"?\n\n"
			+ "This permanently removes its " + plural(teams, "team") + ", " + plural(tasks, "task") + " and all progress.\n"
			+ (members > 0 ? plural(members, "player") + " will be disconnected.\n" : "")
			+ "This can't be undone.";
		if (TsgHubUi.confirmDelete(this, "Delete event", message, "Delete event")) plugin.deleteEvent(TsgHubUi.str(currentEvent, "id"));
	}

	private void confirmDeleteTeam(JsonObject team, int members)
	{
		String message = "Delete team \"" + TsgHubUi.str(team, "name") + "\"?\n\n"
			+ (members > 0 ? "Its " + plural(members, "member") + " will be removed from the event, and " : "")
			+ (members > 0 ? "its" : "Its") + " progress will be deleted. Its invite code stops working.\n"
			+ "This can't be undone.";
		if (TsgHubUi.confirmDelete(this, "Delete team", message, "Delete team")) plugin.deleteTeam(TsgHubUi.str(team, "id"));
	}

	private static boolean reconcilable(JsonObject task)
	{
		JsonObject config = TsgHubUi.object(task, "config");
		String type = TsgHubUi.str(task, "type");
		return "drop".equals(type) || "kill".equals(type) && !"chat".equals(TsgHubUi.str(config, "signal"));
	}

	void confirmReconcile(String taskId, JsonObject preview)
	{
		JsonArray claims = TsgHubUi.array(preview, "claims");
		if (claims.size() == 0)
		{
			JOptionPane.showMessageDialog(this, "No logged loot to credit for this task.", "Reconcile", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		StringBuilder message = new StringBuilder("Credit " + claims.size() + (claims.size() == 1 ? " match" : " matches") + " from the loot log to this task?\n\n");
		int shown = Math.min(claims.size(), MAX_RECONCILE_LINES);
		for (int i = 0; i < shown; i++)
		{
			JsonObject claim = claims.get(i).getAsJsonObject();
			message.append(TsgHubUi.str(claim, "displayName")).append(" (").append(TsgHubUi.str(claim, "teamName")).append("): ")
				.append(TsgHubUi.str(claim, "name")).append(" x").append(TsgHubUi.integer(claim, "quantity", 1)).append("\n");
		}
		if (claims.size() > shown) message.append("...and ").append(claims.size() - shown).append(" more\n");
		int choice = JOptionPane.showConfirmDialog(this, message.toString(), "Reconcile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice == JOptionPane.OK_OPTION) plugin.reconcileTask(taskId, false);
	}

	private void confirmDeleteTask(JsonObject task)
	{
		String message = "Delete task \"" + TsgHubUi.str(task, "title") + "\"?\n\n"
			+ "Every team's progress and claims for it will be deleted.\nThis can't be undone.";
		if (TsgHubUi.confirmDelete(this, "Delete task", message, "Delete task")) plugin.deleteTask(TsgHubUi.str(task, "id"));
	}

	private static String plural(int count, String noun)
	{
		return count + " " + noun + (count == 1 ? "" : "s");
	}

	void finishTaskEdit()
	{
		closeTaskEditor();
		tabs.select(tasksTabButton);
	}

	private void renderTeams()
	{
		teamsTab.removeAll();
		if (!viewedTeamId.isEmpty() && findTeam(viewedTeamId) != null)
		{
			renderTeamDetail(findTeam(viewedTeamId));
			refresh(teamsTab);
			return;
		}
		viewedTeamId = "";
		JsonArray teams = TsgHubUi.array(currentEvent, "teams");
		JsonArray scores = TsgHubUi.array(currentEvent, "teamScores");
		int totalTasks = TsgHubUi.array(currentEvent, "tasks").size();
		List<JsonObject> ranked = TsgHubUi.objects(teams);
		ranked.sort(Comparator.comparingInt((JsonObject t) -> TsgHubUi.integer(TsgHubUi.scoreFor(scores, TsgHubUi.str(t, "id")), "points", 0)).reversed()
			.thenComparing(t -> TsgHubUi.str(t, "name"), String.CASE_INSENSITIVE_ORDER));

		if (ranked.isEmpty())
		{
			teamsTab.add(TsgHubUi.wrapped("No teams yet. Add your first team below, then share its code with the players on it.", TsgHubUi.MUTED, FontManager.getRunescapeFont(), DETAIL_TEXT_W));
			teamsTab.add(Box.createVerticalStrut(8));
		}
		for (int i = 0; i < ranked.size(); i++)
		{
			JsonObject team = ranked.get(i);
			String teamId = TsgHubUi.str(team, "id");
			JsonObject score = TsgHubUi.scoreFor(scores, teamId);
			int members = countMembers(teamId);

			JPanel card = TsgHubUi.card();
			card.setLayout(new BorderLayout(10, 4));
			card.add(TsgHubUi.rankLabel(i + 1, FontManager.getRunescapeBoldFont().deriveFont(18f), 20), BorderLayout.WEST);
			JPanel text = TsgHubUi.stack();
			text.add(TsgHubUi.label(TsgHubUi.str(team, "name"), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
			text.add(TsgHubUi.caption(TsgHubUi.integer(score, "points", 0) + " pts · " + TsgHubUi.integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks · "
				+ members + (members == 1 ? " member" : " members")));
			card.add(text, BorderLayout.CENTER);

			JPanel actions = TsgHubUi.panel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			String code = TsgHubUi.str(team, "inviteCode");
			if (!code.isEmpty())
			{
				JLabel codeLabel = TsgHubUi.label(code, TsgHubUi.ACCENT, new Font(Font.MONOSPACED, Font.BOLD, 13));
				codeLabel.setToolTipText("Team invite code");
				actions.add(codeLabel);
				JButton copy = TsgHubUi.button("Copy code");
				copy.addActionListener(e -> {
					TsgHubUi.copyToClipboard(code);
					TsgHubUi.flashText(copy, "Copied!", 1500);
				});
				actions.add(copy);
			}
			JButton rename = TsgHubUi.button("Rename");
			rename.addActionListener(e -> promptRename(teamId, TsgHubUi.str(team, "name")));
			actions.add(rename);
			JButton view = TsgHubUi.button("Details");
			view.addActionListener(e -> {
				viewedTeamId = teamId;
				renderTeams();
			});
			actions.add(view);
			JButton delete = TsgHubUi.iconButton(new TsgHubUi.TrashIcon(), "Delete team");
			delete.addActionListener(e -> confirmDeleteTeam(team, members));
			actions.add(delete);
			card.add(actions, BorderLayout.EAST);
			teamsTab.add(TsgHubUi.fitHeight(card));
			teamsTab.add(Box.createVerticalStrut(5));
		}

		teamsTab.add(Box.createVerticalStrut(8));
		JButton add = TsgHubUi.button("Add team");
		add.addActionListener(e -> addTeam());
		JPanel addField = TsgHubUi.panel(new BorderLayout(0, 3));
		addField.add(TsgHubUi.caption("New team name"), BorderLayout.NORTH);
		addField.add(TsgHubUi.row(newTeamName, add), BorderLayout.CENTER);
		teamsTab.add(TsgHubUi.fitHeight(addField));
		refresh(teamsTab);
	}

	private void renderTeamDetail(JsonObject team)
	{
		String teamId = TsgHubUi.str(team, "id");
		JButton back = TsgHubUi.button("All teams");
		back.setIcon(new TsgHubUi.BackIcon());
		back.addActionListener(e -> {
			viewedTeamId = "";
			renderTeams();
		});
		teamsTab.add(back);
		teamsTab.add(Box.createVerticalStrut(8));
		JsonObject score = TsgHubUi.scoreFor(TsgHubUi.array(currentEvent, "teamScores"), teamId);
		teamsTab.add(TsgHubUi.label(TsgHubUi.str(team, "name"), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont().deriveFont(18f)));
		String code = TsgHubUi.str(team, "inviteCode");
		teamsTab.add(TsgHubUi.caption(TsgHubUi.integer(score, "points", 0) + " pts" + (code.isEmpty() ? "" : " · code " + code)));

		teamsTab.add(TsgHubUi.sectionTitle("Members"));
		List<String> roster = new ArrayList<>();
		for (JsonObject member : TsgHubUi.objects(TsgHubUi.array(currentEvent, "members")))
			if (TsgHubUi.str(member, "teamId").equals(teamId)) roster.add(TsgHubUi.str(member, "displayName"));
		roster.sort(String.CASE_INSENSITIVE_ORDER);
		teamsTab.add(TsgHubUi.wrapped(roster.isEmpty() ? "Nobody has joined yet. Share the team code above." : String.join(", ", roster), TsgHubUi.TEXT, FontManager.getRunescapeFont(), DETAIL_TEXT_W));

		teamsTab.add(TsgHubUi.sectionTitle("Task progress"));
		JsonArray progressRows = TsgHubUi.array(score, "tasks");
		JsonArray tasks = TsgHubUi.array(currentEvent, "tasks");
		if (tasks.size() == 0) teamsTab.add(TsgHubUi.label("No tasks yet.", TsgHubUi.MUTED, FontManager.getRunescapeFont()));
		for (JsonObject task : TsgHubUi.objects(tasks))
		{
			String taskId = TsgHubUi.str(task, "id");
			JsonObject progress = TsgHubUi.progressFor(progressRows, taskId);
			boolean completed = TsgHubUi.bool(progress, "completed");

			JPanel card = TsgHubUi.card();
			JPanel text = TsgHubUi.stack();
			JLabel name = TsgHubUi.label(TsgHubUi.str(task, "title"), completed ? TsgHubUi.MUTED : TsgHubUi.TEXT, FontManager.getRunescapeBoldFont());
			if (completed) name.setIcon(new TsgHubUi.CheckIcon());
			text.add(name);
			Font small = FontManager.getRunescapeSmallFont();
			String progressScope = TsgHubUi.str(progress, "scope");
			boolean perMember = "individual".equals(progressScope) || "solo".equals(progressScope);
			if (completed)
			{
				String by = TsgHubUi.str(progress, "completedBy");
				boolean creditedToOrganizer = TsgHubUi.bool(progress, "override") && !TsgHubUi.bool(progress, "overrideCredited");
				text.add(TsgHubUi.label(TsgHubUi.completedLine(progress), TsgHubUi.SUCCESS, small));
				JsonObject manual = overrideClaim(teamId, taskId);
				if (manual != null)
				{
					JsonObject evidence = manual.getAsJsonObject("evidence");
					String markedBy = TsgHubUi.str(evidence, "markedBy").isEmpty() ? TsgHubUi.str(manual, "displayName") : TsgHubUi.str(evidence, "markedBy");
					String how = !creditedToOrganizer && markedBy.equalsIgnoreCase(by) ? "Marked complete manually" : "Marked complete by " + markedBy;
					String note = TsgHubUi.str(evidence, "note");
					text.add(TsgHubUi.wrapped(how + (note.isEmpty() ? "" : " · Reason: " + note), TsgHubUi.WARNING, small, DETAIL_TEXT_W - 140));
				}
				if (!perMember && !TsgHubUi.contributorsText(progress, ", ", 12).isEmpty())
				{
					text.add(TsgHubUi.wrapped("Contributors: " + TsgHubUi.contributorsText(progress, ", ", 12), TsgHubUi.MUTED, small, DETAIL_TEXT_W - 140));
				}
			}
			else if (perMember)
			{
				String leader = TsgHubUi.str(progress, "leader");
				if ("solo".equals(progressScope) && !leader.isEmpty())
					text.add(TsgHubUi.caption("Leader: " + leader + " (" + TsgHubUi.integer(progress, "progress", 0) + "/" + TsgHubUi.integer(progress, "target", 1) + ")"));
				JsonArray entries = TsgHubUi.array(progress, "members");
				if (entries.size() == 0) text.add(TsgHubUi.caption("No members yet."));
				for (JsonObject entry : TsgHubUi.objects(entries)) text.add(TsgHubUi.caption(TsgHubUi.str(entry, "displayName") + ": " + progressText(entry)));
			}
			else
			{
				List<TsgHubUi.SetLine> sets = TsgHubUi.setLines(progress, true);
				if (!sets.isEmpty())
				{
					int started = 0;
					for (TsgHubUi.SetLine set : sets)
					{
						if (set.have == 0 && started > 0) continue;
						if (set.have > 0) started++;
						String line = set.name + " " + set.have + "/" + set.target
							+ (set.found.isEmpty() ? "" : " · have " + String.join(", ", set.found))
							+ (set.needed.isEmpty() ? "" : " · need " + String.join(", ", set.needed));
						text.add(TsgHubUi.wrapped(line, TsgHubUi.MUTED, small, DETAIL_TEXT_W - 140));
						if (set.have == 0) break;
					}
					int notStarted = sets.size() - Math.max(1, started);
					if (notStarted > 0) text.add(TsgHubUi.caption("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started"));
				}
				else
				{
					text.add(TsgHubUi.caption(progressText(progress)));
					String amounts = TsgHubUi.contributorsText(progress, ", ", 12);
					if (!amounts.isEmpty()) text.add(TsgHubUi.wrapped("Contributors: " + amounts, TsgHubUi.MUTED, small, DETAIL_TEXT_W - 140));
				}
			}
			card.add(text, BorderLayout.CENTER);
			if (!completed)
			{
				JButton complete = TsgHubUi.button("Mark complete");
				complete.setToolTipText("Manually complete this task for " + TsgHubUi.str(team, "name"));
				complete.addActionListener(e -> promptComplete(taskId, teamId, TsgHubUi.str(task, "title"), TsgHubUi.str(team, "name")));
				card.add(TsgHubUi.north(complete), BorderLayout.EAST);
			}
			teamsTab.add(TsgHubUi.fitHeight(card));
			teamsTab.add(Box.createVerticalStrut(4));
		}
	}

	private void addTeam()
	{
		String name = newTeamName.getText().trim();
		if (name.isEmpty())
		{
			setStatus("Enter a team name first.", TsgHubUi.Tone.ERROR);
			newTeamName.requestFocusInWindow();
			return;
		}
		plugin.createTeam(name);
	}

	private void promptRename(String teamId, String currentName)
	{
		String name = (String) JOptionPane.showInputDialog(this, "New team name:", "Rename team", JOptionPane.PLAIN_MESSAGE, null, null, currentName);
		if (name != null && !name.trim().isEmpty() && !name.trim().equals(currentName)) plugin.renameTeam(teamId, name.trim());
	}

	private void promptComplete(String taskId, String teamId, String taskName, String teamName)
	{
		JComboBox<String[]> credit = new JComboBox<>();
		credit.addItem(new String[] {"", "No one (admin)"});
		List<String[]> roster = new ArrayList<>();
		for (JsonObject member : TsgHubUi.objects(TsgHubUi.array(currentEvent, "members")))
			if (TsgHubUi.str(member, "teamId").equals(teamId)) roster.add(new String[] {TsgHubUi.str(member, "id"), TsgHubUi.str(member, "displayName")});
		roster.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
		roster.forEach(credit::addItem);
		credit.setRenderer((list, value, index, selected, focus) -> TsgHubUi.listRow(value == null ? "" : value[1], selected));
		JTextField reason = new JTextField(28);
		placeholder(reason, "e.g. Drop confirmed in Discord screenshot");
		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.add(new JLabel("Mark \"" + taskName + "\" complete for " + teamName + "."));
		form.add(new JLabel("Credit to"));
		form.add(credit);
		form.add(new JLabel("Reason (admins only)"));
		form.add(reason);
		SwingUtilities.invokeLater(reason::requestFocusInWindow);
		while (true)
		{
			int choice = JOptionPane.showConfirmDialog(this, form, "Mark complete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION) return;
			if (!reason.getText().trim().isEmpty()) break;
			JOptionPane.showMessageDialog(this, "Add a short reason so other admins know why.", "Reason required", JOptionPane.WARNING_MESSAGE);
		}
		String[] picked = (String[]) credit.getSelectedItem();
		plugin.completeTask(taskId, teamId, picked == null || picked[0].isEmpty() ? null : picked[0], reason.getText().trim());
	}

	private JsonObject overrideClaim(String teamId, String taskId)
	{
		for (JsonObject claim : TsgHubUi.objects(TsgHubUi.array(currentEvent, "claims")))
		{
			JsonObject evidence = TsgHubUi.object(claim, "evidence");
			if (TsgHubUi.str(claim, "teamId").equals(teamId) && TsgHubUi.str(claim, "taskId").equals(taskId)
				&& "organizer".equals(TsgHubUi.str(claim, "source")) && "approved".equals(TsgHubUi.str(claim, "status"))
				&& TsgHubUi.bool(evidence, "completedOverride")) return claim;
		}
		return null;
	}

	private void renderLeaderboard()
	{
		leaderboardTab.removeAll();
		String type = TsgHubUi.str(currentEvent, "type");
		if (!"skill".equals(type) && !"boss".equals(type)) return;
		JsonObject config = TsgHubUi.eventConfig(currentEvent);
		String unit = "skill".equals(type) ? "XP" : "kills";
		boolean started = !"scheduled".equals(TsgHubUi.str(currentEvent, "status"));
		int participants = TsgHubUi.integer(currentEvent, "participants", 0);
		String what = "skill".equals(type) ? TsgHubUi.skillName(TsgHubUi.str(config, "skill")) + " XP gained" : TsgHubUi.str(config, "npcName") + " kills";
		leaderboardTab.add(TsgHubUi.caption(started ? what + " · " + participants + " taking part"
			: participants + " signed up · counting starts " + TsgHubUi.eventStartWhen(currentEvent)));
		leaderboardTab.add(Box.createVerticalStrut(8));
		JsonArray rows = TsgHubUi.array(currentEvent, "leaderboard");
		if (rows.size() == 0) leaderboardTab.add(TsgHubUi.wrapped("Nobody has joined yet. Players join with one click from the TSG Hub sidebar.", TsgHubUi.MUTED, FontManager.getRunescapeFont(), DETAIL_TEXT_W));
		for (int i = 0; i < rows.size(); i++)
		{
			JsonObject row = rows.get(i).getAsJsonObject();
			int rank = TsgHubUi.integer(row, "rank", i + 1);
			JPanel card = TsgHubUi.card();
			card.setLayout(new BorderLayout(10, 0));
			if (started)
			{
				card.add(TsgHubUi.rankLabel(rank, FontManager.getRunescapeBoldFont(), 28), BorderLayout.WEST);
			}
			card.add(TsgHubUi.label(TsgHubUi.str(row, "displayName"), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), BorderLayout.CENTER);
			if (started)
			{
				boolean tracking = TsgHubUi.bool(row, "tracking");
				String gained = tracking ? "+" + String.format("%,d", TsgHubUi.integer(row, "gained", 0)) + " " + unit : "Waiting for first " + ("XP".equals(unit) ? "XP" : "kill");
				card.add(TsgHubUi.label(gained, !tracking ? TsgHubUi.MUTED : rank == 1 ? TsgHubUi.ACCENT : TsgHubUi.TEXT, tracking ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont()), BorderLayout.EAST);
			}
			leaderboardTab.add(TsgHubUi.fitHeight(card));
			leaderboardTab.add(Box.createVerticalStrut(6));
		}
		refresh(leaderboardTab);
	}

	private void renderDetails()
	{
		detailsTab.removeAll();
		if (!"drop-party".equals(TsgHubUi.str(currentEvent, "type"))) return;
		JsonObject config = TsgHubUi.eventConfig(currentEvent);
		Font font = FontManager.getRunescapeFont();
		String countdown = TsgHubUi.capitalize(TsgHubUi.eventRelative(currentEvent));
		if (!countdown.isEmpty()) detailsTab.add(TsgHubUi.label(countdown, "Ended".equals(countdown) ? TsgHubUi.MUTED : TsgHubUi.SUCCESS, FontManager.getRunescapeBoldFont()));
		detailsTab.add(Box.createVerticalStrut(6));
		detailsTab.add(TsgHubUi.wrapped("When: " + TsgHubUi.eventWhen(currentEvent), TsgHubUi.TEXT, font, DETAIL_TEXT_W));
		if (TsgHubUi.integer(config, "world", 0) > 0) detailsTab.add(TsgHubUi.label("World: " + TsgHubUi.integer(config, "world", 0), TsgHubUi.TEXT, font));
		if (!TsgHubUi.str(config, "location").isEmpty()) detailsTab.add(TsgHubUi.label("Where: " + TsgHubUi.str(config, "location"), TsgHubUi.TEXT, font));
		if (!TsgHubUi.str(config, "host").isEmpty()) detailsTab.add(TsgHubUi.label("Host: " + TsgHubUi.str(config, "host"), TsgHubUi.TEXT, font));
		if (!TsgHubUi.str(config, "notes").isEmpty()) detailsTab.add(TsgHubUi.wrapped("Notes: " + TsgHubUi.str(config, "notes"), TsgHubUi.MUTED, font, DETAIL_TEXT_W));
		detailsTab.add(Box.createVerticalStrut(10));
		detailsTab.add(TsgHubUi.caption("Players see this in the TSG Hub sidebar. Use Edit event to change it."));
		refresh(detailsTab);
	}

	private void renderTasks()
	{
		tasksTab.removeAll();
		JPanel top = TsgHubUi.panel(new BorderLayout());
		JsonArray tasks = TsgHubUi.array(currentEvent, "tasks");
		top.add(TsgHubUi.caption(tasks.size() + (tasks.size() == 1 ? " task" : " tasks") + ", shared by every team"), BorderLayout.CENTER);
		JButton add = TsgHubUi.primaryButton("New task");
		add.addActionListener(e -> beginNewTask());
		top.add(add, BorderLayout.EAST);
		tasksTab.add(TsgHubUi.fitHeight(top));
		tasksTab.add(Box.createVerticalStrut(8));
		if (tasks.size() == 0)
		{
			tasksTab.add(TsgHubUi.wrapped("No tasks yet. Choose New task to add the first one.", TsgHubUi.MUTED, FontManager.getRunescapeFont(), DETAIL_TEXT_W));
		}
		for (JsonObject task : TsgHubUi.objects(tasks))
		{
			JPanel card = TsgHubUi.card();
			JPanel text = TsgHubUi.stack();
			text.add(TsgHubUi.label(TsgHubUi.str(task, "title"), TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
			JPanel meta = TsgHubUi.panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			meta.add(TsgHubUi.badge(TsgHubUi.integer(task, "points", 1) + " pts", TsgHubUi.ACCENT));
			meta.add(TsgHubUi.badge(TsgHubUi.taskTypeLabel(task), TsgHubUi.MUTED));
			String scopeTag = "individual".equals(TsgHubUi.str(task, "scope")) ? "Everyone" : "solo".equals(TsgHubUi.str(task, "scope")) ? "Solo" : "Team";
			meta.add(TsgHubUi.badge(scopeTag, TsgHubUi.MUTED));
			String summary = taskSummary(task);
			if (!summary.isEmpty()) meta.add(TsgHubUi.caption(summary));
			text.add(meta);
			String description = TsgHubUi.str(task, "description");
			if (!description.isEmpty()) text.add(TsgHubUi.wrapped(description, TsgHubUi.MUTED, FontManager.getRunescapeSmallFont(), DETAIL_TEXT_W - 80));
			card.add(text, BorderLayout.CENTER);
			JButton edit = TsgHubUi.button("Edit");
			edit.addActionListener(e -> beginTaskEdit(task));
			JButton delete = TsgHubUi.iconButton(new TsgHubUi.TrashIcon(), "Delete task");
			delete.addActionListener(e -> confirmDeleteTask(task));
			JPanel taskButtons = TsgHubUi.panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			if (reconcilable(task))
			{
				JButton reconcile = TsgHubUi.button("Reconcile");
				reconcile.setToolTipText("Credit matching loot players received earlier in this event");
				reconcile.addActionListener(e -> plugin.reconcileTask(TsgHubUi.str(task, "id"), true));
				taskButtons.add(reconcile);
			}
			taskButtons.add(edit);
			taskButtons.add(delete);
			card.add(TsgHubUi.north(taskButtons), BorderLayout.EAST);
			tasksTab.add(TsgHubUi.fitHeight(card));
			tasksTab.add(Box.createVerticalStrut(5));
		}
		refresh(tasksTab);
	}

	private String taskSummary(JsonObject task)
	{
		JsonObject config = TsgHubUi.object(task, "config");
		int count = TsgHubUi.integer(config, "targetCount", 1);
		switch (TsgHubUi.str(task, "type"))
		{
			case "kill": return count + "x " + TsgHubUi.str(config, "npcName");
			case "raid":
				return count + "x " + raidSummary(strings(TsgHubUi.array(config, "modes")));
			case "drop":
				JsonArray sets = TsgHubUi.array(config, "itemGroups");
				if (sets.size() == 1) return nameOrPieces(sets.get(0).getAsJsonArray());
				if (sets.size() > 1) return "any of " + sets.size() + " sets";
				if (TsgHubUi.bool(config, "requireAllItems")) return TsgHubUi.array(config, "itemNames").size() + " pieces";
				if (!TsgHubUi.str(config, "itemGroup").isEmpty()) return count + "x";
				return count + "x from " + TsgHubUi.array(config, "itemNames").size() + " items";
			default: return "";
		}
	}

	private void renderClaims()
	{
		claimsTab.removeAll();
		JsonArray claims = TsgHubUi.array(currentEvent, "claims");
		JsonArray tasks = TsgHubUi.array(currentEvent, "tasks");
		int pending = 0;
		for (JsonObject claim : TsgHubUi.objects(claims))
		{
			if (!"pending".equals(TsgHubUi.str(claim, "status"))) continue;
			pending++;
			String taskTitle = "Task";
			for (JsonObject task : TsgHubUi.objects(tasks))
				if (TsgHubUi.str(task, "id").equals(TsgHubUi.str(claim, "taskId"))) taskTitle = TsgHubUi.str(task, "title");
			JsonObject team = findTeam(TsgHubUi.str(claim, "teamId"));
			JPanel card = TsgHubUi.card();
			JPanel text = TsgHubUi.stack();
			text.add(TsgHubUi.label(taskTitle, TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()));
			text.add(TsgHubUi.caption(TsgHubUi.str(claim, "displayName") + (team == null ? "" : " · " + TsgHubUi.str(team, "name"))));
			JsonObject evidence = TsgHubUi.object(claim, "evidence");
			String note = TsgHubUi.str(evidence, "note");
			if (!note.isEmpty())
			{
				text.add(Box.createVerticalStrut(3));
				text.add(TsgHubUi.wrapped(note, TsgHubUi.TEXT, FontManager.getRunescapeFont(), DETAIL_TEXT_W - 150));
			}
			card.add(text, BorderLayout.CENTER);
			JPanel actions = TsgHubUi.panel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			Matcher link = Pattern.compile("https?://\\S+").matcher(note);
			if (link.find())
			{
				String url = link.group();
				JButton open = TsgHubUi.button("Open link");
				open.setToolTipText(url);
				open.addActionListener(e -> LinkBrowser.browse(url));
				actions.add(open);
			}
			String claimId = TsgHubUi.str(claim, "id");
			JButton reject = TsgHubUi.button("Reject");
			reject.addActionListener(e -> {
				if (JOptionPane.showConfirmDialog(this, "Reject this claim?", "Reject claim", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
					plugin.reviewClaim(claimId, false);
			});
			JButton approve = TsgHubUi.primaryButton("Approve");
			approve.addActionListener(e -> plugin.reviewClaim(claimId, true));
			actions.add(reject);
			actions.add(approve);
			card.add(TsgHubUi.north(actions), BorderLayout.EAST);
			claimsTab.add(TsgHubUi.fitHeight(card));
			claimsTab.add(Box.createVerticalStrut(5));
		}
		if (pending == 0)
		{
			claimsTab.add(TsgHubUi.wrapped("Nothing to review. Proof submitted for manual tasks shows up here.", TsgHubUi.MUTED, FontManager.getRunescapeFont(), DETAIL_TEXT_W));
		}
		claimsTabButton.setText(pending == 0 ? "Claims" : "Claims (" + pending + ")");
		refresh(claimsTab);
	}

	private void beginCreateEvent()
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
		partyHost.setText(plugin.getDetectedPlayerName());
		partyNotes.setText("");
		for (JTextField prize : prizeFields) prize.setText("");
		showEventForm();
	}

	private void showEventForm()
	{
		updateEventFormType();
		eventFormError.setVisible(false);
		detailLayout.show(detail, "event-form");
		eventName.requestFocusInWindow();
	}

	private void beginEditEvent()
	{
		if (currentEvent == null) return;
		editingEvent = true;
		eventFormTitle.setText("Edit event");
		detectedClan.setVisible(false);
		eventFormSave.setText("Save changes");
		eventName.setText(TsgHubUi.str(currentEvent, "name"));
		Instant start = TsgHubUi.eventStart(currentEvent);
		Instant end = TsgHubUi.eventEnd(currentEvent);
		setEventTimes(start == null ? null : start.atZone(ZoneId.systemDefault()), end == null ? null : end.atZone(ZoneId.systemDefault()));
		eventHideScores.setSelected(TsgHubUi.bool(currentEvent, "hideScores"));
		eventHidden.setSelected(TsgHubUi.bool(currentEvent, "hidden"));
		String type = TsgHubUi.str(currentEvent, "type");
		eventType.setSelectedIndex(Math.max(0, Arrays.asList(EVENT_TYPES).indexOf(type.isEmpty() ? "bingo" : type)));
		eventType.setEnabled(false);
		JsonObject config = TsgHubUi.eventConfig(currentEvent);
		eventSkill.setSelectedItem(TsgHubUi.skillName(TsgHubUi.str(config, "skill")));
		eventBoss.setText(TsgHubUi.str(config, "npcName"));
		if ("loot".equals(TsgHubUi.str(config, "signal"))) eventSignalLoot.setSelected(true);
		else eventSignalKc.setSelected(true);
		partyWorld.setText(TsgHubUi.integer(config, "world", 0) > 0 ? String.valueOf(TsgHubUi.integer(config, "world", 0)) : "");
		partyLocation.setText(TsgHubUi.str(config, "location"));
		partyHost.setText(TsgHubUi.str(config, "host"));
		partyNotes.setText(TsgHubUi.str(config, "notes"));
		List<Long> prizes = TsgHubUi.eventPrizes(currentEvent);
		for (int i = 0; i < prizeFields.length; i++) prizeFields[i].setText(i < prizes.size() ? TsgHubUi.millions(prizes.get(i)) : "");
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
				if (positiveInt(world) < 0) { eventFormFailed("World must be a number, like 330.", partyWorld); return; }
				config.addProperty("world", positiveInt(world));
			}
			config.addProperty("location", partyLocation.getText().trim());
			config.addProperty("host", partyHost.getText().trim());
			config.addProperty("notes", partyNotes.getText().trim());
		}
		JsonArray prizes = new JsonArray();
		for (int i = 0; i < prizeFields.length; i++)
		{
			long gp = TsgHubUi.parseMillions(prizeFields[i].getText());
			if (gp < 0) { eventFormFailed(TsgHubUi.place(i + 1) + " prize must be a number of millions, like 20 or 2.5.", prizeFields[i]); return; }
			if (gp > 0 && prizes.size() < i) { eventFormFailed("Fill in the prizes in order, starting with 1st.", prizeFields[i]); return; }
			if (gp > 0) prizes.add(gp);
		}
		eventFormError.setVisible(false);
		if (editingEvent && currentEvent != null) plugin.updateEvent(TsgHubUi.str(currentEvent, "id"), name, start, end, eventHideScores.isSelected(), eventHidden.isSelected(), "bingo".equals(type) ? null : config, prizes);
		else plugin.createEvent(name, start, end, eventHideScores.isSelected(), eventHidden.isSelected(), type, config, prizes);
	}

	private void setEventTimes(ZonedDateTime start, ZonedDateTime end)
	{
		DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
		eventStart.setDate(start == null ? LocalDate.now() : start.toLocalDate());
		eventStartTime.setText(start == null ? "" : start.format(clock));
		eventEnd.setDate(end == null ? eventStart.getDate() : end.toLocalDate());
		eventEndTime.setText(end == null ? "" : end.format(clock));
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
		eventZoneHint.setText(TsgHubUi.html(TsgHubUi.escape("Times are in your time zone, " + TsgHubUi.zoneLabel(ZoneId.systemDefault(), TsgHubUi.clock.instant())
			+ ". Players see them in theirs." + (dropParty ? " Leave the end time empty if there's no set end." : "")), 480));
		placeholder(eventName, dropParty ? "e.g. Halloween drop party, clan trip" : "e.g. Autumn Bingo");
		eventName.repaint();
		eventTypeHint.setText("skill".equals(type) ? "Players join with one click; most XP gained in the skill wins."
			: "boss".equals(type) ? "Players join with one click; most kills of the boss wins."
			: dropParty ? "Anything else: an announcement with the time and place. Nothing to join or track."
			: "Teams join with invite codes and complete tiles.");
		revalidate();
		repaint();
	}

	private void beginNewTask()
	{
		editingTaskId = "";
		taskEditorTitle.setText("New task");
		taskSave.setText("Add task");
		taskTitle.setText("");
		taskDescription.setText("");
		taskType.setSelectedItem(TaskType.MANUAL);
		scopeTeam.setSelected(true);
		taskBoss.setText("");
		signalKc.setSelected(true);
		raidChoice.setSelectedIndex(0);
		rebuildRaidModes("");
		dropSpecific.setSelected(true);
		resetItems();
		targetCount.setText("1");
		taskPoints.setText("1");
		openTaskEditor();
	}

	private void beginTaskEdit(JsonObject task)
	{
		editingTaskId = TsgHubUi.str(task, "id");
		taskEditorTitle.setText("Edit task");
		taskSave.setText("Save task");
		taskTitle.setText(TsgHubUi.str(task, "title"));
		taskDescription.setText(TsgHubUi.str(task, "description"));
		String scope = TsgHubUi.str(task, "scope");
		if ("individual".equals(scope)) scopeIndividual.setSelected(true);
		else if ("solo".equals(scope)) scopeSolo.setSelected(true);
		else scopeTeam.setSelected(true);
		taskPoints.setText(String.valueOf(TsgHubUi.integer(task, "points", 1)));
		JsonObject config = TsgHubUi.object(task, "config");
		targetCount.setText(String.valueOf(TsgHubUi.integer(config, "targetCount", 1)));
		String type = TsgHubUi.str(task, "type");
		TaskType selected = "kill".equals(type) ? TaskType.KILL
			: "raid".equals(type) ? TaskType.RAID
			: "drop".equals(type) ? (TsgHubUi.bool(config, "requireAllItems") || TsgHubUi.array(config, "itemGroups").size() > 0 ? TaskType.ITEM_SET : TaskType.DROP)
			: TaskType.MANUAL;
		taskType.setSelectedItem(selected);

		taskBoss.setText(TsgHubUi.str(config, "npcName"));
		if ("loot".equals(TsgHubUi.str(config, "signal"))) signalLoot.setSelected(true);
		else signalKc.setSelected(true);

		List<String> modes = strings(TsgHubUi.array(config, "modes"));
		selectRaidModes(modes);

		String group = TsgHubUi.str(config, "itemGroup");
		if ("jar".equals(group)) dropJar.setSelected(true);
		else if ("pet".equals(group)) dropPet.setSelected(true);
		else dropSpecific.setSelected(true);

		resetItems();
		JsonArray groups = TsgHubUi.array(config, "itemGroups");
		if (groups.size() > 0)
		{
			itemGroups.clear();
			for (int g = 0; g < groups.size(); g++)
			{
				List<TsgHubPlugin.ItemSuggestion> items = new ArrayList<>();
				for (JsonObject item : TsgHubUi.objects(groups.get(g).getAsJsonArray()))
					items.add(new TsgHubPlugin.ItemSuggestion(TsgHubUi.integer(item, "id", 0), TsgHubUi.str(item, "name")));
				itemGroups.add(items);
			}
		}
		else
		{
			JsonArray names = TsgHubUi.array(config, "itemNames");
			JsonArray ids = TsgHubUi.array(config, "itemIds");
			for (int i = 0; i < names.size(); i++)
				itemGroups.get(0).add(new TsgHubPlugin.ItemSuggestion(i < ids.size() ? ids.get(i).getAsInt() : 0, names.get(i).getAsString()));
		}
		refreshGroupChoices(0);
		renderSelectedItems();
		openTaskEditor();
	}

	private void openTaskEditor()
	{
		taskFormError.setVisible(false);
		itemSearch.setText("");
		itemResults.clear();
		updateTaskVisibility();
		detailLayout.show(detail, "task-editor");
		taskTitle.requestFocusInWindow();
	}

	private void closeTaskEditor()
	{
		editingTaskId = "";
		searchDebounce.stop();
		detailLayout.show(detail, currentEvent == null ? "empty" : "event");
	}

	private void updateTaskVisibility()
	{
		TaskType type = (TaskType) taskType.getSelectedItem();
		boolean drop = type == TaskType.DROP;
		boolean specificDrop = drop && dropSpecific.isSelected();
		boolean grouped = type == TaskType.ITEM_SET;
		bossRow.setVisible(type == TaskType.KILL);
		signalRow.setVisible(type == TaskType.KILL);
		raidRow.setVisible(type == TaskType.RAID);
		dropKindRow.setVisible(drop);
		presetRow.setVisible(grouped);
		groupRow.setVisible(grouped);
		itemPickerRow.setVisible(type == TaskType.ITEM_SET || specificDrop);
		boolean counted = type == TaskType.KILL || type == TaskType.RAID || drop;
		countRow.setVisible(counted);
		numbersRow.removeAll();
		if (counted) numbersRow.add(countRow);
		numbersRow.add(pointsRow);
		if (!counted) numbersRow.add(Box.createHorizontalGlue());
		targetCountCaption.setText(type == TaskType.KILL ? "Kills needed" : type == TaskType.RAID ? "Completions needed" : "Drops needed");
		itemSearchHint.setText(defaultItemHint());
		if (!grouped && itemGroups.size() > 1) collapseGroups();
		renderSelectedItems();
		revalidate();
		repaint();
	}

	private String defaultItemHint()
	{
		if (taskType.getSelectedItem() == TaskType.ITEM_SET) return "Every item in a set is required.";
		return "Any of these items counts as a drop. Need one of several groups (3 shards OR 3 other shards)? Use Complete a set.";
	}

	private void submitTask()
	{
		TaskType type = (TaskType) taskType.getSelectedItem();
		String error = validateTask(type);
		if (error != null) { taskFormFailed(error); return; }
		taskFormError.setVisible(false);
		String targets = type == TaskType.KILL ? taskBoss.getText() : type == TaskType.RAID ? String.join("\n", selectedRaidModes()) : "";
		int dropCategory = dropJar.isSelected() ? 1 : dropPet.isSelected() ? 2 : 0;
		plugin.saveTask(editingTaskId, taskTitle.getText(), taskDescription.getText(), type.ordinal(),
			scopeIndividual.isSelected() ? 1 : scopeSolo.isSelected() ? 2 : 0, signalLoot.isSelected() ? 1 : 0, dropCategory,
			targets, selectedItemsJson(type), type == TaskType.ITEM_SET ? 1 : 0,
			targetCount.getText(), taskPoints.getText());
	}

	private String validateTask(TaskType type)
	{
		if (taskTitle.getText().trim().isEmpty()) { taskTitle.requestFocusInWindow(); return "Give the task a title."; }
		if (positiveInt(taskPoints.getText()) < 0) { taskPoints.requestFocusInWindow(); return "Points must be a whole number, 1 or more."; }
		if (countRow.isVisible() && positiveInt(targetCount.getText()) < 0) { targetCount.requestFocusInWindow(); return targetCountCaption.getText() + " must be a whole number, 1 or more."; }
		if (type == TaskType.KILL && taskBoss.getText().isEmpty()) { taskBoss.field().requestFocusInWindow(); return "Enter the boss name."; }
		if (type == TaskType.RAID && selectedRaidModes().isEmpty()) return "Pick at least one raid.";
		if (itemPickerRow.isVisible())
		{
			int total = itemGroups.stream().mapToInt(List::size).sum();
			if (total == 0) { itemSearch.requestFocusInWindow(); return "Add at least one item."; }
			if (groupRow.isVisible() && itemGroups.stream().anyMatch(List::isEmpty)) return "Every set needs at least one item, or remove the empty set.";
		}
		return null;
	}

	private int positiveInt(String text)
	{
		try
		{
			int value = Integer.parseInt(text.trim());
			return value >= 1 ? value : -1;
		}
		catch (NumberFormatException e) { return -1; }
	}

	private List<String> selectedRaidModes()
	{
		List<String> modes = new ArrayList<>();
		String[] raid = RAID_MODES[Math.max(0, raidChoice.getSelectedIndex())];
		JRadioButton any = raidModeButtons.get("");
		for (Map.Entry<String, JRadioButton> entry : raidModeButtons.entrySet())
		{
			if (!entry.getKey().isEmpty() && entry.getValue().isSelected()) modes.add(entry.getKey());
		}
		if (modes.isEmpty() || any != null && any.isSelected())
		{
			modes.clear();
			for (int i = 1; i < raid.length; i += 2) modes.add(raid[i]);
		}
		return modes;
	}

	private JsonArray selectedItemsJson(TaskType type)
	{
		JsonArray items = new JsonArray();
		boolean grouped = type == TaskType.ITEM_SET;
		for (int g = 0; g < itemGroups.size(); g++)
		{
			for (TsgHubPlugin.ItemSuggestion item : itemGroups.get(g))
			{
				JsonObject json = new JsonObject();
				json.addProperty("name", item.name);
				json.addProperty("id", item.id);
				json.addProperty("group", grouped ? g : -1);
				items.add(json);
			}
		}
		return items;
	}

	private void runItemSearch()
	{
		String query = itemSearch.getText().trim();
		JScrollPane results = resultsScroll();
		if (query.length() < 2)
		{
			itemResults.clear();
			results.setVisible(false);
			itemSearchHint.setText(defaultItemHint());
			itemPickerRow.revalidate();
			return;
		}
		long requestId = ++itemSearchRequestId;
		itemSearchHint.setText("Searching...");
		plugin.searchItems(query, found -> {
			if (requestId != itemSearchRequestId) return;
			itemResults.clear();
			for (TsgHubPlugin.ItemSuggestion item : found) itemResults.addElement(item);
			itemResultList.setVisibleRowCount(Math.max(1, Math.min(6, found.size())));
			results.setVisible(!found.isEmpty());
			if (!found.isEmpty()) itemResultList.setSelectedIndex(0);
			itemSearchHint.setText(found.isEmpty()
				? "No tradeable items match '" + query + "'. Some untradeable drops aren't in RuneLite's catalog."
				: "Click a result (or press Enter) to add it.");
			itemPickerRow.revalidate();
		});
	}

	private JScrollPane resultsScroll()
	{
		for (Component component : itemPickerRow.getComponents())
			if (component instanceof JScrollPane && "results".equals(component.getName())) return (JScrollPane) component;
		throw new IllegalStateException("results list missing");
	}

	private void addItem(TsgHubPlugin.ItemSuggestion item)
	{
		int group = groupRow.isVisible() ? Math.max(0, activeGroup.getSelectedIndex()) : 0;
		List<TsgHubPlugin.ItemSuggestion> target = itemGroups.get(group);
		if (target.stream().anyMatch(existing -> existing.id == item.id))
		{
			itemSearchHint.setText(item.name + " is already added.");
			return;
		}
		target.add(item);
		itemSearch.setText("");
		itemResults.clear();
		resultsScroll().setVisible(false);
		itemSearchHint.setText("Added " + item.name + ". Search again to add more.");
		renderSelectedItems();
		itemSearch.requestFocusInWindow();
	}

	private void resetItems()
	{
		itemGroups.clear();
		itemGroups.add(new ArrayList<>());
		refreshGroupChoices(0);
		renderSelectedItems();
	}

	private void collapseGroups()
	{
		List<TsgHubPlugin.ItemSuggestion> merged = new ArrayList<>();
		for (List<TsgHubPlugin.ItemSuggestion> group : itemGroups)
			for (TsgHubPlugin.ItemSuggestion item : group)
				if (merged.stream().noneMatch(existing -> existing.id == item.id)) merged.add(item);
		itemGroups.clear();
		itemGroups.add(merged);
		refreshGroupChoices(0);
	}

	private void refreshGroupChoices(int select)
	{
		activeGroup.removeAllItems();
		for (int i = 0; i < itemGroups.size(); i++) activeGroup.addItem(setLabel(i));
		if (select >= 0 && select < itemGroups.size()) activeGroup.setSelectedIndex(select);
	}

	private String setLabel(int index)
	{
		String name = TsgHubItemSets.nameFor(ids(itemGroups.get(index)));
		return "Set " + (index + 1) + (name.isEmpty() ? "" : ": " + name);
	}

	private void renderSelectedItems()
	{
		selectedItemsPanel.removeAll();
		boolean grouped = groupRow != null && groupRow.isVisible();
		if (!grouped)
		{
			for (TsgHubPlugin.ItemSuggestion item : itemGroups.get(0)) selectedItemsPanel.add(TsgHubUi.fitHeight(itemRow(item, itemGroups.get(0))));
			if (itemGroups.get(0).isEmpty()) selectedItemsPanel.add(TsgHubUi.caption("No items added yet."));
		}
		else
		{
			for (int g = 0; g < itemGroups.size(); g++) selectedItemsPanel.add(setCard(g));
		}
		selectedItemsPanel.revalidate();
		selectedItemsPanel.repaint();
	}

	private JPanel setCard(int index)
	{
		List<TsgHubPlugin.ItemSuggestion> group = itemGroups.get(index);
		JPanel card = new JPanel(new BorderLayout(0, 6));
		card.setBackground(TsgHubUi.CARD);
		boolean active = index == activeGroup.getSelectedIndex() && itemGroups.size() > 1;
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, active ? TsgHubUi.ACCENT : TsgHubUi.CARD),
			BorderFactory.createEmptyBorder(6, 8, 6, 6)));
		JPanel header = TsgHubUi.row();
		String name = TsgHubItemSets.nameFor(ids(group));
		header.add(TsgHubUi.label((name.isEmpty() ? "Set " + (index + 1) : name) + "  ", TsgHubUi.TEXT, FontManager.getRunescapeBoldFont()), BorderLayout.WEST);
		header.add(TsgHubUi.caption(group.size() == 1 ? "1 piece" : group.size() + " pieces"), BorderLayout.CENTER);
		if (itemGroups.size() > 1)
		{
			JButton remove = TsgHubUi.iconButton(new RemoveIcon(), "Remove this set");
			remove.addActionListener(e -> {
				itemGroups.remove(index);
				refreshGroupChoices(Math.max(0, Math.min(index, itemGroups.size() - 1)));
				renderSelectedItems();
			});
			header.add(remove, BorderLayout.EAST);
		}
		card.add(header, BorderLayout.NORTH);
		if (group.isEmpty())
		{
			card.add(TsgHubUi.caption("Empty. Search above to add pieces."), BorderLayout.CENTER);
		}
		else
		{
			JPanel pieces = TsgHubUi.panel(new GridLayout(0, 2, 4, 2));
			for (TsgHubPlugin.ItemSuggestion item : group) pieces.add(itemRow(item, group));
			card.add(pieces, BorderLayout.CENTER);
		}
		JPanel wrap = TsgHubUi.panel(new BorderLayout());
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		wrap.add(card, BorderLayout.CENTER);
		return TsgHubUi.fitHeight(wrap);
	}

	private JPanel itemRow(TsgHubPlugin.ItemSuggestion item, List<TsgHubPlugin.ItemSuggestion> owner)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(TsgHubUi.BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
		JLabel name = TsgHubUi.label(item.name, TsgHubUi.TEXT, FontManager.getRunescapeFont());
		name.setIcon(iconFor(item.id, selectedItemsPanel));
		row.add(name, BorderLayout.CENTER);
		JButton remove = TsgHubUi.iconButton(new RemoveIcon(), "Remove " + item.name);
		remove.addActionListener(e -> {
			owner.remove(item);
			refreshGroupChoices(Math.max(0, activeGroup.getSelectedIndex()));
			renderSelectedItems();
		});
		row.add(remove, BorderLayout.EAST);
		return row;
	}

	private ImageIcon iconFor(int itemId, JComponent repaintTarget)
	{
		if (itemId <= 0) return null;
		ImageIcon cached = itemIcons.get(itemId);
		if (cached != null) return cached;
		AsyncBufferedImage image = plugin.getItemImage(itemId);
		if (image == null) return null;
		ImageIcon icon = new ImageIcon(image.getScaledInstance(18, 16, Image.SCALE_SMOOTH));
		itemIcons.put(itemId, icon);
		image.onLoaded(() -> SwingUtilities.invokeLater(() -> {
			itemIcons.put(itemId, new ImageIcon(image.getScaledInstance(18, 16, Image.SCALE_SMOOTH)));
			itemResultList.repaint();
			renderSelectedItems();
		}));
		return icon;
	}

	private static final class RemoveIcon extends TsgHubUi.HoverIcon
	{
		RemoveIcon() { super(12); }
		@Override void paint(Graphics2D g, Component c)
		{
			g.drawLine(2, 2, 10, 10);
			g.drawLine(10, 2, 2, 10);
		}
	}

	private static void placeholder(JTextField field, String text)
	{
		field.putClientProperty("JTextField.placeholderText", text);
	}

	private static JPanel field(String label, Component input)
	{
		JPanel row = TsgHubUi.panel(new BorderLayout(0, 3));
		row.add(TsgHubUi.caption(label), BorderLayout.NORTH);
		row.add(input, BorderLayout.CENTER);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return TsgHubUi.fitHeight(row);
	}

	private static JPanel radioField(String label, JRadioButton... options)
	{
		ButtonGroup group = new ButtonGroup();
		JPanel choices = TsgHubUi.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		for (JRadioButton option : options)
		{
			group.add(option);
			choices.add(TsgHubUi.plain(option, TsgHubUi.TEXT));
			choices.add(Box.createHorizontalStrut(10));
		}
		return field(label, choices);
	}

	private static JPanel footer(JButton cancel, JButton save)
	{
		JPanel footer = TsgHubUi.panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.add(cancel);
		footer.add(save);
		return TsgHubUi.fitHeight(footer);
	}

	private static JComponent narrowPage(JPanel form)
	{
		JPanel holder = TsgHubUi.panel(new BorderLayout());
		form.setMaximumSize(new Dimension(520, Integer.MAX_VALUE));
		JPanel limiter = new JPanel()
		{
			@Override public Dimension getPreferredSize()
			{
				int available = holder.getWidth() > 0 ? holder.getWidth() : 520;
				return new Dimension(Math.min(520, available), form.getPreferredSize().height);
			}
		};
		limiter.setLayout(new BorderLayout());
		limiter.setOpaque(false);
		limiter.add(form, BorderLayout.CENTER);
		holder.add(limiter, BorderLayout.WEST);
		TsgHubUi.WidthTrackingPanel page = new TsgHubUi.WidthTrackingPanel();
		page.add(holder);
		return TsgHubUi.scroll(page);
	}

	private void showFormError(JLabel label, String message)
	{
		label.setText(TsgHubUi.html(TsgHubUi.escape(message), DETAIL_TEXT_W));
		label.setVisible(true);
		label.revalidate();
	}

	private JsonObject findTeam(String teamId)
	{
		for (JsonObject team : TsgHubUi.objects(TsgHubUi.array(currentEvent, "teams"))) if (TsgHubUi.str(team, "id").equals(teamId)) return team;
		return null;
	}

	private int countMembers(String teamId)
	{
		int count = 0;
		for (JsonObject member : TsgHubUi.objects(TsgHubUi.array(currentEvent, "members"))) if (TsgHubUi.str(member, "teamId").equals(teamId)) count++;
		return count;
	}

	private static String progressText(JsonObject progress)
	{
		if (TsgHubUi.bool(progress, "completed")) return "Complete";
		if (TsgHubUi.bool(progress, "pending")) return "Awaiting review";
		JsonObject best = TsgHubUi.bestSet(progress);
		if (best != null)
		{
			int options = TsgHubUi.array(progress, "alternatives").size();
			List<String> missing = TsgHubUi.missingPieces(TsgHubUi.array(best, "items"));
			return TsgHubUi.setName(best) + " " + TsgHubUi.integer(best, "progress", 0) + "/" + TsgHubUi.integer(best, "target", 1)
				+ (options > 1 ? " (closest of " + options + ")" : "") + (missing.isEmpty() ? "" : " · need " + String.join(", ", missing));
		}
		return TsgHubUi.integer(progress, "progress", 0) + "/" + TsgHubUi.integer(progress, "target", 1);
	}

	private static List<String> strings(JsonArray array)
	{
		List<String> values = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) values.add(array.get(i).getAsString());
		return values;
	}

	private static String nameOrPieces(JsonArray items)
	{
		String name = TsgHubItemSets.nameFor(TsgHubUi.itemIds(items));
		return name.isEmpty() ? items.size() + " pieces" : name;
	}

	private static String raidSummary(List<String> modes)
	{
		for (String[] raid : RAID_MODES)
		{
			List<String> all = new ArrayList<>();
			for (int i = 1; i < raid.length; i += 2) all.add(raid[i]);
			if (!modes.isEmpty() && modes.size() == all.size() && modes.containsAll(all))
				return raidLabel(all.get(0)).split(" ")[0] + " (any mode)";
		}
		List<String> labels = new ArrayList<>();
		for (String mode : modes) labels.add(raidLabel(mode));
		return String.join(", ", labels);
	}

	private static String raidLabel(String mode)
	{
		for (String[] raid : RAID_MODES)
			for (int i = 1; i < raid.length; i += 2)
				if (raid[i].equals(mode)) return raid[0].replace("Chambers of Xeric", "CoX").replace("Theatre of Blood", "ToB").replace("Tombs of Amascut", "ToA") + " " + raid[i + 1];
		return mode;
	}

	private static int statusOrder(String status)
	{
		return "active".equals(status) ? 0 : "ended".equals(status) ? 2 : 1;
	}

	private static void refresh(JPanel panel)
	{
		panel.revalidate();
		panel.repaint();
	}
}
