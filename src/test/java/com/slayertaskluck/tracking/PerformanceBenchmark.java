package com.slayertaskluck.tracking;

import com.google.gson.Gson;
import com.slayertaskluck.SlayerTaskLuckConfig;
import com.slayertaskluck.math.FairnessCache;
import com.slayertaskluck.math.StatsCalculator;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.TrackerData;
import com.slayertaskluck.ui.SlayerTaskLuckOverlay;
import com.slayertaskluck.ui.SlayerTaskLuckPanel;
import com.slayertaskluck.ui.PanelActions;
import com.slayertaskluck.ui.TrackerView;
import com.slayertaskluck.ui.ViewBuilder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import javax.swing.SwingUtilities;
import org.junit.Assume;
import org.junit.Test;

/**
 * Timing check for the plugin's heavier paths at large history sizes. Skipped in normal builds; run with
 * {@code STL_PERF=1 ./gradlew test --tests '*PerformanceBenchmark' --rerun-tasks}. Writes build/perf-report.txt.
 */
public class PerformanceBenchmark
{
	private static final int[] SIZES = {100, 1_000, 5_000, 20_000};

	@Test
	public void benchmark() throws Exception
	{
		Assume.assumeTrue("set STL_PERF=1 to run", "1".equals(System.getenv("STL_PERF")));
		final Gson gson = new Gson();
		final StringBuilder out = new StringBuilder();
		out.append("Slayer Task Luck performance check (median of 5 runs after warm-up)\n");
		out.append("Budgets: one frame at 50 fps = 20 ms, one game tick = 600 ms\n\n");

		final List<String> listTexts = KuradalFixture.texts(KuradalFixture.NO_TASK);
		line(out, "Read a task list (parse + weight recovery), client thread", micros(() ->
		{
			OddsModel.buildSnapshot("duradel", "Kuradal", 1, 90, 120, OddsParser.parse(listTexts), null);
			return 0;
		}), "us");

		for (int n : SIZES)
		{
			final TrackerData data = history(n);
			final String json = gson.toJson(data);
			out.append(String.format(Locale.ROOT, "%n== %,d Kuradal rolls + %,d Aya skips (save file %,d KB) ==%n", n, n, json.length() / 1024));

			line(out, "Log a new task (scoring, under the data lock), client thread", micros(() ->
			{
				RollScorer.build(data, draft(n * 2L + 5, "Gargoyles", false));
				return 0;
			}), "us");
			line(out, "Copy data for the background thread (data lock held)", millis(() -> data.copy().getRolls().size()), "ms");
			line(out, "Serialize a save (background thread, no lock)", millis(() -> gson.toJson(data.copy()).length()), "ms");
			line(out, "Stats without fairness, background thread", millis(() ->
				StatsCalculator.compute(data, "duradel", "Kuradal", 0.1, 0.1, false).getRolls()), "ms");
			line(out, "Stats with fairness simulation, background thread", millis(() ->
				StatsCalculator.compute(data, "duradel", "Kuradal", 0.1, 0.1, true).getRolls()), "ms");

			final TrackerView view = ViewBuilder.build(data, TrackerView.State.READY, null, null, 0.1, 0.1,
				name -> "Aya".equals(name), 90, 120, null);
			final FairnessCache cache = new FairnessCache();
			line(out, "View rebuild after a new roll (fairness re-run), background", millis(() ->
				ViewBuilder.build(data, TrackerView.State.READY, null, null, 0.1, 0.1, name -> "Aya".equals(name), 90, 120, null).getRollCount()), "ms");
			ViewBuilder.build(data, TrackerView.State.READY, null, null, 0.1, 0.1, name -> "Aya".equals(name), 90, 120, cache);
			line(out, "View rebuild after ticking a wanted task (fairness cached)", millis(() ->
				ViewBuilder.build(data, TrackerView.State.READY, null, null, 0.1, 0.1, name -> "Aya".equals(name), 90, 120, cache).getRollCount()), "ms");
			line(out, "Side panel refresh, Swing thread", millis(() -> panelUpdate(view)), "ms");
			line(out, "Overlay draw, every frame", overlayMicros(view), "us");
		}

		final String report = out.toString();
		System.out.println(report);
		Files.createDirectories(Paths.get("build"));
		Files.write(Paths.get("build", "perf-report.txt"), report.getBytes(StandardCharsets.UTF_8));
	}

	/** A long, realistic Turael-skip history: each Kuradal roll is followed by an Aya skip. */
	private static TrackerData history(int targetRolls)
	{
		final TrackerData d = new TrackerData();
		final OddsSnapshot snap = OddsModel.buildSnapshot("duradel", "Kuradal", 0, 90, 120,
			OddsParser.parse(KuradalFixture.texts(KuradalFixture.NO_TASK)), null);
		d.getSnapshots().add(snap);
		d.getWanted().put("duradel", Arrays.asList("Abyssal Demons", "Dark Beasts"));
		final Map<String, Double> dist = OddsModel.distribution(snap, null, null);
		final String[] tasks = dist.keySet().toArray(new String[0]);
		final double[] cum = new double[tasks.length];
		double run = 0;
		for (int i = 0; i < tasks.length; i++)
		{
			run += dist.get(tasks[i]);
			cum[i] = run;
		}
		final Random random = new Random(42);
		for (int i = 0; i < targetRolls; i++)
		{
			final double u = random.nextDouble();
			int t = 0;
			while (t < tasks.length - 1 && cum[t] < u)
			{
				t++;
			}
			d.getRolls().add(RollScorer.build(d, draft(i * 2L + 1, tasks[t], false)));
			d.getRolls().add(RollScorer.build(d, draft(i * 2L + 2, "Crawling Hands", true)));
		}
		return d;
	}

	private static RollDraft draft(long step, String task, boolean filler)
	{
		return RollDraft.builder()
			.timestamp(1_000_000L + step * 300_000L)
			.masterName(filler ? "Aya" : "Kuradal")
			.masterSlot(filler ? 1 : 5)
			.masterGroup(filler ? "turael" : "duradel")
			.task(task)
			.amount(150)
			.filler(filler)
			.slayerLevel(90)
			.combatLevel(120)
			.build();
	}

	private static int panelUpdate(TrackerView view)
	{
		final AtomicReference<RuntimeException> error = new AtomicReference<>();
		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					PANEL.get().update(view, 0.1, 0.1);
				}
				catch (RuntimeException e)
				{
					error.set(e);
				}
			});
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
		if (error.get() != null)
		{
			throw error.get();
		}
		return 0;
	}

	private static final ThreadLocal<SlayerTaskLuckPanel> PANEL_HOLDER = new ThreadLocal<>();
	private static final java.util.function.Supplier<SlayerTaskLuckPanel> PANEL = () ->
	{
		if (PANEL_HOLDER.get() == null)
		{
			PANEL_HOLDER.set(new SlayerTaskLuckPanel(new PanelActions()
			{
				@Override
				public void setWanted(String group, List<String> tasks)
				{
				}

				@Override
				public void setExcluded(String rollId, boolean excluded)
				{
				}

				@Override
				public void rescore(String group)
				{
				}

				@Override
				public void resetData()
				{
				}

				@Override
				public void exportCsv()
				{
				}
			}));
		}
		return PANEL_HOLDER.get();
	};

	private static double overlayMicros(TrackerView view)
	{
		final SlayerTaskLuckConfig config = new SlayerTaskLuckConfig()
		{
		};
		final SlayerTaskLuckOverlay overlay = new SlayerTaskLuckOverlay(config, () -> view, () -> true);
		final BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		final int frames = 20_000;
		for (int i = 0; i < frames; i++)
		{
			overlay.render(g); // warm-up
		}
		final long start = System.nanoTime();
		for (int i = 0; i < frames; i++)
		{
			overlay.render(g);
		}
		final double us = (System.nanoTime() - start) / 1000.0 / frames;
		g.dispose();
		return us;
	}

	private static double micros(LongSupplier work)
	{
		return median(work, 200) / 1000.0;
	}

	private static double millis(LongSupplier work)
	{
		return median(work, 1) / 1_000_000.0;
	}

	/** Median wall time in ns of one call (averaged over `inner` calls per sample). */
	private static double median(LongSupplier work, int inner)
	{
		for (int i = 0; i < 3; i++)
		{
			for (int j = 0; j < inner; j++)
			{
				work.getAsLong();
			}
		}
		final double[] samples = new double[5];
		for (int s = 0; s < samples.length; s++)
		{
			final long start = System.nanoTime();
			for (int j = 0; j < inner; j++)
			{
				work.getAsLong();
			}
			samples[s] = (System.nanoTime() - start) / (double) inner;
		}
		Arrays.sort(samples);
		return samples[samples.length / 2];
	}

	private static void line(StringBuilder out, String what, double value, String unit)
	{
		out.append(String.format(Locale.ROOT, "  %-62s %10.2f %s%n", what, value, unit));
	}
}
