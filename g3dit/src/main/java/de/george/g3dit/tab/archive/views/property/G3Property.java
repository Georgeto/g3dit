package de.george.g3dit.tab.archive.views.property;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezware.dialog.task.TaskDialogs;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.l2fprod.common.propertysheet.AbstractProperty;
import com.l2fprod.common.propertysheet.Property;

import de.george.g3utils.io.G3Serializable;
import de.george.lrentnode.classes.desc.PropertyDescriptor;
import de.george.lrentnode.classes.desc.PropertyDescriptorRegistry;
import de.george.lrentnode.enums.G3Enums;
import de.george.lrentnode.properties.ClassProperty;
import de.george.lrentnode.properties.bTObjArray_bTPropertyContainer;
import de.george.lrentnode.properties.bTPropertyContainer;
import de.george.lrentnode.properties.bTPropertyObject;

@SuppressWarnings("rawtypes")
public class G3Property extends AbstractProperty {
	private static final Logger logger = LoggerFactory.getLogger(G3Property.class);

	private String propertySet;
	private ClassProperty classProperty;

	private Property parent;
	private List<Property> subProperties = ImmutableList.of();

	public G3Property(ClassProperty classProperty) {
		this(classProperty, null);
	}

	public G3Property(ClassProperty classProperty, String propertySet) {
		this.propertySet = propertySet;
		this.classProperty = classProperty;
		if (classProperty.getValue() instanceof bTPropertyObject) {
			bTPropertyObject propertyObject = (bTPropertyObject) classProperty.getValue();
			subProperties = ImmutableList.copyOf(FluentIterable.from(propertyObject.getClazz().properties())
					.transform(p -> new G3Property(p, propertyObject.getClassName())));
		}
	}

	public ClassProperty getClassProperty() {
		return classProperty;
	}

	@Override
	public String getName() {
		return classProperty.getName();
	}

	@Override
	public String getDisplayName() {
		return getName();
	}

	@Override
	public String getShortDescription() {
		return classProperty.getType().replaceAll("<", "&lt;").replaceAll(">", "&gt;");
	}

	@Override
	public String getCategory() {
		if (propertySet != null) {
			return PropertyDescriptorRegistry.getInstance().lookupProperty(propertySet, classProperty.getName())
					.map(PropertyDescriptor::getCategory).orElse("");
		} else {
			return "";
		}

	}

	@Override
	public Class<?> getType() {
		if (classProperty.getValue() instanceof bTPropertyContainer) {
			return G3EnumWrapper.class;
		}

		if (classProperty.getValue() instanceof bTObjArray_bTPropertyContainer) {
			return G3EnumArrayWrapper.class;
		}

		Object value = classProperty.getValue();
		if (value == null) {
			return Object.class;
		}

		PropertyValueConverter<?, ?> converter = PropertyValueConverterRegistry.getInstance().getConverter(value.getClass());
		if (converter != null) {
			return converter.getValueType();
		}

		return value.getClass();
	}

	@Override
	public boolean isEditable() {
		return true;
	}

	@Override
	public Object getValue() {
		G3Serializable value = classProperty.getValue();
		if (value instanceof bTPropertyContainer) {
			return new G3EnumWrapper(((bTPropertyContainer) value).getEnumValue(), G3Enums.byG3Type(classProperty.getType()));
		}

		if (value instanceof bTObjArray_bTPropertyContainer) {
			return new G3EnumArrayWrapper(((bTObjArray_bTPropertyContainer<?>) value).getNativeEntries(),
					G3Enums.byG3Type(classProperty.getType()));
		}

		if (value instanceof bTPropertyObject) {
			return null;
		}

		try {
			if (value != null) {
				PropertyValueConverter<G3Serializable, Object> converter = PropertyValueConverterRegistry.getInstance()
						.getConverter(value.getClass());
				if (converter != null) {
					return converter.convertTo(value);
				}
			}
		} catch (Exception e) {
			logger.warn("Fehler beim Anzeigen des Wertes der Property '{}'.", getName(), e);
			TaskDialogs.error(null, "Fehler beim Anzeigen", "Fehler beim Anzeigen des Wertes der Property '" + getName() + "'.");
			throw new RuntimeException(e);
		}

		return value;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.l2fprod.common.propertysheet.Property#setValue(java.lang.Object)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		try {
			if (classProperty.getValue() instanceof bTPropertyContainer) {
				((bTPropertyContainer<?>) classProperty.getValue()).setEnumValue(((G3EnumWrapper) value).getEnumValue());
				return;
			}

			if (classProperty.getValue() instanceof bTObjArray_bTPropertyContainer) {
				((bTObjArray_bTPropertyContainer<?>) classProperty.getValue())
						.setNativeEntries(((G3EnumArrayWrapper) value).getEnumValues());
				return;
			}

			if (classProperty.getValue() != null) {
				PropertyValueConverter<G3Serializable, Object> converter = PropertyValueConverterRegistry.getInstance()
						.getConverter(classProperty.getValue().getClass());
				if (converter != null) {
					classProperty.setValue(converter.convertFrom(classProperty.getValue(), value));
					return;
				}
			}

			classProperty.setValue((G3Serializable) value);
		} catch (Exception e) {
			logger.warn("Fehler beim Parsen des eingegebenen Wertes '{}' für Property '{}'.", value, getName(), e);
			TaskDialogs.error(null, "Fehler beim Parsen",
					"Fehler beim Parsen des eingegebenen Wertes '" + value + "' für Property '" + getName() + "'.");
		} finally {
			// Call afterwards, as it triggers the PropertySheetPanelListeners
			super.setValue(value);
		}
	}

	@Override
	public int hashCode() {
		return classProperty.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return classProperty == other;
	}

	@Override
	public String toString() {
		return "name=" + getName() + ", displayName=" + getDisplayName() + ", type=" + getType() + ", category=" + getCategory()
				+ ", editable=" + isEditable() + ", value=" + getValue();
	}

	@Override
	public Property getParentProperty() {
		return parent;
	}

	@Override
	public Property[] getSubProperties() {
		return subProperties.toArray(new Property[subProperties.size()]);
	}

	@Override
	public void readFromObject(Object object) {
		// We don't need this
	}

	@Override
	public void writeToObject(Object object) {
		// We don't need this
	}
}
