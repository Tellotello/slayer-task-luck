package com.slayertaskluck.ui;

import com.slayertaskluck.math.Fairness;
import com.slayertaskluck.math.GroupStats;
import com.slayertaskluck.math.Verdict;
import com.slayertaskluck.model.Confidence;
import com.slayertaskluck.model.Roll;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: glance first, details on demand. A master picker, a headline card (verdict, dry streak, wanted
 * vs expected, detours), the luck bar, then collapsible sections. All methods run on the Swing thread.
 */
public class SlayerTaskLuckPanel extends PluginPanel
{
	private static final int HISTORY_PAGE = 20;
	private static final int FAIRNESS_TOP = 5;

	private final PanelActions actions;
	private final JPanel content = new JPanel();
	private final JComboBox<TrackerView.MasterOption> masterPicker = new JComboBox<>();
	private final JPanel pickerRow = new JPanel(new BorderLayout());
	private final JPanel notices = column();
	private final JLabel emptyState = new JLabel();
	private final JPanel card = new JPanel();
	private final JLabel barTitle = UiStyle.small("");
	private final LuckBar luckBar = new LuckBar();
	private final LuckChart chart = new LuckChart();
	private final JLabel chartEmpty = UiStyle.small("");
	private final CollapsibleSection chartSection = new CollapsibleSection("Luck over time", true);
	private final CollapsibleSection historySection = new CollapsibleSection("History", false);
	private final CollapsibleSection fairnessSection = new CollapsibleSection("Fairness by task", false);
	private final CollapsibleSection wantedSection = new CollapsibleSection("Wanted tasks", false);
	private final CollapsibleSection dataSection = new CollapsibleSection("Data", false);

	private TrackerView view = TrackerView.EMPTY;
	private String selected;
	private boolean updatingPicker;
	private int historyShown = HISTORY_PAGE;
	private boolean fairnessShowAll;
	private double unluckyCut = 0.1;
	private double luckyCut = 0.1;

	public SlayerTaskLuckPanel(PanelActions actions)
	{
		this.actions = actions;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(UiStyle.BACKGROUND);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(UiStyle.BACKGROUND);

		final JLabel title = UiStyle.label("Slayer Task Luck", FontManager.getRunescapeBoldFont(), ColorScheme.BRAND_ORANGE);
		addLeft(content, title);

		pickerRow.setBackground(UiStyle.BACKGROUND);
		pickerRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		final JLabel masterLabel = UiStyle.label("Master  ", FontManager.getRunescapeSmallFont(), UiStyle.MUTED);
		pickerRow.add(masterLabel, BorderLayout.WEST);
		masterPicker.setFocusable(false);
		masterPicker.addActionListener(e ->
		{
			if (!updatingPicker && masterPicker.getSelectedItem() != null)
			{
				selected = ((TrackerView.MasterOption) masterPicker.getSelectedItem()).getGroup();
				historyShown = HISTORY_PAGE;
				render();
			}
		});
		pickerRow.add(masterPicker, BorderLayout.CENTER);
		addLeft(content, pickerRow);

		addLeft(content, notices);

		emptyState.setForeground(UiStyle.TEXT);
		emptyState.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		addLeft(content, emptyState);

		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(UiStyle.CARD);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		final JPanel cardWrap = column();
		cardWrap.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		cardWrap.add(card);
		addLeft(content, cardWrap);

		barTitle.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
		addLeft(content, barTitle);
		luckBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		addLeft(content, luckBar);

		chartSection.content().add(chart);
		chart.setAlignmentX(Component.LEFT_ALIGNMENT);
		chartSection.content().add(chartEmpty);
		for (CollapsibleSection s : new CollapsibleSection[]{chartSection, historySection, fairnessSection, wantedSection, dataSection})
		{
			addLeft(content, s);
		}

		add(content, BorderLayout.NORTH);
		render();
	}

	/** Shows a new view; keeps the user's master choice when it still exists. */
	public void update(TrackerView view, double unluckyCut, double luckyCut)
	{
		this.view = view;
		this.unluckyCut = unluckyCut;
		this.luckyCut = luckyCut;
		render();
	}

	private void render()
	{
		final boolean ready = view.getState() == TrackerView.State.READY;
		final boolean hasData = ready && view.hasAnyData();
		final List<TrackerView.MasterOption> options = new ArrayList<>(view.getMasters());
		if (view.getStats().containsKey(TrackerView.ALL))
		{
			options.add(new TrackerView.MasterOption(TrackerView.ALL, "All masters"));
		}
		if (selected == null || options.stream().noneMatch(o -> o.getGroup().equals(selected)))
		{
			selected = view.getDefaultGroup();
		}
		refreshPicker(options);

		renderNotices();
		final GroupStats stats = selected != null ? view.getStats().get(selected) : null;

		if (!ready)
		{
			UiStyle.setWrapped(emptyState, view.getState() == TrackerView.State.LOADING
				? "Loading your data..." : "Log in to see your Slayer luck.", UiStyle.CONTENT_WIDTH);
		}
		else if (!hasData || options.isEmpty())
		{
			UiStyle.setWrapped(emptyState, "Open the odds list at a Slayer master to start tracking. "
				+ "Talk to your Slayer master, open Rewards, then the task list. Then pick your wanted tasks here.", UiStyle.CONTENT_WIDTH);
		}
		emptyState.setVisible(!ready || !hasData || options.isEmpty());
		pickerRow.setVisible(ready && !options.isEmpty());

		final boolean showStats = ready && stats != null;
		card.getParent().setVisible(showStats);
		barTitle.setVisible(showStats && stats.hasLuckData());
		luckBar.setVisible(showStats && stats.hasLuckData());
		chartSection.setVisible(showStats);
		historySection.setVisible(ready && hasData);
		fairnessSection.setVisible(showStats && !stats.isAllMasters());
		wantedSection.setVisible(showStats && !stats.isAllMasters());
		dataSection.setVisible(ready && hasData);

		if (showStats)
		{
			renderCard(stats);
			barTitle.setText("Lifetime luck at " + stats.getName());
			luckBar.setValue(stats.getPercentile(), stats.getLuckVerdict().getTone(), unluckyCut, luckyCut);
			luckBar.setToolTipText(stats.hasLuckData()
				? "Your luck ranked against normal luck: " + UiStyle.percent(stats.getPercentile())
				+ " (mid-p percentile; 50% is exactly average)" : null);
			renderChart(stats);
			if (!stats.isAllMasters())
			{
				renderFairness(stats);
				renderWanted(stats);
			}
		}
		if (ready && hasData)
		{
			renderHistory();
			renderData();
		}
		revalidate();
		repaint();
	}

	private void refreshPicker(List<TrackerView.MasterOption> options)
	{
		updatingPicker = true;
		try
		{
			final List<TrackerView.MasterOption> current = new ArrayList<>();
			for (int i = 0; i < masterPicker.getItemCount(); i++)
			{
				current.add(masterPicker.getItemAt(i));
			}
			if (!current.equals(options))
			{
				masterPicker.removeAllItems();
				for (TrackerView.MasterOption o : options)
				{
					masterPicker.addItem(o);
				}
			}
			for (int i = 0; i < masterPicker.getItemCount(); i++)
			{
				if (masterPicker.getItemAt(i).getGroup().equals(selected))
				{
					masterPicker.setSelectedIndex(i);
				}
			}
		}
		finally
		{
			updatingPicker = false;
		}
	}

	private void renderNotices()
	{
		notices.removeAll();
		final List<String> lines = new ArrayList<>();
		if (view.getProblem() != null)
		{
			lines.add(view.getProblem());
		}
		if (view.getNotice() != null)
		{
			lines.add(view.getNotice());
		}
		if (selected != null)
		{
			lines.addAll(view.getNotices().getOrDefault(selected, Collections.emptyList()));
		}
		for (String line : lines)
		{
			final JLabel l = UiStyle.text(line, FontManager.getRunescapeSmallFont(), UiStyle.NOTICE, UiStyle.CONTENT_WIDTH);
			l.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
			addLeft(notices, l);
		}
		notices.setVisible(!lines.isEmpty());
	}

	// ---- headline card ----

	private void renderCard(GroupStats s)
	{
		card.removeAll();
		final JLabel verdict = UiStyle.chip();
		final Verdict luck = s.getLuckVerdict();
		verdict.setText(luck.getTone() == Verdict.Tone.NONE ? "no rolls yet" : luck.getTone().word());
		verdict.setBackground(UiStyle.chipBackground(luck.getTone()));
		verdict.setForeground(UiStyle.toneColor(luck.getTone()));
		card.add(textRow(s.isAllMasters() ? "All masters" : "Luck at " + s.getName(),
			FontManager.getRunescapeBoldFont(), Color.WHITE, verdict, UiStyle.CARD_WIDTH, null));

		if (!s.hasLuckData())
		{
			final String hint = s.getRolls() == 0
				? (s.isAllMasters() ? "No counted rolls yet." : "Get a task from " + s.getName() + " to start.")
				: "Pick your wanted tasks to see your luck.";
			card.add(spacer(6));
			card.add(left(UiStyle.text(hint, FontManager.getRunescapeSmallFont(), UiStyle.TEXT, UiStyle.CARD_WIDTH)));
			addDetours(s);
			card.revalidate();
			return;
		}

		card.add(spacer(6));
		final JLabel dryChip = UiStyle.chip();
		UiStyle.styleChip(dryChip, s.getDryVerdict());
		card.add(textRow("Dry streak: " + s.getDryRolls() + (s.getDryRolls() == 1 ? " roll" : " rolls"),
			FontManager.getRunescapeFont(), UiStyle.TEXT, dryChip, UiStyle.CARD_WIDTH,
			"<html>" + s.getDryRolls() + " rolls in a row without a wanted task.<br>"
				+ "If the listed odds are right, a streak at least this long happens about "
				+ Verdict.oneIn(s.getDryProbability()) + " times (" + UiStyle.percentFine(s.getDryProbability()) + ").</html>"));

		final JLabel wantedChip = UiStyle.chip();
		UiStyle.styleChip(wantedChip, luck);
		card.add(textRow("Wanted: " + s.getObserved() + " of " + UiStyle.oneDecimal(s.getExpected()) + " expected",
			FontManager.getRunescapeFont(), UiStyle.TEXT, wantedChip, UiStyle.CARD_WIDTH,
			"<html>Going by the odds on each roll, normal luck would have given about "
				+ UiStyle.oneDecimal(s.getExpected()) + " (&plusmn;" + UiStyle.oneDecimal(s.getSd()) + ") wanted tasks.<br>"
				+ "Percentile " + UiStyle.percent(s.getPercentile()) + " (mid-p), z = " + UiStyle.oneDecimal(s.getZ())
				+ ", over " + s.getRolls() + " counted rolls.</html>"));

		addDetours(s);

		card.add(spacer(6));
		card.add(left(note("Longest dry streak: " + s.getLongestDry() + " ("
			+ (s.getLongestDryProbability() <= unluckyCut ? Verdict.oneIn(s.getLongestDryProbability()) : "normal")
			+ " for " + s.getRolls() + " rolls)", UiStyle.CARD_WIDTH)));
		if (s.getChancePerRoll() != null && s.getChancePerRoll() > 0)
		{
			card.add(left(note("Now " + UiStyle.percentFine(s.getChancePerRoll()) + " per roll: a wanted task about every "
				+ UiStyle.oneDecimal(1 / s.getChancePerRoll()) + " rolls", UiStyle.CARD_WIDTH)));
		}
		if (s.getUncounted() > 0)
		{
			card.add(left(note(s.getUncounted() + " roll" + (s.getUncounted() == 1 ? "" : "s") + " not counted (see History)",
				UiStyle.CARD_WIDTH)));
		}
		card.revalidate();
	}

	private void addDetours(GroupStats s)
	{
		if (s.getTotalDetours() == 0)
		{
			return;
		}
		final String who = view.getFillerName() != null ? view.getFillerName() : "Filler";
		card.add(spacer(2));
		card.add(textRow(who + " detours: " + s.getDetours()
				+ (s.getDetourMillis() > 0 ? " (" + UiStyle.duration(s.getDetourMillis()) + ")" : ""),
			FontManager.getRunescapeFont(), UiStyle.TEXT, null, UiStyle.CARD_WIDTH,
			"<html>Filler tasks since your last wanted task, timed until your next assignment.<br>All time: "
				+ s.getTotalDetours() + " (" + UiStyle.duration(s.getTotalDetourMillis()) + ")</html>"));
	}

	// ---- chart ----

	private void renderChart(GroupStats s)
	{
		final int n = s.getChartDiff().length;
		final boolean enough = n >= 10 && s.hasLuckData();
		chart.setVisible(enough);
		chartEmpty.setVisible(!enough);
		UiStyle.setWrapped(chartEmpty, "The chart appears after 10 counted rolls (" + n + " so far).", UiStyle.CONTENT_WIDTH);
		if (enough)
		{
			chart.setData(s.getChartDiff(), s.getChartSd());
		}
		chartSection.setSummary(enough ? (s.getObserved() >= s.getExpected() ? "+" : "")
			+ UiStyle.oneDecimal(s.getObserved() - s.getExpected()) : n + "/10");
	}

	// ---- history ----

	private void renderHistory()
	{
		final JPanel c = historySection.content();
		c.removeAll();
		final List<Roll> rolls = new ArrayList<>();
		for (Roll r : view.getHistory())
		{
			if (selected == null || TrackerView.ALL.equals(selected) || r.isFiller() || Objects.equals(r.getMasterGroup(), selected))
			{
				rolls.add(r);
			}
		}
		historySection.setSummary(rolls.isEmpty() ? "" : "newest: " + shortTask(rolls.get(0)));
		if (rolls.isEmpty())
		{
			c.add(left(UiStyle.small("No tasks logged yet.")));
		}
		for (int i = 0; i < Math.min(historyShown, rolls.size()); i++)
		{
			c.add(historyRow(rolls.get(i)));
		}
		if (rolls.size() > historyShown)
		{
			final JLabel more = UiStyle.link("Show " + Math.min(HISTORY_PAGE, rolls.size() - historyShown) + " more", () ->
			{
				historyShown += HISTORY_PAGE;
				renderHistory();
				historySection.revalidate();
			});
			more.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
			c.add(left(more));
		}
		c.revalidate();
		c.repaint();
	}

	private static String shortTask(Roll r)
	{
		final String t = r.getTask() == null ? "?" : r.getTask();
		return t.length() > 16 ? t.substring(0, 15) + "." : t;
	}

	private JComponent historyRow(Roll r)
	{
		final JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(UiStyle.CARD);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, UiStyle.BACKGROUND),
			BorderFactory.createEmptyBorder(4, 6, 4, 6)));

		final Color main = r.isExcluded() ? UiStyle.MUTED : UiStyle.TEXT;
		final JLabel task = UiStyle.label((r.getTask() == null ? "?" : r.getTask()) + " x" + r.getAmount(), FontManager.getRunescapeFont(), main);
		final String right;
		final Color rightColor;
		if (r.isFiller())
		{
			right = "skip";
			rightColor = UiStyle.MUTED;
		}
		else if (r.getPTask() != null)
		{
			right = UiStyle.percentFine(r.getPTask());
			rightColor = Boolean.TRUE.equals(r.getWanted()) && !r.isExcluded() ? UiStyle.LUCKY : main;
		}
		else
		{
			right = "?";
			rightColor = UiStyle.MUTED;
		}
		row.add(row(task, UiStyle.label(right, FontManager.getRunescapeFont(), rightColor), null));

		final String status;
		if (r.isFiller())
		{
			status = "filler";
		}
		else if (r.isExcluded())
		{
			status = "excluded";
		}
		else if (r.getConfidence() == Confidence.NONE)
		{
			status = "not counted";
		}
		else
		{
			status = (Boolean.TRUE.equals(r.getWanted()) ? "wanted" : "not wanted") + (r.getConfidence() == Confidence.STALE ? " *" : "");
		}
		final JLabel meta = UiStyle.small(r.getMasterName() + " - " + UiStyle.time(r.getTimestamp()) + " - " + status);
		JLabel toggle = null;
		if (!r.isFiller())
		{
			toggle = UiStyle.link(r.isExcluded() ? "Include" : "Exclude", () -> actions.setExcluded(r.getId(), !r.isExcluded()));
			toggle.setToolTipText(r.isExcluded() ? "Count this roll again" : "Leave this roll out of the stats (you can undo this)");
		}
		row.add(row(meta, toggle, null));

		final StringBuilder tip = new StringBuilder("<html>");
		if (r.getPTask() != null)
		{
			tip.append("Listed chance of this task: ").append(UiStyle.percentFine(r.getPTask())).append("<br>");
		}
		if (r.getPWanted() != null)
		{
			tip.append("Chance of a wanted task on this roll: ").append(UiStyle.percentFine(r.getPWanted())).append("<br>");
		}
		if (r.getExcludedTask() != null && !r.isFiller())
		{
			tip.append("Rolled while on ").append(UiStyle.escape(r.getExcludedTask())).append(" (left out of the roll)<br>");
		}
		if (r.getConfidenceNote() != null)
		{
			tip.append(UiStyle.escape(r.getConfidenceNote())).append(r.getConfidence() == Confidence.STALE ? " (*)" : "");
		}
		tip.append("</html>");
		if (tip.length() > "<html></html>".length())
		{
			setTooltipDeep(row, tip.toString());
		}
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	/** Swing shows the tooltip of the component under the mouse, so labels inside a row need it too. */
	private static void setTooltipDeep(JComponent c, String tip)
	{
		if (c.getToolTipText() == null || c instanceof JPanel)
		{
			c.setToolTipText(tip);
		}
		for (Component child : c.getComponents())
		{
			if (child instanceof JComponent)
			{
				setTooltipDeep((JComponent) child, tip);
			}
		}
	}

	// ---- fairness ----

	private void renderFairness(GroupStats s)
	{
		final JPanel c = fairnessSection.content();
		c.removeAll();
		final Fairness.Result f = s.getFairness();
		if (f == null)
		{
			fairnessSection.setSummary(s.getRolls() + "/" + s.getFairnessMinimum());
			c.add(left(UiStyle.text("Checks whether you get each task as often as the listed odds say. "
				+ "Needs " + s.getFairnessMinimum() + " counted rolls at " + s.getName() + " (" + s.getRolls() + " so far).",
				FontManager.getRunescapeSmallFont(), UiStyle.TEXT, UiStyle.CONTENT_WIDTH)));
			c.revalidate();
			return;
		}

		final boolean odd = f.getPValue() < 0.05;
		fairnessSection.setSummary(odd ? "unusual" : "looks fair");
		final String verdict = odd
			? "Unusual: a spread this uneven happens by chance only about " + Verdict.oneIn(f.getPValue()) + " times."
			: "The listed odds look right: your spread of tasks is normal for " + f.getRolls() + " rolls.";
		final JLabel v = UiStyle.text(verdict, FontManager.getRunescapeSmallFont(), odd ? UiStyle.NOTICE : UiStyle.TEXT, UiStyle.CONTENT_WIDTH);
		v.setToolTipText("Monte Carlo replay of your " + f.getRolls() + " rolls, " + f.getSimulations()
			+ " times. Empirical p-value " + String.format("%.3f", f.getPValue()) + ".");
		c.add(left(v));
		c.add(spacer(4));
		c.add(left(note("With this many tasks, a few always look off by chance.", UiStyle.CONTENT_WIDTH)));
		c.add(spacer(6));

		final List<Fairness.Row> rows = f.getRows();
		final int show = fairnessShowAll ? rows.size() : Math.min(FAIRNESS_TOP, rows.size());
		for (int i = 0; i < show; i++)
		{
			final Fairness.Row r = rows.get(i);
			final JLabel nums = UiStyle.label("got " + r.getObserved() + " - exp " + UiStyle.oneDecimal(r.getExpected()),
				FontManager.getRunescapeSmallFont(), UiStyle.MUTED);
			c.add(textRow(r.getTask(), FontManager.getRunescapeSmallFont(), UiStyle.TEXT, nums, UiStyle.CONTENT_WIDTH, null));
		}
		if (rows.size() > FAIRNESS_TOP)
		{
			final JLabel toggle = UiStyle.link(fairnessShowAll ? "Show top " + FAIRNESS_TOP : "Show all " + rows.size(), () ->
			{
				fairnessShowAll = !fairnessShowAll;
				renderFairness(s);
			});
			toggle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
			c.add(left(toggle));
		}
		c.revalidate();
		c.repaint();
	}

	// ---- wanted tasks ----

	private void renderWanted(GroupStats s)
	{
		final JPanel c = wantedSection.content();
		c.removeAll();
		final String group = s.getGroup();
		final List<TrackerView.WantedOption> options = view.getWantedOptions().getOrDefault(group, Collections.emptyList());
		if (options.isEmpty())
		{
			wantedSection.setSummary("");
			c.add(left(UiStyle.text("Open " + s.getName() + "'s task list first; the tasks you can get appear here.",
				FontManager.getRunescapeSmallFont(), UiStyle.TEXT, UiStyle.CONTENT_WIDTH)));
			c.revalidate();
			return;
		}

		double total = 0;
		final List<String> current = new ArrayList<>();
		for (TrackerView.WantedOption o : options)
		{
			if (o.isWanted())
			{
				total += o.getChance();
				current.add(o.getTask());
			}
		}
		wantedSection.setSummary(UiStyle.percentFine(total) + " per roll");
		c.add(left(UiStyle.label(current.isEmpty() ? "Tick the tasks you're hunting."
				: "Chance per roll: " + UiStyle.percentFine(total) + " (about 1 in " + UiStyle.oneDecimal(1 / total) + ")",
			FontManager.getRunescapeSmallFont(), UiStyle.TEXT)));
		c.add(spacer(4));

		for (TrackerView.WantedOption o : options)
		{
			final JCheckBox box = new JCheckBox(o.getTask(), o.isWanted());
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setBackground(UiStyle.BACKGROUND);
			box.setForeground(o.isWanted() ? Color.WHITE : UiStyle.TEXT);
			box.setFocusable(false);
			box.addActionListener(e ->
			{
				final List<String> next = new ArrayList<>(current);
				if (box.isSelected())
				{
					next.add(o.getTask());
				}
				else
				{
					next.remove(o.getTask());
				}
				actions.setWanted(group, next);
			});
			c.add(row(box, UiStyle.label(UiStyle.percentFine(o.getChance()), FontManager.getRunescapeSmallFont(), UiStyle.MUTED), null));
		}

		c.add(spacer(6));
		c.add(left(note("Changes apply to new rolls. Past rolls keep the wanted list they were rolled under.", UiStyle.CONTENT_WIDTH)));
		final JLabel apply = UiStyle.link("Apply to past rolls...", () ->
		{
			final int answer = JOptionPane.showConfirmDialog(this,
				"Re-score every past roll at " + s.getName() + " using the wanted tasks ticked now?\n\n"
					+ "Choosing what counts as \"wanted\" after seeing your results makes the luck numbers less honest.",
				"Apply wanted tasks to past rolls", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				actions.rescore(group);
			}
		});
		apply.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		c.add(left(apply));
		c.revalidate();
		c.repaint();
	}

	// ---- data ----

	private void renderData()
	{
		final JPanel c = dataSection.content();
		c.removeAll();
		dataSection.setSummary(view.getRollCount() + " tasks");
		c.add(left(note(view.getRollCount() + " tasks logged, " + view.getSnapshotCount() + " task lists saved", UiStyle.CONTENT_WIDTH)));
		c.add(spacer(6));
		final JButton export = button("Export CSV");
		export.addActionListener(e -> actions.exportCsv());
		final JButton reset = button("Reset data...");
		reset.addActionListener(e ->
		{
			final int answer = JOptionPane.showConfirmDialog(this,
				"Delete every logged task, saved task list and wanted task for this account?\nThis can't be undone. Export a CSV first if you want a copy.",
				"Reset Slayer Task Luck data", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				actions.resetData();
			}
		});
		final JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		buttons.setBackground(UiStyle.BACKGROUND);
		buttons.add(export);
		buttons.add(reset);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		c.add(buttons);
		c.revalidate();
	}

	// ---- layout helpers ----

	private static JButton button(String text)
	{
		final JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		return b;
	}

	private static JPanel column()
	{
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(UiStyle.BACKGROUND);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static void addLeft(JPanel parent, JComponent child)
	{
		child.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(child);
	}

	private static JComponent left(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}

	private static Component spacer(int h)
	{
		final Component s = Box.createVerticalStrut(h);
		((JComponent) s).setAlignmentX(Component.LEFT_ALIGNMENT);
		return s;
	}

	/** Secondary text, wrapped to {@code width}. */
	private static JLabel note(String text, int width)
	{
		return UiStyle.text(text, FontManager.getRunescapeSmallFont(), UiStyle.MUTED, width);
	}

	/** A {@link #row} whose text wraps to leave room for the value on its right. */
	private static JPanel textRow(String text, Font font, Color color, JComponent rightSide, int width, String tooltip)
	{
		final int room = width - (rightSide != null ? rightSide.getPreferredSize().width + UiStyle.ROW_GAP : 0);
		return row(UiStyle.text(text, font, color, room), rightSide, tooltip);
	}

	/** One line: content on the left, an optional chip or value on the right. Transparent over its parent. */
	private static JPanel row(JComponent leftSide, JComponent rightSide, String tooltip)
	{
		final JPanel r = new JPanel(new BorderLayout(UiStyle.ROW_GAP, 0));
		r.setOpaque(false);
		r.add(leftSide, BorderLayout.CENTER);
		if (rightSide != null)
		{
			final boolean wrapped = leftSide instanceof JLabel && ((JLabel) leftSide).getText().startsWith("<html>");
			if (wrapped)
			{
				// Keep a chip on the first line instead of stretching it down the wrapped text.
				final JPanel top = new JPanel(new BorderLayout());
				top.setOpaque(false);
				top.add(rightSide, BorderLayout.NORTH);
				r.add(top, BorderLayout.EAST);
			}
			else
			{
				r.add(rightSide, BorderLayout.EAST);
			}
		}
		if (tooltip != null)
		{
			r.setToolTipText(tooltip);
			leftSide.setToolTipText(tooltip);
			if (rightSide != null)
			{
				rightSide.setToolTipText(tooltip);
			}
		}
		r.setAlignmentX(Component.LEFT_ALIGNMENT);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
		return r;
	}
}
