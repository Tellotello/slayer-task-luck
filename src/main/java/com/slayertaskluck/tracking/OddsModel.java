package com.slayertaskluck.tracking;

import com.slayertaskluck.model.OddsSnapshot;
import com.slayertaskluck.model.SnapshotEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Value;

/** Turns task-list snapshots into roll probabilities. Pure logic, no game access. */
public final class OddsModel
{
	public static final String BOSS = "Boss";

	private OddsModel()
	{
	}

	/** Builds a snapshot from parsed rows, recovering exact weights when possible. */
	public static OddsSnapshot buildSnapshot(String masterGroup, String masterName, long timestamp,
		int slayerLevel, int combatLevel, List<SnapshotEntry> entries, String heldTask)
	{
		final int[] permille = new int[entries.size()];
		for (int i = 0; i < permille.length; i++)
		{
			permille[i] = OddsParser.toPermille(entries.get(i).getShownPercent());
		}
		final int[] weights = WeightInference.infer(permille);
		int total = 0;
		for (int i = 0; i < permille.length; i++)
		{
			final int w = weights != null ? weights[i] : permille[i];
			entries.get(i).setWeight(w);
			total += w;
		}

		final OddsSnapshot s = new OddsSnapshot();
		s.setId(UUID.randomUUID().toString());
		s.setMasterGroup(masterGroup);
		s.setMasterName(masterName);
		s.setTimestamp(timestamp);
		s.setSlayerLevel(slayerLevel);
		s.setCombatLevel(combatLevel);
		s.setEntries(entries);
		s.setWeightsExact(weights != null);
		s.setTotalWeight(total);
		final SnapshotEntry held = heldTask == null ? null : find(s, heldTask);
		s.setHeldTask(held != null ? held.getName() : null);
		s.setContentHash(hash(entries, s.getHeldTask()));
		return s;
	}

	/** Normalised key for matching task names across sources ("Kalphite"/"Kalphites", "TzHaar"/"Tzhaar"). */
	public static String key(String name)
	{
		if (name == null)
		{
			return "";
		}
		String k = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
		if (k.endsWith("s"))
		{
			k = k.substring(0, k.length() - 1);
		}
		return k;
	}

	public static SnapshotEntry find(OddsSnapshot s, String task)
	{
		final String k = key(task);
		for (SnapshotEntry e : s.getEntries())
		{
			if (key(e.getName()).equals(k))
			{
				return e;
			}
		}
		return null;
	}

	/**
	 * Weights the game would use for a roll: every available task in the snapshot, plus the task held while the
	 * snapshot was taken (shown as 0% then; weight borrowed from another exact snapshot), minus the task held at
	 * roll time.
	 */
	public static Map<String, Integer> rollWeights(OddsSnapshot s, String excludedTask, Map<String, Integer> borrowed)
	{
		final Map<String, Integer> weights = new LinkedHashMap<>();
		for (SnapshotEntry e : s.getEntries())
		{
			if (e.getWeight() > 0)
			{
				weights.put(e.getName(), e.getWeight());
			}
		}
		if (s.getHeldTask() != null && !weights.containsKey(s.getHeldTask()) && s.isWeightsExact() && borrowed != null)
		{
			final Integer w = borrowed.get(key(s.getHeldTask()));
			if (w != null && w > 0)
			{
				weights.put(s.getHeldTask(), w);
			}
		}
		if (excludedTask != null)
		{
			final SnapshotEntry ex = find(s, excludedTask);
			if (ex != null)
			{
				weights.remove(ex.getName());
			}
		}
		return weights;
	}

	/** Probability distribution (task name -> chance) for a roll. */
	public static Map<String, Double> distribution(OddsSnapshot s, String excludedTask, Map<String, Integer> borrowed)
	{
		final Map<String, Integer> weights = rollWeights(s, excludedTask, borrowed);
		long total = 0;
		for (int w : weights.values())
		{
			total += w;
		}
		final Map<String, Double> dist = new LinkedHashMap<>();
		if (total == 0)
		{
			return dist;
		}
		for (Map.Entry<String, Integer> e : weights.entrySet())
		{
			dist.put(e.getKey(), e.getValue() / (double) total);
		}
		return dist;
	}

	/** Exact weights by task key from the given exact snapshots, newest first wins. */
	public static Map<String, Integer> learnedWeights(Collection<OddsSnapshot> newestFirst)
	{
		final Map<String, Integer> learned = new HashMap<>();
		for (OddsSnapshot s : newestFirst)
		{
			if (!s.isWeightsExact())
			{
				continue;
			}
			for (SnapshotEntry e : s.getEntries())
			{
				if (e.getWeight() > 0)
				{
					learned.putIfAbsent(key(e.getName()), e.getWeight());
				}
			}
		}
		return learned;
	}

	/** Whether the snapshot is missing a weight it needs (held task with nothing to borrow from). */
	public static boolean missingHeldWeight(OddsSnapshot s, String excludedTask, Map<String, Integer> borrowed)
	{
		if (s.getHeldTask() == null || key(s.getHeldTask()).equals(key(excludedTask)))
		{
			return false;
		}
		return !s.isWeightsExact() || borrowed == null || !borrowed.containsKey(key(s.getHeldTask()));
	}

	/** Result of scoring one roll against a distribution. */
	@Value
	public static class Scored
	{
		/** Name of the matched list row, or null when the received task isn't on the list. */
		String matched;
		double pTask;
		double pWanted;
		boolean wanted;
	}

	/**
	 * Scores a received task against a distribution and a wanted set. Boss tasks are assigned as the specific boss,
	 * so a task that isn't on the list falls back to the list's "Boss" row when that row is possible.
	 */
	public static Scored score(Map<String, Double> dist, String receivedTask, Collection<String> wantedNames)
	{
		String matched = null;
		final String k = key(receivedTask);
		for (String name : dist.keySet())
		{
			if (key(name).equals(k))
			{
				matched = name;
				break;
			}
		}
		if (matched == null && dist.containsKey(BOSS))
		{
			matched = BOSS;
		}

		final Set<String> wantedKeys = new HashSet<>();
		if (wantedNames != null)
		{
			for (String w : wantedNames)
			{
				wantedKeys.add(key(w));
			}
		}
		double pWanted = 0;
		boolean wanted = false;
		for (Map.Entry<String, Double> e : dist.entrySet())
		{
			if (wantedKeys.contains(key(e.getKey())))
			{
				pWanted += e.getValue();
				if (e.getKey().equals(matched))
				{
					wanted = true;
				}
			}
		}
		final double pTask = matched != null ? dist.get(matched) : 0;
		return new Scored(matched, pTask, Math.min(1.0, pWanted), wanted);
	}

	private static String hash(List<SnapshotEntry> entries, String held)
	{
		final StringBuilder sb = new StringBuilder();
		for (SnapshotEntry e : entries)
		{
			sb.append(e.getName()).append('=').append(OddsParser.toPermille(e.getShownPercent())).append(';');
		}
		sb.append("held=").append(held);
		try
		{
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			final StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++)
			{
				hex.append(String.format("%02x", digest[i]));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			return Integer.toHexString(sb.toString().hashCode());
		}
	}
}
