package com.tsghub;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class TsgHubOutbox<T extends TsgHubOutbox.Item>
{
	static final long MAX_AGE_MILLIS = 600_000;
	static final int MAX_PENDING = 200;

	abstract static class Item
	{
		final long createdAt;

		Item(long now)
		{
			createdAt = now;
		}
	}

	private final Map<String, T> items = new LinkedHashMap<>();

	void add(String id, T item)
	{
		items.put(id, item);
		while (items.size() > MAX_PENDING) items.remove(items.keySet().iterator().next());
	}

	T get(String id)
	{
		return items.get(id);
	}

	void remove(String id)
	{
		items.remove(id);
	}

	void expire(long now)
	{
		items.values().removeIf(item -> now - item.createdAt > MAX_AGE_MILLIS);
	}

	Collection<T> values()
	{
		return items.values();
	}

	int size()
	{
		return items.size();
	}

	void clear()
	{
		items.clear();
	}
}
