package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.util.Set;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.Window;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.ItemContainer;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.client.input.MouseManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import com.tsghub.TsgHubUi.Tone;

@PluginDescriptor(name = "TSG Hub", description = "Type Shiii Gaming clan events and progress tracking", tags = {"tsg", "clan", "bingo", "events"})
public class TsgHubPlugin extends Plugin
{
	// Dev mode only; must match the server's TEST_MANAGER_NAMES.
	private static final java.util.Set<String> DEV_TEST_MANAGER_NAMES = parseNames(System.getProperty("tsghub.testManagers", ""));
	private static final String SERVICE_URL = "https://tsg-api.tylerhammer.com";
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
	@Inject private okhttp3.OkHttpClient okHttpClient;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private net.runelite.client.game.ChatIconManager chatIconManager;
	@Inject private net.runelite.client.party.PartyService partyService;
	@Inject private net.runelite.client.party.WSClient wsClient;
	@Inject private net.runelite.client.game.SpriteManager spriteManager;
	@Inject private net.runelite.client.eventbus.EventBus eventBus;
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
	private static final long CLAN_ADMIN_TOKEN_MARGIN_MILLIS = 60_000;
	private static final int PET_CHECK_TICKS = 5;
	private static final int MAX_LOOT_LOG_ITEMS = 50;
	private static final int CLAN_CHECK_TICKS = 50;
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
	private volatile boolean routedAsHubMember;
	private volatile boolean routedClanPending;
	private volatile String hubClanName = "TSGaming";
	private volatile List<PvmTask> pvmTasks = Collections.emptyList();
	private volatile String taskEventId = "";
	private volatile TsgHubApi api;
	private TsgHubCompetitionTracker competitions;
	private TsgHubGroups groups;
	private TsgHubPresence presence;
	private com.tsghub.group.GroupTracker groupTracker;

	@Override
	protected void startUp()
	{
		TsgHubSession.init(configManager);
		String clanOverride = System.getProperty("tsghub.clanName", "").trim();
		hubClanName = developerMode && !clanOverride.isEmpty() ? clanOverride : "TSGaming";
		String serviceOverride = System.getProperty("tsghub.serviceUrl", "").trim();
		api = new TsgHubApi(okHttpClient, developerMode && !serviceOverride.isEmpty() ? serviceOverride : SERVICE_URL);
		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, "tsg-hub-api");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleAtFixedRate(this::pollTeamNotifications, 10, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(this::autoRefreshBoard, BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		competitions = new TsgHubCompetitionTracker(this::api, executor, client, clientThread);
		itemSearchExecutor = Executors.newFixedThreadPool(2, r -> {
			Thread thread = new Thread(r, "tsg-hub-item-search");
			thread.setDaemon(true);
			return thread;
		});
		panel = new TsgHubPanel(this);
		sidebar = new TsgHubSidebarPanel(this, new com.tsghub.group.GroupMembersPanel(new com.tsghub.group.GroupViewSettings()
		{
			@Override public boolean autoExpandMembers() { return config.partyExpandMembers(); }
			@Override public boolean displayVirtualLevels() { return config.partyVirtualLevels(); }
			@Override public boolean displayPlayerWorlds() { return config.partyShowWorlds(); }
		}, spriteManager, itemManager));
		groups = new TsgHubGroups(this, client, partyService, executor, this::api, () -> sidebar);
		groupTracker = new com.tsghub.group.GroupTracker(client, clientThread, partyService, wsClient, itemManager, groups::isGroupParty, config::partyShowSelf, this::currentArea, groups);
		groups.setTracker(groupTracker);
		eventBus.register(groupTracker);
		groupTracker.start();
		executor.scheduleAtFixedRate(groups::heartbeat, 5, TsgHubGroups.HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(groups::autoRefresh, TsgHubGroups.REFRESH_SECONDS, TsgHubGroups.REFRESH_SECONDS, TimeUnit.SECONDS);
		presence = new TsgHubPresence(this, client, clientThread, chatIconManager, executor, this::api, () -> sidebar);
		executor.scheduleAtFixedRate(presence::autoRefresh, TsgHubPresence.REFRESH_SECONDS, TsgHubPresence.REFRESH_SECONDS, TimeUnit.SECONDS);
		boardOverlay = new TsgHubBoardOverlay(client);
		overlayManager.add(boardOverlay);
		mouseManager.registerMouseListener(boardOverlay);
		navigationButton = NavigationButton.builder()
			.tooltip("TSG Hub")
			.icon(createIcon())
			.priority(5)
			.panel(sidebar)
			.build();
		clientToolbar.addNavigation(navigationButton);
		sidebarRouted = false;
		clientThread.invokeLater(this::refreshDetectedClan);
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
		if (executor != null) executor.shutdownNow();
		if (itemSearchExecutor != null) itemSearchExecutor.shutdownNow();
	}

	void openWorkspace()
	{
		if (!canManageOrganizerUi())
		{
			memberStatus("Admin tools are for the event creator and Clan Administrators or higher.", Tone.ERROR);
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
				Window owner = javax.swing.SwingUtilities.getWindowAncestor(client.getCanvas());
				if (owner != null && owner.getIconImages() != null) hubWindow.setIconImages(owner.getIconImages());
				hubWindow.setLocationRelativeTo(owner);
			}
			hubWindow.setVisible(true);
			hubWindow.toFront();
			loadManagedEvents();
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

	private BufferedImage createIcon()
	{
		return ImageUtil.loadImageResource(getClass(), "icon.png");
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

	String currentArea() { return presence == null ? "" : presence.area(); }
	boolean inClanChat() { return inClanChat; }
	String getDetectedPlayerName() { return detectedPlayerName; }
	int getDetectedClanRank() { return detectedClanRank; }
	String getCurrentEventId() { return TsgHubSession.get("eventId"); }
	AsyncBufferedImage getItemImage(int itemId) { return itemManager == null ? null : itemManager.getImage(itemId); }

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
		return !detectedClanName.isEmpty() && normalizePlayerName(detectedClanName).equals(normalizePlayerName(hubClanName));
	}

	boolean canManageOrganizerUi()
	{
		if (!isInHubClan()) return false;
		return detectedClanRank >= ClanRank.ADMINISTRATOR.getRank() || isDevTestManager();
	}

	private boolean isDevTestManager()
	{
		return developerMode && DEV_TEST_MANAGER_NAMES.contains(normalizePlayerName(detectedPlayerName));
	}

	private static java.util.Set<String> parseNames(String csv)
	{
		java.util.Set<String> names = new java.util.HashSet<>();
		for (String name : csv.split(","))
		{
			String normalized = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
			if (!normalized.isEmpty()) names.add(normalized);
		}
		return names;
	}

	private String normalizePlayerName(String name)
	{
		return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	@net.runelite.client.eventbus.Subscribe
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
		detectedPlayerName = "";
		detectedClanName = "";
		detectedClanRank = -1;
		inClanChat = false;
		clanCheckTicks = 0;
		sidebarRouted = false;
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

	@net.runelite.client.eventbus.Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		String previousClan = detectedClanName;
		refreshDetectedClan();
		if (config.dataSharingOptIn() && sidebarRouted && !detectedClanName.equals(previousClan)) loadClanEvents();
	}

	@net.runelite.client.eventbus.Subscribe
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
	}

	@net.runelite.client.eventbus.Subscribe
	public void onRuneScapeProfileChanged(net.runelite.client.events.RuneScapeProfileChanged event)
	{
		attemptedXpClaims.clear();
		syncedClanRanks.clear();
		syncedIdentity = "";
		if (competitions != null) competitions.clear();
		clearTaskCache();
		if (boardOverlay != null) boardOverlay.setVisible(false);
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
		if (loggedIn && !pending && isInHubClan() && config.dataSharingOptIn()) syncIdentity();
		SwingUtilities.invokeLater(() -> {
			if (!loggedIn) { sidebar.showLoggedOut(); return; }
			if (pending) { sidebar.showCheckingClan(); return; }
			if (!isInHubClan()) { sidebar.showNotInClan(hubClanName, detectedClanName); return; }
			if (!config.dataSharingOptIn()) { sidebar.showSharingOff(); return; }
			sidebar.showHome();
			loadClanEvents();
			String eventId = TsgHubSession.get("eventId");
			String memberName = TsgHubSession.get("memberName:" + eventId);
			if (memberName.isEmpty()) memberName = TsgHubSession.get("displayName");
			if (!eventId.isEmpty() && !TsgHubSession.get("token").isEmpty()
				&& normalizePlayerName(memberName).equals(normalizePlayerName(detectedPlayerName)))
				activateEvent(eventId);
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
		JsonObject payload = new JsonObject();
		payload.addProperty("code", code.trim());
		if (expectedEventId != null && !expectedEventId.trim().isEmpty()) payload.addProperty("eventId", expectedEventId.trim());
		payload.addProperty("displayName", client.getLocalPlayer().getName());
		payload.addProperty("clanName", detectedClanName);
		payload.addProperty("clanRank", detectedClanRank);
		addAccountHash(payload);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/join", null, payload);
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
		String token = TsgHubSession.get("memberToken:" + eventId);
		if (token.isEmpty() && eventId.equals(TsgHubSession.get("eventId"))) token = TsgHubSession.get("token");
		String revokeToken = token;
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
		TsgHubSession.removePrefix("clanAdminToken:");
		TsgHubSession.removePrefix("clanAdminExpiresAt:");
		TsgHubSession.removePrefix("organizerToken:");
		TsgHubSession.removePrefix("organizerName:");
		attemptedXpClaims.clear();
		syncedClanRanks.clear();
		clearTaskCache();
		if (boardOverlay != null) boardOverlay.setVisible(false);
	}

	private void revokeRemoteSession()
	{
		if (executor == null || executor.isShutdown()) return;
		java.util.Map<String, String> sessions = new java.util.HashMap<>();
		List<String> organizerSessions = new ArrayList<>();
		for (String key : TsgHubSession.keysWithPrefix("clanAdminToken:")) organizerSessions.add(TsgHubSession.get(key));
		for (String key : TsgHubSession.keysWithPrefix("memberToken:"))
		{
			String eventId = key.substring("memberToken:".length());
			sessions.put(eventId, TsgHubSession.get(key));
		}
		String activeEventId = TsgHubSession.get("eventId");
		String activeToken = TsgHubSession.get("token");
		if (!activeEventId.isEmpty() && !activeToken.isEmpty()) sessions.putIfAbsent(activeEventId, activeToken);
		if (sessions.isEmpty() && organizerSessions.isEmpty()) return;
		executor.submit(() -> {
			for (java.util.Map.Entry<String, String> session : sessions.entrySet())
			{
				try { api().request("POST", "/v1/events/" + session.getKey() + "/disconnect", session.getValue(), new JsonObject()); }
				catch (Exception e) { /* Local disconnect still succeeds while the service is unavailable. */ }
			}
			for (String token : organizerSessions)
			{
				try { api().request("POST", "/v1/organizer-session/disconnect", token, new JsonObject()); }
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
			catch (Exception e)
			{
				String message = e.getMessage() == null ? "" : e.getMessage();
				if (message.startsWith("Event not found") || message.startsWith("Connect to this event first"))
				{
					// Event or team was deleted: forget it instead of failing every refresh.
					forgetEvent(eventId);
					memberStatus("That event is no longer available.", Tone.ERROR);
					loadClanEvents();
				}
				else memberStatus("Couldn't update the board. " + TsgHubUi.friendlyError(e), Tone.ERROR);
			}
			finally { sidebarBusy(false); }
		});
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
		JsonObject body = new JsonObject();
		body.addProperty("displayName", detectedPlayerName);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("clanRank", detectedClanRank);
		addAccountHash(body);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/participate", null, body);
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

	private void openCompetition(String eventId, boolean open)
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
			catch (Exception e)
			{
				String message = e.getMessage() == null ? "" : e.getMessage();
				if (message.startsWith("Event not found") || message.startsWith("Connect to this event first"))
				{
					forgetEvent(eventId);
					memberStatus("That event is no longer available.", Tone.ERROR);
					loadClanEvents();
				}
				else memberStatus("Couldn't load the leaderboard. " + TsgHubUi.friendlyError(e), Tone.ERROR);
			}
			finally { sidebarBusy(false); }
		});
	}

	private String accountHash()
	{
		long hash = client.getAccountHash();
		return hash == -1 ? "" : Long.toString(hash);
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
		if (!stored.isEmpty() && !normalizePlayerName(stored).equals(normalizePlayerName(name))) TsgHubSession.set(key, name);
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
		String encodedClan;
		try { encodedClan = java.net.URLEncoder.encode(clanName, java.nio.charset.StandardCharsets.UTF_8.name()); }
		catch (java.io.UnsupportedEncodingException e) { return; }
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				String credential = null;
				if (canUseClanAdminSession())
				{
					try { credential = ensureOrganizerListCredential(); }
					catch (Exception ignored) { credential = null; }
				}
				JsonObject response = api().request("GET", "/v1/events?clanName=" + encodedClan, credential, null);
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
		String token = TsgHubSession.get("memberToken:" + eventId);
		if (token.isEmpty() && eventId.equals(TsgHubSession.get("eventId"))) token = TsgHubSession.get("token");
		if (token.isEmpty())
		{
			memberStatus("Join this event with your team code first.", Tone.ERROR);
			return;
		}
		String displayName = TsgHubSession.get("memberName:" + eventId);
		if (displayName.isEmpty() && eventId.equals(TsgHubSession.get("eventId"))) displayName = TsgHubSession.get("displayName");
		clearTaskCache();
		TsgHubSession.set("token", token);
		TsgHubSession.set("eventId", eventId);
		TsgHubSession.set("displayName", displayName);
		attemptedXpClaims.clear();
		refreshBoard(true);
	}

	private void syncClanRank(String eventId, String token, JsonObject event)
	{
		String displayName = detectedPlayerName;
		String memberName = TsgHubSession.get("displayName");
		String eventClan = event.has("clanName") && !event.get("clanName").isJsonNull() ? event.get("clanName").getAsString() : "";
		if (token.isEmpty() || displayName.isEmpty() || !displayName.equalsIgnoreCase(memberName)
			|| eventClan.isEmpty() || !eventClan.equalsIgnoreCase(detectedClanName)) return;
		int rank = detectedClanRank;
		String key = eventId + ":" + normalizePlayerName(displayName);
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
		body.addProperty("evidenceId", "manual-" + java.util.UUID.randomUUID());
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
		organizerStatus("Loading event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				String credential = canUseClanAdminSession() ? ensureOrganizerListCredential() : organizerCredential(eventId.trim());
				JsonObject event = api().request("GET", "/v1/events/" + eventId.trim() + "/organizer", credential, null);
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
				String credential = ensureOrganizerListCredential();
				if (credential.isEmpty()) throw new IllegalStateException("Join or create an event first");
				JsonObject response = api().request("GET", "/v1/managed-events", credential, null);
				SwingUtilities.invokeLater(() -> panel.setManagedEvents(response.getAsJsonArray("events")));
			}
			catch (Exception e) { organizerStatus("Couldn't load events. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
		});
	}

	void createEvent(String name, String startDate, String endDate, boolean hideScores, boolean hidden, String type, JsonObject typeConfig)
	{
		if (!isInHubClan()) { eventFormFailed("TSG Hub is only for members of the " + hubClanName + " clan."); return; }
		if (!config.dataSharingOptIn()) { eventFormFailed("Turn on sharing in the TSG Hub sidebar first."); return; }
		if (detectedClanRank < ClanRank.ADMINISTRATOR.getRank() && !isDevTestManager())
		{
			eventFormFailed("Creating events requires Clan Administrator rank or higher.");
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
		body.addProperty("startDate", startDate.trim());
		body.addProperty("endDate", endDate.trim());
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		body.addProperty("type", type);
		addAccountHash(body);
		body.add("config", typeConfig == null ? new JsonObject() : typeConfig);
		organizerStatus("Creating event...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				JsonObject response = api().request("POST", "/v1/events", null, body);
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

	void updateEvent(String eventId, String name, String startDate, String endDate, boolean hideScores, boolean hidden, JsonObject typeConfig)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name.trim());
		body.addProperty("startDate", startDate.trim());
		body.addProperty("endDate", endDate.trim());
		body.addProperty("hideScores", hideScores);
		body.addProperty("hidden", hidden);
		if (typeConfig != null) body.add("config", typeConfig);
		String credential = organizerCredential(eventId);
		organizerStatus("Saving...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				api().request("PATCH", "/v1/events/" + eventId, credential, body);
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
		body.addProperty("startDate", TsgHubUi.str(event, "startDate"));
		body.addProperty("endDate", TsgHubUi.str(event, "endDate"));
		body.addProperty("hidden", false);
		String credential = organizerCredential(eventId);
		organizerStatus("Publishing...", Tone.INFO);
		executor.submit(() -> {
			try
			{
				api().request("PATCH", "/v1/events/" + eventId, credential, body);
				organizerStatus("Event published. Players can see it now.", Tone.SUCCESS);
				refreshOrganizerEvent(true);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { organizerStatus("Couldn't publish. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
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
				for (String mode : targetNames) modes.add(mode.toLowerCase(java.util.Locale.ROOT));
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
					Map<Integer, JsonArray> groupedItems = new java.util.TreeMap<>();
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
				api().request("DELETE", "/v1/events/" + eventId, credential, null);
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
			SwingUtilities.invokeLater(() -> sidebar.showEventList());
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
				JsonObject event = api().request("GET", "/v1/events/" + eventId + "/organizer", organizerCredential(eventId), null);
				SwingUtilities.invokeLater(() -> {
					if (open) panel.openOrganizerEvent(event);
					else panel.showEvent(event);
				});
			}
			catch (Exception e) { organizerStatus("Couldn't refresh the event. " + TsgHubUi.friendlyError(e), Tone.ERROR); }
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

	@net.runelite.client.eventbus.Subscribe
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

	@net.runelite.client.eventbus.Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!isInHubClan()) return;
		if (event.getNpc() == null) return;
		if (config.dataSharingOptIn() && competitions != null) competitions.onNpcLoot(event.getNpc().getName(), client.getTickCount());
		processLoot("NPC", event.getNpc().getName(), event.getItems(), 1);
	}

	@net.runelite.client.eventbus.Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isInHubClan()) return;
		if (event.getType() == null || "PLAYER".equals(event.getType().name())) return;
		processLoot(event.getType().name(), event.getName(), event.getItems(), event.getAmount());
	}

	@net.runelite.client.eventbus.Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isInHubClan()) return;
		if (event == null || event.getMessage() == null || client.getLocalPlayer() == null) return;
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
		String message = event.getMessage().replaceAll("<[^>]*>", "").toLowerCase(java.util.Locale.ROOT);
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

	@net.runelite.client.eventbus.Subscribe
	public void onGameTick(GameTick tick)
	{
		if (presence != null) presence.onGameTick();
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
		Set<Integer> pets = new java.util.HashSet<>();
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
		String normalized = name.replaceAll("<[^>]*>", "").trim().toLowerCase(java.util.Locale.ROOT);
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
				if (!message.contains(itemName.toLowerCase(java.util.Locale.ROOT))) continue;
				int itemId = i < task.targetItemIds.size() ? task.targetItemIds.get(i) : 0;
				submitPvmClaim(eventId, task, "drop", itemName, 1, itemId);
			}
		}
	}

	private void submitKillCountSignals(String eventId, String message)
	{
		if (!message.contains("kill count") && !message.contains("kill-count")) return;
		java.util.regex.Matcher countMatch = TsgHubCompetitionTracker.KILL_COUNT.matcher(message);
		if (!countMatch.find()) return;
		int count;
		try { count = Integer.parseInt(countMatch.group(1).replace(",", "")); }
		catch (NumberFormatException e) { return; }
		for (PvmTask task : pvmTasks)
		{
			if (!"kill".equals(task.type) || !task.chatKillCount || task.targetNames.isEmpty()) continue;
			String bossName = task.targetNames.get(0);
			if (!message.contains(bossName.toLowerCase(java.util.Locale.ROOT))) continue;
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
					JsonObject result = api().request("POST", "/v1/events/" + eventId + "/claims", claimToken(eventId), claim);
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
		claim.addProperty("evidenceId", "raid-" + java.util.UUID.randomUUID());
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
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/claims", claimToken(eventId), claim);
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
		String lootId = "loot-" + java.util.UUID.randomUUID();
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
						&& itemName.toLowerCase(java.util.Locale.ROOT).startsWith("jar of ")
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
			try { api().request("POST", "/v1/events/" + eventId + "/loot", claimToken(eventId), loot); }
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
		String key = tick + ":" + sourceType + ":" + sourceName.toLowerCase(java.util.Locale.ROOT) + ":" + String.join(",", stacks);
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
		if (eventId.isEmpty() || !eventId.equals(taskEventId) || claimToken(eventId).isEmpty()) return "";
		return eventId;
	}

	private String claimToken(String eventId)
	{
		String token = TsgHubSession.get("memberToken:" + eventId);
		if (token.isEmpty() && eventId.equals(TsgHubSession.get("eventId"))) token = TsgHubSession.get("token");
		return token;
	}

	private void submitPvmClaim(String eventId, PvmTask task, String source, String name, int quantity, int itemId)
	{
		submitPvmClaim(eventId, task, source, name, quantity, itemId, null);
	}

	private void submitPvmClaim(String eventId, PvmTask task, String source, String name, int quantity, int itemId, String lootId)
	{
		JsonObject claim = new JsonObject();
		claim.addProperty("taskId", task.id);
		claim.addProperty("evidenceId", source + "-" + java.util.UUID.randomUUID());
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
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/claims", claimToken(eventId), claim);
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
			String clan = java.net.URLEncoder.encode(detectedClanName, java.nio.charset.StandardCharsets.UTF_8.name());
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
		try { showProgressMessage(api().request("POST", "/v1/events/" + eventId + "/claims", claimToken(eventId), claim)); }
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
				for (int j = 0; j < modes.size(); j++) targetNames.add(modes.get(j).getAsString().toLowerCase(java.util.Locale.ROOT));
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
				JsonObject result = api().request(method, "/v1/events/" + eventId + suffix, credential, payload);
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

	private String organizerCredential(String eventId)
	{
		String ownerToken = TsgHubSession.get("organizerToken:" + eventId);
		String creatorName = TsgHubSession.get("organizerName:" + eventId);
		if (!ownerToken.isEmpty() && !creatorName.isEmpty() && creatorName.equalsIgnoreCase(detectedPlayerName)) return ownerToken;
		String adminToken = savedClanAdminToken();
		if (!adminToken.isEmpty()) return adminToken;
		return TsgHubSession.get("token");
	}

	private String organizerListCredential()
	{
		String adminToken = savedClanAdminToken();
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

	private String ensureOrganizerListCredential() throws Exception
	{
		String existing = organizerListCredential();
		if (!canUseClanAdminSession()) return existing;
		String clanKey = normalizePlayerName(detectedClanName);
		if (clanKey.isEmpty()) return existing;
		String tokenKey = "clanAdminToken:" + clanKey;
		String expiresKey = "clanAdminExpiresAt:" + clanKey;
		String previousToken = TsgHubSession.get(tokenKey);
		if (!previousToken.isEmpty() && clanAdminExpiresAt(clanKey) > System.currentTimeMillis() + CLAN_ADMIN_TOKEN_MARGIN_MILLIS) return previousToken;
		if (!previousToken.isEmpty())
		{
			try { api().request("POST", "/v1/organizer-session/disconnect", previousToken, new JsonObject()); }
			catch (Exception ignored) { /* A restarted service has already forgotten the previous temporary token. */ }
		}
		JsonObject body = new JsonObject();
		body.addProperty("displayName", detectedPlayerName);
		body.addProperty("clanName", detectedClanName);
		body.addProperty("clanRank", detectedClanRank);
		JsonObject session = api().request("POST", "/v1/organizer-session", null, body);
		String token = session.get("token").getAsString();
		long validUntil = java.time.Instant.parse(session.get("expiresAt").getAsString()).toEpochMilli();
		TsgHubSession.set(tokenKey, token);
		TsgHubSession.set(expiresKey, Long.toString(validUntil));
		return token;
	}

	private String savedClanAdminToken()
	{
		if (!canUseClanAdminSession()) return "";
		String clanKey = normalizePlayerName(detectedClanName);
		if (clanKey.isEmpty()) return "";
		return clanAdminExpiresAt(clanKey) > System.currentTimeMillis() ? TsgHubSession.get("clanAdminToken:" + clanKey) : "";
	}

	private long clanAdminExpiresAt(String clanKey)
	{
		try { return Long.parseLong(TsgHubSession.get("clanAdminExpiresAt:" + clanKey)); }
		catch (NumberFormatException ignored) { return 0; }
	}

	private boolean canUseClanAdminSession()
	{
		return isDevTestManager()
			|| detectedClanRank >= ClanRank.ADMINISTRATOR.getRank();
	}
}
