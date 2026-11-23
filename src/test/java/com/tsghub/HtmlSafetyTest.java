package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.Test;

public class HtmlSafetyTest
{
	private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

	@Test
	public void plainLabelsIgnoreHtml()
	{
		JLabel label = TsgHubUi.label("<html><b>Zezima</b>", Color.WHITE, FONT);
		assertNull(label.getClientProperty(BasicHTML.propertyKey));
		label.setText("<html><img src='http://example.com/x.png'>");
		assertNull(label.getClientProperty(BasicHTML.propertyKey));
	}

	@Test
	public void badgesIgnoreHtml()
	{
		assertNull(TsgHubUi.badge("<html><b>tag</b>", Color.WHITE).getClientProperty(BasicHTML.propertyKey));
	}

	@Test
	public void htmlLabelsRenderHtml()
	{
		assertNotNull(TsgHubUi.htmlLabel(TsgHubHtml.html(TsgHubHtml.escape("<b>x</b>"), 100), Color.WHITE, FONT).getClientProperty(BasicHTML.propertyKey));
		JLabel label = TsgHubUi.label("", Color.WHITE, FONT);
		TsgHubUi.setHtml(label, "<html>ok</html>");
		assertNotNull(label.getClientProperty(BasicHTML.propertyKey));
	}

	@Test
	public void escapesMarkupAndQuotes()
	{
		assertEquals("&lt;a href=&#39;x&#39; title=&quot;y&quot;&gt;&amp;", TsgHubHtml.escape("<a href='x' title=\"y\">&"));
	}
}
