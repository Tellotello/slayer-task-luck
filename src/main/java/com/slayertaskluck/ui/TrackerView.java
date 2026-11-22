package com.slayertaskluck.ui;

import com.slayertaskluck.math.GroupStats;
import com.slayertaskluck.model.Roll;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/** Immutable snapshot of everything the panel and overlay show. Built off the client thread. */
@Value
@Builder(toBuilder = true)
public class TrackerView
{
	public static final String ALL = "*";

	public enum State
	{
		LOGGED_OUT,
		LOADING,
		READY
	}

	public static final TrackerView EMPTY = TrackerView.builder()
		.state(State.LOGGED_OUT)
		.masters(Collections.emptyList())
		.stats(Collections.emptyMap())
		.history(Collections.emptyList())
		.wantedOptions(Collections.emptyMap())
		.notices(Collections.emptyMap())
		.build();

	State state;
	/** Storage problem worth telling the user about, or null. */
	String problem;
	/** One-off tracker message (e.g. a task list it couldn't attribute), or null. */
	String notice;

	/** Target masters with data (most recently used first), then every other target master. */
	List<MasterOption> masters;
	/** Master shown by default (and by the overlay): the last one rolled at. */
	String defaultGroup;
	/** Stats per master group, plus {@link #ALL} when there are several. */
	Map<String, GroupStats> stats;
	/** All rolls, newest first. */
	List<Roll> history;
	Map<String, List<WantedOption>> wantedOptions;
	Map<String, List<String>> notices;
	/** Name of the filler master used most recently ("Aya"), for wording like "Aya detours". */
	String fillerName;
	int rollCount;
	int snapshotCount;

	public boolean hasAnyData()
	{
		return rollCount > 0 || snapshotCount > 0;
	}

	@Value
	public static class MasterOption
	{
		String group;
		String name;

		@Override
		public String toString()
		{
			return name;
		}
	}

	@Value
	public static class WantedOption
	{
		String task;
		/** Chance per roll with no active task. */
		double chance;
		boolean wanted;
	}
}
