package de.george.g3dit.gui.dialogs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.jdesktop.swingx.decorator.EnabledHighlighter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezware.dialog.task.TaskDialogs;
import com.google.common.base.Predicates;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.matchers.AbstractMatcherEditor;
import de.george.g3dit.EditorContext;
import de.george.g3dit.check.EntityDescriptor;
import de.george.g3dit.check.FileDescriptor;
import de.george.g3dit.gui.table.TableColumnDef;
import de.george.g3dit.gui.table.TableUtil;
import de.george.g3dit.gui.table.TableUtil.SortableEventTable;
import de.george.g3dit.gui.table.renderer.FileTableCellRenderer;
import de.george.g3dit.tab.EditorTab.EditorTabType;
import de.george.g3dit.tab.archive.EditorArchiveTab;
import de.george.g3dit.util.AbstractFileWorker;
import de.george.g3dit.util.FileDialogWrapper;
import de.george.g3dit.util.Icons;
import de.george.g3utils.gui.ListTableModel;
import de.george.g3utils.gui.SwingUtils;
import de.george.g3utils.io.G3FileReader;
import de.george.g3utils.io.G3FileReaderVirtual;
import de.george.g3utils.structure.Guid;
import de.george.g3utils.util.IOUtils;
import de.george.lrentnode.archive.ArchiveFile;
import de.george.lrentnode.archive.eCEntity;
import de.george.lrentnode.classes.eCIlluminated_PS;
import de.george.lrentnode.classes.eCIlluminated_PS.StaticLights;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.enums.G3Enums.eEStaticIlluminated;
import de.george.lrentnode.iterator.ArchiveFileIterator;
import net.miginfocom.swing.MigLayout;
import one.util.streamex.StreamEx;

public class ImportStaticLightdataDialog extends AbstractTableProgressDialog {
	private static final Logger logger = LoggerFactory.getLogger(ImportStaticLightdataDialog.class);

	private JRadioButton rbOpenFiles;
	private JRadioButton rbAllFiles;

	private Action actionApplyAll;
	private Action actionApplySelected;
	private JButton btnSaveProtocol;

	private JCheckBox cbEnableFilter;
	private JCheckBox cbHideFiltered;

	private boolean importMostRecent;

	private EventList<Result> results;
	private SortableEventTable<Result> table;

	private static final TableColumnDef COLUMN_NAME = TableColumnDef.withName("Name").size(225).b();

	private static final TableColumnDef COLUMN_GUID = TableColumnDef.withName("Guid").size(300).b();

	private final TableColumnDef COLUMN_FILE = TableColumnDef.withName("File").size(350)
			.cellRenderer(new FileTableCellRenderer(() -> ctx.getOptionStore())).b();

	private static final TableColumnDef COLUMN_LOG = TableColumnDef.withName("Log").displayName("Description").size(75).b();

	public ImportStaticLightdataDialog(EditorContext ctx) {
		super(ctx, "StaticLightdata importieren");
		setSize(1000, 700);
	}

	private void doImport() {
		File lightFolder = FileDialogWrapper.chooseDirectory("Beleuchtungsdateien auswählen", ctx.getParentWindow());
		if (lightFolder == null) {
			return;
		}

		Map<String, Sldat> lightData = new HashMap<>();
		for (File file : IOUtils.listFiles(lightFolder.getAbsolutePath(), f -> f.getName().endsWith(".sldat"))) {
			try (G3FileReader reader = new G3FileReaderVirtual(file)) {
				Sldat sldat = new Sldat(reader);
				lightData.put(sldat.getEntityGuid().getGuid(), sldat);
			} catch (Exception e) {
				logger.warn("Ungültige sldat: {}", e.getMessage());
			}
		}

		doWork(lightData, false);
	}

	private void doApplyAll() {
		doApply(StreamEx.of(results));
	}

	private void doApplySelected() {
		doApply(table.getSelectedRows());
	}

	private void doApply(StreamEx<Result> resultsToApply) {
		Map<String, Sldat> lightData = resultsToApply.filter(Predicates.not(Result::isFiltered)).map(Result::getSldat)
				.collect(Collectors.toMap(s -> s.getEntityGuid().getGuid(), Function.identity()));
		doWork(lightData, true);
	}

	private void doWork(Map<String, Sldat> lightData, boolean applyChanges) {
		importMostRecent = !applyChanges;

		btnSaveProtocol.setEnabled(false);
		results.clear();

		// Geöffnete Dateien
		List<File> openFiles = new ArrayList<>();
		for (EditorArchiveTab tab : ctx.getEditor().<EditorArchiveTab>getTabs(EditorTabType.Archive)) {
			File file = tab.getDataFile().orElseGet(() -> new File("-"));
			if (processFile(tab.getCurrentFile(), file, lightData, applyChanges, cbEnableFilter.isSelected(), results::add)) {
				tab.fileChanged();
			}
			tab.getDataFile().ifPresent(openFiles::add);

		}

		if (rbAllFiles.isSelected()) {
			worker = new ImportLightDataWorker(ctx.getFileManager().worldFilesCallable(), openFiles, lightData, applyChanges,
					cbEnableFilter.isSelected());
			executeWorker();
		} else {
			btnSaveProtocol.setEnabled(!results.isEmpty());
		}
	}

	@Override
	public JComponent createContentPanel() {
		JButton btnImport = registerAction("Änderungen ermitteln", Icons.getImageIcon(Icons.Action.DIFF), this::doImport, true);

		rbOpenFiles = new JRadioButton("In geöffneten Dateien");
		rbOpenFiles.setFocusable(false);
		rbAllFiles = new JRadioButton("In allen Dateien", true);
		rbAllFiles.setFocusable(false);
		SwingUtils.createButtonGroup(rbOpenFiles, rbAllFiles);

		JButton btnApplyAll = registerAction("Alle übernehmen", Icons.getImageIcon(Icons.Select.TICK), this::doApplyAll, true);
		actionApplyAll = btnApplyAll.getAction();
		actionApplyAll.setEnabled(false);

		JButton btnApplySelected = registerAction("Ausgewählte übernehmen", Icons.getImageIcon(Icons.Select.TICK), this::doApplySelected,
				true);
		actionApplySelected = btnApplySelected.getAction();
		actionApplySelected.setEnabled(false);

		btnSaveProtocol = new JButton("Protokoll speichern", Icons.getImageIcon(Icons.IO.SAVE_AS));
		btnSaveProtocol.setEnabled(false);
		btnSaveProtocol.setFocusable(false);
		btnSaveProtocol.addActionListener(e -> saveProtocol());

		cbEnableFilter = new JCheckBox("Objekte filtern", true);
		cbEnableFilter.setToolTipText(SwingUtils.getMultilineText(
				"Entities ohne Mesh werden herausgefiltert, ihre Beleuchtung wird nicht aktualisiert.",
				"Dabei werden die folgenden PropertySets als Meshes gewertet:",
				"<ul><li>eCVisualMeshStatic_PS</li><li>eCVisualMeshDynamic_PS</li><li>eCVisualAnimation_PS</li><li>eCSpeedTree_PS</li></ul>"));

		cbHideFiltered = new JCheckBox("Gefilterte Objekte ausblenden", true);

		JPanel mainPanel = new JPanel(new MigLayout("fill", "[][][]"));
		mainPanel.add(new JLabel("Suchraum"), "spanx 4");
		mainPanel.add(btnImport, "");
		mainPanel.add(btnApplyAll, "");
		mainPanel.add(btnApplySelected, "");
		mainPanel.add(btnSaveProtocol, "wrap");
		mainPanel.add(rbOpenFiles, "split 2, spanx 4");
		mainPanel.add(rbAllFiles, "gapleft 7");
		mainPanel.add(cbEnableFilter, "split 2, spanx");
		mainPanel.add(cbHideFiltered, "wrap");

		results = new BasicEventList<>();

		FilterList<Result> filteredResults = new FilterList<>(results, new HideFiltered());
		table = TableUtil.createSortableTable(filteredResults, Result.class, COLUMN_NAME, COLUMN_GUID, COLUMN_FILE, COLUMN_LOG);
		appendBarAndTable(mainPanel, table.table);

		setEntryActivationListener(i -> ctx.getEditor().openEntity(table.getRowAt(i).getEntity()));

		table.addModelListener(e -> actionApplyAll.setEnabled(importMostRecent && table.table.getRowCount() >= 1));
		TableUtil.enableOnGreaterEqual(table.table, actionApplySelected, 1, () -> importMostRecent);

		table.table.addHighlighter(new EnabledHighlighter(
				(renderer, adapter) -> table.getRowAt(adapter.convertRowIndexToModel(adapter.row)).isFiltered(), false));

		return mainPanel;
	}

	private class HideFiltered extends AbstractMatcherEditor<Result> {
		private HideFiltered() {
			cbHideFiltered.addItemListener(l -> policyChanged());
			policyChanged();
		}

		public void policyChanged() {
			if (cbHideFiltered.isSelected()) {
				fireChanged(r -> !r.isFiltered());
			} else {
				fireMatchAll();
			}
		}
	}

	private void saveProtocol() {
		File saveFile = FileDialogWrapper.saveFile("Protokoll speichern", "Protokoll.csv", "csv", ctx.getParentWindow(),
				FileDialogWrapper.CSV_FILTER);
		if (saveFile != null) {
			try {
				List<String> output = new ArrayList<>();
				output.add("Name;Guid;Datei;Beschreibung");
				results.forEach(e -> output.add(String.format("%s;%s;%s;%s", e.getName(), e.getGuid(), e.getFile(), e.getLog())));
				IOUtils.writeTextFile(output, saveFile, StandardCharsets.UTF_8);
			} catch (IOException e) {
				logger.warn("Fehler beim Speichern des Import-Protokolls: {}", e);
				TaskDialogs.showException(e);
			}
		}
	}

	private boolean processFile(ArchiveFile archiveFile, File file, Map<String, Sldat> lightData, boolean applyChanges,
			boolean enableFilter, Consumer<Result> publish) {
		boolean hasChanged = false;
		int index = 0;
		for (eCEntity entity : archiveFile.getEntities()) {
			eCIlluminated_PS illuminated = entity.getClass(CD.eCIlluminated_PS.class);
			if (illuminated == null || illuminated.property(CD.eCIlluminated_PS.StaticIlluminated)
					.getEnumValue() != eEStaticIlluminated.eEStaticIlluminated_Static) {
				continue;
			}

			Sldat sldat = lightData.get(entity.getGuid());
			if (sldat != null && !sldat.getLightData().equals(illuminated.lights)) {
				String log = illuminated.lights.getLights().size() + " -> " + sldat.getLightData().getLights().size();
				boolean filtered = enableFilter && !entity.hasClass(CD.eCVisualMeshStatic_PS.class)
						&& !entity.hasClass(CD.eCVisualMeshDynamic_PS.class) && !entity.hasClass(CD.eCVisualAnimation_PS.class)
						&& !entity.hasClass(CD.eCSpeedTree_PS.class);
				if (!filtered && applyChanges) {
					illuminated.lights = sldat.getLightData();
				}
				publish.accept(new Result(new EntityDescriptor(entity, index, new FileDescriptor(file, archiveFile.getArchiveType())), log,
						filtered, sldat));
				hasChanged = true;
			}
			index++;
		}
		return hasChanged;
	}

	private class ImportLightDataWorker extends AbstractFileWorker<Void, Result> {

		private Map<String, Sldat> lightData;
		private boolean applyChanges;
		private boolean enableFilter;

		protected ImportLightDataWorker(Callable<List<File>> fileProvider, List<File> openFiles, Map<String, Sldat> lightData,
				boolean applyChanges, boolean enableFilter) {
			super(fileProvider, openFiles, "Ermittele zu bearbeitende Dateien...", "%d/%d Dateien bearbeitet", null);
			setProgressBar(progressBar);
			doneMessageSupplier = this::getDoneMessage;

			this.lightData = lightData;
			this.applyChanges = applyChanges;
			this.enableFilter = enableFilter;
		}

		@Override
		protected void process(List<Result> resultsToProcess) {
			super.process(resultsToProcess);

			if (!isCancelled()) {
				results.addAll(resultsToProcess);
			}
		}

		@Override
		protected Void doInBackground() throws Exception {
			ArchiveFileIterator iterator = new ArchiveFileIterator(getFiles());
			publish();
			while (iterator.hasNext()) {
				ArchiveFile currentFile = iterator.next();
				boolean hasChanged = processFile(iterator.next(), iterator.nextFile(), lightData, applyChanges, enableFilter,
						(Consumer<Result>) this::publish);

				if (applyChanges && hasChanged) {
					File outFile = iterator.nextFile();
					if (ctx.getFileManager().isInSecondaryDataFolder(outFile)) {
						outFile = ctx.getFileManager().moveFromSecondaryToPrimary(outFile).orElse(null);
					}

					if (outFile != null) {
						outFile.getParentFile().mkdirs();
						currentFile.save(outFile);
					}
				}

				filesDone.incrementAndGet();
				publish();
			}
			return null;
		}

		@Override
		protected void done() {
			super.done();
			btnSaveProtocol.setEnabled(!results.isEmpty());
		}

		private String getDoneMessage() {
			if (applyChanges) {
				return String.format("Änderungen übernommen (%d Entities)", results.size());
			} else {
				return String.format("Änderungen ermittelt (%d Entities)", results.size());
			}
		}
	}

	private static class ResultTableModel extends ListTableModel<Result> {
		public ResultTableModel() {
			super("Name", "Guid", "Datei", "Beschreibung");
		}

		@Override
		public Object getValueAt(Result entry, int col) {
			switch (col) {
				case 0:
					return entry.getName();
				case 1:
					return entry.getGuid();
				case 2:
					return entry.getFile();
				case 3:
					return entry.getLog();
			}
			return null;
		}
	}

	public class Result {
		private EntityDescriptor entity;
		private String log;
		private boolean filtered;
		private Sldat sldat;

		public Result(EntityDescriptor entity, String log, boolean filtered, Sldat sldat) {
			this.entity = entity;
			this.log = log;
			this.filtered = filtered;
			this.sldat = sldat;
		}

		public EntityDescriptor getEntity() {
			return entity;
		}

		public String getName() {
			return entity.getDisplayName();
		}

		public String getGuid() {
			return entity.getGuid();
		}

		public File getFile() {
			return entity.getFile().getPath();
		}

		public String getLog() {
			return log;
		}

		public boolean isFiltered() {
			return filtered;
		}

		public Sldat getSldat() {
			return sldat;
		}
	}

	private class Sldat {
		private String entityName;
		private Guid entityGuid;
		public StaticLights lights;

		public Sldat(String entityName, Guid entityGuid, StaticLights lights) {
			this.entityName = entityName;
			this.entityGuid = entityGuid;
			this.lights = lights;
		}

		public Sldat(G3FileReader reader) throws Exception {
			entityName = reader.read(reader.readShort());
			entityGuid = new Guid(reader.readString(reader.readShort()).replace("{", "").replace("}", ""));
			lights = new StaticLights(reader);
		}

		public String getEntityName() {
			return entityName;
		}

		public void setEntityName(String entityName) {
			this.entityName = entityName;
		}

		public Guid getEntityGuid() {
			return entityGuid;
		}

		public void setEntityGuid(Guid entityGuid) {
			this.entityGuid = entityGuid;
		}

		public StaticLights getLightData() {
			return lights;
		}

		public void setLightData(StaticLights lights) {
			this.lights = lights;
		}

	}
}
