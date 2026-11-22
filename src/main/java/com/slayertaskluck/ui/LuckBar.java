package com.slayertaskluck.ui;

import com.slayertaskluck.math.Verdict;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * Lifetime luck as a position on a bar: far left is unluckiest, far right luckiest. The shaded ends are the rarest
 * results on each side (the configured thresholds); the dot is the player.
 */
class LuckBar extends JComponent
{
	private static final int BAR_HEIGHT = 8;
	private static final int DOT = 12;

	private double percentile = Double.NaN;
	private Verdict.Tone tone = Verdict.Tone.NONE;
	private double unluckyCut = 0.1;
	private double luckyCut = 0.1;

	LuckBar()
	{
		setPreferredSize(new Dimension(UiStyle.CONTENT_WIDTH, 34));
		setMinimumSize(getPreferredSize());
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
	}

	void setValue(double percentile, Verdict.Tone tone, double unluckyCut, double luckyCut)
	{
		this.percentile = percentile;
		this.tone = tone;
		this.unluckyCut = unluckyCut;
		this.luckyCut = luckyCut;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		final Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int pad = DOT / 2;
		final int w = getWidth() - 2 * pad;
		final int y = 4;

		g.setColor(UiStyle.blend(UiStyle.CARD, Color.WHITE, 0.08));
		g.fillRoundRect(pad, y + 2, w, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
		g.setColor(UiStyle.blend(UiStyle.CARD, UiStyle.UNLUCKY, 0.45));
		g.fillRoundRect(pad, y + 2, (int) Math.round(w * unluckyCut) + BAR_HEIGHT / 2, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
		g.setColor(UiStyle.blend(UiStyle.CARD, UiStyle.LUCKY, 0.45));
		final int luckyW = (int) Math.round(w * luckyCut);
		g.fillRoundRect(pad + w - luckyW - BAR_HEIGHT / 2, y + 2, luckyW + BAR_HEIGHT / 2, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);

		if (!Double.isNaN(percentile))
		{
			final int x = pad + (int) Math.round(w * Math.max(0, Math.min(1, percentile)));
			g.setColor(UiStyle.CARD);
			g.fillOval(x - DOT / 2 - 1, y + 2 + BAR_HEIGHT / 2 - DOT / 2 - 1, DOT + 2, DOT + 2);
			g.setColor(tone == Verdict.Tone.NORMAL ? Color.WHITE : UiStyle.toneColor(tone));
			g.fillOval(x - DOT / 2, y + 2 + BAR_HEIGHT / 2 - DOT / 2, DOT, DOT);
		}

		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(UiStyle.MUTED);
		final int textY = y + BAR_HEIGHT + 20;
		g.drawString("unluckiest", pad, textY);
		final String right = "luckiest";
		g.drawString(right, pad + w - g.getFontMetrics().stringWidth(right), textY);
		g.dispose();
	}
}
