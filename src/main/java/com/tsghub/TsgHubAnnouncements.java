package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tsghub.TsgHubUi.Tone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

final class TsgHubAnnouncements
{
	static final int MAX_CHAT_LINES = 3;

	private final TsgHubPlugin plugin;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
	private final Supplier<TsgHubNewsEditor> panel;
	private final Consumer<String> chat;
	private volatile JsonArray list;

	TsgHubAnnouncements(TsgHubPlugin plugin, ScheduledExecutorService executor, Supplier<TsgHubApi> api, Supplier<TsgHubNewsEditor> panel, Consumer<String> chat)
	{
		this.plugin = plugin;
		this.executor = executor;
		this.api = api;
		this.panel = panel;
		this.chat = chat;
	}

	void load()
	{
		if (!plugin.canUseHub() || executor.isShutdown()) return;
		String clan = TsgHubApi.encode(plugin.getDetectedClanName());
		String token = plugin.adminKey();
		executor.submit(() -> {
			try { apply(TsgHubUi.array(api.get().request("GET", "/v1/announcements?clanName=" + clan, token.isEmpty() ? null : token, null), "announcements")); }
			catch (Exception ignored) { }
		});
	}

	void apply(JsonArray all)
	{
		if (!plugin.canUseHub()) return;
		JsonArray announcements = published(all);
		list = announcements;
		plugin.ui(s -> s.setAnnouncements(announcements));
		SwingUtilities.invokeLater(() -> {
			TsgHubNewsEditor admin = panel.get();
			if (admin != null) admin.setAnnouncements(all);
		});
		alert(announcements);
	}

	static JsonArray published(JsonArray all)
	{
		JsonArray out = new JsonArray();
		for (JsonObject announcement : TsgHubUi.objects(all)) if (!TsgHubUi.bool(announcement, "draft")) out.add(announcement);
		return out;
	}

	private synchronized void alert(JsonArray announcements)
	{
		Set<String> skip = ids(TsgHubSession.ANNOUNCEMENTS_ALERTED);
		skip.addAll(readIds());
		List<JsonObject> fresh = unread(announcements, skip);
		if (fresh.isEmpty()) return;
		Set<String> alerted = new LinkedHashSet<>();
		for (JsonObject announcement : TsgHubUi.objects(announcements))
		{
			String id = TsgHubUi.str(announcement, "id");
			if (skip.contains(id) || fresh.contains(announcement)) alerted.add(id);
		}
		TsgHubSession.set(TsgHubSession.ANNOUNCEMENTS_ALERTED, String.join(",", alerted));
		for (String line : chatLines(fresh)) chat.accept(line);
	}

	void onLoggedOut()
	{
		list = null;
	}

	int unreadCount()
	{
		JsonArray current = list;
		return current == null ? 0 : unread(current, readIds()).size();
	}

	boolean isUnread(String id)
	{
		return !readIds().contains(id);
	}

	void markRead()
	{
		JsonArray current = list;
		if (current == null) return;
		Set<String> ids = new LinkedHashSet<>();
		for (JsonObject announcement : TsgHubUi.objects(current)) ids.add(TsgHubUi.str(announcement, "id"));
		TsgHubSession.set(TsgHubSession.ANNOUNCEMENTS_READ, String.join(",", ids));
	}

	private static Set<String> readIds()
	{
		return ids(TsgHubSession.ANNOUNCEMENTS_READ);
	}

	private static Set<String> ids(String key)
	{
		String stored = TsgHubSession.get(key);
		return stored.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(stored.split(",")));
	}

	static List<JsonObject> unread(JsonArray announcements, Set<String> read)
	{
		List<JsonObject> out = new ArrayList<>();
		for (JsonObject announcement : TsgHubUi.objects(announcements))
			if (!read.contains(TsgHubUi.str(announcement, "id"))) out.add(announcement);
		return out;
	}

	static List<String> chatLines(List<JsonObject> fresh)
	{
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < fresh.size() && i < MAX_CHAT_LINES; i++) lines.add("News: " + TsgHubUi.str(fresh.get(i), "title"));
		int more = fresh.size() - MAX_CHAT_LINES;
		if (more > 0) lines.add("+" + TsgHubUi.plural(more, "more announcement") + " in the TSG Hub sidebar.");
		return lines;
	}

	void save(String id, String title, String body, boolean pinned, Integer expiresInDays, boolean publish)
	{
		JsonObject request = adminBody();
		request.addProperty("title", title);
		request.addProperty("body", body);
		request.addProperty("pinned", pinned);
		if (expiresInDays != null) request.addProperty("expiresInDays", expiresInDays);
		String success = publish ? "Announcement published." : "Saved.";
		if (id.isEmpty())
		{
			request.addProperty("draft", !publish);
			submit(success, true, token -> announcementId(api.get().request("POST", "/v1/announcements", token, request)));
			return;
		}
		submit(success, true, token -> {
			api.get().request("PATCH", path(id), token, request);
			if (publish) api.get().request("POST", path(id) + "/publish", token, adminBody());
			return id;
		});
	}

	void publish(String id)
	{
		submit("Announcement published.", false, token -> announcementId(api.get().request("POST", path(id) + "/publish", token, adminBody())));
	}

	void setPinned(String id, boolean pinned)
	{
		JsonObject request = adminBody();
		request.addProperty("pinned", pinned);
		submit(pinned ? "Pinned." : "Unpinned.", false, token -> announcementId(api.get().request("PATCH", path(id), token, request)));
	}

	void delete(String id)
	{
		String clan = TsgHubApi.encode(plugin.getDetectedClanName());
		submit("Announcement deleted.", false, token -> {
			api.get().request("DELETE", path(id) + "?clanName=" + clan, token, null);
			return "";
		});
	}

	private static String path(String id)
	{
		return "/v1/announcements/" + TsgHubApi.encode(id);
	}

	private static String announcementId(JsonObject result)
	{
		return TsgHubUi.str(TsgHubUi.object(result, "announcement"), "id");
	}

	private JsonObject adminBody()
	{
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.getDetectedClanName());
		return body;
	}

	private interface Write
	{
		String run(String token) throws Exception;
	}

	private void submit(String success, boolean form, Write write)
	{
		String token = plugin.adminKey();
		if (token.isEmpty() || executor.isShutdown())
		{
			status("Announcements need a hub key with admin access.", Tone.ERROR);
			if (form) SwingUtilities.invokeLater(() -> panel.get().failed());
			return;
		}
		status("Saving...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				String id = write.run(token);
				status(success, Tone.SUCCESS);
				if (form) SwingUtilities.invokeLater(() -> panel.get().showAnnouncement(id));
				load();
			}
			catch (Exception e)
			{
				status("Couldn't save. " + TsgHubUi.friendlyError(e), Tone.ERROR);
				if (form) SwingUtilities.invokeLater(() -> panel.get().failed());
			}
		});
	}

	private void status(String message, Tone tone)
	{
		SwingUtilities.invokeLater(() -> {
			TsgHubNewsEditor admin = panel.get();
			if (admin != null) admin.setStatus(message, tone);
		});
	}
}
