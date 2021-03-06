package de.george.g3dit.gui.map;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.table.ColumnFactory;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.gui.AbstractTableComparatorChooser;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.matchers.AbstractMatcherEditor;
import ca.odell.glazedlists.matchers.MatcherEditor;
import ca.odell.glazedlists.swing.AdvancedListSelectionModel;
import ca.odell.glazedlists.swing.JXTableSupport;
import de.george.g3dit.gui.table.TableUtil;
import de.george.g3dit.rpc.IpcUtil;
import de.george.g3utils.gui.SwingUtils;
import de.george.g3utils.structure.bCVector;

public class MapAndTableComponent<T extends MapItem> {
	private EventList<T> items;

	private EventList<T> filteredItems;
	private MapComponent<T> map;
	private EventList<T> itemsSelectedOnMap = new BasicEventList<>();

	private JXTable table;
	private JXTableSupport<T> tableSupport;
	private JCheckBox cbOnlySelected;
	private SortedList<T> itemsInListSorted;

	private Runnable callbackChange;
	private Function<T, Color> colorProvider;
	private Consumer<T> callbackGoto;
	private Consumer<T> callbackNavigate;

	public MapAndTableComponent(ColumnFactory columnFactory, TableFormat<? super T> tableFormat, MatcherEditor<? super T> matcherEditor) {
		this(columnFactory, tableFormat, matcherEditor, null);
	}

	public MapAndTableComponent(ColumnFactory columnFactory, TableFormat<? super T> tableFormat, MatcherEditor<? super T> matcherEditor,
			Comparator<? super T> itemSorter) {
		items = new BasicEventList<>();
		// Called when a value in the table model changes
		items.addListEventListener(l -> {
			if (callbackChange != null) {
				callbackChange.run();
			}
		});

		filteredItems = new FilterList<>(items, matcherEditor);

		OnlySelected onlySelectedMatcher = new OnlySelected();
		FilterList<T> onlySelectedOnMap = new FilterList<>(filteredItems, onlySelectedMatcher);

		map = new MapComponent<>(filteredItems);

		MapMarkerOverlay<T> markerOverlay = map.addMarkerOverlay(this::getItemColor);
		EntitySelectOverlay<T> selectOverlay = map.addSelectOverlay(this::setSelectedItems);
		map.addItemClickListener((item, e) -> {
			AdvancedListSelectionModel<T> selectionModel = tableSupport.getTableSelectionModel();
			if (!((e.getOriginalEvent().getModifiers() & InputEvent.ALT_MASK) != InputEvent.ALT_MASK
					&& onlySelectedOnMap.contains(item))) {
				selectOverlay.reset();
			}

			selectionModel.getTogglingSelected().clear();
			selectionModel.getTogglingSelected().add(item);
			int minSelectionIndex = selectionModel.getMinSelectionIndex();
			if (minSelectionIndex != -1) {
				tableSupport.getTable().scrollRowToVisible(minSelectionIndex);
			}

			if ((e.getOriginalEvent().getModifiers() & InputEvent.CTRL_MASK) == InputEvent.CTRL_MASK) {
				if (callbackGoto != null) {
					callbackGoto.accept(item);
				}
			}
		});

		table = new JXTable();
		table.setColumnFactory(columnFactory);
		table.setColumnControlVisible(true);
		itemsInListSorted = itemSorter != null ? new SortedList<>(onlySelectedOnMap, itemSorter) : new SortedList<>(onlySelectedOnMap);
		tableSupport = JXTableSupport.install(table, itemsInListSorted, tableFormat, itemsInListSorted,
				AbstractTableComparatorChooser.MULTIPLE_COLUMN_MOUSE_WITH_UNDO);
		SwingUtils.addKeyStroke(table, JComponent.WHEN_IN_FOCUSED_WINDOW, "Goto",
				KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), () -> TableUtil.withSelectedRow(table, this::onGoto));
		SwingUtils.addKeyStroke(table, JComponent.WHEN_IN_FOCUSED_WINDOW, "Navigate",
				KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK),
				() -> TableUtil.withSelectedRow(table, this::onNavigate));
		SwingUtils.addKeyStroke(table, JComponent.WHEN_IN_FOCUSED_WINDOW, "Invert Selection",
				KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK),
				() -> tableSupport.getTableSelectionModel().invertSelection());
		tableSupport.getTableSelectionModel().addListSelectionListener(e -> markerOverlay.repaint());

		MapPositionOverlay positionOverlay = new MapPositionOverlay(map.getModel(), map.getViewer()::getViewRect);
		map.addOverlay(positionOverlay, 2);

		SwingUtils.addKeyStroke(map.getViewer().getComponent(), JComponent.WHEN_IN_FOCUSED_WINDOW, "Goto Position",
				KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), () -> {
					bCVector lastPosition = positionOverlay.getLastPosition();
					if (lastPosition != null) {
						IpcUtil.gotoPosition(lastPosition, true);
					}
				});

		cbOnlySelected = new JCheckBox("Nur auf Karte Ausgewählte anzeigen");
		onlySelectedMatcher.init();
	}

	private void setSelectedItems(List<T> selectedItems) {
		this.itemsSelectedOnMap.clear();
		this.itemsSelectedOnMap.addAll(selectedItems);
		AdvancedListSelectionModel<T> selectionModel = tableSupport.getTableSelectionModel();

		EventList<T> selected = selectionModel.getTogglingSelected();
		selected.clear();

		if (!cbOnlySelected.isSelected()) {
			selected.addAll(selectedItems);

			int minSelectionIndex = selectionModel.getMinSelectionIndex();
			if (minSelectionIndex != -1) {
				tableSupport.getTable().scrollRowToVisible(minSelectionIndex);
			}
		}
		map.getModel().repaint();
	}

	private class OnlySelected extends AbstractMatcherEditor<T> {
		public void init() {
			cbOnlySelected.addItemListener(l -> policyChanged());
			itemsSelectedOnMap.addListEventListener(l -> policyChanged());
			policyChanged();
		}

		public void policyChanged() {
			// TODO: Previously this was wrapped in a SwingUtils.invokeLater() call, not sure if it
			// was really needed.
			// Anyway, it caused an exception in map.addItemClickListener(), because the
			// onlySelectedOnMap list, which is backing the table, got only updated at a later point
			// in time.
			// The call to selectOverlay.reset() causes a call to policyChanged() via
			// itemsSelectedOnMap's event listener.
			if (cbOnlySelected.isSelected() && !itemsSelectedOnMap.isEmpty()) {
				fireChanged(i -> itemsSelectedOnMap.contains(i));
			} else {
				fireMatchAll();
			}
		}
	}

	public List<T> getItems() {
		return items;
	}

	public List<T> getItemsSelectedInList() {
		return tableSupport.getTableSelectionModel().getSelected();
	}

	public void addItem(T item) {
		this.items.add(item);
	}

	public void setItems(Collection<T> items) {
		this.items.clear();
		this.items.addAll(items);
	}

	public void removeItems(Collection<T> items) {
		this.items.removeAll(new ArrayList<>(items));
	}

	public boolean isSelectedInList(T item) {
		return tableSupport.getTableSelectionModel().getSelected().contains(item);
	}

	public boolean isSelectedOnMap(T item) {
		return itemsSelectedOnMap.contains(item);
	}

	public void setCallbackChange(Runnable callbackChange) {
		this.callbackChange = callbackChange;
	}

	public void setColorProvider(Function<T, Color> colorProvider) {
		this.colorProvider = colorProvider;
	}

	public void setCallbackGoto(Consumer<T> callbackGoto) {
		this.callbackGoto = callbackGoto;
	}

	public void setCallbackNavigate(Consumer<T> callbackNavigate) {
		this.callbackNavigate = callbackNavigate;
	}

	public MapComponent<T> getMap() {
		return map;
	}

	public JXTable getTable() {
		return table;
	}

	public JCheckBox getCbOnlySelected() {
		return cbOnlySelected;
	}

	public void setValueAt(Object value, T item, int propertyIndex) {
		// Update table and object.
		tableSupport.getTableModel().setValueAt(value, itemsInListSorted.indexOf(item), propertyIndex);
	}

	private Color getItemColor(T item) {
		if (colorProvider != null) {
			return colorProvider.apply(item);
		}

		if (isSelectedInList(item)) {
			return Color.GREEN;
		} else if (isSelectedOnMap(item)) {
			return Color.BLUE;
		} else {
			return Color.RED;
		}
	}

	private void onGoto(int modelIndex) {
		T item = tableSupport.getTableModel().getElementAt(modelIndex);
		if (callbackGoto != null) {
			callbackGoto.accept(item);
		}
	}

	private void onNavigate(int modelIndex) {
		T item = tableSupport.getTableModel().getElementAt(modelIndex);
		if (callbackNavigate != null) {
			callbackNavigate.accept(item);
		}
	}
}
