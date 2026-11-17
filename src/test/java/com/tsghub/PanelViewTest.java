package com.tsghub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import org.junit.Test;

public class PanelViewTest
{
	private static final String PANEL = "{'title':'Loot split','right':'Running · 1.5M','tone':'success','sections':["
		+ "{'title':'Drops','hint':'right-click to edit','rows':[{'left':'Twisted bow','right':'~1.5M','tone':'muted','notes':['Alice · estimated'],"
		+ "'actions':[{'label':'Skip my share','op':'split.share','args':{'dropId':'d1','displayName':'Alice','share':false},'who':['Alice']},"
		+ "{'label':'Launch','op':'url.open','args':{},'who':[]}]}]},"
		+ "{'rows':[{'left':'All settled','tone':'success'}]}],"
		+ "'buttons':[{'label':'End split','op':'split.toggle','args':{'active':false},'who':['Alice']},{'label':'Wave','op':'split.wave','args':{}}]}";

	private static JsonObject json(String text)
	{
		return new JsonParser().parse(text.replace('\'', '"')).getAsJsonObject();
	}

	private static <T extends Component> List<T> find(Component root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		if (type.isInstance(root)) out.add(type.cast(root));
		if (root instanceof Container) for (Component child : ((Container) root).getComponents()) out.addAll(find(child, type));
		return out;
	}

	private static List<String> texts(Component root)
	{
		List<String> out = new ArrayList<>();
		for (JLabel label : find(root, JLabel.class)) out.add(label.getText());
		return out;
	}

	@Test
	public void rendersRowsAndViewerActions()
	{
		JPanel page = TsgHubUi.stack();
		List<String> ran = new ArrayList<>();
		TsgHubPanelView.render(page, json(PANEL), "alice", null, (op, args) -> ran.add(op + " " + args), ran::add);
		List<String> labels = texts(page);
		assertTrue(labels.containsAll(List.of("Loot split", "Running · 1.5M", "Drops", "right-click to edit", "Twisted bow", "~1.5M", "Alice · estimated", "All settled")));
		List<JButton> buttons = find(page, JButton.class);
		assertEquals(1, buttons.size());
		assertEquals("End split", buttons.get(0).getText());
		buttons.get(0).doClick();
		assertEquals(List.of("split.toggle {\"active\":false}"), ran);
		assertFalse(buttons.get(0).isEnabled());

		JComponent drop = find(page, JPanel.class).stream().filter(p -> p.getComponentPopupMenu() != null).findFirst().orElseThrow();
		List<JMenuItem> items = find(drop.getComponentPopupMenu(), JMenuItem.class);
		assertEquals(1, items.size());
		items.get(0).doClick();
		assertEquals("split.share {\"dropId\":\"d1\",\"displayName\":\"Alice\",\"share\":false}", ran.get(1));
	}

	@Test
	public void hidesOtherViewersActionsAndHints()
	{
		JPanel page = TsgHubUi.stack();
		TsgHubPanelView.render(page, json(PANEL), "Bob", null, (op, args) -> { }, m -> { });
		assertTrue(find(page, AbstractButton.class).isEmpty());
		assertFalse(texts(page).contains("right-click to edit"));
		assertTrue(find(page, JPanel.class).stream().noneMatch(p -> p.getComponentPopupMenu() != null));
	}

	@Test
	public void buttonsOnlyPanelHasNoCard()
	{
		JPanel page = TsgHubUi.stack();
		TsgHubPanelView.render(page, json("{'sections':[],'buttons':[{'label':'Start loot split','op':'split.toggle','args':{'active':true},'who':['Alice']}]}"), "Alice", null, (op, args) -> { }, m -> { });
		assertEquals(2, page.getComponentCount());
		assertEquals("Start loot split", find(page, JButton.class).get(0).getText());
	}

	@Test
	public void knownOpsOnly()
	{
		assertTrue(TsgHubPanelView.visible(json("{'op':'split.paid','who':[]}"), "Alice"));
		assertFalse(TsgHubPanelView.visible(json("{'op':'split.paid','who':['Bob']}"), "Alice"));
		assertFalse(TsgHubPanelView.visible(json("{'op':'url.open'}"), "Alice"));
		assertFalse(TsgHubPanelView.visible(json("{'op':'split.paid','prompt':{'kind':'text'}}"), "Alice"));
		assertEquals("/v1/groups/split/unpay", TsgHubGroups.PANEL_OPS.get("split.unpay"));
	}
}
