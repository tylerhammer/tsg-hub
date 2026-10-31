package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Test;

public class SocketTest
{
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
	private ServerSocket server;
	private Socket peer;
	private TsgHubSocket socket;

	@After
	public void stop() throws IOException
	{
		if (socket != null) socket.disconnect();
		executor.shutdownNow();
		if (peer != null) peer.close();
		if (server != null) server.close();
	}

	private void connect(String ready) throws Exception
	{
		server = new ServerSocket(0);
		socket = new TsgHubSocket(new OkHttpClient(), new Gson(), "http://127.0.0.1:" + server.getLocalPort(), executor, new Recorder());
		socket.connect("TSGaming");
		peer = server.accept();
		peer.setSoTimeout(5_000);
		handshake();
		write(ready);
		assertEquals("ready", events.poll(5, TimeUnit.SECONDS));
	}

	@Test
	public void readyCarriesProtocol() throws Exception
	{
		connect("{\"type\":\"ready\",\"rules\":\"v1\",\"protocol\":\"2\"}");
		assertEquals(2, socket.protocol());
	}

	@Test
	public void oldServerIsProtocolOne() throws Exception
	{
		connect("{\"type\":\"ready\",\"rules\":\"v1\"}");
		assertEquals(1, socket.protocol());
	}

	@Test
	public void errorsWithIdGoToSender() throws Exception
	{
		connect("{\"type\":\"ready\",\"protocol\":\"2\"}");
		write("{\"type\":\"error\",\"topic\":\"group\",\"id\":\"s1\",\"status\":\"403\",\"message\":\"Only the leader can do that\"}");
		assertEquals("rejected s1 403 Only the leader can do that", events.poll(5, TimeUnit.SECONDS));
		write("{\"type\":\"error\",\"topic\":\"group\",\"status\":\"404\",\"message\":\"Group not found\"}");
		assertEquals("group 404", events.poll(5, TimeUnit.SECONDS));
		assertNull(events.poll(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void outboxFollowsServerReplies() throws Exception
	{
		connect("{\"type\":\"ready\",\"protocol\":\"2\"}");
		long[] now = {1_000};
		TsgHubOutbox outbox = new TsgHubOutbox(socket, (method, path, token, body) -> { throw new AssertionError("no REST while live"); }, () -> now[0]);
		JsonObject loot = new JsonObject();
		loot.addProperty("sourceName", "Vorkath");
		assertTrue(outbox.signal("loot", loot, false));
		JsonObject sent = read();
		assertEquals("loot", sent.get("type").getAsString());
		String id = sent.get("id").getAsString();

		write("{\"type\":\"error\",\"topic\":\"loot\",\"id\":\"" + id + "\",\"status\":\"429\",\"message\":\"Too many signals. Try again shortly.\"}");
		String rejected = events.poll(5, TimeUnit.SECONDS);
		assertEquals("rejected " + id + " 429 Too many signals. Try again shortly.", rejected);
		outbox.failed(id, 429, "");
		now[0] += TsgHubOutbox.RETRY_MILLIS;
		outbox.flush();
		assertEquals(id, read().get("id").getAsString());

		write("{\"type\":\"ack\",\"id\":\"" + id + "\"}");
		assertEquals("ack " + id, events.poll(5, TimeUnit.SECONDS));
		outbox.acked(id);
		assertEquals(0, outbox.size());
	}

	private void handshake() throws Exception
	{
		InputStream in = peer.getInputStream();
		ByteArrayOutputStream head = new ByteArrayOutputStream();
		while (!head.toString("UTF-8").endsWith("\r\n\r\n")) head.write(in.read());
		String key = "";
		for (String line : head.toString("UTF-8").split("\r\n"))
		{
			if (line.toLowerCase().startsWith("sec-websocket-key:")) key = line.substring(line.indexOf(':') + 1).trim();
		}
		byte[] digest = MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8));
		String reply = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
			+ Base64.getEncoder().encodeToString(digest) + "\r\n\r\n";
		peer.getOutputStream().write(reply.getBytes(StandardCharsets.UTF_8));
	}

	private void write(String text) throws IOException
	{
		byte[] payload = text.getBytes(StandardCharsets.UTF_8);
		OutputStream out = peer.getOutputStream();
		out.write(0x81);
		if (payload.length < 126) out.write(payload.length);
		else
		{
			out.write(126);
			out.write(payload.length >> 8);
			out.write(payload.length & 0xff);
		}
		out.write(payload);
		out.flush();
	}

	private JsonObject read() throws IOException
	{
		DataInputStream in = new DataInputStream(peer.getInputStream());
		while (true)
		{
			int opcode = in.readUnsignedByte() & 0x0f;
			int second = in.readUnsignedByte();
			long length = second & 0x7f;
			if (length == 126) length = in.readUnsignedShort();
			else if (length == 127) length = in.readLong();
			byte[] mask = new byte[4];
			if ((second & 0x80) != 0) in.readFully(mask);
			byte[] payload = new byte[(int) length];
			in.readFully(payload);
			for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
			if (opcode == 0x1) return new JsonParser().parse(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private final class Recorder implements TsgHubSocket.Listener
	{
		@Override public void onConnection(TsgHubSocket.State state) { }
		@Override public void onSnapshot(JsonObject message) { }
		@Override public void onReady(String rulesVersion) { events.add("ready"); }
		@Override public void onRulesChanged(String version) { }
		@Override public void onChanged(String topic, String eventId) { }
		@Override public void onAdminRevoked(String token) { }
		@Override public void onAnnouncement(String text) { }
		@Override public void onUpdateAvailable(String version) { }
		@Override public void onAck(String id) { events.add("ack " + id); }
		@Override public void onRejected(String id, int status, String message) { events.add("rejected " + id + " " + status + " " + message); }
		@Override public void onSubscribed() { }
		@Override public void onReply(JsonObject message) { }
		@Override public void onXpRequest(String skills) { }
		@Override public void onScreenshotRequest(String id, String token) { }
		@Override public void onGroupHeartbeat(JsonObject message) { }
		@Override public void onGroupRejected(int status) { events.add("group " + status); }
	}
}
