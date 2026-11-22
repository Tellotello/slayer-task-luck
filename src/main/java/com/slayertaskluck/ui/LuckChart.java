package com.slayertaskluck.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * Luck over time: wanted tasks received minus expected after each roll. The dashed line is "exactly as expected";
 * the shaded bands are normal ups and downs (1 and 2 standard deviations). A line sinking below the band means
 * sustained bad luck.
 */
class LuckChart extends JComponent
{
	private static final int HEIGHT = 120;
	private static final int PAD_X = 4;
	private static final int PAD_Y = 8;

	private double[] diff = new double[0];
	private double[] sd = new double[0];

	LuckChart()
	{
		setPreferredSize(new Dimension(UiStyle.CONTENT_WIDTH, HEIGHT));
		setMinimumSize(getPreferredSize());
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setToolTipText("");
	}

	void setData(double[] diff, double[] sd)
	{
		this.diff = diff;
		this.sd = sd;
		repaint();
	}

	private double range()
	{
		double max = 1;
		for (int i = 0; i < diff.length; i++)
		{
			max = Math.max(max, Math.max(Math.abs(diff[i]), 2.2 * sd[i]));
		}
		return max * 1.1;
	}

	private int x(int i, int w)
	{
		final int n = Math.max(1, diff.length);
		return PAD_X + (int) Math.round((w - 2 * PAD_X) * (i + 1) / (double) n);
	}

	private int y(double v, double range, int h)
	{
		final double mid = h / 2.0;
		return (int) Math.round(mid - v / range * (mid - PAD_Y));
	}

	@Override
	protected void paintComponent(Graphics g0)
	{
		final Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int w = getWidth();
		final int h = getHeight();
		g.setColor(UiStyle.CARD);
		g.fillRect(0, 0, w, h);

		final int n = diff.length;
		if (n == 0)
		{
			g.dispose();
			return;
		}
		final double range = range();

		band(g, 2, range, w, h, UiStyle.blend(UiStyle.CARD, Color.WHITE, 0.05));
		band(g, 1, range, w, h, UiStyle.blend(UiStyle.CARD, Color.WHITE, 0.10));

		g.setColor(UiStyle.MUTED);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
		g.drawLine(PAD_X, y(0, range, h), w - PAD_X, y(0, range, h));

		final double last = diff[n - 1];
		final double lastSd = sd[n - 1];
		g.setColor(last < -lastSd ? UiStyle.UNLUCKY : last > lastSd ? UiStyle.LUCKY : Color.WHITE);
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int px = PAD_X;
		int py = y(0, range, h);
		for (int i = 0; i < n; i++)
		{
			final int cx = x(i, w);
			final int cy = y(diff[i], range, h);
			g.drawLine(px, py, cx, cy);
			px = cx;
			py = cy;
		}

		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(UiStyle.MUTED);
		g.drawString(n + " rolls", w - PAD_X - g.getFontMetrics().stringWidth(n + " rolls"), h - 3);
		g.dispose();
	}

	private void band(Graphics2D g, double k, double range, int w, int h, Color color)
	{
		final int n = diff.length;
		final Polygon p = new Polygon();
		p.addPoint(PAD_X, y(0, range, h));
		for (int i = 0; i < n; i++)
		{
			p.addPoint(x(i, w), y(k * sd[i], range, h));
		}
		for (int i = n - 1; i >= 0; i--)
		{
			p.addPoint(x(i, w), y(-k * sd[i], range, h));
		}
		g.setColor(color);
		g.fillPolygon(p);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		final int n = diff.length;
		if (n == 0)
		{
			return null;
		}
		final int w = getWidth();
		int best = 0;
		for (int i = 0; i < n; i++)
		{
			if (Math.abs(x(i, w) - e.getX()) < Math.abs(x(best, w) - e.getX()))
			{
				best = i;
			}
		}
		final double d = diff[best];
		return "After roll " + (best + 1) + ": " + (d >= 0 ? "+" : "") + UiStyle.oneDecimal(d)
			+ " wanted vs expected (normal range ±" + UiStyle.oneDecimal(sd[best]) + ")";
	}
}
