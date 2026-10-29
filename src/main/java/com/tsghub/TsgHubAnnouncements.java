package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.client.util.LinkBrowser;

final class TsgHubAnnouncements
{
	static final int MAX_CHAT_LINES = 3;
	private static final int MAX_CACHED_IMAGES = 20;

	private final TsgHubPlugin plugin;
	private final ScheduledExecutorService executor;
	private final Supplier<TsgHubApi> api;
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

	TsgHubAnnouncements(TsgHubPlugin plugin, ScheduledExecutorService executor, Supplier<TsgHubApi> api, BiConsumer<String, String> chat)
	{
		this.plugin = plugin;
		this.executor = executor;
		this.api = api;
		this.chat = chat;
		this.polls = new TsgHubNewsPoll(plugin, this, () -> {
			plugin.windows().news(TsgHubNewsWindow::render);
			plugin.ui(s -> s.newsView.setAnnouncements(list));
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
		if (TsgHubApi.isNewsImage(path)) LinkBrowser.browse(api.get().url(path));
	}

	synchronized CompletableFuture<BufferedImage> image(String path)
	{
		CompletableFuture<BufferedImage> image = images.get(path);
		if (image == null || image.isCompletedExceptionally())
		{
			image = api.get().newsImage(path);
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
		api.get().call("GET", query, null, null)
			.thenAcceptAsync(result -> apply(TsgHubJson.array(result, "announcements")), executor);
	}

	void apply(JsonArray all)
	{
		if (!plugin.canUseHub()) return;
		JsonArray announcements = published(all);
		list = announcements;
		SwingUtilities.invokeLater(() -> polls.reset(announcements));
		plugin.ui(s -> s.newsView.setAnnouncements(announcements));
		plugin.windows().news(w -> w.setAnnouncements(announcements));
		alert(announcements);
	}

	static JsonArray published(JsonArray all)
	{
		JsonArray out = new JsonArray();
		for (JsonObject announcement : TsgHubJson.objects(all))
			if (!TsgHubJson.bool(announcement, "draft") && !TsgHubJson.bool(announcement, "expired")) out.add(announcement);
		return out;
	}

	private synchronized void alert(JsonArray announcements)
	{
		Set<String> skip = ids(TsgHubSession.ANNOUNCEMENTS_ALERTED);
		skip.addAll(readIds());
		List<JsonObject> fresh = unread(announcements, skip);
		if (fresh.isEmpty()) return;
		Set<String> alerted = new LinkedHashSet<>();
		for (JsonObject announcement : TsgHubJson.objects(announcements))
		{
			String id = TsgHubJson.str(announcement, "id");
			if (skip.contains(id) || fresh.contains(announcement)) alerted.add(id);
		}
		plugin.session().set(TsgHubSession.ANNOUNCEMENTS_ALERTED, String.join(",", alerted));
		List<String> lines = chatLines(fresh);
		for (int i = 0; i < lines.size(); i++) chat.accept(i < MAX_CHAT_LINES && pollOnly(fresh.get(i)) ? "Poll" : "Announcement", lines.get(i));
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
		for (JsonObject announcement : TsgHubJson.objects(current)) ids.add(TsgHubJson.str(announcement, "id"));
		plugin.session().set(TsgHubSession.ANNOUNCEMENTS_READ, String.join(",", ids));
	}

	private Set<String> readIds()
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
		for (JsonObject announcement : TsgHubJson.objects(announcements))
			if (!read.contains(TsgHubJson.str(announcement, "id"))) out.add(announcement);
		return out;
	}

	static List<String> chatLines(List<JsonObject> fresh)
	{
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < fresh.size() && i < MAX_CHAT_LINES; i++)
			lines.add(TsgHubJson.str(fresh.get(i), "title"));
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
		return TsgHubJson.bool(announcement, "pollOnly") && poll(announcement) != null;
	}

	static List<JsonObject> homePolls(JsonArray announcements)
	{
		List<JsonObject> out = new ArrayList<>();
		for (JsonObject announcement : TsgHubJson.objects(announcements)) if (homePoll(announcement)) out.add(announcement);
		return out;
	}

	static boolean homePoll(JsonObject announcement)
	{
		return pollOnly(announcement) && TsgHubJson.bool(announcement, "pinned") && myVote(poll(announcement)).isEmpty();
	}

	static List<Integer> myVote(JsonObject poll)
	{
		List<Integer> picks = new ArrayList<>();
		for (JsonElement pick : TsgHubJson.array(poll, "myVote")) if (pick.isJsonPrimitive()) picks.add(pick.getAsInt());
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
		JsonObject request = clanBody();
		request.addProperty("accountHash", hash);
		JsonArray picks = new JsonArray();
		for (int option : options) picks.add(option);
		request.add("options", picks);
		sendVote("POST", path(id) + "/vote", request, failed);
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
		api.get().call(method, path, null, request).whenCompleteAsync((response, error) -> {
			try
			{
				TsgHubApi.result(response, error);
				if (!plugin.live()) load();
			}
			catch (Exception e)
			{
				String message = "Couldn't vote. " + TsgHubFormat.friendlyError(e);
				SwingUtilities.invokeLater(() -> failed.accept(message));
			}
		}, executor);
	}

	private static String path(String id)
	{
		return "/v1/announcements/" + TsgHubApi.encode(id);
	}

	private JsonObject clanBody()
	{
		JsonObject body = new JsonObject();
		body.addProperty("clanName", plugin.clan().name());
		return body;
	}
}
