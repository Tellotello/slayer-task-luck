package com.slayertaskluck.tracking;

import lombok.Builder;
import lombok.Value;

/** What the tracker saw when a task was assigned, before odds are attached. */
@Value
@Builder
public class RollDraft
{
	long timestamp;
	String masterName;
	int masterSlot;
	String masterGroup;
	String task;
	int taskId;
	int amount;
	int area;
	boolean filler;
	/** Task held at roll time (still active), which the game leaves out of the roll. */
	String excludedTask;
	int slayerLevel;
	int combatLevel;
}
