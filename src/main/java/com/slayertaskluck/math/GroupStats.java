package com.slayertaskluck.math;

import lombok.Builder;
import lombok.Value;

/** Everything the panel and overlay show for one master (or all masters). Immutable. */
@Value
@Builder
public class GroupStats
{
	String group;
	String name;
	boolean allMasters;

	/** Target rolls that count toward luck. */
	int rolls;
	/** Target rolls left out (excluded by the user, or no usable odds). */
	int uncounted;

	int dryRolls;
	double dryProbability;
	Verdict dryVerdict;
	int longestDry;
	double longestDryProbability;

	int observed;
	double expected;
	double sd;
	double z;
	double percentile;
	Verdict luckVerdict;

	/** Filler tasks since the last wanted task, and the time they took. */
	int detours;
	long detourMillis;
	int totalDetours;
	long totalDetourMillis;

	/** Chance per roll right now (latest odds and wanted tasks), or null when unknown. */
	Double chancePerRoll;

	/** Cumulative (observed - expected) and its standard deviation after each counted roll. */
	double[] chartDiff;
	double[] chartSd;

	Fairness.Result fairness;
	int fairnessMinimum;

	public boolean hasLuckData()
	{
		return rolls > 0 && expected > 0;
	}
}
