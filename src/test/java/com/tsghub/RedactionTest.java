package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RedactionTest
{
	private static Map.Entry<Rectangle, String> line(int x, int y, int width, String text)
	{
		return new AbstractMap.SimpleImmutableEntry<>(new Rectangle(x, y, width, 14), text);
	}

	@Test
	public void matchesPrivateLines()
	{
		List<String> bodies = List.of("meet me at ge", "Zezima has logged in.");
		assertTrue(TsgHubRedaction.privateLine("From Zezima:", bodies));
		assertTrue(TsgHubRedaction.privateLine("To Zezima:", bodies));
		assertTrue(TsgHubRedaction.privateLine("[12:01] From Zezima:", bodies));
		assertTrue(TsgHubRedaction.privateLine("meet me at ge", bodies));
		assertTrue(TsgHubRedaction.privateLine("Lynx Titan has logged out.", bodies));
		assertFalse(TsgHubRedaction.privateLine("Woox:", bodies));
		assertFalse(TsgHubRedaction.privateLine("buying gf", bodies));
		assertFalse(TsgHubRedaction.privateLine("me", bodies));
	}

	@Test
	public void coversWholePrivateRows()
	{
		List<Map.Entry<Rectangle, String>> lines = List.of(
			line(0, 0, 60, "Woox:"), line(60, 0, 400, "buying gf"),
			line(0, 14, 80, "From Zezima:"), line(80, 14, 400, "hi"),
			line(0, 28, 60, "Woox:"), line(60, 28, 400, "meet me at ge"),
			line(0, 42, 400, "Your loot is worth 1,000 coins."));
		List<Rectangle> rows = TsgHubRedaction.privateRows(lines, List.of("meet me at ge"));
		assertEquals(List.of(new Rectangle(0, 14, 80, 14), new Rectangle(80, 14, 400, 14), new Rectangle(0, 28, 60, 14), new Rectangle(60, 28, 400, 14)), rows);
	}

	@Test
	public void blacksOutScaledAreas()
	{
		BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, 200, 100);
		g.dispose();
		TsgHubRedaction.blackOut(image, List.of(new Rectangle(10, 10, 20, 5)), 100, 50);
		assertEquals(Color.BLACK.getRGB(), image.getRGB(20, 20));
		assertEquals(Color.BLACK.getRGB(), image.getRGB(60, 29));
		assertEquals(Color.WHITE.getRGB(), image.getRGB(70, 20));
		assertEquals(Color.WHITE.getRGB(), image.getRGB(20, 40));
		assertEquals(Color.WHITE.getRGB(), image.getRGB(5, 5));
	}
}
