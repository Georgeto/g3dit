package de.george.g3dit.tab.archive.views.property;

import java.util.ArrayList;
import java.util.List;

import com.l2fprod.common.beans.editor.ComboBoxPropertyEditor;

import de.george.lrentnode.enums.G3Enums;

public class G3EnumEditor extends ComboBoxPropertyEditor {
	public void initAvaibleEnums(G3EnumWrapper wrapper) {
		List<String> nameList = G3Enums.asList(wrapper.getEnumClass());
		List<G3EnumWrapper> enums = new ArrayList<>(nameList.size());
		for (String name : nameList) {
			enums.add(new G3EnumWrapper(G3Enums.asInt(wrapper.getEnumClass(), name), wrapper.getEnumClass()));
		}

		if (!enums.contains(wrapper)) {
			enums.add(wrapper);
		}

		setAvailableValues(enums.toArray());
	}

	@Override
	public void setValue(Object value) {
		if (value instanceof G3EnumWrapper) {
			initAvaibleEnums((G3EnumWrapper) value);
			super.setValue(value);
		} else {
			throw new IllegalArgumentException(
					String.format("Value must be instance of %s, instead found %s", G3EnumWrapper.class, value.getClass()));
		}
	}
}
