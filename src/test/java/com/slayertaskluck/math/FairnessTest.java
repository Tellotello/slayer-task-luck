package com.slayertaskluck.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class FairnessTest
{
	private static Map<String, Double> dist()
	{
		final Map<String, Double> d = new LinkedHashMap<>();
		d.put("A", 0.1);
		d.put("B", 0.3);
		d.put("C", 0.6);
		return d;
	}

	@Test
	public void fairRollsLookFair()
	{
		final Random random = new Random(11);
		final List<Map<String, Double>> dists = new ArrayList<>();
		final List<String> outcomes = new ArrayList<>();
		for (int i = 0; i < 300; i++)
		{
			final double u = random.nextDouble();
			dists.add(dist());
			outcomes.add(u < 0.1 ? "A" : u < 0.4 ? "B" : "C");
		}
		final Fairness.Result result = Fairness.compute(dists, outcomes, 5000, 1);
		assertEquals(300, result.getRolls());
		assertTrue("p=" + result.getPValue(), result.getPValue() > 0.01);
	}

	@Test
	public void riggedRollsAreFlagged()
	{
		final List<Map<String, Double>> dists = new ArrayList<>();
		final List<String> outcomes = new ArrayList<>();
		for (int i = 0; i < 100; i++)
		{
			dists.add(dist());
			outcomes.add(i % 2 == 0 ? "A" : "C"); // A far too often
		}
		final Fairness.Result result = Fairness.compute(dists, outcomes, 5000, 1);
		assertTrue("p=" + result.getPValue(), result.getPValue() < 0.001);
		assertEquals("A", result.getRows().get(0).getTask());
		assertEquals(50, result.getRows().get(0).getObserved());
		assertEquals(10.0, result.getRows().get(0).getExpected(), 1e-9);
	}
}
