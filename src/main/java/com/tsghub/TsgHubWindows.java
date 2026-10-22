package com.tsghub;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;

@RequiredArgsConstructor
final class TsgHubWindows
{
	private final Client client;
	private final Supplier<TsgHubAnnouncements> announcements;
	private volatile JDialog newsDialog;
	private TsgHubNewsWindow newsWindow;

	void openNews(String id)
	{
		SwingUtilities.invokeLater(() -> {
			if (newsDialog == null || !newsDialog.isDisplayable())
			{
				TsgHubAnnouncements news = announcements.get();
				newsWindow = new TsgHubNewsWindow(news.polls());
				newsWindow.setAnnouncements(news.list());
				newsDialog = dialog("TSG Hub · News", newsWindow, new Dimension(TsgHubNewsWindow.TEXT_W + 60, 320), new Dimension(TsgHubNewsWindow.TEXT_W + 60, 640));
			}
			newsWindow.show(id);
			newsDialog.setVisible(true);
			newsDialog.toFront();
		});
	}

	void news(Consumer<TsgHubNewsWindow> action)
	{
		SwingUtilities.invokeLater(() -> { if (newsWindow != null) action.accept(newsWindow); });
	}

	void hideAll()
	{
		SwingUtilities.invokeLater(() -> { if (newsDialog != null) newsDialog.setVisible(false); });
	}

	void dispose()
	{
		JDialog newsWindowDialog = newsDialog;
		newsDialog = null;
		SwingUtilities.invokeLater(() -> {
			if (newsWindowDialog != null) newsWindowDialog.dispose();
			newsWindow = null;
		});
	}

	private JDialog dialog(String title, Container content, Dimension min, Dimension size)
	{
		Window owner = SwingUtilities.getWindowAncestor(client.getCanvas());
		JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		dialog.setContentPane(content);
		dialog.setResizable(true);
		dialog.setMinimumSize(min);
		dialog.setSize(size);
		dialog.setLocationRelativeTo(owner);
		return dialog;
	}
}
