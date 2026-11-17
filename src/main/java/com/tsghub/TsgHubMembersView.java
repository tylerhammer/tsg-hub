package com.tsghub;

import com.google.gson.*;
import com.tsghub.TsgHubSidebarPanel.*;
import java.awt.*;
import java.awt.image.*;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import static com.tsghub.PlayerNames.*;
import static com.tsghub.TsgHubFormat.*;
import static com.tsghub.TsgHubIcons.*;
import static com.tsghub.TsgHubJson.*;
import static com.tsghub.TsgHubSession.SHOW_OFFLINE;
import static com.tsghub.TsgHubTheme.*;
import static com.tsghub.TsgHubUi.*;

final class TsgHubMembersView
{
	private final TsgHubPlugin plugin;
	private final TsgHubSidebarPanel shell;

	private static final String MEMBER_KEY = "tsgHub.member";
	private final WidthTrackingPanel membersPage = new WidthTrackingPanel();
	final JScrollPane membersScroll = scroll(membersPage);
	private final JPopupMenu memberMenu = new JPopupMenu();

	JsonArray members;
	JsonArray offlineMembers = new JsonArray();
	private boolean notesLoaded;
	private final JCheckBox showOffline = new JCheckBox("Show offline");
	TsgHubMembersView(TsgHubPlugin plugin, TsgHubSidebarPanel shell)
	{
		this.plugin = plugin;
		this.shell = shell;
		memberMenu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { fillMemberMenu(); }
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
			@Override public void popupMenuCanceled(PopupMenuEvent e) { }
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

	void refreshIfShown()
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
		shell.viewHeader(View.MEMBERS, "Members", members == null ? "" : membersSummary(), membersSummary());
		membersPage.removeAll();
		if (members == null)
		{
			membersPage.add(caption("Loading members..."));
			refresh(membersPage);
			return;
		}
		if (members.size() == 0) membersPage.add(hint("Nobody online right now."));
		int myWorld = 0;
		for (JsonObject member : objects(members))
			if (samePlayer(str(member, "displayName"), plugin.clan().playerName())) myWorld = integer(member, "world", 0);
		String[] groups = {"Bossing", "Raiding", "Minigames", "Skilling", "Elsewhere"};
		for (int g = 0; g < groups.length; g++)
		{
			List<JsonObject> inGroup = new ArrayList<>();
			for (JsonObject member : objects(members)) if (activityGroup(str(member, "activity")) == g) inGroup.add(member);
			if (inGroup.isEmpty()) continue;
			if (membersPage.getComponentCount() > 0) membersPage.add(gap(GAP_S));
			membersPage.add(listHeading(groups[g] + " (" + inGroup.size() + ")", true, null));
			for (JsonObject member : inGroup) addCard(membersPage, memberCard(member, myWorld));
		}
		if (offlineMembers.size() > 0)
		{
			addGap(membersPage, GAP_S, listHeading("Offline (" + offlineMembers.size() + ")", true, showOffline));
			if (showOffline.isSelected())
			{
				for (JsonObject member : objects(offlineMembers))
				{
					addCard(membersPage, offlineRow(member));
				}
			}
		}
		membersPage.add(gap(GAP_M));
		membersPage.add(hint(plugin.locationSharingEnabled() ? "Leave clan chat to hide yourself."
			: "You show as Online. Turn on location sharing in settings to show what you're doing."));
		refresh(membersPage);
	}

	static int activityGroup(String activity)
	{
		String[] prefixes = {"Bossing", "Raiding", "Minigame", "Skilling"};
		for (int i = 0; i < prefixes.length; i++) if (activity.startsWith(prefixes[i])) return i;
		return prefixes.length;
	}

	static boolean inActivity(String activity)
	{
		return activityGroup(activity) < 3;
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
			array(member, "previousNames"), str(member, "altOf"), array(member, "alts"), note, visibleWarnings(member), clock.instant(), ZoneId.systemDefault()),
			() -> TsgHubHtml.escape(localTime(str(member, "timeZone"), clock.instant(), ZoneId.systemDefault())));
	}

	private void addMemberBadges(JPanel badges, JsonObject member, String note)
	{
		if (TsgHubRoster.hasActiveWarning(visibleWarnings(member))) inline(badges, new JLabel(icon("warning", TsgHubTheme.ERROR)));
		if (!note.isEmpty()) inline(badges, new JLabel(icon("note", WARNING)));
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
		Color team = plugin.teamColors().colorOf(name);
		top.add(memberName(member, name, team != null ? team : TEXT, boldFont()), BorderLayout.CENTER);
		JPanel badges = inline();
		addMemberBadges(badges, member, note);
		if (world > 0) inline(badges, worldChip(world, !self && world == myWorld));
		if (badges.getComponentCount() > 0) top.add(badges, BorderLayout.EAST);

		JPanel text = stack();
		text.add(top);
		String detail = TsgHubRoster.activityDetail(activity, str(member, "area"));
		JLabel detailLabel = null;
		if (!detail.isEmpty())
		{
			boolean active = inActivity(activity);
			detailLabel = noFullText(shrinkable(label(detail, active ? SUCCESS : MUTED, small)));
			JPanel bottom = row();
			bottom.add(detailLabel, BorderLayout.CENTER);
			addGap(text, GAP_XS, bottom);
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
		JPanel badges = inline();
		addMemberBadges(badges, member, note);
		if (!seen.isEmpty()) inline(badges, label(seen, MUTED, small));
		if (badges.getComponentCount() > 0) row.add(badges, BorderLayout.EAST);
		setMemberTip(row, member, note, null);
		addMemberMenu(row, member);
		return fitHeight(row);
	}

	private String visibleNote(JsonObject member)
	{
		return plugin.isAdmin() ? str(member, "note") : "";
	}

	private JsonArray visibleWarnings(JsonObject member)
	{
		return plugin.isAdmin() ? array(member, "warnings") : new JsonArray();
	}

	private void addMemberMenu(JComponent card, JsonObject member)
	{
		card.putClientProperty(MEMBER_KEY, member);
		withPopup(card, memberMenu);
	}

	private void fillMemberMenu()
	{
		memberMenu.removeAll();
		Component invoker = memberMenu.getInvoker();
		while (invoker != null && !(invoker instanceof JComponent && ((JComponent) invoker).getClientProperty(MEMBER_KEY) != null)) invoker = invoker.getParent();
		if (invoker == null) return;
		JsonObject member = (JsonObject) ((JComponent) invoker).getClientProperty(MEMBER_KEY);
		String name = str(member, "displayName");
		item(memberMenu, "Lookup", () -> shell.lookupView.showLookup(name, TsgHubPresence.accountType(member)));
		if (plugin.isAdmin() && notesLoaded) shell.memberAdmin.addNoteItems(memberMenu, member);
	}
}
