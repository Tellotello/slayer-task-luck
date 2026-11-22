package com.slayertaskluck;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum OverlayMode
{
	WHILE_SKIPPING("While skipping"),
	ALWAYS("Always"),
	NEVER("Never");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
