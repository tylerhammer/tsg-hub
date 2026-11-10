package com.tsghub;

import net.runelite.client.plugins.specialcounter.SpecialWeapon;

final class TsgHubDefenceTarget
{
	static final class Boss
	{
		final String name;
		final int def;
		final int magic;
		final int atk;
		final int str;
		final int magicDef;
		final int floor;
		final String flags;

		Boss(String name, int def, int magic, int atk, int str, int magicDef, int floor, String flags)
		{
			this.name = name;
			this.def = def;
			this.magic = magic;
			this.atk = atk;
			this.str = str;
			this.magicDef = magicDef;
			this.floor = floor;
			this.flags = flags;
		}

		boolean has(char flag)
		{
			return flags.indexOf(flag) >= 0;
		}
	}

	final Boss boss;
	final int startDef;
	final int floor;
	int def;
	int magic;
	int atk;
	int str;
	int magicDef;
	private boolean condemned;

	TsgHubDefenceTarget(Boss boss, boolean inCox, int partySize, boolean challengeMode)
	{
		this.boss = boss;
		int def = boss.def;
		int magic = boss.magic;
		if (inCox && boss.has('C'))
		{
			int n = Math.max(1, partySize) - 1;
			int defensive = 100 + (int) Math.sqrt(n) + n * 7 / 10;
			int offensive = 100 + (int) Math.sqrt(n) * 7 + n;
			boolean magicDefensive = boss.has('M');
			def = def * defensive / 100;
			magic = magic * (magicDefensive ? defensive : offensive) / 100;
			if (challengeMode)
			{
				int cmDef = boss.has('T') ? (partySize < 4 ? 20 : 35) : 50;
				def += def * cmDef / 100;
				magic += magic * (magicDefensive ? cmDef : 50) / 100;
			}
		}
		this.def = def;
		this.startDef = def;
		this.magic = magic;
		this.atk = boss.atk;
		this.str = boss.str;
		this.magicDef = boss.magicDef;
		this.floor = boss.floor;
	}

	boolean atFloor()
	{
		return def <= floor;
	}

	void drain(SpecialWeapon weapon, int hit, boolean missDrain)
	{
		boolean demon = boss.has('D');
		switch (weapon)
		{
			case DRAGON_WARHAMMER:
				if (hit > 0) def -= def * 30 / 100;
				else if (missDrain) def -= def * 5 / 100;
				break;
			case ELDER_MAUL:
				if (hit > 0) def -= def * 35 / 100;
				else if (missDrain) def -= def * 5 / 100;
				break;
			case BANDOS_GODSWORD:
				if (hit > 0) bandos(boss.name.equals("Corporeal Beast") || boss.name.equals("Pestilent Bloat") ? hit * 2 : hit);
				break;
			case TONALZTICS_OF_RALOS:
				for (int i = 0; i < hit; i++) def -= magic / 8;
				break;
			case ARCLIGHT:
			case DARKLIGHT:
				if (hit > 0) def -= startDef * (demon ? 10 : 5) / 100 + 1;
				break;
			case EMBERLIGHT:
				if (hit > 0) def -= startDef * (demon ? 15 : 5) / 100 + 1;
				break;
			case BARRELCHEST_ANCHOR:
				def -= hit / 10;
				break;
			case BONE_DAGGER:
			case DORGESHUUN_CROSSBOW:
				if (def >= startDef) def -= hit;
				break;
			case ACCURSED_SCEPTRE:
				if (hit > 0 && !condemned)
				{
					condemned = true;
					def = def * 85 / 100;
					magic = magic * 85 / 100;
				}
				break;
			case SEERCULL:
				magic -= hit;
				break;
			case EYE_OF_AYAK:
				if (hit > 0 && magicDef > 0) magicDef = Math.max(0, magicDef - hit);
				break;
			default:
				return;
		}
		def = Math.max(def, floor);
		magic = Math.max(magic, 0);
	}

	private void bandos(int damage)
	{
		int start = def;
		def = Math.max(floor, def - damage);
		damage = def > 0 ? 0 : damage - start;
		if (damage > 0)
		{
			start = str;
			str = Math.max(0, str - damage);
			damage = str > 0 ? 0 : damage - start;
		}
		if (damage > 0)
		{
			start = atk;
			atk = Math.max(0, atk - damage);
			damage = atk > 0 ? 0 : damage - start;
		}
		if (damage > 0) magic = Math.max(0, magic - damage);
	}

	static boolean drains(SpecialWeapon weapon)
	{
		switch (weapon)
		{
			case DRAGON_WARHAMMER:
			case ELDER_MAUL:
			case BANDOS_GODSWORD:
			case TONALZTICS_OF_RALOS:
			case ARCLIGHT:
			case DARKLIGHT:
			case EMBERLIGHT:
			case BARRELCHEST_ANCHOR:
			case BONE_DAGGER:
			case DORGESHUUN_CROSSBOW:
			case ACCURSED_SCEPTRE:
			case SEERCULL:
			case EYE_OF_AYAK:
				return true;
			default:
				return false;
		}
	}
}
