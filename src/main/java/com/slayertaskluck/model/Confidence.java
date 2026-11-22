package com.slayertaskluck.model;

/** How much the odds attached to a roll can be trusted. */
public enum Confidence
{
	/** Odds from a snapshot that still matches the player's state. */
	CURRENT,
	/** Odds from a snapshot that may be out of date (levels or blocklist changed since). Still counted, but flagged. */
	STALE,
	/** No usable odds. Shown in history, left out of the luck math. */
	NONE
}
