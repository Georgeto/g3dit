package de.george.g3dit.util;

import java.awt.Color;
import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.JProgressBar;
import javax.swing.plaf.basic.BasicProgressBarUI;

import de.george.g3dit.gui.dialogs.ProgressDialog;

public abstract class AbstractDialogFileWorker<T> extends AbstractFileWorker<T, Integer> {
	protected ProgressDialog progDlg;
	protected String statusFormat;

	protected AbstractDialogFileWorker(Callable<List<File>> fileProvider, List<File> openFiles, String dialogTitle, Window parent) {
		super(fileProvider, openFiles, "", "%d/%d Dateien verarbeitet", "Verarbeitung abgeschlossen");

		progDlg = new ProgressDialog(parent, dialogTitle, "Ermittele zu bearbeitende Dateien...", true);
		progDlg.setLocationRelativeTo(parent);
		progDlg.setCancelListener(() -> cancel(false));

		JProgressBar progressBar = progDlg.getProgressBar();
		progressBar.setIndeterminate(true);
		progressBar.setUI(new BasicProgressBarUI() {
			@Override
			protected Color getSelectionForeground() {
				return Color.WHITE;
			}

			@Override
			protected Color getSelectionBackground() {
				return Color.BLACK;
			}
		});

		setProgressBar(progressBar);
	}

	public ProgressDialog getProgressDialog() {
		return progDlg;
	}

	@Override
	protected void process(List<Integer> chunks) {
		super.process(chunks);
		progDlg.setStatusMessage(String.format(statusFormat, chunks.get(chunks.size() - 1)));
	}

	public void executeAndShowDialog() {
		execute();
		getProgressDialog().setVisible(true);
	}
}
