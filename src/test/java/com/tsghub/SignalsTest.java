package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.util.Filepath;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class SignalsTest
{
	@BeforeClass
	public static void rules()
	{
		TsgHubRules.loadBundled(new Gson());
	}

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final List<JsonObject> posted = new ArrayList<>();
	private final List<JsonObject> received = new ArrayList<>();
	private final List<String> paths = new ArrayList<>();
	private final Map<String, Integer> eventStatus = new HashMap<>();
	private int status = 200;

	@After
	public void stop()
	{
		executor.shutdownNow();
	}

	private TsgHubSignals signals()
	{
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
			Buffer body = new Buffer();
			chain.request().body().writeTo(body);
			posted.add(new JsonParser().parse(body.readUtf8()).getAsJsonObject());
			String path = chain.request().url().encodedPath();
			paths.add(path);
			int code = eventStatus.getOrDefault(path, status);
			String reply = "{\"acked\":[],\"messages\":[{\"type\":\"claimed\",\"taskTitle\":\"Hydra\",\"progress\":\"1/3\"}]}";
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("")
				.body(ResponseBody.create(MediaType.get("application/json"), reply)).build();
		}).build();
		TsgHubSocket socket = new TsgHubSocket(http, new Gson(), "https://hub.test", executor, null, TsgHubTraffic.OFF);
		TsgHubApi api = new TsgHubApi(http, new Gson(), "https://hub.test");
		return new TsgHubSignals(socket, () -> api, received::add);
	}

	@Test
	public void serverRulesReplaceBundled() throws Exception
	{
		TsgHubSignals signals = signals();
		TsgHubRules bundled = TsgHubRules.get();
		assertEquals("Alchemical Hydra", AreaNames.forRegion(5536).name);
		assertEquals("Vorkath", ActivityDetector.bossFor("Vorkath"));
		assertTrue(bundled.pets.contains(11995));
		assertTrue(bundled.rewardInterfaces.contains(153));
		assertTrue(signals.wantsChat("your vorkath kill count is: 5."));
		assertFalse(signals.wantsChat("you catch a shrimp."));
		assertTrue(signals.wantsPlayers("theatre of blood completion time: 20:00"));

		JsonObject server = new JsonParser().parse("{\"version\":\"abc\",\"chat\":[\"You catch\"],\"areas\":[{\"type\":\"CITIES\",\"name\":\"Lumbridge\",\"regions\":[5536]}]}").getAsJsonObject();
		try
		{
			TsgHubRules.apply(server);
			assertEquals("abc", TsgHubRules.get().version);
			assertTrue(signals.wantsChat("you catch a shrimp."));
			assertEquals("Lumbridge", AreaNames.forRegion(5536).name);
			assertNull(ActivityDetector.bossFor("Vorkath"));
		}
		finally { restoreBundled(); }
	}

	@Test
	public void socketSendFallsBackWhenNotReady()
	{
		TsgHubSocket socket = new TsgHubSocket(new OkHttpClient(), new Gson(), "https://hub.test", executor, null, TsgHubTraffic.OFF);
		JsonObject body = new JsonObject();
		body.addProperty("message", "Bob received a drop: Abyssal whip");
		assertFalse(socket.send("drop", body));
		assertFalse(body.has("type"));
	}

	@Test
	public void cachedRulesSurviveRestart() throws Exception
	{
		Filepath cache = Filepath.Unchecked.getRooted(Files.createTempDirectory("tsghub")).join("rules.json");
		JsonObject server = new JsonParser().parse("{\"version\":\"v2\",\"chat\":[\"you catch\"]}").getAsJsonObject();
		try
		{
			TsgHubRules.save(server, cache);
			restoreBundled();
			assertEquals("", TsgHubRules.get().version);
			TsgHubRules.loadCached(cache, new Gson());
			assertEquals("v2", TsgHubRules.get().version);
			assertTrue(TsgHubRules.get().chat.contains("you catch"));
		}
		finally { restoreBundled(); }
	}

	private static void restoreBundled() throws Exception
	{
		try (InputStreamReader reader = new InputStreamReader(TsgHubRules.class.getResourceAsStream("rules.json"), StandardCharsets.UTF_8))
		{
			TsgHubRules.apply(new JsonParser().parse(reader).getAsJsonObject());
		}
	}

	@Test
	public void fallbackRetriesOnlyEventsThatFailed()
	{
		TsgHubSignals signals = signals();
		signals.send("loot", new JsonObject());
		Map<String, String> tokens = new LinkedHashMap<>();
		tokens.put("e1", "member");
		tokens.put("e2", "member");
		tokens.put("gone", "member");
		eventStatus.put("/v1/events/e2/signals", 500);
		eventStatus.put("/v1/events/gone/signals", 404);
		signals.flush(tokens);
		assertEquals(3, posted.size());

		eventStatus.remove("/v1/events/e2/signals");
		signals.flush(tokens);
		assertEquals(List.of("/v1/events/e1/signals", "/v1/events/e2/signals", "/v1/events/gone/signals", "/v1/events/e2/signals"), paths);

		signals.flush(tokens);
		assertEquals(4, posted.size());
	}

	@Test
	public void cacheFromAnotherPluginVersionIsIgnored() throws Exception
	{
		Filepath cache = Filepath.Unchecked.getRooted(Files.createTempDirectory("tsghub")).join("rules.json");
		cache.write("{\"plugin\":\"0.0.1\",\"rules\":{\"version\":\"old\"}}");
		try
		{
			TsgHubRules.loadCached(cache, new Gson());
			assertEquals("", TsgHubRules.get().version);
			assertTrue(TsgHubRules.get().petMessages.contains("you feel something weird sneaking into your backpack"));
		}
		finally { restoreBundled(); }
	}

	@Test
	public void fallbackPostsPendingAndClearsOnSuccess()
	{
		TsgHubSignals signals = signals();
		JsonObject loot = new JsonObject();
		loot.addProperty("sourceName", "Alchemical Hydra");
		signals.send("loot", loot);
		signals.xp("WOODCUTTING", 1000, 1);
		signals.xp("WOODCUTTING", 1200, 2);

		status = 500;
		signals.flush(Map.of("e1", "member"));
		assertEquals(1, posted.size());
		assertEquals(2, posted.get(0).getAsJsonArray("signals").size());

		status = 200;
		signals.flush(Map.of("e1", "member"));
		assertEquals(2, posted.size());
		JsonObject retried = posted.get(1).getAsJsonArray("signals").get(0).getAsJsonObject();
		assertEquals(1200, retried.get("xp").getAsInt());
		assertEquals(1, received.size());

		signals.flush(Map.of("e1", "member"));
		assertEquals(2, posted.size());
	}
}
