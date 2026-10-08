package com.tsghub;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.AsyncBufferedImage;

final class TsgHubTileIcons
{
	private static final Map<String, Integer> SPRITES = sprites();

	private final IntFunction<AsyncBufferedImage> items;
	private final SpriteManager spriteManager;
	private final Map<Integer, AsyncBufferedImage> itemCache = new ConcurrentHashMap<>();
	private final Set<Integer> watched = ConcurrentHashMap.newKeySet();
	private final Map<Integer, BufferedImage> spriteCache = new ConcurrentHashMap<>();
	private final Set<Integer> requested = ConcurrentHashMap.newKeySet();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private final JLabel repainter = new JLabel()
	{
		@Override public void repaint()
		{
			loaded();
		}
	};

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
		int itemId = itemFor(task);
		if (itemId > 0 && items != null)
		{
			AsyncBufferedImage image = itemCache.get(itemId);
			if (image == null)
			{
				image = items.apply(itemId);
				if (image == null) return null;
				itemCache.put(itemId, image);
			}
			if (SwingUtilities.isEventDispatchThread() && watched.add(itemId)) image.addTo(repainter);
			return image;
		}
		int spriteId = spriteFor(task);
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

	static int itemFor(JsonObject task)
	{
		if (task == null || !"drop".equals(TsgHubUi.str(task, "type"))) return 0;
		JsonObject config = config(task);
		JsonArray groups = TsgHubUi.array(config, "itemGroups");
		if (groups.size() > 0 && groups.get(0).isJsonArray() && groups.get(0).getAsJsonArray().size() > 0)
			return TsgHubUi.integer(groups.get(0).getAsJsonArray().get(0).getAsJsonObject(), "id", 0);
		if ("pet".equals(TsgHubUi.str(config, "itemGroup"))) return ItemID.MOLEPET;
		if ("jar".equals(TsgHubUi.str(config, "itemGroup"))) return ItemID.JAR_OF_SWAMP;
		JsonArray ids = TsgHubUi.array(config, "itemIds");
		for (int i = 0; i < ids.size(); i++) if (ids.get(i).isJsonPrimitive() && ids.get(i).getAsInt() > 0) return ids.get(i).getAsInt();
		return 0;
	}

	static int spriteFor(JsonObject task)
	{
		if (task == null) return 0;
		JsonObject config = config(task);
		switch (TsgHubUi.str(task, "type"))
		{
			case "kill":
				String npc = TsgHubUi.str(config, "npcName");
				Integer sprite = SPRITES.get(key(npc));
				if (sprite == null) sprite = SPRITES.get(key(ActivityDetector.bossFor(npc)));
				return sprite == null ? 0 : sprite;
			case "raid":
				JsonArray modes = TsgHubUi.array(config, "modes");
				String mode = modes.size() > 0 && modes.get(0).isJsonPrimitive() ? modes.get(0).getAsString() : "";
				Integer raid = SPRITES.get(key(raidName(mode)));
				return raid == null ? 0 : raid;
			case "xp":
				Integer skill = SPRITES.get(key(TsgHubUi.str(config, "skill")));
				return skill == null ? 0 : skill;
			default:
				return 0;
		}
	}

	static String raidName(String mode)
	{
		switch (mode)
		{
			case "cox": return "Chambers of Xeric";
			case "cox_cm": return "Chambers of Xeric: Challenge Mode";
			case "tob": case "tob_entry": return "Theatre of Blood";
			case "tob_hm": return "Theatre of Blood: Hard Mode";
			case "toa": case "toa_entry": return "Tombs of Amascut";
			case "toa_expert": return "Tombs of Amascut: Expert Mode";
			default: return "";
		}
	}

	private static JsonObject config(JsonObject task)
	{
		return task.has("config") && task.get("config").isJsonObject() ? task.getAsJsonObject("config") : new JsonObject();
	}

	private static String key(String name)
	{
		if (name == null) return "";
		StringBuilder key = new StringBuilder();
		for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) if (Character.isLetterOrDigit(c)) key.append(c);
		return key.toString();
	}

	private static Map<String, Integer> sprites()
	{
		Map<String, Integer> sprites = new HashMap<>();
		for (HiscoreSkill skill : HiscoreSkill.values())
		{
			if (skill.getSpriteId() <= 0) continue;
			sprites.put(key(skill.getName()), skill.getSpriteId());
			sprites.putIfAbsent(key(skill.name()), skill.getSpriteId());
		}
		return sprites;
	}
}
