package com.tsghub;

import java.awt.Window;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

final class TsgHubMemberPicker extends TsgHubSearchField
{
	private static final int MAX_RESULTS = 20;

	private final List<String> names;

	TsgHubMemberPicker(List<String> names, String value)
	{
		super(new JTextField(20));
		this.names = names;
		field().setText(value);
		addSearch("Type to search the clan");
	}

	@Override
	boolean isExact(String query)
	{
		return names.stream().anyMatch(name -> TsgHubUi.samePlayer(name, query));
	}

	@Override
	List<String> matches(String query)
	{
		return PlayerNames.matchNames(names, query, MAX_RESULTS);
	}

	@Override
	void resultsToggled()
	{
		Window window = SwingUtilities.getWindowAncestor(this);
		if (window != null) window.pack();
	}

	@Override
	void enterWithoutResults()
	{
		JButton button = getRootPane() == null ? null : getRootPane().getDefaultButton();
		if (button != null) button.doClick();
	}
}
