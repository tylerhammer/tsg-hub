package com.tsghub;

import static com.tsghub.TsgHubSession.DISPLAY_NAME;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.MEMBER_NAME;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubSession.ORGANIZER_EVENT_ID;
import static com.tsghub.TsgHubSession.ORGANIZER_NAME;
import static com.tsghub.TsgHubSession.ORGANIZER_TOKEN;
import static com.tsghub.TsgHubSession.TOKEN;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.tsghub.TsgHubUi.Tone;
import com.tsghub.group.GroupMembersPanel;
import com.tsghub.group.GroupTracker;
import com.tsghub.group.GroupViewSettings;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
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
	private final Set<String> localMessages = ConcurrentHashMap.newKeySet();
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
	@Inject @Named("developerMode") private boolean developerMode;
	private TsgHubSession session;
	private TsgHubPanel panel;
	private TsgHubSidebarPanel sidebar;
	private volatile JDialog hubWindow;
	private volatile JDialog newsFrame;
	private TsgHubNewsWindow newsWindow;
	private NavigationButton navigationButton;
	private ScheduledExecutorService executor;
	private ExecutorService itemSearchExecutor;
	private final Map<String, Integer> recentLootEvents = new ConcurrentHashMap<>();
	private final Map<String, Integer> syncedClanRanks = new ConcurrentHashMap<>();
	private static final int PET_CHECK_TICKS = 5;
	private static final int MAX_LOOT_LOG_ITEMS = 50;
	private static final int CLAN_CHECK_TICKS = 50;
	private volatile int clanCheckTicks;
	private int petCheckTicks;
	private int recentPet;
	private int recentPetTick;
	private String lastGroupContent = "";
	private int lastGroupContentTick = -1;
	private Set<Integer> inventoryPets = Collections.emptySet();
	private volatile String detectedClanName = "";
	private volatile String detectedPlayerName = "";
	private volatile int detectedClanRank = -1;
	private volatile boolean inClanChat;
	private volatile boolean sidebarRouted;
	private volatile String syncedIdentity = "";
	private volatile String notifiedUpdate = "";
	private volatile boolean routedAsHubMember;
	private volatile boolean routedClanPending;
	private volatile String hubClanName = "TSGaming";
	private volatile List<String> joinedCompetitions = Collections.emptyList();
	private volatile boolean adminVerified;
	private volatile String checkedKeyIdentity = "";
	private volatile String rejectedKey = "";
	private final TsgHubBackoff keyCheckBackoff = new TsgHubBackoff(30_000, 300_000);
	private volatile boolean announceKey;
	private volatile TsgHubApi api;
	private TsgHubSocket socket;
	private TsgHubSignals signals;
	private TsgHubReports reports;
	private TsgHubOrganizer organizer;
	private volatile Filepath rulesCache;
	private TsgHubGroups groups;
	private TsgHubPresence presence;
	private TsgHubMemberMap memberMap;
	private TsgHubMapOverlay mapOverlay;
	private boolean worldMapOpen;
	private boolean mapNoticeChecked;
	private TsgHubDrops drops;
	private TsgHubAnnouncements announcements;
	private TsgHubNewsEditor newsEditor;
	private TsgHubActivity activity;
	private TsgHubActivityPanel activityPanel;
	private GroupTracker groupTracker;

	@Override
	protected void startUp()
	{
		session = new TsgHubSession(configManager);
		TsgHubRules.loadBundled(gson);
		migrateAdminKey();
		String clanOverride = System.getProperty("tsghub.clanName", "").trim();
		hubClanName = developerMode && !clanOverride.isEmpty() ? clanOverride : "TSGaming";
		String serviceOverride = System.getProperty("tsghub.serviceUrl", "").trim();
		String serviceUrl = developerMode && !serviceOverride.isEmpty() ? serviceOverride : SERVICE_URL;
		api = new TsgHubApi(okHttpClient, gson, serviceUrl);
		executor = Executors.newSingleThreadScheduledExecutor(daemon("tsg-hub-api"));
		socket = new TsgHubSocket(okHttpClient, gson, serviceUrl, executor, new TsgHubSocket.Listener()
		{
			@Override public void onReady(String rulesVersion)
			{
				refreshRules(rulesVersion);
				signals.resend();
				reports.resend();
				resyncLiveViews();
			}
			@Override public void onChanged(String topic, String eventId) { liveChange(topic, eventId); }
			@Override public void onAdminRevoked(String token) { recheckHubKey(token); }
			@Override public void onAnnouncement(String text) { announce(text); }
			@Override public void onUpdateAvailable(String version) { updateAvailable(version); }
			@Override public void onSnapshot(JsonObject message) { snapshot(message); }
			@Override public void onConnection(TsgHubSocket.State state) { ui(s -> s.setConnection(state)); }
			@Override public void onAck(String id)
			{
				signals.acked(id);
				reports.acked(id);
			}
			@Override public void onRejected(String id, int status) { reports.failed(id, status); }
			@Override public void onSubscribed() { signals.resend(); }
			@Override public void onReply(JsonObject message) { signalReply(message); }
			@Override public void onXpRequest(String skills) { reportXp(skills); }
			@Override public void onGroupHeartbeat(JsonObject message) { groups.beaten(message); }
			@Override public void onGroupRejected(int status) { groups.beatRejected(status); }
		});
		signals = new TsgHubSignals(socket, this::api, this::signalReply);
		reports = new TsgHubReports(socket::send, (path, body) -> api().request("POST", path, null, body));
		executor.execute(this::loadCachedRules);
		executor.scheduleAtFixedRate(this::socketTick, 2, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(this::autoRefreshBoard), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(this::pollTeamNotifications), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		itemSearchExecutor = Executors.newFixedThreadPool(2, daemon("tsg-hub-item-search"));
		organizer = new TsgHubOrganizer(this, client, clientThread, itemManager, executor, itemSearchExecutor, () -> panel);
		announcements = new TsgHubAnnouncements(this, executor, this::api, () -> newsEditor, this::clanAnnouncement);
		panel = new TsgHubPanel(this);
		newsEditor = new TsgHubNewsEditor(this);
		TsgHubRankIcons rankIcons = new TsgHubRankIcons(chatIconManager);
		activity = new TsgHubActivity(this, client, clientThread, rankIcons, executor, this::api, () -> activityPanel);
		activityPanel = new TsgHubActivityPanel(activity, config::inactivityDays);
		GroupMembersPanel groupMembers = new GroupMembersPanel(new GroupViewSettings()
		{
			@Override public boolean autoExpandMembers() { return config.partyExpandMembers(); }
			@Override public boolean displayVirtualLevels() { return config.partyVirtualLevels(); }
			@Override public boolean displayPlayerWorlds() { return config.partyShowWorlds(); }
		}, spriteManager, itemManager);
		sidebar = new TsgHubSidebarPanel(this, groupMembers);
		groups = new TsgHubGroups(this, client, partyService, executor, this::api, () -> sidebar, socket);
		groupTracker = new GroupTracker(client, clientThread, partyService, wsClient, itemManager, groups::isGroupParty, config::partyShowSelf, this::currentArea, groups);
		groups.setTracker(groupTracker);
		eventBus.register(groupTracker);
		groupTracker.start();
		executor.scheduleAtFixedRate(groups::heartbeat, 5, TsgHubGroups.HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(groupTracker::keepAlive, GroupTracker.KEEPALIVE_SECONDS, GroupTracker.KEEPALIVE_SECONDS, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(groups::autoRefresh), TsgHubGroups.REFRESH_SECONDS, TsgHubGroups.REFRESH_SECONDS, TimeUnit.SECONDS);
		presence = new TsgHubPresence(this, client, clientThread, rankIcons, spriteManager, executor, this::api, () -> sidebar, socket, this::adminKey);
		executor.scheduleAtFixedRate(unlessLive(presence::autoRefresh), TsgHubPresence.REFRESH_SECONDS, TsgHubPresence.REFRESH_SECONDS, TimeUnit.SECONDS);
		memberMap = new TsgHubMemberMap(this, client, clientThread, worldMapPointManager, executor, this::api);
		mapOverlay = new TsgHubMapOverlay(client, worldMapOverlay, memberMap);
		overlayManager.add(mapOverlay);
		executor.scheduleAtFixedRate(unlessLive(memberMap::autoRefresh), TsgHubMemberMap.REFRESH_SECONDS, TsgHubMemberMap.REFRESH_SECONDS, TimeUnit.SECONDS);
		drops = new TsgHubDrops(this, client, clientThread, executor, this::api, () -> sidebar);
		executor.scheduleAtFixedRate(unlessLive(drops::autoRefresh), TsgHubDrops.REFRESH_SECONDS, TsgHubDrops.REFRESH_SECONDS, TimeUnit.SECONDS);
		navigationButton = NavigationButton.builder()
			.tooltip("TSG Hub")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(5)
			.panel(sidebar)
			.build();
		clientToolbar.addNavigation(navigationButton);
		sidebarRouted = false;
		clientThread.invokeLater(this::refreshDetectedClan);
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
		if (groupTracker != null)
		{
			groupTracker.stop();
			eventBus.unregister(groupTracker);
		}
		// Stay listed across a brief restart, but leave the RuneLite party.
		if (groups != null && groupTracker != null && groupTracker.isSharing()) partyService.changeParty(null);
		if (hubWindow != null) SwingUtilities.invokeLater(hubWindow::dispose);
		if (newsFrame != null) SwingUtilities.invokeLater(newsFrame::dispose);
		if (presence != null) presence.shutDown();
		if (memberMap != null) memberMap.setOpen(false);
		if (mapOverlay != null) overlayManager.remove(mapOverlay);
		if (socket != null) socket.disconnect();
		if (executor != null) executor.shutdownNow();
		if (itemSearchExecutor != null) itemSearchExecutor.shutdownNow();
		resetState();
	}

	private void resetState()
	{
		TsgHubRules.reset();
		localMessages.clear();
		recentLootEvents.clear();
		syncedClanRanks.clear();
		hubWindow = null;
		newsFrame = null;
		newsWindow = null;
		clanCheckTicks = 0;
		petCheckTicks = 0;
		recentPet = 0;
		recentPetTick = 0;
		lastGroupContent = "";
		lastGroupContentTick = -1;
		inventoryPets = Collections.emptySet();
		detectedClanName = "";
		detectedPlayerName = "";
		detectedClanRank = -1;
		inClanChat = false;
		sidebarRouted = false;
		syncedIdentity = "";
		notifiedUpdate = "";
		routedAsHubMember = false;
		routedClanPending = false;
		joinedCompetitions = Collections.emptyList();
		adminVerified = false;
		checkedKeyIdentity = "";
		rejectedKey = "";
		keyCheckBackoff.reset();
		announceKey = false;
		worldMapOpen = false;
		mapNoticeChecked = false;
	}

	void openWorkspace()
	{
		if (!canManageOrganizerUi())
		{
			memberStatus("Admin tools need a hub key with admin access. Run /hub key in the clan Discord and paste it in the plugin settings.", Tone.ERROR);
			return;
		}
		SwingUtilities.invokeLater(() -> {
			if (hubWindow == null || !hubWindow.isDisplayable())
			{
				hubWindow = window("TSG Hub · Admin", new TsgHubAdminWindow(panel, organizer::loadManagedEvents, newsEditor, announcements::load, activityPanel, activity::load), new Dimension(680, 480), new Dimension(860, 640));
			}
			hubWindow.setVisible(true);
			hubWindow.toFront();
			organizer.loadManagedEvents();
			syncSocketNow();
		});
	}

	void openNews(String id)
	{
		SwingUtilities.invokeLater(() -> {
			if (newsFrame == null || !newsFrame.isDisplayable())
			{
				newsWindow = new TsgHubNewsWindow(announcements.polls());
				newsWindow.setAnnouncements(announcements.list());
				newsFrame = window("TSG Hub · News", newsWindow, new Dimension(TsgHubNewsWindow.TEXT_W + 60, 320), new Dimension(TsgHubNewsWindow.TEXT_W + 60, 640));
			}
			newsWindow.show(id);
			newsFrame.setVisible(true);
			newsFrame.toFront();
		});
	}

	private JDialog window(String title, Container content, Dimension min, Dimension size)
	{
		Window owner = SwingUtilities.getWindowAncestor(client.getCanvas());
		JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		dialog.setContentPane(content);
		dialog.setResizable(true);
		dialog.setMinimumSize(min);
		dialog.setSize(size);
		dialog.setLocationRelativeTo(owner);
		return dialog;
	}

	void newsWindow(Consumer<TsgHubNewsWindow> action)
	{
		SwingUtilities.invokeLater(() -> { if (newsWindow != null) action.accept(newsWindow); });
	}

	@Provides
	TsgHubConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TsgHubConfig.class);
	}

	TsgHubSession session() { return session; }

	boolean live() { return socket != null && socket.isLive(); }

	String getDetectedClanName() { return detectedClanName; }
	boolean sharingEnabled() { return config.dataSharingOptIn(); }
	boolean locationSharingEnabled() { return config.dataSharingOptIn() && config.shareLocation(); }
	TsgHubGroups groups() { return groups; }
	TsgHubPresence presence() { return presence; }
	TsgHubDrops drops() { return drops; }
	TsgHubAnnouncements announcements() { return announcements; }
	SpriteManager sprites() { return spriteManager; }
	CompletableFuture<HiscoreResult> lookupHiscores(String name, int accountType) { return hiscoreClient.lookupAsync(name, TsgHubHiscores.endpoint(accountType)); }

	String currentArea() { return presence == null ? "" : presence.area(); }
	boolean inClanChat() { return inClanChat; }
	String getDetectedPlayerName() { return detectedPlayerName; }
	int getDetectedClanRank() { return detectedClanRank; }
	AsyncBufferedImage getItemImage(int itemId) { return itemManager == null ? null : itemManager.getImage(itemId); }

	AsyncBufferedImage getCoinImage(long gp) { return itemManager == null ? null : itemManager.getImage(ItemID.COINS, (int) Math.min(gp, Integer.MAX_VALUE), false); }

	String getOrganizerEventId()
	{
		return session.get(ORGANIZER_EVENT_ID);
	}

	String getHubClanName() { return hubClanName; }

	private boolean clanPending()
	{
		return detectedClanName.isEmpty() && clanCheckTicks > 0;
	}

	boolean isInHubClan()
	{
		return PlayerNames.samePlayer(detectedClanName, hubClanName);
	}

	private boolean sharingInClan()
	{
		return isInHubClan() && config.dataSharingOptIn();
	}

	boolean canManageOrganizerUi()
	{
		return isInHubClan() && adminVerified;
	}

	private String configuredKey()
	{
		String key = config.hubKey();
		return key == null ? "" : key.trim();
	}

	String adminKey()
	{
		return adminVerified ? configuredKey() : "";
	}

	private void migrateAdminKey()
	{
		String legacy = configManager.getConfiguration("tsghub", "adminKey");
		if (legacy == null) return;
		if (!legacy.trim().isEmpty() && configuredKey().isEmpty()) configManager.setConfiguration("tsghub", "hubKey", legacy.trim());
		configManager.unsetConfiguration("tsghub", "adminKey");
	}

	private void checkHubKey()
	{
		String key = configuredKey();
		String name = detectedPlayerName;
		if (key.isEmpty())
		{
			checkedKeyIdentity = "";
			if (adminVerified) setAdminVerified(false);
			return;
		}
		if (name.isEmpty() || !sharingInClan()) return;
		String hash = accountHash();
		String identity = key + "\n" + name + "\n" + hash;
		if (identity.equals(checkedKeyIdentity) || key.equals(rejectedKey) || executor == null || executor.isShutdown()) return;
		checkedKeyIdentity = identity;
		String query = "/v1/me?displayName=" + TsgHubApi.encode(name) + (hash.isEmpty() ? "" : "&accountHash=" + hash);
		api().call("GET", query, key, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				if (!identity.equals(checkedKeyIdentity)) return;
				keyCheckBackoff.reset();
				boolean admin = grantsAdmin(result);
				if (admin != adminVerified) setAdminVerified(admin);
				if (admin) loadClanEvents();
				String linkError = TsgHubJson.str(result, "linkError");
				boolean linked = TsgHubJson.bool(result, "linked");
				if (!linkError.isEmpty()) memberStatus(linkError, Tone.ERROR);
				else if (announceKey && linked) memberStatus(admin ? "Hub key accepted. Your Discord account is linked and admin tools are unlocked." : "Hub key accepted. Your Discord account is linked.", Tone.SUCCESS);
				if (linked || !linkError.isEmpty()) announceKey = false;
				if (!linked && hash.isEmpty()) retryKeyCheck(identity);
			}
			catch (TsgHubApi.HttpError e)
			{
				if (e.status == 401) keyRejected(key);
				else retryKeyCheck(identity);
			}
			catch (Exception e) { retryKeyCheck(identity); }
		}, executor);
	}

	static boolean grantsAdmin(JsonObject me)
	{
		String role = TsgHubJson.str(me, "role");
		return role.isEmpty() || "admin".equals(role);
	}

	private void retryKeyCheck(String identity)
	{
		if (!identity.equals(checkedKeyIdentity)) return;
		checkedKeyIdentity = "";
		if (executor != null && !executor.isShutdown()) executor.schedule(this::checkHubKey, keyCheckBackoff.next(), TimeUnit.MILLISECONDS);
	}

	private void recheckHubKey(String key)
	{
		if (!key.equals(configuredKey())) return;
		checkedKeyIdentity = "";
		if (adminVerified) setAdminVerified(false);
		checkHubKey();
	}

	void keyRejected(String key)
	{
		if (!key.equals(configuredKey())) return;
		rejectedKey = key;
		announceKey = false;
		setAdminVerified(false);
		memberStatus("Your hub key wasn't accepted. It may have expired or been revoked. Run /hub key in the clan Discord and paste the new key in the plugin settings.", Tone.ERROR);
	}

	private void setAdminVerified(boolean verified)
	{
		adminVerified = verified;
		syncSocketNow();
		if (presence != null) presence.autoRefresh();
		boolean access = canManageOrganizerUi();
		SwingUtilities.invokeLater(() -> {
			if (sidebar != null) sidebar.setOrganizerAccess(access);
			if (!access && hubWindow != null) hubWindow.setVisible(false);
		});
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
				if (detectedClanName.isEmpty()) clanCheckTicks = CLAN_CHECK_TICKS;
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
		mapNoticeChecked = false;
		if (announcements != null) announcements.onLoggedOut();
		detectedPlayerName = "";
		detectedClanName = "";
		detectedClanRank = -1;
		inClanChat = false;
		clanCheckTicks = 0;
		sidebarRouted = false;
		adminVerified = false;
		checkedKeyIdentity = "";
		syncSocketNow();
		if (signals != null) signals.clear();
		joinedCompetitions = Collections.emptyList();
		SwingUtilities.invokeLater(() -> {
			if (panel != null) panel.setDetectedClanName("", -1);
			if (sidebar != null)
			{
				sidebar.setOrganizerAccess(false);
				sidebar.showLoggedOut();
			}
			if (hubWindow != null) hubWindow.setVisible(false);
			if (newsFrame != null) newsFrame.setVisible(false);
		});
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		String previousClan = detectedClanName;
		refreshDetectedClan();
		if (config.dataSharingOptIn() && sidebarRouted && !detectedClanName.equals(previousClan)) loadClanEvents();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"tsghub".equals(event.getGroup())) return;
		if ("dataSharingOptIn".equals(event.getKey()) && !config.dataSharingOptIn() && reports != null) reports.clear();
		if ("partyShowSelf".equals(event.getKey()))
		{
			if (config.partyShowSelf()) groupTracker.refreshSelf();
			else ui(s -> s.groupSelfHidden());
			return;
		}
		if (event.getKey().startsWith("party"))
		{
			boolean expand = "partyExpandMembers".equals(event.getKey());
			ui(s -> s.groupSettingsChanged(expand));
			return;
		}
		if ("showClanMap".equals(event.getKey()))
		{
			clientThread.invokeLater(this::syncMemberMap);
			return;
		}
		if ("shareLocation".equals(event.getKey()))
		{
			ui(s -> s.locationSharingChanged());
			return;
		}
		if ("hubKey".equals(event.getKey()))
		{
			checkedKeyIdentity = "";
			rejectedKey = "";
			keyCheckBackoff.reset();
			announceKey = !configuredKey().isEmpty();
			if (adminVerified) setAdminVerified(false);
			checkHubKey();
			return;
		}
		if (!"dataSharingOptIn".equals(event.getKey())) return;
		if (!config.dataSharingOptIn())
		{
			if (groups != null) groups.onSharingDisabled();
			if (presence != null) presence.leave();
			clearAllSessions();
			SwingUtilities.invokeLater(() -> {
				if (hubWindow != null) hubWindow.setVisible(false);
				if (newsFrame != null) newsFrame.setVisible(false);
			});
		}
		syncSocketNow();
		sidebarRouted = false;
		routeSidebar();
	}

	void enableSharing()
	{
		configManager.setConfiguration("tsghub", "dataSharingOptIn", true);
	}

	private void refreshDetectedClan()
	{
		ClanSettings settings = client.getClanSettings();
		detectedPlayerName = client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null ? "" : client.getLocalPlayer().getName();
		String name = settings == null ? "" : settings.getName();
		detectedClanName = name == null ? "" : name.trim();
		if (!detectedClanName.isEmpty()) clanCheckTicks = 0;
		int rank = -1;
		if (settings != null && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			ClanMember member = settings.findMember(client.getLocalPlayer().getName());
			if (member != null && member.getRank() != null) rank = member.getRank().getRank();
		}
		detectedClanRank = rank;
		ClanChannel channel = client.getClanChannel();
		inClanChat = channel != null && channel.getName() != null && channel.getName().trim().equalsIgnoreCase(detectedClanName);
		boolean organizerAccess = canManageOrganizerUi();
		syncSocketNow();
		SwingUtilities.invokeLater(() -> {
			if (panel != null) panel.setDetectedClanName(detectedClanName, detectedClanRank);
			if (sidebar != null) sidebar.setOrganizerAccess(organizerAccess);
			if (!organizerAccess && hubWindow != null) hubWindow.setVisible(false);
		});
		if (!sidebarRouted && !detectedPlayerName.isEmpty()) routeSidebar();
		else if (sidebarRouted && (routedAsHubMember != isInHubClan() || routedClanPending != clanPending())) routeSidebar();
		else if (client.getGameState() != GameState.LOGGED_IN && !sidebarRouted) ui(s -> s.showLoggedOut());
		checkHubKey();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		syncedClanRanks.clear();
		syncedIdentity = "";
		if (signals != null) signals.clear();
		joinedCompetitions = Collections.emptyList();
		syncSocketNow();
		if (detectedPlayerName.isEmpty()) return;
		sidebarRouted = false;
		routeSidebar();
	}

	private void routeSidebar()
	{
		if (sidebar == null) return;
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && !detectedPlayerName.isEmpty();
		if (loggedIn) sidebarRouted = true;
		routedAsHubMember = isInHubClan();
		boolean pending = clanPending();
		routedClanPending = pending;
		if (loggedIn && !pending && sharingInClan())
		{
			syncIdentity();
			checkHubKey();
		}
		SwingUtilities.invokeLater(() -> {
			if (!loggedIn) { sidebar.showLoggedOut(); return; }
			if (pending) { sidebar.showCheckingClan(); return; }
			if (!isInHubClan()) { sidebar.showNotInClan(hubClanName, detectedClanName); return; }
			if (!config.dataSharingOptIn()) { sidebar.showSharingOff(); return; }
			sidebar.showHome();
			if (!live())
			{
				loadClanEvents();
				announcements.load();
			}
			String eventId = session.get(EVENT_ID);
			if (!eventId.isEmpty() && !session.get(TOKEN).isEmpty() && PlayerNames.samePlayer(session.memberName(eventId), detectedPlayerName))
				activateEvent(eventId, false);
		});
	}

	void join(String code, String expectedEventId)
	{
		if (!isInHubClan()) { joinFailed("TSG Hub is only for members of the " + hubClanName + " clan."); return; }
		if (!config.dataSharingOptIn()) { joinFailed("Turn on sharing first."); return; }
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
		{
			joinFailed("Log in to RuneScape before joining.");
			return;
		}
		if (detectedClanName.isEmpty())
		{
			joinFailed("No clan detected. Log in to a character in your clan first.");
			return;
		}
		JsonObject payload = memberBody(client.getLocalPlayer().getName());
		payload.addProperty("code", code.trim());
		if (expectedEventId != null && !expectedEventId.trim().isEmpty()) payload.addProperty("eventId", expectedEventId.trim());
		api().call("POST", "/v1/join", adminKey(), payload).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				String joinedEventId = result.getAsJsonObject("member").get("eventId").getAsString();
				String memberToken = result.get("token").getAsString();
				String memberName = result.getAsJsonObject("member").get("displayName").getAsString();
				session.set(MEMBER_TOKEN + joinedEventId, memberToken);
				session.set(MEMBER_NAME + joinedEventId, memberName);
				setActiveEvent(joinedEventId, memberToken, memberName);
				memberStatus("You're in! Progress now counts for your team.", Tone.SUCCESS);
				refreshBoard(true);
			}
			catch (Exception e) { joinFailed(TsgHubFormat.friendlyError(e)); }
		}, executor);
	}

	private void joinFailed(String message)
	{
		ui(s -> s.joinFailed(message));
	}

	void leaveEvent(String eventId)
	{
		String revokeToken = session.memberToken(eventId);
		if (!revokeToken.isEmpty()) disconnect(eventId, revokeToken);
		session.clear(MEMBER_TOKEN + eventId, MEMBER_NAME + eventId);
		if (eventId.equals(session.get(EVENT_ID)))
		{
			session.clear(TOKEN, EVENT_ID);
			}
		memberStatus("Disconnected. Rejoin anytime with your team code.", Tone.SUCCESS);
		ui(s -> s.showEventList());
		loadClanEvents();
	}

	private void clearAllSessions()
	{
		if (signals != null) signals.clear();
		joinedCompetitions = Collections.emptyList();
		revokeRemoteSession();
		session.clear(TOKEN, EVENT_ID, ORGANIZER_EVENT_ID);
		session.removePrefix(MEMBER_TOKEN);
		session.removePrefix(MEMBER_NAME);
		session.removePrefix(ORGANIZER_TOKEN);
		session.removePrefix(ORGANIZER_NAME);
		syncedClanRanks.clear();
		checkedKeyIdentity = "";
		if (adminVerified) setAdminVerified(false);
	}

	private void revokeRemoteSession()
	{
		if (executor == null || executor.isShutdown()) return;
		Map<String, String> sessions = new HashMap<>();
		for (String key : session.keysWithPrefix(MEMBER_TOKEN))
		{
			String eventId = key.substring(MEMBER_TOKEN.length());
			sessions.put(eventId, session.get(key));
		}
		String activeEventId = session.get(EVENT_ID);
		String activeToken = session.get(TOKEN);
		if (!activeEventId.isEmpty() && !activeToken.isEmpty()) sessions.putIfAbsent(activeEventId, activeToken);
		sessions.forEach(this::disconnect);
	}

	private void disconnect(String eventId, String token)
	{
		// Local disconnect still succeeds while the service is unavailable.
		api().call("POST", "/v1/events/" + eventId + "/disconnect", token, new JsonObject());
	}

	void refreshBoard()
	{
		refreshBoard(false);
	}

	private void refreshBoard(boolean open)
	{
		if (!sharingInClan()) return;
		String eventId = session.get(EVENT_ID);
		String token = session.get(TOKEN);
		if (eventId.isEmpty() || token.isEmpty()) return;
		sidebarBusy(true);
		api().call("GET", "/v1/events/" + eventId, token, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject event = TsgHubApi.result(response, error).getAsJsonObject("event");
				applyBoard(eventId, token, event, open);
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't update the board. "); }
			finally { sidebarBusy(false); }
		}, executor);
	}

	private void applyBoard(String eventId, String token, JsonObject event, boolean open)
	{
		syncClanRank(eventId, token, event);
		ui(s -> s.showBoard(event, session.get(DISPLAY_NAME), open));
	}

	private void eventLoadFailed(String eventId, Exception e, String failure)
	{
		String message = e.getMessage() == null ? "" : e.getMessage();
		if (message.startsWith("Event not found") || message.startsWith("Connect to this event first"))
		{
			// Event or team was deleted: forget it instead of failing every refresh.
			forgetEvent(eventId);
			memberStatus("That event is no longer available.", Tone.ERROR);
			loadClanEvents();
		}
		else memberError(failure, e);
	}

	private Runnable unlessLive(Runnable poll)
	{
		return () -> {
			if (!socket.isLive()) poll.run();
		};
	}

	void syncSocketNow()
	{
		ScheduledExecutorService e = executor;
		if (socket == null || e == null || e.isShutdown()) return;
		e.execute(this::socketTick);
	}

	private void socketTick()
	{
		if (!sharingEnabled()) reports.clear();
		reports.flush();
		if (!sharingInClan())
		{
			socket.disconnect();
			return;
		}
		socket.connect(detectedClanName);
		Map<String, String> tokens = new HashMap<>();
		String eventId = session.get(EVENT_ID);
		String token = session.get(TOKEN);
		if (!eventId.isEmpty() && !token.isEmpty()) tokens.put(eventId, token);
		String competitionId = sidebar == null ? null : sidebar.openCompetitionId();
		String memberToken = competitionId == null ? "" : session.get(MEMBER_TOKEN + competitionId);
		if (!memberToken.isEmpty()) tokens.putIfAbsent(competitionId, memberToken);
		for (String joinedId : joinedCompetitions)
		{
			String joinedToken = session.get(MEMBER_TOKEN + joinedId);
			if (!joinedToken.isEmpty()) tokens.putIfAbsent(joinedId, joinedToken);
		}
		Map<String, String> memberTokens = new HashMap<>(tokens);
		String organizerEventId = adminWindowOpen() ? getOrganizerEventId() : "";
		String organizerToken = organizerEventId.isEmpty() ? "" : organizer.credential(organizerEventId);
		if (!organizerToken.isEmpty()) tokens.putIfAbsent(organizerEventId, organizerToken);
		if (organizer.refreshPending() && adminWindowOpen()) organizer.liveRefresh();
		socket.sync(tokens, inClanChat);
		socket.admin(adminKey());
		socket.watch(watchedTopics(competitionId));
		signals.flush(memberTokens);
	}

	private Set<String> watchedTopics(String competitionId)
	{
		Set<String> topics = new HashSet<>();
		topics.add("events");
		topics.add("announcements");
		topics.add("notifications");
		if (sidebar == null) return topics;
		if (memberMap != null && memberMap.isOpen()) topics.add("positions");
		if (sidebar.wantsGroups()) topics.add("groups");
		if (sidebar.wantsMembers()) topics.add("presence");
		if (sidebar.wantsDrops()) topics.add("drops");
		String eventId = session.get(EVENT_ID);
		if (sidebar.wantsAutoRefresh() && !eventId.isEmpty()) topics.add("event:" + eventId);
		if (competitionId != null) topics.add("event:" + competitionId);
		return topics;
	}

	private void snapshot(JsonObject message)
	{
		String eventId = TsgHubJson.str(message, "eventId");
		JsonObject data = message.has("data") && message.get("data").isJsonObject() ? message.getAsJsonObject("data") : new JsonObject();
		switch (TsgHubJson.str(message, "topic"))
		{
			case "groups":
				groups.apply(TsgHubJson.array(data, "groups"));
				break;
			case "presence":
				JsonObject roster = data.has("presence") && data.get("presence").isJsonObject() ? data.getAsJsonObject("presence") : null;
				JsonObject notes = data.has("members") && data.get("members").isJsonObject() ? data.getAsJsonObject("members") : null;
				if (roster != null) presence.apply(TsgHubJson.array(roster, "members"), notes == null ? null : TsgHubJson.array(notes, "members"));
				break;
			case "positions":
				memberMap.apply(TsgHubJson.array(data, "members"));
				break;
			case "drops":
				drops.onSnapshot(TsgHubJson.array(data, "drops"));
				break;
			case "announcements":
				announcements.apply(TsgHubJson.array(data, "announcements"));
				break;
			case "events":
				applyClanEvents(TsgHubJson.array(data, "events"));
				if (adminWindowOpen()) organizer.loadManagedEvents();
				break;
			case "notifications":
				if (eventId.equals(session.get(EVENT_ID))) showNotifications(TsgHubJson.array(data, "notifications"));
				break;
			case "event":
				if (!data.has("event") || !data.get("event").isJsonObject()) break;
				JsonObject event = data.getAsJsonObject("event");
				if (adminWindowOpen() && eventId.equals(getOrganizerEventId())) organizer.liveRefresh();
				if (sidebar == null) break;
				if (eventId.equals(session.get(EVENT_ID)) && sidebar.wantsAutoRefresh()) applyBoard(eventId, session.get(TOKEN), event, false);
				if (eventId.equals(sidebar.openCompetitionId()))
				{
					String name = session.get(MEMBER_NAME + eventId);
					ui(s -> s.showCompetition(event, name, false));
				}
				break;
			default:
		}
	}

	private void resyncLiveViews()
	{
		if (adminWindowOpen())
		{
			organizer.loadManagedEvents();
			organizer.liveRefresh();
		}
	}

	private boolean adminWindowOpen()
	{
		JDialog window = hubWindow;
		return window != null && window.isVisible();
	}

	private void liveChange(String topic, String eventId)
	{
		switch (topic)
		{
			case "groups":
				groups.autoRefresh();
				break;
			case "presence":
				presence.autoRefresh();
				break;
			case "drops":
				drops.autoRefresh();
				break;
			case "announcements":
				announcements.load();
				break;
			case "notifications":
				if (eventId.equals(session.get(EVENT_ID))) pollTeamNotifications();
				break;
			case "event":
				if (adminWindowOpen() && eventId.equals(getOrganizerEventId())) organizer.liveRefresh();
				if (sidebar == null) break;
				if (eventId.equals(session.get(EVENT_ID)) && sidebar.wantsAutoRefresh()) refreshBoard();
				if (eventId.equals(sidebar.openCompetitionId())) openCompetition(eventId);
				break;
			case "events":
				loadClanEvents();
				if (adminWindowOpen()) organizer.loadManagedEvents();
				break;
			default:
		}
	}

	private void autoRefreshBoard()
	{
		if (sidebar == null) return;
		if (sidebar.wantsAutoRefresh()) refreshBoard();
		String competitionId = sidebar.openCompetitionId();
		if (competitionId != null) openCompetition(competitionId);
	}

	void participate(String eventId)
	{
		if (!sharingInClan() || detectedPlayerName.isEmpty()) return;
		JsonObject body = memberBody(detectedPlayerName);
		sidebarBusy(true);
		api().call("POST", "/v1/events/" + eventId + "/participate", adminKey(), body).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				session.set(MEMBER_TOKEN + eventId, result.get("token").getAsString());
				session.set(MEMBER_NAME + eventId, result.getAsJsonObject("member").get("displayName").getAsString());
				syncSocketNow();
				memberStatus("You're in! Your progress counts from now.", Tone.SUCCESS);
				loadClanEvents();
				openCompetition(eventId, true);
			}
			catch (Exception e) { ui(s -> s.competitionJoinFailed(TsgHubFormat.friendlyError(e))); }
			finally { sidebarBusy(false); }
		}, executor);
	}

	void openCompetition(String eventId)
	{
		openCompetition(eventId, false);
	}

	void openCompetition(String eventId, boolean open)
	{
		String token = session.get(MEMBER_TOKEN + eventId);
		if (token.isEmpty()) return;
		String name = session.get(MEMBER_NAME + eventId);
		sidebarBusy(true);
		api().call("GET", "/v1/events/" + eventId, token, null).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject event = TsgHubApi.result(response, error).getAsJsonObject("event");
				ui(s -> s.showCompetition(event, name, open));
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't load the leaderboard. "); }
			finally { sidebarBusy(false); }
		}, executor);
	}

	void openSettings()
	{
		Overlay owner = new Overlay(this)
		{
			@Override public Dimension render(Graphics2D graphics) { return null; }
		};
		eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "", ""), owner));
	}

	void copyDiscordInvite(Runnable done)
	{
		JsonObject body = new JsonObject();
		body.addProperty("displayName", detectedPlayerName);
		body.addProperty("clanName", detectedClanName);
		addAccountHash(body);
		api().call("POST", "/v1/discord/invite", null, body).whenCompleteAsync((response, error) -> {
			try
			{
				String url = TsgHubApi.result(response, error).get("url").getAsString();
				SwingUtilities.invokeLater(() -> TsgHubUi.copyToClipboard(url));
				memberStatus("Discord invite copied. Paste it in your browser to join.", Tone.SUCCESS);
			}
			catch (Exception e) { memberError("", e); }
			finally { SwingUtilities.invokeLater(done); }
		}, executor);
	}

	String accountHash()
	{
		long hash = client.getAccountHash();
		return hash == -1 ? "" : Long.toString(hash);
	}

	private JsonObject memberBody(String displayName)
	{
		JsonObject body = new JsonObject();
		body.addProperty("displayName", displayName);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("clanRank", detectedClanRank);
		addAccountHash(body);
		return body;
	}

	void addAccountHash(JsonObject body)
	{
		String hash = accountHash();
		if (!hash.isEmpty()) body.addProperty("accountHash", hash);
	}

	private void syncIdentity()
	{
		String name = detectedPlayerName;
		String hash = accountHash();
		if (name.isEmpty() || hash.isEmpty() || detectedClanName.isEmpty()) return;
		String identity = hash + ":" + name;
		if (identity.equals(syncedIdentity)) return;
		syncedIdentity = identity;
		JsonArray memberTokens = new JsonArray();
		JsonArray organizerTokens = new JsonArray();
		for (String key : session.keysWithPrefix(MEMBER_TOKEN)) memberTokens.add(session.get(key));
		if (!session.get(TOKEN).isEmpty()) memberTokens.add(session.get(TOKEN));
		for (String key : session.keysWithPrefix(ORGANIZER_TOKEN)) organizerTokens.add(session.get(key));
		for (String key : session.keysWithPrefix(MEMBER_NAME)) renameStored(key, name);
		for (String key : session.keysWithPrefix(ORGANIZER_NAME)) renameStored(key, name);
		renameStored("displayName", name);
		JsonObject body = new JsonObject();
		body.addProperty("displayName", name);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("accountHash", hash);
		body.add("memberTokens", memberTokens);
		body.add("organizerTokens", organizerTokens);
		api().call("POST", "/v1/identity", null, body).whenCompleteAsync((response, error) -> {
			try
			{
				JsonObject result = TsgHubApi.result(response, error);
				if (TsgHubJson.bool(result, "updated") && !session.get(EVENT_ID).isEmpty()) refreshBoard();
			}
			catch (Exception e) { syncedIdentity = ""; }
		}, executor);
	}

	private void renameStored(String key, String name)
	{
		String stored = session.get(key);
		if (!stored.isEmpty() && !PlayerNames.samePlayer(stored, name)) session.set(key, name);
	}

	void loadClanEvents()
	{
		if (!sharingInClan()) return;
		String clanName = detectedClanName;
		if (clanName == null || clanName.trim().isEmpty())
		{
			ui(s -> s.setEvents(new JsonArray()));
			return;
		}
		String encodedClan = TsgHubApi.encode(clanName);
		sidebarBusy(true);
		api().call("GET", "/v1/events?clanName=" + encodedClan, adminKey(), null).whenCompleteAsync((response, error) -> {
			try
			{
				applyClanEvents(TsgHubApi.result(response, error).getAsJsonArray("events"));
			}
			catch (Exception e) { memberError("Couldn't load events. ", e); }
			finally { sidebarBusy(false); }
		}, executor);
	}

	private void applyClanEvents(JsonArray events)
	{
		if (events == null) return;
		for (JsonObject event : TsgHubJson.objects(events))
		{
			String eventId = event.get("id").getAsString();
			String savedToken = session.get(MEMBER_TOKEN + eventId);
			if (savedToken.isEmpty() && eventId.equals(session.get(EVENT_ID)))
			{
				savedToken = session.get(TOKEN);
				if (!savedToken.isEmpty())
				{
					session.set(MEMBER_TOKEN + eventId, savedToken);
					session.set(MEMBER_NAME + eventId, session.get(DISPLAY_NAME));
				}
			}
			event.addProperty("joined", !savedToken.isEmpty());
		}
		joinedCompetitions = joinedCompetitionIds(events);
		ui(s -> s.setEvents(events));
	}

	void activateEvent(String eventId)
	{
		activateEvent(eventId, true);
	}

	private void activateEvent(String eventId, boolean open)
	{
		String token = session.memberToken(eventId);
		if (token.isEmpty())
		{
			memberStatus("Join this event with your team code first.", Tone.ERROR);
			return;
		}
		setActiveEvent(eventId, token, session.memberName(eventId));
		refreshBoard(open);
	}

	private void setActiveEvent(String eventId, String token, String displayName)
	{
		session.set(TOKEN, token);
		session.set(EVENT_ID, eventId);
		session.set(DISPLAY_NAME, displayName);
		syncSocketNow();
	}

	private void syncClanRank(String eventId, String token, JsonObject event)
	{
		String displayName = detectedPlayerName;
		String memberName = session.get(DISPLAY_NAME);
		String eventClan = TsgHubJson.str(event, "clanName");
		if (token.isEmpty() || displayName.isEmpty() || !PlayerNames.samePlayer(displayName, memberName)
			|| eventClan.isEmpty() || !eventClan.equalsIgnoreCase(detectedClanName)) return;
		int rank = detectedClanRank;
		String key = eventId + ":" + PlayerNames.key(displayName);
		Integer synced = syncedClanRanks.get(key);
		if (synced != null && synced == rank) return;
		JsonObject body = new JsonObject();
		body.addProperty("displayName", displayName);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("clanRank", rank);
		api().call("POST", "/v1/events/" + eventId + "/clan-rank", token, body).thenRun(() -> syncedClanRanks.put(key, rank));
	}

	void forgetEvent(String eventId)
	{
		session.clear(ORGANIZER_TOKEN + eventId, ORGANIZER_NAME + eventId, MEMBER_TOKEN + eventId, MEMBER_NAME + eventId);
		if (eventId.equals(session.get(ORGANIZER_EVENT_ID))) session.clear(ORGANIZER_EVENT_ID);
		if (eventId.equals(session.get(EVENT_ID)))
		{
			session.clear(TOKEN, EVENT_ID);
			ui(s -> s.closeBoard());
		}
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
		if (!sharingInClan() || signals == null || client.getLocalPlayer() == null) return;
		signals.xp(event.getSkill().name(), event.getXp(), client.getTickCount());
	}

	private void refreshRules(String version)
	{
		if (version.isEmpty() || version.equals(TsgHubRules.get().version)) return;
		api().call("GET", "/v1/rules", null, null).thenAcceptAsync(rules -> TsgHubRules.save(rules, rulesCache), executor);
	}

	private void loadCachedRules()
	{
		try { rulesCache = getPluginDirectory().join("rules.json"); }
		catch (Exception e) { return; }
		TsgHubRules.loadCached(rulesCache, gson);
	}

	private void reportXp(String skills)
	{
		if (!sharingInClan()) return;
		clientThread.invokeLater(() -> {
			if (client.getLocalPlayer() == null) return;
			for (String name : skills.split(","))
			{
				Skill skill;
				try { skill = Skill.valueOf(name.trim()); }
				catch (IllegalArgumentException e) { continue; }
				signals.xp(skill.name(), client.getSkillExperience(skill), client.getTickCount());
			}
		});
	}

	private void syncMemberMap()
	{
		if (memberMap == null) return;
		boolean show = worldMapOpen && config.showClanMap();
		if (show == memberMap.isOpen()) return;
		memberMap.setOpen(show);
		syncSocketNow();
		if (show && !socket.isLive()) memberMap.autoRefresh();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WORLDMAP)
		{
			worldMapOpen = true;
			syncMemberMap();
		}
		if (sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(event.getGroupId())) socket.rewardInterface(event.getGroupId(), true, client.getTickCount());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.WORLDMAP)
		{
			worldMapOpen = false;
			syncMemberMap();
		}
		if (sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(event.getGroupId())) socket.rewardInterface(event.getGroupId(), false, client.getTickCount());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!isInHubClan()) return;
		if (event.getComposition() == null) return;
		String name = Text.removeTags(event.getComposition().getName());
		if (drops != null) drops.onLoot(name, itemNames(event.getItems()));
		processLoot("NPC", name, event.getItems(), 1);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isInHubClan()) return;
		if (event.getType() == null || "PLAYER".equals(event.getType().name()) || "NPC".equals(event.getType().name())) return;
		if (drops != null) drops.onLoot(event.getName(), itemNames(event.getItems()));
		processLoot(event.getType().name(), event.getName(), event.getItems(), event.getAmount());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event == null || event.getMessage() == null) return;
		if (event.getType() == ChatMessageType.CLAN_MESSAGE && localMessages.remove(Text.removeTags(event.getMessage()))) return;
		if (!isInHubClan() || client.getLocalPlayer() == null) return;
		if (drops != null) drops.onChatMessage(event);
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
		if (!sharingInClan()) return;
		String message = Text.removeTags(event.getMessage()).toLowerCase(Locale.ROOT);
		if (isPetMessage(message))
		{
			if (recentPet > 0 && client.getTickCount() - recentPetTick <= 2) sendPet(recentPet);
			else petCheckTicks = PET_CHECK_TICKS;
			recentPet = 0;
		}
		if (TsgHubRules.get().groupContent.stream().anyMatch(message::contains)) reportGroupContent(Text.removeTags(event.getMessage()).trim(), message);
		if (!signals.wantsChat(message)) return;
		JsonObject chat = new JsonObject();
		chat.addProperty("text", message);
		if (signals.wantsPlayers(message)) addNearbyPlayers(chat);
		signals.send("chat", chat);
	}

	private void reportGroupContent(String text, String key)
	{
		int tick = client.getTickCount();
		if (tick == lastGroupContentTick && key.equals(lastGroupContent)) return;
		lastGroupContentTick = tick;
		lastGroupContent = key;
		long hash = client.getAccountHash();
		Player self = client.getLocalPlayer();
		WorldView worldView = client.getTopLevelWorldView();
		if (hash == -1 || self == null || worldView == null || worldView.players() == null || executor == null || executor.isShutdown()) return;
		List<String> nearby = new ArrayList<>();
		for (Player player : worldView.players())
		{
			if (player != null && player != self && player.getName() != null && player.isClanMember()) nearby.add(Text.toJagexName(player.getName()));
		}
		List<String> clanmates = clanmates(nearby, self.getName());
		if (clanmates.isEmpty()) return;
		JsonObject body = new JsonObject();
		body.addProperty("clanName", detectedClanName);
		body.addProperty("displayName", detectedPlayerName.isEmpty() ? Text.toJagexName(self.getName()) : detectedPlayerName);
		body.addProperty("accountHash", Long.toString(hash));
		body.addProperty("message", text);
		JsonArray names = new JsonArray();
		clanmates.forEach(names::add);
		body.add("clanmates", names);
		report("group", "/v1/group-content", body);
	}

	void report(String type, String path, JsonObject body)
	{
		if (reports == null || reports.send(type, path, body) || executor == null || executor.isShutdown()) return;
		if (client.isClientThread()) executor.execute(reports::flush);
		else reports.flush();
	}

	static List<String> clanmates(List<String> names, String self)
	{
		List<String> out = new ArrayList<>();
		for (String name : names)
		{
			if (name == null || name.trim().isEmpty() || PlayerNames.samePlayer(name, self)) continue;
			if (out.stream().noneMatch(existing -> PlayerNames.samePlayer(existing, name))) out.add(name);
		}
		return out;
	}

	private void addNearbyPlayers(JsonObject chat)
	{
		List<String> visiblePlayers = new ArrayList<>();
		List<String> nonClanPlayers = new ArrayList<>();
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView != null && worldView.players() != null)
		{
			for (Player player : worldView.players())
			{
				if (player == null || player.getName() == null) continue;
				String name = player.getName();
				if (visiblePlayers.stream().noneMatch(existing -> PlayerNames.samePlayer(existing, name))) visiblePlayers.add(name);
				if (!player.isClanMember() && nonClanPlayers.stream().noneMatch(existing -> PlayerNames.samePlayer(existing, name))) nonClanPlayers.add(name);
			}
		}
		JsonArray players = new JsonArray();
		visiblePlayers.forEach(players::add);
		chat.add("players", players);
		JsonArray outsiders = new JsonArray();
		nonClanPlayers.forEach(outsiders::add);
		chat.add("nonClanPlayers", outsiders);
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
		if (!mapNoticeChecked && sharingInClan()) showMapNotice();
		if (clanCheckTicks > 0)
		{
			clanCheckTicks--;
			if (client.getClanSettings() != null) clanCheckTicks = 0;
			if (clanCheckTicks == 0) refreshDetectedClan();
		}
		if (!sharingInClan())
		{
			petCheckTicks = 0;
			return;
		}
		if (petCheckTicks <= 0) return;
		petCheckTicks--;
		int petId = followerPetId();
		if (petId > 0) sendPet(petId);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || !sharingInClan()) return;
		Set<Integer> pets = inventoryPetIds();
		int added = 0;
		for (int itemId : pets) if (!inventoryPets.contains(itemId)) added = itemId;
		inventoryPets = pets;
		if (added <= 0) return;
		if (petCheckTicks > 0) sendPet(added);
		else
		{
			recentPet = added;
			recentPetTick = client.getTickCount();
		}
	}

	private void sendPet(int petId)
	{
		petCheckTicks = 0;
		recentPet = 0;
		JsonObject pet = new JsonObject();
		pet.addProperty("itemId", petId);
		pet.addProperty("name", itemManager.getItemComposition(petId).getName());
		signals.send("pet", pet);
	}

	private static boolean isPetMessage(String message)
	{
		for (String pattern : TsgHubRules.get().petMessages) if (message.contains(pattern)) return true;
		return false;
	}

	private Set<Integer> inventoryPetIds()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null) return Collections.emptySet();
		Set<Integer> pets = new HashSet<>();
		for (Item item : inventory.getItems())
		{
			int itemId = itemManager.canonicalize(item.getId());
			if (TsgHubRules.get().pets.contains(itemId)) pets.add(itemId);
		}
		return pets;
	}

	private int followerPetId()
	{
		NPC follower = client.getFollower();
		String followerName = follower == null ? null : petName(follower.getName());
		if (followerName == null || followerName.isEmpty()) return -1;
		for (int itemId : TsgHubRules.get().pets)
		{
			if (followerName.equals(petName(itemManager.getItemComposition(itemId).getName()))) return itemId;
		}
		return -1;
	}

	private static String petName(String name)
	{
		if (name == null) return null;
		String normalized = Text.removeTags(name).trim().toLowerCase(Locale.ROOT);
		return normalized.startsWith("pet ") ? normalized.substring(4) : normalized;
	}

	private List<String> itemNames(Collection<ItemStack> items)
	{
		List<String> names = new ArrayList<>();
		if (items == null) return names;
		for (ItemStack item : items)
		{
			String name = client.getItemDefinition(itemManager.canonicalize(item.getId())).getName();
			if (name != null) names.add(name);
		}
		return names;
	}

	private void processLoot(String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
		if (!sharingInClan() || client.getLocalPlayer() == null
			|| sourceName == null || items == null || isDuplicateLootEvent(sourceType, sourceName, items)) return;
		JsonArray lootItems = new JsonArray();
		for (ItemStack item : items)
		{
			if (lootItems.size() >= MAX_LOOT_LOG_ITEMS) break;
			int itemId = itemManager.canonicalize(item.getId());
			String itemName = client.getItemDefinition(itemId).getName();
			if (itemName == null || item.getQuantity() < 1) continue;
			JsonObject entry = new JsonObject();
			entry.addProperty("itemId", itemId);
			entry.addProperty("name", itemName);
			entry.addProperty("quantity", item.getQuantity());
			lootItems.add(entry);
		}
		JsonObject loot = new JsonObject();
		loot.addProperty("sourceType", sourceType);
		loot.addProperty("sourceName", sourceName);
		loot.addProperty("kills", Math.max(1, amount));
		loot.add("items", lootItems);
		signals.send("loot", loot);
	}

	private boolean isDuplicateLootEvent(String sourceType, String sourceName, Collection<ItemStack> items)
	{
		int tick = client.getTickCount();
		recentLootEvents.entrySet().removeIf(entry -> tick - entry.getValue() > 3);
		List<String> stacks = new ArrayList<>();
		for (ItemStack item : items) stacks.add(itemManager.canonicalize(item.getId()) + "x" + item.getQuantity());
		Collections.sort(stacks);
		String key = tick + ":" + sourceType + ":" + sourceName.toLowerCase(Locale.ROOT) + ":" + String.join(",", stacks);
		return recentLootEvents.putIfAbsent(key, tick) != null;
	}

	private List<String> joinedCompetitionIds(JsonArray events)
	{
		List<String> ids = new ArrayList<>();
		for (JsonObject event : TsgHubJson.objects(events))
		{
			String type = TsgHubJson.str(event, "type");
			if (!("skill".equals(type) || "boss".equals(type)) || "ended".equals(TsgHubJson.str(event, "status"))) continue;
			String id = TsgHubJson.str(event, "id");
			if (!session.get(MEMBER_TOKEN + id).isEmpty()) ids.add(id);
		}
		return ids;
	}

	private void signalReply(JsonObject message)
	{
		String eventId = TsgHubJson.str(message, "eventId");
		switch (TsgHubJson.str(message, "type"))
		{
			case "progress":
				try
				{
					long gained = Long.parseLong(TsgHubJson.str(message, "gained"));
					String rank = TsgHubJson.str(message, "rank");
					Long parsedRank = rank.isEmpty() ? null : Long.parseLong(rank);
					ui(s -> s.competitionProgress(eventId, gained, parsedRank));
				}
				catch (NumberFormatException ignored) { /* Malformed progress waits for the next report. */ }
				break;
			case "claimed":
				showProgressMessage(TsgHubJson.str(message, "taskTitle"), TsgHubJson.str(message, "progress"), TsgHubJson.str(message, "complete"));
				if (!socket.isLive() && eventId.equals(session.get(EVENT_ID))) refreshBoard();
				break;
			case "notice":
				String text = TsgHubJson.str(message, "text");
				if (!text.isEmpty()) memberStatus(text, "error".equals(TsgHubJson.str(message, "tone")) ? Tone.ERROR : Tone.INFO);
				break;
			default:
		}
	}

	private void pollTeamNotifications()
	{
		if (!sharingInClan() || executor == null || executor.isShutdown()) return;
		String token = session.get(TOKEN);
		String eventId = session.get(EVENT_ID);
		if (token.isEmpty() || eventId.isEmpty()) return;
		String path = "/v1/events/" + eventId + "/notifications?inClanChat=" + inClanChat + "&clanName=" + TsgHubApi.encode(detectedClanName);
		api().call("GET", path, token, null).thenAcceptAsync(result -> {
			if (showNotifications(result.getAsJsonArray("notifications"))) refreshBoard();
		}, executor);
	}

	private boolean showNotifications(JsonArray notifications)
	{
		if (notifications == null) return false;
		for (JsonObject notification : TsgHubJson.objects(notifications))
		{
			if (!"task-completed".equals(TsgHubJson.str(notification, "type"))) continue;
			showLocalChatMessage(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL).append(TsgHubJson.str(notification, "actorName") + " completed ")
				.append(ChatColorType.HIGHLIGHT).append(TsgHubJson.str(notification, "taskTitle"))
				.append(ChatColorType.NORMAL).append(" for " + TsgHubJson.str(notification, "teamName") + "!"));
		}
		return notifications.size() > 0;
	}

	private void showProgressMessage(String title, String count, String complete)
	{
		if (title.isEmpty() || count.isEmpty()) return;
		ChatMessageBuilder message = new ChatMessageBuilder();
		if (!complete.isEmpty())
		{
			message.append(TsgHubTheme.CHAT_SUCCESS, "task".equals(complete) ? "Task complete: " : "Your part is done: ")
				.append(ChatColorType.HIGHLIGHT).append(title)
				.append(ChatColorType.NORMAL).append(" (" + count + ")");
		}
		else message.append(ChatColorType.HIGHLIGHT).append(title).append(ChatColorType.NORMAL).append(": " + count);
		showLocalChatMessage(message);
	}

	private void announce(String text)
	{
		if (!config.eventAnnouncements()) return;
		showClanChatMessage("Event", new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	private void clanAnnouncement(String text)
	{
		if (!config.clanAnnouncements()) return;
		showClanChatMessage("Announcement", new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	private void updateAvailable(String version)
	{
		if (version.equals(notifiedUpdate)) return;
		notifiedUpdate = version;
		ui(s -> s.setUpdate(version));
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("TSG Hub " + Text.escapeJagex(version) + " is available. Restart RuneLite to update."));
	}

	private void showMapNotice()
	{
		mapNoticeChecked = true;
		if (config.mapNoticeShown()) return;
		configManager.setConfiguration("tsghub", "mapNoticeShown", true);
		if (locationSharingEnabled()) showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("Your position now shows on clanmates' world maps. Turn off Share location and activity to stop."));
	}

	private void showLocalChatMessage(ChatMessageBuilder body)
	{
		String message = new ChatMessageBuilder().append(config.chatColor(), "[TSG Hub] ").build() + body.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
	}

	private void showClanChatMessage(String tag, ChatMessageBuilder body)
	{
		if (!isInHubClan())
		{
			showLocalChatMessage(body);
			return;
		}
		String message = new ChatMessageBuilder().append(config.chatColor(), "[" + tag + "] ").build() + body.build().replace("<colHIGHLIGHT>", ColorUtil.colorTag(config.chatColor()));
		localMessages.add(Text.removeTags(message));
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CLAN_MESSAGE).sender(detectedClanName).runeLiteFormattedMessage(message).build());
	}

	private void memberStatus(String message, Tone tone)
	{
		ui(s -> s.setStatus(message, tone));
	}

	private void memberError(String failure, Exception e)
	{
		memberStatus(failure + TsgHubFormat.friendlyError(e), Tone.ERROR);
	}

	private void sidebarBusy(boolean busy)
	{
		ui(s -> s.setBusy(busy));
	}

	TsgHubApi api() { return api; }

	TsgHubOrganizer organizer() { return organizer; }

	void ui(Consumer<TsgHubSidebarPanel> action)
	{
		SwingUtilities.invokeLater(() -> { if (sidebar != null) action.accept(sidebar); });
	}

	boolean canUseHub()
	{
		return isInHubClan() && sharingEnabled() && !detectedPlayerName.isEmpty();
	}

}
