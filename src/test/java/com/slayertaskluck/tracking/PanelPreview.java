package com.slayertaskluck.tracking;

import com.slayertaskluck.math.FairnessCache;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.TrackerData;
import com.slayertaskluck.ui.PanelActions;
import com.slayertaskluck.ui.SlayerTaskLuckPanel;
import com.slayertaskluck.ui.TrackerView;
import com.slayertaskluck.ui.ViewBuilder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.Assume;
import org.junit.Test;

/**
 * Renders the side panel with sample data to PNGs, with every section open, so layout and contrast can be checked
 * without logging in. Skipped in normal builds; run with
 * {@code STL_PREVIEW=1 ./gradlew test --tests '*PanelPreview' --rerun-tasks}. Writes build/panel-*.png.
 */
public class PanelPreview
{
	private static final long MIN = 60_000L;

	@Test
	public void render() throws Exception
	{
		Assume.assumeTrue("set STL_PREVIEW=1 to run", "1".equals(System.getenv("STL_PREVIEW")));
		final List<String> problems = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			RuneLiteLAF.setup();
			try
			{
				problems.addAll(save(newPlayer(), null, "panel-new.png"));
				problems.addAll(save(longHistory(), null, "panel-long.png"));
				problems.addAll(save(longHistory(), "Konar", "panel-unused-master.png"));
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		org.junit.Assert.assertTrue("clipped components: " + problems, problems.isEmpty());
	}

	/** A few rolls, a task list opened while holding a wanted task, a task-storage history. */
	private static TrackerView newPlayer()
	{
		final TrackerData d = new TrackerData();
		d.getSnapshots().add(snapshot(KuradalFixture.ON_FIRE_GIANTS, "Fire Giants", 1000));
		d.getWanted().put("duradel", new ArrayList<>(Arrays.asList("Abyssal Demons", "Fire Giants")));
		final String[] tasks = {"Gargoyles", "Trolls", "Drakes", "Hellhounds", "Nechryael", "Suqahs", "Wyrms"};
		for (int i = 0; i < tasks.length; i++)
		{
			add(d, 2000 + i * 20 * MIN, "Kuradal", tasks[i], false);
			add(d, 2000 + i * 20 * MIN + MIN, "Aya", "Crawling Hands", true);
		}
		return view(d);
	}

	/** Enough rolls for the chart, the fairness check and a long dry streak. */
	private static TrackerView longHistory()
	{
		final TrackerData d = new TrackerData();
		final OddsSnapshot snap = snapshot(KuradalFixture.NO_TASK, null, 1000);
		d.getSnapshots().add(snap);
		d.getWanted().put("duradel", new ArrayList<>(Arrays.asList("Abyssal Demons", "Dark Beasts")));
		final Map<String, Double> dist = OddsModel.distribution(snap, null, null);
		final List<String> names = new ArrayList<>(dist.keySet());
		final Random random = new Random(7);
		for (int i = 0; i < 120; i++)
		{
			double u = random.nextDouble();
			String task = names.get(names.size() - 1);
			for (String n : names)
			{
				u -= dist.get(n);
				if (u <= 0)
				{
					task = n;
					break;
				}
			}
			// End on a long dry streak so the card shows it.
			if (i > 90 && (task.equals("Abyssal Demons") || task.equals("Dark Beasts")))
			{
				task = "Gargoyles";
			}
			add(d, 2000 + i * 20 * MIN, "Kuradal", task, false);
			add(d, 2000 + i * 20 * MIN + MIN, "Aya", "Crawling Hands", true);
		}
		d.getSnapshots().add(OddsModel.buildSnapshot("nieve", "Nieve", 500, 90, 120,
			OddsParser.parse(KuradalFixture.texts(KuradalFixture.NO_TASK)), null));
		return view(d);
	}

	private static OddsSnapshot snapshot(String[][] rows, String held, long t)
	{
		return OddsModel.buildSnapshot("duradel", "Kuradal", t, 90, 120, OddsParser.parse(KuradalFixture.texts(rows)), held);
	}

	private static void add(TrackerData d, long t, String master, String task, boolean filler)
	{
		d.getRolls().add(RollScorer.build(d, RollDraft.builder()
			.timestamp(t)
			.masterName(master)
			.masterSlot(filler ? 1 : 5)
			.masterGroup(Masters.groupOf(master, filler ? 1 : 5))
			.task(task)
			.amount(150)
			.filler(filler)
			.slayerLevel(90)
			.combatLevel(120)
			.build()));
	}

	private static TrackerView view(TrackerData d)
	{
		return ViewBuilder.build(d, TrackerView.State.READY, null, null, 0.1, 0.1,
			name -> Masters.isFiller(name, 0, true, true, true), 90, 120, new FairnessCache());
	}

	private static List<String> save(TrackerView view, String master, String file) throws Exception
	{
		final SlayerTaskLuckPanel panel = new SlayerTaskLuckPanel(new NoActions());
		panel.update(view, 0.1, 0.1);
		if (master != null)
		{
			pick(panel, master);
		}
		openSections(panel);
		final JFrame frame = new JFrame();
		frame.getContentPane().add(panel);
		for (int i = 0; i < 3; i++)
		{
			frame.pack();
		}
		final Dimension size = panel.getSize();
		// Paint at the screen's scale (e.g. 125%): text is measured for that scale, so painting at 100% misleads.
		final double scale = frame.getGraphicsConfiguration().getDefaultTransform().getScaleX();
		final BufferedImage img = new BufferedImage((int) Math.ceil(size.width * scale), (int) Math.ceil(size.height * scale),
			BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = img.createGraphics();
		g.scale(scale, scale);
		panel.printAll(g);
		g.dispose();
		final List<String> problems = new ArrayList<>();
		checkFit(panel, panel, size.width - panel.getInsets().right, problems);
		frame.dispose();
		new File("build").mkdirs();
		ImageIO.write(img, "png", new File("build", file));
		System.out.println(file + ": " + (problems.isEmpty() ? "nothing clipped" : problems.size() + " clipped"));
		problems.forEach(p -> System.out.println("  " + p));
		return problems;
	}

	/** Selects a master in the panel's picker by its shown name. */
	private static void pick(Container c, String master)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof JComboBox)
			{
				final JComboBox<?> box = (JComboBox<?>) child;
				for (int i = 0; i < box.getItemCount(); i++)
				{
					if (String.valueOf(box.getItemAt(i)).equals(master))
					{
						box.setSelectedIndex(i);
						return;
					}
				}
				throw new AssertionError(master + " isn't in the master picker");
			}
			else if (child instanceof Container)
			{
				pick((Container) child, master);
			}
		}
	}

	/** Lists visible labels that are cut short or run past the panel's padding. */
	private static void checkFit(Container root, Container c, int right, List<String> problems)
	{
		for (Component child : c.getComponents())
		{
			if (!child.isVisible())
			{
				continue;
			}
			final int childRight = SwingUtilities.convertPoint(c, child.getX() + child.getWidth(), 0, root).x;
			final String name = child instanceof JLabel ? "'" + ((JLabel) child).getText() + "'" : child.getClass().getSimpleName();
			if (childRight > right)
			{
				problems.add(name + " ends at x=" + childRight + ", past " + right);
			}
			else if (child instanceof JLabel && child.getPreferredSize().width > child.getWidth())
			{
				problems.add(name + " needs " + child.getPreferredSize().width + "px, has " + child.getWidth());
			}
			if (child instanceof Container)
			{
				checkFit(root, (Container) child, right, problems);
			}
		}
	}

	/** Clicks every closed section header ("+ Title"). */
	private static void openSections(Container c)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof JLabel && ((JLabel) child).getText() != null && ((JLabel) child).getText().startsWith("+ "))
			{
				final Component bar = child.getParent();
				bar.dispatchEvent(new MouseEvent(bar, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
			}
			else if (child instanceof Container)
			{
				openSections((Container) child);
			}
		}
	}

	private static class NoActions implements PanelActions
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
	}
}
