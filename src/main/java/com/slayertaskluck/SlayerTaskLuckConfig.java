package com.slayertaskluck;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(SlayerTaskLuckConfig.GROUP)
public interface SlayerTaskLuckConfig extends Config
{
	String GROUP = "slayertaskluck";

	@ConfigSection(
		name = "Overlay",
		description = "The small on-screen summary. The side panel is always available.",
		position = 0
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Masters",
		description = "Which masters count as fillers. Filler tasks are skips: they're logged and counted as detours, never as luck rolls.",
		position = 1
	)
	String mastersSection = "masters";

	@ConfigSection(
		name = "Luck thresholds",
		description = "When a result is called unlucky or lucky",
		position = 2,
		closedByDefault = true
	)
	String thresholdSection = "thresholds";

	@ConfigItem(
		keyName = "overlayMode",
		name = "Show overlay",
		description = "While skipping: when you have no task, a filler task, or are talking to a master.",
		position = 0,
		section = overlaySection
	)
	default OverlayMode overlayMode()
	{
		return OverlayMode.WHILE_SKIPPING;
	}

	@ConfigItem(
		keyName = "showWantedLine",
		name = "Show wanted line",
		description = "Wanted tasks received vs expected",
		position = 1,
		section = overlaySection
	)
	default boolean showWantedLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDetoursLine",
		name = "Show detours line",
		description = "Filler tasks done since your last wanted task, and the time they took",
		position = 2,
		section = overlaySection
	)
	default boolean showDetoursLine()
	{
		return false;
	}

	@ConfigItem(
		keyName = "fillerAya",
		name = "Aya is a filler",
		description = "Count Aya's tasks as skips",
		position = 0,
		section = mastersSection
	)
	default boolean fillerAya()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fillerTurael",
		name = "Turael is a filler",
		description = "Count Turael's tasks as skips",
		position = 1,
		section = mastersSection
	)
	default boolean fillerTurael()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fillerSpria",
		name = "Spria is a filler",
		description = "Count Spria's tasks as skips",
		position = 2,
		section = mastersSection
	)
	default boolean fillerSpria()
	{
		return true;
	}

	@Range(min = 1, max = 25)
	@ConfigItem(
		keyName = "unluckyPercent",
		name = "Unlucky below (%)",
		description = "Results rarer than this on the unlucky side are shown in red",
		position = 0,
		section = thresholdSection
	)
	default int unluckyPercent()
	{
		return 10;
	}

	@Range(min = 1, max = 25)
	@ConfigItem(
		keyName = "luckyPercent",
		name = "Lucky above (%)",
		description = "Results rarer than this on the lucky side are shown in green",
		position = 1,
		section = thresholdSection
	)
	default int luckyPercent()
	{
		return 10;
	}
}
