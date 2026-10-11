package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.After;
import org.junit.Test;

public class SignalsTest
{
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final List<JsonObject> posted = new ArrayList<>();
	private final List<JsonObject> received = new ArrayList<>();
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
			String reply = "{\"acked\":[],\"messages\":[{\"type\":\"claimed\",\"taskTitle\":\"Hydra\",\"progress\":\"1/3\"}]}";
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("")
				.body(ResponseBody.create(MediaType.get("application/json"), reply)).build();
		}).build();
		TsgHubSocket socket = new TsgHubSocket(http, "https://hub.test", executor, null);
		TsgHubApi api = new TsgHubApi(http, "https://hub.test");
		return new TsgHubSignals(socket, () -> api, received::add);
	}

	@Test
	public void serverRulesReplaceDefaults()
	{
		TsgHubSignals signals = signals();
		assertTrue(signals.wantsChat("your vorkath kill count is: 5."));
		assertFalse(signals.wantsChat("you catch a shrimp."));
		signals.rules("you catch|kill count", "");
		assertTrue(signals.wantsChat("you catch a shrimp."));
		assertTrue(signals.wantsPlayers("theatre of blood completion time: 20:00"));
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
