package de.george.g3dit.gui.components.tab;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class JButtonTabbedPane extends JTabbedPane {
	private boolean selectedTabBoldFont = false;

	public JButtonTabbedPane() {
		super();
		init();
	}

	public JButtonTabbedPane(int tabPlacement) {
		super(tabPlacement);
		init();
	}

	public JButtonTabbedPane(int tabPlacement, int tabLayoutPolicy) {
		super(tabPlacement, tabLayoutPolicy);
		init();
	}

	public void setSelectedTabBoldFont(boolean selectedTabBoldFont) {
		this.selectedTabBoldFont = selectedTabBoldFont;
		int index = getSelectedIndex();
		if (index != -1) {
			internalRefreshTabComponentAt(index);
		}
	}

	public boolean isSelectedTabBoldFont() {
		return selectedTabBoldFont;
	}

	private final void init() {
		addChangeListener(e -> {
			JButtonTabbedPane pane = JButtonTabbedPane.this;
			for (int i = 0; i < pane.getTabCount(); i++) {
				internalRefreshTabComponentAt(i);
			}
		});
	}

	@Override
	public void addTab(String title, Component component) {
		addTab(title, null, component);
	}

	@Override
	public void addTab(String title, Icon icon, Component component) {
		addTab(title, icon, component, null);
	}

	@Override
	public void addTab(String title, Icon icon, Component component, String tip) {
		super.addTab(title, icon, component, tip);
		int index = indexOfComponent(component);
		createTabComponentAt(index, title, null, icon);
	}

	@Override
	public void insertTab(String title, Icon icon, Component component, String tip, int index) {
		super.insertTab(title, icon, component, tip, index);
		createTabComponentAt(index, title, null, icon);
	}

	public void addTabAction(int index, Action action, Icon icon) {
		addTabAction(index, action, icon, false);
	}

	public void addTabAction(int index, Action action, Icon icon, boolean front) {
		Optional<JButtonTabComponent> tabComponent = buttonTabComponentAt(index);
		if (tabComponent.isPresent()) {
			tabComponent.get().addButton(action, icon, front);
		}

		internalRefreshTabComponentAt(index);
	}

	@Override
	public void setDisabledIconAt(int index, Icon disabledIcon) {
		super.setDisabledIconAt(index, disabledIcon);
		internalRefreshTabComponentAt(index);
	}

	@Override
	public void setIconAt(int index, Icon icon) {
		Optional<JButtonTabComponent> tabComponent = buttonTabComponentAt(index);
		if (tabComponent.isPresent()) {
			tabComponent.get().setIcon(icon);
		}

		internalRefreshTabComponentAt(index);
	}

	@Override
	public void setTitleAt(int index, String title) {
		Optional<JButtonTabComponent> tabComponent = buttonTabComponentAt(index);
		if (tabComponent.isPresent()) {
			tabComponent.get().setTitle(title);
		}

		internalRefreshTabComponentAt(index);
	}

	public void setTitleColorAt(int index, Color titleColor) {
		Optional<JButtonTabComponent> tabComponent = buttonTabComponentAt(index);
		if (tabComponent.isPresent()) {
			tabComponent.get().setTitleColor(titleColor);
		}

		internalRefreshTabComponentAt(index);
	}

	final private void createTabComponentAt(int index, String title, Color titleColor, Icon icon) {
		super.setTabComponentAt(index, new JButtonTabComponent(title, titleColor, icon));
		internalRefreshTabComponentAt(index);
	}

	final private void internalRefreshTabComponentAt(final int index) {
		Optional<JButtonTabComponent> tabComponent = buttonTabComponentAt(index);
		if (tabComponent.isPresent()) {
			tabComponent.get().rebuild(index == getSelectedIndex());
		}
		repaint();
	}

	private Optional<JButtonTabComponent> buttonTabComponentAt(int index) {
		Component c = super.getTabComponentAt(index);
		if (c != null && c instanceof JButtonTabComponent) {
			return Optional.of((JButtonTabComponent) c);
		}
		return Optional.empty();
	}

	private class JButtonTabComponent extends JPanel {

		private String title;
		private Color titleColor;
		private Icon icon;
		private List<ButtonTabButton> buttons;

		private Dimension buttonSize;
		private int buttonSeparatorStrutSize;

		public JButtonTabComponent(String title, Color titleColor, Icon icon) {
			this.title = title;
			this.titleColor = titleColor;
			this.icon = icon;
			buttons = new ArrayList<>();
			setOpaque(false);
			setBorder(new EmptyBorder(3, 0, 0, 0));
			FlowLayout flowLayout = (FlowLayout) getLayout();
			flowLayout.setVgap(0);
			flowLayout.setHgap(0);

			if (UIManager.getLookAndFeel().getName().toLowerCase().contains("nimbus")) {
				buttonSize = new Dimension(16, 16);
				buttonSeparatorStrutSize = 0;
			} else {
				buttonSize = new Dimension(14, 14);
				buttonSeparatorStrutSize = 2;
			}
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public Color getTitleColor() {
			return titleColor;
		}

		public void setTitleColor(Color titleColor) {
			this.titleColor = titleColor;
		}

		public Icon getIcon() {
			return icon;
		}

		public void setIcon(Icon icon) {
			this.icon = icon;
		}

		public void addButton(Action action, Icon icon, boolean front) {
			if (front) {
				buttons.add(0, new ButtonTabButton(action, icon));
			} else {
				buttons.add(new ButtonTabButton(action, icon));
			}
		}

		public void rebuild(boolean selected) {
			removeAll();
			if (icon != null) {
				add(new JLabel(icon));
			}

			if (title != null) {
				if (icon != null) {
					add(Box.createHorizontalStrut(8));
				}
				JLabel lblTitle = new JLabel(title);
				if (titleColor != null) {
					lblTitle.setForeground(titleColor);
				}
				if (selected && isSelectedTabBoldFont()) {
					lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD));
				}
				add(lblTitle);
			}

			if ((icon != null || title != null) && buttons.size() > 0) {
				add(Box.createHorizontalStrut(8));
			}

			for (int i = 0; i < buttons.size(); i++) {
				if (i > 0) {
					add(Box.createHorizontalStrut(buttonSeparatorStrutSize));
				}
				ButtonTabButton tabButton = buttons.get(i);
				JButton button = new JButton();
				button.setFocusable(false);
				button.setIcon(tabButton.getIcon());
				button.setPreferredSize(buttonSize);
				button.addActionListener(tabButton.getAction());
				add(button);
			}

		}

		private class ButtonTabButton {
			private Action action;
			private Icon icon;

			public ButtonTabButton(Action action, Icon icon) {
				this.action = action;
				this.icon = icon;
			}

			public Action getAction() {
				return action;
			}

			public Icon getIcon() {
				return icon;
			}
		}
	}
}
