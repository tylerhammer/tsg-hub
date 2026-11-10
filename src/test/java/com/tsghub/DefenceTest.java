package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tsghub.TsgHubDefenceTarget.Boss;
import java.util.Arrays;
import java.util.Map;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import org.junit.Test;

public class DefenceTest
{
	private static final Map<String, Boss> BOSSES = TsgHubDefence.load(new Gson());

	private static TsgHubDefenceTarget target(String name)
	{
		return new TsgHubDefenceTarget(TsgHubDefence.boss(BOSSES, name), false, 1, false);
	}

	@Test
	public void loadsTableAndMatchesNames()
	{
		assertTrue(BOSSES.size() > 60);
		assertEquals("General Graardor", TsgHubDefence.boss(BOSSES, "<col=ffff00>General Graardor</col>").name);
		assertEquals("Core", TsgHubDefence.boss(BOSSES, "Core").name);
		assertEquals("Great Olm (Left claw)", TsgHubDefence.boss(BOSSES, "Great Olm (Left claw)").name);
		assertNull(TsgHubDefence.boss(BOSSES, "Goblin"));
		assertNull(TsgHubDefence.boss(BOSSES, null));
	}

	@Test
	public void warhammerTakesThirtyPercentOfCurrent()
	{
		TsgHubDefenceTarget t = target("General Graardor");
		t.drain(SpecialWeapon.DRAGON_WARHAMMER, 40, false);
		assertEquals(175, t.def);
		t.drain(SpecialWeapon.DRAGON_WARHAMMER, 40, false);
		assertEquals(123, t.def);
		t.drain(SpecialWeapon.DRAGON_WARHAMMER, 0, false);
		assertEquals(123, t.def);
	}

	@Test
	public void bandosDoublesOnCorpAndStopsAtFloor()
	{
		TsgHubDefenceTarget corp = target("Corporeal Beast");
		corp.drain(SpecialWeapon.BANDOS_GODSWORD, 50, false);
		assertEquals(210, corp.def);
		TsgHubDefenceTarget nex = target("Nex");
		nex.drain(SpecialWeapon.BANDOS_GODSWORD, 70, false);
		assertEquals(250, nex.def);
		assertTrue(nex.atFloor());
	}

	@Test
	public void arclightUsesStartingDefenceAndDemonBonus()
	{
		TsgHubDefenceTarget kril = target("K'ril Tsutsaroth");
		kril.drain(SpecialWeapon.ARCLIGHT, 10, false);
		assertEquals(270 - 28, kril.def);
		kril.drain(SpecialWeapon.ARCLIGHT, 10, false);
		assertEquals(270 - 56, kril.def);
	}

	@Test
	public void boneDaggerOnlyDrainsUndrainedTarget()
	{
		TsgHubDefenceTarget t = target("Vorkath");
		t.drain(SpecialWeapon.BONE_DAGGER, 20, false);
		assertEquals(194, t.def);
		t.drain(SpecialWeapon.BONE_DAGGER, 20, false);
		assertEquals(194, t.def);
	}

	@Test
	public void coxScalesDefenceByPartySize()
	{
		TsgHubDefenceTarget solo = new TsgHubDefenceTarget(TsgHubDefence.boss(BOSSES, "Tekton"), true, 1, false);
		assertEquals(205, solo.def);
		TsgHubDefenceTarget five = new TsgHubDefenceTarget(TsgHubDefence.boss(BOSSES, "Tekton"), true, 5, false);
		assertEquals(205 * (100 + 2 + 2) / 100, five.def);
	}

	@Test
	public void chatMessageShowsChangedStats()
	{
		assertEquals("Tyler: DWH hit Tekton, Def 205 to 144",
			TsgHubDefence.message("Tyler", SpecialWeapon.DRAGON_WARHAMMER, 40, "Tekton", new int[]{205, 205, 0}, new int[]{144, 205, 0}));
		assertEquals("Bob: BGS missed Tekton, Def 205",
			TsgHubDefence.message("Bob", SpecialWeapon.BANDOS_GODSWORD, 0, "Tekton", new int[]{205, 205, 0}, new int[]{205, 205, 0}));
		assertEquals("Bob: Seercull hit Vorkath, Magic 150 to 120",
			TsgHubDefence.message("Bob", SpecialWeapon.SEERCULL, 30, "Vorkath", new int[]{214, 150, 240}, new int[]{214, 120, 240}));
		assertEquals("Amy: Ayak missed Vorkath, Magic def 240",
			TsgHubDefence.message("Amy", SpecialWeapon.EYE_OF_AYAK, 0, "Vorkath", new int[]{214, 150, 240}, new int[]{214, 150, 240}));
		assertEquals("Amy: Sceptre hit Vorkath, Def 214 to 181, Magic 150 to 127",
			TsgHubDefence.message("Amy", SpecialWeapon.ACCURSED_SCEPTRE, 20, "Vorkath", new int[]{214, 150, 240}, new int[]{181, 127, 240}));
	}

	@Test
	public void ayakLowersMagicDefenceBonusOnly()
	{
		TsgHubDefenceTarget t = target("Vorkath");
		t.drain(SpecialWeapon.EYE_OF_AYAK, 45, false);
		assertEquals(214, t.def);
		assertEquals(195, t.magicDef);
		t.drain(SpecialWeapon.EYE_OF_AYAK, 300, false);
		assertEquals(0, t.magicDef);
		TsgHubDefenceTarget core = target("Core");
		core.drain(SpecialWeapon.EYE_OF_AYAK, 20, false);
		assertEquals(-30, core.magicDef);
	}

	@Test
	public void resolvesRalosGlaives()
	{
		assertEquals(1, TsgHubDefence.resolve(SpecialWeapon.TONALZTICS_OF_RALOS, Arrays.asList(12, 0)));
		assertEquals(-1, TsgHubDefence.resolve(SpecialWeapon.TONALZTICS_OF_RALOS, Arrays.asList(12)));
	}
}
