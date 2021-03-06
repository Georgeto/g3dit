package de.george.g3dit.tab.shared;

import java.awt.Container;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.netbeans.validation.api.ui.ValidationGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezware.dialog.task.TaskDialogs;

import de.george.g3dit.EditorContext;
import de.george.g3dit.gui.components.JEnumComboBox;
import de.george.g3dit.tab.shared.AbstractElementsPanel.InsertPosition;
import de.george.g3dit.util.FileDialogWrapper;
import de.george.g3dit.util.FileManager;
import de.george.g3dit.util.Icons;
import de.george.g3dit.util.PropertySync;
import de.george.g3utils.gui.SwingUtils;
import de.george.g3utils.gui.UndoableTextField;
import de.george.g3utils.structure.bCEulerAngles;
import de.george.g3utils.structure.bCMatrix3;
import de.george.g3utils.structure.bCOrientedBox;
import de.george.g3utils.structure.bCSphere;
import de.george.g3utils.structure.bCVector;
import de.george.g3utils.validation.ValidationGroupWrapper;
import de.george.lrentnode.archive.G3ClassContainer;
import de.george.lrentnode.classes.eCCollisionShape;
import de.george.lrentnode.classes.eCCollisionShape.BoxShape;
import de.george.lrentnode.classes.eCCollisionShape.CapsuleShape;
import de.george.lrentnode.classes.eCCollisionShape.FileShape;
import de.george.lrentnode.classes.eCCollisionShape.PointShape;
import de.george.lrentnode.classes.eCCollisionShape.Shape;
import de.george.lrentnode.classes.eCCollisionShape.SphereShape;
import de.george.lrentnode.classes.eCCollisionShape_PS;
import de.george.lrentnode.classes.eCResourceCollisionMesh_PS;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.enums.G3Enums.eECollisionShapeType;
import de.george.lrentnode.enums.G3Enums.eEShapeGroup;
import de.george.lrentnode.enums.G3Enums.eEShapeMaterial;
import de.george.lrentnode.util.FileUtil;
import net.miginfocom.swing.MigLayout;

public class SharedCollisionShapeTab extends AbstractSharedTab {
	private Logger logger = LoggerFactory.getLogger(SharedCollisionShapeTab.class);

	private CollisionShapesPanel stacksPanel;

	public SharedCollisionShapeTab(EditorContext ctx, Container container) {
		super(ctx, container);
	}

	@Override
	public void initComponents(ValidationGroup validation, JScrollPane scrollPane) {
		container.setLayout(new MigLayout("fillx"));

		JLabel lblInventoryStacks = SwingUtils.createBoldLabel("CollisionShapes");
		container.add(lblInventoryStacks, "gaptop 7, wrap");

		JButton btnLoadFromXnvmsh = new JButton("Aus .xnvmsh laden", Icons.getImageIcon(Icons.IO.IMPORT));
		btnLoadFromXnvmsh.addActionListener(a -> loadFromXnvmsh());
		container.add(btnLoadFromXnvmsh, "gapleft 7, wrap");

		stacksPanel = new CollisionShapesPanel(scrollPane);
		stacksPanel.initValidation(validation);
		container.add(stacksPanel, "grow, wrap");
	}

	@Override
	public String getTabTitle() {
		return "CollisionShape";
	}

	@Override
	public boolean isActive(G3ClassContainer entity) {
		return entity.hasClass(CD.eCCollisionShape_PS.class);
	}

	@Override
	public void loadValues(G3ClassContainer entity) {
		stacksPanel.loadValues(entity.getClass(CD.eCCollisionShape_PS.class));
	}

	@Override
	public void saveValues(G3ClassContainer entity) {

		stacksPanel.saveValues(entity.getClass(CD.eCCollisionShape_PS.class));
	}

	private void loadFromXnvmsh() {
		File fileColMesh = FileDialogWrapper.openFile("Aus .xnvmsh laden", ctx.getParentWindow(), FileDialogWrapper.COLLISION_MESH_FILTER);
		if (fileColMesh == null) {
			return;
		}

		try {
			eCResourceCollisionMesh_PS colMesh = FileUtil.openCollisionMesh(new FileInputStream(fileColMesh));

			stacksPanel.clear();
			for (int i = 0; i < colMesh.getNxsBoundaries().size(); i++) {
				FileShape fileShape = new FileShape(fileColMesh.getName(), i);
				fileShape.setMeshBoundary(colMesh.getNxsBoundaries().get(i));

				eCCollisionShape colShape = new eCCollisionShape(false);
				colShape.property(CD.eCCollisionShape.ShapeType)
						.setEnumValue(colMesh.isConvexResource() ? eECollisionShapeType.eECollisionShapeType_ConvexHull
								: eECollisionShapeType.eECollisionShapeType_TriMesh);
				colShape.setShape(fileShape);

				CollisionShapePanel colShapePanel = new CollisionShapePanel(colShape);
				stacksPanel.insertElementRelative(colShapePanel, null, InsertPosition.After);
			}
		} catch (IOException e) {
			logger.warn("Fehler beim Lesen des CollisionMeshes '{}': {}", fileColMesh.getName(), e);
			TaskDialogs.showException(e);
		}
	}

	private class CollisionShapesPanel extends AbstractElementsPanel<eCCollisionShape_PS> {
		public CollisionShapesPanel(JScrollPane navScroll) {
			super("CollisionShape", navScroll, true);

			setLayout(new MigLayout("fillx, insets 0 5 0 0", "[]"));
		}

		/**
		 * Nach dem Umschalten auf eine Entity aufrufen, um deren Werte ins GUI zu laden
		 */
		@Override
		public void loadValuesInternal(eCCollisionShape_PS shapes) {
			for (eCCollisionShape shape : shapes.getShapes()) {
				CollisionShapePanel stackPanel = new CollisionShapePanel(shape);
				insertElementRelative(stackPanel, null, InsertPosition.After);
			}
		}

		/**
		 * Vor dem Umschalten auf eine andere Entity aufrufen, um Änderungen zu speichern
		 */
		@Override
		public void saveValuesInternal(eCCollisionShape_PS shapes) {
			shapes.getShapes().clear();
			for (int i = 0; i < getComponentCount(); i++) {
				CollisionShapePanel shapePanel = (CollisionShapePanel) getComponent(i);
				shapes.addShape(shapePanel.saveShape());
			}
		}

		@Override
		protected void removeValuesInternal(eCCollisionShape_PS shapes) {
			shapes.getShapes().clear();
		}

		@Override
		protected AbstractElementPanel getNewElement() {
			return new CollisionShapePanel(new eCCollisionShape(false));
		}
	}

	private class CollisionShapePanel extends AbstractElementPanel {
		private JEnumComboBox<eECollisionShapeType> cbShapeType;
		private JEnumComboBox<eEShapeMaterial> cbMaterial;
		private JEnumComboBox<eEShapeGroup> cbGroup;
		private JButton btnTple;
		private String title;

		private eCCollisionShape colShape;
		@SuppressWarnings("rawtypes")
		private ShapePanel shapePanel;
		private int lastShapeType;
		private boolean shapeTypeChanged = false;

		public CollisionShapePanel(eCCollisionShape colShape) {
			super("CollisionShape", stacksPanel);
			this.colShape = colShape;
			setLayout(new MigLayout("fillx", "[]10[]10[]"));

			add(new JLabel("ShapeType"));
			add(new JLabel("Material"));
			add(new JLabel("Group"), "wrap");

			cbShapeType = new JEnumComboBox<>(eECollisionShapeType.class);
			add(cbShapeType, "width 100:125:150");
			cbMaterial = new JEnumComboBox<>(eEShapeMaterial.class);
			add(cbMaterial, "width 100:125:150");
			cbGroup = new JEnumComboBox<>(eEShapeGroup.class);
			add(cbGroup, "width 100:125:150, wrap");

			PropertySync sync = PropertySync.wrap(colShape);
			sync.readEnum(cbShapeType, CD.eCCollisionShape.ShapeType);
			sync.readEnum(cbMaterial, CD.eCCollisionShape.Material);
			sync.readEnum(cbGroup, CD.eCCollisionShape.Group);

			JPanel operationPanel = getOperationPanel();
			add(operationPanel, "cell 3 0, spanx 2, spany");

			btnTple = new JButton(Icons.getImageIcon(Icons.Action.BOOK));
			btnTple.setToolTipText("Template laden");
			operationPanel.add(btnTple, "cell 1 1, width 27!, height 27!");

			loadShape();
			lastShapeType = colShape.property(CD.eCCollisionShape.ShapeType).getEnumValue();
			cbShapeType.addItemListener(e -> shapeTypeChanged());
		}

		@Override
		public void initValidation(ValidationGroup group) {

		}

		@Override
		public void removeValidation(ValidationGroupWrapper group) {

		}

		@Override
		protected String getBorderTitle() {
			return title;
		}

		private void shapeTypeChanged() {
			int shapeType = cbShapeType.getSelectedValue();
			Shape shape = eCCollisionShape.getDefaultShape(shapeType);

			if (shape == null) {
				cbShapeType.setSelectedValue(lastShapeType);
				return;
			}

			lastShapeType = shapeType;
			shapeTypeChanged = true;
			if (shape.getClass() != colShape.getShape().getClass()) {
				colShape.setShape(shape);
				loadShape();
			}
		}

		@SuppressWarnings("unchecked")
		private void loadShape() {
			if (shapePanel != null) {
				this.remove(shapePanel);
			}

			Shape shape = colShape.getShape();
			switch (cbShapeType.getSelectedValue()) {
				case eECollisionShapeType.eECollisionShapeType_TriMesh:
				case eECollisionShapeType.eECollisionShapeType_ConvexHull:
					shapePanel = new FileShapePanel();
					break;
				case eECollisionShapeType.eECollisionShapeType_Box:
					shapePanel = new BoxShapePanel();
					break;
				case eECollisionShapeType.eECollisionShapeType_Capsule:
					shapePanel = new CapsuleShapePanel();
					break;
				case eECollisionShapeType.eECollisionShapeType_Sphere:
					shapePanel = new SphereShapePanel();
					break;
				case eECollisionShapeType.eECollisionShapeType_Point:
					shapePanel = new PointShapePanel();
					break;
				default:
					// reader.info(logger, "Unknown ShapeType {}", shapeType);
					// shape = new UnknownShape(reader, deadcodePosition - reader.getPos() - 38);
					break;
			}

			if (shapePanel != null) {
				shapePanel.load(shape);
				this.add(shapePanel, "cell 0 2, gaptop 7, spanx 3, grow, wrap");
			}

		}

		public eCCollisionShape saveShape() {
			PropertySync sync = PropertySync.wrap(colShape);
			sync.writeEnum(cbShapeType, CD.eCCollisionShape.ShapeType);
			sync.writeEnum(cbMaterial, CD.eCCollisionShape.Material);
			int oldGroup = colShape.property(CD.eCCollisionShape.Group).getEnumValue();
			sync.writeEnum(cbGroup, CD.eCCollisionShape.Group);
			int newGroup = colShape.property(CD.eCCollisionShape.Group).getEnumValue();

			if (oldGroup != newGroup) {
				switch (newGroup) {
					case eEShapeGroup.eEShapeGroup_WeaponTrigger:
					case eEShapeGroup.eEShapeGroup_Camera_Obstacle:
						colShape.property(CD.eCCollisionShape.DisableResponse).setBool(true);
						break;
					default:
						colShape.property(CD.eCCollisionShape.DisableResponse).setBool(false);
						break;
				}
			}

			if (shapePanel != null && (shapeTypeChanged || shapePanel.hasChanged())) {
				colShape.setShape(shapePanel.save());
			}

			return colShape;
		}
	}

	private abstract class ShapePanel<T extends Shape> extends JPanel {
		public abstract boolean hasChanged();

		public abstract void load(T shape);

		public abstract T save();
	}

	private class FileShapePanel extends ShapePanel<FileShape> {
		private UndoableTextField tfShapeFile;
		private UndoableTextField tfResourceIndex;

		public FileShapePanel() {
			setLayout(new MigLayout("fillx, insets 0"));

			add(new JLabel("ShapeFile"), "");
			add(new JLabel("ResourceIndex"), "wrap");
			tfShapeFile = SwingUtils.createUndoTF();
			add(tfShapeFile, "growx, width 75%");
			tfResourceIndex = SwingUtils.createUndoTF();
			tfResourceIndex.setToolTipText(
					"<html>Für CollisionMeshes die aus einem NXS-Mesh bestehen 0.<p>Für CollisionMeshes die aus zwei oder mehr NXS-Meshes bestehen,<br>müssen ebenso so viele CollisionShapes existieren,<br>mit einem aufsteigenden ResourceIndex beginnend bei 0.</html>");
			add(tfResourceIndex, "growx, width 25%, wrap");
		}

		@Override
		public void load(FileShape shape) {
			tfShapeFile.setText(shape.getShapeFile());
			tfResourceIndex.setText(Integer.toString(shape.getResourceIndex()));
		}

		@Override
		public FileShape save() {
			FileShape fileShape = new FileShape(tfShapeFile.getText(), Integer.valueOf(tfResourceIndex.getText()));

			boolean update = TaskDialogs.ask(ctx.getParentWindow(), "Boundary des CollisionShapes aktualisieren?",
					"Soll die Boundary dieses CollisionShapes aus dem angegeben CollisionMesh übernommen werden?");
			if (update) {
				Optional<File> colFile = ctx.getFileManager().searchFile(FileManager.RP_COMPILED_PHYSIC, fileShape.getShapeFile());
				if (colFile.isPresent()) {
					try {
						eCResourceCollisionMesh_PS colMesh = FileUtil.openCollisionMesh(new FileInputStream(colFile.get()));

						if (fileShape.getResourceIndex() < colMesh.getNxsBoundaries().size()) {
							fileShape.setMeshBoundary(colMesh.getNxsBoundaries().get(fileShape.getResourceIndex()));
						} else {
							TaskDialogs.inform(ctx.getParentWindow(), "Ungültiger ResourceIndex",
									"Der angegebene ResourceIndex " + fileShape.getResourceIndex()
											+ " passt nicht zum CollisionMesh, welches " + colMesh.getNxsBoundaries().size()
											+ " NXS-Meshes enthält.\nBoundary wird auf ungültig gesetzt.");
						}

					} catch (IOException e) {
						logger.warn("Fehler beim Lesen des CollisionMeshes '{}': {}", fileShape.getShapeFile(), e);
						TaskDialogs.showException(e);
					}
				} else {
					TaskDialogs.inform(ctx.getParentWindow(), "CollisionMesh nicht gefunden", "Das CollisionMesh '"
							+ fileShape.getShapeFile() + "' konnte nicht gefunden werden.\nBoundary wird auf ungültig gesetzt.");
				}
			}

			return fileShape;
		}

		@Override
		public boolean hasChanged() {
			return tfShapeFile.hasChanged() || tfResourceIndex.hasChanged();
		}
	}

	private class BoxShapePanel extends ShapePanel<BoxShape> {
		private UndoableTextField tfCenter, tfExtent, tfPitch, tfYaw, tfRoll;

		public BoxShapePanel() {
			setLayout(new MigLayout("fillx, insets 0", "[fill, grow][fill, grow][fill, grow]"));

			tfCenter = SwingUtils.createUndoTF();
			add(new JLabel("Center"), "wrap");
			add(tfCenter, "spanx 3, wrap");
			tfExtent = SwingUtils.createUndoTF();
			add(new JLabel("Extent"), "wrap");
			add(tfExtent, "spanx 3, wrap");
			tfPitch = SwingUtils.createUndoTF();
			tfYaw = SwingUtils.createUndoTF();
			tfRoll = SwingUtils.createUndoTF();
			add(new JLabel("Pitch"), "");
			add(new JLabel("Yaw"), "");
			add(new JLabel("Roll"), "wrap");
			add(tfPitch, "");
			add(tfYaw, "");
			add(tfRoll, "wrap");
		}

		@Override
		public void load(BoxShape shape) {
			tfCenter.setText(shape.getOrientedBox().getCenter().toString());
			tfExtent.setText(shape.getOrientedBox().getExtent().toString());
			bCEulerAngles rotation = new bCEulerAngles(shape.getOrientedBox().getOrientation());
			tfYaw.setText(Float.toString(rotation.getYawDeg()));
			tfPitch.setText(Float.toString(rotation.getPitchDeg()));
			tfRoll.setText(Float.toString(rotation.getRollDeg()));
		}

		@Override
		public BoxShape save() {
			bCVector center = bCVector.fromString(tfCenter.getText());
			bCVector extend = bCVector.fromString(tfExtent.getText());
			bCMatrix3 orientation = new bCMatrix3(bCEulerAngles.fromDegree(Float.parseFloat(tfYaw.getText()),
					Float.parseFloat(tfPitch.getText()), Float.parseFloat(tfRoll.getText())));
			return new BoxShape(new bCOrientedBox(center, extend, orientation));
		}

		@Override
		public boolean hasChanged() {
			return tfCenter.hasChanged() || tfExtent.hasChanged() || tfPitch.hasChanged() || tfYaw.hasChanged() || tfRoll.hasChanged();
		}
	}

	private class CapsuleShapePanel extends ShapePanel<CapsuleShape> {
		private UndoableTextField tfCenter, tfRadius, tfHeight, tfPitch, tfYaw, tfRoll;

		public CapsuleShapePanel() {
			setLayout(new MigLayout("fillx, insets 0", "[fill, grow][fill, grow][fill, grow]"));

			tfCenter = SwingUtils.createUndoTF();
			add(new JLabel("Center"), "wrap");
			add(tfCenter, "spanx 3, wrap");
			tfRadius = SwingUtils.createUndoTF();
			tfHeight = SwingUtils.createUndoTF();
			add(new JLabel("Radius"), "spanx 3, split 2");
			add(new JLabel("Height"), "wrap");
			add(tfRadius, "spanx 3, split 2");
			add(tfHeight, "wrap");
			tfPitch = SwingUtils.createUndoTF();
			tfYaw = SwingUtils.createUndoTF();
			tfRoll = SwingUtils.createUndoTF();
			add(new JLabel("Pitch"), "");
			add(new JLabel("Yaw"), "");
			add(new JLabel("Roll"), "wrap");
			add(tfPitch, "");
			add(tfYaw, "");
			add(tfRoll, "wrap");
		}

		@Override
		public void load(CapsuleShape shape) {
			tfCenter.setText(shape.getCenter().toString());
			tfRadius.setText(Float.toString(shape.getRadius()));
			tfHeight.setText(Float.toString(shape.getHeight()));
			bCEulerAngles orientation = new bCEulerAngles(shape.getOrientation());
			tfPitch.setText(Float.toString(orientation.getPitchDeg()));
			tfYaw.setText(Float.toString(orientation.getYawDeg()));
			tfRoll.setText(Float.toString(orientation.getRollDeg()));
		}

		@Override
		public CapsuleShape save() {
			bCVector center = bCVector.fromString(tfCenter.getText());
			float radius = Float.parseFloat(tfRadius.getText());
			float height = Float.parseFloat(tfHeight.getText());
			bCMatrix3 orientation = new bCMatrix3(bCEulerAngles.fromDegree(Float.parseFloat(tfYaw.getText()),
					Float.parseFloat(tfPitch.getText()), Float.parseFloat(tfRoll.getText())));
			return new CapsuleShape(height, radius, orientation, center);
		}

		@Override
		public boolean hasChanged() {
			return tfCenter.hasChanged() || tfRadius.hasChanged() || tfHeight.hasChanged() || tfPitch.hasChanged() || tfYaw.hasChanged()
					|| tfRoll.hasChanged();
		}
	}

	private class SphereShapePanel extends ShapePanel<SphereShape> {
		private UndoableTextField tfRadius;
		private UndoableTextField tfPosition;

		public SphereShapePanel() {
			setLayout(new MigLayout("fillx, insets 0", "[fill, grow][]"));

			add(new JLabel("Position"), "");
			add(new JLabel("Radius"), "wrap");

			tfPosition = SwingUtils.createUndoTF();
			add(tfPosition, "");

			tfRadius = SwingUtils.createUndoTF();
			add(tfRadius, "width 100:125:150");
		}

		@Override
		public void load(SphereShape shape) {
			tfRadius.setText(Float.toString(shape.getSphere().getRadius()));
			tfPosition.setText(shape.getSphere().getPosition().toString());
		}

		@Override
		public SphereShape save() {
			return new SphereShape(new bCSphere(Float.valueOf(tfRadius.getText()), bCVector.fromString(tfPosition.getText())));
		}

		@Override
		public boolean hasChanged() {
			return tfPosition.hasChanged() || tfRadius.hasChanged();
		}
	}

	private class PointShapePanel extends ShapePanel<PointShape> {
		private UndoableTextField tfPosition;

		public PointShapePanel() {
			setLayout(new MigLayout("fillx, insets 0", "[fill, grow]"));

			add(new JLabel("Position"), "wrap");
			tfPosition = SwingUtils.createUndoTF();
			add(tfPosition, "");
		}

		@Override
		public void load(PointShape shape) {
			tfPosition.setText(shape.getPosition().toString());
		}

		@Override
		public PointShape save() {
			return new PointShape(bCVector.fromString(tfPosition.getText()));
		}

		@Override
		public boolean hasChanged() {
			return tfPosition.hasChanged();
		}
	}
}
