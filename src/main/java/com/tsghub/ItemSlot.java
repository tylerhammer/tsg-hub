package com.tsghub;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

class ItemSlot extends JLabel
{
	private final ItemManager itemManager;
	private ImageIcon placeholder;
	private AsyncBufferedImage pending;
	private long shown;

	ItemSlot(int width, int height, ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setOpaque(true);
		setBackground(TsgHubTheme.SLOT);
		setPreferredSize(new Dimension(width, height));
		setMinimumSize(getPreferredSize());
		setHorizontalAlignment(CENTER);
	}

	void setPlaceholder(BufferedImage image)
	{
		BufferedImage faded = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = faded.createGraphics();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
		g.drawImage(image, 0, 0, null);
		g.dispose();
		placeholder = new ImageIcon(faded);
		if (pending == null) setIcon(placeholder);
	}

	void setItem(GameItem item, String tooltip)
	{
		setToolTipText(item == null ? null : tooltip);
		if (item == null || itemManager == null)
		{
			pending = null;
			setIcon(placeholder);
			return;
		}
		long key = ((long) item.getId() << 32) | (item.getQty() & 0xffffffffL);
		if (pending != null && shown == key) return;
		shown = key;
		AsyncBufferedImage image = itemManager.getImage(item.getId(), item.getQty(), item.isStackable());
		pending = image;
		setIcon(new ImageIcon(image));
		image.onLoaded(() -> SwingUtilities.invokeLater(() -> {
			if (pending == image) setIcon(new ImageIcon(image));
		}));
	}
}
