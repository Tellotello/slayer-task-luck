package com.slayertaskluck.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.slayertaskluck.math.FairnessCache;
import com.slayertaskluck.math.GroupStats;
import com.slayertaskluck.math.StatsCalculator;
import com.slayertaskluck.math.Verdict;
import com.slayertaskluck.model.Confidence;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.Roll;
import com.slayertaskluck.model.TrackerData;
import com.slayertaskluck.ui.TrackerView;
import com.slayertaskluck.ui.ViewBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Replays a Turael-skip session through scoring and stats and checks the numbers shown. */
public class ReplayTest
{
	private static final long MIN = 60_000L;
	private static final double WANTED_P = 23 / 161.0; // Abyssal Demons (12) + Dark Beasts (11)

	private static TrackerData session()
	{
		final TrackerData d = new TrackerData();
		d.getSnapshots().add(OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null)); // timestamp 1000
		d.getWanted().put("duradel", Arrays.asList("Abyssal Demons", "Dark Beasts"));

		add(d, 1 * MIN, "Kuradal", "Gargoyles", false);
		add(d, 2 * MIN, "Aya", "Crawling Hands", true);
		add(d, 11 * MIN, "Kuradal", "Trolls", false);
		add(d, 12 * MIN, "Aya", "Cows", true);
		add(d, 21 * MIN, "Kuradal", "Abyssal Demons", false);
		add(d, 60 * MIN, "Kuradal", "Gargoyles", false);
		add(d, 61 * MIN, "Aya", "Rats", true);
		add(d, 70 * MIN, "Kuradal", "Kalphite", false); // blocked: not on the list -> NONE
		return d;
	}

	private static void add(TrackerData d, long t, String master, String task, boolean filler)
	{
		d.getRolls().add(RollScorer.build(d, RollDraft.builder()
			.timestamp(t)
			.masterName(master)
			.masterSlot(filler ? 1 : 5)
			.masterGroup(Masters.groupOf(master, filler ? 1 : 5))
			.task(task)
			.amount(100)
			.filler(filler)
			.slayerLevel(90)
			.combatLevel(120)
			.build()));
	}

	@Test
	public void scoresEachRollFromTheSnapshot()
	{
		final TrackerData d = session();
		final Roll first = d.getRolls().get(0);
		assertEquals(Confidence.CURRENT, first.getConfidence());
		assertEquals(8 / 161.0, first.getPTask(), 1e-12);
		assertEquals(WANTED_P, first.getPWanted(), 1e-12);
		assertFalse(first.getWanted());

		final Roll filler = d.getRolls().get(1);
		assertTrue(filler.isFiller());
		assertNull(filler.getPWanted());

		assertTrue(d.getRolls().get(4).getWanted());
		assertEquals(Confidence.NONE, d.getRolls().get(7).getConfidence());
	}

	@Test
	public void statsMatchTheSession()
	{
		final GroupStats s = StatsCalculator.compute(session(), "duradel", "Kuradal", 0.1, 0.1, true);
		assertEquals(4, s.getRolls());
		assertEquals(1, s.getUncounted());
		assertEquals(1, s.getObserved());
		assertEquals(4 * WANTED_P, s.getExpected(), 1e-12);
		assertEquals(1, s.getDryRolls());
		assertEquals(1 - WANTED_P, s.getDryProbability(), 1e-12);
		assertEquals(Verdict.Tone.NORMAL, s.getDryVerdict().getTone());
		assertEquals(2, s.getLongestDry());
		// One filler since the last wanted roll, timed until the next assignment (9 minutes).
		assertEquals(1, s.getDetours());
		assertEquals(9 * MIN, s.getDetourMillis());
		assertEquals(3, s.getTotalDetours());
		assertEquals(4, s.getChartDiff().length);
		assertEquals(1 - 4 * WANTED_P, s.getChartDiff()[3], 1e-12);
		assertEquals(WANTED_P, s.getChancePerRoll(), 1e-12);
		assertNull("fairness waits for 50 rolls", s.getFairness());
	}

	@Test
	public void excludingARollTakesItOutOfTheStats()
	{
		final TrackerData d = session();
		d.getRolls().get(4).setExcluded(true); // the wanted Abyssal Demons roll
		final GroupStats s = StatsCalculator.compute(d, "duradel", "Kuradal", 0.1, 0.1, false);
		assertEquals(3, s.getRolls());
		assertEquals(0, s.getObserved());
		assertEquals(3, s.getDryRolls());
	}

	@Test
	public void rescoringAppliesANewWantedList()
	{
		final TrackerData d = session();
		d.getWanted().put("duradel", Arrays.asList("Gargoyles"));
		for (Roll r : d.getRolls())
		{
			if (!r.isFiller())
			{
				RollScorer.score(d, r);
			}
		}
		final GroupStats s = StatsCalculator.compute(d, "duradel", "Kuradal", 0.1, 0.1, false);
		assertEquals(2, s.getObserved());
		assertEquals(4 * 8 / 161.0, s.getExpected(), 1e-12);
	}

	@Test
	public void copyIsEqualButIndependent()
	{
		final TrackerData d = session();
		final TrackerData c = d.copy();
		assertEquals(d, c);
		c.getRolls().get(0).setExcluded(true);
		c.getWanted().get("duradel").add("Trolls");
		assertFalse(d.getRolls().get(0).isExcluded());
		assertFalse(d.getWanted().get("duradel").contains("Trolls"));
	}

	@Test
	public void fairnessIsReusedUntilTheCountedRollsChange()
	{
		final TrackerData d = new TrackerData();
		d.getSnapshots().add(OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null));
		d.getWanted().put("duradel", new java.util.ArrayList<>(Arrays.asList("Abyssal Demons")));
		for (int i = 0; i < 55; i++)
		{
			add(d, 2000 + i * MIN, "Kuradal", i % 2 == 0 ? "Gargoyles" : "Trolls", false);
		}
		final FairnessCache cache = new FairnessCache();
		final GroupStats first = StatsCalculator.compute(d, "duradel", "Kuradal", 0.1, 0.1, true, cache);
		d.getWanted().put("duradel", Arrays.asList("Trolls")); // wanted list doesn't affect fairness
		final GroupStats again = StatsCalculator.compute(d, "duradel", "Kuradal", 0.1, 0.1, true, cache);
		assertSame(first.getFairness(), again.getFairness());

		add(d, 900 * MIN, "Kuradal", "Drakes", false);
		final GroupStats after = StatsCalculator.compute(d, "duradel", "Kuradal", 0.1, 0.1, true, cache);
		assertEquals(56, after.getFairness().getRolls());
	}

	@Test
	public void pickerListsEveryTargetMasterUsedOnesFirst()
	{
		final TrackerData d = session();
		d.getMasterNames().put("duradel", "Kuradal"); // the store records the name last seen
		final TrackerView v = view(d);
		final List<String> names = new ArrayList<>();
		for (TrackerView.MasterOption o : v.getMasters())
		{
			names.add(o.getName());
		}
		assertEquals("Kuradal", names.get(0));
		assertEquals(Arrays.asList("Kuradal", "Mazchna", "Vannaka", "Chaeldar", "Konar", "Nieve", "Krystilia"), names);
		assertEquals("duradel", v.getDefaultGroup());
		assertEquals(0, v.getStats().get("konar").getRolls());
		assertTrue(v.getNotices().get("konar").get(0).startsWith("Open Konar's task list"));
	}

	@Test
	public void heldTaskCanBeTickedBeforeItsChanceIsKnown()
	{
		final TrackerData d = new TrackerData();
		d.getSnapshots().add(OddsParsingTest.snapshot(KuradalFixture.ON_FIRE_GIANTS, "Fire Giants"));
		d.getWanted().put("duradel", new ArrayList<>(Arrays.asList("Abyssal Demons", "Kalphite Queen")));

		TrackerView.WantedOption fire = option(view(d), "Fire Giants");
		assertNull("the list shows a held task as 0%", fire.getChance());
		assertFalse(fire.isWanted());
		assertTrue(fire.getNote() != null);
		final TrackerView.WantedOption gone = option(view(d), "Kalphite Queen");
		assertEquals(0.0, gone.getChance(), 0);
		assertTrue("a wanted task off the list can still be unticked", gone.isWanted());

		// An older list opened without a task supplies the weight.
		final OddsSnapshot older = OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null);
		older.setTimestamp(500);
		d.getSnapshots().add(older);
		fire = option(view(d), "Fire Giants");
		assertEquals(KuradalFixture.expectedWeight("Fire Giants") / 161.0, fire.getChance(), 1e-12);
		assertNull(fire.getNote());
	}

	private static TrackerView view(TrackerData d)
	{
		return ViewBuilder.build(d, TrackerView.State.READY, null, null, 0.1, 0.1,
			name -> Masters.isFiller(name, 0, true, true, true), 90, 120, null);
	}

	private static TrackerView.WantedOption option(TrackerView v, String task)
	{
		for (TrackerView.WantedOption o : v.getWantedOptions().get("duradel"))
		{
			if (o.getTask().equals(task))
			{
				return o;
			}
		}
		throw new AssertionError(task + " isn't offered");
	}

	@Test
	public void fairnessRunsOnceThereAreEnoughRolls()
	{
		final TrackerData d = new TrackerData();
		d.getSnapshots().add(OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null));
		d.getWanted().put("duradel", Arrays.asList("Abyssal Demons"));
		final String[] tasks = {"Gargoyles", "Trolls", "Drakes", "Wyrms", "Hellhounds", "Nechryael", "Ankou", "Suqahs", "Elves", "Bloodveld"};
		for (int i = 0; i < 60; i++)
		{
			add(d, 2000 + i * MIN, "Kuradal", tasks[i % tasks.length], false);
		}
		final GroupStats s = StatsCalculator.compute(d, "duradel", "Kuradal", 0.1, 0.1, true);
		assertEquals(60, s.getFairness().getRolls());
		assertTrue(s.getFairness().getPValue() > 0 && s.getFairness().getPValue() <= 1);
		assertEquals(Verdict.Tone.UNLUCKY, s.getDryVerdict().getTone()); // 60 rolls, 0 abyssals at 7.45%
	}
}
