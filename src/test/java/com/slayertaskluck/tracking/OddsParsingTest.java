package com.slayertaskluck.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.SnapshotEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class OddsParsingTest
{
	@Test
	public void parsesRowsIncludingTagsAndVaries()
	{
		final List<SnapshotEntry> rows = OddsParser.parse(Arrays.asList(
			"Slayer Tasks", "Araxytes (60-80)", "", "0.0%", "Boss (Varies)", "0.0%",
			"<col=ff9040>Gargoyles (130-200)</col>", "4.9%", "stray 1.0%"));
		assertEquals(3, rows.size());
		assertEquals("Araxytes", rows.get(0).getName());
		assertEquals(60, rows.get(0).getMin());
		assertEquals(80, rows.get(0).getMax());
		assertEquals("Boss", rows.get(1).getName());
		assertEquals(0, rows.get(1).getMax());
		assertEquals("Gargoyles", rows.get(2).getName());
		assertEquals(4.9, rows.get(2).getShownPercent(), 1e-9);
	}

	@Test
	public void parsesFullKuradalList()
	{
		assertEquals(43, OddsParser.parse(KuradalFixture.texts(KuradalFixture.NO_TASK)).size());
	}

	@Test
	public void recoversExactWeightsFromTruncatedPercentages()
	{
		final OddsSnapshot s = snapshot(KuradalFixture.NO_TASK, null);
		assertTrue(s.isWeightsExact());
		assertEquals(161, s.getTotalWeight());
		for (SnapshotEntry e : s.getEntries())
		{
			assertEquals(e.getName(), KuradalFixture.expectedWeight(e.getName()), e.getWeight());
		}
	}

	@Test
	public void currentTaskShowsAsZeroAndRescalesTheRest()
	{
		final OddsSnapshot s = snapshot(KuradalFixture.ON_FIRE_GIANTS, "Fire Giants");
		assertTrue(s.isWeightsExact());
		assertEquals(154, s.getTotalWeight());
		assertEquals("Fire Giants", s.getHeldTask());
		assertEquals(8, OddsModel.find(s, "Gargoyles").getWeight());
	}

	@Test
	public void heldTaskWeightIsBorrowedFromAnotherSnapshot()
	{
		final OddsSnapshot full = snapshot(KuradalFixture.NO_TASK, null);
		final OddsSnapshot held = snapshot(KuradalFixture.ON_FIRE_GIANTS, "Fire Giants");
		final Map<String, Integer> borrowed = OddsModel.learnedWeights(Arrays.asList(held, full));

		assertFalse(OddsModel.missingHeldWeight(held, null, borrowed));
		final Map<String, Double> dist = OddsModel.distribution(held, null, borrowed);
		assertEquals(7 / 161.0, dist.get("Fire Giants"), 1e-12);
		assertEquals(8 / 161.0, dist.get("Gargoyles"), 1e-12);
		assertTrue(OddsModel.missingHeldWeight(held, null, Collections.emptyMap()));
	}

	@Test
	public void activeTaskIsLeftOutOfTheRoll()
	{
		final OddsSnapshot full = snapshot(KuradalFixture.NO_TASK, null);
		final Map<String, Double> dist = OddsModel.distribution(full, "Gargoyles", null);
		assertNull(dist.get("Gargoyles"));
		assertEquals(12 / 153.0, dist.get("Abyssal Demons"), 1e-12);
	}

	@Test
	public void scoresWantedAndBossFallback()
	{
		final OddsSnapshot full = snapshot(KuradalFixture.NO_TASK, null);
		final Map<String, Double> dist = OddsModel.distribution(full, null, null);

		final OddsModel.Scored hit = OddsModel.score(dist, "Abyssal Demons", Arrays.asList("abyssal demons", "Dark Beasts", "Dark Beasts"));
		assertEquals("Abyssal Demons", hit.getMatched());
		assertTrue(hit.isWanted());
		assertEquals(23 / 161.0, hit.getPWanted(), 1e-12);

		final OddsModel.Scored miss = OddsModel.score(dist, "Kalphite", Collections.singletonList("Dark Beasts"));
		assertNull("Kalphites is blocked (0%), so it can't match", miss.getMatched());

		final OddsModel.Scored gargoyles = OddsModel.score(dist, "Gargoyles", Collections.singletonList("Dark Beasts"));
		assertNotNull(gargoyles.getMatched());
		assertFalse(gargoyles.isWanted());
	}

	@Test
	public void identicalListsHashTheSame()
	{
		assertEquals(snapshot(KuradalFixture.NO_TASK, null).getContentHash(), snapshot(KuradalFixture.NO_TASK, null).getContentHash());
		assertFalse(snapshot(KuradalFixture.NO_TASK, null).getContentHash()
			.equals(snapshot(KuradalFixture.ON_FIRE_GIANTS, "Fire Giants").getContentHash()));
	}

	@Test
	public void weightInferenceRejectsImpossibleLists()
	{
		assertNull(WeightInference.infer(new int[]{0, 0}));
		assertNull(WeightInference.infer(new int[]{999, 999}));
	}

	@Test
	public void mastersGroupAndFillers()
	{
		assertEquals("duradel", Masters.groupOf("Kuradal", 5));
		assertEquals("duradel", Masters.groupOf("Duradel", 5));
		assertEquals("turael", Masters.groupOf("Aya", 1));
		assertEquals("slot5", Masters.groupOf(null, 5));
		assertTrue(Masters.isFiller("Aya", 1, true, false, false));
		assertFalse(Masters.isFiller("Turael", 1, true, false, false));
		assertFalse(Masters.isFiller("Kuradal", 5, true, true, true));
		assertTrue(Masters.isFiller(null, 1, true, false, false));
		assertEquals("Some Player", Masters.cleanName("<col=ffffff>Some Player</col>"));
	}

	static OddsSnapshot snapshot(String[][] rows, String held)
	{
		return OddsModel.buildSnapshot("duradel", "Kuradal", 1000, 90, 120,
			OddsParser.parse(KuradalFixture.texts(rows)), held);
	}
}
