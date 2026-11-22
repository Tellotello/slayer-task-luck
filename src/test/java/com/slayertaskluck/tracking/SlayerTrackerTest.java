package com.slayertaskluck.tracking;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.google.gson.Gson;
import com.slayertaskluck.SlayerTaskLuckConfig;
import com.slayertaskluck.model.Roll;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Replays Slayer varp changes through {@link SlayerTracker} the way the game sends them, and checks which ones are
 * logged as rolls. Task Storage moves a task in or out of {@code SLAYER_STORED_VARP}; a master's assignment doesn't
 * touch it.
 */
public class SlayerTrackerTest
{
	private static final int NECHRYAEL = 52;
	private static final int GARGOYLES = 46;
	private static final int ABYSSAL_DEMONS = 42;
	private static final int BATS = 8;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private int tick = 1000;
	private int gap = 3;
	private ScheduledExecutorService executor;
	private TrackerStore store;
	private SlayerTracker tracker;

	@Before
	public void setUp() throws Exception
	{
		final Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTickCount()).thenAnswer(i -> tick);
		when(client.getVarpValue(anyInt())).thenAnswer(i -> varps.getOrDefault(i.<Integer>getArgument(0), 0));
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault(i.<Integer>getArgument(0), 0));
		final SlayerTaskLuckConfig config = mock(SlayerTaskLuckConfig.class);
		when(config.fillerAya()).thenReturn(true);
		when(config.fillerTurael()).thenReturn(true);
		when(config.fillerSpria()).thenReturn(true);

		executor = Executors.newSingleThreadScheduledExecutor();
		final Path dir = folder.getRoot().toPath();
		final Filepath root = Filepath.Unchecked.getRooted(dir);
		store = new TrackerStore(new Gson(), executor, () -> root, () -> { });
		store.switchProfile("test");
		executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

		tracker = new SlayerTracker(client, store, config, () -> { });
		tracker.start();
	}

	@After
	public void tearDown()
	{
		executor.shutdownNow();
	}

	@Test
	public void anAssignmentIsLogged()
	{
		assign("Kuradal", 5, GARGOYLES, 150);
		assertEquals(tasks("Task #46"), logged());
	}

	@Test
	public void storingAndUnstoringAreNotRolls()
	{
		assign("Kuradal", 5, NECHRYAEL, 227);
		store();
		assign("Aya", 1, BATS, 26);
		finishTask();
		unstore();
		assertEquals(tasks("Task #52", "Task #8"), logged());
	}

	@Test
	public void aSwapIsNotARoll()
	{
		assign("Kuradal", 5, NECHRYAEL, 227);
		store();
		assign("Kuradal", 5, GARGOYLES, 150);
		swap();
		assertEquals(tasks("Task #52", "Task #46"), logged());
	}

	/** The reported bug: unstore, store again, then right-click Assignment straight away. */
	@Test
	public void anAssignmentRightAfterStoringIsLogged()
	{
		assign("Kuradal", 5, NECHRYAEL, 227);
		store();
		unstore();
		gap = 1; // the new task arrives on the very next tick after storing
		store();
		assign("Kuradal", 5, ABYSSAL_DEMONS, 220);
		assertEquals(tasks("Task #52", "Task #42"), logged());
	}

	@Test
	public void anAssignmentWithATaskInStorageIsLogged()
	{
		assign("Kuradal", 5, NECHRYAEL, 227);
		store();
		assign("Aya", 1, BATS, 26);
		finishTask();
		assign("Kuradal", 5, GARGOYLES, 150);
		assertEquals(tasks("Task #52", "Task #8", "Task #46"), logged());
	}

	/** Even with no storage change to go on, a task that comes back with kills already done isn't an assignment. */
	@Test
	public void aTaskWithKillsDoneIsNotARoll()
	{
		assign("Kuradal", 5, NECHRYAEL, 227);
		varps.put(VarPlayerID.SLAYER_COUNT, 180); // kills lower the count without any event the tracker acts on
		set(VarPlayerID.SLAYER_TARGET, 0);
		set(VarPlayerID.SLAYER_COUNT, 0);
		next();
		assign("Aya", 1, BATS, 26);
		finishTask();
		set(VarPlayerID.SLAYER_TARGET, NECHRYAEL);
		set(VarPlayerID.SLAYER_COUNT_ORIGINAL, 227);
		set(VarPlayerID.SLAYER_COUNT, 180);
		next();
		assertEquals(tasks("Task #52", "Task #8"), logged());
	}

	// ---- the game, as the tracker sees it ----

	private int storedTarget;
	private int storedCount;
	private int storedOriginal;

	private void assign(String master, int slot, int target, int amount)
	{
		final ChatMessage line = new ChatMessage();
		line.setType(ChatMessageType.DIALOG);
		line.setMessage(master + "|Your new task is to kill " + amount + ".");
		tracker.onChatMessage(line);
		varbits.put(VarbitID.SLAYER_MASTER, slot);
		set(VarPlayerID.SLAYER_TARGET, target);
		set(VarPlayerID.SLAYER_COUNT_ORIGINAL, amount);
		set(VarPlayerID.SLAYER_COUNT, amount);
		next();
	}

	private void finishTask()
	{
		set(VarPlayerID.SLAYER_COUNT, 0);
		next();
	}

	/** Rewards > Tasks > Store task: the current task moves into storage. */
	private void store()
	{
		storedTarget = varps.getOrDefault(VarPlayerID.SLAYER_TARGET, 0);
		storedCount = varps.getOrDefault(VarPlayerID.SLAYER_COUNT, 0);
		storedOriginal = varps.getOrDefault(VarPlayerID.SLAYER_COUNT_ORIGINAL, 0);
		set(VarPlayerID.SLAYER_STORED_VARP, storedTarget << 16 | storedCount);
		set(VarPlayerID.SLAYER_COUNT, 0);
		next();
	}

	/** Rewards > Tasks > Unstore: the stored task becomes the current task again. */
	private void unstore()
	{
		set(VarPlayerID.SLAYER_STORED_VARP, 0);
		set(VarPlayerID.SLAYER_TARGET, storedTarget);
		set(VarPlayerID.SLAYER_COUNT_ORIGINAL, storedOriginal);
		set(VarPlayerID.SLAYER_COUNT, storedCount);
		next();
	}

	/** Rewards > Tasks > Swap: the current and stored tasks trade places. */
	private void swap()
	{
		final int target = varps.get(VarPlayerID.SLAYER_TARGET);
		final int count = varps.get(VarPlayerID.SLAYER_COUNT);
		final int original = varps.get(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		set(VarPlayerID.SLAYER_STORED_VARP, target << 16 | count);
		set(VarPlayerID.SLAYER_TARGET, storedTarget);
		set(VarPlayerID.SLAYER_COUNT_ORIGINAL, storedOriginal);
		set(VarPlayerID.SLAYER_COUNT, storedCount);
		storedTarget = target;
		storedCount = count;
		storedOriginal = original;
		next();
	}

	private void set(int varp, int value)
	{
		varps.put(varp, value);
		final VarbitChanged event = new VarbitChanged();
		event.setVarpId(varp);
		event.setVarbitId(-1);
		event.setValue(value);
		tracker.onVarbitChanged(event);
	}

	/** The tracker reads changes back on the next tick; {@link #gap} ticks pass before the next action. */
	private void next()
	{
		for (int i = 0; i < gap; i++)
		{
			tick++;
			tracker.onGameTick(new GameTick());
		}
	}

	private List<String> logged()
	{
		final List<String> out = new ArrayList<>();
		for (Roll r : store.copy().getRolls())
		{
			out.add(r.getTask());
		}
		return out;
	}

	private static List<String> tasks(String... names)
	{
		return new ArrayList<>(java.util.Arrays.asList(names));
	}
}
