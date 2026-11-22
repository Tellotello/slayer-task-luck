package com.slayertaskluck.math;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the last fairness result per master. The simulation only depends on which rolls count and the odds they were
 * rolled under, not on the wanted list or settings, so most view rebuilds (ticking a wanted task, changing a
 * threshold) reuse it instead of re-running thousands of simulated histories. Used from one thread only.
 */
public class FairnessCache
{
	private final Map<String, Long> signatures = new HashMap<>();
	private final Map<String, Fairness.Result> results = new HashMap<>();

	Fairness.Result get(String group, long signature)
	{
		final Long known = signatures.get(group);
		return known != null && known == signature ? results.get(group) : null;
	}

	void put(String group, long signature, Fairness.Result result)
	{
		signatures.put(group, signature);
		results.put(group, result);
	}
}
