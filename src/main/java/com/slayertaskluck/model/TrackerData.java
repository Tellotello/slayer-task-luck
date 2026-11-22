package com.slayertaskluck.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** Everything stored for one RuneScape profile (account + world type). Saved as one JSON file. */
@Data
public class TrackerData
{
	public static final int SCHEMA_VERSION = 1;

	private int schemaVersion = SCHEMA_VERSION;
	private List<OddsSnapshot> snapshots = new ArrayList<>();
	private List<Roll> rolls = new ArrayList<>();
	/** Wanted task names per master group. */
	private Map<String, List<String>> wanted = new HashMap<>();
	/** Last display name seen per master group. */
	private Map<String, String> masterNames = new HashMap<>();
	/** Blocked task names from the rewards Tasks tab (account-wide), and when that list last changed. */
	private List<String> blocked = new ArrayList<>();
	private long blockedChangedAt;
}
