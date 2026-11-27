package com.tsghub;

import java.util.*;

final class TsgHubCaskets
{
	static final List<String> TIERS = Arrays.asList("beginner", "easy", "medium", "hard", "elite", "master");
	private static final String CASKET = "reward casket (";
	private static final String[] CLUES = {"clue scroll (", "challenge scroll (", "puzzle box ("};
	private static final int WINDOW = 2;

	private Map<String, Integer> caskets;
	private Map<String, Integer> clues;
	private final Map<String, Integer> clueGone = new HashMap<>();
	private final Map<String, Integer> casketGained = new HashMap<>();
	private final Map<String, Integer> gainedCount = new HashMap<>();

	static String casketTier(String name)
	{
		return tier(name, CASKET);
	}

	static String clueTier(String name)
	{
		for (String prefix : CLUES)
		{
			String tier = tier(name, prefix);
			if (tier != null) return tier;
		}
		return null;
	}

	private static String tier(String name, String prefix)
	{
		if (name == null) return null;
		String lower = name.toLowerCase(Locale.ROOT).trim();
		if (!lower.startsWith(prefix) || !lower.endsWith(")")) return null;
		String tier = lower.substring(prefix.length(), lower.length() - 1);
		return TIERS.contains(tier) ? tier : null;
	}

	List<String> update(Map<String, Integer> nowCaskets, Map<String, Integer> nowClues, int tick, boolean storageOpen)
	{
		Map<String, Integer> lastCaskets = caskets, lastClues = clues;
		caskets = nowCaskets;
		clues = nowClues;
		List<String> out = new ArrayList<>();
		if (lastCaskets == null || storageOpen)
		{
			clearPending();
			return out;
		}
		clueGone.values().removeIf(at -> tick - at > WINDOW);
		casketGained.entrySet().removeIf(e -> {
			boolean stale = tick - e.getValue() > WINDOW;
			if (stale) gainedCount.remove(e.getKey());
			return stale;
		});
		for (String tier : TIERS)
		{
			if (count(nowClues, tier) < count(lastClues, tier)) clueGone.put(tier, tick);
			int gained = count(nowCaskets, tier) - count(lastCaskets, tier);
			if (gained > 0)
			{
				casketGained.put(tier, tick);
				gainedCount.merge(tier, gained, Integer::sum);
			}
			if (!casketGained.containsKey(tier) || !clueGone.containsKey(tier)) continue;
			for (int i = gainedCount.remove(tier); i > 0; i--) out.add(tier);
			casketGained.remove(tier);
			clueGone.remove(tier);
		}
		return out;
	}

	void reset()
	{
		caskets = null;
		clues = null;
		clearPending();
	}

	private void clearPending()
	{
		clueGone.clear();
		casketGained.clear();
		gainedCount.clear();
	}

	private static int count(Map<String, Integer> counts, String tier)
	{
		return counts.getOrDefault(tier, 0);
	}
}
