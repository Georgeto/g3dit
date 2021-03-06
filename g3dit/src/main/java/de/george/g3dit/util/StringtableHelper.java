package de.george.g3dit.util;

import java.awt.Window;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.ezware.dialog.task.TaskDialogs;
import com.google.common.base.Joiner;

import de.george.g3utils.structure.Stringtable;
import de.george.lrentnode.archive.G3ClassContainer;
import de.george.lrentnode.classes.DefaultClass;

public class StringtableHelper {
	public static void clearStringtableSafe(List<? extends G3ClassContainer> classContainers, Stringtable stringtable) {
		clearStringtableSafe(classContainers, stringtable, false, null);
	}

	public static void clearStringtableSafe(Collection<? extends G3ClassContainer> classContainers, Stringtable stringtable,
			boolean displayWarning, Window parent) {
		Set<String> unknownClasses = classContainers.stream().flatMap(c -> c.getClasses().stream()).filter(c -> c instanceof DefaultClass)
				.filter(c -> ((DefaultClass) c).getRaw() != null && ((DefaultClass) c).getRaw().length() > 4).map(c -> c.getClassName())
				.collect(Collectors.toSet());

		if (!unknownClasses.isEmpty()) {
			if (displayWarning) {
				boolean isConfirmed = TaskDialogs.isConfirmed(parent,
						"Datei enthält unbekannte Klassen, durch das Aufräumen der Stringtable\nkönnten Stringtableeinträge gelöscht werden,\ndie noch von diesen Klassen verwendet werden.\nStringtable trotzdem aufräumen?",
						Joiner.on("\n").join(unknownClasses));
				if (isConfirmed) {
					stringtable.clear();
				}
			}
		} else {
			stringtable.clear();
		}
	}
}
