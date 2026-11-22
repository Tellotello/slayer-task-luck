package com.slayertaskluck.tracking;

/**
 * Recovers the game's integer task weights from the task list's percentages.
 *
 * <p>The game shows each chance as {@code floor(1000 * w / W) / 10} percent (truncated, not rounded), which is why
 * the list sums to about 98-99%. For a total weight {@code W < 1000} each shown value pins its weight to at most one
 * integer, so the smallest {@code W} for which every row has a valid weight and the weights add up to {@code W}
 * gives the exact weights. Kuradal's list, for example, resolves to a total of 161.
 */
public final class WeightInference
{
	/** Above this the per-row intervals can hold more than one integer and the answer stops being unique. */
	private static final int MAX_TOTAL = 999;

	private WeightInference()
	{
	}

	/**
	 * @param permille shown percentages in tenths of a percent (0 = unavailable)
	 * @return integer weights in the same order (0 where unavailable), or null when no exact fit exists
	 */
	public static int[] infer(int[] permille)
	{
		int available = 0;
		for (int s : permille)
		{
			if (s < 0 || s > 1000)
			{
				return null;
			}
			if (s > 0)
			{
				available++;
			}
		}
		if (available == 0)
		{
			return null;
		}

		final int[] weights = new int[permille.length];
		for (int total = available; total <= MAX_TOTAL; total++)
		{
			long sum = 0;
			boolean feasible = true;
			for (int i = 0; i < permille.length && feasible; i++)
			{
				final int s = permille[i];
				if (s == 0)
				{
					weights[i] = 0;
					continue;
				}
				// floor(1000 w / total) == s  <=>  s * total / 1000 <= w < (s + 1) * total / 1000
				final long lo = ceilDiv((long) s * total, 1000);
				final long hi = ceilDiv((long) (s + 1) * total, 1000) - 1;
				if (lo < 1 || lo > hi)
				{
					feasible = false;
				}
				else
				{
					weights[i] = (int) lo;
					sum += lo;
				}
			}
			if (feasible && sum == total && matches(permille, weights, total))
			{
				return weights.clone();
			}
		}
		return null;
	}

	/** True when these weights reproduce every shown value exactly. */
	public static boolean matches(int[] permille, int[] weights, int total)
	{
		for (int i = 0; i < permille.length; i++)
		{
			if (weights[i] == 0)
			{
				if (permille[i] != 0)
				{
					return false;
				}
				continue;
			}
			if ((int) ((1000L * weights[i]) / total) != permille[i])
			{
				return false;
			}
		}
		return true;
	}

	private static long ceilDiv(long a, long b)
	{
		return -Math.floorDiv(-a, b);
	}
}
