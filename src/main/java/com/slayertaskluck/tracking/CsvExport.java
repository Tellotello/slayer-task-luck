package com.slayertaskluck.tracking;

import com.slayertaskluck.model.Roll;
import com.slayertaskluck.model.TrackerData;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Every logged task as CSV, oldest first. */
public final class CsvExport
{
	private CsvExport()
	{
	}

	public static String build(TrackerData data)
	{
		final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
		iso.setTimeZone(TimeZone.getTimeZone("UTC"));
		final StringBuilder sb = new StringBuilder();
		sb.append("time_utc,master,master_group,task,amount,filler,wanted,chance_of_task,chance_of_wanted,confidence,excluded,note\n");
		final List<Roll> rolls = new ArrayList<>(data.getRolls());
		rolls.sort(Comparator.comparingLong(Roll::getTimestamp));
		for (Roll r : rolls)
		{
			sb.append(iso.format(new Date(r.getTimestamp()))).append(',')
				.append(cell(r.getMasterName())).append(',')
				.append(cell(r.getMasterGroup())).append(',')
				.append(cell(r.getTask())).append(',')
				.append(r.getAmount()).append(',')
				.append(r.isFiller()).append(',')
				.append(r.getWanted() == null ? "" : r.getWanted()).append(',')
				.append(r.getPTask() == null ? "" : String.format(Locale.ROOT, "%.6f", r.getPTask())).append(',')
				.append(r.getPWanted() == null ? "" : String.format(Locale.ROOT, "%.6f", r.getPWanted())).append(',')
				.append(r.getConfidence() == null ? "" : r.getConfidence()).append(',')
				.append(r.isExcluded()).append(',')
				.append(cell(r.getConfidenceNote())).append('\n');
		}
		return sb.toString();
	}

	private static String cell(String s)
	{
		if (s == null)
		{
			return "";
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n"))
		{
			return '"' + s.replace("\"", "\"\"") + '"';
		}
		return s;
	}
}
