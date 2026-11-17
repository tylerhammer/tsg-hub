package com.tsghub;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import net.runelite.client.game.*;
import net.runelite.client.util.*;

class ItemSlot extends JLabel
{
	private final ItemManager itemManager;
	private ImageIcon placeholder;
	private AsyncBufferedImage pending;
	private long shown;

	ItemSlot(int width, int height, ItemManager itemManager)
	{
		this.itemManager = itemManager;
		setOpaque(false);
		setPreferredSize(new Dimension(width, height));
		setMinimumSize(getPreferredSize());
		setHorizontalAlignment(CENTER);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		TsgHubTheme.paintSlot(g, this, TsgHubTheme.SLOT);
		super.paintComponent(g);
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
