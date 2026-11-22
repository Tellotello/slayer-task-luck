package com.slayertaskluck;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.slayertaskluck.tracking.CsvExport;
import com.slayertaskluck.tracking.Masters;
import com.slayertaskluck.tracking.SlayerTracker;
import com.slayertaskluck.tracking.TrackerStore;
import com.slayertaskluck.ui.PanelActions;
import com.slayertaskluck.ui.TrackerView;
import com.slayertaskluck.ui.SlayerTaskLuckOverlay;
import com.slayertaskluck.ui.SlayerTaskLuckPanel;
import com.slayertaskluck.ui.ViewBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Slayer Task Luck",
	// Never change: names the data folder returned by getPluginDirectory().
	internalName = "slayer-task-luck",
	description = "Logs your Slayer task rolls with their exact odds and shows how lucky you have been at getting the tasks you want",
	tags = {"slayer", "task", "luck", "odds", "statistics", "turael", "aya", "skip"}
)
public class SlayerTaskLuckPlugin extends Plugin
{
	private static final long RECOMPUTE_DELAY_MS = 150;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SlayerTaskLuckConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private Gson gson;

	private ScheduledExecutorService executor;
	private TrackerStore store;
	private volatile SlayerTracker tracker;
	private SlayerTaskLuckPanel panel;
	private NavigationButton navButton;
	private SlayerTaskLuckOverlay overlay;
	private volatile TrackerView view = TrackerView.EMPTY;
	private final AtomicBoolean recomputeQueued = new AtomicBoolean();

	@Override
	protected void startUp() throws Exception
	{
		executor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			final Thread t = new Thread(r, "slayer-task-luck");
			t.setDaemon(true);
			return t;
		});
		store = new TrackerStore(gson, executor, this::getPluginDirectory, this::scheduleRecompute);
		tracker = new SlayerTracker(client, store, config, this::scheduleRecompute);

		panel = new SlayerTaskLuckPanel(new Actions());
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Slayer Task Luck")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlay = new SlayerTaskLuckOverlay(config, () -> view, tracker::isSkippingContext);
		overlayManager.add(overlay);

		eventBus.register(tracker);
		clientThread.invoke(() ->
		{
			tracker.start();
			store.switchProfile(configManager.getRSProfileKey());
		});
		scheduleRecompute();
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(tracker);
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		store.flush();
		// Non-blocking: lets the final save finish in the background, then the thread ends.
		executor.shutdown();
		navButton = null;
		panel = null;
		overlay = null;
		tracker = null;
		view = TrackerView.EMPTY;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		store.switchProfile(configManager.getRSProfileKey());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SlayerTaskLuckConfig.GROUP.equals(event.getGroup()))
		{
			scheduleRecompute();
		}
	}

	/** Coalesces changes into one background recompute of the view. Safe from any thread. */
	private void scheduleRecompute()
	{
		if (recomputeQueued.compareAndSet(false, true))
		{
			try
			{
				executor.schedule(this::recompute, RECOMPUTE_DELAY_MS, TimeUnit.MILLISECONDS);
			}
			catch (RejectedExecutionException e)
			{
				recomputeQueued.set(false);
			}
		}
	}

	private void recompute()
	{
		recomputeQueued.set(false);
		final SlayerTracker t = tracker;
		if (t == null)
		{
			return;
		}
		final TrackerView.State state = !store.hasProfile() ? TrackerView.State.LOGGED_OUT
			: store.isLoaded() ? TrackerView.State.READY : TrackerView.State.LOADING;
		final double unlucky = config.unluckyPercent() / 100.0;
		final double lucky = config.luckyPercent() / 100.0;
		final TrackerView next;
		try
		{
			next = ViewBuilder.build(store.copy(), state, store.getProblem(), t.getNotice(), unlucky, lucky,
				name -> Masters.isFiller(name, 0, config.fillerAya(), config.fillerTurael(), config.fillerSpria()),
				t.getSlayerLevel(), t.getCombatLevel());
		}
		catch (RuntimeException e)
		{
			log.warn("Slayer Task Luck: couldn't build the view", e);
			return;
		}
		view = next;
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.update(next, unlucky, lucky);
			}
		});
	}

	@Provides
	SlayerTaskLuckConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerTaskLuckConfig.class);
	}

	/** Panel actions, called on the Swing thread. */
	private class Actions implements PanelActions
	{
		@Override
		public void setWanted(String group, List<String> tasks)
		{
			store.setWanted(group, tasks);
		}

		@Override
		public void setExcluded(String rollId, boolean excluded)
		{
			store.setExcluded(rollId, excluded);
		}

		@Override
		public void rescore(String group)
		{
			store.rescore(group);
		}

		@Override
		public void resetData()
		{
			store.reset();
		}

		@Override
		public void exportCsv()
		{
			final List<Filepath> chosen = new Filepath.Chooser()
				.setIsSave()
				.setAcceptsFiles()
				.setDialogTitle("Export Slayer Task Luck data")
				.addExtensionFilter("CSV file", "csv")
				.setDefaultExtension("csv")
				.setFileName("slayer-task-luck.csv")
				.showDialog(panel);
			if (chosen == null || chosen.isEmpty())
			{
				return;
			}
			final Filepath target = chosen.get(0);
			final String csv = CsvExport.build(store.copy());
			executor.execute(() ->
			{
				try
				{
					target.write(csv);
				}
				catch (IOException e)
				{
					log.warn("Slayer Task Luck: export failed", e);
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel,
						"Couldn't write the file: " + e.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE));
				}
			});
		}
	}
}
