package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.tsghub.TsgHubUi.Tone;
import com.tsghub.group.GroupMembersPanel;
import com.tsghub.group.GroupTracker;
import com.tsghub.group.GroupViewSettings;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Window;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
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
import net.runelite.client.input.MouseManager;
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
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "TSG Hub", description = "Type Shiii Gaming clan events and progress tracking", tags = {"tsg", "clan", "bingo", "events"})
public class TsgHubPlugin extends Plugin
{
	private static final String SERVICE_URL = "https://api.typeshiigaming.com";
	private static final long BOARD_AUTO_REFRESH_SECONDS = 15;
	private static final Set<Integer> PVM_PET_ITEM_IDS = Set.of(11995, 12643, 12644, 12645, 12646, 12647, 12648, 12649, 12650, 12651, 12652, 12654, 12655, 12703, 12816, 12921, 12939, 12940, 13178, 13179, 13180, 13181, 13182, 13225, 13247, 13262, 20693, 20851, 21273, 21291, 21748, 21992, 22318, 22319, 22376, 22378, 22380, 22382, 22384, 22473, 22663, 22746, 22748, 22750, 22752, 23495, 23757, 23759, 23760, 24491, 25748, 25749, 25750, 25751, 25752, 25836, 25842, 25843, 26348, 27352, 27386, 27590, 27592, 27593, 27649, 27650, 27651, 28246, 28248, 28250, 28252, 28801, 28960, 29836, 30152, 30154, 30622, 30888, 31130, 31285, 31287);
	@Inject private Client client;
	@Inject private ItemManager itemManager;
	@Inject private TsgHubConfig config;
	@Inject private ConfigManager configManager;
	@Inject private ClientThread clientThread;
	@Inject private ClientToolbar clientToolbar;
	@Inject private OverlayManager overlayManager;
	@Inject private MouseManager mouseManager;
	@Inject private OkHttpClient okHttpClient;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private ChatIconManager chatIconManager;
	@Inject private PartyService partyService;
	@Inject private WSClient wsClient;
	@Inject private SpriteManager spriteManager;
	@Inject private EventBus eventBus;
	@Inject @Named("developerMode") private boolean developerMode;
	private TsgHubPanel panel;
	private TsgHubSidebarPanel sidebar;
	private TsgHubBoardOverlay boardOverlay;
	private JFrame hubWindow;
	private NavigationButton navigationButton;
	private ScheduledExecutorService executor;
	private ExecutorService itemSearchExecutor;
	private final Set<String> attemptedXpClaims = ConcurrentHashMap.newKeySet();
	private final Set<String> attemptedKillCountClaims = ConcurrentHashMap.newKeySet();
	private final Map<String, Integer> recentLootEvents = new ConcurrentHashMap<>();
	private final Map<String, Integer> syncedClanRanks = new ConcurrentHashMap<>();
	private static final int PET_CHECK_TICKS = 5;
	private static final int MAX_LOOT_LOG_ITEMS = 50;
	private static final int CLAN_CHECK_TICKS = 50;
	private static final long KEY_CHECK_RETRY_SECONDS = 15;
	private static final long KEY_CHECK_MAX_RETRY_SECONDS = 300;
	private volatile int clanCheckTicks;
	private int petCheckTicks;
	private Set<Integer> inventoryPets = Collections.emptySet();
	private volatile List<XpTask> xpTasks = Collections.emptyList();
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
	private volatile List<PvmTask> pvmTasks = Collections.emptyList();
	private volatile String taskEventId = "";
	private volatile boolean adminVerified;
	private volatile String checkedKeyIdentity = "";
	private volatile String rejectedKey = "";
	private volatile int keyCheckAttempts;
	private volatile boolean announceKey;
	private volatile TsgHubApi api;
	private TsgHubSocket socket;
	private volatile boolean organizerRefreshPending;
	private TsgHubCompetitionTracker competitions;
	private TsgHubGroups groups;
	private TsgHubPresence presence;
	private TsgHubDrops drops;
	private TsgHubRanks ranks;
	private GroupTracker groupTracker;

	@Override
	protected void startUp()
	{
		TsgHubSession.init(configManager);
		migrateAdminKey();
		String clanOverride = System.getProperty("tsghub.clanName", "").trim();
		hubClanName = developerMode && !clanOverride.isEmpty() ? clanOverride : "TSGaming";
		String serviceOverride = System.getProperty("tsghub.serviceUrl", "").trim();
		String serviceUrl = developerMode && !serviceOverride.isEmpty() ? serviceOverride : SERVICE_URL;
		api = new TsgHubApi(okHttpClient, serviceUrl);
		executor = Executors.newSingleThreadScheduledExecutor(daemon("tsg-hub-api"));
		socket = new TsgHubSocket(okHttpClient, serviceUrl, executor, new TsgHubSocket.Listener()
		{
			@Override public void onReady() { resyncLiveViews(); }
			@Override public void onChanged(String topic, String eventId) { liveChange(topic, eventId); }
			@Override public void onAdminRevoked(String token) { recheckHubKey(token); }
			@Override public void onAnnouncement(String text) { announce(text); }
			@Override public void onUpdateAvailable(String version) { updateAvailable(version); }
		});
		executor.scheduleAtFixedRate(this::socketTick, 2, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(this::autoRefreshBoard), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		competitions = new TsgHubCompetitionTracker(this::api, executor, client, clientThread);
		itemSearchExecutor = Executors.newFixedThreadPool(2, daemon("tsg-hub-item-search"));
		panel = new TsgHubPanel(this);
		GroupMembersPanel groupMembers = new GroupMembersPanel(new GroupViewSettings()
		{
			@Override public boolean autoExpandMembers() { return config.partyExpandMembers(); }
			@Override public boolean displayVirtualLevels() { return config.partyVirtualLevels(); }
			@Override public boolean displayPlayerWorlds() { return config.partyShowWorlds(); }
		}, spriteManager, itemManager);
		sidebar = new TsgHubSidebarPanel(this, groupMembers);
		groups = new TsgHubGroups(this, client, partyService, executor, this::api, () -> sidebar);
		groupTracker = new GroupTracker(client, clientThread, partyService, wsClient, itemManager, groups::isGroupParty, config::partyShowSelf, this::currentArea, groups);
		groups.setTracker(groupTracker);
		eventBus.register(groupTracker);
		groupTracker.start();
		executor.scheduleAtFixedRate(groups::heartbeat, 5, TsgHubGroups.HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(groupTracker::keepAlive, GroupTracker.KEEPALIVE_SECONDS, GroupTracker.KEEPALIVE_SECONDS, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(groups::autoRefresh), TsgHubGroups.REFRESH_SECONDS, TsgHubGroups.REFRESH_SECONDS, TimeUnit.SECONDS);
		presence = new TsgHubPresence(this, client, clientThread, chatIconManager, executor, this::api, () -> sidebar, socket);
		executor.scheduleAtFixedRate(unlessLive(presence::autoRefresh), TsgHubPresence.REFRESH_SECONDS, TsgHubPresence.REFRESH_SECONDS, TimeUnit.SECONDS);
		drops = new TsgHubDrops(this, client, clientThread, executor, this::api, () -> sidebar);
		executor.scheduleAtFixedRate(unlessLive(drops::autoRefresh), TsgHubDrops.REFRESH_SECONDS, TsgHubDrops.REFRESH_SECONDS, TimeUnit.SECONDS);
		ranks = new TsgHubRanks(this, client, executor, this::api, this::adminKey);
		boardOverlay = new TsgHubBoardOverlay(client);
		overlayManager.add(boardOverlay);
		mouseManager.registerMouseListener(boardOverlay);
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
		if (boardOverlay != null)
		{
			boardOverlay.setVisible(false);
			overlayManager.remove(boardOverlay);
			mouseManager.unregisterMouseListener(boardOverlay);
		}
		if (hubWindow != null) SwingUtilities.invokeLater(hubWindow::dispose);
		if (presence != null) presence.shutDown();
		if (ranks != null) ranks.shutDown();
		if (socket != null) socket.disconnect();
		if (executor != null) executor.shutdownNow();
		if (itemSearchExecutor != null) itemSearchExecutor.shutdownNow();
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
				hubWindow = new JFrame("TSG Hub · Admin");
				hubWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
				hubWindow.setContentPane(panel);
				hubWindow.setMinimumSize(new Dimension(680, 480));
				hubWindow.setSize(860, 640);
				Window owner = SwingUtilities.getWindowAncestor(client.getCanvas());
				if (owner != null && owner.getIconImages() != null) hubWindow.setIconImages(owner.getIconImages());
				hubWindow.setLocationRelativeTo(owner);
			}
			hubWindow.setVisible(true);
			hubWindow.toFront();
			loadManagedEvents();
			syncSocketNow();
		});
	}

	void showBoardOverlay()
	{
		if (boardOverlay == null) return;
		if (TsgHubSession.get("token").isEmpty())
		{
			memberStatus("Join an event before opening the board overlay.", Tone.ERROR);
			return;
		}
		boardOverlay.setVisible(true);
	}

	void setBoardOverlayData(JsonObject event, String displayName)
	{
		boardOverlay.setEvent(event, TsgHubUi.teamIdFor(event, displayName));
	}

	@Provides
	TsgHubConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TsgHubConfig.class);
	}

	String getDetectedClanName() { return detectedClanName; }
	boolean sharingEnabled() { return config.dataSharingOptIn(); }
	boolean locationSharingEnabled() { return config.dataSharingOptIn() && config.shareLocation(); }
	TsgHubGroups groups() { return groups; }
	TsgHubPresence presence() { return presence; }
	TsgHubDrops drops() { return drops; }

	String currentArea() { return presence == null ? "" : presence.area(); }
	boolean inClanChat() { return inClanChat; }
	String getDetectedPlayerName() { return detectedPlayerName; }
	int getDetectedClanRank() { return detectedClanRank; }
	String getCurrentEventId() { return TsgHubSession.get("eventId"); }
	AsyncBufferedImage getItemImage(int itemId) { return itemManager == null ? null : itemManager.getImage(itemId); }

	AsyncBufferedImage getCoinImage(long gp) { return itemManager == null ? null : itemManager.getImage(ItemID.COINS, (int) Math.min(gp, Integer.MAX_VALUE), false); }

	private String getOrganizerEventId()
	{
		return TsgHubSession.get("organizerEventId");
	}

	String getHubClanName() { return hubClanName; }

	private boolean clanPending()
	{
		return detectedClanName.isEmpty() && clanCheckTicks > 0;
	}

	boolean isInHubClan()
	{
		return TsgHubUi.samePlayer(detectedClanName, hubClanName);
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

	private String adminKey()
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
		if (name.isEmpty() || !isInHubClan() || !config.dataSharingOptIn()) return;
		String hash = accountHash();
		String identity = key + "\n" + name + "\n" + hash;
		if (identity.equals(checkedKeyIdentity) || key.equals(rejectedKey) || executor == null || executor.isShutdown()) return;
		checkedKeyIdentity = identity;
		executor.submit(() -> {
			try
			{
				String query = "/v1/me?displayName=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
				if (!hash.isEmpty()) query += "&accountHash=" + hash;
				JsonObject result = api().request("GET", query, key, null);
				if (!identity.equals(checkedKeyIdentity)) return;
				keyCheckAttempts = 0;
				boolean admin = grantsAdmin(result);
				if (admin != adminVerified) setAdminVerified(admin);
				if (admin) loadClanEvents();
				String linkError = TsgHubUi.str(result, "linkError");
				boolean linked = TsgHubUi.bool(result, "linked");
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
		});
	}

	static boolean grantsAdmin(JsonObject me)
	{
		String role = TsgHubUi.str(me, "role");
		return role.isEmpty() || "admin".equals(role);
	}

	private void retryKeyCheck(String identity)
	{
		if (!identity.equals(checkedKeyIdentity)) return;
		checkedKeyIdentity = "";
		long delay = Math.min(KEY_CHECK_MAX_RETRY_SECONDS, KEY_CHECK_RETRY_SECONDS << Math.min(keyCheckAttempts++, 5));
		if (executor != null && !executor.isShutdown()) executor.schedule(this::checkHubKey, delay, TimeUnit.SECONDS);
	}

	private void recheckHubKey(String key)
	{
		if (!key.equals(configuredKey())) return;
		checkedKeyIdentity = "";
		if (adminVerified) setAdminVerified(false);
		checkHubKey();
	}

	private void keyRejected(String key)
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
		if (ranks != null) ranks.reset();
		detectedPlayerName = "";
		detectedClanName = "";
		detectedClanRank = -1;
		inClanChat = false;
		clanCheckTicks = 0;
		sidebarRouted = false;
		adminVerified = false;
		checkedKeyIdentity = "";
		syncSocketNow();
		if (competitions != null) competitions.clear();
		SwingUtilities.invokeLater(() -> {
			if (panel != null) panel.setDetectedClanName("", -1);
			if (sidebar != null)
			{
				sidebar.setOrganizerAccess(false);
				sidebar.showLoggedOut();
			}
			if (hubWindow != null) hubWindow.setVisible(false);
		});
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		String previousClan = detectedClanName;
		refreshDetectedClan();
		if (ranks != null) ranks.onClanChannelChanged();
		if (config.dataSharingOptIn() && sidebarRouted && !detectedClanName.equals(previousClan)) loadClanEvents();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (ranks != null) ranks.onScriptPostFired(event.getScriptId());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"tsghub".equals(event.getGroup())) return;
		if ("partyShowSelf".equals(event.getKey()))
		{
			if (config.partyShowSelf()) groupTracker.refreshSelf();
			else SwingUtilities.invokeLater(() -> sidebar.groupSelfHidden());
			return;
		}
		if (event.getKey().startsWith("party"))
		{
			boolean expand = "partyExpandMembers".equals(event.getKey());
			SwingUtilities.invokeLater(() -> sidebar.groupSettingsChanged(expand));
			return;
		}
		if ("shareLocation".equals(event.getKey()))
		{
			SwingUtilities.invokeLater(() -> sidebar.locationSharingChanged());
			return;
		}
		if ("hubKey".equals(event.getKey()))
		{
			checkedKeyIdentity = "";
			rejectedKey = "";
			keyCheckAttempts = 0;
			announceKey = !configuredKey().isEmpty();
			if (adminVerified) setAdminVerified(false);
			if (ranks != null) ranks.reset();
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
			if (sidebar != null)
			{
				sidebar.setOrganizerAccess(organizerAccess);
			}
			if (!organizerAccess && hubWindow != null) hubWindow.setVisible(false);
		});
		if (!sidebarRouted && !detectedPlayerName.isEmpty()) routeSidebar();
		else if (sidebarRouted && (routedAsHubMember != isInHubClan() || routedClanPending != clanPending())) routeSidebar();
		else if (client.getGameState() != GameState.LOGGED_IN && !sidebarRouted) SwingUtilities.invokeLater(() -> sidebar.showLoggedOut());
		checkHubKey();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		attemptedXpClaims.clear();
		syncedClanRanks.clear();
		syncedIdentity = "";
		if (ranks != null) ranks.reset();
		if (competitions != null) competitions.clear();
		clearTaskCache();
		if (boardOverlay != null) boardOverlay.setVisible(false);
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
		if (loggedIn && !pending && isInHubClan() && config.dataSharingOptIn())
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
			loadClanEvents();
			String eventId = TsgHubSession.get("eventId");
			if (!eventId.isEmpty() && !TsgHubSession.get("token").isEmpty() && TsgHubUi.samePlayer(eventSession("memberName:", eventId, "displayName"), detectedPlayerName))
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
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/join", adminKey(), payload);
				String joinedEventId = result.getAsJsonObject("member").get("eventId").getAsString();
				String memberToken = result.get("token").getAsString();
				String memberName = result.getAsJsonObject("member").get("displayName").getAsString();
				clearTaskCache();
				TsgHubSession.set("memberToken:" + joinedEventId, memberToken);
				TsgHubSession.set("memberName:" + joinedEventId, memberName);
				TsgHubSession.set("token", memberToken);
				TsgHubSession.set("displayName", memberName);
				TsgHubSession.set("eventId", joinedEventId);
				attemptedXpClaims.clear();
				memberStatus("You're in! Progress now counts for your team.", Tone.SUCCESS);
				refreshBoard(true);
			}
			catch (Exception e) { joinFailed(TsgHubUi.friendlyError(e)); }
		});
	}

	private void joinFailed(String message)
	{
		SwingUtilities.invokeLater(() -> sidebar.joinFailed(message));
	}

	void leaveEvent(String eventId)
	{
		String revokeToken = eventToken(eventId);
		if (!revokeToken.isEmpty())
		{
			executor.submit(() -> {
				try { api().request("POST", "/v1/events/" + eventId + "/disconnect", revokeToken, new JsonObject()); }
				catch (Exception ignored) { /* Local disconnect still succeeds while the service is unavailable. */ }
			});
		}
		TsgHubSession.set("memberToken:" + eventId, "");
		TsgHubSession.set("memberName:" + eventId, "");
		if (eventId.equals(TsgHubSession.get("eventId")))
		{
			TsgHubSession.set("token", "");
			TsgHubSession.set("eventId", "");
			attemptedXpClaims.clear();
			clearTaskCache();
			if (boardOverlay != null) boardOverlay.setVisible(false);
		}
		memberStatus("Disconnected. Rejoin anytime with your team code.", Tone.SUCCESS);
		SwingUtilities.invokeLater(() -> sidebar.showEventList());
		loadClanEvents();
	}

	private void clearAllSessions()
	{
		if (competitions != null) competitions.clear();
		revokeRemoteSession();
		TsgHubSession.set("token", "");
		TsgHubSession.set("eventId", "");
		TsgHubSession.set("organizerEventId", "");
		TsgHubSession.removePrefix("memberToken:");
		TsgHubSession.removePrefix("memberName:");
		TsgHubSession.removePrefix("organizerToken:");
		TsgHubSession.removePrefix("organizerName:");
		attemptedXpClaims.clear();
		syncedClanRanks.clear();
		clearTaskCache();
		checkedKeyIdentity = "";
		if (adminVerified) setAdminVerified(false);
		if (boardOverlay != null) boardOverlay.setVisible(false);
	}

	private void revokeRemoteSession()
	{
		if (executor == null || executor.isShutdown()) return;
		Map<String, String> sessions = new HashMap<>();
		for (String key : TsgHubSession.keysWithPrefix("memberToken:"))
		{
			String eventId = key.substring("memberToken:".length());
			sessions.put(eventId, TsgHubSession.get(key));
		}
		String activeEventId = TsgHubSession.get("eventId");
		String activeToken = TsgHubSession.get("token");
		if (!activeEventId.isEmpty() && !activeToken.isEmpty()) sessions.putIfAbsent(activeEventId, activeToken);
		if (sessions.isEmpty()) return;
		executor.submit(() -> {
			for (Map.Entry<String, String> session : sessions.entrySet())
			{
				try { api().request("POST", "/v1/events/" + session.getKey() + "/disconnect", session.getValue(), new JsonObject()); }
				catch (Exception e) { /* Local disconnect still succeeds while the service is unavailable. */ }
			}
		});
	}

	void refreshBoard()
	{
		refreshBoard(false);
	}

	private void refreshBoard(boolean open)
	{
		if (!isInHubClan()) return;
		if (!config.dataSharingOptIn()) return;
		String eventId = TsgHubSession.get("eventId");
		String token = TsgHubSession.get("token");
		if (eventId.isEmpty() || token.isEmpty()) return;
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("GET", "/v1/events/" + eventId, token, null);
				JsonObject event = result.getAsJsonObject("event");
				syncClanRank(eventId, token, event);
				if (eventId.equals(TsgHubSession.get("eventId")))
				{
					cacheXpTasks(event);
					cachePvmTasks(event);
					taskEventId = eventId;
				}
				SwingUtilities.invokeLater(() -> {
					String displayName = TsgHubSession.get("displayName");
					setBoardOverlayData(event, displayName);
					sidebar.showBoard(event, displayName, open);
				});
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't update the board. "); }
			finally { sidebarBusy(false); }
		});
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
		else memberStatus(failure + TsgHubUi.friendlyError(e), Tone.ERROR);
	}

	private Runnable unlessLive(Runnable poll)
	{
		return () -> {
			if (!socket.isLive()) poll.run();
		};
	}

	private void syncSocketNow()
	{
		ScheduledExecutorService e = executor;
		if (socket == null || e == null || e.isShutdown()) return;
		e.execute(this::socketTick);
	}

	private void socketTick()
	{
		if (!isInHubClan() || !config.dataSharingOptIn())
		{
			socket.disconnect();
			return;
		}
		socket.connect(detectedClanName);
		Map<String, String> tokens = new HashMap<>();
		String eventId = TsgHubSession.get("eventId");
		String token = TsgHubSession.get("token");
		if (!eventId.isEmpty() && !token.isEmpty()) tokens.put(eventId, token);
		String competitionId = sidebar == null ? null : sidebar.openCompetitionId();
		String memberToken = competitionId == null ? "" : TsgHubSession.get("memberToken:" + competitionId);
		if (!memberToken.isEmpty()) tokens.putIfAbsent(competitionId, memberToken);
		String organizerEventId = adminWindowOpen() ? getOrganizerEventId() : "";
		String organizerToken = organizerEventId.isEmpty() ? "" : organizerCredential(organizerEventId);
		if (!organizerToken.isEmpty()) tokens.putIfAbsent(organizerEventId, organizerToken);
		if (organizerRefreshPending && adminWindowOpen()) liveRefreshOrganizerEvent();
		socket.sync(tokens, inClanChat);
		socket.admin(adminKey());
		if (!socket.isLive()) pollTeamNotifications();
	}

	private void resyncLiveViews()
	{
		pollTeamNotifications();
		autoRefreshBoard();
		groups.autoRefresh();
		presence.autoRefresh();
		drops.autoRefresh();
		loadClanEvents();
		if (adminWindowOpen())
		{
			loadManagedEvents();
			liveRefreshOrganizerEvent();
		}
	}

	private boolean adminWindowOpen()
	{
		JFrame window = hubWindow;
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
			case "notifications":
				if (eventId.equals(TsgHubSession.get("eventId"))) pollTeamNotifications();
				break;
			case "event":
				if (adminWindowOpen() && eventId.equals(getOrganizerEventId())) liveRefreshOrganizerEvent();
				if (sidebar == null) break;
				if (eventId.equals(TsgHubSession.get("eventId")) && sidebar.wantsAutoRefresh()) refreshBoard();
				if (eventId.equals(sidebar.openCompetitionId())) openCompetition(eventId);
				break;
			case "events":
				loadClanEvents();
				if (adminWindowOpen()) loadManagedEvents();
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
		if (!isInHubClan() || !config.dataSharingOptIn() || detectedPlayerName.isEmpty()) return;
		JsonObject body = memberBody(detectedPlayerName);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/participate", adminKey(), body);
				TsgHubSession.set("memberToken:" + eventId, result.get("token").getAsString());
				TsgHubSession.set("memberName:" + eventId, result.getAsJsonObject("member").get("displayName").getAsString());
				memberStatus("You're in! Your progress counts from now.", Tone.SUCCESS);
				loadClanEvents();
				openCompetition(eventId, true);
			}
			catch (Exception e) { SwingUtilities.invokeLater(() -> sidebar.competitionJoinFailed(TsgHubUi.friendlyError(e))); }
			finally { sidebarBusy(false); }
		});
	}

	void openCompetition(String eventId)
	{
		openCompetition(eventId, false);
	}

	void openCompetition(String eventId, boolean open)
	{
		String token = TsgHubSession.get("memberToken:" + eventId);
		if (token.isEmpty()) return;
		String name = TsgHubSession.get("memberName:" + eventId);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject event = api().request("GET", "/v1/events/" + eventId, token, null).getAsJsonObject("event");
				SwingUtilities.invokeLater(() -> sidebar.showCompetition(event, name, open));
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't load the leaderboard. "); }
			finally { sidebarBusy(false); }
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

	void copyDiscordInvite(Runnable done)
	{
		JsonObject body = new JsonObject();
		body.addProperty("displayName", detectedPlayerName);
		body.addProperty("clanName", detectedClanName);
		addAccountHash(body);
		executor.submit(() -> {
			try
			{
				String url = api().request("POST", "/v1/discord/invite", null, body).get("url").getAsString();
				SwingUtilities.invokeLater(() -> TsgHubUi.copyToClipboard(url));
				memberStatus("Discord invite copied. Paste it in your browser to join.", Tone.SUCCESS);
			}
			catch (Exception e) { memberStatus(TsgHubUi.friendlyError(e), Tone.ERROR); }
			finally { SwingUtilities.invokeLater(done); }
		});
	}

	private String accountHash()
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

	private void addAccountHash(JsonObject body)
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
		for (String key : TsgHubSession.keysWithPrefix("memberToken:")) memberTokens.add(TsgHubSession.get(key));
		if (!TsgHubSession.get("token").isEmpty()) memberTokens.add(TsgHubSession.get("token"));
		for (String key : TsgHubSession.keysWithPrefix("organizerToken:")) organizerTokens.add(TsgHubSession.get(key));
		for (String key : TsgHubSession.keysWithPrefix("memberName:")) renameStored(key, name);
		for (String key : TsgHubSession.keysWithPrefix("organizerName:")) renameStored(key, name);
		renameStored("displayName", name);
		JsonObject body = new JsonObject();
		body.addProperty("displayName", name);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("accountHash", hash);
		body.add("memberTokens", memberTokens);
		body.add("organizerTokens", organizerTokens);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/identity", null, body);
				if (TsgHubUi.bool(result, "updated") && !TsgHubSession.get("eventId").isEmpty()) refreshBoard();
			}
			catch (Exception e) { syncedIdentity = ""; }
		});
	}

	private void renameStored(String key, String name)
	{
		String stored = TsgHubSession.get(key);
		if (!stored.isEmpty() && !TsgHubUi.samePlayer(stored, name)) TsgHubSession.set(key, name);
	}

	void loadClanEvents()
	{
		if (!isInHubClan()) return;
		if (!config.dataSharingOptIn()) return;
		String clanName = detectedClanName;
		if (clanName == null || clanName.trim().isEmpty())
		{
			SwingUtilities.invokeLater(() -> sidebar.setEvents(new JsonArray()));
			return;
		}
		String encodedClan = URLEncoder.encode(clanName, StandardCharsets.UTF_8);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject response = api().request("GET", "/v1/events?clanName=" + encodedClan, adminKey(), null);
				JsonArray events = response.getAsJsonArray("events");
				for (int i = 0; i < events.size(); i++)
				{
					JsonObject event = events.get(i).getAsJsonObject();
					String eventId = event.get("id").getAsString();
					String savedToken = TsgHubSession.get("memberToken:" + eventId);
					if (savedToken.isEmpty() && eventId.equals(TsgHubSession.get("eventId")))
					{
						savedToken = TsgHubSession.get("token");
						if (!savedToken.isEmpty())
						{
							TsgHubSession.set("memberToken:" + eventId, savedToken);
							TsgHubSession.set("memberName:" + eventId, TsgHubSession.get("displayName"));
						}
					}
					event.addProperty("joined", !savedToken.isEmpty());
				}
				competitions.setEvents(events);
				SwingUtilities.invokeLater(() -> sidebar.setEvents(events));
			}
			catch (Exception e) { memberStatus("Couldn't load events. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
			finally { sidebarBusy(false); }
		});
	}

	void activateEvent(String eventId)
	{
		activateEvent(eventId, true);
	}

	private void activateEvent(String eventId, boolean open)
	{
		String token = eventToken(eventId);
		if (token.isEmpty())
		{
			memberStatus("Join this event with your team code first.", Tone.ERROR);
			return;
		}
		String displayName = eventSession("memberName:", eventId, "displayName");
		clearTaskCache();
		TsgHubSession.set("token", token);
		TsgHubSession.set("eventId", eventId);
		TsgHubSession.set("displayName", displayName);
		attemptedXpClaims.clear();
		refreshBoard(open);
	}

	private void syncClanRank(String eventId, String token, JsonObject event)
	{
		String displayName = detectedPlayerName;
		String memberName = TsgHubSession.get("displayName");
		String eventClan = event.has("clanName") && !event.get("clanName").isJsonNull() ? event.get("clanName").getAsString() : "";
		if (token.isEmpty() || displayName.isEmpty() || !displayName.equalsIgnoreCase(memberName)
			|| eventClan.isEmpty() || !eventClan.equalsIgnoreCase(detectedClanName)) return;
		int rank = detectedClanRank;
		String key = eventId + ":" + TsgHubUi.playerKey(displayName);
		Integer synced = syncedClanRanks.get(key);
		if (synced != null && synced == rank) return;
		JsonObject body = new JsonObject();
		body.addProperty("displayName", displayName);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("clanRank", rank);
		try
		{
			api().request("POST", "/v1/events/" + eventId + "/clan-rank", token, body);
			syncedClanRanks.put(key, rank);
		}
		catch (Exception ignored) { /* Board refresh should still succeed if a rank sync is unavailable. */ }
	}

	void submitManual(String taskId, String note)
	{
		String eventId = TsgHubSession.get("eventId");
		String token = TsgHubSession.get("token");
		JsonObject body = new JsonObject();
		body.addProperty("taskId", taskId);
		body.addProperty("evidenceId", "manual-" + UUID.randomUUID());
		body.addProperty("source", "manual");
		JsonObject evidence = new JsonObject(); evidence.addProperty("note", note.trim()); body.add("evidence", evidence);
		memberStatus("Sending proof...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				api().request("POST", "/v1/events/" + eventId + "/claims", token, body);
				memberStatus("Sent. An admin will review it.", Tone.SUCCESS);
				SwingUtilities.invokeLater(() -> sidebar.manualSubmitFinished(true));
				refreshBoard();
			}
			catch (Exception e)
			{
				memberStatus("Couldn't send proof. " + TsgHubUi.friendlyError(e), Tone.ERROR);
				SwingUtilities.invokeLater(() -> sidebar.manualSubmitFinished(false));
			}
		});
	}

	void selectEvent(String eventId)
	{
		if (eventId.trim().isEmpty()) return;
		TsgHubSession.set("organizerEventId", eventId.trim());
		syncSocketNow();
		organizerStatus("Loading event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject event = organizerRequest("GET", "/v1/events/" + eventId.trim() + "/organizer", organizerCredential(eventId.trim()), null);
				SwingUtilities.invokeLater(() -> panel.openOrganizerEvent(event));
				organizerStatus("", Tone.INFO);
			}
			catch (Exception e) { organizerStatus("Couldn't open event. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	void loadManagedEvents()
	{
		if (!isInHubClan()) return;
		if (!config.dataSharingOptIn()) { organizerStatus("Turn on sharing in the TSG Hub sidebar first.", Tone.ERROR); return; }
		if (!canManageOrganizerUi()) return;
		executor.submit(() -> {
			try
			{
				String credential = organizerListCredential();
				if (credential.isEmpty()) throw new IllegalStateException("Join or create an event first");
				JsonObject response = organizerRequest("GET", "/v1/managed-events", credential, null);
				SwingUtilities.invokeLater(() -> panel.setManagedEvents(response.getAsJsonArray("events")));
			}
			catch (Exception e) { organizerStatus("Couldn't load events. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	void createEvent(String name, Instant startsAt, Instant endsAt, boolean hideScores, boolean hidden, String type, JsonObject typeConfig, JsonArray prizes)
	{
		if (!isInHubClan()) { eventFormFailed("TSG Hub is only for members of the " + hubClanName + " clan."); return; }
		if (!config.dataSharingOptIn()) { eventFormFailed("Turn on sharing in the TSG Hub sidebar first."); return; }
		String credential = adminKey();
		if (credential.isEmpty())
		{
			eventFormFailed("Creating events needs a hub key with admin access from /hub key in the clan Discord.");
			return;
		}
		if (detectedClanName.isEmpty()) { eventFormFailed("No clan detected. Log in to a character in your clan first."); return; }
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) { eventFormFailed("Log in first so you're recorded as the admin."); return; }
		JsonObject body = new JsonObject();
		String creatorName = client.getLocalPlayer().getName();
		body.addProperty("name", name.trim());
		body.addProperty("clanName", detectedClanName);
		body.addProperty("createdByName", creatorName);
		body.addProperty("clanRank", detectedClanRank);
		addEventTimes(body, startsAt, endsAt);
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		body.addProperty("type", type);
		addAccountHash(body);
		body.add("config", typeConfig == null ? new JsonObject() : typeConfig);
		body.add("prizes", prizes);
		organizerStatus("Creating event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject response = organizerRequest("POST", "/v1/events", credential, body);
				JsonObject result = response.getAsJsonObject("event");
				String id = result.get("id").getAsString();
				TsgHubSession.set("organizerEventId", id);
				TsgHubSession.set("organizerToken:" + id, response.get("organizerToken").getAsString());
				TsgHubSession.set("organizerName:" + id, creatorName);
				SwingUtilities.invokeLater(() -> panel.eventCreated(result));
				organizerStatus("Event created. Add teams next.", Tone.SUCCESS);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { eventFormFailed(TsgHubUi.friendlyError(e)); organizerStatus("", Tone.INFO); }
		});
	}

	private static void addEventTimes(JsonObject body, Instant startsAt, Instant endsAt)
	{
		body.addProperty("startsAt", startsAt.toString());
		if (endsAt != null) body.addProperty("endsAt", endsAt.toString());
	}

	void updateEvent(String eventId, String name, Instant startsAt, Instant endsAt, boolean hideScores, boolean hidden, JsonObject typeConfig, JsonArray prizes)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name.trim());
		addEventTimes(body, startsAt, endsAt);
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		if (typeConfig != null) body.add("config", typeConfig);
		body.add("prizes", prizes);
		String credential = organizerCredential(eventId);
		organizerStatus("Saving...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				organizerRequest("PATCH", "/v1/events/" + eventId, credential, body);
				organizerStatus("Event saved.", Tone.SUCCESS);
				refreshOrganizerEvent(true);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { eventFormFailed(TsgHubUi.friendlyError(e)); organizerStatus("", Tone.INFO); }
		});
	}

	void publishEvent(JsonObject event)
	{
		String eventId = TsgHubUi.str(event, "id");
		JsonObject body = new JsonObject();
		body.addProperty("name", TsgHubUi.str(event, "name"));
		Instant startsAt = TsgHubUi.eventStart(event);
		if (startsAt == null) { organizerStatus("Couldn't publish. The event has no start time.", Tone.ERROR); return; }
		addEventTimes(body, startsAt, TsgHubUi.eventEnd(event));
		body.addProperty("hidden", false);
		String credential = organizerCredential(eventId);
		organizerStatus("Publishing...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				organizerRequest("PATCH", "/v1/events/" + eventId, credential, body);
				organizerStatus("Event published. Players can see it now.", Tone.SUCCESS);
				refreshOrganizerEvent(true);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { organizerStatus("Couldn't publish. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	void endEvent(String eventId)
	{
		String credential = organizerCredential(eventId);
		organizerStatus("Ending event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				organizerRequest("POST", "/v1/events/" + eventId + "/end", credential, null);
				organizerStatus("Event ended.", Tone.SUCCESS);
				refreshOrganizerEvent(true);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { organizerStatus("Couldn't end the event. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	private void eventFormFailed(String message)
	{
		SwingUtilities.invokeLater(() -> panel.eventFormFailed(message));
	}

	void createTeam(String name)
	{
		JsonObject body = new JsonObject(); body.addProperty("name", name.trim());
		adminRequest("POST", "/teams", body, "Team added. Copy its code and share it with the team.", result -> {
			SwingUtilities.invokeLater(() -> panel.teamCreated());
			refreshOrganizerEvent(false);
		}, null);
	}

	void renameTeam(String teamId, String name)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name.trim());
		adminRequest("PATCH", "/teams/" + teamId, body, "Team renamed.", result -> refreshOrganizerEvent(false), null);
	}
	void saveTask(String taskId, String title, String description, int selectedType, int selectedScope, int selectedKillSignal, int dropCategory, String targetNamesText, JsonArray selectedItems, int dropRuleMode, String targetCount, String points)
	{
		JsonObject body = new JsonObject();
		String type = selectedType == 1 ? "kill" : selectedType == 2 || selectedType == 3 ? "drop" : selectedType == 4 ? "raid" : "manual";
		body.addProperty("title", title.trim());
		body.addProperty("description", description.trim());
		body.addProperty("type", type);
		body.addProperty("scope", selectedScope == 1 ? "individual" : selectedScope == 2 ? "solo" : "team");
		try { body.addProperty("points", Integer.parseInt(points.trim())); }
		catch (NumberFormatException e) { taskFormFailed("Points must be a whole number."); return; }
		if (!"manual".equals(type))
		{
			JsonObject cfg = new JsonObject();
			List<String> targetNames = new ArrayList<>();
			if (selectedType == 2 || selectedType == 3)
			{
				for (int i = 0; i < selectedItems.size(); i++) targetNames.add(selectedItems.get(i).getAsJsonObject().get("name").getAsString());
			}
			else for (String name : targetNamesText.split("\\R")) if (!name.trim().isEmpty()) targetNames.add(name.trim());
			if (targetNames.isEmpty() && !(selectedType == 2 && dropCategory > 0)) { taskFormFailed(selectedType == 2 || selectedType == 3 ? "Search for and add at least one item." : "Enter at least one target name or raid mode."); return; }
			if (selectedType != 3)
			{
				try { cfg.addProperty("targetCount", Integer.parseInt(targetCount.trim())); }
				catch (NumberFormatException e) { taskFormFailed("Target count must be a whole number."); return; }
			}
			if ("kill".equals(type))
			{
				if (targetNames.size() != 1) { taskFormFailed("A boss kill task takes one NPC name."); return; }
				cfg.addProperty("npcName", targetNames.get(0));
				cfg.addProperty("signal", selectedKillSignal == 0 ? "chat" : "loot");
			}
			else if ("raid".equals(type))
			{
				JsonArray modes = new JsonArray();
				for (String mode : targetNames) modes.add(mode.toLowerCase(Locale.ROOT));
				cfg.add("modes", modes);
				cfg.addProperty("clanOnly", true);
			}
			else if (selectedType == 2 && dropCategory > 0)
			{
				cfg.addProperty("itemGroup", dropCategory == 1 ? "jar" : "pet");
			}
			else
			{
				JsonArray itemNames = new JsonArray();
				for (String name : targetNames) itemNames.add(name);
				cfg.add("itemNames", itemNames);
				JsonArray itemIds = new JsonArray();
				for (int i = 0; i < selectedItems.size(); i++)
				{
					JsonObject selected = selectedItems.get(i).getAsJsonObject();
					itemIds.add(selected.get("id").getAsInt());
				}
				cfg.add("itemIds", itemIds);
				// Completing any one set group finishes the task.
				if (selectedType == 3 || dropRuleMode == 1)
				{
					Map<Integer, JsonArray> groupedItems = new TreeMap<>();
					for (int i = 0; i < selectedItems.size(); i++)
					{
						JsonObject selected = selectedItems.get(i).getAsJsonObject();
						int group = selected.get("group").getAsInt();
						if (group < 0) group = 0;
						JsonArray groupItems = groupedItems.computeIfAbsent(group, ignored -> new JsonArray());
						JsonObject item = new JsonObject();
						item.addProperty("name", selected.get("name").getAsString());
						item.addProperty("id", selected.get("id").getAsInt());
						groupItems.add(item);
					}
					JsonArray groups = new JsonArray();
					for (JsonArray group : groupedItems.values()) groups.add(group);
					cfg.add("itemGroups", groups);
				}
			}
			body.add("config", cfg);
		}
		Consumer<JsonObject> done = result -> {
			SwingUtilities.invokeLater(() -> panel.finishTaskEdit());
			refreshOrganizerEvent(false);
		};
		if (taskId == null || taskId.isEmpty())
			adminRequest("POST", "/tasks", body, "Task added for every team.", done, this::taskFormFailed);
		else
			adminRequest("PATCH", "/tasks/" + taskId, body, "Task saved. Existing progress was rechecked.", done, this::taskFormFailed);
	}

	private void taskFormFailed(String message)
	{
		SwingUtilities.invokeLater(() -> panel.taskFormFailed(message));
	}

	void deleteTask(String taskId)
	{
		adminRequest("DELETE", "/tasks/" + taskId, null, "Task deleted.", result -> {
			SwingUtilities.invokeLater(() -> panel.taskDeleted(taskId));
			refreshOrganizerEvent(false);
		}, null);
	}

	void deleteTeam(String teamId)
	{
		adminRequest("DELETE", "/teams/" + teamId, null, "Team deleted.", result -> refreshOrganizerEvent(false), null);
	}

	void deleteEvent(String eventId)
	{
		String credential = organizerCredential(eventId);
		organizerStatus("Deleting event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				organizerRequest("DELETE", "/v1/events/" + eventId, credential, null);
				forgetEvent(eventId);
				SwingUtilities.invokeLater(() -> panel.eventDeleted(eventId));
				organizerStatus("Event deleted.", Tone.SUCCESS);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { organizerStatus("Couldn't delete the event. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	private void forgetEvent(String eventId)
	{
		TsgHubSession.set("organizerToken:" + eventId, "");
		TsgHubSession.set("organizerName:" + eventId, "");
		TsgHubSession.set("memberToken:" + eventId, "");
		TsgHubSession.set("memberName:" + eventId, "");
		if (eventId.equals(TsgHubSession.get("organizerEventId"))) TsgHubSession.set("organizerEventId", "");
		if (eventId.equals(TsgHubSession.get("eventId")))
		{
			TsgHubSession.set("token", "");
			TsgHubSession.set("eventId", "");
			clearTaskCache();
			SwingUtilities.invokeLater(() -> sidebar.closeBoard());
		}
	}

	void completeTask(String taskId, String teamId, String memberId, String note)
	{
		JsonObject body = new JsonObject(); body.addProperty("teamId", teamId); body.addProperty("note", note.trim());
		if (memberId != null && !memberId.isEmpty()) body.addProperty("memberId", memberId);
		adminRequest("POST", "/tasks/" + taskId + "/complete", body, "Marked complete.", result -> refreshOrganizerEvent(false), null);
	}

	void reconcileTask(String taskId, boolean dryRun)
	{
		JsonObject body = new JsonObject();
		body.addProperty("dryRun", dryRun);
		adminRequest("POST", "/tasks/" + taskId + "/reconcile", body, "", result -> {
			if (dryRun)
			{
				SwingUtilities.invokeLater(() -> panel.confirmReconcile(taskId, result));
				return;
			}
			int count = result.has("count") ? result.get("count").getAsInt() : 0;
			organizerStatus("Credited " + count + (count == 1 ? " match" : " matches") + " from the loot log.", Tone.SUCCESS);
			refreshOrganizerEvent(false);
		}, null);
	}

	void reviewClaim(String claimId, boolean approve)
	{
		JsonObject body = new JsonObject();
		body.addProperty("status", approve ? "approved" : "rejected");
		adminRequest("POST", "/claims/" + claimId + "/review", body, approve ? "Claim approved." : "Claim rejected.", result -> refreshOrganizerEvent(false), null);
	}

	private void refreshOrganizerEvent(boolean open)
	{
		String eventId = getOrganizerEventId();
		if (eventId.isEmpty() || executor == null || executor.isShutdown()) return;
		executor.submit(() -> {
			try
			{
				JsonObject event = organizerRequest("GET", "/v1/events/" + eventId + "/organizer", organizerCredential(eventId), null);
				SwingUtilities.invokeLater(() -> {
					if (open) panel.openOrganizerEvent(event);
					else panel.showEvent(event);
				});
			}
			catch (Exception e) { organizerStatus("Couldn't refresh the event. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	private void liveRefreshOrganizerEvent()
	{
		organizerRefreshPending = false;
		String eventId = getOrganizerEventId();
		if (eventId.isEmpty() || executor == null || executor.isShutdown()) return;
		executor.submit(() -> {
			try
			{
				JsonObject event = organizerRequest("GET", "/v1/events/" + eventId + "/organizer", organizerCredential(eventId), null);
				SwingUtilities.invokeLater(() -> {
					if (!eventId.equals(getOrganizerEventId())) return;
					if (panel.editingText()) organizerRefreshPending = true;
					else panel.showEvent(event);
				});
			}
			catch (Exception ignored) { }
		});
	}

	void searchItems(String query, Consumer<List<ItemSuggestion>> callback)
	{
		if (itemSearchExecutor == null || itemSearchExecutor.isShutdown())
		{
			SwingUtilities.invokeLater(() -> callback.accept(Collections.emptyList()));
			return;
		}
		itemSearchExecutor.submit(() -> {
			List<ItemPrice> matches;
			try
			{
				matches = itemManager.search(query);
			}
			catch (RuntimeException e)
			{
				organizerStatus("Item search failed. " + TsgHubUi.friendlyError(e), Tone.ERROR);
				SwingUtilities.invokeLater(() -> callback.accept(Collections.emptyList()));
				return;
			}

			// canonicalize() calls getItemDefinition, which must run on the client thread.
			clientThread.invokeLater(() -> {
				List<ItemSuggestion> suggestions = new ArrayList<>();
				try
				{
					for (ItemPrice result : matches)
					{
						if (result.getName() == null || result.getName().trim().isEmpty()) continue;
						int id = itemManager.canonicalize(result.getId());
						if (suggestions.stream().noneMatch(item -> item.id == id)) suggestions.add(new ItemSuggestion(id, result.getName().trim()));
						if (suggestions.size() >= 30) break;
					}
				}
				catch (RuntimeException e) { organizerStatus("Item search failed. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
				List<ItemSuggestion> result = suggestions;
				SwingUtilities.invokeLater(() -> callback.accept(result));
			});
		});
	}

	static final class ItemSuggestion
	{
		final int id;
		final String name;

		ItemSuggestion(int id, String name) { this.id = id; this.name = name; }
		@Override public String toString() { return name; }
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
		if (!isInHubClan()) return;
		if (config.dataSharingOptIn() && competitions != null) competitions.onXp(event.getSkill(), event.getXp());
		if (!config.dataSharingOptIn() || client.getLocalPlayer() == null) return;
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		String skill = event.getSkill().getName();
		int xp = event.getXp();
		for (XpTask task : xpTasks)
		{
			if (!skill.equalsIgnoreCase(task.skill) || xp < task.targetXp) continue;
			String key = task.id + ":" + task.targetXp;
			if (!attemptedXpClaims.add(key)) continue;
			executor.submit(() -> submitXpClaim(eventId, task, skill, xp, key));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (competitions != null) competitions.onInterfaceOpened(event.getGroupId());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (competitions != null) competitions.onInterfaceClosed(event.getGroupId());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!isInHubClan()) return;
		if (event.getComposition() == null) return;
		String name = Text.removeTags(event.getComposition().getName());
		if (config.dataSharingOptIn() && competitions != null) competitions.onNpcLoot(name, client.getTickCount());
		processLoot("NPC", name, event.getItems(), 1);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isInHubClan()) return;
		if (event.getType() == null || "PLAYER".equals(event.getType().name()) || "NPC".equals(event.getType().name())) return;
		processLoot(event.getType().name(), event.getName(), event.getItems(), event.getAmount());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isInHubClan()) return;
		if (event == null || event.getMessage() == null || client.getLocalPlayer() == null) return;
		if (drops != null) drops.onChatMessage(event);
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
		String message = event.getMessage().replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
		if (config.dataSharingOptIn() && competitions != null) competitions.onChat(message);
		if (!config.dataSharingOptIn()) return;
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		submitKillCountSignals(eventId, message);
		submitCollectionLogSignals(eventId, message);
		if (isPetMessage(message) && hasPetTask()) petCheckTicks = PET_CHECK_TICKS;
		String mode = detectRaidMode(message);
		if (mode == null) return;
		List<String> visiblePlayers = new ArrayList<>();
		List<String> nonClanPlayers = new ArrayList<>();
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView != null && worldView.players() != null)
		{
			for (Player player : worldView.players())
			{
				if (player == null || player.getName() == null) continue;
				String name = player.getName();
				if (visiblePlayers.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) visiblePlayers.add(name);
				if (!player.isClanMember() && nonClanPlayers.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) nonClanPlayers.add(name);
			}
		}
		for (PvmTask task : pvmTasks)
		{
			if (!"raid".equals(task.type) || !task.targetNames.contains(mode)) continue;
			submitRaidClaim(eventId, task, mode, visiblePlayers, nonClanPlayers);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (presence != null) presence.onGameTick();
		if (ranks != null) ranks.onGameTick();
		if (clanCheckTicks > 0)
		{
			clanCheckTicks--;
			if (client.getClanSettings() != null) clanCheckTicks = 0;
			if (clanCheckTicks == 0) refreshDetectedClan();
		}
		if (!hasPetTask())
		{
			petCheckTicks = 0;
			inventoryPets = Collections.emptySet();
			return;
		}
		if (petCheckTicks <= 0)
		{
			inventoryPets = inventoryPetIds();
			return;
		}
		petCheckTicks--;
		int petId = newPetId();
		if (petId <= 0) return;
		petCheckTicks = 0;
		inventoryPets = inventoryPetIds();
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		String petName = itemManager.getItemComposition(petId).getName();
		for (PvmTask task : pvmTasks)
		{
			if ("drop".equals(task.type) && "pet".equals(task.itemGroup)) submitPvmClaim(eventId, task, "drop", petName, 1, petId);
		}
	}

	private static boolean isPetMessage(String message)
	{
		return message.contains("you have a funny feeling like you're being followed")
			|| message.contains("you feel something weird sneaking into your backpack");
	}

	private boolean hasPetTask()
	{
		for (PvmTask task : pvmTasks) if ("drop".equals(task.type) && "pet".equals(task.itemGroup)) return true;
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
			if (PVM_PET_ITEM_IDS.contains(itemId)) pets.add(itemId);
		}
		return pets;
	}

	private int newPetId()
	{
		for (int itemId : inventoryPetIds()) if (!inventoryPets.contains(itemId)) return itemId;
		NPC follower = client.getFollower();
		String followerName = follower == null ? null : petName(follower.getName());
		if (followerName == null || followerName.isEmpty()) return -1;
		for (int itemId : PVM_PET_ITEM_IDS)
		{
			if (followerName.equals(petName(itemManager.getItemComposition(itemId).getName()))) return itemId;
		}
		return -1;
	}

	private static String petName(String name)
	{
		if (name == null) return null;
		String normalized = name.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
		return normalized.startsWith("pet ") ? normalized.substring(4) : normalized;
	}

	private void submitCollectionLogSignals(String eventId, String message)
	{
		if (!message.contains("collection log") || !(message.contains("new item") || message.contains("new entry"))) return;
		for (PvmTask task : pvmTasks)
		{
			if (!"drop".equals(task.type) || !task.requireAllItems) continue;
			for (int i = 0; i < task.targetNames.size(); i++)
			{
				String itemName = task.targetNames.get(i);
				if (!message.contains(itemName.toLowerCase(Locale.ROOT))) continue;
				int itemId = i < task.targetItemIds.size() ? task.targetItemIds.get(i) : 0;
				submitPvmClaim(eventId, task, "drop", itemName, 1, itemId);
			}
		}
	}

	private void submitKillCountSignals(String eventId, String message)
	{
		if (!message.contains("kill count") && !message.contains("kill-count")) return;
		Matcher countMatch = TsgHubCompetitionTracker.KILL_COUNT.matcher(message);
		if (!countMatch.find()) return;
		int count;
		try { count = Integer.parseInt(countMatch.group(1).replace(",", "")); }
		catch (NumberFormatException e) { return; }
		for (PvmTask task : pvmTasks)
		{
			if (!"kill".equals(task.type) || !task.chatKillCount || task.targetNames.isEmpty()) continue;
			String bossName = task.targetNames.get(0);
			if (!message.contains(bossName.toLowerCase(Locale.ROOT))) continue;
			String key = task.id + ":" + count;
			if (!attemptedKillCountClaims.add(key)) continue;
			JsonObject claim = new JsonObject();
			claim.addProperty("taskId", task.id);
			claim.addProperty("evidenceId", "kc-" + task.id + "-" + count);
			claim.addProperty("source", "kill");
			JsonObject evidence = new JsonObject();
			evidence.addProperty("name", bossName);
			evidence.addProperty("quantity", 1);
			evidence.addProperty("killCount", count);
			claim.add("evidence", evidence);
			executor.submit(() -> {
				try
				{
					JsonObject result = api().request("POST", "/v1/events/" + eventId + "/claims", eventToken(eventId), claim);
					showProgressMessage(result);
					SwingUtilities.invokeLater(() -> refreshBoard());
				}
				catch (Exception e)
				{
					attemptedKillCountClaims.remove(key);
					SwingUtilities.invokeLater(() -> memberStatus("Kill count sync failed. " + TsgHubUi.friendlyError(e), Tone.ERROR));
				}
			});
		}
	}

	private String detectRaidMode(String message)
	{
		if (message.contains("theatre of blood") && message.contains("completion time"))
		{
			if (message.contains("hard mode")) return "tob_hm";
			if (message.contains("entry mode")) return "tob_entry";
			return "tob";
		}
		if (message.contains("tombs of amascut") && message.contains("completion time"))
		{
			if (message.contains("expert")) return "toa_expert";
			if (message.contains("entry mode")) return "toa_entry";
			return "toa";
		}
		if (message.contains("your completed chambers of xeric challenge mode count is")) return "cox_cm";
		if (message.contains("your completed chambers of xeric count is")) return "cox";
		return null;
	}

	private void submitRaidClaim(String eventId, PvmTask task, String mode, List<String> players, List<String> nonClanPlayers)
	{
		JsonObject claim = new JsonObject();
		claim.addProperty("taskId", task.id);
		claim.addProperty("evidenceId", "raid-" + UUID.randomUUID());
		claim.addProperty("source", "raid");
		JsonObject evidence = new JsonObject();
		evidence.addProperty("mode", mode);
		JsonArray playerNames = new JsonArray();
		for (String player : players) playerNames.add(player);
		evidence.add("players", playerNames);
		JsonArray outsiders = new JsonArray();
		for (String player : nonClanPlayers) outsiders.add(player);
		evidence.add("nonClanPlayers", outsiders);
		claim.add("evidence", evidence);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/claims", eventToken(eventId), claim);
				showProgressMessage(result);
				SwingUtilities.invokeLater(() -> refreshBoard());
			}
			catch (Exception e) { SwingUtilities.invokeLater(() -> memberStatus("Raid progress not credited. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
		});
	}

	private void processLoot(String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
		if (!config.dataSharingOptIn() || client.getLocalPlayer() == null
			|| sourceName == null || items == null || isDuplicateLootEvent(sourceType, sourceName, items)) return;
		String eventId = claimEventId();
		if (eventId.isEmpty()) return;
		String lootId = "loot-" + UUID.randomUUID();
		submitLootLog(eventId, lootId, sourceType, sourceName, items, amount);
		for (PvmTask task : pvmTasks)
		{
			if ("kill".equals(task.type) && !task.chatKillCount && "NPC".equals(sourceType) && !task.targetNames.isEmpty() && sourceName.equalsIgnoreCase(task.targetNames.get(0)))
			{
				submitPvmClaim(eventId, task, "kill", sourceName, Math.max(1, amount), -1, lootId);
			}
			else if ("drop".equals(task.type))
			{
				for (ItemStack item : items)
				{
					int itemId = itemManager.canonicalize(item.getId());
					String itemName = client.getItemDefinition(itemId).getName();
					boolean itemMatch = "jar".equals(task.itemGroup) && itemName != null
						&& itemName.toLowerCase(Locale.ROOT).startsWith("jar of ")
						|| "pet".equals(task.itemGroup) && PVM_PET_ITEM_IDS.contains(itemId);
					for (int i = 0; i < task.targetNames.size(); i++)
					{
						int configuredId = i < task.targetItemIds.size() ? task.targetItemIds.get(i) : 0;
						if (configuredId > 0 ? configuredId == itemId : itemName != null && itemName.equalsIgnoreCase(task.targetNames.get(i))) itemMatch = true;
					}
					if (itemName != null && itemMatch)
						submitPvmClaim(eventId, task, "drop", itemName, item.getQuantity(), itemId, lootId);
				}
			}
		}
	}

	private void submitLootLog(String eventId, String lootId, String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
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
		if (lootItems.size() == 0) return;
		JsonObject loot = new JsonObject();
		loot.addProperty("lootId", lootId);
		loot.addProperty("sourceType", sourceType);
		loot.addProperty("sourceName", sourceName);
		loot.addProperty("kills", Math.max(1, amount));
		loot.add("items", lootItems);
		executor.submit(() -> {
			try { api().request("POST", "/v1/events/" + eventId + "/loot", eventToken(eventId), loot); }
			catch (Exception ignored) { }
		});
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

	private void clearTaskCache()
	{
		taskEventId = "";
		xpTasks = Collections.emptyList();
		pvmTasks = Collections.emptyList();
	}

	private String claimEventId()
	{
		String eventId = TsgHubSession.get("eventId");
		if (eventId.isEmpty() || !eventId.equals(taskEventId) || eventToken(eventId).isEmpty()) return "";
		return eventId;
	}

	private static String eventToken(String eventId)
	{
		return eventSession("memberToken:", eventId, "token");
	}

	private static String eventSession(String prefix, String eventId, String activeKey)
	{
		String value = TsgHubSession.get(prefix + eventId);
		return value.isEmpty() && eventId.equals(TsgHubSession.get("eventId")) ? TsgHubSession.get(activeKey) : value;
	}

	private void submitPvmClaim(String eventId, PvmTask task, String source, String name, int quantity, int itemId)
	{
		submitPvmClaim(eventId, task, source, name, quantity, itemId, null);
	}

	private void submitPvmClaim(String eventId, PvmTask task, String source, String name, int quantity, int itemId, String lootId)
	{
		JsonObject claim = new JsonObject();
		claim.addProperty("taskId", task.id);
		claim.addProperty("evidenceId", source + "-" + UUID.randomUUID());
		claim.addProperty("source", source);
		JsonObject evidence = new JsonObject();
		evidence.addProperty("name", name);
		evidence.addProperty("quantity", quantity);
		if (itemId > 0) evidence.addProperty("itemId", itemId);
		if (lootId != null) evidence.addProperty("lootId", lootId);
		claim.add("evidence", evidence);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/claims", eventToken(eventId), claim);
				showProgressMessage(result);
				SwingUtilities.invokeLater(() -> refreshBoard());
			}
			catch (Exception e) { SwingUtilities.invokeLater(() -> memberStatus("PVM progress sync failed. " + TsgHubUi.friendlyError(e), Tone.ERROR)); }
		});
	}

	private void pollTeamNotifications()
	{
		if (!isInHubClan() || !config.dataSharingOptIn() || executor == null || executor.isShutdown()) return;
		String token = TsgHubSession.get("token");
		String eventId = TsgHubSession.get("eventId");
		if (token.isEmpty() || eventId.isEmpty()) return;
		try
		{
			String clan = URLEncoder.encode(detectedClanName, StandardCharsets.UTF_8);
			String path = "/v1/events/" + eventId + "/notifications?inClanChat=" + inClanChat + "&clanName=" + clan;
			JsonObject result = api().request("GET", path, token, null);
			JsonArray notifications = result.getAsJsonArray("notifications");
			if (notifications == null) return;
			for (int i = 0; i < notifications.size(); i++)
			{
				JsonObject notification = notifications.get(i).getAsJsonObject();
				if (!"task-completed".equals(notification.get("type").getAsString())) continue;
				showLocalChatMessage(new ChatMessageBuilder()
					.append(ChatColorType.NORMAL).append(notification.get("actorName").getAsString() + " completed ")
					.append(ChatColorType.HIGHLIGHT).append(notification.get("taskTitle").getAsString())
					.append(ChatColorType.NORMAL).append(" for " + notification.get("teamName").getAsString() + "!"));
			}
			if (notifications.size() > 0) refreshBoard();
		}
		catch (Exception ignored) { /* Notifications retry on the next scheduled poll. */ }
	}

	private void showProgressMessage(JsonObject result)
	{
		if (result == null || result.has("duplicate") && result.get("duplicate").getAsBoolean() || !result.has("progress")) return;
		JsonObject progress = result.getAsJsonObject("progress");
		// Per-member tasks report the submitter's own count.
		JsonObject mine = result.has("memberProgress") && result.get("memberProgress").isJsonObject() ? result.getAsJsonObject("memberProgress") : null;
		JsonObject shown = mine != null ? mine : progress;
		String title = result.get("taskTitle").getAsString();
		String count = shown.get("progress").getAsInt() + "/" + shown.get("target").getAsInt();
		ChatMessageBuilder message = new ChatMessageBuilder();
		if (!progress.get("completed").getAsBoolean() && mine != null && TsgHubUi.bool(mine, "completed"))
		{
			message.append(TsgHubUi.SUCCESS.darker(), "Your part is done: ")
				.append(ChatColorType.HIGHLIGHT).append(title)
				.append(ChatColorType.NORMAL).append(" (" + count + ")");
		}
		else if (progress.get("completed").getAsBoolean())
		{
			message.append(TsgHubUi.SUCCESS.darker(), "Task complete: ")
				.append(ChatColorType.HIGHLIGHT).append(title)
				.append(ChatColorType.NORMAL).append(" (" + count + ")");
		}
		else
		{
			message.append(ChatColorType.HIGHLIGHT).append(title)
				.append(ChatColorType.NORMAL).append(": " + count);
		}
		showLocalChatMessage(message);
	}

	private void announce(String text)
	{
		if (!config.eventAnnouncements()) return;
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	private void updateAvailable(String version)
	{
		if (version.equals(notifiedUpdate)) return;
		notifiedUpdate = version;
		SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setUpdate(version); });
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("TSG Hub " + Text.escapeJagex(version) + " is available. Restart RuneLite to update."));
	}

	private void showLocalChatMessage(ChatMessageBuilder body)
	{
		String message = new ChatMessageBuilder().append(TsgHubUi.ACCENT.darker(), "[TSG Hub] ").build() + body.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
	}

	private void submitXpClaim(String eventId, XpTask task, String skill, int xp, String key)
	{
		JsonObject claim = new JsonObject();
		claim.addProperty("taskId", task.id);
		claim.addProperty("evidenceId", "xp-" + key);
		claim.addProperty("source", "xp");
		JsonObject evidence = new JsonObject(); evidence.addProperty("skill", skill); evidence.addProperty("xp", xp); claim.add("evidence", evidence);
		try { showProgressMessage(api().request("POST", "/v1/events/" + eventId + "/claims", eventToken(eventId), claim)); }
		catch (Exception e)
		{
			attemptedXpClaims.remove(key);
			SwingUtilities.invokeLater(() -> memberStatus("Progress sync paused. " + TsgHubUi.friendlyError(e), Tone.ERROR));
		}
	}

	private void cacheXpTasks(JsonObject event)
	{
		List<XpTask> found = new ArrayList<>();
		JsonArray tasks = event.getAsJsonArray("tasks");
		for (int i = 0; i < tasks.size(); i++)
		{
			JsonObject task = tasks.get(i).getAsJsonObject();
			if (!"xp".equals(task.get("type").getAsString())) continue;
			JsonObject config = task.getAsJsonObject("config");
			found.add(new XpTask(task.get("id").getAsString(), config.get("skill").getAsString(), config.get("targetXp").getAsInt()));
		}
		xpTasks = found;
	}

	private void cachePvmTasks(JsonObject event)
	{
		List<PvmTask> found = new ArrayList<>();
		JsonArray tasks = event.getAsJsonArray("tasks");
		for (int i = 0; i < tasks.size(); i++)
		{
			JsonObject task = tasks.get(i).getAsJsonObject();
			String type = task.get("type").getAsString();
			if (!"kill".equals(type) && !"drop".equals(type) && !"raid".equals(type)) continue;
			JsonObject config = task.getAsJsonObject("config");
			List<String> targetNames = new ArrayList<>();
			if ("kill".equals(type)) targetNames.add(config.get("npcName").getAsString());
			else if ("raid".equals(type) && config.has("modes"))
			{
				JsonArray modes = config.getAsJsonArray("modes");
				for (int j = 0; j < modes.size(); j++) targetNames.add(modes.get(j).getAsString().toLowerCase(Locale.ROOT));
			}
			else if (config.has("itemNames") && config.get("itemNames").isJsonArray())
			{
				JsonArray itemNames = config.getAsJsonArray("itemNames");
				for (int j = 0; j < itemNames.size(); j++) targetNames.add(itemNames.get(j).getAsString());
			}
			else if (config.has("itemName")) targetNames.add(config.get("itemName").getAsString());
			List<Integer> targetItemIds = new ArrayList<>();
			if (config.has("itemIds") && config.get("itemIds").isJsonArray())
			{
				JsonArray ids = config.getAsJsonArray("itemIds");
				for (int j = 0; j < ids.size(); j++) targetItemIds.add(ids.get(j).getAsInt());
			}
			found.add(new PvmTask(task.get("id").getAsString(), type, targetNames, targetItemIds,
				"chat".equals(config.has("signal") ? config.get("signal").getAsString() : "loot"),
				config.has("requireAllItems") && config.get("requireAllItems").getAsBoolean() || config.has("itemGroups"),
				config.has("itemGroup") ? config.get("itemGroup").getAsString() : ""));
		}
		pvmTasks = found;
	}

	private static final class XpTask
	{
		private final String id;
		private final String skill;
		private final int targetXp;
		private XpTask(String id, String skill, int targetXp) { this.id = id; this.skill = skill; this.targetXp = targetXp; }
	}

	private static final class PvmTask
	{
		private final String id;
		private final String type;
		private final List<String> targetNames;
		private final List<Integer> targetItemIds;
		private final boolean chatKillCount;
		private final boolean requireAllItems;
		private final String itemGroup;
		private PvmTask(String id, String type, List<String> targetNames, List<Integer> targetItemIds, boolean chatKillCount, boolean requireAllItems, String itemGroup)
		{
			this.id = id;
			this.type = type;
			this.targetNames = targetNames;
			this.targetItemIds = targetItemIds;
			this.chatKillCount = chatKillCount;
			this.requireAllItems = requireAllItems;
			this.itemGroup = itemGroup;
		}
	}

	private void memberStatus(String message, Tone tone)
	{
		SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setStatus(message, tone); });
	}

	private void organizerStatus(String message, Tone tone)
	{
		SwingUtilities.invokeLater(() -> { if (panel != null) panel.setStatus(message, tone); });
	}

	private void sidebarBusy(boolean busy)
	{
		SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setBusy(busy); });
	}

	TsgHubApi api() { return api; }

	private void adminRequest(String method, String suffix, JsonObject payload, String success, Consumer<JsonObject> callback, Consumer<String> onError)
	{
		String eventId = getOrganizerEventId();
		if (eventId.isEmpty()) { organizerStatus("Open an event first.", Tone.ERROR); return; }
		String credential = organizerCredential(eventId);
		organizerStatus("Saving...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject result = organizerRequest(method, "/v1/events/" + eventId + suffix, credential, payload);
				organizerStatus(success, Tone.SUCCESS);
				callback.accept(result);
			}
			catch (Exception e)
			{
				if (onError != null) { onError.accept(TsgHubUi.friendlyError(e)); organizerStatus("", Tone.INFO); }
				else organizerStatus(TsgHubUi.friendlyError(e), Tone.ERROR);
			}
		});
	}

	private JsonObject organizerRequest(String method, String path, String credential, JsonObject payload) throws Exception
	{
		try { return api().request(method, path, credential, payload); }
		catch (TsgHubApi.HttpError e)
		{
			if (e.status == 401 && !credential.isEmpty() && credential.equals(adminKey())) keyRejected(credential);
			throw e;
		}
	}

	private String organizerCredential(String eventId)
	{
		String ownerToken = TsgHubSession.get("organizerToken:" + eventId);
		String creatorName = TsgHubSession.get("organizerName:" + eventId);
		if (!ownerToken.isEmpty() && !creatorName.isEmpty() && creatorName.equalsIgnoreCase(detectedPlayerName)) return ownerToken;
		String adminToken = adminKey();
		if (!adminToken.isEmpty()) return adminToken;
		return TsgHubSession.get("token");
	}

	private String organizerListCredential()
	{
		String adminToken = adminKey();
		if (!adminToken.isEmpty()) return adminToken;
		String eventId = getOrganizerEventId();
		if (!eventId.isEmpty())
		{
			String credential = organizerCredential(eventId);
			if (!credential.isEmpty()) return credential;
		}
		String memberToken = TsgHubSession.get("token");
		if (!memberToken.isEmpty()) return memberToken;
		for (String key : TsgHubSession.keysWithPrefix("organizerToken:"))
		{
			String candidateId = key.substring("organizerToken:".length());
			String creator = TsgHubSession.get("organizerName:" + candidateId);
			if (!creator.isEmpty() && creator.equalsIgnoreCase(detectedPlayerName)) return TsgHubSession.get(key);
		}
		return "";
	}
}
