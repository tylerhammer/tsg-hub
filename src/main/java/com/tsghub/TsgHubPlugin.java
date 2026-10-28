package com.tsghub;

import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.TOKEN;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.tsghub.TsgHubUi.Tone;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import okhttp3.OkHttpClient;

@Slf4j
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
	@Inject private DrawManager drawManager;
	@Inject @Named("developerMode") private boolean developerMode;
	private TsgHubSession session;
	private TsgHubClan clan;
	private TsgHubKeys keys;
	private TsgHubChat chat;
	private TsgHubWindows windows;
	private TsgHubSessions sessions;
	private TsgHubLiveSync liveSync;
	private TsgHubGameTracker tracker;
	private TsgHubCoffer coffer;
	private TsgHubSidebarPanel sidebar;
	private NavigationButton navigationButton;
	private ScheduledExecutorService executor;
	private final List<ScheduledFuture<?>> tasks = new ArrayList<>();
	private volatile boolean sidebarRouted;
	private volatile boolean routedAsHubMember;
	private volatile boolean routedClanPending;
	private volatile TsgHubApi api;
	private TsgHubSignals signals;
	private TsgHubReports reports;
	private TsgHubGroups groups;
	private TsgHubPresence presence;
	private TsgHubMemberMap memberMap;
	private TsgHubMapOverlay mapOverlay;
	private boolean worldMapOpen;
	private TsgHubDrops drops;
	private TsgHubRecords records;
	private TsgHubAnnouncements announcements;
	private GroupTracker groupTracker;

	@Override
	protected void startUp()
	{
		session = new TsgHubSession(configManager);
		TsgHubRules.loadBundled(gson);
		String clanOverride = System.getProperty("tsghub.clanName", "").trim();
		clan = new TsgHubClan(client, developerMode && !clanOverride.isEmpty() ? clanOverride : "TSGaming");
		String serviceOverride = System.getProperty("tsghub.serviceUrl", "").trim();
		String serviceUrl = developerMode && !serviceOverride.isEmpty() ? serviceOverride : SERVICE_URL;
		api = new TsgHubApi(okHttpClient, gson, serviceUrl);
		executor = Executors.newSingleThreadScheduledExecutor(daemon("tsg-hub-api"));
		keys = new TsgHubKeys(this, config, configManager, executor);
		chat = new TsgHubChat(this, config, configManager, chatMessageManager);
		sessions = new TsgHubSessions(this, executor);
		liveSync = new TsgHubLiveSync(this, okHttpClient, gson, serviceUrl, executor);
		TsgHubSocket socket = liveSync.socket();
		signals = new TsgHubSignals(socket, this::api, chat::reply);
		reports = new TsgHubReports(socket::send, (method, path, token, body) -> api().request(method, path, token, body));
		executor.execute(this::loadCachedRules);
		every(liveSync::tick, 2, 5, TimeUnit.SECONDS);
		every(liveSync.unlessLive(() -> sessions.autoRefreshBoard(sidebar)), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		every(liveSync.unlessLive(sessions::pollNotifications), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		announcements = new TsgHubAnnouncements(this, executor, this::api, chat::clanAnnouncement);
		TsgHubRankIcons rankIcons = new TsgHubRankIcons(chatIconManager);
		windows = new TsgHubWindows(client, () -> announcements);
		GroupMembersPanel groupMembers = new GroupMembersPanel(config, spriteManager, itemManager);
		sidebar = new TsgHubSidebarPanel(this, groupMembers);
		groups = new TsgHubGroups(this, client, partyService, executor, this::api, () -> sidebar, socket);
		groupTracker = new GroupTracker(client, clientThread, partyService, wsClient, itemManager, groups::isGroupParty, config::partyShowSelf, this::currentArea, groups);
		groups.setTracker(groupTracker);
		eventBus.register(groupTracker);
		groupTracker.start();
		every(groups::heartbeat, 5, TsgHubGroups.HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		every(groupTracker::keepAlive, GroupTracker.KEEPALIVE_SECONDS, GroupTracker.KEEPALIVE_SECONDS, TimeUnit.SECONDS);
		every(liveSync.unlessLive(groups::autoRefresh), TsgHubGroups.REFRESH_SECONDS, TsgHubGroups.REFRESH_SECONDS, TimeUnit.SECONDS);
		presence = new TsgHubPresence(this, client, clientThread, rankIcons, spriteManager, executor, this::api, () -> sidebar, socket, keys::adminKey);
		every(liveSync.unlessLive(presence::autoRefresh), TsgHubPresence.REFRESH_SECONDS, TsgHubPresence.REFRESH_SECONDS, TimeUnit.SECONDS);
		memberMap = new TsgHubMemberMap(this, client, clientThread, worldMapPointManager, executor, this::api);
		mapOverlay = new TsgHubMapOverlay(client, worldMapOverlay, memberMap);
		overlayManager.add(mapOverlay);
		every(liveSync.unlessLive(memberMap::autoRefresh), TsgHubMemberMap.REFRESH_SECONDS, TsgHubMemberMap.REFRESH_SECONDS, TimeUnit.SECONDS);
		drops = new TsgHubDrops(this, client, drawManager, executor, this::api, () -> sidebar);
		every(liveSync.unlessLive(drops::autoRefresh), TsgHubDrops.REFRESH_SECONDS, TsgHubDrops.REFRESH_SECONDS, TimeUnit.SECONDS);
		records = new TsgHubRecords(this, configManager, executor, this::api);
		tracker = new TsgHubGameTracker(this, client, clientThread, itemManager, executor);
		eventBus.register(tracker);
		coffer = new TsgHubCoffer(this, client, body -> tracker.report("coffer", "/v1/coffer", body));
		eventBus.register(coffer);
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

	private void every(Runnable task, long initialDelay, long period, TimeUnit unit)
	{
		tasks.add(executor.scheduleAtFixedRate(task, initialDelay, period, unit));
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
		if (tracker != null) eventBus.unregister(tracker);
		if (coffer != null) eventBus.unregister(coffer);
		if (groupTracker != null)
		{
			groupTracker.stop();
			eventBus.unregister(groupTracker);
		}
		// Stay listed across a brief restart, but leave the RuneLite party.
		if (groups != null && groupTracker != null && groupTracker.isSharing()) partyService.changeParty(null);
		if (windows != null) windows.dispose();
		if (presence != null) presence.shutDown();
		if (memberMap != null) memberMap.setOpen(false);
		if (mapOverlay != null) overlayManager.remove(mapOverlay);
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
		api().call("POST", "/v1/admin/login-link", key, body).whenCompleteAsync(TsgHubApi.handle(response ->
			LinkBrowser.browse(response.get("url").getAsString()),
			e -> {
				int status = e instanceof TsgHubApi.HttpError ? ((TsgHubApi.HttpError) e).status : 0;
				if (status == 401) keys.rejected(key);
				else
				{
					if (status == 403) keys.recheck(key);
					memberError("Couldn't open the admin site. ", e);
				}
			}, () -> SwingUtilities.invokeLater(done)), executor);
	}

	@Provides
	TsgHubConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TsgHubConfig.class);
	}

	TsgHubSession session() { return session; }
	TsgHubClan clan() { return clan; }
	TsgHubKeys keys() { return keys; }
	TsgHubChat chat() { return chat; }
	TsgHubWindows windows() { return windows; }
	TsgHubSessions sessions() { return sessions; }
	TsgHubLiveSync liveSync() { return liveSync; }
	TsgHubGameTracker tracker() { return tracker; }
	TsgHubSignals signals() { return signals; }
	TsgHubReports reports() { return reports; }
	TsgHubApi api() { return api; }
	TsgHubSidebarPanel sidebar() { return sidebar; }
	TsgHubGroups groups() { return groups; }
	TsgHubPresence presence() { return presence; }
	TsgHubMemberMap memberMap() { return memberMap; }
	TsgHubDrops drops() { return drops; }
	TsgHubRecords records() { return records; }
	TsgHubAnnouncements announcements() { return announcements; }
	SpriteManager sprites() { return spriteManager; }

	boolean live() { return liveSync != null && liveSync.live(); }
	boolean sharingEnabled() { return config.dataSharingOptIn(); }
	boolean screenshotsEnabled() { return config.dataSharingOptIn() && config.shareDropScreenshots(); }
	boolean locationSharingEnabled() { return config.dataSharingOptIn() && config.shareLocation(); }
	CompletableFuture<HiscoreResult> lookupHiscores(String name, int accountType) { return hiscoreClient.lookupAsync(name, TsgHubHiscores.endpoint(accountType)); }
	String currentArea() { return presence == null ? "" : presence.area(); }
	AsyncBufferedImage getItemImage(int itemId) { return itemManager == null ? null : itemManager.getImage(itemId); }
	AsyncBufferedImage getCoinImage(long gp) { return itemManager == null ? null : itemManager.getImage(ItemID.COINS, (int) Math.min(gp, Integer.MAX_VALUE), false); }

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
		if (presence != null) presence.onLoggedOut();
		if (memberMap != null) memberMap.setOpen(false);
		worldMapOpen = false;
		chat.onLoggedOut();
		if (announcements != null) announcements.onLoggedOut();
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
		windows.hideAll();
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
		if ("dataSharingOptIn".equals(event.getKey()) && !config.dataSharingOptIn() && reports != null) reports.clear();
		if ("partyShowSelf".equals(event.getKey()))
		{
			if (config.partyShowSelf()) groupTracker.refreshSelf();
			else ui(s -> s.partiesView.groupSelfHidden());
			return;
		}
		if (event.getKey().startsWith("party"))
		{
			boolean expand = "partyExpandMembers".equals(event.getKey());
			ui(s -> s.partiesView.groupSettingsChanged(expand));
			return;
		}
		if ("showClanMap".equals(event.getKey()))
		{
			clientThread.invokeLater(this::syncMemberMap);
			return;
		}
		if ("shareLocation".equals(event.getKey()))
		{
			ui(s -> s.membersView.locationSharingChanged());
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
			sessions.clearAll();
			keys.revoke();
			windows.hideAll();
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
		api().call("POST", "/v1/discord/invite", null, body).whenCompleteAsync(TsgHubApi.handle(response ->
			LinkBrowser.browse(response.get("url").getAsString()), onError, done), executor);
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
	}

	private void syncMemberMap()
	{
		if (memberMap == null) return;
		boolean show = worldMapOpen && config.showClanMap();
		if (show == memberMap.isOpen()) return;
		memberMap.setOpen(show);
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
		if (sharingInClan()) chat.mapNotice();
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
