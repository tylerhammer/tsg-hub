package com.tsghub;

import static com.tsghub.TsgHubSession.DISPLAY_NAME;
import static com.tsghub.TsgHubSession.EVENT_ID;
import static com.tsghub.TsgHubSession.MEMBER_NAME;
import static com.tsghub.TsgHubSession.MEMBER_TOKEN;
import static com.tsghub.TsgHubSession.ORGANIZER_EVENT_ID;
import static com.tsghub.TsgHubSession.ORGANIZER_NAME;
import static com.tsghub.TsgHubSession.ORGANIZER_TOKEN;
import static com.tsghub.TsgHubSession.TOKEN;

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
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

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
	private TsgHubPanel panel;
	private TsgHubSidebarPanel sidebar;
	private volatile JFrame hubWindow;
	private NavigationButton navigationButton;
	private ScheduledExecutorService executor;
	private ExecutorService itemSearchExecutor;
	private final Map<String, Integer> recentLootEvents = new ConcurrentHashMap<>();
	private final Map<String, Integer> syncedClanRanks = new ConcurrentHashMap<>();
	private static final int PET_CHECK_TICKS = 5;
	private static final int MAX_LOOT_LOG_ITEMS = 50;
	private static final int CLAN_CHECK_TICKS = 50;
	private static final long KEY_CHECK_RETRY_SECONDS = 15;
	private static final long KEY_CHECK_MAX_RETRY_SECONDS = 300;
	private volatile int clanCheckTicks;
	private int petCheckTicks;
	private int recentPet;
	private int recentPetTick;
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
	private volatile int keyCheckAttempts;
	private volatile boolean announceKey;
	private volatile TsgHubApi api;
	private TsgHubSocket socket;
	private TsgHubSignals signals;
	private TsgHubOrganizer organizer;
	private volatile Filepath rulesCache;
	private TsgHubGroups groups;
	private TsgHubPresence presence;
	private TsgHubMemberMap memberMap;
	private TsgHubMapOverlay mapOverlay;
	private boolean mapNoticeChecked;
	private TsgHubDrops drops;
	private TsgHubAnnouncements announcements;
	private TsgHubNewsEditor newsEditor;
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
			@Override public void onReady(String rulesVersion)
			{
				refreshRules(rulesVersion);
				signals.resend();
				resyncLiveViews();
			}
			@Override public void onChanged(String topic, String eventId) { liveChange(topic, eventId); }
			@Override public void onAdminRevoked(String token) { recheckHubKey(token); }
			@Override public void onAnnouncement(String text) { announce(text); }
			@Override public void onUpdateAvailable(String version) { updateAvailable(version); }
			@Override public void onSnapshot(JsonObject message) { snapshot(message); }
			@Override public void onConnection(TsgHubSocket.State state) { ui(s -> s.setConnection(state)); }
			@Override public void onAck(String id) { signals.acked(id); }
			@Override public void onSubscribed() { signals.resend(); }
			@Override public void onReply(JsonObject message) { signalReply(message); }
			@Override public void onXpRequest(String skills) { reportXp(skills); }
		});
		signals = new TsgHubSignals(socket, this::api, this::signalReply);
		executor.execute(this::loadCachedRules);
		executor.scheduleAtFixedRate(this::socketTick, 2, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(this::autoRefreshBoard), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		itemSearchExecutor = Executors.newFixedThreadPool(2, daemon("tsg-hub-item-search"));
		organizer = new TsgHubOrganizer(this, client, clientThread, itemManager, executor, itemSearchExecutor, () -> panel);
		announcements = new TsgHubAnnouncements(this, executor, this::api, () -> newsEditor, this::clanAnnouncement);
		panel = new TsgHubPanel(this);
		newsEditor = new TsgHubNewsEditor(this);
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
		presence = new TsgHubPresence(this, client, clientThread, chatIconManager, spriteManager, executor, this::api, () -> sidebar, socket, this::adminKey);
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
		if (presence != null) presence.shutDown();
		if (memberMap != null) memberMap.setOpen(false);
		if (mapOverlay != null) overlayManager.remove(mapOverlay);
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
				hubWindow.setContentPane(new TsgHubAdminWindow(panel, organizer::loadManagedEvents, newsEditor, announcements::load));
				hubWindow.setMinimumSize(new Dimension(680, 480));
				hubWindow.setSize(860, 640);
				Window owner = SwingUtilities.getWindowAncestor(client.getCanvas());
				if (owner != null && owner.getIconImages() != null) hubWindow.setIconImages(owner.getIconImages());
				hubWindow.setLocationRelativeTo(owner);
			}
			hubWindow.setVisible(true);
			hubWindow.toFront();
			organizer.loadManagedEvents();
			syncSocketNow();
		});
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
		return TsgHubSession.get(ORGANIZER_EVENT_ID);
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
		executor.submit(() -> {
			try
			{
				String query = "/v1/me?displayName=" + TsgHubApi.encode(name);
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
		if ("shareLocation".equals(event.getKey()))
		{
			ui(s -> s.locationSharingChanged());
			return;
		}
		if ("hubKey".equals(event.getKey()))
		{
			checkedKeyIdentity = "";
			rejectedKey = "";
			keyCheckAttempts = 0;
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
			loadClanEvents();
			announcements.load();
			String eventId = TsgHubSession.get(EVENT_ID);
			if (!eventId.isEmpty() && !TsgHubSession.get(TOKEN).isEmpty() && TsgHubUi.samePlayer(TsgHubSession.memberName(eventId), detectedPlayerName))
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
				TsgHubSession.set(MEMBER_TOKEN + joinedEventId, memberToken);
				TsgHubSession.set(MEMBER_NAME + joinedEventId, memberName);
				setActiveEvent(joinedEventId, memberToken, memberName);
				memberStatus("You're in! Progress now counts for your team.", Tone.SUCCESS);
				refreshBoard(true);
			}
			catch (Exception e) { joinFailed(TsgHubUi.friendlyError(e)); }
		});
	}

	private void joinFailed(String message)
	{
		ui(s -> s.joinFailed(message));
	}

	void leaveEvent(String eventId)
	{
		String revokeToken = TsgHubSession.memberToken(eventId);
		if (!revokeToken.isEmpty()) executor.submit(() -> disconnect(eventId, revokeToken));
		TsgHubSession.clear(MEMBER_TOKEN + eventId, MEMBER_NAME + eventId);
		if (eventId.equals(TsgHubSession.get(EVENT_ID)))
		{
			TsgHubSession.clear(TOKEN, EVENT_ID);
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
		TsgHubSession.clear(TOKEN, EVENT_ID, ORGANIZER_EVENT_ID);
		TsgHubSession.removePrefix(MEMBER_TOKEN);
		TsgHubSession.removePrefix(MEMBER_NAME);
		TsgHubSession.removePrefix(ORGANIZER_TOKEN);
		TsgHubSession.removePrefix(ORGANIZER_NAME);
		syncedClanRanks.clear();
		checkedKeyIdentity = "";
		if (adminVerified) setAdminVerified(false);
	}

	private void revokeRemoteSession()
	{
		if (executor == null || executor.isShutdown()) return;
		Map<String, String> sessions = new HashMap<>();
		for (String key : TsgHubSession.keysWithPrefix(MEMBER_TOKEN))
		{
			String eventId = key.substring(MEMBER_TOKEN.length());
			sessions.put(eventId, TsgHubSession.get(key));
		}
		String activeEventId = TsgHubSession.get(EVENT_ID);
		String activeToken = TsgHubSession.get(TOKEN);
		if (!activeEventId.isEmpty() && !activeToken.isEmpty()) sessions.putIfAbsent(activeEventId, activeToken);
		if (!sessions.isEmpty()) executor.submit(() -> sessions.forEach(this::disconnect));
	}

	private void disconnect(String eventId, String token)
	{
		try { api().request("POST", "/v1/events/" + eventId + "/disconnect", token, new JsonObject()); }
		catch (Exception ignored) { /* Local disconnect still succeeds while the service is unavailable. */ }
	}

	void refreshBoard()
	{
		refreshBoard(false);
	}

	private void refreshBoard(boolean open)
	{
		if (!sharingInClan()) return;
		String eventId = TsgHubSession.get(EVENT_ID);
		String token = TsgHubSession.get(TOKEN);
		if (eventId.isEmpty() || token.isEmpty()) return;
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject event = api().request("GET", "/v1/events/" + eventId, token, null).getAsJsonObject("event");
				applyBoard(eventId, token, event, open);
			}
			catch (Exception e) { eventLoadFailed(eventId, e, "Couldn't update the board. "); }
			finally { sidebarBusy(false); }
		});
	}

	private void applyBoard(String eventId, String token, JsonObject event, boolean open)
	{
		syncClanRank(eventId, token, event);
		ui(s -> s.showBoard(event, TsgHubSession.get(DISPLAY_NAME), open));
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
		if (!sharingInClan())
		{
			socket.disconnect();
			return;
		}
		socket.connect(detectedClanName);
		Map<String, String> tokens = new HashMap<>();
		String eventId = TsgHubSession.get(EVENT_ID);
		String token = TsgHubSession.get(TOKEN);
		if (!eventId.isEmpty() && !token.isEmpty()) tokens.put(eventId, token);
		String competitionId = sidebar == null ? null : sidebar.openCompetitionId();
		String memberToken = competitionId == null ? "" : TsgHubSession.get(MEMBER_TOKEN + competitionId);
		if (!memberToken.isEmpty()) tokens.putIfAbsent(competitionId, memberToken);
		for (String joinedId : joinedCompetitions)
		{
			String joinedToken = TsgHubSession.get(MEMBER_TOKEN + joinedId);
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
		if (!socket.isLive()) pollTeamNotifications();
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
		String eventId = TsgHubSession.get(EVENT_ID);
		if (sidebar.wantsAutoRefresh() && !eventId.isEmpty()) topics.add("event:" + eventId);
		if (competitionId != null) topics.add("event:" + competitionId);
		return topics;
	}

	private void snapshot(JsonObject message)
	{
		String eventId = TsgHubUi.str(message, "eventId");
		JsonObject data = message.has("data") && message.get("data").isJsonObject() ? message.getAsJsonObject("data") : new JsonObject();
		switch (TsgHubUi.str(message, "topic"))
		{
			case "groups":
				groups.apply(TsgHubUi.array(data, "groups"));
				break;
			case "presence":
				JsonObject roster = data.has("presence") && data.get("presence").isJsonObject() ? data.getAsJsonObject("presence") : null;
				JsonObject notes = data.has("members") && data.get("members").isJsonObject() ? data.getAsJsonObject("members") : null;
				if (roster != null) presence.apply(TsgHubUi.array(roster, "members"), notes == null ? null : TsgHubUi.array(notes, "members"));
				break;
			case "positions":
				memberMap.apply(TsgHubUi.array(data, "members"));
				break;
			case "drops":
				drops.onSnapshot(TsgHubUi.array(data, "drops"));
				break;
			case "announcements":
				announcements.apply(TsgHubUi.array(data, "announcements"));
				break;
			case "events":
				applyClanEvents(TsgHubUi.array(data, "events"));
				if (adminWindowOpen()) organizer.loadManagedEvents();
				break;
			case "notifications":
				if (eventId.equals(TsgHubSession.get(EVENT_ID))) showNotifications(TsgHubUi.array(data, "notifications"));
				break;
			case "event":
				if (!data.has("event") || !data.get("event").isJsonObject()) break;
				JsonObject event = data.getAsJsonObject("event");
				if (adminWindowOpen() && eventId.equals(getOrganizerEventId())) organizer.liveRefresh();
				if (sidebar == null) break;
				if (eventId.equals(TsgHubSession.get(EVENT_ID)) && sidebar.wantsAutoRefresh()) applyBoard(eventId, TsgHubSession.get(TOKEN), event, false);
				if (eventId.equals(sidebar.openCompetitionId()))
				{
					String name = TsgHubSession.get(MEMBER_NAME + eventId);
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
			case "announcements":
				announcements.load();
				break;
			case "notifications":
				if (eventId.equals(TsgHubSession.get(EVENT_ID))) pollTeamNotifications();
				break;
			case "event":
				if (adminWindowOpen() && eventId.equals(getOrganizerEventId())) organizer.liveRefresh();
				if (sidebar == null) break;
				if (eventId.equals(TsgHubSession.get(EVENT_ID)) && sidebar.wantsAutoRefresh()) refreshBoard();
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
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/participate", adminKey(), body);
				TsgHubSession.set(MEMBER_TOKEN + eventId, result.get("token").getAsString());
				TsgHubSession.set(MEMBER_NAME + eventId, result.getAsJsonObject("member").get("displayName").getAsString());
				syncSocketNow();
				memberStatus("You're in! Your progress counts from now.", Tone.SUCCESS);
				loadClanEvents();
				openCompetition(eventId, true);
			}
			catch (Exception e) { ui(s -> s.competitionJoinFailed(TsgHubUi.friendlyError(e))); }
			finally { sidebarBusy(false); }
		});
	}

	void openCompetition(String eventId)
	{
		openCompetition(eventId, false);
	}

	void openCompetition(String eventId, boolean open)
	{
		String token = TsgHubSession.get(MEMBER_TOKEN + eventId);
		if (token.isEmpty()) return;
		String name = TsgHubSession.get(MEMBER_NAME + eventId);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject event = api().request("GET", "/v1/events/" + eventId, token, null).getAsJsonObject("event");
				ui(s -> s.showCompetition(event, name, open));
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
			catch (Exception e) { memberError("", e); }
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
		for (String key : TsgHubSession.keysWithPrefix(MEMBER_TOKEN)) memberTokens.add(TsgHubSession.get(key));
		if (!TsgHubSession.get(TOKEN).isEmpty()) memberTokens.add(TsgHubSession.get(TOKEN));
		for (String key : TsgHubSession.keysWithPrefix(ORGANIZER_TOKEN)) organizerTokens.add(TsgHubSession.get(key));
		for (String key : TsgHubSession.keysWithPrefix(MEMBER_NAME)) renameStored(key, name);
		for (String key : TsgHubSession.keysWithPrefix(ORGANIZER_NAME)) renameStored(key, name);
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
				if (TsgHubUi.bool(result, "updated") && !TsgHubSession.get(EVENT_ID).isEmpty()) refreshBoard();
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
		if (!sharingInClan()) return;
		String clanName = detectedClanName;
		if (clanName == null || clanName.trim().isEmpty())
		{
			ui(s -> s.setEvents(new JsonArray()));
			return;
		}
		String encodedClan = TsgHubApi.encode(clanName);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				applyClanEvents(api().request("GET", "/v1/events?clanName=" + encodedClan, adminKey(), null).getAsJsonArray("events"));
			}
			catch (Exception e) { memberError("Couldn't load events. ", e); }
			finally { sidebarBusy(false); }
		});
	}

	private void applyClanEvents(JsonArray events)
	{
		if (events == null) return;
		for (JsonObject event : TsgHubUi.objects(events))
		{
			String eventId = event.get("id").getAsString();
			String savedToken = TsgHubSession.get(MEMBER_TOKEN + eventId);
			if (savedToken.isEmpty() && eventId.equals(TsgHubSession.get(EVENT_ID)))
			{
				savedToken = TsgHubSession.get(TOKEN);
				if (!savedToken.isEmpty())
				{
					TsgHubSession.set(MEMBER_TOKEN + eventId, savedToken);
					TsgHubSession.set(MEMBER_NAME + eventId, TsgHubSession.get(DISPLAY_NAME));
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
		String token = TsgHubSession.memberToken(eventId);
		if (token.isEmpty())
		{
			memberStatus("Join this event with your team code first.", Tone.ERROR);
			return;
		}
		setActiveEvent(eventId, token, TsgHubSession.memberName(eventId));
		refreshBoard(open);
	}

	private void setActiveEvent(String eventId, String token, String displayName)
	{
		TsgHubSession.set(TOKEN, token);
		TsgHubSession.set(EVENT_ID, eventId);
		TsgHubSession.set(DISPLAY_NAME, displayName);
		syncSocketNow();
	}

	private void syncClanRank(String eventId, String token, JsonObject event)
	{
		String displayName = detectedPlayerName;
		String memberName = TsgHubSession.get(DISPLAY_NAME);
		String eventClan = TsgHubUi.str(event, "clanName");
		if (token.isEmpty() || displayName.isEmpty() || !TsgHubUi.samePlayer(displayName, memberName)
			|| eventClan.isEmpty() || !eventClan.equalsIgnoreCase(detectedClanName)) return;
		int rank = detectedClanRank;
		String key = eventId + ":" + PlayerNames.normalize(displayName);
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

	void forgetEvent(String eventId)
	{
		TsgHubSession.clear(ORGANIZER_TOKEN + eventId, ORGANIZER_NAME + eventId, MEMBER_TOKEN + eventId, MEMBER_NAME + eventId);
		if (eventId.equals(TsgHubSession.get(ORGANIZER_EVENT_ID))) TsgHubSession.clear(ORGANIZER_EVENT_ID);
		if (eventId.equals(TsgHubSession.get(EVENT_ID)))
		{
			TsgHubSession.clear(TOKEN, EVENT_ID);
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
		try { TsgHubRules.save(api().request("GET", "/v1/rules", null, null), rulesCache); }
		catch (Exception ignored) { }
	}

	private void loadCachedRules()
	{
		try { rulesCache = getPluginDirectory().join("rules.json"); }
		catch (Exception e) { return; }
		TsgHubRules.loadCached(rulesCache);
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

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WORLDMAP && memberMap != null)
		{
			memberMap.setOpen(true);
			syncSocketNow();
			if (!socket.isLive()) memberMap.autoRefresh();
		}
		if (sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(event.getGroupId())) socket.rewardInterface(event.getGroupId(), true, client.getTickCount());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.WORLDMAP && memberMap != null)
		{
			memberMap.setOpen(false);
			syncSocketNow();
		}
		if (sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(event.getGroupId())) socket.rewardInterface(event.getGroupId(), false, client.getTickCount());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!isInHubClan()) return;
		if (event.getComposition() == null) return;
		String name = Text.removeTags(event.getComposition().getName());
		if (drops != null) drops.onLoot(name);
		processLoot("NPC", name, event.getItems(), 1);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isInHubClan()) return;
		if (event.getType() == null || "PLAYER".equals(event.getType().name()) || "NPC".equals(event.getType().name())) return;
		if (drops != null) drops.onLoot(event.getName());
		processLoot(event.getType().name(), event.getName(), event.getItems(), event.getAmount());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isInHubClan()) return;
		if (event == null || event.getMessage() == null || client.getLocalPlayer() == null) return;
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
		if (!signals.wantsChat(message)) return;
		JsonObject chat = new JsonObject();
		chat.addProperty("text", message);
		if (signals.wantsPlayers(message)) addNearbyPlayers(chat);
		signals.send("chat", chat);
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
				if (visiblePlayers.stream().noneMatch(existing -> TsgHubUi.samePlayer(existing, name))) visiblePlayers.add(name);
				if (!player.isClanMember() && nonClanPlayers.stream().noneMatch(existing -> TsgHubUi.samePlayer(existing, name))) nonClanPlayers.add(name);
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

	private static JsonObject claim(String taskId, String evidenceId, String source, JsonObject evidence)
	{
		JsonObject claim = new JsonObject();
		claim.addProperty("taskId", taskId);
		claim.addProperty("evidenceId", evidenceId);
		claim.addProperty("source", source);
		claim.add("evidence", evidence);
		return claim;
	}

	private static List<String> joinedCompetitionIds(JsonArray events)
	{
		List<String> ids = new ArrayList<>();
		for (JsonObject event : TsgHubUi.objects(events))
		{
			String type = TsgHubUi.str(event, "type");
			if (!("skill".equals(type) || "boss".equals(type)) || "ended".equals(TsgHubUi.str(event, "status"))) continue;
			String id = TsgHubUi.str(event, "id");
			if (!TsgHubSession.get(MEMBER_TOKEN + id).isEmpty()) ids.add(id);
		}
		return ids;
	}

	private void signalReply(JsonObject message)
	{
		String eventId = TsgHubUi.str(message, "eventId");
		switch (TsgHubUi.str(message, "type"))
		{
			case "progress":
				try
				{
					long gained = Long.parseLong(TsgHubUi.str(message, "gained"));
					String rank = TsgHubUi.str(message, "rank");
					Long parsedRank = rank.isEmpty() ? null : Long.parseLong(rank);
					ui(s -> s.competitionProgress(eventId, gained, parsedRank));
				}
				catch (NumberFormatException ignored) { /* Malformed progress waits for the next report. */ }
				break;
			case "claimed":
				showProgressMessage(TsgHubUi.str(message, "taskTitle"), TsgHubUi.str(message, "progress"), TsgHubUi.str(message, "complete"));
				if (!socket.isLive() && eventId.equals(TsgHubSession.get(EVENT_ID))) refreshBoard();
				break;
			case "notice":
				String text = TsgHubUi.str(message, "text");
				if (!text.isEmpty()) memberStatus(text, "error".equals(TsgHubUi.str(message, "tone")) ? Tone.ERROR : Tone.INFO);
				break;
			default:
		}
	}

	private void pollTeamNotifications()
	{
		if (!sharingInClan() || executor == null || executor.isShutdown()) return;
		String token = TsgHubSession.get(TOKEN);
		String eventId = TsgHubSession.get(EVENT_ID);
		if (token.isEmpty() || eventId.isEmpty()) return;
		try
		{
			String path = "/v1/events/" + eventId + "/notifications?inClanChat=" + inClanChat + "&clanName=" + TsgHubApi.encode(detectedClanName);
			if (showNotifications(api().request("GET", path, token, null).getAsJsonArray("notifications"))) refreshBoard();
		}
		catch (Exception ignored) { /* Notifications retry on the next scheduled poll. */ }
	}

	private boolean showNotifications(JsonArray notifications)
	{
		if (notifications == null) return false;
		for (JsonObject notification : TsgHubUi.objects(notifications))
		{
			if (!"task-completed".equals(TsgHubUi.str(notification, "type"))) continue;
			showLocalChatMessage(new ChatMessageBuilder()
				.append(ChatColorType.NORMAL).append(TsgHubUi.str(notification, "actorName") + " completed ")
				.append(ChatColorType.HIGHLIGHT).append(TsgHubUi.str(notification, "taskTitle"))
				.append(ChatColorType.NORMAL).append(" for " + TsgHubUi.str(notification, "teamName") + "!"));
		}
		return notifications.size() > 0;
	}

	private void showProgressMessage(String title, String count, String complete)
	{
		if (title.isEmpty() || count.isEmpty()) return;
		ChatMessageBuilder message = new ChatMessageBuilder();
		if (!complete.isEmpty())
		{
			message.append(TsgHubTheme.SUCCESS.darker(), "task".equals(complete) ? "Task complete: " : "Your part is done: ")
				.append(ChatColorType.HIGHLIGHT).append(title)
				.append(ChatColorType.NORMAL).append(" (" + count + ")");
		}
		else message.append(ChatColorType.HIGHLIGHT).append(title).append(ChatColorType.NORMAL).append(": " + count);
		showLocalChatMessage(message);
	}

	private void announce(String text)
	{
		if (!config.eventAnnouncements()) return;
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
	}

	private void clanAnnouncement(String text)
	{
		if (!config.clanAnnouncements()) return;
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append(Text.escapeJagex(text)));
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
		String message = new ChatMessageBuilder().append(TsgHubTheme.ACCENT.darker(), "[TSG Hub] ").build() + body.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
	}

	private void memberStatus(String message, Tone tone)
	{
		ui(s -> s.setStatus(message, tone));
	}

	private void memberError(String failure, Exception e)
	{
		memberStatus(failure + TsgHubUi.friendlyError(e), Tone.ERROR);
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
