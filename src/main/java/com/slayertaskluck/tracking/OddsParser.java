package com.slayertaskluck.tracking;

import com.slayertaskluck.model.SnapshotEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Slayer task list ("Slayer Tasks" interface). Rows appear as a name text such as
 * {@code "Gargoyles (130-200)"} or {@code "Boss (Varies)"} followed, a few widgets later, by {@code "4.9%"}.
 * Pairing by pattern rather than by fixed child index keeps this working if the layout shifts.
 */
public final class OddsParser
{
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern NAME = Pattern.compile("^(.+?)\\s*\\((?:(\\d+)\\s*-\\s*(\\d+)|[A-Za-z ]+)\\)$");
	private static final Pattern PERCENT = Pattern.compile("^(\\d{1,3}(?:\\.\\d+)?)\\s*%$");

	private OddsParser()
	{
	}

	public static List<SnapshotEntry> parse(List<String> texts)
	{
		final List<SnapshotEntry> entries = new ArrayList<>();
		SnapshotEntry pending = null;
		for (String raw : texts)
		{
			if (raw == null)
			{
				continue;
			}
			final String text = TAGS.matcher(raw).replaceAll("").replace(' ', ' ').trim();
			if (text.isEmpty())
			{
				continue;
			}

			final Matcher percent = PERCENT.matcher(text);
			if (percent.matches())
			{
				if (pending != null)
				{
					pending.setShownPercent(Double.parseDouble(percent.group(1)));
					entries.add(pending);
					pending = null;
				}
				continue;
			}

			final Matcher name = NAME.matcher(text);
			if (name.matches())
			{
				pending = new SnapshotEntry();
				pending.setName(name.group(1).trim());
				if (name.group(2) != null)
				{
					pending.setMin(Integer.parseInt(name.group(2)));
					pending.setMax(Integer.parseInt(name.group(3)));
				}
			}
		}
		return entries;
	}

	/** Shown percentage as an integer number of tenths of a percent (per-mille), e.g. 4.9% -> 49. */
	public static int toPermille(double shownPercent)
	{
		return (int) Math.round(shownPercent * 10);
	}
}
