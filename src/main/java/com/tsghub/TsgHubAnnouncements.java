package com.tsghub;

import com.google.gson.*;
import java.awt.image.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;
import net.runelite.client.util.*;
import static com.tsghub.TsgHubJson.*;

final class TsgHubAnnouncements
{
	static final int MAX_CHAT_LINES = 3;
	private static final int MAX_CACHED_IMAGES = 20;

	private final TsgHubPlugin plugin;
	private final ScheduledExecutorService executor;
	private final BiConsumer<String, String> chat;
	private final TsgHubNewsPoll polls;
	private volatile JsonArray list;
	private final Map<String, CompletableFuture<BufferedImage>> images = new LinkedHashMap<String, CompletableFuture<BufferedImage>>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<BufferedImage>> eldest)
		{
			return size() > MAX_CACHED_IMAGES;
		}
	};

	TsgHubAnnouncements(TsgHubPlugin plugin, ScheduledExecutorService executor, BiConsumer<String, String> chat)
	{
		this.plugin = plugin;
		this.executor = executor;
		this.chat = chat;
		this.polls = new TsgHubNewsPoll(plugin, this, () -> {
			plugin.ui(s -> s.newsView.setAnnouncements(list));
			plugin.newsOverlay().refresh();
		});
	}

	TsgHubNewsPoll polls()
	{
		return polls;
	}

	JsonArray list()
	{
		return list;
	}

	void openImage(String path)
	{
		if (TsgHubApi.isNewsImage(path)) LinkBrowser.browse(plugin.api().url(path));
	}

	synchronized CompletableFuture<BufferedImage> image(String path)
	{
		CompletableFuture<BufferedImage> image = images.get(path);
		if (image == null || image.isCompletedExceptionally())
		{
			image = plugin.api().newsImage(path);
			images.put(path, image);
		}
		return image;
	}

	void load()
	{
		if (!plugin.canUseHub() || executor.isShutdown()) return;
		String clan = TsgHubApi.encode(plugin.clan().name());
		String hash = plugin.clan().accountHash();
		String query = "/v1/announcements?clanName=" + clan + (hash.isEmpty() ? "" : "&accountHash=" + hash);
		plugin.api().call("GET", query, null, null)
			.thenAcceptAsync(result -> apply(array(result, "announcements")), executor);
	}

	void apply(JsonArray all)
	{
		if (!plugin.canUseHub()) return;
		JsonArray announcements = published(all);
		list = announcements;
		SwingUtilities.invokeLater(() -> polls.reset(announcements));
		plugin.ui(s -> s.newsView.setAnnouncements(announcements));
		alert(announcements);
		plugin.newsOverlay().refresh();
	}

	static JsonArray published(JsonArray all)
	{
		JsonArray out = new JsonArray();
		for (JsonObject announcement : objects(all))
			if (!bool(announcement, "draft") && !bool(announcement, "expired")) out.add(announcement);
		return out;
	}

	private synchronized void alert(JsonArray announcements)
	{
		Set<String> skip = ids(TsgHubSession.ANNOUNCEMENTS_ALERTED);
		skip.addAll(readIds());
		List<JsonObject> fresh = unread(announcements, skip);
		if (fresh.isEmpty()) return;
		Set<String> alerted = new LinkedHashSet<>();
		for (JsonObject announcement : objects(announcements))
		{
			String id = str(announcement, "id");
			if (skip.contains(id) || fresh.contains(announcement)) alerted.add(id);
		}
		plugin.session().set(TsgHubSession.ANNOUNCEMENTS_ALERTED, String.join(",", alerted));
		List<String> lines = chatLines(fresh);
		for (int i = 0; i < lines.size(); i++) chat.accept(i < MAX_CHAT_LINES && pollOnly(fresh.get(i)) ? "Poll" : "Announcement", lines.get(i));
	}

	void onLoggedOut()
	{
		list = null;
		plugin.newsOverlay().closeReader();
		plugin.newsOverlay().refresh();
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
		for (JsonObject announcement : objects(current)) ids.add(str(announcement, "id"));
		plugin.session().set(TsgHubSession.ANNOUNCEMENTS_READ, String.join(",", ids));
		plugin.newsOverlay().refresh();
	}

	void markRead(String id)
	{
		Set<String> ids = readIds();
		ids.add(id);
		plugin.session().set(TsgHubSession.ANNOUNCEMENTS_READ, String.join(",", ids));
		plugin.newsOverlay().refresh();
		plugin.ui(s -> s.newsView.setAnnouncements(list));
	}

	Set<String> readIds()
	{
		return ids(TsgHubSession.ANNOUNCEMENTS_READ);
	}

	private Set<String> ids(String key)
	{
		String stored = plugin.session().get(key);
		return stored.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(stored.split(",")));
	}

	static List<JsonObject> unread(JsonArray announcements, Set<String> read)
	{
		List<JsonObject> out = new ArrayList<>();
		for (JsonObject announcement : objects(announcements))
			if (!read.contains(str(announcement, "id"))) out.add(announcement);
		return out;
	}

	static List<String> chatLines(List<JsonObject> fresh)
	{
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < fresh.size() && i < MAX_CHAT_LINES; i++)
			lines.add(str(fresh.get(i), "title"));
		int more = fresh.size() - MAX_CHAT_LINES;
		if (more > 0) lines.add("+" + TsgHubFormat.plural(more, "more announcement") + " in the TSG Hub sidebar.");
		return lines;
	}

	static JsonObject poll(JsonObject announcement)
	{
		return announcement != null && announcement.has("poll") && announcement.get("poll").isJsonObject() ? announcement.getAsJsonObject("poll") : null;
	}

	static boolean pollOnly(JsonObject announcement)
	{
		return bool(announcement, "pollOnly") && poll(announcement) != null;
	}

	static List<JsonObject> homePolls(JsonArray announcements)
	{
		List<JsonObject> out = new ArrayList<>();
		for (JsonObject announcement : objects(announcements)) if (homePoll(announcement)) out.add(announcement);
		return out;
	}

	static boolean homePoll(JsonObject announcement)
	{
		return pollOnly(announcement) && bool(announcement, "pinned") && myVote(poll(announcement)).isEmpty();
	}

	static List<Integer> myVote(JsonObject poll)
	{
		List<Integer> picks = new ArrayList<>();
		for (JsonElement pick : array(poll, "myVote")) if (pick.isJsonPrimitive()) picks.add(pick.getAsInt());
		return picks;
	}

	void vote(String id, List<Integer> options, Consumer<String> failed)
	{
		String hash = plugin.clan().accountHash();
		if (hash.isEmpty() || !plugin.canUseHub() || executor.isShutdown())
		{
			failed.accept("Log in to vote.");
			return;
		}
		JsonArray picks = new JsonArray();
		for (int option : options) picks.add(option);
		sendVote("POST", path(id) + "/vote", obj("clanName", plugin.clan().name(), "accountHash", hash, "options", picks), failed);
	}

	void unvote(String id, Consumer<String> failed)
	{
		String hash = plugin.clan().accountHash();
		if (hash.isEmpty() || !plugin.canUseHub() || executor.isShutdown())
		{
			failed.accept("Log in to vote.");
			return;
		}
		sendVote("DELETE", path(id) + "/vote?clanName=" + TsgHubApi.encode(plugin.clan().name()) + "&accountHash=" + hash, null, failed);
	}

	private void sendVote(String method, String path, JsonObject request, Consumer<String> failed)
	{
		plugin.api().call(method, path, null, request, response -> {
			if (!plugin.live()) load();
		}, e -> {
			String message = "Couldn't vote. " + TsgHubFormat.friendlyError(e);
			SwingUtilities.invokeLater(() -> failed.accept(message));
		});
	}

	private static String path(String id)
	{
		return "/v1/announcements/" + TsgHubApi.encode(id);
	}
}
