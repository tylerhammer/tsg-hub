package com.tsghub;

import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.SHOW_OFFLINE;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;
import static net.runelite.client.util.ColorUtil.toHexColor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubSidebarPanel.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

final class TsgHubMembersView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private static final String MEMBER_KEY = "tsgHub.member";
	private final WidthTrackingPanel membersPage = new WidthTrackingPanel();
	final JScrollPane membersScroll = scroll(membersPage);
	private final javax.swing.JPopupMenu memberMenu = new javax.swing.JPopupMenu();

	JsonArray members;
	JsonArray offlineMembers = new JsonArray();
	private boolean notesLoaded;
	private final JCheckBox showOffline = new JCheckBox("Show offline");
	TsgHubMembersView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		memberMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener()
		{
			@Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { fillMemberMenu(); }
			@Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { }
			@Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
		});
		plain(showOffline, MUTED).setFont(smallFont());
		showOffline.setSelected("true".equals(plugin.session().get(SHOW_OFFLINE)));
		showOffline.addActionListener(e -> {
			plugin.session().set(SHOW_OFFLINE, showOffline.isSelected() ? "true" : "");
			renderMembers();
		});
	}

	int onlineCount()
	{
		return members == null ? 0 : members.size();
	}

	String membersSummary()
	{
		if (members == null) return "Loading...";
		if (members.size() == 0) return "Nobody online";
		return members.size() + " online";
	}

	void locationSharingChanged()
	{
		if (shell.shows(View.MEMBERS)) renderMembers();
	}

	void setMembers(JsonArray members, JsonArray offline, boolean notesLoaded)
	{
		if (Objects.equals(this.members, members) && Objects.equals(offlineMembers, offline) && this.notesLoaded == notesLoaded) return;
		this.members = members;
		this.offlineMembers = offline;
		this.notesLoaded = notesLoaded;
		if (shell.shows(View.MEMBERS)) renderMembers();
		if (shell.view == View.HOME) shell.homeView.renderHome();
	}

	void showMembers()
	{
		if (shell.pinned == View.MEMBERS)
		{
			shell.dock.expand();
			plugin.presence().open(false);
			return;
		}
		shell.setView(View.MEMBERS);
		renderMembers();
		plugin.presence().open(false);
	}

	void renderMembers()
	{
		shell.viewHeader(View.MEMBERS, "Members", "", membersSummary());
		membersPage.removeAll();
		if (members == null)
		{
			membersPage.add(caption("Loading members..."));
			refresh(membersPage);
			return;
		}
		membersPage.add(shell.sectionHeading("Online (" + members.size() + ")", null));
		if (members.size() == 0) membersPage.add(TsgHubSidebarPanel.hint("Nobody online right now."));
		int myWorld = 0;
		for (JsonObject member : objects(members))
			if (samePlayer(str(member, "displayName"), plugin.clan().playerName())) myWorld = integer(member, "world", 0);
		for (JsonObject member : objects(members))
		{
			membersPage.add(memberCard(member, myWorld));
			membersPage.add(Box.createVerticalStrut(LIST_GAP));
		}
		if (offlineMembers.size() > 0)
		{
			membersPage.add(Box.createVerticalStrut(GAP_S));
			membersPage.add(shell.sectionHeading("Offline (" + offlineMembers.size() + ")", showOffline));
			if (showOffline.isSelected())
			{
				for (JsonObject member : objects(offlineMembers))
				{
					membersPage.add(offlineRow(member));
					membersPage.add(Box.createVerticalStrut(LIST_GAP));
				}
			}
		}
		membersPage.add(Box.createVerticalStrut(GAP_M));
		membersPage.add(TsgHubSidebarPanel.hint(plugin.locationSharingEnabled() ? "Leave clan chat to hide yourself."
			: "You show as Online. Turn on location sharing in settings to show what you're doing."));
		refresh(membersPage);
	}

	private JLabel memberName(JsonObject member, String text, Color color, Font font)
	{
		JLabel label = shrinkable(label(text, color, font));
		BufferedImage icon = plugin.presence().nameIcon(member);
		if (icon != null)
		{
			label.setIcon(new ImageIcon(icon));
			label.setIconTextGap(4);
		}
		return label;
	}

	private void setMemberTip(JComponent card, JsonObject member, String note, JLabel detail)
	{
		fullTextTooltip(card, () -> TsgHubRoster.rosterTooltip(detail != null && truncated(detail) ? TsgHubRoster.locationTip(str(member, "activity"), str(member, "area")) : "",
			array(member, "previousNames"), str(member, "altOf"), array(member, "alts"), note, visibleWarnings(member), clock.instant(), ZoneId.systemDefault()));
	}

	private void addMemberBadges(JPanel badges, JsonObject member, String note, boolean more)
	{
		boolean warned = TsgHubRoster.hasActiveWarning(visibleWarnings(member));
		if (warned)
		{
			JLabel warning = new JLabel(icon("warning", TsgHubTheme.ERROR));
			if (!note.isEmpty() || more) warning.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			badges.add(warning);
		}
		if (!note.isEmpty())
		{
			JLabel noteLabel = new JLabel(icon("note", WARNING));
			if (more) noteLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			badges.add(noteLabel);
		}
	}

	private JPanel memberCard(JsonObject member, int myWorld)
	{
		Font small = smallFont();
		String name = str(member, "displayName");
		String activity = str(member, "activity");
		String note = visibleNote(member);
		int world = integer(member, "world", 0);
		boolean self = samePlayer(name, plugin.clan().playerName());
		JPanel card = card();
		if (self) highlightSelf(card);

		JPanel top = row();
		top.add(memberName(member, name, TEXT, boldFont()), BorderLayout.CENTER);
		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		badges.setOpaque(false);
		boolean sameWorld = !self && world > 0 && world == myWorld;
		addMemberBadges(badges, member, note, world > 0);
		if (world > 0) badges.add(label("W" + world, sameWorld ? SUCCESS : MUTED, small));
		if (badges.getComponentCount() > 0) top.add(badges, BorderLayout.EAST);

		JPanel text = stack();
		text.add(top);
		String detail = TsgHubRoster.activityDetail(activity, str(member, "area"));
		JLabel detailLabel = null;
		if (!detail.isEmpty())
		{
			boolean active = !"Idle".equals(activity) && !"Online".equals(activity) && !activity.isEmpty();
			detailLabel = noFullText(shrinkable(label(detail, active ? SUCCESS : MUTED, small)));
			JPanel bottom = row();
			bottom.add(detailLabel, BorderLayout.CENTER);
			text.add(Box.createVerticalStrut(GAP_XS));
			text.add(bottom);
		}
		card.add(text, BorderLayout.CENTER);
		setMemberTip(card, member, note, detailLabel);
		addMemberMenu(card, member);
		return fitHeight(card);
	}

	private JPanel offlineRow(JsonObject member)
	{
		Font small = smallFont();
		String note = visibleNote(member);
		String seen = TsgHubRoster.lastSeen(str(member, "lastSeenAt"), clock.instant());
		JPanel row = card();
		row.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
		row.add(memberName(member, str(member, "displayName"), MUTED, small), BorderLayout.CENTER);
		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		badges.setOpaque(false);
		addMemberBadges(badges, member, note, !seen.isEmpty());
		if (!seen.isEmpty()) badges.add(label(seen, MUTED, small));
		if (badges.getComponentCount() > 0) row.add(badges, BorderLayout.EAST);
		setMemberTip(row, member, note, null);
		addMemberMenu(row, member);
		return fitHeight(row);
	}

	private String visibleNote(JsonObject member)
	{
		return plugin.canManageOrganizerUi() ? str(member, "note") : "";
	}

	private JsonArray visibleWarnings(JsonObject member)
	{
		return plugin.canManageOrganizerUi() ? array(member, "warnings") : new JsonArray();
	}

	private void addMemberMenu(JComponent card, JsonObject member)
	{
		card.putClientProperty(MEMBER_KEY, member);
		card.setComponentPopupMenu(memberMenu);
		inheritMenu(card);
	}

	private void fillMemberMenu()
	{
		memberMenu.removeAll();
		Component invoker = memberMenu.getInvoker();
		while (invoker != null && !(invoker instanceof JComponent && ((JComponent) invoker).getClientProperty(MEMBER_KEY) != null)) invoker = invoker.getParent();
		if (invoker == null) return;
		JsonObject member = (JsonObject) ((JComponent) invoker).getClientProperty(MEMBER_KEY);
		String name = str(member, "displayName");
		javax.swing.JMenuItem lookup = new javax.swing.JMenuItem("Lookup");
		lookup.addActionListener(e -> shell.lookupView.showLookup(name, TsgHubPresence.accountType(member)));
		memberMenu.add(lookup);
		if (plugin.canManageOrganizerUi() && notesLoaded) shell.memberAdmin.addNoteItems(memberMenu, member);
	}

	private static void inheritMenu(java.awt.Container parent)
	{
		for (Component child : parent.getComponents())
		{
			if (child instanceof JComponent) ((JComponent) child).setInheritsPopupMenu(true);
			if (child instanceof java.awt.Container) inheritMenu((java.awt.Container) child);
		}
	}
}
