package com.slayertaskluck.math;

import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.Roll;
import com.slayertaskluck.model.TrackerData;
import com.slayertaskluck.tracking.OddsModel;
import com.slayertaskluck.tracking.RollScorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds {@link GroupStats} from saved data. Pure; runs on the plugin's background thread. */
public final class StatsCalculator
{
	/** Fairness needs this many counted rolls at a master before it says anything. */
	public static final int FAIRNESS_MINIMUM = 50;
	/** One detour longer than this (e.g. logged out overnight) is capped so time totals stay meaningful. */
	static final long MAX_DETOUR_MS = 2 * 60 * 60 * 1000L;
	/** Simulated rolls per fairness run (simulations x rolls). 1,000+ simulations is plenty for a fair/unusual verdict. */
	private static final long SIMULATION_BUDGET = 4_000_000L;
	private static final int MAX_SIMULATIONS = 20_000;
	private static final int MIN_SIMULATIONS = 500;

	private StatsCalculator()
	{
	}

	public static GroupStats compute(TrackerData data, String group, String name, double unluckyCut, double luckyCut,
		boolean withFairness)
	{
		return compute(data, group, name, unluckyCut, luckyCut, withFairness, null);
	}

	/**
	 * @param group master group, or null for all masters
	 * @param cache reuses the fairness result while the counted rolls are unchanged; may be null
	 */
	public static GroupStats compute(TrackerData data, String group, String name, double unluckyCut, double luckyCut,
		boolean withFairness, FairnessCache cache)
	{
		final List<Roll> all = new ArrayList<>(data.getRolls());
		all.sort(Comparator.comparingLong(Roll::getTimestamp));

		final List<Roll> counted = new ArrayList<>();
		int uncounted = 0;
		for (Roll r : all)
		{
			if (r.isFiller() || (group != null && !group.equals(r.getMasterGroup())))
			{
				continue;
			}
			if (r.countsForLuck())
			{
				counted.add(r);
			}
			else
			{
				uncounted++;
			}
		}

		final int n = counted.size();
		final double[] p = new double[n];
		int observed = 0;
		double expected = 0;
		double variance = 0;
		final double[] chartDiff = new double[n];
		final double[] chartSd = new double[n];
		int run = 0;
		int longest = 0;
		for (int i = 0; i < n; i++)
		{
			final Roll r = counted.get(i);
			p[i] = r.getPWanted();
			final boolean hit = Boolean.TRUE.equals(r.getWanted());
			observed += hit ? 1 : 0;
			expected += p[i];
			variance += p[i] * (1 - p[i]);
			chartDiff[i] = observed - expected;
			chartSd[i] = Math.sqrt(variance);
			run = hit ? 0 : run + 1;
			longest = Math.max(longest, run);
		}

		// Current dry streak: rolls since the last wanted one.
		int dry = 0;
		double dryProb = 1;
		for (int i = n - 1; i >= 0 && !Boolean.TRUE.equals(counted.get(i).getWanted()); i--)
		{
			dry++;
			dryProb *= 1 - p[i];
		}

		final double sd = Math.sqrt(variance);
		final Verdict luck;
		double percentile = Double.NaN;
		if (n > 0 && expected > 0)
		{
			percentile = LuckMath.midP(LuckMath.poissonBinomial(p), observed);
			luck = Verdict.fromPercentile(percentile, unluckyCut, luckyCut);
		}
		else
		{
			luck = Verdict.NONE;
		}
		final Verdict dryVerdict = n > 0 && expected > 0 ? Verdict.unluckySide(dryProb, unluckyCut) : Verdict.NONE;

		// Detours: filler tasks after the last wanted roll in scope, timed until the next assignment.
		int detours = 0;
		long detourMs = 0;
		int totalDetours = 0;
		long totalDetourMs = 0;
		for (int i = 0; i < all.size(); i++)
		{
			final Roll r = all.get(i);
			if (r.isFiller())
			{
				final long ms = i + 1 < all.size()
					? Math.min(MAX_DETOUR_MS, Math.max(0, all.get(i + 1).getTimestamp() - r.getTimestamp()))
					: 0;
				detours++;
				detourMs += ms;
				totalDetours++;
				totalDetourMs += ms;
			}
			else if (r.countsForLuck() && Boolean.TRUE.equals(r.getWanted())
				&& (group == null || group.equals(r.getMasterGroup())))
			{
				detours = 0;
				detourMs = 0;
			}
		}

		final GroupStats.GroupStatsBuilder b = GroupStats.builder()
			.group(group)
			.name(name)
			.allMasters(group == null)
			.rolls(n)
			.uncounted(uncounted)
			.dryRolls(dry)
			.dryProbability(dryProb)
			.dryVerdict(dryVerdict)
			.longestDry(longest)
			.longestDryProbability(n > 0 ? LuckMath.longestDryAtLeast(p, longest) : 1)
			.observed(observed)
			.expected(expected)
			.sd(sd)
			.z(sd > 0 ? (observed - expected) / sd : 0)
			.percentile(percentile)
			.luckVerdict(luck)
			.detours(detours)
			.detourMillis(detourMs)
			.totalDetours(totalDetours)
			.totalDetourMillis(totalDetourMs)
			.chartDiff(chartDiff)
			.chartSd(chartSd)
			.fairnessMinimum(FAIRNESS_MINIMUM);

		if (group != null)
		{
			final List<OddsSnapshot> snaps = RollScorer.snapshotsNewestFirst(data, group);
			final Map<String, Integer> borrowed = OddsModel.learnedWeights(snaps);
			if (!snaps.isEmpty())
			{
				final Map<String, Double> now = OddsModel.distribution(snaps.get(0), null, borrowed);
				final List<String> wanted = data.getWanted().getOrDefault(group, Collections.emptyList());
				b.chancePerRoll(OddsModel.score(now, "", wanted).getPWanted());
			}
			if (withFairness && n >= FAIRNESS_MINIMUM)
			{
				b.fairness(fairness(group, counted, snaps, borrowed, cache));
			}
		}
		return b.build();
	}

	private static Fairness.Result fairness(String group, List<Roll> counted, List<OddsSnapshot> snaps,
		Map<String, Integer> borrowed, FairnessCache cache)
	{
		// The result depends only on which rolls count and their odds, not on the wanted list.
		long signature = counted.size();
		for (Roll r : counted)
		{
			signature = signature * 31 + r.getId().hashCode();
			signature = signature * 31 + Objects.hashCode(r.getSnapshotId());
			signature = signature * 31 + Objects.hashCode(r.getExcludedTask());
		}
		for (OddsSnapshot s : snaps)
		{
			signature = signature * 31 + s.getId().hashCode();
		}
		if (cache != null)
		{
			final Fairness.Result cached = cache.get(group, signature);
			if (cached != null)
			{
				return cached;
			}
		}

		final Map<String, OddsSnapshot> byId = new HashMap<>();
		for (OddsSnapshot s : snaps)
		{
			byId.put(s.getId(), s);
		}
		final List<Map<String, Double>> dists = new ArrayList<>();
		final List<String> outcomes = new ArrayList<>();
		for (Roll r : counted)
		{
			final OddsSnapshot s = byId.get(r.getSnapshotId());
			if (s == null)
			{
				continue;
			}
			final Map<String, Double> dist = OddsModel.distribution(s, r.getExcludedTask(), borrowed);
			final OddsModel.Scored scored = OddsModel.score(dist, r.getTask(), Collections.emptyList());
			if (scored.getMatched() != null)
			{
				dists.add(dist);
				outcomes.add(scored.getMatched());
			}
		}
		final int sims = (int) Math.max(MIN_SIMULATIONS, Math.min(MAX_SIMULATIONS, SIMULATION_BUDGET / Math.max(1, dists.size())));
		// Fixed seed per data size: the result doesn't flicker between redraws.
		final Fairness.Result result = Fairness.compute(dists, outcomes, sims, 0x5EED + dists.size());
		if (cache != null)
		{
			cache.put(group, signature, result);
		}
		return result;
	}
}
