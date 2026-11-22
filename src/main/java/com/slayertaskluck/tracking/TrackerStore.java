package com.slayertaskluck.tracking;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.Roll;
import com.slayertaskluck.model.TrackerData;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Owns the tracked data for the current RuneScape profile and its JSON file. All mutations are synchronized and
 * may come from the client thread (new rolls) or the Swing thread (panel actions). Disk I/O runs on the plugin's
 * background executor, debounced, and written atomically (temp file, then move).
 */
@Slf4j
public class TrackerStore
{
	public interface DirectorySupplier
	{
		Filepath get() throws IOException;
	}

	private static final long SAVE_DELAY_MS = 1500;
	private static final long SAVE_RETRY_MS = 30_000;
	private static final String SAVE_PROBLEM = "Couldn't save data: ";

	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final DirectorySupplier directory;
	private final Runnable onChange;

	private TrackerData data = new TrackerData();
	private String profileKey;
	private boolean loaded;
	private boolean dirty;
	private String problem;
	private final List<Consumer<TrackerData>> pending = new ArrayList<>();
	private ScheduledFuture<?> saveFuture;

	public TrackerStore(Gson gson, ScheduledExecutorService executor, DirectorySupplier directory, Runnable onChange)
	{
		this.gson = gson;
		this.executor = executor;
		this.directory = directory;
		this.onChange = onChange;
	}

	// ---- profile & persistence ----

	/** Switches to another RuneScape profile: saves the current one and loads the new one in the background. */
	public void switchProfile(String key)
	{
		synchronized (this)
		{
			if (Objects.equals(key, profileKey))
			{
				return;
			}
			if (loaded && dirty)
			{
				submitSave(profileKey, gson.toJson(data));
			}
			profileKey = key;
			loaded = false;
			dirty = false;
			problem = null;
			data = new TrackerData();
			if (key != null)
			{
				final String k = key;
				execute(() -> load(k));
			}
		}
		onChange.run();
	}

	private void load(String key)
	{
		TrackerData loadedData = null;
		String loadProblem = null;
		try
		{
			final Filepath file = directory.get().joinSegment(fileName(key));
			if (file.exists())
			{
				try (Reader reader = file.openReader())
				{
					loadedData = gson.fromJson(reader, TrackerData.class);
				}
				catch (JsonParseException e)
				{
					final String aside = fileName(key) + ".unreadable-" + System.currentTimeMillis();
					file.moveTo(directory.get().joinSegment(aside), StandardCopyOption.REPLACE_EXISTING);
					loadProblem = "Saved data couldn't be read, so it was set aside as " + aside + " and tracking started fresh.";
					log.warn("Slayer Task Luck: unreadable data file moved to {}", aside, e);
				}
			}
		}
		catch (IOException e)
		{
			loadProblem = "Couldn't load saved data: " + e.getMessage();
			log.warn("Slayer Task Luck: load failed", e);
		}

		synchronized (this)
		{
			if (!key.equals(profileKey))
			{
				return; // switched again meanwhile
			}
			data = sanitize(loadedData);
			loaded = true;
			problem = loadProblem;
			if (!pending.isEmpty())
			{
				for (Consumer<TrackerData> op : pending)
				{
					op.accept(data);
				}
				pending.clear();
				markDirty();
			}
		}
		log.debug("Loaded tracker data for profile ({} rolls, {} snapshots)", data.getRolls().size(), data.getSnapshots().size());
		onChange.run();
	}

	private static TrackerData sanitize(TrackerData d)
	{
		if (d == null)
		{
			d = new TrackerData();
		}
		if (d.getSnapshots() == null)
		{
			d.setSnapshots(new ArrayList<>());
		}
		if (d.getRolls() == null)
		{
			d.setRolls(new ArrayList<>());
		}
		if (d.getWanted() == null)
		{
			d.setWanted(new HashMap<>());
		}
		if (d.getMasterNames() == null)
		{
			d.setMasterNames(new HashMap<>());
		}
		if (d.getBlocked() == null)
		{
			d.setBlocked(new ArrayList<>());
		}
		d.setSchemaVersion(TrackerData.SCHEMA_VERSION);
		return d;
	}

	/** Must be called with the lock held. */
	private void markDirty()
	{
		dirty = true;
		if (saveFuture == null || saveFuture.isDone())
		{
			try
			{
				saveFuture = executor.schedule(this::saveNow, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
			}
			catch (RejectedExecutionException e)
			{
				// shutting down; flush() handles the final save
			}
		}
	}

	private void saveNow()
	{
		final String key;
		final String json;
		synchronized (this)
		{
			if (!loaded || !dirty || profileKey == null)
			{
				return;
			}
			key = profileKey;
			json = gson.toJson(data);
			dirty = false;
		}
		write(key, json);
	}

	private void submitSave(String key, String json)
	{
		execute(() -> write(key, json));
	}

	private void write(String key, String json)
	{
		try
		{
			final Filepath dir = directory.get();
			// getPluginDirectory() creates .runelite/plugin-data but not this plugin's folder inside it.
			if (!dir.exists())
			{
				dir.createDirectories();
			}
			final Filepath target = dir.joinSegment(fileName(key));
			final Filepath tmp = dir.createTempFile("stl-", ".tmp");
			tmp.write(json);
			try
			{
				tmp.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				tmp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e)
		{
			log.warn("Slayer Task Luck: save failed", e);
			synchronized (this)
			{
				if (key.equals(profileKey))
				{
					problem = SAVE_PROBLEM + e.getMessage();
					// Keep the data in memory and try again shortly.
					dirty = true;
					try
					{
						saveFuture = executor.schedule(this::saveNow, SAVE_RETRY_MS, TimeUnit.MILLISECONDS);
					}
					catch (RejectedExecutionException ignored)
					{
						// shutting down
					}
				}
			}
			onChange.run();
			return;
		}

		boolean cleared = false;
		synchronized (this)
		{
			if (key.equals(profileKey) && problem != null && problem.startsWith(SAVE_PROBLEM))
			{
				problem = null;
				cleared = true;
			}
		}
		if (cleared)
		{
			onChange.run();
		}
	}

	/** Queues a final save. Non-blocking; the executor finishes it after shutdown() is called. */
	public void flush()
	{
		synchronized (this)
		{
			if (saveFuture != null)
			{
				saveFuture.cancel(false);
				saveFuture = null;
			}
			if (loaded && dirty && profileKey != null)
			{
				submitSave(profileKey, gson.toJson(data));
				dirty = false;
			}
		}
	}

	private void execute(Runnable r)
	{
		try
		{
			executor.execute(r);
		}
		catch (RejectedExecutionException e)
		{
			log.debug("executor stopped; dropping task");
		}
	}

	static String fileName(String profileKey)
	{
		return profileKey.replaceAll("[^A-Za-z0-9_-]", "_") + ".json";
	}

	// ---- reads ----

	public synchronized boolean isLoaded()
	{
		return loaded;
	}

	public synchronized boolean hasProfile()
	{
		return profileKey != null;
	}

	public synchronized String getProblem()
	{
		return problem;
	}

	/** Deep copy for background computation (view building, export). */
	public synchronized TrackerData copy()
	{
		return gson.fromJson(gson.toJson(data), TrackerData.class);
	}

	/** Whether the most recent roll with this task and amount came from a filler master, or null if unknown. */
	public synchronized Boolean fillerFor(int taskId, int amount)
	{
		final List<Roll> rolls = data.getRolls();
		for (int i = rolls.size() - 1; i >= 0; i--)
		{
			final Roll r = rolls.get(i);
			if (r.getTaskId() == taskId && r.getAmount() == amount)
			{
				return r.isFiller();
			}
		}
		return null;
	}

	// ---- mutations ----

	private void mutate(Consumer<TrackerData> op)
	{
		synchronized (this)
		{
			if (!loaded)
			{
				if (profileKey != null)
				{
					pending.add(op);
				}
				return;
			}
			op.accept(data);
			markDirty();
		}
		onChange.run();
	}

	public void addSnapshot(OddsSnapshot s)
	{
		mutate(d ->
		{
			d.getMasterNames().put(s.getMasterGroup(), s.getMasterName());
			for (OddsSnapshot existing : RollScorer.snapshotsNewestFirst(d, s.getMasterGroup()))
			{
				if (Objects.equals(existing.getContentHash(), s.getContentHash()))
				{
					return; // unchanged list; keep the original (rolls since then already use it)
				}
				break;
			}
			d.getSnapshots().add(s);
		});
	}

	public void recordRoll(RollDraft draft)
	{
		mutate(d ->
		{
			if (draft.getMasterName() != null)
			{
				d.getMasterNames().put(draft.getMasterGroup(), draft.getMasterName());
				d.getMasterNames().put(slotKey(draft.getMasterSlot()), draft.getMasterName());
			}
			d.getRolls().add(RollScorer.build(d, draft));
		});
	}

	/** Last master name seen for a role slot, used when an assignment arrives without a dialogue line. */
	public synchronized String nameForSlot(int slot)
	{
		return data.getMasterNames().get(slotKey(slot));
	}

	private static String slotKey(int slot)
	{
		return "#slot" + slot;
	}

	public void setWanted(String group, Collection<String> tasks)
	{
		mutate(d -> d.getWanted().put(group, new ArrayList<>(new LinkedHashSet<>(tasks))));
	}

	public void setExcluded(String rollId, boolean excluded)
	{
		mutate(d ->
		{
			for (Roll r : d.getRolls())
			{
				if (r.getId().equals(rollId))
				{
					r.setExcluded(excluded);
				}
			}
		});
	}

	/** Re-scores every target roll of a group with the current wanted tasks. */
	public void rescore(String group)
	{
		mutate(d ->
		{
			for (Roll r : d.getRolls())
			{
				if (!r.isFiller() && group.equals(r.getMasterGroup()))
				{
					RollScorer.score(d, r);
				}
			}
		});
	}

	public void setBlocked(List<String> blocked)
	{
		mutate(d ->
		{
			final boolean seenBefore = d.getBlockedChangedAt() != 0;
			if (!seenBefore)
			{
				// The first read is a baseline, not a change (1 = "seen, never changed").
				d.setBlocked(new ArrayList<>(blocked));
				d.setBlockedChangedAt(1);
			}
			else if (!new LinkedHashSet<>(d.getBlocked()).equals(new LinkedHashSet<>(blocked)))
			{
				d.setBlocked(new ArrayList<>(blocked));
				d.setBlockedChangedAt(System.currentTimeMillis());
			}
		});
	}

	public void reset()
	{
		mutate(d ->
		{
			d.getRolls().clear();
			d.getSnapshots().clear();
			d.getWanted().clear();
			d.getMasterNames().clear();
			d.getBlocked().clear();
			d.setBlockedChangedAt(0);
		});
	}
}
