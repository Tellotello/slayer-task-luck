package com.slayertaskluck.math;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

public class LuckMathTest
{
	private static final double TOLERANCE = 0.001; // 0.1 percentage points

	private static double[] constant(double p, int rolls)
	{
		double[] chances = new double[rolls];
		Arrays.fill(chances, p);
		return chances;
	}

	@Test
	public void dryStreakMatchesPlanFixturesAt13Percent()
	{
		assertEquals(0.498, LuckMath.dryStreakProbability(constant(0.13, 5)), TOLERANCE);
		assertEquals(0.248, LuckMath.dryStreakProbability(constant(0.13, 10)), TOLERANCE);
		assertEquals(0.124, LuckMath.dryStreakProbability(constant(0.13, 15)), TOLERANCE);
		assertEquals(0.062, LuckMath.dryStreakProbability(constant(0.13, 20)), TOLERANCE);
		assertEquals(0.015, LuckMath.dryStreakProbability(constant(0.13, 30)), TOLERANCE);
		assertEquals(0.001, LuckMath.dryStreakProbability(constant(0.13, 50)), TOLERANCE);
	}

	@Test
	public void noRollsMeansCertainty()
	{
		assertEquals(1.0, LuckMath.dryStreakProbability(new double[0]), 0.0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsOutOfRangeProbability()
	{
		LuckMath.dryStreakProbability(new double[]{1.5});
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNaN()
	{
		LuckMath.dryStreakProbability(new double[]{Double.NaN});
	}

	@Test
	public void poissonBinomialMatchesHandComputedCases()
	{
		final double[] coin = LuckMath.poissonBinomial(new double[]{0.5, 0.5});
		assertEquals(0.25, coin[0], 1e-12);
		assertEquals(0.5, coin[1], 1e-12);
		assertEquals(0.25, coin[2], 1e-12);

		final double[] mixed = LuckMath.poissonBinomial(new double[]{0.1, 0.2, 0.3});
		assertEquals(0.504, mixed[0], 1e-12);
		assertEquals(0.398, mixed[1], 1e-12);
		assertEquals(0.092, mixed[2], 1e-12);
		assertEquals(0.006, mixed[3], 1e-12);
	}

	@Test
	public void midPPutsAnAverageResultAtTheCentre()
	{
		final double[] pmf = LuckMath.poissonBinomial(new double[]{0.5, 0.5});
		assertEquals(0.5, LuckMath.midP(pmf, 1), 1e-12);
		assertEquals(0.125, LuckMath.midP(pmf, 0), 1e-12);
		assertEquals(0.875, LuckMath.midP(pmf, 2), 1e-12);
	}

	@Test
	public void longestDryStreakMatchesBruteForce()
	{
		final Random random = new Random(7);
		for (int trial = 0; trial < 20; trial++)
		{
			final int n = 1 + random.nextInt(9);
			final double[] p = new double[n];
			for (int i = 0; i < n; i++)
			{
				p[i] = random.nextDouble() * 0.6;
			}
			for (int length = 1; length <= n; length++)
			{
				assertEquals(bruteForceLongest(p, length), LuckMath.longestDryAtLeast(p, length), 1e-12);
			}
		}
	}

	private static double bruteForceLongest(double[] p, int length)
	{
		final int n = p.length;
		double total = 0;
		for (int mask = 0; mask < (1 << n); mask++)
		{
			double prob = 1;
			int run = 0;
			int longest = 0;
			for (int i = 0; i < n; i++)
			{
				final boolean hit = (mask & (1 << i)) != 0;
				prob *= hit ? p[i] : 1 - p[i];
				run = hit ? 0 : run + 1;
				longest = Math.max(longest, run);
			}
			if (longest >= length)
			{
				total += prob;
			}
		}
		return total;
	}

	@Test
	public void verdictWording()
	{
		assertEquals("1 in 16", Verdict.oneIn(0.062));
		assertEquals("1 in 830", Verdict.oneIn(0.0012));
		assertEquals("1 in 2.5M", Verdict.oneIn(4e-7));
		assertEquals(Verdict.Tone.UNLUCKY, Verdict.fromPercentile(0.03, 0.1, 0.1).getTone());
		assertEquals("1 in 33", Verdict.fromPercentile(0.03, 0.1, 0.1).chip());
		assertEquals(Verdict.Tone.LUCKY, Verdict.fromPercentile(0.95, 0.1, 0.1).getTone());
		assertEquals("normal", Verdict.fromPercentile(0.5, 0.1, 0.1).chip());
	}
}
