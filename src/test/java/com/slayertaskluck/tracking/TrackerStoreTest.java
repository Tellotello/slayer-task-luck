package com.slayertaskluck.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.slayertaskluck.model.TrackerData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TrackerStoreTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private ScheduledExecutorService executor;
	private Path dir;

	@Before
	public void setUp()
	{
		executor = Executors.newSingleThreadScheduledExecutor();
		dir = folder.getRoot().toPath();
	}

	@After
	public void tearDown()
	{
		executor.shutdownNow();
	}

	private TrackerStore newStore()
	{
		final Filepath root = Filepath.Unchecked.getRooted(dir);
		return new TrackerStore(new Gson(), executor, () -> root, () -> { });
	}

	private void drain() throws Exception
	{
		executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
	}

	@Test
	public void savesAndReloadsAProfile() throws Exception
	{
		final TrackerStore store = newStore();
		store.switchProfile("abc123");
		drain();
		assertTrue(store.isLoaded());

		store.addSnapshot(OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null));
		store.setWanted("duradel", Collections.singletonList("Abyssal Demons"));
		store.recordRoll(RollDraft.builder().timestamp(5000).masterName("Kuradal").masterSlot(5)
			.masterGroup("duradel").task("Abyssal Demons").amount(200).build());
		store.flush();
		drain();
		assertTrue(Files.exists(dir.resolve("abc123.json")));

		final TrackerStore reloaded = newStore();
		reloaded.switchProfile("abc123");
		drain();
		final TrackerData d = reloaded.copy();
		assertEquals(1, d.getRolls().size());
		assertEquals(1, d.getSnapshots().size());
		assertTrue(d.getRolls().get(0).getWanted());
		assertEquals("Kuradal", reloaded.nameForSlot(5));
	}

	@Test
	public void createsTheDataFolderOnFirstSave() throws Exception
	{
		// Like getPluginDirectory(): the returned folder doesn't exist yet.
		final Path missing = dir.resolve("plugin-data").resolve("slayer-task-luck");
		final Filepath root = Filepath.Unchecked.getRooted(missing);
		final TrackerStore store = new TrackerStore(new Gson(), executor, () -> root, () -> { });
		store.switchProfile("fresh");
		drain();
		store.setWanted("duradel", Collections.singletonList("Abyssal Demons"));
		store.flush();
		drain();
		assertTrue(Files.exists(missing.resolve("fresh.json")));
		assertEquals(null, store.getProblem());
	}

	@Test
	public void unreadableFileIsSetAsideNotLost() throws Exception
	{
		Files.write(dir.resolve("broken.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
		final TrackerStore store = newStore();
		store.switchProfile("broken");
		drain();
		assertTrue(store.isLoaded());
		assertNotNull(store.getProblem());
		assertEquals(0, store.copy().getRolls().size());
		assertTrue(Files.list(dir).anyMatch(p -> p.getFileName().toString().startsWith("broken.json.unreadable-")));
	}

	@Test
	public void identicalListIsNotStoredTwice() throws Exception
	{
		final TrackerStore store = newStore();
		store.switchProfile("dedupe");
		drain();
		store.addSnapshot(OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null));
		store.addSnapshot(OddsParsingTest.snapshot(KuradalFixture.NO_TASK, null));
		assertEquals(1, store.copy().getSnapshots().size());
		store.addSnapshot(OddsParsingTest.snapshot(KuradalFixture.ON_FIRE_GIANTS, "Fire Giants"));
		assertEquals(2, store.copy().getSnapshots().size());
	}
}
