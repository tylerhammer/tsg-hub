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
	private final Supplier<TsgHubAdminWindow> admin;
	private final Supplier<TsgHubAnnouncements> announcements;
	private volatile JDialog adminDialog;
	private volatile JDialog newsDialog;
	private TsgHubNewsWindow newsWindow;

	void openAdmin(Runnable opened)
	{
		SwingUtilities.invokeLater(() -> {
			if (adminDialog == null || !adminDialog.isDisplayable())
			{
				adminDialog = dialog("TSG Hub · Admin", admin.get(), new Dimension(680, 480), new Dimension(860, 640));
			}
			adminDialog.setVisible(true);
			adminDialog.toFront();
			opened.run();
		});
	}

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

	boolean adminOpen()
	{
		JDialog window = adminDialog;
		return window != null && window.isVisible();
	}

	void hideAdmin()
	{
		SwingUtilities.invokeLater(() -> { if (adminDialog != null) adminDialog.setVisible(false); });
	}

	void hideAll()
	{
		SwingUtilities.invokeLater(() -> {
			if (adminDialog != null) adminDialog.setVisible(false);
			if (newsDialog != null) newsDialog.setVisible(false);
		});
	}

	void dispose()
	{
		JDialog adminWindow = adminDialog;
		JDialog newsWindowDialog = newsDialog;
		adminDialog = null;
		newsDialog = null;
		SwingUtilities.invokeLater(() -> {
			if (adminWindow != null) adminWindow.dispose();
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
