package com.slayertaskluck.model;

import lombok.Data;

/** One Slayer task assignment. */
@Data
public class Roll
{
	private String id;
	private long timestamp;

	private String masterGroup;
	private String masterName;
	/** Value of the SLAYER_MASTER varbit (role slot: 1 = Turael/Aya, 5 = Duradel/Kuradal). */
	private int masterSlot;

	private String task;
	private int taskId;
	private int amount;
	/** Konar location id (0 for other masters). */
	private int area;

	/** Assigned by a filler master (Aya/Turael/Spria by default). Filler rolls never enter the luck math. */
	private boolean filler;

	/** Player levels when rolled, used to tell whether the saved odds still applied. */
	private int slayerLevel;
	private int combatLevel;

	// Target-master rolls only
	private String snapshotId;
	/** Task held when rolling, which the game removes from the roll. Null when none. */
	private String excludedTask;
	/** Chance of the task actually received. */
	private Double pTask;
	/** Chance that this roll gave a wanted task. */
	private Double pWanted;
	private Boolean wanted;
	private Confidence confidence;
	private String confidenceNote;

	/** Set by the user to leave a misdetected or unwanted entry out of the stats (reversible). */
	private boolean excluded;

	/** Field-by-field copy; much cheaper than a JSON round trip, so locks are held only briefly. */
	public Roll copy()
	{
		final Roll r = new Roll();
		r.id = id;
		r.timestamp = timestamp;
		r.masterGroup = masterGroup;
		r.masterName = masterName;
		r.masterSlot = masterSlot;
		r.task = task;
		r.taskId = taskId;
		r.amount = amount;
		r.area = area;
		r.filler = filler;
		r.slayerLevel = slayerLevel;
		r.combatLevel = combatLevel;
		r.snapshotId = snapshotId;
		r.excludedTask = excludedTask;
		r.pTask = pTask;
		r.pWanted = pWanted;
		r.wanted = wanted;
		r.confidence = confidence;
		r.confidenceNote = confidenceNote;
		r.excluded = excluded;
		return r;
	}

	/** True when this roll can be used in the luck math. */
	public boolean countsForLuck()
	{
		return !filler && !excluded && confidence != null && confidence != Confidence.NONE
			&& pWanted != null && wanted != null;
	}
}
