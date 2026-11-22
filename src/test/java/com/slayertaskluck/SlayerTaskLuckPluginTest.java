package com.slayertaskluck;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher: starts the full RuneLite client with this plugin loaded as if it were built in.
 * Run with `./gradlew run` (adds --developer-mode --debug), or run this main() from the IDE.
 */
public class SlayerTaskLuckPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SlayerTaskLuckPlugin.class);
		RuneLite.main(args);
	}
}
