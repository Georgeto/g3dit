package de.george.g3dit.tab.archive.views.entity;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;

import com.ezware.dialog.task.TaskDialogs;

import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.diff.node.ToMapPrintingVisitor;
import de.george.g3dit.cache.Caches;
import de.george.g3dit.cache.TemplateCache.TemplateCacheEntry;
import de.george.g3dit.entitytree.filter.GuidEntityFilter.MatchMode;
import de.george.g3dit.gui.components.JEntityGuidField;
import de.george.g3dit.gui.components.JFocusNameField;
import de.george.g3dit.gui.components.JSearchNamedGuidField.Layout;
import de.george.g3dit.gui.components.JTemplateGuidField;
import de.george.g3dit.gui.dialogs.DisplayTextDialog;
import de.george.g3dit.gui.dialogs.EntitySearchDialog;
import de.george.g3dit.gui.dialogs.TemplateNameSearchDialog;
import de.george.g3dit.gui.validation.ChangeTimeValidator;
import de.george.g3dit.gui.validation.TemplateExistenceValidator;
import de.george.g3dit.tab.archive.EditorArchiveTab;
import de.george.g3dit.tab.shared.BoundingBoxPanel;
import de.george.g3dit.tab.shared.PositionPanel;
import de.george.g3dit.util.Icons;
import de.george.g3utils.gui.SwingUtils;
import de.george.g3utils.gui.UndoableTextField;
import de.george.g3utils.structure.GuidUtil;
import de.george.g3utils.structure.bCBox;
import de.george.g3utils.structure.bCMatrix;
import de.george.g3utils.util.IOUtils;
import de.george.g3utils.validation.EmtpyWarnValidator;
import de.george.g3utils.validation.GuidValidator;
import de.george.g3utils.validation.IsALongValidator;
import de.george.lrentnode.archive.eCEntity;
import de.george.lrentnode.archive.lrentdat.LrentdatEntity;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.diff.EntityDiffer;
import de.george.lrentnode.template.TemplateEntity;
import de.george.lrentnode.template.TemplateFile;
import de.george.lrentnode.util.EntityUtil;
import de.george.lrentnode.util.FileUtil;
import net.miginfocom.swing.MigLayout;

public class AllgemeinTab extends AbstractEntityTab {
	private JFocusNameField tfName;
	private UndoableTextField tfChangeTime;

	private JEntityGuidField tfGuid;
	private JTemplateGuidField tfRefGuid;
	private JLabel lblRefGuid, lblChangeTime;
	private PositionPanel plWorldPosition;
	private JButton btnLoadFromTemplate;
	private JButton btnDiffTemplate;

	private PositionPanel plLocalPosition;
	private BoundingBoxPanel plLocalNodeBoundary;

	private boolean enclave, npc, anchor, interact, party;

	public AllgemeinTab(EditorArchiveTab ctx) {
		super(ctx);
	}

	@Override
	public void initComponents() {
		setLayout(new MigLayout("fillx", "[][][]push[grow]"));

		JLabel lblName = new JLabel("Name");
		add(lblName, "wrap");

		tfName = new JFocusNameField(ctx);
		tfName.initValidation(validation(), "Name", EmtpyWarnValidator.INSTANCE);
		add(tfName, "width 100:300:300, wrap");

		JLabel lblGuid = new JLabel("Guid");
		add(lblGuid, "wrap");

		tfGuid = new JEntityGuidField(ctx);
		tfGuid.setFieldLayout(Layout.VerticalNoName);
		tfGuid.initValidation(validation(), "Guid", GuidValidator.INSTANCE);
		add(tfGuid, "width 100:300:300");

		tfGuid.addMenuItem("Alle Nutzer dieses Freepoints auflisten", Icons.getImageIcon(Icons.Misc.GLOBE),
				(ctx, g) -> EntitySearchDialog.openEntitySearchGuid(ctx, MatchMode.Routine, g), (ctx, g) -> interact);

		tfGuid.addMenuItem("Alle PartyMember dieses NPCs auflisten", Icons.getImageIcon(Icons.Misc.GLOBE),
				(ctx, g) -> EntitySearchDialog.openEntitySearchGuid(ctx, MatchMode.PartyLeader, g), (ctx, g) -> npc && party);

		tfGuid.addMenuItem("Alle InteractionPoints dieses Anchors auflisten", Icons.getImageIcon(Icons.Misc.GLOBE),
				(ctx, g) -> EntitySearchDialog.openEntitySearchGuid(ctx, MatchMode.AnchorPoint, g), (ctx, g) -> anchor);

		tfGuid.addMenuItem("Alle Mitglieder dieser Enclave auflisten", Icons.getImageIcon(Icons.Misc.GLOBE),
				(ctx, g) -> EntitySearchDialog.openEntitySearchGuid(ctx, MatchMode.Enclave, g), (ctx, g) -> enclave);

		JButton btnRandomGuid = new JButton(Icons.getImageIcon(Icons.Data.COUNTER));
		btnRandomGuid.setToolTipText("Zufällige Guid generieren");
		btnRandomGuid.addActionListener(a -> tfGuid.setText(GuidUtil.randomGUID(), true));
		add(btnRandomGuid, "spanx 3, split 3, width 27!, height 27!");

		JButton btnCopyGuid = new JButton(Icons.getImageIcon(Icons.Action.COPY));
		btnCopyGuid.setToolTipText("Guid in Zwischenablage kopieren");
		btnCopyGuid.addActionListener(a -> IOUtils.copyToClipboard(tfGuid.getText()));
		add(btnCopyGuid, "width 27!, height 27!, wrap");

		plWorldPosition = new PositionPanel("World-Position", ctx.getParentWindow(), positionMatrix -> changeWorldPosition(positionMatrix),
				positionMatrix -> changeWorldPositionKeepChilds(positionMatrix));
		add(plWorldPosition, "width 100:300:400, spanx 4, grow, wrap");

		plLocalPosition = new PositionPanel("Local-Position", ctx.getParentWindow(),
				positionMatrix -> changeLocalPosition(positionMatrix));
		add(plLocalPosition, "width 100:300:400, spanx 4, grow, wrap");

		plLocalNodeBoundary = new BoundingBoxPanel("BoundingBox", ctx, box -> changeLocalNodeBoundary(box), () -> {
			ctx.saveView(); // Mesh wurde eventuell im Mesh Tab bearbeitet
			return EntityUtil.getMesh(ctx.getCurrentEntity()).orElse(null);
		}, () -> tfName.getText());
		add(plLocalNodeBoundary, "width 100:300:400, spanx 4, grow, wrap");

		lblRefGuid = new JLabel("Reference Guid");
		add(lblRefGuid, "wrap");

		tfRefGuid = new JTemplateGuidField(ctx);
		tfRefGuid.initValidation(validation(), "Reference Guid", GuidValidator.INSTANCE_ALLOW_EMPTY,
				new TemplateExistenceValidator(validation(), ctx));
		add(tfRefGuid, "width 100:300:300");

		btnLoadFromTemplate = new JButton(Icons.getImageIcon(Icons.IO.UPLOAD));
		btnLoadFromTemplate.setToolTipText("Reference Guid und ChangeTime aus Template laden");
		add(btnLoadFromTemplate, "width 27!, height 27!");
		btnLoadFromTemplate.addActionListener(e -> handleLoadFromTemplate());

		btnDiffTemplate = new JButton(Icons.getImageIcon(Icons.Action.DIFF));
		btnDiffTemplate.setToolTipText("Mit Template vergleichen");
		add(btnDiffTemplate, "width 27!, height 27!, wrap");
		btnDiffTemplate.addActionListener(e -> handleDiffTemplate());

		lblChangeTime = new JLabel("ChangeTime");
		add(lblChangeTime, "wrap");

		tfChangeTime = SwingUtils.createUndoTF();
		tfChangeTime.setName("ChangeTime");
		addValidators(tfChangeTime, IsALongValidator.INSTANCE, new ChangeTimeValidator(validation(), ctx, tfRefGuid));
		add(tfChangeTime, "width 50:100:100, wrap");

		tfRefGuid.addGuidFiedListener(g -> evalDiffTemplate());
		Caches.template(ctx).addUpdateListener(this, c -> evalDiffTemplate());
	}

	@Override
	public String getTabTitle() {
		return "Allgemein";
	}

	@Override
	public boolean isActive(eCEntity entity) {
		return true;
	}

	/**
	 * Nach dem Umschalten auf eine Entity aufrufen, um deren Werte ins GUI zu laden
	 */
	@Override
	public void loadValues(eCEntity entity) {
		enclave = entity.hasClass(CD.gCEnclave_PS.class);
		npc = entity.hasClass(CD.gCNPC_PS.class);
		anchor = entity.hasClass(CD.gCAnchor_PS.class);
		interact = entity.hasClass(CD.gCInteraction_PS.class)
				&& (entity.hasClass(CD.gCNavOffset_PS.class) || entity.hasClass(CD.gCAIHelper_FreePoint_PS.class));
		party = entity.hasClass(CD.gCParty_PS.class);

		// Allgemein Tab
		tfName.setText(entity.getName());
		tfGuid.setText(entity.getGuid());

		plWorldPosition.setPositionMatrix(entity.getWorldMatrix());
		plLocalPosition.setPositionMatrix(entity.getLocalMatrix());

		plLocalNodeBoundary.setBoundingBox(entity.getLocalNodeBoundary());

		// Reference Guid und ChangeTime machen nur in Lrendats Sinn
		if (entity instanceof LrentdatEntity) {
			tfRefGuid.setText(entity.getCreator());
			tfChangeTime.setText(String.valueOf(entity.getDataChangedTimeStamp()));
			lblRefGuid.setVisible(true);
			tfRefGuid.setVisible(true);
			lblChangeTime.setVisible(true);
			tfChangeTime.setVisible(true);
			btnLoadFromTemplate.setVisible(true);
			btnDiffTemplate.setVisible(true);
		} else {
			// Dummy-Werte, damit die Fehleranzeige nicht Fehler in einem unsichtbaren Feld anzeigt
			tfRefGuid.setText(null);
			tfChangeTime.setText("0");
			lblRefGuid.setVisible(false);
			tfRefGuid.setVisible(false);
			lblChangeTime.setVisible(false);
			tfChangeTime.setVisible(false);
			btnLoadFromTemplate.setVisible(false);
			btnDiffTemplate.setVisible(false);
		}
	}

	/**
	 * Vor dem Umschalten auf eine andere Entity aufrufen, um Änderungen zu speichern
	 */
	@Override
	public void saveValues(eCEntity entity) {
		entity.setName(tfName.getText());
		entity.setGuid(GuidUtil.parseGuid(tfGuid.getText()));

		// Reference Guid und ChangeTime machen nur in Lrendats Sinn
		if (entity instanceof LrentdatEntity) {
			entity.setCreator(GuidUtil.parseGuid(tfRefGuid.getText()));
			entity.setDataChangedTimeStamp(Long.valueOf(tfChangeTime.getText()));

		}
	}

	private void changeWorldPosition(bCMatrix worldMatrix) {
		ctx.modifyEntityMatrix(ctx.getCurrentEntity(), e -> e.setToWorldMatrix(worldMatrix));
	}

	private void changeWorldPositionKeepChilds(bCMatrix worldMatrix) {
		eCEntity entity = ctx.getCurrentEntity();
		Map<eCEntity, bCMatrix> childPositions = entity.getChilds().stream().collect(Collectors.toMap(c -> c, c -> c.getWorldMatrix()));
		ctx.modifyEntityMatrix(entity, e -> e.setToWorldMatrix(worldMatrix));
		childPositions.forEach((e, p) -> e.setToWorldMatrix(p));
	}

	private void changeLocalPosition(bCMatrix localMatrix) {
		ctx.modifyEntityMatrix(ctx.getCurrentEntity(), e -> e.setLocalMatrix(localMatrix));

	}

	private void changeLocalNodeBoundary(bCBox box) {
		ctx.getCurrentEntity().updateLocalNodeBoundary(box);
		ctx.fileChanged();
		ctx.refreshView();
	}

	private void handleLoadFromTemplate() {
		new TemplateNameSearchDialog(tpleFile -> {
			if (tpleFile.getHeaderCount() == 2) {
				TemplateEntity refHeader = tpleFile.getReferenceHeader();
				tfRefGuid.setText(refHeader.getGuid(), true);
				tfChangeTime.setText(Long.toString(refHeader.getDataChangedTimeStamp()), true);
				return true;
			} else {
				return false;
			}
		}, ctx, tfName.getText()).open();
	}

	private void evalDiffTemplate() {
		btnDiffTemplate.setEnabled(Caches.template(ctx).getEntryByGuid(tfRefGuid.getText()).isPresent());
	}

	private void handleDiffTemplate() {
		Optional<TemplateCacheEntry> entry = Caches.template(ctx).getEntryByGuid(tfRefGuid.getText());
		if (entry.isPresent()) {
			try {
				TemplateFile tple = FileUtil.openTemplate(entry.get().getFile());
				DiffNode diff = new EntityDiffer(true).diff(ctx.getCurrentEntity(), tple.getReferenceHeader());
				ToMapPrintingVisitor mapPrintingVisitor = new ToMapPrintingVisitor(ctx.getCurrentEntity(), tple.getReferenceHeader());
				diff.visit(mapPrintingVisitor);
				DisplayTextDialog dialog = new DisplayTextDialog("Vergleich: Template - Entity", mapPrintingVisitor.getMessagesAsString(),
						ctx.getParentWindow(), false);
				dialog.setVisible(true);
			} catch (IOException e) {
				TaskDialogs.showException(e);
			}
		}
	}

	@Override
	public void registerKeyStrokes(JComponent container) {
		container.registerKeyboardAction(a -> tfGuid.setText(GuidUtil.randomGUID(), true),
				KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
	}

	@Override
	public void unregisterKeyStrokes(JComponent container) {
		container.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK));
	}
}
