package com.tsghub;

import static com.tsghub.TsgHubBingo.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.util.AsyncBufferedImage;

final class TsgHubTaskEditor
{
	private final TsgHubPlugin plugin;
	private final TsgHubPanel shell;

	TsgHubTaskEditor(TsgHubPlugin plugin, TsgHubPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		itemGroups.add(new ArrayList<>());
	}

	private enum TaskType
	{
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
	private final DefaultListModel<ItemSuggestion> itemResults = new DefaultListModel<>();
	private final JList<ItemSuggestion> itemResultList = new JList<>(itemResults);
	private final JScrollPane resultsScroll = new JScrollPane(itemResultList);
	private final JLabel itemSearchHint = caption("");
	private final JComboBox<String> activeGroup = new JComboBox<>();
	private final JPanel selectedItemsPanel = stack();
	private final List<List<ItemSuggestion>> itemGroups = new ArrayList<>();
	private final JTextField targetCount = new JTextField("1");
	private final JLabel targetCountCaption = caption("How many");
	private final JTextField taskPoints = new JTextField("1");
	private final JLabel taskFormError = label("", TsgHubTheme.ERROR, smallFont());
	private final JButton taskSave = primaryButton("Add task");
	private final Map<Integer, ImageIcon> itemIcons = new HashMap<>();
	private final Timer searchDebounce = new Timer(300, e -> runItemSearch());
	private final JPanel numbersRow = panel(new GridLayout(1, 2, 10, 0));
	private JPanel pointsRow;
	private JPanel scopeRow;
	private JPanel bossRow;
	private JPanel signalRow;
	private JPanel raidRow;
	private JPanel dropKindRow;
	private JPanel presetRow;
	private JPanel groupRow;
	private JPanel itemPickerRow;
	private JPanel countRow;
	private long itemSearchRequestId;
	String editingTaskId = "";

	JComponent buildTaskEditor()
	{
		JPanel form = stack();
		form.add(taskEditorTitle);
		form.add(field("Title", taskTitle));
		form.add(field("Description (optional)", taskDescription));
		form.add(field("Type", taskType));

		scopeRow = TsgHubPanel.radioField("Progress", scopeTeam, scopeIndividual, scopeSolo);
		scopeTeam.setToolTipText("Everyone's progress adds up. 50 kills can be split across the team.");
		scopeIndividual.setToolTipText("Each member has to reach the target on their own. The tile completes when all of them have.");
		scopeSolo.setToolTipText("Members don't share progress. The tile completes when any one member reaches the target alone.");
		form.add(scopeRow);

		bossRow = taskBoss;
		form.add(bossRow);
		signalRow = TsgHubPanel.radioField("Track kills using", signalKc, signalLoot);
		form.add(signalRow);

		raidRow = buildRaidPicker();
		form.add(raidRow);

		dropKindRow = TsgHubPanel.radioField("What counts", dropSpecific, dropJar, dropPet);
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
		form.add(gap(GAP_L));
		JButton cancel = onClick(button("Cancel"), () -> closeTaskEditor());
		taskSave.addActionListener(e -> submitTask());
		form.add(TsgHubPanel.footer(cancel, taskSave));

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

	private static List<Integer> ids(List<ItemSuggestion> items)
	{
		List<Integer> ids = new ArrayList<>();
		for (ItemSuggestion item : items) ids.add(item.id);
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
		picker.add(gap(GAP_M));
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
		refresh(raidModeChoices);
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
		picker.add(gap(GAP_XS));
		itemSearch.setToolTipText("Type at least 2 letters to search RuneLite's item catalog");
		onTextChange(itemSearch, searchDebounce::restart);
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
		resultsScroll.setBorder(BorderFactory.createLineBorder(BORDER));
		resultsScroll.setVisible(false);
		picker.add(resultsScroll);
		picker.add(gap(GAP_S));
		picker.add(selectedItemsPanel);
		picker.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return picker;
	}

	void taskFormFailed(String message)
	{
		showError(taskFormError, message, TsgHubPanel.DETAIL_TEXT_W);
	}

	void finishTaskEdit()
	{
		closeTaskEditor();
		shell.tabs.select(shell.tasksTabButton);
	}

	void beginTaskEdit(JsonObject task)
	{
		editingTaskId = str(task, "id");
		taskEditorTitle.setText(editingTaskId.isEmpty() ? "New task" : "Edit task");
		taskSave.setText(editingTaskId.isEmpty() ? "Add task" : "Save task");
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
		TaskType selected = "raid".equals(type) ? TaskType.RAID
			: "drop".equals(type) ? (bool(config, "requireAllItems") || array(config, "itemGroups").size() > 0 ? TaskType.ITEM_SET : TaskType.DROP)
			: TaskType.KILL;
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
				List<ItemSuggestion> items = new ArrayList<>();
				for (JsonObject item : objects(groups.get(g).getAsJsonArray()))
					items.add(new ItemSuggestion(integer(item, "id", 0), str(item, "name")));
				itemGroups.add(items);
			}
		}
		else
		{
			JsonArray names = array(config, "itemNames");
			JsonArray ids = array(config, "itemIds");
			for (int i = 0; i < names.size(); i++)
				itemGroups.get(0).add(new ItemSuggestion(i < ids.size() ? ids.get(i).getAsInt() : 0, names.get(i).getAsString()));
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
		shell.detailLayout.show(shell.detail, "task-editor");
		taskTitle.requestFocusInWindow();
	}

	private void closeTaskEditor()
	{
		editingTaskId = "";
		searchDebounce.stop();
		shell.detailLayout.show(shell.detail, shell.currentEvent == null ? "empty" : "event");
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
		shell.revalidate();
		shell.repaint();
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
		JsonObject body = new JsonObject();
		body.addProperty("title", taskTitle.getText().trim());
		body.addProperty("description", taskDescription.getText().trim());
		body.addProperty("type", type == TaskType.KILL ? "kill" : type == TaskType.RAID ? "raid" : "drop");
		body.addProperty("scope", scopeIndividual.isSelected() ? "individual" : scopeSolo.isSelected() ? "solo" : "team");
		body.addProperty("points", positiveInt(taskPoints.getText()));
		JsonObject cfg = new JsonObject();
		if (type != TaskType.ITEM_SET) cfg.addProperty("targetCount", positiveInt(targetCount.getText()));
		if (type == TaskType.KILL)
		{
			cfg.addProperty("npcName", taskBoss.getText().trim());
			cfg.addProperty("signal", signalLoot.isSelected() ? "loot" : "chat");
		}
		else if (type == TaskType.RAID)
		{
			JsonArray modes = new JsonArray();
			selectedRaidModes().forEach(mode -> modes.add(mode.toLowerCase(Locale.ROOT)));
			cfg.add("modes", modes);
			cfg.addProperty("clanOnly", true);
		}
		else if (type == TaskType.DROP && (dropJar.isSelected() || dropPet.isSelected())) cfg.addProperty("itemGroup", dropJar.isSelected() ? "jar" : "pet");
		else
		{
			JsonArray itemNames = new JsonArray();
			JsonArray itemIds = new JsonArray();
			JsonArray groups = new JsonArray();
			for (List<ItemSuggestion> group : itemGroups)
			{
				JsonArray groupItems = new JsonArray();
				for (ItemSuggestion item : group)
				{
					itemNames.add(item.name);
					itemIds.add(item.id);
					JsonObject json = new JsonObject();
					json.addProperty("name", item.name);
					json.addProperty("id", item.id);
					groupItems.add(json);
				}
				if (groupItems.size() > 0) groups.add(groupItems);
			}
			cfg.add("itemNames", itemNames);
			cfg.add("itemIds", itemIds);
			if (type == TaskType.ITEM_SET) cfg.add("itemGroups", groups);
		}
		body.add("config", cfg);
		plugin.organizer().saveTask(editingTaskId, body);
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

	int positiveInt(String text)
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

	private void runItemSearch()
	{
		String query = itemSearch.getText().trim();
		JScrollPane results = resultsScroll;
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
		plugin.organizer().searchItems(query, found -> {
			if (requestId != itemSearchRequestId) return;
			itemResults.clear();
			for (ItemSuggestion item : found) itemResults.addElement(item);
			itemResultList.setVisibleRowCount(Math.max(1, Math.min(6, found.size())));
			results.setVisible(!found.isEmpty());
			if (!found.isEmpty()) itemResultList.setSelectedIndex(0);
			itemSearchHint.setText(found.isEmpty()
				? "No tradeable items match '" + query + "'. Some untradeable drops aren't in RuneLite's catalog."
				: "Click a result (or press Enter) to add it.");
			itemPickerRow.revalidate();
		});
	}

	private void addItem(ItemSuggestion item)
	{
		int group = groupRow.isVisible() ? Math.max(0, activeGroup.getSelectedIndex()) : 0;
		List<ItemSuggestion> target = itemGroups.get(group);
		if (target.stream().anyMatch(existing -> existing.id == item.id))
		{
			itemSearchHint.setText(item.name + " is already added.");
			return;
		}
		target.add(item);
		itemSearch.setText("");
		itemResults.clear();
		resultsScroll.setVisible(false);
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
		List<ItemSuggestion> merged = new ArrayList<>();
		for (List<ItemSuggestion> group : itemGroups)
			for (ItemSuggestion item : group)
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
			for (ItemSuggestion item : itemGroups.get(0)) selectedItemsPanel.add(fitHeight(itemRow(item, itemGroups.get(0))));
			if (itemGroups.get(0).isEmpty()) selectedItemsPanel.add(caption("No items added yet."));
		}
		else
		{
			for (int g = 0; g < itemGroups.size(); g++) selectedItemsPanel.add(setCard(g));
		}
		refresh(selectedItemsPanel);
	}

	private JPanel setCard(int index)
	{
		List<ItemSuggestion> group = itemGroups.get(index);
		JPanel card = new JPanel(new BorderLayout(0, 6));
		card.setBackground(CARD);
		boolean active = index == activeGroup.getSelectedIndex() && itemGroups.size() > 1;
		card.setBorder(selectedBorder(active, CARD));
		JPanel header = row();
		String name = TsgHubItemSets.nameFor(ids(group));
		header.add(boldLabel((name.isEmpty() ? "Set " + (index + 1) : name) + "  "), BorderLayout.WEST);
		header.add(caption(group.size() == 1 ? "1 piece" : group.size() + " pieces"), BorderLayout.CENTER);
		if (itemGroups.size() > 1)
		{
			JButton remove = iconButton(hover("remove"), "Remove this set");
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
			for (ItemSuggestion item : group) pieces.add(itemRow(item, group));
			card.add(pieces, BorderLayout.CENTER);
		}
		JPanel wrap = panel(new BorderLayout());
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		wrap.add(card, BorderLayout.CENTER);
		return fitHeight(wrap);
	}

	private JPanel itemRow(ItemSuggestion item, List<ItemSuggestion> owner)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
		JLabel name = label(item.name, TEXT, plainFont());
		name.setIcon(iconFor(item.id, selectedItemsPanel));
		row.add(name, BorderLayout.CENTER);
		JButton remove = iconButton(hover("remove"), "Remove " + item.name);
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
			icon.setImage(image.getScaledInstance(18, 16, Image.SCALE_SMOOTH));
			repaintTarget.repaint();
		}));
		return icon;
	}

	static String nameOrPieces(JsonArray items)
	{
		String name = TsgHubItemSets.nameFor(itemIds(items));
		return name.isEmpty() ? items.size() + " pieces" : name;
	}

	static String raidSummary(List<String> modes)
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
}
