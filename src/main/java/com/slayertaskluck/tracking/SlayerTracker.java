package com.slayertaskluck.tracking;

import com.slayertaskluck.SlayerTaskLuckConfig;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.SnapshotEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Watches the game (read-only) for Slayer task assignments and task-list openings, all on the client thread.
 *
 * <p>Detection (see docs/DESIGN.md): a new assignment changes {@code SLAYER_TARGET}
 * and/or {@code SLAYER_COUNT_ORIGINAL} within one tick; kills only lower {@code SLAYER_COUNT}. Changes are
 * collected and read back one tick later, so every value (and the master's dialogue line) has arrived. The values
 * the client loads at login or after a world hop are taken as the baseline, never as an assignment.
 */
@Slf4j
public class SlayerTracker
{
	private static final int LOGIN_SETTLE_TICKS = 3;
	/** A dialogue line this recent names the master who assigned the task. */
	private static final int SPEAKER_TICKS = 15;
	/** A master clicked or talked to this recently owns the task list that just opened. */
	private static final int MASTER_TICKS = 100;
	/** How long after talking to a master the overlay counts as "while skipping". */
	private static final int CONTEXT_TICKS = 15;
	private static final Pattern SLOT = Pattern.compile("^Slot \\d+:?$");

	private final Client client;
	private final TrackerStore store;
	private final SlayerTaskLuckConfig config;
	private final Runnable onNotice;
	private final Map<Integer, String> taskNames = new HashMap<>();

	private boolean expectReload = true;
	private int settleTicks;
	private boolean haveBaseline;
	private int baseTarget;
	private int baseOriginal;
	private int baseCount;
	private int pendingTick = -1;

	private String lastSpeaker;
	private int lastSpeakerTick = -10_000;
	private String lastMaster;
	private int lastMasterTick = -10_000;
	private int listReadTick = -1;
	private int rewardsReadTick = -1;
	private Boolean currentTaskFiller;

	private volatile boolean skippingContext;
	private volatile int slayerLevel;
	private volatile int combatLevel;
	private volatile String notice;

	public SlayerTracker(Client client, TrackerStore store, SlayerTaskLuckConfig config, Runnable onNotice)
	{
		this.client = client;
		this.store = store;
		this.config = config;
		this.onNotice = onNotice;
	}

	/** Call on the client thread when the plugin starts. */
	public void start()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			expectReload = false;
			captureBaseline();
		}
	}

	public boolean isSkippingContext()
	{
		return skippingContext;
	}

	public int getSlayerLevel()
	{
		return slayerLevel;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	/** A one-off message for the panel (e.g. a list we couldn't attribute), or null. */
	public String getNotice()
	{
		return notice;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGIN_SCREEN:
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				expectReload = true;
				haveBaseline = false;
				pendingTick = -1;
				break;
			case LOGGED_IN:
				// LOGGED_IN also follows every loading screen; only a real login or hop reloads the varps.
				if (expectReload)
				{
					expectReload = false;
					settleTicks = LOGIN_SETTLE_TICKS;
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!haveBaseline || settleTicks > 0 || event.getVarbitId() != -1)
		{
			return;
		}
		final int varp = event.getVarpId();
		final boolean trigger = varp == VarPlayerID.SLAYER_TARGET
			|| varp == VarPlayerID.SLAYER_COUNT_ORIGINAL
			|| (varp == VarPlayerID.SLAYER_COUNT && event.getValue() > baseCount);
		if (trigger && pendingTick < 0)
		{
			pendingTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		final int now = client.getTickCount();
		if (settleTicks > 0)
		{
			if (--settleTicks == 0)
			{
				captureBaseline();
			}
		}
		else if (!haveBaseline && client.getGameState() == GameState.LOGGED_IN)
		{
			captureBaseline();
		}

		if (pendingTick >= 0 && now > pendingTick)
		{
			pendingTick = -1;
			finishAssignment(now);
		}
		if (listReadTick >= 0 && now >= listReadTick)
		{
			listReadTick = -1;
			readTaskList(now);
		}
		if (rewardsReadTick >= 0 && now >= rewardsReadTick)
		{
			rewardsReadTick = -1;
			readBlockedTasks();
		}

		final int count = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		skippingContext = count <= 0
			|| Boolean.TRUE.equals(currentTaskFiller)
			|| now - Math.max(lastSpeakerTick, lastMasterTick) <= CONTEXT_TICKS;
		updateLevels();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.DIALOG)
		{
			return;
		}
		// Dialogue lines arrive as "Speaker|text".
		final String message = event.getMessage();
		final int bar = message.indexOf('|');
		if (bar <= 0)
		{
			return;
		}
		final String speaker = Masters.cleanName(message.substring(0, bar));
		if (speaker != null && !isLocalPlayer(speaker))
		{
			lastSpeaker = speaker;
			lastSpeakerTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		noteMaster(event.getMenuEntry().getNpc());
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		final Actor target = event.getTarget();
		if (event.getSource() == client.getLocalPlayer() && target instanceof NPC)
		{
			noteMaster((NPC) target);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Rows are filled in a tick after the interface loads, so read on the next tick.
		if (event.getGroupId() == InterfaceID.SLAYER_REWARDS_TASK_LIST)
		{
			listReadTick = client.getTickCount() + 1;
		}
		else if (event.getGroupId() == InterfaceID.SLAYER_REWARDS)
		{
			rewardsReadTick = client.getTickCount() + 1;
		}
	}

	private void noteMaster(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		final String name = Masters.cleanName(npc.getName());
		if (Masters.isKnownMaster(name))
		{
			lastMaster = name;
			lastMasterTick = client.getTickCount();
		}
	}

	private void captureBaseline()
	{
		baseTarget = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		baseOriginal = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		baseCount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		haveBaseline = true;
		pendingTick = -1;

		currentTaskFiller = null;
		if (baseCount > 0 && baseTarget > 0)
		{
			currentTaskFiller = store.fillerFor(baseTarget, baseOriginal);
			if (currentTaskFiller == null)
			{
				final int slot = client.getVarbitValue(VarbitID.SLAYER_MASTER);
				currentTaskFiller = Masters.isFiller(store.nameForSlot(slot), slot,
					config.fillerAya(), config.fillerTurael(), config.fillerSpria());
			}
		}
		updateLevels();
		log.debug("Slayer baseline: task {} ({}/{}), filler={}", baseTarget, baseCount, baseOriginal, currentTaskFiller);
	}

	private void finishAssignment(int now)
	{
		final int target = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		final int original = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
		final int count = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		final int area = client.getVarpValue(VarPlayerID.SLAYER_AREA);
		final int slot = client.getVarbitValue(VarbitID.SLAYER_MASTER);

		final boolean changed = target != baseTarget || original != baseOriginal || count > baseCount;
		final String excluded = baseCount > 0 && baseTarget > 0 ? taskName(baseTarget) : null;
		baseTarget = target;
		baseOriginal = original;
		baseCount = count;
		if (!changed || target <= 0 || count <= 0)
		{
			return; // cancelled, cleared or completed, not a new task
		}

		String master = null;
		if (now - lastSpeakerTick <= SPEAKER_TICKS)
		{
			master = lastSpeaker;
		}
		else if (now - lastMasterTick <= SPEAKER_TICKS)
		{
			master = lastMaster;
		}
		if (master == null)
		{
			master = store.nameForSlot(slot);
		}
		final String group = Masters.groupOf(master, slot);
		final boolean filler = Masters.isFiller(master, slot, config.fillerAya(), config.fillerTurael(), config.fillerSpria());
		final String task = taskName(target);

		store.recordRoll(RollDraft.builder()
			.timestamp(System.currentTimeMillis())
			.masterName(master != null ? master : Masters.fallbackName(slot))
			.masterSlot(slot)
			.masterGroup(group)
			.task(task)
			.taskId(target)
			.amount(original)
			.area(area)
			.filler(filler)
			.excludedTask(excluded)
			.slayerLevel(slayerLevel)
			.combatLevel(combatLevel)
			.build());
		currentTaskFiller = filler;
		log.debug("Assignment: {} x{} from {} (slot {}, group {}, filler {})", task, original, master, slot, group, filler);
	}

	private void readTaskList(int now)
	{
		final Widget drawable = client.getWidget(InterfaceID.SlayerRewardsTaskList.DRAWABLE);
		if (drawable == null)
		{
			return;
		}
		final List<String> texts = new ArrayList<>();
		collectTexts(drawable, texts, 0);
		final List<SnapshotEntry> entries = OddsParser.parse(texts);
		if (entries.size() < 3)
		{
			log.debug("Task list had {} parsable rows; ignored", entries.size());
			return;
		}

		String master = null;
		if (now - lastMasterTick <= MASTER_TICKS)
		{
			master = lastMaster;
		}
		else if (now - lastSpeakerTick <= MASTER_TICKS && Masters.isKnownMaster(lastSpeaker))
		{
			master = lastSpeaker;
		}
		if (master == null)
		{
			notice = "Couldn't tell which master that task list belongs to. Talk to the master, then open it again.";
			onNotice.run();
			return;
		}

		final int count = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		final String held = count > 0 ? taskName(client.getVarpValue(VarPlayerID.SLAYER_TARGET)) : null;
		updateLevels();
		final OddsSnapshot snapshot = OddsModel.buildSnapshot(Masters.groupOf(master, 0), master,
			System.currentTimeMillis(), slayerLevel, combatLevel, entries, held);
		store.addSnapshot(snapshot);
		notice = null;
		log.debug("Task list from {}: {} rows, exact weights {} (total {})", master, entries.size(),
			snapshot.isWeightsExact(), snapshot.getTotalWeight());
	}

	private void readBlockedTasks()
	{
		final Widget tasks = client.getWidget(InterfaceID.SlayerRewards.TASKS_CONTENT_SCROLLABLE);
		if (tasks == null)
		{
			return;
		}
		final List<String> texts = new ArrayList<>();
		collectTexts(tasks, texts, 0);
		final List<String> blocked = new ArrayList<>();
		boolean sawSlot = false;
		for (int i = 0; i < texts.size(); i++)
		{
			if (SLOT.matcher(texts.get(i).trim()).matches())
			{
				sawSlot = true;
				if (i + 1 < texts.size())
				{
					final String name = Masters.cleanName(texts.get(i + 1));
					if (name != null && !name.equalsIgnoreCase("Empty") && !SLOT.matcher(name).matches())
					{
						blocked.add(name);
					}
				}
			}
		}
		if (sawSlot)
		{
			store.setBlocked(blocked);
		}
	}

	private static void collectTexts(Widget w, List<String> out, int depth)
	{
		if (w == null || depth > 6)
		{
			return;
		}
		final String text = w.getText();
		if (text != null && !text.isEmpty())
		{
			out.add(text);
		}
		for (Widget[] children : new Widget[][]{w.getDynamicChildren(), w.getStaticChildren(), w.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget c : children)
				{
					collectTexts(c, out, depth + 1);
				}
			}
		}
	}

	/** Task name from the game's Slayer task table (the row whose COL_ID matches SLAYER_TARGET). */
	private String taskName(int taskId)
	{
		if (taskId <= 0)
		{
			return null;
		}
		return taskNames.computeIfAbsent(taskId, id ->
		{
			try
			{
				final List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, id);
				if (rows != null && !rows.isEmpty())
				{
					final Object[] name = client.getDBTableField(rows.get(0), DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
					if (name != null && name.length > 0 && name[0] != null)
					{
						return String.valueOf(name[0]);
					}
				}
			}
			catch (RuntimeException e)
			{
				log.debug("Task name lookup failed for {}", id, e);
			}
			return "Task #" + id;
		});
	}

	private void updateLevels()
	{
		slayerLevel = client.getRealSkillLevel(Skill.SLAYER);
		final Player p = client.getLocalPlayer();
		if (p != null)
		{
			combatLevel = p.getCombatLevel();
		}
	}

	private boolean isLocalPlayer(String name)
	{
		final Player p = client.getLocalPlayer();
		final String own = p != null ? Masters.cleanName(p.getName()) : null;
		return own != null && own.equalsIgnoreCase(name);
	}
}
