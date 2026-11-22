package com.slayertaskluck.ui;

import com.slayertaskluck.math.FairnessCache;
import com.slayertaskluck.math.GroupStats;
import com.slayertaskluck.math.StatsCalculator;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.Roll;
import com.slayertaskluck.model.TrackerData;
import com.slayertaskluck.tracking.OddsModel;
import com.slayertaskluck.tracking.RollScorer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Turns saved data into a {@link TrackerView}. Pure; runs on the plugin's background thread. */
public final class ViewBuilder
{
	private ViewBuilder()
	{
	}

	public static TrackerView build(TrackerData d, TrackerView.State state, String problem, String notice,
		double unluckyCut, double luckyCut, Predicate<String> isFillerName, int slayerLevel, int combatLevel,
		FairnessCache fairnessCache)
	{
		if (state != TrackerView.State.READY)
		{
			return TrackerView.EMPTY.toBuilder().state(state).problem(problem).build();
		}

		// Target masters, most recently active first.
		final Map<String, Long> lastActive = new LinkedHashMap<>();
		String fillerName = null;
		for (Roll r : d.getRolls())
		{
			if (r.isFiller())
			{
				fillerName = r.getMasterName();
			}
			else
			{
				lastActive.merge(r.getMasterGroup(), r.getTimestamp(), Math::max);
			}
		}
		for (OddsSnapshot s : d.getSnapshots())
		{
			if (!isFillerName.test(s.getMasterName()))
			{
				lastActive.merge(s.getMasterGroup(), s.getTimestamp(), Math::max);
			}
		}
		final List<String> groups = new ArrayList<>(lastActive.keySet());
		groups.sort((a, b) -> Long.compare(lastActive.get(b), lastActive.get(a)));

		final List<TrackerView.MasterOption> masters = new ArrayList<>();
		final Map<String, GroupStats> stats = new HashMap<>();
		final Map<String, List<TrackerView.WantedOption>> wanted = new HashMap<>();
		final Map<String, List<String>> notices = new HashMap<>();
		for (String g : groups)
		{
			final String name = d.getMasterNames().getOrDefault(g, capitalize(g));
			masters.add(new TrackerView.MasterOption(g, name));
			stats.put(g, StatsCalculator.compute(d, g, name, unluckyCut, luckyCut, true, fairnessCache));
			wanted.put(g, wantedOptions(d, g));
			notices.put(g, notices(d, g, name, slayerLevel, combatLevel));
		}
		if (groups.size() > 1)
		{
			stats.put(TrackerView.ALL, StatsCalculator.compute(d, null, "All masters", unluckyCut, luckyCut, false));
		}

		final List<Roll> history = new ArrayList<>(d.getRolls());
		history.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

		return TrackerView.builder()
			.state(state)
			.problem(problem)
			.notice(notice)
			.masters(masters)
			.defaultGroup(groups.isEmpty() ? null : lastTargetRollGroup(history, groups))
			.stats(stats)
			.history(history)
			.wantedOptions(wanted)
			.notices(notices)
			.fillerName(fillerName)
			.rollCount(d.getRolls().size())
			.snapshotCount(d.getSnapshots().size())
			.build();
	}

	private static String lastTargetRollGroup(List<Roll> newestFirst, List<String> groups)
	{
		for (Roll r : newestFirst)
		{
			if (!r.isFiller() && groups.contains(r.getMasterGroup()))
			{
				return r.getMasterGroup();
			}
		}
		return groups.get(0);
	}

	private static List<TrackerView.WantedOption> wantedOptions(TrackerData d, String group)
	{
		final List<OddsSnapshot> snaps = RollScorer.snapshotsNewestFirst(d, group);
		final Set<String> wantedKeys = new HashSet<>();
		for (String w : d.getWanted().getOrDefault(group, Collections.emptyList()))
		{
			wantedKeys.add(OddsModel.key(w));
		}
		final List<TrackerView.WantedOption> options = new ArrayList<>();
		if (snaps.isEmpty())
		{
			return options;
		}
		final Map<String, Double> dist = OddsModel.distribution(snaps.get(0), null, OddsModel.learnedWeights(snaps));
		for (Map.Entry<String, Double> e : dist.entrySet())
		{
			options.add(new TrackerView.WantedOption(e.getKey(), e.getValue(), wantedKeys.contains(OddsModel.key(e.getKey()))));
		}
		options.sort((a, b) -> Double.compare(b.getChance(), a.getChance()));
		return options;
	}

	private static List<String> notices(TrackerData d, String group, String name, int slayerLevel, int combatLevel)
	{
		final List<String> out = new ArrayList<>();
		final List<OddsSnapshot> snaps = RollScorer.snapshotsNewestFirst(d, group);
		if (snaps.isEmpty())
		{
			out.add("Open " + name + "'s task list (Rewards, then the task list) so the odds can be saved.");
			return out;
		}
		final OddsSnapshot latest = snaps.get(0);
		final List<String> reasons = new ArrayList<>();
		if (slayerLevel > 0 && latest.getSlayerLevel() > 0 && slayerLevel != latest.getSlayerLevel())
		{
			reasons.add("Slayer level");
		}
		if (combatLevel > 0 && latest.getCombatLevel() > 0 && combatLevel != latest.getCombatLevel())
		{
			reasons.add("combat level");
		}
		if (d.getBlockedChangedAt() > latest.getTimestamp())
		{
			reasons.add("blocklist");
		}
		if (!reasons.isEmpty())
		{
			out.add("Your " + String.join(" and ", reasons) + " changed since you last opened " + name
				+ "'s task list. Open it again so the odds stay exact.");
		}
		if (OddsModel.missingHeldWeight(latest, null, OddsModel.learnedWeights(snaps)))
		{
			out.add("Open " + name + "'s task list once while you have no task, so the odds include "
				+ latest.getHeldTask() + ".");
		}
		if (!latest.isWeightsExact())
		{
			out.add("Couldn't recover exact odds from the list, so the shown percentages are used.");
		}
		if (d.getWanted().getOrDefault(group, Collections.emptyList()).isEmpty())
		{
			out.add("Pick the tasks you're hunting under Wanted tasks.");
		}
		return out;
	}

	private static String capitalize(String s)
	{
		return s == null || s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
