package com.slayertaskluck.ui;

import com.slayertaskluck.math.Verdict;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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
	/** Main text: near white, about 12:1 against the card. */
	public static final Color TEXT = new Color(0xDC, 0xDC, 0xDC);
	/** Secondary text: about 6:1 against the card, still easy to read. */
	public static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	public static final Color LINK = ColorScheme.BRAND_ORANGE;

	/** Usable width inside the side panel's padding (and inside a section). */
	public static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 20;
	/** Usable width inside the headline card's padding. */
	public static final int CARD_WIDTH = CONTENT_WIDTH - 16;
	/** Gap between the text and the value on its right in a row. */
	public static final int ROW_GAP = 6;
	/** Measured text can render a pixel or two wider than its metrics say. */
	private static final int SLACK = 2;

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

	/** A label whose text is broken into lines that fit {@code width} pixels. */
	public static JLabel text(String text, Font font, Color color, int width)
	{
		final JLabel l = label("", font, color);
		setWrapped(l, text, width);
		return l;
	}

	/**
	 * Sets text broken into lines that fit {@code width} pixels in the label's font. The breaks are measured here
	 * rather than left to a CSS width, which Swing's HTML renderer scales by 1.3 and then clips.
	 */
	public static void setWrapped(JLabel label, String text, int width)
	{
		final List<String> lines = lines(text, label.getFontMetrics(label.getFont()), width - SLACK);
		if (lines.size() <= 1)
		{
			label.setText(lines.isEmpty() ? "" : lines.get(0));
			return;
		}
		final StringBuilder html = new StringBuilder("<html>");
		for (int i = 0; i < lines.size(); i++)
		{
			html.append(i > 0 ? "<br>" : "").append(escape(lines.get(i)));
		}
		label.setText(html.append("</html>").toString());
	}

	/** Greedy word wrap. A single word wider than the line is cut to fit with "...". */
	static List<String> lines(String text, FontMetrics fm, int width)
	{
		final List<String> out = new ArrayList<>();
		String line = "";
		for (String word : (text == null ? "" : text).split(" "))
		{
			if (word.isEmpty())
			{
				continue;
			}
			final String joined = line.isEmpty() ? word : line + " " + word;
			if (fm.stringWidth(joined) <= width)
			{
				line = joined;
			}
			else if (line.isEmpty())
			{
				out.add(fit(word, fm, width));
			}
			else
			{
				out.add(line);
				line = fm.stringWidth(word) <= width ? word : "";
				if (line.isEmpty())
				{
					out.add(fit(word, fm, width));
				}
			}
		}
		if (!line.isEmpty())
		{
			out.add(line);
		}
		return out;
	}

	/** Shortens text with "..." until it fits {@code width} pixels. */
	public static String fit(String text, FontMetrics fm, int width)
	{
		if (text == null || fm.stringWidth(text) <= width)
		{
			return text;
		}
		String s = text;
		while (!s.isEmpty() && fm.stringWidth(s + "...") > width)
		{
			s = s.substring(0, s.length() - 1);
		}
		return s.trim() + "...";
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
