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

	/**
	 * Copy that is safe to hand to another thread. Rolls are copied (they change when excluded or re-scored);
	 * snapshots are shared because they are never modified after being stored.
	 */
	public TrackerData copy()
	{
		final TrackerData d = new TrackerData();
		d.schemaVersion = schemaVersion;
		d.snapshots = new ArrayList<>(snapshots);
		d.rolls = new ArrayList<>(rolls.size());
		for (Roll r : rolls)
		{
			d.rolls.add(r.copy());
		}
		d.wanted = new HashMap<>();
		for (Map.Entry<String, List<String>> e : wanted.entrySet())
		{
			d.wanted.put(e.getKey(), new ArrayList<>(e.getValue()));
		}
		d.masterNames = new HashMap<>(masterNames);
		d.blocked = new ArrayList<>(blocked);
		d.blockedChangedAt = blockedChangedAt;
		return d;
	}
}
