package com.tsghub;

import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import com.tsghub.TsgHubActivity.Member;
import com.tsghub.TsgHubActivity.Snapshot;
import com.tsghub.TsgHubActivity.Summary;
import com.tsghub.TsgHubUi.Tone;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
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
import javax.swing.Box;
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

final class TsgHubActivityPanel extends JPanel
{
	private static final String[] COLUMNS = {"Member", "Rank", "Joined clan", "Last seen", "Active days", "Hours", "Events", "Group", "Drops", "Version", "Discord"};
	private static final int[] WIDTHS = {150, 110, 90, 80, 70, 60, 60, 60, 50, 120, 60};
	private static final int MEMBER = 0, RANK = 1, JOINED = 2, SEEN = 3, ACTIVE = 4, HOURS = 5, EVENTS = 6, GROUP = 7, DROPS = 8, VERSION = 9, DISCORD = 10;
	private static final String[] WINDOW_LABELS = {"Last 7 days", "Last 30 days", "Last 90 days"};
	private static final int[] WINDOW_DAYS = {7, 30, 90};
	private static final int DEFAULT_WINDOW = 1;
	private static final Color INACTIVE_ROW = ColorUtil.colorLerp(CARD, TsgHubTheme.ERROR, 0.12);
	private static final int STATUS_W = 600;
	private static final Border CELL_BORDER = BorderFactory.createEmptyBorder(0, 6, 0, 6);

	private final TsgHubActivity activity;
	private final IntSupplier threshold;
	private final Model model = new Model();
	private final JTable table = new JTable(model);
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
	private final TsgHubSearchField search = new TsgHubSearchField(new JTextField(16))
	{
		@Override boolean isExact(String query) { return true; }
		@Override List<String> matches(String query) { return Collections.emptyList(); }
	};
	private final StatusLine status = new StatusLine(STATUS_W);
	private final RefreshIcon refreshIcon = new RefreshIcon();
	private final JButton refresh = iconButton(refreshIcon, "Refresh clan activity");
	private volatile int days = WINDOW_DAYS[DEFAULT_WINDOW];
	private Snapshot snapshot;
	private Instant now = Instant.now();
	private int limit;
	private final Map<Integer, ImageIcon> rankIcons = new HashMap<>();
	private JDialog detailDialog;
	private TsgHubActivityDetail detail;

	TsgHubActivityPanel(TsgHubActivity activity, IntSupplier threshold)
	{
		super(new BorderLayout(0, GAP_M));
		this.activity = activity;
		this.threshold = threshold;
		setBackground(BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));

		JPanel top = stack();
		top.add(buildSummary());
		top.add(Box.createVerticalStrut(GAP_M));
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
		now = Instant.now();
		limit = threshold.getAsInt();
		model.setMembers(loaded.members);
		updateSummary();
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
		wrap.add(Box.createVerticalStrut(GAP_S));
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
			activity.load(days);
		});
		refresh.addActionListener(e -> activity.load(days));

		JPanel toggles = panel(new FlowLayout(FlowLayout.LEFT, GAP_M, 0));
		toggles.add(caption("Show only:"));
		for (JCheckBox toggle : new JCheckBox[] {inactiveOnly, notOnHubOnly, outdatedOnly})
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
		sorter.setComparator(VERSION, Comparator.comparing((Member m) -> m.onHub()).thenComparing(m -> !m.outdated()).thenComparing(Member::version, TsgHubActivity::compareVersions));
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
		boolean inactive = inactiveOnly.isSelected(), notOnHub = notOnHubOnly.isSelected(), outdated = outdatedOnly.isSelected();
		limit = threshold.getAsInt();
		sorter.setRowFilter(new RowFilter<Model, Integer>()
		{
			@Override public boolean include(Entry<? extends Model, ? extends Integer> entry)
			{
				Member member = model.get(entry.getIdentifier());
				if (!needle.isEmpty() && !matches(member, needle)) return false;
				if (!inactive && !notOnHub && !outdated) return true;
				return (inactive && member.inactive(now, limit)) || (notOnHub && !member.onHub()) || (outdated && member.outdated());
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
		Summary summary = TsgHubActivity.summarize(current.members, current.eventsInWindow, now, limit);
		activeValue.setText(summary.active + " / " + summary.total);
		activeCaption.setText("seen in the last " + plural(limit, "day"));
		adoptionValue.setText(summary.total == 0 ? "-" : Math.round(100f * summary.onHub / summary.total) + "%");
		adoptionCaption.setText(summary.onHub + " of " + summary.total + " on TSG Hub");
		turnoutValue.setText(summary.turnoutPercent < 0 ? "-" : summary.turnoutPercent + "%");
		turnoutCaption.setText(current.eventsInWindow == 0 ? "no events in the last " + plural(current.days, "day")
			: "avg attendance across " + plural(current.eventsInWindow, "event"));
		footnote.setText("Click a member for their daily activity, events and group content.");
		applyFilter();
	}

	private void openDetail(Member member)
	{
		if (detailDialog == null || !detailDialog.isDisplayable())
		{
			detail = new TsgHubActivityDetail();
			detailDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "", Dialog.ModalityType.MODELESS);
			detailDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
			detailDialog.setContentPane(detail);
			detailDialog.setSize(TsgHubActivityDetail.DETAIL_W + 40, 620);
			detailDialog.setLocationRelativeTo(this);
		}
		detailDialog.setTitle("TSG Hub · " + member.name);
		detail.show(member, activity.rankIcon(member.rankId), now);
		detailDialog.setVisible(true);
		detailDialog.toFront();
		if (!member.onHub()) return;
		String name = TsgHubJson.str(member.stats, "displayName");
		int window = days;
		activity.loadMember(name.isEmpty() ? member.name : name, window, loaded -> detail.setDetail(member, loaded, window), message -> detail.failed(member, message));
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
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column)
		{
			Member member = (Member) value;
			super.getTableCellRendererComponent(table, text(member, column), selected, false, row, column);
			setFont(column == MEMBER ? boldFont() : plainFont());
			setBorder(CELL_BORDER);
			setIcon(null);
			setToolTipText(tooltip(member, column));
			boolean inactive = member.inactive(now, limit);
			setBackground(selected ? CARD_HOVER : inactive ? INACTIVE_ROW : CARD);
			Color color = !member.onHub() ? DIM : column == SEEN && inactive ? TsgHubTheme.ERROR : TEXT;
			if (column == VERSION && member.outdated()) color = WARNING;
			if ((column == VERSION || column == DISCORD) && !member.onHub()) color = MUTED;
			setForeground(color);
			if (column == RANK)
			{
				setIcon(rankIcons.computeIfAbsent(member.rankId, id -> {
					BufferedImage icon = activity.rankIcon(id);
					return icon == null ? null : new ImageIcon(icon);
				}));
			}
			if (column == DISCORD && member.discordLinked())
			{
				setText("");
				setIcon(CHECK);
			}
			setHorizontalAlignment(column == DISCORD ? CENTER : column >= ACTIVE && column <= DROPS ? RIGHT : LEFT);
			return this;
		}

		private String text(Member member, int column)
		{
			switch (column)
			{
				case MEMBER: return member.name;
				case RANK: return member.rank;
				case JOINED: return member.joined == null ? "-" : member.joined.format(TsgHubFormat.DATE_WITH_YEAR);
				case SEEN: return member.onHub() ? TsgHubActivity.ago(member.lastSeen(), now) : "-";
				case ACTIVE: return count(member, "activeDays");
				case HOURS: return member.onHub() ? String.format(Locale.US, "%.1f", member.stat("minutesOnline") / 60f) : "-";
				case EVENTS: return member.onHub() ? member.stat("eventsAttended") + "/" + (snapshot == null ? 0 : snapshot.eventsInWindow) : "-";
				case GROUP: return count(member, "groupContent");
				case DROPS: return count(member, "drops");
				case VERSION: return versionText(member);
				case DISCORD: return "-";
				default: return "";
			}
		}

		private String tooltip(Member member, int column)
		{
			if (column == MEMBER && !member.previousNames().isEmpty()) return "Previously " + String.join(", ", member.previousNames());
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
	}

	private static String count(Member member, String key)
	{
		return member.onHub() ? String.valueOf(member.stat(key)) : "-";
	}

	private static String versionText(Member member)
	{
		if (!member.onHub()) return "Not on hub";
		String version = member.version().isEmpty() ? "unknown" : member.version();
		return version + (member.outdated() ? " (outdated)" : "");
	}

	private static final Icon CHECK = new Icon()
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(TsgHubTheme.SUCCESS);
			g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.drawPolyline(new int[]{x + 2, x + 5, x + 10}, new int[]{y + 6, y + 9, y + 3}, 3);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 12;
		}

		@Override
		public int getIconHeight()
		{
			return 12;
		}
	};
}
