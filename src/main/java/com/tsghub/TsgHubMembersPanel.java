package com.tsghub;

import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.google.gson.JsonArray;
import com.tsghub.TsgHubMembers.Member;
import com.tsghub.TsgHubMembers.Snapshot;
import com.tsghub.TsgHubMembers.Summary;
import com.tsghub.TsgHubUi.Tone;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import net.runelite.client.util.ColorUtil;

final class TsgHubMembersPanel extends JPanel
{
	private static final String[] COLUMNS = {"Member", "Rank", "Joined clan", "Last seen", "Active days", "Hours", "Events", "Group", "Drops", "Version", "Discord"};
	private static final int[] WIDTHS = {150, 110, 90, 80, 70, 60, 60, 60, 50, 120, 60};
	private static final int MEMBER = 0, RANK = 1, JOINED = 2, SEEN = 3, ACTIVE = 4, HOURS = 5, EVENTS = 6, GROUP = 7, DROPS = 8, VERSION = 9, DISCORD = 10;
	private static final String[] WINDOW_LABELS = {"Last 7 days", "Last 30 days", "Last 90 days"};
	private static final int[] WINDOW_DAYS = {7, 30, 90};
	private static final int DEFAULT_WINDOW = 1;
	private static final Color INACTIVE_ROW = ColorUtil.colorLerp(CARD, TsgHubTheme.ERROR, 0.12);
	private static final int STATUS_W = 600;
	private static final Icon CHECK = icon("check", SUCCESS);
	private static final Icon WARNED = icon("warning", TsgHubTheme.ERROR);
	private static final Icon NOTED = icon("note", WARNING);
	private static final int BADGE_GAP = 4;
	private static final Border NOTED_BORDER = BorderFactory.createEmptyBorder(0, 6, 0, 6 + NOTED.getIconWidth() + BADGE_GAP);
	private static final Border WARNED_BORDER = BorderFactory.createEmptyBorder(0, 6, 0, 6 + WARNED.getIconWidth() + BADGE_GAP + NOTED.getIconWidth() + BADGE_GAP);
	private static final Border CELL_BORDER = BorderFactory.createEmptyBorder(0, 6, 0, 6);

	private final TsgHubMembers members;
	private final IntSupplier threshold;
	private final Model model = new Model();
	private final JTable table = new JTable(model)
	{
		@Override public String getToolTipText(MouseEvent e)
		{
			int row = rowAtPoint(e.getPoint()), column = columnAtPoint(e.getPoint());
			return row < 0 || column < 0 ? null : tooltip(model.get(convertRowIndexToModel(row)), convertColumnIndexToModel(column));
		}
	};
	private final TableRowSorter<Model> sorter = new TableRowSorter<>(model);
	private final JLabel activeValue = label("-", TEXT, headingFont());
	private final JLabel activeCaption = caption(" ");
	private final JLabel adoptionValue = label("-", TEXT, headingFont());
	private final JLabel adoptionCaption = caption(" ");
	private final JLabel turnoutValue = label("-", TEXT, headingFont());
	private final JLabel turnoutCaption = caption(" ");
	private final JLabel footnote = caption(" ");
	private final JComboBox<String> window = new JComboBox<>(WINDOW_LABELS);
	private final JCheckBox inactiveOnly = new JCheckBox("Inactive");
	private final JCheckBox notOnHubOnly = new JCheckBox("Not on hub");
	private final JCheckBox outdatedOnly = new JCheckBox("Outdated");
	private final JCheckBox warnedOnly = new JCheckBox("Warned");
	private final TsgHubSearchField search = new TsgHubSearchField(new JTextField(16))
	{
		@Override boolean isExact(String query) { return true; }
		@Override List<String> matches(String query) { return Collections.emptyList(); }
	};
	private final StatusLine status = new StatusLine(STATUS_W);
	private final Glyph refreshIcon = hover("refresh");
	private final JButton refresh = iconButton(refreshIcon, "Refresh clan members");
	private volatile int days = WINDOW_DAYS[DEFAULT_WINDOW];
	private Snapshot snapshot;
	private JsonArray appliedNotes;
	private Instant now = Instant.now();
	private int limit;
	private final Map<Integer, ImageIcon> rankIcons = new HashMap<>();
	private JDialog detailDialog;
	private TsgHubMemberDetail detail;

	TsgHubMembersPanel(TsgHubMembers members, IntSupplier threshold)
	{
		super(new BorderLayout(0, GAP_M));
		this.members = members;
		this.threshold = threshold;
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));

		JPanel top = stack();
		top.add(buildSummary());
		top.add(gap(GAP_M));
		top.add(buildToolbar());
		add(top, BorderLayout.NORTH);
		add(buildTable(), BorderLayout.CENTER);
		add(status, BorderLayout.SOUTH);
	}

	int days()
	{
		return days;
	}

	void setStatus(String message, Tone tone)
	{
		status.show(message, tone);
		refreshIcon.setSpinning(message != null && message.endsWith("..."), refresh);
	}

	void failed(String message)
	{
		if (snapshot != null && snapshot.days != days)
		{
			for (int i = 0; i < WINDOW_DAYS.length; i++)
			{
				if (WINDOW_DAYS[i] != snapshot.days) continue;
				days = snapshot.days;
				window.setSelectedIndex(i);
			}
		}
		setStatus(message, Tone.ERROR);
	}

	void setActivity(Snapshot loaded)
	{
		snapshot = loaded;
		appliedNotes = null;
		now = Instant.now();
		limit = threshold.getAsInt();
		model.setMembers(loaded.members);
		updateSummary();
		refreshDetail();
		setStatus(loaded.fromClan ? "" : "Clan settings aren't loaded, so this shows TSG Hub records only. Log in to see every clan member.", loaded.fromClan ? Tone.INFO : Tone.ERROR);
	}

	private JPanel buildSummary()
	{
		JPanel strip = panel(new GridLayout(1, 3, GAP_M, 0));
		strip.add(statCard(caption("ACTIVE MEMBERS"), activeValue, activeCaption));
		strip.add(statCard(caption("HUB ADOPTION"), adoptionValue, adoptionCaption));
		strip.add(statCard(caption("EVENT TURNOUT"), turnoutValue, turnoutCaption));
		JPanel wrap = stack();
		wrap.add(fitHeight(strip));
		wrap.add(gap(GAP_S));
		wrap.add(footnote);
		return wrap;
	}

	private JPanel buildToolbar()
	{
		search.addSearch("Search members");
		onTextChange(search.field(), this::applyFilter);
		window.setSelectedIndex(DEFAULT_WINDOW);
		window.addActionListener(e -> {
			int index = window.getSelectedIndex();
			if (index < 0 || WINDOW_DAYS[index] == days) return;
			days = WINDOW_DAYS[index];
			members.load(days);
		});
		refresh.addActionListener(e -> members.load(days));

		JPanel toggles = panel(new FlowLayout(FlowLayout.LEFT, GAP_M, 0));
		toggles.add(caption("Show only:"));
		for (JCheckBox toggle : new JCheckBox[] {inactiveOnly, notOnHubOnly, outdatedOnly, warnedOnly})
		{
			plain(toggle, TEXT);
			toggle.addActionListener(e -> applyFilter());
			toggles.add(toggle);
		}

		JPanel west = panel(new FlowLayout(FlowLayout.LEFT, GAP_M, 0));
		west.add(search);
		west.add(window);
		west.add(toggles);
		return fitHeight(row(west, refresh));
	}

	private JScrollPane buildTable()
	{
		sorter.setComparator(MEMBER, Comparator.comparing((Member m) -> m.name.toLowerCase(Locale.ROOT)));
		sorter.setComparator(RANK, Comparator.comparingInt((Member m) -> m.rankValue));
		sorter.setComparator(JOINED, Comparator.comparing((Member m) -> m.joined, Comparator.nullsFirst(Comparator.naturalOrder())));
		sorter.setComparator(SEEN, Comparator.comparing(Member::lastSeen, Comparator.nullsFirst(Comparator.naturalOrder())));
		sorter.setComparator(ACTIVE, Comparator.comparingInt((Member m) -> m.stat("activeDays")));
		sorter.setComparator(HOURS, Comparator.comparingInt((Member m) -> m.stat("minutesOnline")));
		sorter.setComparator(EVENTS, Comparator.comparingInt((Member m) -> m.stat("eventsAttended")).thenComparingInt(m -> m.stat("eventsJoined")));
		sorter.setComparator(GROUP, Comparator.comparingInt((Member m) -> m.stat("groupContent")).thenComparingInt(m -> m.stat("groupPartners")));
		sorter.setComparator(DROPS, Comparator.comparingInt((Member m) -> m.stat("drops")));
		sorter.setComparator(DISCORD, Comparator.comparing((Member m) -> m.onHub()).thenComparing(Member::discordLinked));
		sorter.setComparator(VERSION, Comparator.comparing((Member m) -> m.onHub()).thenComparing(m -> !m.outdated()).thenComparing(Member::version, TsgHubMembers::compareVersions));
		List<RowSorter.SortKey> keys = new ArrayList<>();
		keys.add(new RowSorter.SortKey(SEEN, SortOrder.DESCENDING));
		sorter.setSortKeys(keys);
		sorter.setSortsOnUpdates(true);
		table.setRowSorter(sorter);

		table.setDefaultRenderer(Object.class, new Cell());
		table.setBackground(CARD);
		table.setForeground(TEXT);
		table.setGridColor(BORDER);
		table.setShowVerticalLines(false);
		table.setRowHeight(24);
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		for (int i = 0; i < WIDTHS.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
		JTableHeader header = table.getTableHeader();
		header.setReorderingAllowed(false);
		header.setBackground(BACKGROUND);
		header.setForeground(MUTED);
		header.setFont(smallFont());
		table.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent e)
			{
				int row = table.rowAtPoint(e.getPoint());
				if (row >= 0) openDetail(model.get(table.convertRowIndexToModel(row)));
			}
		});

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createLineBorder(BORDER));
		scroll.getViewport().setBackground(CARD);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private void applyFilter()
	{
		String needle = PlayerNames.searchKey(search.getText());
		boolean inactive = inactiveOnly.isSelected(), notOnHub = notOnHubOnly.isSelected(), outdated = outdatedOnly.isSelected(), warned = warnedOnly.isSelected();
		limit = threshold.getAsInt();
		sorter.setRowFilter(new RowFilter<Model, Integer>()
		{
			@Override public boolean include(Entry<? extends Model, ? extends Integer> entry)
			{
				Member member = model.get(entry.getIdentifier());
				if (!needle.isEmpty() && !matches(member, needle)) return false;
				if (!inactive && !notOnHub && !outdated && !warned) return true;
				return (inactive && member.inactive(now, limit)) || (notOnHub && !member.onHub()) || (outdated && member.outdated())
					|| (warned && !member.activeWarnings().isEmpty());
			}
		});
	}

	private static boolean matches(Member member, String needle)
	{
		if (PlayerNames.searchKey(member.name).contains(needle)) return true;
		for (String old : member.previousNames()) if (PlayerNames.searchKey(old).contains(needle)) return true;
		return false;
	}

	private void updateSummary()
	{
		Snapshot current = snapshot;
		if (current == null) return;
		limit = threshold.getAsInt();
		Summary summary = TsgHubMembers.summarize(current.members, current.eventsInWindow, now, limit);
		activeValue.setText(summary.active + " / " + summary.total);
		activeCaption.setText("seen in the last " + plural(limit, "day"));
		adoptionValue.setText(summary.total == 0 ? "-" : Math.round(100f * summary.onHub / summary.total) + "%");
		adoptionCaption.setText(summary.onHub + " of " + summary.total + " on TSG Hub");
		turnoutValue.setText(summary.turnoutPercent < 0 ? "-" : summary.turnoutPercent + "%");
		turnoutCaption.setText(current.eventsInWindow == 0 ? "no events in the last " + plural(current.days, "day")
			: "avg attendance across " + plural(current.eventsInWindow, "event"));
		footnote.setText("Click a member for their activity, admin note and warnings.");
		applyFilter();
	}

	private void openDetail(Member member)
	{
		if (detailDialog == null || !detailDialog.isDisplayable())
		{
			detail = new TsgHubMemberDetail(members, () -> snapshot == null ? Collections.emptyList() : snapshot.members);
			detailDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "", Dialog.ModalityType.MODELESS);
			detailDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
			detailDialog.setContentPane(detail);
			detailDialog.setSize(TsgHubMemberDetail.DETAIL_W + 40, 620);
			detailDialog.setLocationRelativeTo(this);
		}
		detailDialog.setTitle("TSG Hub · " + member.name);
		detail.show(member, members.rankIcon(member.rankId), now, snapshot != null && snapshot.notesLoaded);
		detailDialog.setVisible(true);
		detailDialog.toFront();
		if (!member.onHub()) return;
		int window = days;
		members.loadMember(member.hubName(), window, loaded -> detail.setDetail(member, loaded, window), message -> detail.failed(member, message));
	}

	void applyNotes(JsonArray notes)
	{
		Snapshot current = snapshot;
		if (current == null || notes.equals(appliedNotes)) return;
		appliedNotes = notes;
		snapshot = new Snapshot(TsgHubMembers.withNotes(current.members, notes), current.days, current.eventsInWindow, current.latestVersion, current.fromClan, true);
		model.setMembers(snapshot.members);
		refreshDetail();
	}

	private void refreshDetail()
	{
		if (detailDialog == null || !detailDialog.isVisible()) return;
		for (Member member : snapshot.members) detail.setNotes(member, snapshot.notesLoaded);
	}

	private String tooltip(Member member, int column)
	{
		if (column == MEMBER)
		{
			String tip = TsgHubRoster.rosterTooltip("", TsgHubJson.array(member.stats, "previousNames"), "", new JsonArray(), member.note(), member.warnings(), now, ZoneId.systemDefault());
			return tip.isEmpty() ? null : tooltipHtml(tip);
		}
		if (column == EVENTS && member.onHub())
		{
			int window = snapshot == null ? 0 : snapshot.eventsInWindow;
			return "Took part in " + member.stat("eventsAttended") + " of " + plural(window, "event") + ", joined " + member.stat("eventsJoined");
		}
		if (column == SEEN && member.lastSeen() != null) return localDate(member.lastSeen(), ZoneId.systemDefault());
		if (column == VERSION && member.outdated()) return "Running an older plugin than " + (snapshot == null ? "the latest" : snapshot.latestVersion);
		if (column == GROUP && member.stat("groupContent") > 0) return plural(member.stat("groupContent"), "completion") + " with " + plural(member.stat("groupPartners"), "clanmate");
		if (column == DISCORD && member.discordLinked()) return "Linked to Discord";
		return null;
	}

	private final class Model extends AbstractTableModel
	{
		private List<Member> members = new ArrayList<>();

		void setMembers(List<Member> next)
		{
			members = new ArrayList<>(next);
			fireTableDataChanged();
		}

		Member get(int row)
		{
			return members.get(row);
		}

		@Override public int getRowCount() { return members.size(); }
		@Override public int getColumnCount() { return COLUMNS.length; }
		@Override public String getColumnName(int column) { return COLUMNS[column]; }
		@Override public Object getValueAt(int row, int column) { return members.get(row); }
	}

	private final class Cell extends DefaultTableCellRenderer
	{
		private boolean warned;
		private boolean noted;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column)
		{
			Member member = (Member) value;
			super.getTableCellRendererComponent(table, text(member, column), selected, false, row, column);
			setFont(column == MEMBER ? boldFont() : plainFont());
			setBorder(CELL_BORDER);
			setIcon(null);
			boolean inactive = member.inactive(now, limit);
			setBackground(selected ? CARD_HOVER : inactive ? INACTIVE_ROW : CARD);
			Color color = !member.onHub() ? DIM : column == SEEN && inactive ? TsgHubTheme.ERROR : TEXT;
			if (column == VERSION && member.outdated()) color = WARNING;
			if ((column == VERSION || column == DISCORD) && !member.onHub()) color = MUTED;
			setForeground(color);
			if (column == RANK)
			{
				setIcon(rankIcons.computeIfAbsent(member.rankId, id -> {
					BufferedImage icon = members.rankIcon(id);
					return icon == null ? null : new ImageIcon(icon);
				}));
			}
			warned = column == MEMBER && !member.activeWarnings().isEmpty();
			noted = column == MEMBER && !member.note().isEmpty();
			if (warned || noted) setBorder(warned ? WARNED_BORDER : NOTED_BORDER);
			if (column == DISCORD && member.discordLinked())
			{
				setText("");
				setIcon(CHECK);
			}
			setHorizontalAlignment(column == DISCORD ? CENTER : column >= ACTIVE && column <= DROPS ? RIGHT : LEFT);
			return this;
		}

		@Override
		protected void paintComponent(java.awt.Graphics g)
		{
			super.paintComponent(g);
			int x = getWidth() - 6 - NOTED.getIconWidth();
			if (noted) NOTED.paintIcon(this, g, x, (getHeight() - NOTED.getIconHeight()) / 2);
			x -= BADGE_GAP + WARNED.getIconWidth();
			if (warned) WARNED.paintIcon(this, g, x, (getHeight() - WARNED.getIconHeight()) / 2);
		}

		private String text(Member member, int column)
		{
			switch (column)
			{
				case MEMBER: return member.name;
				case RANK: return member.rank;
				case JOINED: return member.joined == null ? "-" : member.joined.format(TsgHubFormat.DATE_WITH_YEAR);
				case SEEN: return member.onHub() ? TsgHubMembers.ago(member.lastSeen(), now) : "-";
				case ACTIVE: return count(member, "activeDays");
				case HOURS: return member.onHub() ? halfHours(member.stat("minutesOnline")) : "-";
				case EVENTS: return member.onHub() ? member.stat("eventsAttended") + "/" + (snapshot == null ? 0 : snapshot.eventsInWindow) : "-";
				case GROUP: return count(member, "groupContent");
				case DROPS: return count(member, "drops");
				case VERSION: return versionText(member);
				case DISCORD: return "-";
				default: return "";
			}
		}

	}

	private static String count(Member member, String key)
	{
		return member.onHub() ? String.valueOf(member.stat(key)) : "-";
	}

	static String halfHours(int minutes)
	{
		int halves = Math.round(minutes / 30f);
		return halves / 2 + (halves % 2 == 0 ? "" : ".5");
	}

	private static String versionText(Member member)
	{
		if (!member.onHub()) return "Not on hub";
		String version = member.version().isEmpty() ? "unknown" : member.version();
		return version + (member.outdated() ? " (outdated)" : "");
	}
}
