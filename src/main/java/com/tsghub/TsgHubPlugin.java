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
import net.runelite.api.events.GameTick;
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
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "TSG Hub", internalName = "tsg-hub", description = "Type Shiii Gaming clan events and progress tracking", tags = {"tsg", "clan", "bingo", "events"})
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
	@Inject @Named("developerMode") private boolean developerMode;
	private TsgHubPanel panel;
	private TsgHubSidebarPanel sidebar;
	private JFrame hubWindow;
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
	private volatile boolean organizerRefreshPending;
	private TsgHubSignals signals;
	private volatile Filepath rulesCache;
	private TsgHubGroups groups;
	private TsgHubPresence presence;
	private TsgHubDrops drops;
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
			@Override public void onState(TsgHubSocket.State state) { SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setConnection(state); }); }
			@Override public void onAck(String id) { signals.acked(id); }
			@Override public void onReply(JsonObject message) { signalReply(message); }
			@Override public void onXpRequest(String skills) { reportXp(skills); }
		});
		signals = new TsgHubSignals(socket, this::api, this::signalReply);
		executor.execute(this::loadCachedRules);
		executor.scheduleAtFixedRate(this::socketTick, 2, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(this::autoRefreshBoard), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
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
		presence = new TsgHubPresence(this, client, clientThread, chatIconManager, spriteManager, executor, this::api, () -> sidebar, socket, this::adminKey);
		executor.scheduleAtFixedRate(unlessLive(presence::autoRefresh), TsgHubPresence.REFRESH_SECONDS, TsgHubPresence.REFRESH_SECONDS, TimeUnit.SECONDS);
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
	SpriteManager sprites() { return spriteManager; }
	CompletableFuture<HiscoreResult> lookupHiscores(String name, int accountType) { return hiscoreClient.lookupAsync(name, TsgHubHiscores.endpoint(accountType)); }

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
		if (name.isEmpty() || !sharingInClan()) return;
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
		else if (client.getGameState() != GameState.LOGGED_IN && !sidebarRouted) SwingUtilities.invokeLater(() -> sidebar.showLoggedOut());
		checkHubKey();
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		syncedClanRanks.clear();
		syncedIdentity = "";
		if (signals != null) signals.clear();
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
				TsgHubSession.set("memberToken:" + joinedEventId, memberToken);
				TsgHubSession.set("memberName:" + joinedEventId, memberName);
				TsgHubSession.set("token", memberToken);
				TsgHubSession.set("displayName", memberName);
				TsgHubSession.set("eventId", joinedEventId);
				syncSocketNow();
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
		if (!revokeToken.isEmpty()) executor.submit(() -> disconnect(eventId, revokeToken));
		TsgHubSession.clear("memberToken:" + eventId, "memberName:" + eventId);
		if (eventId.equals(TsgHubSession.get("eventId")))
		{
			TsgHubSession.clear("token", "eventId");
			}
		memberStatus("Disconnected. Rejoin anytime with your team code.", Tone.SUCCESS);
		SwingUtilities.invokeLater(() -> sidebar.showEventList());
		loadClanEvents();
	}

	private void clearAllSessions()
	{
		if (signals != null) signals.clear();
		revokeRemoteSession();
		TsgHubSession.clear("token", "eventId", "organizerEventId");
		TsgHubSession.removePrefix("memberToken:");
		TsgHubSession.removePrefix("memberName:");
		TsgHubSession.removePrefix("organizerToken:");
		TsgHubSession.removePrefix("organizerName:");
		syncedClanRanks.clear();
		checkedKeyIdentity = "";
		if (adminVerified) setAdminVerified(false);
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
				SwingUtilities.invokeLater(() -> sidebar.showBoard(event, TsgHubSession.get("displayName"), open));
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
		else memberError(failure, e);
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
		if (!sharingInClan())
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
		for (String joinedId : joinedCompetitions)
		{
			String joinedToken = TsgHubSession.get("memberToken:" + joinedId);
			if (!joinedToken.isEmpty()) tokens.putIfAbsent(joinedId, joinedToken);
		}
		Map<String, String> memberTokens = new HashMap<>(tokens);
		String organizerEventId = adminWindowOpen() ? getOrganizerEventId() : "";
		String organizerToken = organizerEventId.isEmpty() ? "" : organizerCredential(organizerEventId);
		if (!organizerToken.isEmpty()) tokens.putIfAbsent(organizerEventId, organizerToken);
		if (organizerRefreshPending && adminWindowOpen()) liveRefreshOrganizerEvent();
		socket.sync(tokens, inClanChat);
		socket.admin(adminKey());
		if (!socket.isLive()) pollTeamNotifications();
		signals.flush(memberTokens);
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
		if (!sharingInClan() || detectedPlayerName.isEmpty()) return;
		JsonObject body = memberBody(detectedPlayerName);
		sidebarBusy(true);
		executor.submit(() -> {
			try
			{
				JsonObject result = api().request("POST", "/v1/events/" + eventId + "/participate", adminKey(), body);
				TsgHubSession.set("memberToken:" + eventId, result.get("token").getAsString());
				TsgHubSession.set("memberName:" + eventId, result.getAsJsonObject("member").get("displayName").getAsString());
				syncSocketNow();
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
		if (!sharingInClan()) return;
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
				for (JsonObject event : TsgHubUi.objects(events))
				{
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
				joinedCompetitions = joinedCompetitionIds(events);
				SwingUtilities.invokeLater(() -> sidebar.setEvents(events));
			}
			catch (Exception e) { memberError("Couldn't load events. ", e); }
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
		TsgHubSession.set("token", token);
		TsgHubSession.set("eventId", eventId);
		TsgHubSession.set("displayName", displayName);
		syncSocketNow();
		refreshBoard(open);
	}

	private void syncClanRank(String eventId, String token, JsonObject event)
	{
		String displayName = detectedPlayerName;
		String memberName = TsgHubSession.get("displayName");
		String eventClan = TsgHubUi.str(event, "clanName");
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
		JsonObject evidence = new JsonObject();
		evidence.addProperty("note", note.trim());
		JsonObject body = claim(taskId, "manual-" + UUID.randomUUID(), "manual", evidence);
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
				memberError("Couldn't send proof. ", e);
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
				JsonObject event = organizerEvent(eventId.trim());
				SwingUtilities.invokeLater(() -> panel.openOrganizerEvent(event));
				organizerStatus("", Tone.INFO);
			}
			catch (Exception e) { organizerError("Couldn't open event. ", e); }
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
			catch (Exception e) { organizerError("Couldn't load events. ", e); }
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
			catch (Exception e) { organizerFailed(e, null, this::eventFormFailed); }
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
		eventAction(eventId, "PATCH", "", body, "Saving...", "Event saved.", null, () -> refreshOrganizerEvent(true));
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
		eventAction(eventId, "PATCH", "", body, "Publishing...", "Event published. Players can see it now.", "Couldn't publish. ", () -> refreshOrganizerEvent(true));
	}

	void endEvent(String eventId)
	{
		eventAction(eventId, "POST", "/end", null, "Ending event...", "Event ended.", "Couldn't end the event. ", () -> refreshOrganizerEvent(true));
	}

	private void eventAction(String eventId, String method, String suffix, JsonObject body, String pending, String success, String failure, Runnable done)
	{
		String credential = organizerCredential(eventId);
		organizerStatus(pending, Tone.INFO);
		executor.submit(() -> {
			try
			{
				organizerRequest(method, "/v1/events/" + eventId + suffix, credential, body);
				done.run();
				organizerStatus(success, Tone.SUCCESS);
				loadManagedEvents();
				loadClanEvents();
			}
			catch (Exception e) { organizerFailed(e, failure, failure == null ? this::eventFormFailed : null); }
		});
	}

	private void organizerFailed(Exception e, String failure, Consumer<String> onError)
	{
		if (onError == null) organizerError(failure, e);
		else
		{
			onError.accept(TsgHubUi.friendlyError(e));
			organizerStatus("", Tone.INFO);
		}
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
				for (JsonObject item : TsgHubUi.objects(selectedItems)) targetNames.add(item.get("name").getAsString());
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
				targetNames.forEach(itemNames::add);
				cfg.add("itemNames", itemNames);
				JsonArray itemIds = new JsonArray();
				for (JsonObject item : TsgHubUi.objects(selectedItems)) itemIds.add(item.get("id").getAsInt());
				cfg.add("itemIds", itemIds);
				// Completing any one set group finishes the task.
				if (selectedType == 3 || dropRuleMode == 1)
				{
					Map<Integer, JsonArray> groupedItems = new TreeMap<>();
					for (JsonObject selected : TsgHubUi.objects(selectedItems))
					{
						JsonArray groupItems = groupedItems.computeIfAbsent(Math.max(0, selected.get("group").getAsInt()), ignored -> new JsonArray());
						JsonObject item = new JsonObject();
						item.addProperty("name", selected.get("name").getAsString());
						item.addProperty("id", selected.get("id").getAsInt());
						groupItems.add(item);
					}
					JsonArray groups = new JsonArray();
					groupedItems.values().forEach(groups::add);
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
		eventAction(eventId, "DELETE", "", null, "Deleting event...", "Event deleted.", "Couldn't delete the event. ", () -> {
			forgetEvent(eventId);
			SwingUtilities.invokeLater(() -> panel.eventDeleted(eventId));
		});
	}

	private void forgetEvent(String eventId)
	{
		TsgHubSession.clear("organizerToken:" + eventId, "organizerName:" + eventId, "memberToken:" + eventId, "memberName:" + eventId);
		if (eventId.equals(TsgHubSession.get("organizerEventId"))) TsgHubSession.clear("organizerEventId");
		if (eventId.equals(TsgHubSession.get("eventId")))
		{
			TsgHubSession.clear("token", "eventId");
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
			int count = TsgHubUi.integer(result, "count", 0);
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
				JsonObject event = organizerEvent(eventId);
				SwingUtilities.invokeLater(() -> {
					if (open) panel.openOrganizerEvent(event);
					else panel.showEvent(event);
				});
			}
			catch (Exception e) { organizerError("Couldn't refresh the event. ", e); }
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
				JsonObject event = organizerEvent(eventId);
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
				organizerError("Item search failed. ", e);
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
				catch (RuntimeException e) { organizerError("Item search failed. ", e); }
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
		if (!sharingInClan() || signals == null) return;
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
		if (sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(event.getGroupId())) socket.rewardInterface(event.getGroupId(), true, client.getTickCount());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (sharingInClan() && TsgHubRules.get().rewardInterfaces.contains(event.getGroupId())) socket.rewardInterface(event.getGroupId(), false, client.getTickCount());
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!isInHubClan()) return;
		if (event.getComposition() == null) return;
		processLoot("NPC", Text.removeTags(event.getComposition().getName()), event.getItems(), 1);
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
		if (!sharingInClan()) return;
		String message = event.getMessage().replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
		if (isPetMessage(message)) petCheckTicks = PET_CHECK_TICKS;
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
				if (visiblePlayers.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) visiblePlayers.add(name);
				if (!player.isClanMember() && nonClanPlayers.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) nonClanPlayers.add(name);
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
	public void onGameTick(GameTick tick)
	{
		if (presence != null) presence.onGameTick();
		if (clanCheckTicks > 0)
		{
			clanCheckTicks--;
			if (client.getClanSettings() != null) clanCheckTicks = 0;
			if (clanCheckTicks == 0) refreshDetectedClan();
		}
		if (!sharingInClan())
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
		JsonObject pet = new JsonObject();
		pet.addProperty("itemId", petId);
		pet.addProperty("name", itemManager.getItemComposition(petId).getName());
		signals.send("pet", pet);
	}

	private static boolean isPetMessage(String message)
	{
		return message.contains("you have a funny feeling like you're being followed")
			|| message.contains("you feel something weird sneaking into your backpack");
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

	private int newPetId()
	{
		for (int itemId : inventoryPetIds()) if (!inventoryPets.contains(itemId)) return itemId;
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
		String normalized = name.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
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

	private static String eventToken(String eventId)
	{
		return eventSession("memberToken:", eventId, "token");
	}

	private static String eventSession(String prefix, String eventId, String activeKey)
	{
		String value = TsgHubSession.get(prefix + eventId);
		return value.isEmpty() && eventId.equals(TsgHubSession.get("eventId")) ? TsgHubSession.get(activeKey) : value;
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
			if (!TsgHubSession.get("memberToken:" + id).isEmpty()) ids.add(id);
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
					SwingUtilities.invokeLater(() -> sidebar.competitionProgress(eventId, gained, parsedRank));
				}
				catch (NumberFormatException ignored) { /* Malformed progress waits for the next report. */ }
				break;
			case "claimed":
				showProgressMessage(TsgHubUi.str(message, "taskTitle"), TsgHubUi.str(message, "progress"), TsgHubUi.str(message, "complete"));
				if (eventId.equals(TsgHubSession.get("eventId"))) refreshBoard();
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
		String token = TsgHubSession.get("token");
		String eventId = TsgHubSession.get("eventId");
		if (token.isEmpty() || eventId.isEmpty()) return;
		try
		{
			String path = "/v1/events/" + eventId + "/notifications?inClanChat=" + inClanChat + "&clanName=" + URLEncoder.encode(detectedClanName, StandardCharsets.UTF_8);
			JsonArray notifications = api().request("GET", path, token, null).getAsJsonArray("notifications");
			if (notifications == null) return;
			for (JsonObject notification : TsgHubUi.objects(notifications))
			{
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

	private void updateAvailable(String version)
	{
		if (version.equals(notifiedUpdate)) return;
		notifiedUpdate = version;
		SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setUpdate(version); });
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("TSG Hub " + Text.escapeJagex(version) + " is available. Restart RuneLite to update."));
	}

	private void showLocalChatMessage(ChatMessageBuilder body)
	{
		String message = new ChatMessageBuilder().append(TsgHubTheme.ACCENT.darker(), "[TSG Hub] ").build() + body.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.GAMEMESSAGE).runeLiteFormattedMessage(message).build());
	}

	private void memberStatus(String message, Tone tone)
	{
		SwingUtilities.invokeLater(() -> { if (sidebar != null) sidebar.setStatus(message, tone); });
	}

	private void memberError(String failure, Exception e)
	{
		memberStatus(failure + TsgHubUi.friendlyError(e), Tone.ERROR);
	}

	private void organizerError(String failure, Exception e)
	{
		organizerStatus(failure + TsgHubUi.friendlyError(e), Tone.ERROR);
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
			catch (Exception e) { organizerFailed(e, "", onError); }
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

	private JsonObject organizerEvent(String eventId) throws Exception
	{
		return organizerRequest("GET", "/v1/events/" + eventId + "/organizer", organizerCredential(eventId), null);
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
