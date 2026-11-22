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
	private final String title;
	private final JLabel header = new JLabel();
	private final JLabel summary = new JLabel();
	private final JPanel content = new JPanel();
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
		bar.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
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

	/** Short text on the right of the header, visible even while collapsed. */
	void setSummary(String text)
	{
		summary.setText(text == null ? "" : text);
	}

	void setOpen(boolean open)
	{
		this.open = open;
		header.setText((open ? "- " : "+ ") + title);
		content.setVisible(open);
		revalidate();
		repaint();
	}
}
