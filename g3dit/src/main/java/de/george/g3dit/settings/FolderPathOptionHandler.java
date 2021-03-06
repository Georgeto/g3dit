package de.george.g3dit.settings;

import java.awt.Window;
import java.io.File;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

import de.george.g3dit.util.FileDialogWrapper;
import de.george.g3utils.gui.SwingUtils;
import de.george.g3utils.util.IOUtils;
import net.miginfocom.swing.MigLayout;

public class FolderPathOptionHandler extends TitledOptionHandler<String> {
	private JTextField tfPath;
	private String chooseFolderDialogTitle;

	public FolderPathOptionHandler(Window parent, String title, String chooseFolderDialogTitle) {
		super(parent, title);
		this.chooseFolderDialogTitle = chooseFolderDialogTitle;
	}

	@Override
	protected void load(String value) {
		tfPath.setText(value);
	}

	@Override
	protected Optional<String> save() {
		String path = tfPath.getText();
		if (path.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(IOUtils.ensureTrailingSlash(path));
		}
	}

	@Override
	protected void addValueComponent(JPanel content) {
		tfPath = SwingUtils.createUndoTF();
		content.add(tfPath, "grow");

		JButton btnPath = new JButton("...");
		btnPath.addActionListener(e -> {
			File file = FileDialogWrapper.chooseDirectory(chooseFolderDialogTitle, getParent());
			if (file != null) {
				tfPath.setText(file.getAbsolutePath());
			}
		});
		content.add(btnPath, "");
	}

	@Override
	protected MigLayout getLayoutManager() {
		return new MigLayout("ins 0, fillx", "[grow]5px push[]");
	}
}
