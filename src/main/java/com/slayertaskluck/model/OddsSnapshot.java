package com.slayertaskluck.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * A master's task list captured when the player opened it. Kept as history so every roll keeps the odds it was
 * rolled under.
 */
@Data
public class OddsSnapshot
{
	private String id;
	/** Canonical master group (e.g. "duradel" covers Duradel and Kuradal). */
	private String masterGroup;
	/** Name the game showed. */
	private String masterName;
	private long timestamp;
	private int slayerLevel;
	private int combatLevel;
	private List<SnapshotEntry> entries = new ArrayList<>();
	/**
	 * The task the player was on while the list was open. The game shows it as 0%, so its weight is unknown in
	 * this snapshot and is borrowed from another snapshot when needed.
	 */
	private String heldTask;
	/** True when integer game weights were recovered exactly from the shown percentages. */
	private boolean weightsExact;
	private int totalWeight;
	/** Identifies identical lists so re-opening an unchanged list doesn't add a snapshot. */
	private String contentHash;
}
