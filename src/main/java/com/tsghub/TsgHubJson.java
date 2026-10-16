package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class TsgHubJson
{
	private TsgHubJson()
	{
	}

	static List<String> strings(JsonArray array)
	{
		List<String> out = new ArrayList<>();
		for (JsonElement element : array) if (element.isJsonPrimitive()) out.add(element.getAsString());
		return out;
	}

	static JsonObject byId(JsonArray items, String id)
	{
		if (items == null || id == null) return null;
		for (JsonObject item : objects(items)) if (id.equals(str(item, "id"))) return item;
		return null;
	}

	static String str(JsonObject object, String key)
	{
		if (object == null) return "";
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	static int integer(JsonObject object, String key, int fallback)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
		try { return object.get(key).getAsInt(); }
		catch (RuntimeException ignored) { return fallback; }
	}

	static boolean bool(JsonObject object, String key)
	{
		if (object == null || !object.has(key) || object.get(key).isJsonNull()) return false;
		try { return object.get(key).getAsBoolean(); }
		catch (RuntimeException ignored) { return false; }
	}

	static JsonArray array(JsonObject object, String key)
	{
		return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
	}

	static Instant instant(String iso)
	{
		try { return iso == null || iso.isEmpty() ? null : Instant.parse(iso); }
		catch (DateTimeException e) { return null; }
	}

	static JsonObject object(JsonObject object, String key)
	{
		return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
	}

	static List<JsonObject> objects(JsonArray array)
	{
		List<JsonObject> objects = new ArrayList<>();
		for (JsonElement element : array) objects.add(element.getAsJsonObject());
		return objects;
	}
}
