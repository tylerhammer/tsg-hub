package com.tsghub;

import com.google.gson.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.imageio.*;
import okhttp3.*;

final class TsgHubApi
{
	static final class HttpError extends IllegalStateException
	{
		final int status;

		HttpError(int status, String message)
		{
			super(message);
			this.status = status;
		}
	}

	private static final class Flight
	{
		final CompletableFuture<JsonObject> result = new CompletableFuture<>();
		volatile int waiters;
	}

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final MediaType JPEG = MediaType.parse("image/jpeg");
	private static final long MAX_IMAGE_BYTES = 8 << 20;
	private final String baseUrl;
	private final OkHttpClient http;
	private final Gson gson;
	private final Executor executor;
	private final Map<String, Flight> inFlight = new ConcurrentHashMap<>();

	TsgHubApi(OkHttpClient http, Gson gson, String baseUrl, Executor executor)
	{
		this.gson = gson;
		this.executor = executor;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.http = http.newBuilder()
			.connectTimeout(5, TimeUnit.SECONDS)
			.readTimeout(8, TimeUnit.SECONDS)
			.build();
	}

	CompletableFuture<JsonObject> call(String method, String path, String token, JsonObject payload)
	{
		if (!"GET".equals(method)) return send(method, path, token, payload);
		String key = path + " " + (token == null ? "" : token);
		boolean[] started = {false};
		Flight flight = inFlight.compute(key, (k, current) -> {
			Flight next = current == null ? new Flight() : current;
			started[0] = current == null;
			next.waiters++;
			return next;
		});
		if (started[0]) send(method, path, token, null).whenComplete((value, error) -> {
			inFlight.remove(key, flight);
			if (error == null) flight.result.complete(value);
			else flight.result.completeExceptionally(error);
		});
		return flight.result.thenApply(value -> flight.waiters > 1 ? value.deepCopy() : value);
	}

	CompletableFuture<JsonObject> call(String method, String path, String token, JsonObject payload, Consumer<JsonObject> onResult, Consumer<Exception> onError)
	{
		return call(method, path, token, payload, onResult, onError, () -> { });
	}

	CompletableFuture<JsonObject> call(String method, String path, String token, JsonObject payload, Consumer<JsonObject> onResult, Consumer<Exception> onError, Runnable after)
	{
		return call(method, path, token, payload).whenCompleteAsync((response, error) ->
		{
			try { onResult.accept(result(response, error)); }
			catch (Exception e) { onError.accept(e); }
			finally { after.run(); }
		}, executor);
	}

	static int status(Throwable error)
	{
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		return cause instanceof HttpError ? ((HttpError) cause).status : 0;
	}

	static JsonObject result(JsonObject value, Throwable error) throws Exception
	{
		if (error == null) return value;
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (cause instanceof Exception) throw (Exception) cause;
		throw new IllegalStateException(cause);
	}

	CompletableFuture<JsonObject> upload(String path, String token, byte[] jpeg)
	{
		return send(build("POST", path, token, null).newBuilder().post(RequestBody.create(JPEG, jpeg)).build());
	}

	private CompletableFuture<JsonObject> send(String method, String path, String token, JsonObject payload)
	{
		return send(build(method, path, token, payload));
	}

	String url(String path)
	{
		return baseUrl + path;
	}

	static boolean isNewsImage(String path)
	{
		return path.matches("/v1/announcements/images/[A-Za-z0-9_-]+");
	}

	CompletableFuture<BufferedImage> newsImage(String path)
	{
		CompletableFuture<BufferedImage> future = new CompletableFuture<>();
		if (!isNewsImage(path))
		{
			future.completeExceptionally(new IllegalArgumentException("Not a news image"));
			return future;
		}
		Request request = new Request.Builder().url(baseUrl + path).header(TsgHubVersion.HEADER, TsgHubVersion.VERSION).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body == null || body.contentLength() > MAX_IMAGE_BYTES) throw new IOException("Image returned HTTP " + response.code());
					BufferedImage image = ImageIO.read(new ByteArrayInputStream(body.bytes()));
					if (image == null) throw new IOException("Unreadable image");
					future.complete(image);
				}
				catch (Exception e) { future.completeExceptionally(e); }
			}
		});
		return future;
	}

	private CompletableFuture<JsonObject> send(Request request)
	{
		CompletableFuture<JsonObject> future = new CompletableFuture<>();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					future.complete(read(response));
				}
				catch (Exception e) { future.completeExceptionally(e); }
			}
		});
		return future;
	}

	private Request build(String method, String path, String token, JsonObject payload)
	{
		Request.Builder builder = new Request.Builder()
			.url(baseUrl + path)
			.header("Accept", "application/json")
			.header(TsgHubVersion.HEADER, TsgHubVersion.VERSION);
		if (token != null && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);
		RequestBody body = payload == null ? null : RequestBody.create(JSON, payload.toString());
		if (body == null && !"GET".equals(method) && !"DELETE".equals(method)) body = RequestBody.create(JSON, "{}");
		return builder.method(method, body).build();
	}

	private JsonObject read(Response response) throws IOException
	{
		ResponseBody responseBody = response.body();
		String text = responseBody == null ? "" : responseBody.string();
		JsonObject json = parseObject(text);
		if (!response.isSuccessful())
		{
			String message = json != null && json.has("error") && json.get("error").isJsonPrimitive()
				? json.get("error").getAsString()
				: "Service returned HTTP " + response.code();
			throw new HttpError(response.code(), message);
		}
		if (json == null) throw new IllegalStateException("Service returned an unexpected response");
		return json;
	}

	static String encode(String value)
	{
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}

	private JsonObject parseObject(String text)
	{
		if (text.isEmpty()) return new JsonObject();
		try
		{
			JsonElement element = gson.fromJson(text, JsonElement.class);
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		}
		catch (JsonParseException e) { return null; }
	}
}
