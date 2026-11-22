package com.slayertaskluck.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of a Slayer master's task list, as shown in game. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotEntry
{
	/** Task name as shown, e.g. "Abyssal Demons". */
	private String name;
	/** Assigned amount range as shown; 0/0 when the list says "Varies". */
	private int min;
	private int max;
	/** The percentage the game displays (truncated to 0.1%). Kept for validation and display only. */
	private double shownPercent;
	/**
	 * Relative weight used for the math. Exact integer game weight when {@link OddsSnapshot#isWeightsExact()},
	 * otherwise the shown per-mille value. 0 means the task can't be assigned.
	 */
	private int weight;
}
