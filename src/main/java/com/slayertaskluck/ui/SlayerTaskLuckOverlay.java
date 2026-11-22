package com.slayertaskluck.ui;

import com.slayertaskluck.OverlayMode;
import com.slayertaskluck.SlayerTaskLuckConfig;
import com.slayertaskluck.math.GroupStats;
import com.slayertaskluck.math.Verdict;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * The small on-screen summary for the master you last rolled at. Reads a precomputed view, so it does no math per
 * frame. Hidden until there is data.
 */
public class SlayerTaskLuckOverlay extends OverlayPanel
{
	private final SlayerTaskLuckConfig config;
	private final Supplier<TrackerView> view;
	private final BooleanSupplier skipping;

	public SlayerTaskLuckOverlay(SlayerTaskLuckConfig config, Supplier<TrackerView> view, BooleanSupplier skipping)
	{
		this.config = config;
		this.view = view;
		this.skipping = skipping;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(170, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final OverlayMode mode = config.overlayMode();
		final TrackerView v = view.get();
		if (mode == OverlayMode.NEVER || v == null || v.getState() != TrackerView.State.READY || v.getDefaultGroup() == null)
		{
			return null;
		}
		if (mode == OverlayMode.WHILE_SKIPPING && !skipping.getAsBoolean())
		{
			return null;
		}
		final GroupStats s = v.getStats().get(v.getDefaultGroup());
		if (s == null || !s.hasLuckData())
		{
			return null;
		}

		final Verdict luck = s.getLuckVerdict();
		panelComponent.getChildren().add(LineComponent.builder()
			.left(s.getName())
			.leftColor(Color.WHITE)
			.right(luck.getTone().word())
			.rightColor(UiStyle.toneColor(luck.getTone()))
			.build());
		final Verdict dry = s.getDryVerdict();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Dry streak")
			.right(s.getDryRolls() + " - " + dry.chip())
			.rightColor(UiStyle.toneColor(dry.getTone()))
			.build());
		if (config.showWantedLine())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Wanted")
				.right(s.getObserved() + " / " + UiStyle.oneDecimal(s.getExpected()) + " - " + luck.chip())
				.rightColor(UiStyle.toneColor(luck.getTone()))
				.build());
		}
		if (config.showDetoursLine() && s.getTotalDetours() > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Detours")
				.right(s.getDetours() + (s.getDetourMillis() > 0 ? " - " + UiStyle.duration(s.getDetourMillis()) : ""))
				.build());
		}
		return super.render(graphics);
	}
}
