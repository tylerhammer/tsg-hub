package com.tsghub;

import java.util.Locale;

final class TsgHubCofferWatch
{
	static final long CHAT_WINDOW_MILLIS = 15_000;

	enum Kind
	{
		SNAPSHOT, DEPOSIT, WITHDRAWAL;

		String wire()
		{
			return name().toLowerCase(Locale.ROOT);
		}
	}

	static final class Change
	{
		final Kind kind;
		final long before;
		final long after;
		final boolean confirmed;

		Change(Kind kind, long before, long after, boolean confirmed)
		{
			this.kind = kind;
			this.before = before;
			this.after = after;
			this.confirmed = confirmed;
		}
	}

	private long balance = -1;
	private Kind chatKind;
	private long chatAt;

	void reset()
	{
		balance = -1;
		chatKind = null;
		chatAt = 0;
	}

	void chat(String line, String self, long now)
	{
		Kind kind = chatKind(line, self);
		if (kind == null) return;
		chatKind = kind;
		chatAt = now;
	}

	Change observe(long coins, long now)
	{
		if (coins < 0) return null;
		if (balance < 0)
		{
			balance = coins;
			return new Change(Kind.SNAPSHOT, coins, coins, false);
		}
		if (coins == balance) return null;
		Kind kind = coins > balance ? Kind.DEPOSIT : Kind.WITHDRAWAL;
		boolean confirmed = chatKind == kind && now >= chatAt && now - chatAt <= CHAT_WINDOW_MILLIS;
		Change change = new Change(kind, balance, coins, confirmed);
		balance = coins;
		chatKind = null;
		chatAt = 0;
		return change;
	}

	static Kind chatKind(String line, String self)
	{
		if (line == null) return null;
		String text = PlayerNames.key(line);
		if (!text.contains("coffer")) return null;
		String name = PlayerNames.key(self);
		boolean mine = text.startsWith("you ") || text.startsWith("you've ") || (!name.isEmpty() && text.startsWith(name + " "));
		if (!mine) return null;
		if (text.contains("deposited") || text.contains("donated")) return Kind.DEPOSIT;
		if (text.contains("withdrew") || text.contains("withdrawn")) return Kind.WITHDRAWAL;
		return null;
	}
}
