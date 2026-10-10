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
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
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
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "TSG Hub", description = "Type Shiii Gaming clan events and progress tracking", tags = {"tsg", "clan", "bingo", "events"})
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
	private volatile JFrame hubWindow;
	private NavigationButton navigationButton;
	private ScheduledExecutorService executor;
	private ExecutorService itemSearchExecutor;
	private final Map<String, Integer> syncedClanRanks = new ConcurrentHashMap<>();
	private static final int CLAN_CHECK_TICKS = 50;
	private static final long KEY_CHECK_RETRY_SECONDS = 15;
	private static final long KEY_CHECK_MAX_RETRY_SECONDS = 300;
	private volatile int clanCheckTicks;
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
	private volatile boolean adminVerified;
	private volatile String checkedKeyIdentity = "";
	private volatile String rejectedKey = "";
	private volatile int keyCheckAttempts;
	private volatile boolean announceKey;
	private volatile TsgHubApi api;
	private TsgHubSocket socket;
	private TsgHubCompetitionTracker competitions;
	private TsgHubClaims claims;
	private TsgHubOrganizer organizer;
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
			@Override public void onReady() { resyncLiveViews(); }
			@Override public void onChanged(String topic, String eventId) { liveChange(topic, eventId); }
			@Override public void onAdminRevoked(String token) { recheckHubKey(token); }
			@Override public void onAnnouncement(String text) { announce(text); }
			@Override public void onUpdateAvailable(String version) { updateAvailable(version); }
			@Override public void onSubscribed(String eventId) { if (competitions != null) competitions.onSubscribed(eventId); }
			@Override public void onProgress(String eventId, long gained, Long rank) { ui(s -> s.competitionProgress(eventId, gained, rank)); }
		});
		executor.scheduleAtFixedRate(this::socketTick, 2, 5, TimeUnit.SECONDS);
		executor.scheduleAtFixedRate(unlessLive(this::autoRefreshBoard), BOARD_AUTO_REFRESH_SECONDS, BOARD_AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
		competitions = new TsgHubCompetitionTracker(this::api, executor, client, clientThread, socket);
		claims = new TsgHubClaims(this, client, itemManager, executor, this::api);
		itemSearchExecutor = Executors.newFixedThreadPool(2, daemon("tsg-hub-item-search"));
		organizer = new TsgHubOrganizer(this, client, clientThread, itemManager, executor, itemSearchExecutor, () -> panel);
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
		if (competitions != null) competitions.clear();
		clearTaskCache();
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
			clearTaskCache();
			}
		memberStatus("Disconnected. Rejoin anytime with your team code.", Tone.SUCCESS);
		ui(s -> s.showEventList());
		loadClanEvents();
	}

	private void clearAllSessions()
	{
		if (competitions != null) competitions.clear();
		revokeRemoteSession();
		TsgHubSession.clear(TOKEN, EVENT_ID, ORGANIZER_EVENT_ID);
		TsgHubSession.removePrefix(MEMBER_TOKEN);
		TsgHubSession.removePrefix(MEMBER_NAME);
		TsgHubSession.removePrefix(ORGANIZER_TOKEN);
		TsgHubSession.removePrefix(ORGANIZER_NAME);
		syncedClanRanks.clear();
		clearTaskCache();
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
				JsonObject result = api().request("GET", "/v1/events/" + eventId, token, null);
				JsonObject event = result.getAsJsonObject("event");
				syncClanRank(eventId, token, event);
				if (eventId.equals(TsgHubSession.get(EVENT_ID)))
				{
					if (claims != null) claims.setTasks(eventId, event);
				}
				ui(s -> s.showBoard(event, TsgHubSession.get(DISPLAY_NAME), open));
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
		for (String joinedId : competitions.joinedIds())
		{
			String joinedToken = TsgHubSession.get(MEMBER_TOKEN + joinedId);
			if (!joinedToken.isEmpty()) tokens.putIfAbsent(joinedId, joinedToken);
		}
		String organizerEventId = adminWindowOpen() ? getOrganizerEventId() : "";
		String organizerToken = organizerEventId.isEmpty() ? "" : organizer.credential(organizerEventId);
		if (!organizerToken.isEmpty()) tokens.putIfAbsent(organizerEventId, organizerToken);
		if (organizer.refreshPending() && adminWindowOpen()) organizer.liveRefresh();
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
				JsonObject response = api().request("GET", "/v1/events?clanName=" + encodedClan, adminKey(), null);
				JsonArray events = response.getAsJsonArray("events");
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
							TsgHubSession.set(MEMBER_NAME + eventId, TsgHubSession.get("displayName"));
						}
					}
					event.addProperty("joined", !savedToken.isEmpty());
				}
				competitions.setEvents(events);
				ui(s -> s.setEvents(events));
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
		clearTaskCache();
		TsgHubSession.set(TOKEN, token);
		TsgHubSession.set(EVENT_ID, eventId);
		TsgHubSession.set(DISPLAY_NAME, displayName);
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
			clearTaskCache();
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
		if (!isInHubClan()) return;
		if (config.dataSharingOptIn() && competitions != null) competitions.onXp(event.getSkill(), event.getXp());
		if (!config.dataSharingOptIn() || client.getLocalPlayer() == null || claims == null) return;
		claims.onXp(event.getSkill().getName(), event.getXp());
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
		String message = Text.removeTags(event.getMessage()).toLowerCase(Locale.ROOT);
		if (config.dataSharingOptIn() && competitions != null) competitions.onChat(message);
		if (config.dataSharingOptIn() && claims != null) claims.onChat(message);
	}

	private void processLoot(String sourceType, String sourceName, Collection<ItemStack> items, int amount)
	{
		if (config.dataSharingOptIn() && client.getLocalPlayer() != null && claims != null) claims.onLoot(sourceType, sourceName, items, amount);
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
		if (claims != null) claims.onTick();
	}

	private void clearTaskCache()
	{
		if (claims != null) claims.clear();
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

	void showProgressMessage(JsonObject result)
	{
		if (result == null || result.has("duplicate") && result.get("duplicate").getAsBoolean() || !result.has("progress")) return;
		JsonObject progress = result.getAsJsonObject("progress");
		// Per-member tasks report the submitter's own count.
		JsonObject mine = result.has("memberProgress") && result.get("memberProgress").isJsonObject() ? result.getAsJsonObject("memberProgress") : null;
		JsonObject shown = mine != null ? mine : progress;
		String title = result.get("taskTitle").getAsString();
		String count = shown.get("progress").getAsInt() + "/" + shown.get("target").getAsInt();
		boolean complete = progress.get("completed").getAsBoolean();
		ChatMessageBuilder message = new ChatMessageBuilder();
		if (complete || mine != null && TsgHubUi.bool(mine, "completed"))
		{
			message.append(TsgHubTheme.SUCCESS.darker(), complete ? "Task complete: " : "Your part is done: ")
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
		ui(s -> s.setUpdate(version));
		showLocalChatMessage(new ChatMessageBuilder().append(ChatColorType.HIGHLIGHT).append("TSG Hub " + Text.escapeJagex(version) + " is available. Restart RuneLite to update."));
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

	void memberError(String failure, Exception e)
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
