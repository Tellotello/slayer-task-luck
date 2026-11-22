package com.slayertaskluck.tracking;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Slayer master names and groups. Masters that share a role (Duradel/Kuradal, Turael/Aya, Nieve/Steve) are grouped
 * so stats carry over, while the name the game shows is kept for display.
 */
public final class Masters
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Map<String, String> GROUP_BY_NAME = new HashMap<>();
	/** Group key to the name shown for it, easiest master first. */
	private static final Map<String, String> GROUPS = new LinkedHashMap<>();

	static
	{
		group("Turael", "Aya");
		group("Spria");
		group("Mazchna", "Achtryn Vis");
		group("Vannaka");
		group("Chaeldar");
		group("Konar", "Konar quo Maten");
		group("Nieve", "Steve");
		group("Duradel", "Kuradal");
		group("Krystilia");
	}

	private Masters()
	{
	}

	/** A group is keyed by its first master's lower-case name ("Duradel" and "Kuradal" are "duradel"). */
	private static void group(String... names)
	{
		final String group = names[0].toLowerCase(Locale.ROOT);
		GROUPS.put(group, names[0]);
		for (String name : names)
		{
			GROUP_BY_NAME.put(name.toLowerCase(Locale.ROOT), group);
		}
	}

	/** Every master group (key to display name), easiest master first. */
	public static Map<String, String> groups()
	{
		return Collections.unmodifiableMap(GROUPS);
	}

	/** Strips colour tags and non-breaking spaces from a name the game shows. */
	public static String cleanName(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		final String clean = TAGS.matcher(raw).replaceAll("").replace(' ', ' ').trim();
		return clean.isEmpty() ? null : clean;
	}

	public static boolean isKnownMaster(String name)
	{
		return name != null && GROUP_BY_NAME.containsKey(name.toLowerCase(Locale.ROOT));
	}

	/** Group key for stats. Unknown masters get their own group; an unknown name falls back to the role slot. */
	public static String groupOf(String name, int slot)
	{
		if (name != null)
		{
			final String lower = name.toLowerCase(Locale.ROOT);
			return GROUP_BY_NAME.getOrDefault(lower, lower);
		}
		return "slot" + slot;
	}

	public static String fallbackName(int slot)
	{
		return "Slayer master " + slot;
	}

	/**
	 * Whether a master counts as a filler (its tasks are skips, never luck rolls). Decided by name from settings.
	 * Without a name, slot 1 (Turael's role) counts as filler when Aya or Turael is enabled.
	 */
	public static boolean isFiller(String name, int slot, boolean aya, boolean turael, boolean spria)
	{
		if (name == null)
		{
			return slot == 1 && (aya || turael);
		}
		switch (name.toLowerCase(Locale.ROOT))
		{
			case "aya":
				return aya;
			case "turael":
				return turael;
			case "spria":
				return spria;
			default:
				return false;
		}
	}
}
