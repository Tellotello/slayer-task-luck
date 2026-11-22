package com.slayertaskluck.math;

/**
 * Pure luck statistics with no RuneLite dependencies. Every roll {@code i} at a target master has a chance
 * {@code p[i]} of giving a wanted task.
 */
public final class LuckMath
{
	private LuckMath()
	{
	}

	/**
	 * Chance of going this many rolls (or more) without a wanted task: the product of (1 - p_i) over the rolls
	 * since the last wanted task.
	 */
	public static double dryStreakProbability(double[] wantedChances)
	{
		double probability = 1.0;
		for (double p : wantedChances)
		{
			check(p);
			probability *= 1.0 - p;
		}
		return probability;
	}

	/**
	 * Exact distribution of the number of wanted tasks over rolls with different chances (Poisson-binomial),
	 * by the standard O(n^2) dynamic program. {@code pmf[k]} = P(exactly k wanted).
	 */
	public static double[] poissonBinomial(double[] p)
	{
		final double[] pmf = new double[p.length + 1];
		pmf[0] = 1.0;
		for (int i = 0; i < p.length; i++)
		{
			check(p[i]);
			final double q = 1.0 - p[i];
			for (int k = i + 1; k >= 1; k--)
			{
				pmf[k] = pmf[k] * q + pmf[k - 1] * p[i];
			}
			pmf[0] *= q;
		}
		return pmf;
	}

	/** P(X <= k). */
	public static double cdf(double[] pmf, int k)
	{
		double sum = 0;
		for (int i = 0; i <= k && i < pmf.length; i++)
		{
			sum += pmf[i];
		}
		return Math.min(1.0, sum);
	}

	/**
	 * Mid-p percentile: P(X < k) + P(X = k) / 2. Unlike P(X <= k) it puts an exactly average result near 50%,
	 * which matters for small whole-number counts. Low = unlucky, high = lucky.
	 */
	public static double midP(double[] pmf, int observed)
	{
		final double below = cdf(pmf, observed - 1);
		final double at = observed >= 0 && observed < pmf.length ? pmf[observed] : 0;
		return Math.max(0, Math.min(1, below + at / 2));
	}

	/**
	 * Chance that somewhere in this sequence of rolls there is a run of at least {@code length} rolls in a row
	 * without a wanted task. O(n * length) dynamic program over the current run length.
	 */
	public static double longestDryAtLeast(double[] p, int length)
	{
		if (length <= 0)
		{
			return 1.0;
		}
		double[] run = new double[length]; // run[r] = P(no run of `length` yet, current dry run = r)
		run[0] = 1.0;
		double reached = 0;
		for (double pi : p)
		{
			check(pi);
			final double q = 1.0 - pi;
			final double[] next = new double[length];
			double alive = 0;
			for (double v : run)
			{
				alive += v;
			}
			next[0] = alive * pi;
			for (int r = 0; r < length - 1; r++)
			{
				next[r + 1] = run[r] * q;
			}
			reached += run[length - 1] * q;
			run = next;
		}
		return Math.min(1.0, reached);
	}

	private static void check(double p)
	{
		if (!(p >= 0 && p <= 1)) // also rejects NaN
		{
			throw new IllegalArgumentException("probability out of range: " + p);
		}
	}
}
