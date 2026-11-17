package com.tsghub;

import com.google.gson.*;
import com.google.inject.*;
import com.tsghub.TsgHubUi.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.inject.*;
import javax.inject.Inject;
import javax.swing.*;
import lombok.*;
import lombok.experimental.*;
import lombok.extern.slf4j.*;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.audio.*;
import net.runelite.client.callback.*;
import net.runelite.client.chat.*;
import net.runelite.client.config.*;
import net.runelite.client.eventbus.*;
import net.runelite.client.events.*;
import net.runelite.client.game.*;
import net.runelite.client.hiscore.*;
import net.runelite.client.input.*;
import net.runelite.client.party.*;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.infobox.*;
import net.runelite.client.ui.overlay.worldmap.*;
import net.runelite.client.util.*;
import okhttp3.*;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.TOKEN;

@Slf4j
@Accessors(fluent = true)
@PluginDescriptor(name = "TSG Hub", internalName = "tsg-hub", description = "TypeShii Gaming clan events and progress tracking", tags = {"tsg", "clan", "bingo", "events"})
public class TsgHubPlugin extends Plugin
{
	private static final String SERVICE_URL = "https://api.typeshiigaming.com";
	private static final long BOARD_AUTO_REFRESH_SECONDS = 15;
	@Inject private Client client;
	@Inject private ItemManager itemManager;
	@Inject private TsgHubConfig config;
	@Inject private ConfigManager configManager;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private OkHttpClient okHttpClient;
	@Inject private Gson gson;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private ChatIconManager chatIconManager;
	@Inject private PartyService partyService;
	@Inject private WSClient wsClient;
	@Inject private SpriteManager spriteManager;
	@Inject private HiscoreClient hiscoreClient;
	@Inject private EventBus eventBus;
	@Inject private WorldMapPointManager worldMapPointManager;
	@Inject private WorldMapOverlay worldMapOverlay;
	@Inject private OverlayManager overlayManager;
	@Inject private MouseManager mouseManager;
	@Inject private DrawManager drawManager;
	@Inject private AudioPlayer audioPlayer;
	@Inject private PluginManager pluginManager;
	@Inject private InfoBoxManager infoBoxManager;
	@Inject private SkillIconManager skillIconManager;
	@Inject @Named("developerMode") private boolean developerMode;
	@Getter private TsgHubSession session;
	@Getter private TsgHubClan clan;
	@Getter private TsgHubKeys keys;
	@Getter private TsgHubChat chat;
	@Getter private TsgHubSessions sessions;
	@Getter private TsgHubLiveSync liveSync;
	@Getter private TsgHubGameTracker tracker;
	@Getter private TsgHubTeamColors teamColors;
	@Getter private TsgHubSidebarPanel sidebar;
	@Getter private TsgHubBingo bingo;
	@Getter private volatile TsgHubApi api;
	@Getter private TsgHubSignals signals;
	@Getter private TsgHubOutbox outbox;
	@Getter private TsgHubUsage usage;
	@Getter private TsgHubGroups groups;
	@Getter private TsgHubPresence presence;
	@Getter private TsgHubMemberMap memberMap;
	@Getter private TsgHubDrops drops;
	@Getter private TsgHubRecords records;
	@Getter private TsgHubAnnouncements announcements;
	@Getter private TsgHubNewsOverlay newsOverlay;
	private TsgHubCoffer coffer;
	private TsgHubSales sales;
	private TsgHubTrades trades;
	private NavigationButton navigationButton;
	private ScheduledExecutorService executor;
	private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
	private final List<Object> subscribers = new ArrayList<>();
	private final List<Overlay> overlays = new ArrayList<>();
	private volatile boolean sidebarRouted;
	private volatile boolean routedAsHubMember;
	private volatile boolean routedClanPending;
	private TsgHubMapOverlay mapOverlay;
	private TsgHubCountdown countdown;
	private boolean worldMapOpen;
	private GroupTracker groupTracker;
	private TsgHubDefence defence;

	@Override
	protected void startUp()
	{
		session = new TsgHubSession(configManager);
		executor = Executors.newSingleThreadScheduledExecutor(daemon("tsg-hub-api"));
		executor.execute(() -> TsgHubRules.loadBundled(gson));
		String clanOverride = System.getProperty("tsghub.clanName", "").trim();
		clan = new TsgHubClan(client, developerMode && !clanOverride.isEmpty() ? clanOverride : "TSGaming");
		String serviceOverride = System.getProperty("tsghub.serviceUrl", "").trim();
		String serviceUrl = developerMode && !serviceOverride.isEmpty() ? serviceOverride : SERVICE_URL;
		api = new TsgHubApi(okHttpClient, gson, serviceUrl, executor);
		keys = new TsgHubKeys(this, config, configManager, executor);
		chat = new TsgHubChat(this, config, configManager, chatMessageManager);
		sessions = new TsgHubSessions(this, executor);
		liveSync = new TsgHubLiveSync(this, okHttpClient, gson, serviceUrl, executor);
		TsgHubSocket socket = liveSync.socket();
		outbox = new TsgHubOutbox(socket, api::call);
		signals = new TsgHubSignals(socket, outbox, api, chat::reply);
		usage = new TsgHubUsage(this::sharingInClan, this::usageIdentity, body -> outbox.report("usage", "/v1/usage", body), () -> TsgHubUsage.enabledSettings(config));
		every(usage::flush, TsgHubUsage.FLUSH_SECONDS, TsgHubUsage.FLUSH_SECONDS);
		executor.execute(this::loadCachedRules);
		every(liveSync::tick, 2, 5);
		poll(() -> sessions.autoRefreshBoard(sidebar), BOARD_AUTO_REFRESH_SECONDS);
		poll(sessions::pollNotifications, BOARD_AUTO_REFRESH_SECONDS);
		newsOverlay = overlay(new TsgHubNewsOverlay(this, config, client));
		mouseManager.registerMouseListener(newsOverlay);
		mouseManager.registerMouseWheelListener(newsOverlay);
		announcements = new TsgHubAnnouncements(this, executor, chat::clanAnnouncement);
		TsgHubRankIcons rankIcons = new TsgHubRankIcons(chatIconManager);
		bingo = new TsgHubBingo(client, audioPlayer, config, configManager, overlayManager, mouseManager, new TsgHubTileIcons(this::getItemImage, spriteManager), usage::count);
		bingo.start();
		PrayerSprites.load(gson);
		GroupMembersPanel groupMembers = new GroupMembersPanel(config, spriteManager, itemManager);
		sidebar = new TsgHubSidebarPanel(this, groupMembers);
		groups = new TsgHubGroups(this, client, partyService, executor);
		groupTracker = subscribe(new GroupTracker(client, clientThread, partyService, wsClient, itemManager, groups::isGroupParty, config::partyShowSelf, this::currentArea, groups));
		groups.setTracker(groupTracker);
		groupTracker.start();
		every(groups::heartbeat, 5, TsgHubGroups.HEARTBEAT_SECONDS);
		every(groupTracker::keepAlive, GroupTracker.KEEPALIVE_SECONDS, GroupTracker.KEEPALIVE_SECONDS);
		poll(groups::autoRefresh, TsgHubGroups.REFRESH_SECONDS);
		presence = new TsgHubPresence(this, client, clientThread, rankIcons, spriteManager, executor);
		poll(presence::autoRefresh, TsgHubPresence.REFRESH_SECONDS);
		memberMap = new TsgHubMemberMap(this, client, clientThread, worldMapPointManager, executor, this::api);
		mapOverlay = overlay(new TsgHubMapOverlay(client, worldMapOverlay, memberMap));
		countdown = overlay(new TsgHubCountdown(client, config, sessions::clanEvents, id -> !session.memberToken(id).isEmpty()));
		poll(memberMap::autoRefresh, TsgHubMemberMap.REFRESH_SECONDS);
		drops = new TsgHubDrops(this, client, drawManager, executor, this::api, () -> sidebar);
		poll(drops::autoRefresh, TsgHubDrops.REFRESH_SECONDS);
		records = new TsgHubRecords(this, configManager, executor, this::api);
		tracker = subscribe(new TsgHubGameTracker(this, client, clientThread, itemManager, executor));
		coffer = subscribe(new TsgHubCoffer(this, client, body -> tracker.report("coffer", "POST", "/v1/coffer", null, body)));
		sales = subscribe(new TsgHubSales(this, client, itemManager));
		trades = subscribe(new TsgHubTrades(this, client));
		defence = subscribe(new TsgHubDefence(this, client, clientThread, partyService, wsClient, pluginManager, infoBoxManager, overlayManager, skillIconManager, config, gson, chat::defenceSpec));
		defence.start();
		teamColors = subscribe(new TsgHubTeamColors(config, () -> ui(s -> s.membersView.refreshIfShown())));
		navigationButton = NavigationButton.builder()
			.tooltip("TSG Hub")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(5)
			.panel(sidebar)
			.build();
		clientToolbar.addNavigation(navigationButton);
		discordLinkChanged();
		sidebarRouted = false;
		clientThread.invokeLater(this::refreshDetectedClan);
	}

	private <T> T subscribe(T subscriber)
	{
		eventBus.register(subscriber);
		subscribers.add(subscriber);
		return subscriber;
	}

	private <T extends Overlay> T overlay(T overlay)
	{
		overlayManager.add(overlay);
		overlays.add(overlay);
		return overlay;
	}

	private void every(Runnable task, long initialDelay, long period)
	{
		tasks.add(executor.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS));
	}

	private void poll(Runnable task, long seconds)
	{
		every(() -> {
			if (!live()) task.run();
		}, seconds, seconds);
	}

	private static ThreadFactory daemon(String name)
	{
		return r -> {
			Thread thread = new Thread(r, name);
			thread.setDaemon(true);
			return thread;
		};
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
		if (defence != null) defence.stop();
		if (groupTracker != null) groupTracker.stop();
		subscribers.forEach(eventBus::unregister);
		subscribers.clear();
		if (groups != null && groupTracker != null && groupTracker.isSharing()) partyService.changeParty(null);
		if (presence != null) presence.leave();
		if (memberMap != null) memberMap.setOpen(false);
		if (bingo != null) bingo.stop();
		overlays.forEach(overlayManager::remove);
		overlays.clear();
		if (newsOverlay != null)
		{
			mouseManager.unregisterMouseListener(newsOverlay);
			mouseManager.unregisterMouseWheelListener(newsOverlay);
			newsOverlay.closeReader();
		}
		if (liveSync != null) liveSync.socket().disconnect();
		tasks.forEach(task -> task.cancel(false));
		tasks.clear();
		if (keys != null) keys.cancelRetry();
		if (drops != null) drops.shutDown();
		if (executor != null) executor.shutdownNow();
		TsgHubRules.reset();
		if (clan != null) clan.reset();
		if (keys != null) keys.reset();
		if (chat != null) chat.reset();
		if (sessions != null) sessions.reset();
		if (tracker != null) tracker.reset();
		sidebarRouted = false;
		routedAsHubMember = false;
		routedClanPending = false;
		worldMapOpen = false;
	}

	private void loadCachedRules()
	{
		try { liveSync.loadCachedRules(getPluginDirectory().join("rules.json")); }
		catch (Exception ignored) { }
	}

	void openAdminSite(Runnable done)
	{
		String key = keys.adminKey();
		if (!isAdmin() || key.isEmpty())
		{
			memberStatus(keys.needsLink() ? "The admin site needs a linked Discord account with admin access. Click Link Discord on the home screen." : "The admin site needs a Discord account with admin access.", Tone.ERROR);
			SwingUtilities.invokeLater(done);
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("displayName", clan.playerName());
		clan.addAccountHash(body);
		api().call("POST", "/v1/admin/login-link", key, body, response ->
			LinkBrowser.browse(response.get("url").getAsString()),
			e -> {
				int status = TsgHubApi.status(e);
				if (status == 401) keys.rejected(key);
				else
				{
					if (status == 403) keys.recheck(key);
					memberError("Couldn't open the admin site. ", e);
				}
			}, () -> SwingUtilities.invokeLater(done));
	}

	@Provides
	TsgHubConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TsgHubConfig.class);
	}

	void openNews(String id)
	{
		newsOverlay.openReader(id);
	}

	SpriteManager sprites() { return spriteManager; }

	boolean live() { return liveSync != null && liveSync.live(); }
	boolean sharingEnabled() { return config.dataSharingOptIn(); }
	boolean screenshotsEnabled() { return config.dataSharingOptIn() && config.shareDropScreenshots(); }
	boolean hidePrivateChat() { return config.hideScreenshotPms(); }
	boolean locationSharingEnabled() { return config.dataSharingOptIn() && config.shareLocation(); }
	CompletableFuture<HiscoreResult> lookupHiscores(String name, int accountType) { return hiscoreClient.lookupAsync(name, TsgHubHiscores.endpoint(accountType)); }
	String currentArea() { return presence == null ? "" : presence.area(); }
	AsyncBufferedImage getItemImage(int itemId) { return itemManager == null ? null : itemManager.getImage(itemId); }
	AsyncBufferedImage getCoinImage(long gp) { return itemManager == null ? null : itemManager.getImage(ItemID.COINS, (int) Math.min(gp, Integer.MAX_VALUE), false); }

	private JsonObject usageIdentity()
	{
		long hash = client.getAccountHash();
		return hash == -1 ? null : TsgHubJson.obj("clanName", clan.name(), "accountHash", Long.toString(hash));
	}

	boolean sharingInClan()
	{
		return clan.isHubClan() && config.dataSharingOptIn();
	}

	boolean isAdmin()
	{
		return clan.isHubClan() && keys.verified();
	}

	boolean canUseHub()
	{
		return clan.isHubClan() && sharingEnabled() && !clan.playerName().isEmpty();
	}

	void accessChanged()
	{
		liveSync.syncNow();
		if (presence != null && !(keys.verified() && live())) presence.autoRefresh();
		boolean access = isAdmin();
		SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setAdminAccess(access); });
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			// The local player can be null for a few ticks after login.
			clientThread.invokeLater(() -> {
				if (client.getGameState() != GameState.LOGGED_IN) return true;
				if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) return false;
				clan.loggedIn();
				refreshDetectedClan();
				return true;
			});
			return;
		}
		// Loading screens and world hops aren't logouts.
		if (state != GameState.LOGIN_SCREEN && state != GameState.LOGIN_SCREEN_AUTHENTICATOR) return;
		bingo.resetReveals();
		if (presence != null) presence.onLoggedOut();
		if (memberMap != null) memberMap.setOpen(false);
		worldMapOpen = false;
		chat.onLoggedOut();
		if (announcements != null) announcements.onLoggedOut();
		if (teamColors != null) teamColors.apply(null);
		clan.reset();
		sidebarRouted = false;
		keys.loggedOut();
		liveSync.syncNow();
		if (signals != null) signals.clear();
		if (records != null) records.reset();
		sessions.loggedOut();
		SwingUtilities.invokeLater(() -> {
			if (sidebar != null)
			{
				sidebar.setAdminAccess(false);
				sidebar.showLoggedOut();
			}
		});
		newsOverlay.closeReader();
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		String previousClan = clan.name();
		refreshDetectedClan();
		if (config.dataSharingOptIn() && sidebarRouted && !clan.name().equals(previousClan)) sessions.loadClanEvents();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (TsgHubRecords.PB_GROUP.equals(event.getGroup()) && records != null) records.onConfigChanged(event);
		if (!"tsghub".equals(event.getGroup())) return;
		if ("dataSharingOptIn".equals(event.getKey()) && !config.dataSharingOptIn() && outbox != null)
		{
			outbox.clear();
			usage.clear();
		}
		if ("newsOverlay".equals(event.getKey()) && newsOverlay != null)
		{
			if (!config.newsOverlay()) newsOverlay.closeReader();
			newsOverlay.refresh();
		}
		if ("partyShowSelf".equals(event.getKey()))
		{
			if (config.partyShowSelf()) groupTracker.refreshSelf();
			else ui(s -> s.groupMembers.removeSelf());
			return;
		}
		if (event.getKey().startsWith("party"))
		{
			boolean expand = "partyExpandMembers".equals(event.getKey());
			ui(s -> s.groupMembers.settingsChanged(expand));
			return;
		}
		if (event.getKey().startsWith("bingo"))
		{
			bingo.configChanged(event.getKey());
			liveSync.syncNow();
			if ("bingoOverlay".equals(event.getKey()))
			{
				if (config.bingoOverlay()) sessions.refreshBoard();
				ui(s -> s.boardView.boardOverlayChanged());
			}
			return;
		}
		if ("showClanMap".equals(event.getKey()))
		{
			clientThread.invokeLater(this::syncMemberMap);
			return;
		}
		if ("shareLocation".equals(event.getKey()) || "teamNameColors".equals(event.getKey()))
		{
			ui(s -> s.membersView.refreshIfShown());
			return;
		}
		if ("hubKey".equals(event.getKey()))
		{
			keys.keyChanged();
			discordLinkChanged();
			return;
		}
		if (!"dataSharingOptIn".equals(event.getKey())) return;
		if (!config.dataSharingOptIn())
		{
			if (groups != null) groups.onSharingDisabled();
			if (presence != null) presence.leave();
			if (signals != null) signals.clear();
			if (teamColors != null) teamColors.apply(null);
			sessions.clearAll();
			keys.revoke();
			newsOverlay.closeReader();
		}
		liveSync.syncNow();
		sidebarRouted = false;
		routeSidebar();
	}

	void enableSharing()
	{
		configManager.setConfiguration("tsghub", "dataSharingOptIn", true);
	}

	private void refreshDetectedClan()
	{
		clan.detect();
		boolean adminAccess = isAdmin();
		liveSync.syncNow();
		ui(s -> s.setAdminAccess(adminAccess));
		if (!sidebarRouted && !clan.playerName().isEmpty()) routeSidebar();
		else if (sidebarRouted && (routedAsHubMember != clan.isHubClan() || routedClanPending != clan.pending())) routeSidebar();
		else if (client.getGameState() != GameState.LOGGED_IN && !sidebarRouted) ui(s -> s.showLoggedOut());
		keys.check();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		sessions.profileChanged();
		bingo.clear();
		if (signals != null) signals.clear();
		liveSync.syncNow();
		if (clan.playerName().isEmpty()) return;
		sidebarRouted = false;
		routeSidebar();
	}

	private void routeSidebar()
	{
		if (sidebar == null) return;
		String player = clan.playerName();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && !player.isEmpty();
		if (loggedIn) sidebarRouted = true;
		routedAsHubMember = clan.isHubClan();
		boolean pending = clan.pending();
		routedClanPending = pending;
		if (loggedIn && !pending && sharingInClan())
		{
			sessions.syncIdentity();
			keys.check();
			records.syncPbs();
		}
		SwingUtilities.invokeLater(() -> {
			if (!loggedIn) { sidebar.showLoggedOut(); return; }
			if (pending) { sidebar.showCheckingClan(); return; }
			if (!clan.isHubClan()) { sidebar.showNotInClan(clan.hubName(), clan.name()); return; }
			if (!config.dataSharingOptIn()) { sidebar.showSharingOff(); return; }
			sidebar.homeView.showHome();
			if (!live())
			{
				sessions.loadClanEvents();
				announcements.load();
			}
			String eventId = session.get(EVENT_ID);
			if (!eventId.isEmpty() && !session.get(TOKEN).isEmpty() && PlayerNames.samePlayer(session.memberName(eventId), player))
				sessions.activateEvent(eventId, false);
		});
	}

	void openSettings()
	{
		Overlay owner = new Overlay(this)
		{
			@Override public Dimension render(Graphics2D graphics) { return null; }
		};
		eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "", ""), owner));
	}

	void openDiscordInvite(Runnable done)
	{
		discordInvite(e -> memberError("Couldn't open the Discord invite. ", e), () -> SwingUtilities.invokeLater(done));
	}

	void joinDiscord(Consumer<Exception> onError)
	{
		discordInvite(onError, () -> { });
	}

	private void discordInvite(Consumer<Exception> onError, Runnable done)
	{
		JsonObject body = new JsonObject();
		body.addProperty("displayName", clan.playerName());
		body.addProperty("clanName", clan.name());
		clan.addAccountHash(body);
		api().call("POST", "/v1/discord/invite", null, body, response ->
			LinkBrowser.browse(response.get("url").getAsString()), onError, done);
	}

	void discordLinkChanged()
	{
		boolean needsLink = keys.needsLink();
		long deadline = keys.linkDeadline();
		boolean starting = keys.linkStarting();
		ui(s -> s.setDiscordLink(needsLink, deadline, starting));
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (presence != null) presence.onInteractingChanged(event.getSource(), event.getTarget());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (presence != null) presence.onNpcDespawned(event.getNpc());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (presence != null) presence.onVarbitChanged(event.getVarpId(), event.getVarbitId());
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (presence != null) presence.onXp(event.getSkill(), event.getXp());
		if (sharingInClan() && client.getLocalPlayer() != null) signals().xp(event.getSkill().name(), event.getXp(), client.getTickCount());
	}

	private void syncMemberMap()
	{
		if (memberMap == null) return;
		boolean show = worldMapOpen && config.showClanMap();
		if (show == memberMap.isOpen()) return;
		memberMap.setOpen(show);
		if (show) usage.count("map.clanmates");
		liveSync.syncNow();
		if (show && !live()) memberMap.autoRefresh();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.WORLDMAP) return;
		worldMapOpen = true;
		syncMemberMap();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() != InterfaceID.WORLDMAP) return;
		worldMapOpen = false;
		syncMemberMap();
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		if (memberMap != null) memberMap.onClientTick();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (presence != null) presence.onGameTick();
		if (countdown != null) countdown.onGameTick(sharingInClan() && config.eventCountdown());
		if (sharingInClan()) chat.mapNotice();
		chat.onGameTick();
		if (clan.tick()) refreshDetectedClan();
	}

	void memberStatus(String message, Tone tone)
	{
		ui(s -> s.setStatus(message, tone));
	}

	void memberError(String failure, Exception e)
	{
		memberStatus(failure + TsgHubFormat.friendlyError(e), Tone.ERROR);
	}

	void sidebarBusy(boolean busy)
	{
		ui(s -> s.setBusy(busy));
	}

	void ui(Consumer<TsgHubSidebarPanel> action)
	{
		SwingUtilities.invokeLater(() -> { if (sidebar != null) action.accept(sidebar); });
	}
}
