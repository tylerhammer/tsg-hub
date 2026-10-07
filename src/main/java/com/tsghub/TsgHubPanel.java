package com.tsghub;

import static com.tsghub.TsgHubUi.*;

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

	private final JPanel eventList = stack();
	private final CardLayout detailLayout = new CardLayout();
	private final JPanel detail = new JPanel(detailLayout);
	private final StatusLine status = new StatusLine(DETAIL_TEXT_W);

	private final JLabel eventTitle = label(" ", TEXT, boldFont().deriveFont(20f));
	private final JLabel eventMeta = caption(" ");
	private final JLabel eventSchedule = caption(" ");
	private final WidthTrackingPanel teamsTab = new WidthTrackingPanel();
	private final WidthTrackingPanel tasksTab = new WidthTrackingPanel();
	private final WidthTrackingPanel claimsTab = new WidthTrackingPanel();
	private MaterialTabGroup tabs;
	private MaterialTab teamsTabButton;
	private MaterialTab tasksTabButton;
	private MaterialTab claimsTabButton;
	private MaterialTab leaderboardTabButton;
	private MaterialTab detailsTabButton;
	private final WidthTrackingPanel leaderboardTab = new WidthTrackingPanel();
	private final WidthTrackingPanel detailsTab = new WidthTrackingPanel();
	private final JTextField newTeamName = new JTextField();

	private final JLabel eventFormTitle = sectionTitle("New event");
	private final JLabel detectedClan = caption("");
	private final JTextField eventName = new JTextField();
	private JPanel nameRow;
	private final TsgHubDatePicker eventStart = new TsgHubDatePicker();
	private final TsgHubDatePicker eventEnd = new TsgHubDatePicker();
	private final JTextField eventStartTime = new JTextField();
	private final JTextField eventEndTime = new JTextField();
	private final JLabel eventZoneHint = caption("");
	private final JCheckBox eventHideScores = new JCheckBox("Hide scores from players");
	private final JCheckBox eventHidden = new JCheckBox("Hide event from players");
	private final JButton publishEvent = primaryButton("Publish");
	private final JButton endEvent = button("End event");
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
	private JPanel skillRow, bossRow2, signalRow2, partyRows, hideScoresRow;
	private JLabel endTimeCaption;
	private final JLabel eventFormError = label("", TsgHubUi.ERROR, smallFont());
	private final JButton eventFormSave = primaryButton("Create event");
	private boolean editingEvent;

	private final JLabel taskEditorTitle = sectionTitle("New task");
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
	private final JLabel itemSearchHint = caption("");
	private final JComboBox<String> activeGroup = new JComboBox<>();
	private final JPanel selectedItemsPanel = stack();
	private final List<List<TsgHubPlugin.ItemSuggestion>> itemGroups = new ArrayList<>();
	private final JTextField targetCount = new JTextField("1");
	private final JLabel targetCountCaption = caption("How many");
	private final JTextField taskPoints = new JTextField("1");
	private final JLabel taskFormError = label("", TsgHubUi.ERROR, smallFont());
	private final JButton taskSave = primaryButton("Add task");
	private final Map<Integer, ImageIcon> itemIcons = new HashMap<>();
	private final Timer searchDebounce = new Timer(300, e -> runItemSearch());
	private final JPanel numbersRow = panel(new GridLayout(1, 2, 10, 0));
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
		setBackground(BACKGROUND);

		add(buildEventListColumn(), BorderLayout.WEST);

		detail.setOpaque(false);
		detail.add(north(emptyState("Pick an event", "Choose an event on the left, or create a new one.", 320)), "empty");
		detail.add(buildEventForm(), "event-form");
		detail.add(buildEventWorkspace(), "event");
		detail.add(buildTaskEditor(), "task-editor");

		JPanel right = panel(new BorderLayout());
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
		column.setBackground(CARD);
		column.setPreferredSize(new Dimension(LIST_W, 100));
		column.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

		JButton refresh = iconButton(new RefreshIcon(), "Refresh events");
		refresh.addActionListener(e -> plugin.loadManagedEvents());
		JPanel header = row(boldLabel("Events"), refresh);

		JButton create = primaryButton("New event");
		create.addActionListener(e -> beginCreateEvent());

		JPanel top = stack();
		top.add(fitHeight(header));
		top.add(Box.createVerticalStrut(8));
		top.add(fitHeight(create));
		column.add(top, BorderLayout.NORTH);

		WidthTrackingPanel listWrap = new WidthTrackingPanel();
		listWrap.add(eventList);
		column.add(scroll(listWrap), BorderLayout.CENTER);
		return column;
	}

	private JComponent buildEventForm()
	{
		JPanel form = stack();
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
		signalRow2 = radioField("Count kills using", eventSignalKc, eventSignalLoot);
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
		form.add(Box.createVerticalStrut(6));
		form.add(plain(eventHidden, TEXT));
		form.add(wrapped("Only admins can see and join it, so you can set it up and test it first. Publish it when it's ready. Players who already joined keep access.", MUTED, smallFont(), 480));
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

		JButton cancel = button("Cancel");
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
		JPanel workspace = panel(new BorderLayout(0, 8));
		JPanel header = panel(new BorderLayout(10, 0));
		JPanel titles = stack();
		titles.add(eventTitle);
		titles.add(Box.createVerticalStrut(2));
		titles.add(eventMeta);
		titles.add(Box.createVerticalStrut(2));
		titles.add(eventSchedule);
		header.add(titles, BorderLayout.CENTER);
		JButton edit = button("Edit event");
		edit.addActionListener(e -> beginEditEvent());
		JButton deleteEvent = iconButton(new TrashIcon(), "Delete event");
		deleteEvent.addActionListener(e -> confirmDeleteEvent());
		publishEvent.setToolTipText("Let players see and join this event");
		publishEvent.addActionListener(e -> { if (currentEvent != null) plugin.publishEvent(currentEvent); });
		JPanel editButtons = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		endEvent.setToolTipText("Mark this event as finished and let the clan know");
		endEvent.addActionListener(e -> confirmEndEvent());
		editButtons.add(publishEvent);
		editButtons.add(endEvent);
		editButtons.add(edit);
		editButtons.add(deleteEvent);
		header.add(north(editButtons), BorderLayout.EAST);

		JPanel display = panel(new BorderLayout());
		tabs = new MaterialTabGroup(display);
		tabs.setLayout(new FlowLayout(FlowLayout.LEFT, 12, 0));
		tabs.setOpaque(false);
		teamsTabButton = tab("Teams", teamsTab);
		tasksTabButton = tab("Tasks", tasksTab);
		claimsTabButton = tab("Claims", claimsTab);
		leaderboardTabButton = tab("Leaderboard", leaderboardTab);
		detailsTabButton = tab("Details", detailsTab);
		tabs.select(teamsTabButton);

		JPanel north = stack();
		north.add(fitHeight(header));
		north.add(Box.createVerticalStrut(10));
		JPanel tabRow = panel(new BorderLayout());
		tabRow.setBorder(bottomRule());
		tabRow.add(tabs, BorderLayout.WEST);
		north.add(fitHeight(tabRow));
		workspace.add(north, BorderLayout.NORTH);
		display.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		workspace.add(display, BorderLayout.CENTER);

		newTeamName.addActionListener(e -> addTeam());
		placeholder(newTeamName, "e.g. Dragon Slayers");
		return workspace;
	}

	private MaterialTab tab(String name, JComponent content)
	{
		MaterialTab tab = new MaterialTab(name, tabs, scroll(content));
		tabs.addTab(tab);
		return tab;
	}

	private JComponent buildTaskEditor()
	{
		JPanel form = stack();
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

		JButton newGroup = button("New set");
		newGroup.addActionListener(e -> {
			itemGroups.add(new ArrayList<>());
			refreshGroupChoices(itemGroups.size() - 1);
			renderSelectedItems();
			itemSearch.requestFocusInWindow();
		});
		groupRow = field("Or build your own: search adds items to", row(activeGroup, newGroup));

		itemPickerRow = buildItemPicker();
		form.add(groupRow);
		form.add(itemPickerRow);

		countRow = panel(new BorderLayout(0, 3));
		countRow.add(targetCountCaption, BorderLayout.NORTH);
		countRow.add(targetCount, BorderLayout.CENTER);
		countRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		pointsRow = field("Points", taskPoints);
		numbersRow.add(countRow);
		numbersRow.add(pointsRow);
		form.add(fitHeight(numbersRow));

		taskFormError.setVisible(false);
		form.add(taskFormError);
		form.add(Box.createVerticalStrut(10));
		JButton cancel = button("Cancel");
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
		JButton add = button("Add");
		add.addActionListener(e -> {
			if (presetChoice.getSelectedItem() instanceof TsgHubItemSets.Preset) addPreset((TsgHubItemSets.Preset) presetChoice.getSelectedItem());
		});
		JPanel row = field("Sets that count", row(presetChoice, add));
		row.add(caption("Completing any one set finishes the task. Teammates can each contribute pieces."), BorderLayout.SOUTH);
		return fitHeight(row);
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

		JPanel picker = stack();
		picker.add(field("Raid", raidChoice));
		picker.add(field("Mode", raidModeChoices));
		picker.add(caption("Only raids with clan members count."));
		picker.add(Box.createVerticalStrut(8));
		JButton greenLog = button("Make a green log task for this raid instead");
		greenLog.setToolTipText("Switches to Complete a set with every unique from this raid");
		greenLog.addActionListener(e -> switchToGreenLog());
		JPanel greenLogRow = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		greenLogRow.add(greenLog);
		picker.add(fitHeight(greenLogRow));
		picker.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return fitHeight(picker);
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
		JRadioButton radio = plain(new JRadioButton(label), TEXT);
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
		JPanel picker = stack();
		picker.add(caption("Items"));
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
		picker.add(fitHeight(itemSearch));
		picker.add(itemSearchHint);

		itemResultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		itemResultList.setVisibleRowCount(5);
		itemResultList.setBackground(CARD);
		itemResultList.setCellRenderer((list, value, index, selected, focus) -> {
			JLabel row = listRow(value == null ? "" : value.name, selected);
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
		resultsScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		resultsScroll.setVisible(false);
		resultsScroll.setName("results");
		picker.add(resultsScroll);
		picker.add(Box.createVerticalStrut(6));
		picker.add(selectedItemsPanel);
		picker.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return picker;
	}

	void setStatus(String text, Tone tone)
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
		List<JsonObject> sorted = objects(events);
		sorted.sort(Comparator
			.comparingInt((JsonObject e) -> statusOrder(str(e, "status")))
			.thenComparing(TsgHubUi::eventStart, Comparator.nullsLast(Comparator.naturalOrder())));
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
			if (selected) card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT), BorderFactory.createEmptyBorder(6, 5, 6, 7)));
			card.setBackground(selected ? CARD_HOVER : BACKGROUND);
			card.add(eventLines(event, plugin::getCoinImage), BorderLayout.CENTER);
			card.setToolTipText(eventWhen(event));
			clickable(card, () -> {
				selectedEventId = id;
				setManagedEvents(managedEvents);
				plugin.selectEvent(id);
			});
			eventList.add(fitHeight(card));
			eventList.add(Box.createVerticalStrut(6));
		}
		if (sorted.isEmpty())
		{
			eventList.add(wrapped("No events yet. Create one to get started.", MUTED, smallFont(), LIST_W - 30));
		}
		eventList.revalidate();
		eventList.repaint();
	}

	void openOrganizerEvent(JsonObject event)
	{
		boolean sameEvent = currentEvent != null && str(currentEvent, "id").equals(str(event, "id"));
		selectedEventId = str(event, "id");
		showEvent(event);
		if (!sameEvent)
		{
			viewedTeamId = "";
			String type = str(event, "type");
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
		String state = str(event, "status");
		String type = str(event, "type");
		boolean bingo = type.isEmpty() || "bingo".equals(type);
		boolean competition = "skill".equals(type) || "boss".equals(type);
		String counts = bingo ? array(event, "teams").size() + " teams · " + array(event, "tasks").size() + " tasks"
			: competition ? integer(event, "participants", 0) + ("scheduled".equals(state) ? " signed up" : " taking part") : "";
		String relative = eventRelative(event);
		String detail = bingo ? "Bingo" : eventDetail(event);
		List<Long> prizes = eventPrizes(event);
		eventMeta.setText((detail.isEmpty() ? "" : detail + " · ") + (relative.isEmpty() ? statusLabel(state) : capitalize(relative))
			+ (counts.isEmpty() ? "" : " · " + counts)
			+ (bool(event, "hideScores") ? " · Scores hidden from players" : "")
			+ (bool(event, "hidden") ? " · Hidden from players" : ""));
		eventSchedule.setText(eventWhen(event) + (prizes.isEmpty() ? "" : " · Prize pool " + formatGp(prizeTotal(prizes)) + " (" + prizeSummary(prizes) + ")"));
		publishEvent.setVisible(bool(event, "hidden"));
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
		if (currentEvent != null && str(currentEvent, "id").equals(eventId))
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
		if (confirmDelete(this, "End event", message, "End event")) plugin.endEvent(str(currentEvent, "id"));
	}

	private void confirmDeleteEvent()
	{
		if (currentEvent == null) return;
		int teams = array(currentEvent, "teams").size();
		int tasks = array(currentEvent, "tasks").size();
		int members = array(currentEvent, "members").size();
		String message = "Delete \"" + TsgHubUi.eventName(currentEvent) + "\"?\n\n"
			+ "This permanently removes its " + plural(teams, "team") + ", " + plural(tasks, "task") + " and all progress.\n"
			+ (members > 0 ? plural(members, "player") + " will be disconnected.\n" : "")
			+ "This can't be undone.";
		if (confirmDelete(this, "Delete event", message, "Delete event")) plugin.deleteEvent(str(currentEvent, "id"));
	}

	private void confirmDeleteTeam(JsonObject team, int members)
	{
		String message = "Delete team \"" + str(team, "name") + "\"?\n\n"
			+ (members > 0 ? "Its " + plural(members, "member") + " will be removed from the event, and " : "")
			+ (members > 0 ? "its" : "Its") + " progress will be deleted. Its invite code stops working.\n"
			+ "This can't be undone.";
		if (confirmDelete(this, "Delete team", message, "Delete team")) plugin.deleteTeam(str(team, "id"));
	}

	private static boolean reconcilable(JsonObject task)
	{
		JsonObject config = object(task, "config");
		String type = str(task, "type");
		return "drop".equals(type) || "kill".equals(type) && !"chat".equals(str(config, "signal"));
	}

	void confirmReconcile(String taskId, JsonObject preview)
	{
		JsonArray claims = array(preview, "claims");
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
			message.append(str(claim, "displayName")).append(" (").append(str(claim, "teamName")).append("): ")
				.append(str(claim, "name")).append(" x").append(integer(claim, "quantity", 1)).append("\n");
		}
		if (claims.size() > shown) message.append("...and ").append(claims.size() - shown).append(" more\n");
		int choice = JOptionPane.showConfirmDialog(this, message.toString(), "Reconcile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (choice == JOptionPane.OK_OPTION) plugin.reconcileTask(taskId, false);
	}

	private void confirmDeleteTask(JsonObject task)
	{
		String message = "Delete task \"" + str(task, "title") + "\"?\n\n"
			+ "Every team's progress and claims for it will be deleted.\nThis can't be undone.";
		if (confirmDelete(this, "Delete task", message, "Delete task")) plugin.deleteTask(str(task, "id"));
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
		JsonArray teams = array(currentEvent, "teams");
		JsonArray scores = array(currentEvent, "teamScores");
		int totalTasks = array(currentEvent, "tasks").size();
		List<JsonObject> ranked = objects(teams);
		ranked.sort(Comparator.comparingInt((JsonObject t) -> integer(scoreFor(scores, str(t, "id")), "points", 0)).reversed()
			.thenComparing(t -> str(t, "name"), String.CASE_INSENSITIVE_ORDER));

		if (ranked.isEmpty())
		{
			teamsTab.add(wrapped("No teams yet. Add your first team below, then share its code with the players on it.", MUTED, plainFont(), DETAIL_TEXT_W));
			teamsTab.add(Box.createVerticalStrut(8));
		}
		for (int i = 0; i < ranked.size(); i++)
		{
			JsonObject team = ranked.get(i);
			String teamId = str(team, "id");
			JsonObject score = scoreFor(scores, teamId);
			int members = countMembers(teamId);

			JPanel card = card();
			card.setLayout(new BorderLayout(10, 4));
			card.add(rankLabel(i + 1, boldFont().deriveFont(18f), 20), BorderLayout.WEST);
			JPanel text = stack();
			text.add(boldLabel(str(team, "name")));
			text.add(caption(integer(score, "points", 0) + " pts · " + integer(score, "completedTasks", 0) + "/" + totalTasks + " tasks · "
				+ members + (members == 1 ? " member" : " members")));
			card.add(text, BorderLayout.CENTER);

			JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			String code = str(team, "inviteCode");
			if (!code.isEmpty())
			{
				JLabel codeLabel = label(code, ACCENT, new Font(Font.MONOSPACED, Font.BOLD, 13));
				codeLabel.setToolTipText("Team invite code");
				actions.add(codeLabel);
				JButton copy = button("Copy code");
				copy.addActionListener(e -> {
					copyToClipboard(code);
					flashText(copy, "Copied!", 1500);
				});
				actions.add(copy);
			}
			JButton rename = button("Rename");
			rename.addActionListener(e -> promptRename(teamId, str(team, "name")));
			actions.add(rename);
			JButton view = button("Details");
			view.addActionListener(e -> {
				viewedTeamId = teamId;
				renderTeams();
			});
			actions.add(view);
			JButton delete = iconButton(new TrashIcon(), "Delete team");
			delete.addActionListener(e -> confirmDeleteTeam(team, members));
			actions.add(delete);
			card.add(actions, BorderLayout.EAST);
			teamsTab.add(fitHeight(card));
			teamsTab.add(Box.createVerticalStrut(5));
		}

		teamsTab.add(Box.createVerticalStrut(8));
		JButton add = button("Add team");
		add.addActionListener(e -> addTeam());
		JPanel addField = panel(new BorderLayout(0, 3));
		addField.add(caption("New team name"), BorderLayout.NORTH);
		addField.add(row(newTeamName, add), BorderLayout.CENTER);
		teamsTab.add(fitHeight(addField));
		refresh(teamsTab);
	}

	private void renderTeamDetail(JsonObject team)
	{
		String teamId = str(team, "id");
		JButton back = button("All teams");
		back.setIcon(new BackIcon());
		back.addActionListener(e -> {
			viewedTeamId = "";
			renderTeams();
		});
		teamsTab.add(back);
		teamsTab.add(Box.createVerticalStrut(8));
		JsonObject score = scoreFor(array(currentEvent, "teamScores"), teamId);
		teamsTab.add(label(str(team, "name"), TEXT, boldFont().deriveFont(18f)));
		String code = str(team, "inviteCode");
		teamsTab.add(caption(integer(score, "points", 0) + " pts" + (code.isEmpty() ? "" : " · code " + code)));

		teamsTab.add(sectionTitle("Members"));
		List<String> roster = new ArrayList<>();
		for (JsonObject member : objects(array(currentEvent, "members")))
			if (str(member, "teamId").equals(teamId)) roster.add(str(member, "displayName"));
		roster.sort(String.CASE_INSENSITIVE_ORDER);
		teamsTab.add(wrapped(roster.isEmpty() ? "Nobody has joined yet. Share the team code above." : String.join(", ", roster), TEXT, plainFont(), DETAIL_TEXT_W));

		teamsTab.add(sectionTitle("Task progress"));
		JsonArray progressRows = array(score, "tasks");
		JsonArray tasks = array(currentEvent, "tasks");
		if (tasks.size() == 0) teamsTab.add(label("No tasks yet.", MUTED, plainFont()));
		for (JsonObject task : objects(tasks))
		{
			String taskId = str(task, "id");
			JsonObject progress = progressFor(progressRows, taskId);
			boolean completed = bool(progress, "completed");

			JPanel card = card();
			JPanel text = stack();
			JLabel name = label(str(task, "title"), completed ? MUTED : TEXT, boldFont());
			if (completed) name.setIcon(new CheckIcon());
			text.add(name);
			String progressScope = str(progress, "scope");
			boolean perMember = "individual".equals(progressScope) || "solo".equals(progressScope);
			if (completed)
			{
				String by = str(progress, "completedBy");
				boolean creditedToOrganizer = bool(progress, "override") && !bool(progress, "overrideCredited");
				text.add(label(completedLine(progress), SUCCESS, smallFont()));
				JsonObject manual = overrideClaim(teamId, taskId);
				if (manual != null)
				{
					JsonObject evidence = manual.getAsJsonObject("evidence");
					String markedBy = str(evidence, "markedBy").isEmpty() ? str(manual, "displayName") : str(evidence, "markedBy");
					String how = !creditedToOrganizer && markedBy.equalsIgnoreCase(by) ? "Marked complete manually" : "Marked complete by " + markedBy;
					String note = str(evidence, "note");
					text.add(wrapped(how + (note.isEmpty() ? "" : " · Reason: " + note), WARNING, smallFont(), DETAIL_TEXT_W - 140));
				}
				if (!perMember && !contributorsText(progress, ", ", 12).isEmpty())
				{
					text.add(wrapped("Contributors: " + contributorsText(progress, ", ", 12), MUTED, smallFont(), DETAIL_TEXT_W - 140));
				}
			}
			else if (perMember)
			{
				String leader = str(progress, "leader");
				if ("solo".equals(progressScope) && !leader.isEmpty())
					text.add(caption("Leader: " + leader + " (" + integer(progress, "progress", 0) + "/" + integer(progress, "target", 1) + ")"));
				JsonArray entries = array(progress, "members");
				if (entries.size() == 0) text.add(caption("No members yet."));
				for (JsonObject entry : objects(entries)) text.add(caption(str(entry, "displayName") + ": " + progressText(entry)));
			}
			else
			{
				List<SetLine> sets = setLines(progress, true);
				if (!sets.isEmpty())
				{
					int started = 0;
					for (SetLine set : sets)
					{
						if (set.have == 0 && started > 0) continue;
						if (set.have > 0) started++;
						String line = set.name + " " + set.have + "/" + set.target
							+ (set.found.isEmpty() ? "" : " · have " + String.join(", ", set.found))
							+ (set.needed.isEmpty() ? "" : " · need " + String.join(", ", set.needed));
						text.add(wrapped(line, MUTED, smallFont(), DETAIL_TEXT_W - 140));
						if (set.have == 0) break;
					}
					int notStarted = sets.size() - Math.max(1, started);
					if (notStarted > 0) text.add(caption("+" + notStarted + (notStarted == 1 ? " more set" : " more sets") + " not started"));
				}
				else
				{
					text.add(caption(progressText(progress)));
					String amounts = contributorsText(progress, ", ", 12);
					if (!amounts.isEmpty()) text.add(wrapped("Contributors: " + amounts, MUTED, smallFont(), DETAIL_TEXT_W - 140));
				}
			}
			card.add(text, BorderLayout.CENTER);
			if (!completed)
			{
				JButton complete = button("Mark complete");
				complete.setToolTipText("Manually complete this task for " + str(team, "name"));
				complete.addActionListener(e -> promptComplete(taskId, teamId, str(task, "title"), str(team, "name")));
				card.add(north(complete), BorderLayout.EAST);
			}
			teamsTab.add(fitHeight(card));
			teamsTab.add(Box.createVerticalStrut(4));
		}
	}

	private void addTeam()
	{
		String name = newTeamName.getText().trim();
		if (name.isEmpty())
		{
			setStatus("Enter a team name first.", Tone.ERROR);
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
		for (JsonObject member : objects(array(currentEvent, "members")))
			if (str(member, "teamId").equals(teamId)) roster.add(new String[] {str(member, "id"), str(member, "displayName")});
		roster.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
		roster.forEach(credit::addItem);
		credit.setRenderer((list, value, index, selected, focus) -> listRow(value == null ? "" : value[1], selected));
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
		for (JsonObject claim : objects(array(currentEvent, "claims")))
		{
			JsonObject evidence = object(claim, "evidence");
			if (str(claim, "teamId").equals(teamId) && str(claim, "taskId").equals(taskId)
				&& "organizer".equals(str(claim, "source")) && "approved".equals(str(claim, "status"))
				&& bool(evidence, "completedOverride")) return claim;
		}
		return null;
	}

	private void renderLeaderboard()
	{
		leaderboardTab.removeAll();
		String type = str(currentEvent, "type");
		if (!"skill".equals(type) && !"boss".equals(type)) return;
		JsonObject config = eventConfig(currentEvent);
		String unit = "skill".equals(type) ? "XP" : "kills";
		boolean started = !"scheduled".equals(str(currentEvent, "status"));
		int participants = integer(currentEvent, "participants", 0);
		String what = "skill".equals(type) ? skillName(str(config, "skill")) + " XP gained" : str(config, "npcName") + " kills";
		leaderboardTab.add(caption(started ? what + " · " + participants + " taking part"
			: participants + " signed up · counting starts " + eventStartWhen(currentEvent)));
		leaderboardTab.add(Box.createVerticalStrut(8));
		JsonArray rows = array(currentEvent, "leaderboard");
		if (rows.size() == 0) leaderboardTab.add(wrapped("Nobody has joined yet. Players join with one click from the TSG Hub sidebar.", MUTED, plainFont(), DETAIL_TEXT_W));
		for (int i = 0; i < rows.size(); i++)
		{
			JsonObject row = rows.get(i).getAsJsonObject();
			int rank = integer(row, "rank", i + 1);
			JPanel card = card();
			card.setLayout(new BorderLayout(10, 0));
			if (started)
			{
				card.add(rankLabel(rank, boldFont(), 28), BorderLayout.WEST);
			}
			card.add(boldLabel(str(row, "displayName")), BorderLayout.CENTER);
			if (started)
			{
				boolean tracking = bool(row, "tracking");
				String gained = tracking ? "+" + String.format("%,d", integer(row, "gained", 0)) + " " + unit : "Waiting for first " + ("XP".equals(unit) ? "XP" : "kill");
				card.add(label(gained, !tracking ? MUTED : rank == 1 ? ACCENT : TEXT, tracking ? boldFont() : smallFont()), BorderLayout.EAST);
			}
			leaderboardTab.add(fitHeight(card));
			leaderboardTab.add(Box.createVerticalStrut(6));
		}
		refresh(leaderboardTab);
	}

	private void renderDetails()
	{
		detailsTab.removeAll();
		if (!"drop-party".equals(str(currentEvent, "type"))) return;
		JsonObject config = eventConfig(currentEvent);
		String countdown = capitalize(eventRelative(currentEvent));
		if (!countdown.isEmpty()) detailsTab.add(label(countdown, "Ended".equals(countdown) ? MUTED : SUCCESS, boldFont()));
		detailsTab.add(Box.createVerticalStrut(6));
		detailsTab.add(wrapped("When: " + eventWhen(currentEvent), TEXT, plainFont(), DETAIL_TEXT_W));
		if (integer(config, "world", 0) > 0) detailsTab.add(label("World: " + integer(config, "world", 0), TEXT, plainFont()));
		if (!str(config, "location").isEmpty()) detailsTab.add(label("Where: " + str(config, "location"), TEXT, plainFont()));
		if (!str(config, "host").isEmpty()) detailsTab.add(label("Host: " + str(config, "host"), TEXT, plainFont()));
		if (!str(config, "notes").isEmpty()) detailsTab.add(wrapped("Notes: " + str(config, "notes"), MUTED, plainFont(), DETAIL_TEXT_W));
		detailsTab.add(Box.createVerticalStrut(10));
		detailsTab.add(caption("Players see this in the TSG Hub sidebar. Use Edit event to change it."));
		refresh(detailsTab);
	}

	private void renderTasks()
	{
		tasksTab.removeAll();
		JPanel top = panel(new BorderLayout());
		JsonArray tasks = array(currentEvent, "tasks");
		top.add(caption(tasks.size() + (tasks.size() == 1 ? " task" : " tasks") + ", shared by every team"), BorderLayout.CENTER);
		JButton add = primaryButton("New task");
		add.addActionListener(e -> beginNewTask());
		top.add(add, BorderLayout.EAST);
		tasksTab.add(fitHeight(top));
		tasksTab.add(Box.createVerticalStrut(8));
		if (tasks.size() == 0)
		{
			tasksTab.add(wrapped("No tasks yet. Choose New task to add the first one.", MUTED, plainFont(), DETAIL_TEXT_W));
		}
		for (JsonObject task : objects(tasks))
		{
			JPanel card = card();
			JPanel text = stack();
			text.add(boldLabel(str(task, "title")));
			JPanel meta = panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			meta.add(badge(integer(task, "points", 1) + " pts", ACCENT));
			meta.add(badge(taskTypeLabel(task), MUTED));
			String scopeTag = "individual".equals(str(task, "scope")) ? "Everyone" : "solo".equals(str(task, "scope")) ? "Solo" : "Team";
			meta.add(badge(scopeTag, MUTED));
			String summary = taskSummary(task);
			if (!summary.isEmpty()) meta.add(caption(summary));
			text.add(meta);
			String description = str(task, "description");
			if (!description.isEmpty()) text.add(wrapped(description, MUTED, smallFont(), DETAIL_TEXT_W - 80));
			card.add(text, BorderLayout.CENTER);
			JButton edit = button("Edit");
			edit.addActionListener(e -> beginTaskEdit(task));
			JButton delete = iconButton(new TrashIcon(), "Delete task");
			delete.addActionListener(e -> confirmDeleteTask(task));
			JPanel taskButtons = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			if (reconcilable(task))
			{
				JButton reconcile = button("Reconcile");
				reconcile.setToolTipText("Credit matching loot players received earlier in this event");
				reconcile.addActionListener(e -> plugin.reconcileTask(str(task, "id"), true));
				taskButtons.add(reconcile);
			}
			taskButtons.add(edit);
			taskButtons.add(delete);
			card.add(north(taskButtons), BorderLayout.EAST);
			tasksTab.add(fitHeight(card));
			tasksTab.add(Box.createVerticalStrut(5));
		}
		refresh(tasksTab);
	}

	private String taskSummary(JsonObject task)
	{
		JsonObject config = object(task, "config");
		int count = integer(config, "targetCount", 1);
		switch (str(task, "type"))
		{
			case "kill": return count + "x " + str(config, "npcName");
			case "raid":
				return count + "x " + raidSummary(strings(array(config, "modes")));
			case "drop":
				JsonArray sets = array(config, "itemGroups");
				if (sets.size() == 1) return nameOrPieces(sets.get(0).getAsJsonArray());
				if (sets.size() > 1) return "any of " + sets.size() + " sets";
				if (bool(config, "requireAllItems")) return array(config, "itemNames").size() + " pieces";
				if (!str(config, "itemGroup").isEmpty()) return count + "x";
				return count + "x from " + array(config, "itemNames").size() + " items";
			default: return "";
		}
	}

	private void renderClaims()
	{
		claimsTab.removeAll();
		JsonArray claims = array(currentEvent, "claims");
		JsonArray tasks = array(currentEvent, "tasks");
		int pending = 0;
		for (JsonObject claim : objects(claims))
		{
			if (!"pending".equals(str(claim, "status"))) continue;
			pending++;
			String taskTitle = "Task";
			for (JsonObject task : objects(tasks))
				if (str(task, "id").equals(str(claim, "taskId"))) taskTitle = str(task, "title");
			JsonObject team = findTeam(str(claim, "teamId"));
			JPanel card = card();
			JPanel text = stack();
			text.add(boldLabel(taskTitle));
			text.add(caption(str(claim, "displayName") + (team == null ? "" : " · " + str(team, "name"))));
			JsonObject evidence = object(claim, "evidence");
			String note = str(evidence, "note");
			if (!note.isEmpty())
			{
				text.add(Box.createVerticalStrut(3));
				text.add(wrapped(note, TEXT, plainFont(), DETAIL_TEXT_W - 150));
			}
			card.add(text, BorderLayout.CENTER);
			JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			Matcher link = Pattern.compile("https?://\\S+").matcher(note);
			if (link.find())
			{
				String url = link.group();
				JButton open = button("Open link");
				open.setToolTipText(url);
				open.addActionListener(e -> LinkBrowser.browse(url));
				actions.add(open);
			}
			String claimId = str(claim, "id");
			JButton reject = button("Reject");
			reject.addActionListener(e -> {
				if (JOptionPane.showConfirmDialog(this, "Reject this claim?", "Reject claim", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
					plugin.reviewClaim(claimId, false);
			});
			JButton approve = primaryButton("Approve");
			approve.addActionListener(e -> plugin.reviewClaim(claimId, true));
			actions.add(reject);
			actions.add(approve);
			card.add(north(actions), BorderLayout.EAST);
			claimsTab.add(fitHeight(card));
			claimsTab.add(Box.createVerticalStrut(5));
		}
		if (pending == 0)
		{
			claimsTab.add(wrapped("Nothing to review. Proof submitted for manual tasks shows up here.", MUTED, plainFont(), DETAIL_TEXT_W));
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
		eventName.setText(str(currentEvent, "name"));
		Instant start = TsgHubUi.eventStart(currentEvent);
		Instant end = TsgHubUi.eventEnd(currentEvent);
		setEventTimes(start == null ? null : start.atZone(ZoneId.systemDefault()), end == null ? null : end.atZone(ZoneId.systemDefault()));
		eventHideScores.setSelected(bool(currentEvent, "hideScores"));
		eventHidden.setSelected(bool(currentEvent, "hidden"));
		String type = str(currentEvent, "type");
		eventType.setSelectedIndex(Math.max(0, Arrays.asList(EVENT_TYPES).indexOf(type.isEmpty() ? "bingo" : type)));
		eventType.setEnabled(false);
		JsonObject config = eventConfig(currentEvent);
		eventSkill.setSelectedItem(skillName(str(config, "skill")));
		eventBoss.setText(str(config, "npcName"));
		if ("loot".equals(str(config, "signal"))) eventSignalLoot.setSelected(true);
		else eventSignalKc.setSelected(true);
		partyWorld.setText(integer(config, "world", 0) > 0 ? String.valueOf(integer(config, "world", 0)) : "");
		partyLocation.setText(str(config, "location"));
		partyHost.setText(str(config, "host"));
		partyNotes.setText(str(config, "notes"));
		List<Long> prizes = eventPrizes(currentEvent);
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
			long gp = parseMillions(prizeFields[i].getText());
			if (gp < 0) { eventFormFailed(place(i + 1) + " prize must be a number of millions, like 20 or 2.5.", prizeFields[i]); return; }
			if (gp > 0 && prizes.size() < i) { eventFormFailed("Fill in the prizes in order, starting with 1st.", prizeFields[i]); return; }
			if (gp > 0) prizes.add(gp);
		}
		eventFormError.setVisible(false);
		if (editingEvent && currentEvent != null) plugin.updateEvent(str(currentEvent, "id"), name, start, end, eventHideScores.isSelected(), eventHidden.isSelected(), "bingo".equals(type) ? null : config, prizes);
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
		eventZoneHint.setText(html(escape("Times are in your time zone, " + zoneLabel(ZoneId.systemDefault(), clock.instant())
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
		editingTaskId = str(task, "id");
		taskEditorTitle.setText("Edit task");
		taskSave.setText("Save task");
		taskTitle.setText(str(task, "title"));
		taskDescription.setText(str(task, "description"));
		String scope = str(task, "scope");
		if ("individual".equals(scope)) scopeIndividual.setSelected(true);
		else if ("solo".equals(scope)) scopeSolo.setSelected(true);
		else scopeTeam.setSelected(true);
		taskPoints.setText(String.valueOf(integer(task, "points", 1)));
		JsonObject config = object(task, "config");
		targetCount.setText(String.valueOf(integer(config, "targetCount", 1)));
		String type = str(task, "type");
		TaskType selected = "kill".equals(type) ? TaskType.KILL
			: "raid".equals(type) ? TaskType.RAID
			: "drop".equals(type) ? (bool(config, "requireAllItems") || array(config, "itemGroups").size() > 0 ? TaskType.ITEM_SET : TaskType.DROP)
			: TaskType.MANUAL;
		taskType.setSelectedItem(selected);

		taskBoss.setText(str(config, "npcName"));
		if ("loot".equals(str(config, "signal"))) signalLoot.setSelected(true);
		else signalKc.setSelected(true);

		List<String> modes = strings(array(config, "modes"));
		selectRaidModes(modes);

		String group = str(config, "itemGroup");
		if ("jar".equals(group)) dropJar.setSelected(true);
		else if ("pet".equals(group)) dropPet.setSelected(true);
		else dropSpecific.setSelected(true);

		resetItems();
		JsonArray groups = array(config, "itemGroups");
		if (groups.size() > 0)
		{
			itemGroups.clear();
			for (int g = 0; g < groups.size(); g++)
			{
				List<TsgHubPlugin.ItemSuggestion> items = new ArrayList<>();
				for (JsonObject item : objects(groups.get(g).getAsJsonArray()))
					items.add(new TsgHubPlugin.ItemSuggestion(integer(item, "id", 0), str(item, "name")));
				itemGroups.add(items);
			}
		}
		else
		{
			JsonArray names = array(config, "itemNames");
			JsonArray ids = array(config, "itemIds");
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
			for (TsgHubPlugin.ItemSuggestion item : itemGroups.get(0)) selectedItemsPanel.add(fitHeight(itemRow(item, itemGroups.get(0))));
			if (itemGroups.get(0).isEmpty()) selectedItemsPanel.add(caption("No items added yet."));
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
		card.setBackground(CARD);
		boolean active = index == activeGroup.getSelectedIndex() && itemGroups.size() > 1;
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, active ? ACCENT : CARD),
			BorderFactory.createEmptyBorder(6, 8, 6, 6)));
		JPanel header = row();
		String name = TsgHubItemSets.nameFor(ids(group));
		header.add(boldLabel((name.isEmpty() ? "Set " + (index + 1) : name) + "  "), BorderLayout.WEST);
		header.add(caption(group.size() == 1 ? "1 piece" : group.size() + " pieces"), BorderLayout.CENTER);
		if (itemGroups.size() > 1)
		{
			JButton remove = iconButton(new RemoveIcon(), "Remove this set");
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
			card.add(caption("Empty. Search above to add pieces."), BorderLayout.CENTER);
		}
		else
		{
			JPanel pieces = panel(new GridLayout(0, 2, 4, 2));
			for (TsgHubPlugin.ItemSuggestion item : group) pieces.add(itemRow(item, group));
			card.add(pieces, BorderLayout.CENTER);
		}
		JPanel wrap = panel(new BorderLayout());
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		wrap.add(card, BorderLayout.CENTER);
		return fitHeight(wrap);
	}

	private JPanel itemRow(TsgHubPlugin.ItemSuggestion item, List<TsgHubPlugin.ItemSuggestion> owner)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
		JLabel name = label(item.name, TEXT, plainFont());
		name.setIcon(iconFor(item.id, selectedItemsPanel));
		row.add(name, BorderLayout.CENTER);
		JButton remove = iconButton(new RemoveIcon(), "Remove " + item.name);
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

	private static final class RemoveIcon extends HoverIcon
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
		JPanel row = panel(new BorderLayout(0, 3));
		row.add(caption(label), BorderLayout.NORTH);
		row.add(input, BorderLayout.CENTER);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return fitHeight(row);
	}

	private static JPanel radioField(String label, JRadioButton... options)
	{
		ButtonGroup group = new ButtonGroup();
		JPanel choices = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		for (JRadioButton option : options)
		{
			group.add(option);
			choices.add(plain(option, TEXT));
			choices.add(Box.createHorizontalStrut(10));
		}
		return field(label, choices);
	}

	private static JPanel footer(JButton cancel, JButton save)
	{
		JPanel footer = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.add(cancel);
		footer.add(save);
		return fitHeight(footer);
	}

	private static JComponent narrowPage(JPanel form)
	{
		JPanel holder = panel(new BorderLayout());
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
		WidthTrackingPanel page = new WidthTrackingPanel();
		page.add(holder);
		return scroll(page);
	}

	private void showFormError(JLabel label, String message)
	{
		label.setText(html(escape(message), DETAIL_TEXT_W));
		label.setVisible(true);
		label.revalidate();
	}

	private JsonObject findTeam(String teamId)
	{
		for (JsonObject team : objects(array(currentEvent, "teams"))) if (str(team, "id").equals(teamId)) return team;
		return null;
	}

	private int countMembers(String teamId)
	{
		int count = 0;
		for (JsonObject member : objects(array(currentEvent, "members"))) if (str(member, "teamId").equals(teamId)) count++;
		return count;
	}

	private static String progressText(JsonObject progress)
	{
		if (bool(progress, "completed")) return "Complete";
		if (bool(progress, "pending")) return "Awaiting review";
		JsonObject best = bestSet(progress);
		if (best != null)
		{
			int options = array(progress, "alternatives").size();
			List<String> missing = missingPieces(array(best, "items"));
			return TsgHubUi.setName(best) + " " + integer(best, "progress", 0) + "/" + integer(best, "target", 1)
				+ (options > 1 ? " (closest of " + options + ")" : "") + (missing.isEmpty() ? "" : " · need " + String.join(", ", missing));
		}
		return integer(progress, "progress", 0) + "/" + integer(progress, "target", 1);
	}

	private static List<String> strings(JsonArray array)
	{
		List<String> values = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) values.add(array.get(i).getAsString());
		return values;
	}

	private static String nameOrPieces(JsonArray items)
	{
		String name = TsgHubItemSets.nameFor(itemIds(items));
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
