package com.slayertaskluck.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** A titled section that opens and closes on click. Only "Luck over time" starts open. */
class CollapsibleSection extends JPanel
{
	private static final int BAR_PADDING = 8;

	private final String title;
	private final JLabel header = new JLabel();
	private final JLabel summary = new JLabel();
	private final JPanel content = new JPanel();
	private String summaryText = "";
	private boolean open;

	CollapsibleSection(String title, boolean open)
	{
		this.title = title;
		this.open = open;
		setLayout(new BorderLayout());
		setBackground(UiStyle.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		final JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(UiStyle.CARD);
		bar.setBorder(BorderFactory.createEmptyBorder(5, BAR_PADDING, 5, BAR_PADDING));
		bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(UiStyle.TEXT);
		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(UiStyle.MUTED);
		bar.add(header, BorderLayout.WEST);
		bar.add(summary, BorderLayout.EAST);
		bar.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setOpen(!CollapsibleSection.this.open);
			}
		});

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(UiStyle.BACKGROUND);
		content.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

		add(bar, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
		setOpen(open);
	}

	JPanel content()
	{
		return content;
	}

	/** Short text on the right of the header, visible even while collapsed. Shortened with "..." if it won't fit. */
	void setSummary(String text)
	{
		summaryText = text == null ? "" : text;
		fitSummary();
	}

	private void fitSummary()
	{
		final int room = UiStyle.CONTENT_WIDTH - BAR_PADDING * 2 - header.getPreferredSize().width - UiStyle.ROW_GAP;
		summary.setText(UiStyle.fit(summaryText, summary.getFontMetrics(summary.getFont()), room));
	}

	void setOpen(boolean open)
	{
		this.open = open;
		header.setText((open ? "- " : "+ ") + title);
		fitSummary();
		content.setVisible(open);
		revalidate();
		repaint();
	}
}
