package com.tsghub;

import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

final class TsgHubNewsEditor extends JPanel
{
	private static final int LIST_W = 270;
	private static final int TEXT_W = 480;
	private static final int MAX_TITLE = 80;
	private static final int MAX_BODY = 1000;
	private static final int MAX_POLL_OPTIONS = 10;
	private static final int MAX_POLL_OPTION = 60;
	private static final String[] EXPIRY_LABELS = {"1 hour", "6 hours", "12 hours", "1 day", "3 days", "7 days", "30 days", "Never"};
	private static final int[] EXPIRY_HOURS = {1, 6, 12, 24, 72, 168, 720, 0};
	private static final int DEFAULT_EXPIRY = 5;
	private static final String KEEP_EXPIRY = "Keep current";

	private final TsgHubPlugin plugin;
	private final JPanel list = stack();
	private final CardLayout detailLayout = new CardLayout();
	private final JPanel detail = new JPanel(detailLayout);
	private final StatusLine status = new StatusLine(TEXT_W);

	private final JTextField title = new JTextField();
	private final JTextArea body = new JTextArea(8, 40);
	private final JCheckBox pinned = new JCheckBox("Pin to the top of News");
	private final JComboBox<String> expiry = new JComboBox<>();
	private final JCheckBox hasPoll = new JCheckBox("Add a poll");
	private final JTextArea pollOptions = new JTextArea(4, 40);
	private final JCheckBox pollMulti = new JCheckBox("Members can pick more than one");
	private final JCheckBox pollAnonymous = new JCheckBox("Anonymous: hide who voted for what");
	private final JLabel pollHint = label("", MUTED, smallFont());
	private final JPanel pollFields = stack();
	private final JLabel formTitle = sectionTitle("New announcement");
	private final JLabel expiryHint = label("", MUTED, smallFont());
	private final JButton draft = button("Save as draft");
	private final JButton post = primaryButton("Post now");
	private final JLabel error = label("", TsgHubTheme.ERROR, smallFont());

	private final JLabel viewTitle = label(" ", TEXT, titleFont());
	private final JLabel viewMeta = label(" ", MUTED, smallFont());
	private final JLabel draftNote = label("Draft: only admins can see this. Publish it when it's ready.", WARNING, smallFont());
	private final JLabel expiredNote = label("Expired: only admins can see this now. It's removed 90 days after expiring.", MUTED, smallFont());
	private final JPanel viewBody = stack();
	private final JButton edit = button("Edit");
	private final JButton publish = primaryButton("Publish");
	private final JButton pin = button("Pin");
	private final JButton delete = iconButton(new TrashIcon(), "Delete announcement");

	private JsonArray announcements;
	private JsonObject selected;
	private String selectedId = "";
	private JsonObject editing;

	TsgHubNewsEditor(TsgHubPlugin plugin)
	{
		super(new BorderLayout());
		this.plugin = plugin;
		setBackground(BACKGROUND);
		add(buildListColumn(), BorderLayout.WEST);

		detail.setOpaque(false);
		detail.add(north(emptyState("Pick an announcement", "Choose one on the left, or post a new one.", 320)), "empty");
		detail.add(narrowPage(buildForm()), "form");
		detail.add(buildView(), "view");

		JPanel right = panel(new BorderLayout());
		right.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
		right.add(detail, BorderLayout.CENTER);
		right.add(status, BorderLayout.SOUTH);
		add(right, BorderLayout.CENTER);
		setAnnouncements(null);
	}

	private JComponent buildListColumn()
	{
		JPanel column = new JPanel(new BorderLayout(0, 8));
		column.setBackground(CARD);
		column.setPreferredSize(new Dimension(LIST_W, 100));
		column.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

		JButton refresh = iconButton(new RefreshIcon(), "Refresh announcements");
		refresh.addActionListener(e -> plugin.announcements().load());
		JButton create = primaryButton("New announcement");
		create.addActionListener(e -> beginPost());

		JPanel top = stack();
		top.add(fitHeight(row(boldLabel("Announcements"), refresh)));
		top.add(Box.createVerticalStrut(GAP_M));
		top.add(fitHeight(create));
		column.add(top, BorderLayout.NORTH);

		WidthTrackingPanel listWrap = new WidthTrackingPanel();
		listWrap.add(list);
		column.add(scroll(listWrap), BorderLayout.CENTER);
		return column;
	}

	private JPanel buildForm()
	{
		JPanel form = stack();
		form.add(formTitle);
		form.add(wrapped("Clan members get a chat message once and see it under News in the TSG Hub sidebar. Save it as a draft to let other admins review it first.", MUTED, smallFont(), TEXT_W));
		form.add(Box.createVerticalStrut(GAP_M));
		form.add(field("Title", title));
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setFont(title.getFont());
		JScrollPane bodyScroll = new JScrollPane(body);
		bodyScroll.setBorder(title.getBorder());
		JPanel message = panel(new BorderLayout(0, 3));
		message.add(bodyScroll, BorderLayout.CENTER);
		message.add(label("Supports **bold**, *italic*, # headings, - lists, 1. steps, > quotes and [links](url)", MUTED, smallFont()), BorderLayout.SOUTH);
		form.add(field("Message (optional)", message));
		JPanel expiryRow = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		expiryRow.add(expiry);
		expiryRow.add(Box.createHorizontalStrut(GAP_M));
		expiryRow.add(expiryHint);
		form.add(field("Expires after", fitHeight(expiryRow)));
		form.add(plain(pinned, TEXT));
		form.add(plain(hasPoll, TEXT));
		pollOptions.setLineWrap(true);
		pollOptions.setFont(title.getFont());
		JScrollPane optionsScroll = new JScrollPane(pollOptions);
		optionsScroll.setBorder(title.getBorder());
		pollFields.setBorder(BorderFactory.createEmptyBorder(GAP_S, 20, 0, 0));
		pollFields.add(field("Options, one per line (2 to " + MAX_POLL_OPTIONS + ")", optionsScroll));
		pollFields.add(plain(pollMulti, TEXT));
		pollFields.add(plain(pollAnonymous, TEXT));
		pollFields.add(pollHint);
		form.add(pollFields);
		hasPoll.addActionListener(e -> {
			pollFields.setVisible(hasPoll.isSelected());
			refresh(form);
		});
		form.add(Box.createVerticalStrut(GAP_L));
		error.setVisible(false);
		form.add(error);
		JButton cancel = button("Cancel");
		cancel.addActionListener(e -> show(selected));
		JPanel footer = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		footer.add(cancel);
		footer.add(draft);
		footer.add(post);
		form.add(fitHeight(footer));

		placeholder(title, "e.g. Clan meeting Saturday");
		draft.addActionListener(e -> submit(false));
		post.addActionListener(e -> submit(true));
		return form;
	}

	private JComponent buildView()
	{
		JPanel view = panel(new BorderLayout(0, 8));
		JPanel header = panel(new BorderLayout(12, 0));
		JPanel titles = stack();
		titles.add(viewTitle);
		titles.add(Box.createVerticalStrut(GAP_XS));
		titles.add(viewMeta);
		titles.add(draftNote);
		titles.add(expiredNote);
		header.add(titles, BorderLayout.CENTER);
		edit.addActionListener(e -> beginEdit());
		publish.addActionListener(e -> confirmPublish());
		pin.addActionListener(e -> {
			if (selected != null) plugin.announcements().setPinned(str(selected, "id"), !bool(selected, "pinned"));
		});
		delete.addActionListener(e -> confirmDelete());
		JPanel buttons = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.add(edit);
		buttons.add(publish);
		buttons.add(pin);
		buttons.add(delete);
		header.add(north(buttons), BorderLayout.EAST);
		header.setBorder(BorderFactory.createCompoundBorder(bottomRule(), BorderFactory.createEmptyBorder(0, 0, 10, 0)));
		view.add(header, BorderLayout.NORTH);
		WidthTrackingPanel page = new WidthTrackingPanel();
		page.add(viewBody);
		view.add(scroll(page), BorderLayout.CENTER);
		return view;
	}

	private void beginPost()
	{
		selectedId = "";
		selected = null;
		renderList();
		openForm(null);
	}

	private void beginEdit()
	{
		if (selected != null) openForm(selected);
	}

	private void openForm(JsonObject announcement)
	{
		editing = announcement;
		boolean isDraft = announcement != null && bool(announcement, "draft");
		boolean live = announcement != null && !isDraft;
		formTitle.setText(announcement == null ? "New announcement" : isDraft ? "Edit draft" : "Edit announcement");
		title.setText(announcement == null ? "" : str(announcement, "title"));
		body.setText(announcement == null ? "" : str(announcement, "body"));
		body.setCaretPosition(0);
		pinned.setSelected(announcement != null && bool(announcement, "pinned"));
		expiry.removeAllItems();
		if (live) expiry.addItem(KEEP_EXPIRY);
		for (String option : EXPIRY_LABELS) expiry.addItem(option);
		expiry.setSelectedIndex(live ? 0 : isDraft ? expiryIndex(draftHours(announcement)) : DEFAULT_EXPIRY);
		JsonObject poll = TsgHubAnnouncements.poll(announcement);
		boolean locked = poll != null && bool(poll, "locked");
		hasPoll.setSelected(poll != null);
		pollFields.setVisible(poll != null);
		List<String> labels = new ArrayList<>();
		if (poll != null) for (JsonObject option : objects(array(poll, "options"))) labels.add(str(option, "label"));
		pollOptions.setText(String.join("\n", labels));
		pollMulti.setSelected(poll != null && bool(poll, "multi"));
		pollAnonymous.setSelected(poll != null && bool(poll, "anonymous"));
		for (JComponent input : new JComponent[]{hasPoll, pollOptions, pollMulti, pollAnonymous}) input.setEnabled(!locked);
		pollHint.setText(locked ? "Locked: members have already voted." : "Members see the results as votes come in.");
		expiryHint.setText(live ? "New choices count from now." : "Counts from when it's posted.");
		draft.setVisible(!live);
		draft.setText(isDraft ? "Save draft" : "Save as draft");
		post.setText(live ? "Save changes" : isDraft ? "Publish" : "Post now");
		draft.setEnabled(true);
		post.setEnabled(true);
		error.setVisible(false);
		detailLayout.show(detail, "form");
		title.requestFocusInWindow();
	}

	private static int expiryIndex(int hours)
	{
		for (int i = 0; i < EXPIRY_HOURS.length; i++) if (EXPIRY_HOURS[i] == hours) return i;
		return DEFAULT_EXPIRY;
	}

	static int draftHours(JsonObject announcement)
	{
		return integer(announcement, "expiresInHours", integer(announcement, "expiresInDays", 7) * 24);
	}

	static String hours(int hours)
	{
		return hours % 24 == 0 ? plural(hours / 24, "day") : plural(hours, "hour");
	}

	private Integer chosenExpiry()
	{
		int index = expiry.getSelectedIndex();
		if (KEEP_EXPIRY.equals(expiry.getItemAt(0)))
		{
			if (index <= 0) return null;
			index--;
		}
		return EXPIRY_HOURS[Math.max(0, index)];
	}

	static List<String> pollLines(String text)
	{
		Set<String> seen = new LinkedHashSet<>();
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n"))
		{
			String option = line.trim();
			if (!option.isEmpty() && seen.add(option.toLowerCase())) lines.add(option);
		}
		return lines;
	}

	private JsonElement chosenPoll()
	{
		if (!hasPoll.isEnabled()) return null;
		if (!hasPoll.isSelected()) return TsgHubAnnouncements.poll(editing) != null ? JsonNull.INSTANCE : null;
		JsonObject poll = new JsonObject();
		JsonArray options = new JsonArray();
		for (String option : pollLines(pollOptions.getText())) options.add(option);
		poll.add("options", options);
		poll.addProperty("multi", pollMulti.isSelected());
		poll.addProperty("anonymous", pollAnonymous.isSelected());
		return poll;
	}

	private void submit(boolean publishNow)
	{
		String text = title.getText().trim();
		String message = body.getText().trim();
		if (text.isEmpty()) { fail("Add a title."); return; }
		if (text.length() > MAX_TITLE) { fail("Keep the title under " + MAX_TITLE + " characters."); return; }
		if (message.length() > MAX_BODY) { fail("Keep the message under " + MAX_BODY + " characters."); return; }
		if (hasPoll.isSelected() && hasPoll.isEnabled())
		{
			List<String> options = pollLines(pollOptions.getText());
			if (options.size() < 2 || options.size() > MAX_POLL_OPTIONS) { fail("A poll needs 2 to " + MAX_POLL_OPTIONS + " different options."); return; }
			for (String option : options)
				if (option.length() > MAX_POLL_OPTION) { fail("Keep poll options under " + MAX_POLL_OPTION + " characters."); return; }
		}
		error.setVisible(false);
		draft.setEnabled(false);
		post.setEnabled(false);
		boolean live = editing != null && !bool(editing, "draft");
		String id = editing == null ? "" : str(editing, "id");
		plugin.announcements().save(id, text, message, pinned.isSelected(), chosenExpiry(), chosenPoll(), publishNow && !live);
	}

	private void confirmPublish()
	{
		if (selected == null) return;
		if (javax.swing.JOptionPane.showConfirmDialog(this, "Publish \"" + str(selected, "title") + "\"? Clan members get a chat message and see it under News.",
			"Publish announcement", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE) == javax.swing.JOptionPane.OK_OPTION)
			plugin.announcements().publish(str(selected, "id"));
	}

	private void fail(String message)
	{
		showError(error, message, TEXT_W);
	}

	private void confirmDelete()
	{
		if (selected == null) return;
		if (TsgHubUi.confirmDelete(this, "Delete announcement", "Delete \"" + str(selected, "title") + "\"? Members won't see it anymore.", "Delete"))
			plugin.announcements().delete(str(selected, "id"));
	}

	void setStatus(String message, Tone tone)
	{
		status.show(message, tone);
	}

	void showAnnouncement(String id)
	{
		draft.setEnabled(true);
		post.setEnabled(true);
		selectedId = id;
		select();
	}

	void failed()
	{
		draft.setEnabled(true);
		post.setEnabled(true);
	}

	void setAnnouncements(JsonArray announcements)
	{
		this.announcements = announcements;
		renderList();
		select();
	}

	private void select()
	{
		JsonObject match = null;
		if (announcements != null)
			for (JsonObject announcement : objects(announcements))
				if (str(announcement, "id").equals(selectedId)) match = announcement;
		if (match != null) show(match);
		else if (!selectedId.isEmpty() && announcements != null) show(null);
	}

	private void show(JsonObject announcement)
	{
		selected = announcement;
		selectedId = announcement == null ? "" : str(announcement, "id");
		renderList();
		if (announcement == null)
		{
			detailLayout.show(detail, "empty");
			return;
		}
		Instant now = clock.instant();
		ZoneId zone = ZoneId.systemDefault();
		viewTitle.setText(html(escape(str(announcement, "title")), TEXT_W));
		viewMeta.setText(html(escape(meta(announcement, now, zone)), TEXT_W));
		boolean isDraft = bool(announcement, "draft");
		boolean expired = bool(announcement, "expired");
		draftNote.setVisible(isDraft);
		expiredNote.setVisible(expired);
		edit.setVisible(!expired);
		publish.setVisible(isDraft);
		pin.setVisible(!isDraft && !expired);
		boolean isPinned = bool(announcement, "pinned");
		pin.setText(isPinned ? "Unpin" : "Pin");
		pin.setToolTipText(isPinned ? "Stop keeping this at the top of News" : "Keep this at the top of News");
		viewBody.removeAll();
		String text = str(announcement, "body");
		viewBody.add(text.isEmpty()
			? label("No message, just the title.", DIM, smallFont())
			: linkedHtml(postHtml(text), TEXT, plainFont(), TEXT_W));
		JsonObject poll = TsgHubAnnouncements.poll(announcement);
		if (poll != null) addPollResults(poll, expired);
		refresh(viewBody);
		detailLayout.show(detail, "view");
	}

	static String meta(JsonObject announcement, Instant now, ZoneId zone)
	{
		String when = TsgHubRoster.warningDate(str(announcement, "postedAt"), now, zone);
		if (bool(announcement, "draft"))
		{
			int hours = draftHours(announcement);
			return "Drafted by " + str(announcement, "postedBy") + " · " + when
				+ (hours == 0 ? " · never expires" : " · expires " + hours(hours) + " after posting");
		}
		String meta = "Posted by " + str(announcement, "postedBy") + " · " + when;
		if (!str(announcement, "publishedBy").isEmpty()) meta += " · published by " + str(announcement, "publishedBy");
		if (!str(announcement, "editedAt").isEmpty())
			meta += " · edited by " + str(announcement, "editedBy") + " " + TsgHubRoster.warningDate(str(announcement, "editedAt"), now, zone);
		String expires = str(announcement, "expiresAt");
		if (expires.isEmpty()) return meta + " · never expires";
		return meta + (bool(announcement, "expired") ? " · expired " : " · expires ") + TsgHubRoster.warningDate(expires, now, zone);
	}

	private void renderList()
	{
		list.removeAll();
		if (announcements == null)
		{
			list.add(caption("Loading..."));
		}
		else if (announcements.size() == 0)
		{
			list.add(wrapped("Nothing posted right now. Post one to get started.", MUTED, smallFont(), LIST_W - 30));
		}
		else
		{
			Instant now = clock.instant();
			ZoneId zone = ZoneId.systemDefault();
			String section = null;
			for (JsonObject announcement : objects(announcements))
			{
				String next = bool(announcement, "draft") ? "Drafts" : bool(announcement, "expired") ? "Expired" : bool(announcement, "pinned") ? "Pinned" : "Active";
				if (!next.equals(section))
				{
					list.add(listHeading(next, section == null));
					section = next;
				}
				list.add(listCard(announcement, now, zone));
				list.add(Box.createVerticalStrut(LIST_GAP));
			}
		}
		refresh(list);
	}

	private JComponent listCard(JsonObject announcement, Instant now, ZoneId zone)
	{
		String id = str(announcement, "id");
		boolean isSelected = id.equals(selectedId);
		JPanel card = card();
		if (isSelected) card.setBorder(selectedBorder(true, CARD_HOVER));
		card.setBackground(isSelected ? CARD_HOVER : BACKGROUND);
		String by = (bool(announcement, "draft") ? "Draft by " : "") + str(announcement, "postedBy");
		card.add(twoLines(shrinkable(boldLabel(str(announcement, "title"))),
			caption(by + " · " + TsgHubRoster.warningDate(str(announcement, "postedAt"), now, zone))), BorderLayout.CENTER);
		JsonObject poll = TsgHubAnnouncements.poll(announcement);
		if (poll != null)
		{
			JLabel tag = badge("Poll · " + integer(poll, "voters", 0), ACCENT);
			tag.setToolTipText(plural(integer(poll, "voters", 0), "voter"));
			card.add(north(tag), BorderLayout.EAST);
		}
		clickable(card, () -> show(announcement));
		return fitHeight(card);
	}

	private void addPollResults(JsonObject poll, boolean closed)
	{
		viewBody.add(Box.createVerticalStrut(GAP_M));
		viewBody.add(sectionTitle(closed ? "Final results" : "Poll results"));
		JPanel tags = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		int voters = integer(poll, "voters", 0);
		for (JLabel tag : new JLabel[]{
			badge(bool(poll, "multi") ? "Pick any" : "Pick one", MUTED),
			badge(bool(poll, "anonymous") ? "Anonymous" : "Public votes", MUTED),
			closed ? badge("Closed", MUTED) : bool(poll, "locked") ? badge("Options locked", WARNING) : null})
		{
			if (tag == null) continue;
			tags.add(tag);
			tags.add(Box.createHorizontalStrut(GAP_S));
		}
		tags.add(Box.createHorizontalStrut(GAP_S));
		tags.add(caption(voters == 0 ? "No votes yet" : plural(voters, "voter")));
		viewBody.add(fitHeight(tags));
		viewBody.add(Box.createVerticalStrut(GAP_M));
		List<JsonObject> options = objects(array(poll, "options"));
		int top = 0;
		for (JsonObject option : options) top = Math.max(top, integer(option, "votes", 0));
		for (JsonObject option : options)
		{
			viewBody.add(pollOptionCard(option, voters, top, bool(poll, "anonymous")));
			viewBody.add(Box.createVerticalStrut(LIST_GAP));
		}
	}

	private static JComponent pollOptionCard(JsonObject option, int voters, int top, boolean anonymous)
	{
		int votes = integer(option, "votes", 0);
		boolean leading = votes > 0 && votes == top;
		JPanel card = card();
		JPanel lines = stack();
		String share = voters == 0 ? "" : " · " + Math.round(votes * 100f / voters) + "%";
		lines.add(fitHeight(row(shrinkable(boldLabel(str(option, "label"))),
			label(plural(votes, "vote") + share, leading ? ACCENT : MUTED, leading ? boldFont() : smallFont()))));
		lines.add(Box.createVerticalStrut(GAP_S));
		lines.add(voteBar(votes, voters, leading));
		List<String> names = strings(array(option, "voters"));
		if (!anonymous && !names.isEmpty())
		{
			lines.add(Box.createVerticalStrut(GAP_S));
			lines.add(wrapped(String.join(", ", names), MUTED, smallFont(), TEXT_W - 30));
		}
		card.add(lines, BorderLayout.CENTER);
		return fitHeight(card);
	}

	private static JComponent voteBar(int votes, int voters, boolean leading)
	{
		JComponent bar = new JComponent()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(BACKGROUND);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(leading ? ACCENT.darker() : ACCENT.darker().darker());
				g.fillRect(0, 0, voters == 0 ? 0 : Math.round(getWidth() * votes / (float) voters), getHeight());
			}
		};
		bar.setPreferredSize(new Dimension(100, 6));
		bar.setMinimumSize(new Dimension(0, 6));
		return fitHeight(bar);
	}

	static String pollSummary(JsonObject poll, boolean closed)
	{
		return (closed ? "Closed poll · " : "Poll · ") + (bool(poll, "multi") ? "pick any" : "pick one") + (bool(poll, "anonymous") ? " · anonymous" : "")
			+ " · " + plural(integer(poll, "voters", 0), "voter");
	}

	private static JPanel field(String label, JComponent input)
	{
		JPanel row = panel(new BorderLayout(0, 3));
		row.add(caption(label), BorderLayout.NORTH);
		row.add(input, BorderLayout.CENTER);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		return fitHeight(row);
	}
}
