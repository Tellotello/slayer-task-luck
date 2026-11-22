package com.slayertaskluck.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import lombok.Value;

/**
 * Do the listed odds hold up? Compares how often each task was received with how often each roll's own odds say it
 * should have been, and runs one global Monte Carlo test: replay every roll with its odds many times and see how
 * often a chi-square-style gap at least as big as the real one appears. Per-task rows are descriptive only, since
 * with many tasks a few always look off by chance.
 */
public final class Fairness
{
	@Value
	public static class Row
	{
		String task;
		int observed;
		double expected;
	}

	@Value
	public static class Result
	{
		/** Sorted by the size of the gap between observed and expected, biggest first. */
		List<Row> rows;
		double statistic;
		/** Share of simulated histories at least as uneven as the real one. Small = the odds look off. */
		double pValue;
		int simulations;
		int rolls;
	}

	private Fairness()
	{
	}

	/**
	 * @param distributions per roll: task -> chance (each summing to 1)
	 * @param outcomes per roll: the task received (a key of that roll's distribution)
	 */
	public static Result compute(List<Map<String, Double>> distributions, List<String> outcomes, int simulations, long seed)
	{
		final Map<String, Integer> index = new HashMap<>();
		final List<String> names = new ArrayList<>();
		final int n = distributions.size();
		final int[][] taskIdx = new int[n][];
		final double[][] cumulative = new double[n][];
		final int[] observedIdx = new int[n];
		int used = 0;

		for (int i = 0; i < n; i++)
		{
			final Map<String, Double> dist = distributions.get(i);
			final String outcome = outcomes.get(i);
			if (dist.isEmpty() || !dist.containsKey(outcome))
			{
				continue;
			}
			final int[] idx = new int[dist.size()];
			final double[] cum = new double[dist.size()];
			double running = 0;
			int j = 0;
			for (Map.Entry<String, Double> e : dist.entrySet())
			{
				idx[j] = index.computeIfAbsent(e.getKey(), k ->
				{
					names.add(k);
					return names.size() - 1;
				});
				running += e.getValue();
				cum[j] = running;
				j++;
			}
			cum[cum.length - 1] = Double.MAX_VALUE; // guard against rounding at the top end
			taskIdx[used] = idx;
			cumulative[used] = cum;
			observedIdx[used] = index.get(outcome);
			used++;
		}

		final int k = names.size();
		final double[] expected = new double[k];
		final int[] observed = new int[k];
		for (int i = 0; i < used; i++)
		{
			final int[] idx = taskIdx[i];
			final double[] cum = cumulative[i];
			double prev = 0;
			for (int j = 0; j < idx.length; j++)
			{
				final double c = j == idx.length - 1 ? 1.0 : cum[j];
				expected[idx[j]] += c - prev;
				prev = c;
			}
			observed[observedIdx[i]]++;
		}

		final double statistic = chiSquare(observed, expected);
		int atLeast = 0;
		final SplittableRandom random = new SplittableRandom(seed);
		final int[] counts = new int[k];
		for (int s = 0; s < simulations; s++)
		{
			Arrays.fill(counts, 0);
			for (int i = 0; i < used; i++)
			{
				final double u = random.nextDouble();
				int pos = Arrays.binarySearch(cumulative[i], u);
				if (pos < 0)
				{
					pos = -pos - 1;
				}
				counts[taskIdx[i][Math.min(pos, taskIdx[i].length - 1)]]++;
			}
			if (chiSquare(counts, expected) >= statistic - 1e-9)
			{
				atLeast++;
			}
		}

		final List<Row> rows = new ArrayList<>();
		for (int t = 0; t < k; t++)
		{
			rows.add(new Row(names.get(t), observed[t], expected[t]));
		}
		rows.sort((a, b) -> Double.compare(Math.abs(b.getObserved() - b.getExpected()), Math.abs(a.getObserved() - a.getExpected())));
		final double p = simulations > 0 ? (atLeast + 1) / (double) (simulations + 1) : Double.NaN;
		return new Result(rows, statistic, p, simulations, used);
	}

	private static double chiSquare(int[] observed, double[] expected)
	{
		double sum = 0;
		for (int t = 0; t < expected.length; t++)
		{
			if (expected[t] > 1e-12)
			{
				final double d = observed[t] - expected[t];
				sum += d * d / expected[t];
			}
		}
		return sum;
	}
}
