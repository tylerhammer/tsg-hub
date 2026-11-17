package com.tsghub;

import com.google.gson.*;
import java.awt.image.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.runelite.client.game.*;
import net.runelite.client.hiscore.*;
import net.runelite.client.util.*;
import static com.tsghub.TsgHubJson.*;

final class TsgHubTileIcons
{
	private static final Map<String, Integer> SPRITES = new ConcurrentHashMap<>();

	private final IntFunction<AsyncBufferedImage> items;
	private final SpriteManager spriteManager;
	private final Map<Integer, AsyncBufferedImage> itemCache = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> spriteCache = new ConcurrentHashMap<>();
	private final Set<Integer> requested = ConcurrentHashMap.newKeySet();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final Map<JsonObject, Integer> resolved = Collections.synchronizedMap(new IdentityHashMap<>());

	TsgHubTileIcons(IntFunction<AsyncBufferedImage> items, SpriteManager spriteManager)
	{
		this.items = items;
		this.spriteManager = spriteManager;
	}

	void onLoaded(Runnable listener)
	{
		listeners.add(listener);
	}

	private void loaded()
	{
		for (Runnable listener : listeners) listener.run();
	}

	BufferedImage icon(JsonObject task)
	{
		if (task == null) return null;
		Integer id = resolved.get(task);
		if (id == null)
		{
			if (resolved.size() > 1000) resolved.clear();
			int itemId = integer(task, "iconItemId", 0);
			id = itemId > 0 ? itemId : -spriteFor(task);
			resolved.put(task, id);
		}
		return id > 0 ? item(id) : sprite(-id);
	}

	private BufferedImage item(int itemId)
	{
		if (items == null) return null;
		AsyncBufferedImage image = itemCache.get(itemId);
		if (image == null)
		{
			image = items.apply(itemId);
			if (image == null) return null;
			itemCache.put(itemId, image);
			image.onLoaded(this::loaded);
		}
		return image;
	}

	private BufferedImage sprite(int spriteId)
	{
		if (spriteId <= 0 || spriteManager == null) return null;
		BufferedImage sprite = spriteCache.get(spriteId);
		if (sprite == null && requested.add(spriteId))
		{
			spriteManager.getSpriteAsync(spriteId, 0, image -> {
				if (image == null) return;
				spriteCache.put(spriteId, image);
				loaded();
			});
		}
		return sprite;
	}

	static int spriteFor(JsonObject task)
	{
		for (JsonElement name : array(task, "iconBosses"))
		{
			int sprite = name.isJsonPrimitive() ? sprite(name.getAsString(), HiscoreSkillType.BOSS) : 0;
			if (sprite > 0) return sprite;
		}
		return sprite(str(task, "iconSkill"), HiscoreSkillType.SKILL);
	}

	private static int sprite(String name, HiscoreSkillType type)
	{
		if (name == null || name.isEmpty()) return 0;
		return SPRITES.computeIfAbsent(type + ":" + name, k -> {
			HiscoreSkill skill = TsgHubHiscores.named(name, type);
			return skill == null ? 0 : Math.max(0, skill.getSpriteId());
		});
	}
}
