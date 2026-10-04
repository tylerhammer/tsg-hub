package com.tsghub;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private final String baseUrl;
	private final OkHttpClient http;

	TsgHubApi(OkHttpClient http, String baseUrl)
	{
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.http = http.newBuilder()
			.connectTimeout(5, TimeUnit.SECONDS)
			.readTimeout(8, TimeUnit.SECONDS)
			.build();
	}

	JsonObject request(String method, String path, String token, JsonObject payload) throws Exception
	{
		Request.Builder builder = new Request.Builder()
			.url(baseUrl + path)
			.header("Accept", "application/json")
			.header(TsgHubVersion.HEADER, TsgHubVersion.VERSION);
		if (token != null && !token.isEmpty()) builder.header("Authorization", "Bearer " + token);
		RequestBody body = payload == null ? null : RequestBody.create(JSON, payload.toString());
		// OkHttp requires a body for POST/PATCH/PUT.
		if (body == null && !"GET".equals(method) && !"DELETE".equals(method)) body = RequestBody.create(JSON, "{}");
		builder.method(method, body);

		try (Response response = http.newCall(builder.build()).execute())
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
	}

	private static JsonObject parseObject(String text)
	{
		if (text.isEmpty()) return new JsonObject();
		try
		{
			JsonElement element = new JsonParser().parse(text);
			return element.isJsonObject() ? element.getAsJsonObject() : null;
		}
		catch (JsonParseException e) { return null; }
	}
}
