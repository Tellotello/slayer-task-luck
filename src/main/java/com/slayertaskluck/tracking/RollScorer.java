package com.slayertaskluck.tracking;

import com.slayertaskluck.model.Confidence;
import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.Roll;
import com.slayertaskluck.model.TrackerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attaches odds to rolls from the saved snapshots. Pure logic over {@link TrackerData}. */
public final class RollScorer
{
	private RollScorer()
	{
	}

	public static Roll build(TrackerData data, RollDraft d)
	{
		final Roll r = new Roll();
		r.setId(UUID.randomUUID().toString());
		r.setTimestamp(d.getTimestamp());
		r.setMasterGroup(d.getMasterGroup());
		r.setMasterName(d.getMasterName());
		r.setMasterSlot(d.getMasterSlot());
		r.setTask(d.getTask());
		r.setTaskId(d.getTaskId());
		r.setAmount(d.getAmount());
		r.setArea(d.getArea());
		r.setFiller(d.isFiller());
		r.setExcludedTask(d.getExcludedTask());
		r.setSlayerLevel(d.getSlayerLevel());
		r.setCombatLevel(d.getCombatLevel());
		if (!r.isFiller())
		{
			score(data, r);
		}
		return r;
	}

	/** (Re)computes the odds of a target roll from the snapshot in effect when it was rolled. */
	public static void score(TrackerData data, Roll r)
	{
		r.setSnapshotId(null);
		r.setPTask(null);
		r.setPWanted(null);
		r.setWanted(null);

		final List<OddsSnapshot> group = snapshotsNewestFirst(data, r.getMasterGroup());
		OddsSnapshot snap = null;
		for (OddsSnapshot s : group)
		{
			if (s.getTimestamp() <= r.getTimestamp())
			{
				snap = s;
				break;
			}
		}
		if (snap == null)
		{
			r.setConfidence(Confidence.NONE);
			r.setConfidenceNote("No task list saved for this master yet");
			return;
		}
		r.setSnapshotId(snap.getId());

		final Map<String, Integer> borrowed = OddsModel.learnedWeights(group);
		final Map<String, Double> dist = OddsModel.distribution(snap, r.getExcludedTask(), borrowed);
		final List<String> wanted = data.getWanted().getOrDefault(r.getMasterGroup(), Collections.emptyList());
		final OddsModel.Scored scored = OddsModel.score(dist, r.getTask(), wanted);
		if (scored.getMatched() == null)
		{
			r.setConfidence(Confidence.NONE);
			r.setConfidenceNote("Not on the saved task list; open the list again");
			return;
		}

		r.setPTask(scored.getPTask());
		r.setPWanted(scored.getPWanted());
		r.setWanted(scored.isWanted());

		final List<String> stale = new ArrayList<>();
		if (r.getSlayerLevel() > 0 && snap.getSlayerLevel() > 0 && r.getSlayerLevel() != snap.getSlayerLevel())
		{
			stale.add("Slayer level changed");
		}
		if (r.getCombatLevel() > 0 && snap.getCombatLevel() > 0 && r.getCombatLevel() != snap.getCombatLevel())
		{
			stale.add("combat level changed");
		}
		if (data.getBlockedChangedAt() > snap.getTimestamp() && data.getBlockedChangedAt() <= r.getTimestamp())
		{
			stale.add("blocklist changed");
		}
		if (OddsModel.missingHeldWeight(snap, r.getExcludedTask(), borrowed))
		{
			stale.add("list was opened during a " + snap.getHeldTask() + " task");
		}
		if (!snap.isWeightsExact())
		{
			stale.add("approximate odds");
		}

		final List<String> notes = new ArrayList<>();
		if (OddsModel.BOSS.equals(scored.getMatched()) && !OddsModel.key(r.getTask()).equals(OddsModel.key(OddsModel.BOSS)))
		{
			notes.add("counted as a Boss task");
		}
		if (wanted.isEmpty())
		{
			notes.add("no wanted tasks picked yet");
		}
		if (stale.isEmpty())
		{
			r.setConfidence(Confidence.CURRENT);
		}
		else
		{
			r.setConfidence(Confidence.STALE);
			notes.addAll(0, stale);
		}
		r.setConfidenceNote(notes.isEmpty() ? null : capitalize(String.join(", ", notes)));
	}

	public static List<OddsSnapshot> snapshotsNewestFirst(TrackerData data, String group)
	{
		final List<OddsSnapshot> list = new ArrayList<>();
		for (OddsSnapshot s : data.getSnapshots())
		{
			if (s.getMasterGroup() != null && s.getMasterGroup().equals(group))
			{
				list.add(s);
			}
		}
		list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
		return list;
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
