package com.slayertaskluck.ui;

import com.slayertaskluck.math.Verdict;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Shared colours, fonts and formatting. Three soft tints only: red (unlucky), none (normal), green (lucky). */
public final class UiStyle
{
	public static final Color UNLUCKY = new Color(0xE0, 0x7A, 0x7A);
	public static final Color LUCKY = new Color(0x8C, 0xCB, 0x8C);
	public static final Color NOTICE = new Color(0xE8, 0xC4, 0x6A);
	public static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	public static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	public static final Color TEXT = ColorScheme.LIGHT_GRAY_COLOR;
	public static final Color MUTED = ColorScheme.MEDIUM_GRAY_COLOR;
	public static final Color LINK = ColorScheme.BRAND_ORANGE;

	/** Usable width inside the side panel's padding. */
	public static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 20;

	private UiStyle()
	{
	}

	public static Color toneColor(Verdict.Tone tone)
	{
		switch (tone)
		{
			case UNLUCKY:
				return UNLUCKY;
			case LUCKY:
				return LUCKY;
			default:
				return TEXT;
		}
	}

	/** Chip background: the tint blended into the card colour, so it stays soft. */
	public static Color chipBackground(Verdict.Tone tone)
	{
		switch (tone)
		{
			case UNLUCKY:
				return blend(CARD, UNLUCKY, 0.28);
			case LUCKY:
				return blend(CARD, LUCKY, 0.28);
			default:
				return blend(CARD, Color.WHITE, 0.06);
		}
	}

	public static Color blend(Color base, Color tint, double amount)
	{
		return new Color(
			(int) Math.round(base.getRed() + (tint.getRed() - base.getRed()) * amount),
			(int) Math.round(base.getGreen() + (tint.getGreen() - base.getGreen()) * amount),
			(int) Math.round(base.getBlue() + (tint.getBlue() - base.getBlue()) * amount));
	}

	public static JLabel label(String text, Font font, Color color)
	{
		final JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		return l;
	}

	public static JLabel small(String text)
	{
		return label(text, FontManager.getRunescapeSmallFont(), MUTED);
	}

	/** A rounded-looking chip label ("1 in 16", "normal"). */
	public static JLabel chip()
	{
		final JLabel l = new JLabel();
		l.setOpaque(true);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		return l;
	}

	public static void styleChip(JLabel chip, Verdict verdict)
	{
		chip.setText(verdict.chip());
		chip.setBackground(chipBackground(verdict.getTone()));
		chip.setForeground(verdict.isTinted() ? toneColor(verdict.getTone()) : TEXT);
	}

	/** Makes a label behave like a link. */
	public static JLabel link(String text, Runnable action)
	{
		final JLabel l = label(text, FontManager.getRunescapeSmallFont(), LINK);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		});
		return l;
	}

	public static String wrap(String text, int width)
	{
		return "<html><div style='width:" + width + "px'>" + escape(text) + "</div></html>";
	}

	public static String escape(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static String percent(double p)
	{
		if (p >= 0.0995 || p == 0)
		{
			return String.format(Locale.ROOT, "%.0f%%", p * 100).replace("NaN", "?");
		}
		return String.format(Locale.ROOT, "%.1f%%", p * 100);
	}

	public static String percentFine(double p)
	{
		return String.format(Locale.ROOT, "%.1f%%", p * 100);
	}

	public static String oneDecimal(double v)
	{
		return String.format(Locale.ROOT, "%.1f", v);
	}

	public static String duration(long millis)
	{
		final long minutes = Math.round(millis / 60_000.0);
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	public static String time(long timestamp)
	{
		final Calendar now = Calendar.getInstance();
		final Calendar then = Calendar.getInstance();
		then.setTimeInMillis(timestamp);
		final boolean today = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
			&& now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
		return new SimpleDateFormat(today ? "HH:mm" : "MMM d HH:mm", Locale.ENGLISH).format(new Date(timestamp));
	}
}
