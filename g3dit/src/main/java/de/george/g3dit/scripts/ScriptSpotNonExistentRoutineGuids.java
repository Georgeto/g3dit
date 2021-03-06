package de.george.g3dit.scripts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;

import de.george.lrentnode.archive.ArchiveFile;
import de.george.lrentnode.archive.eCEntity;
import de.george.lrentnode.classes.G3Class;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.enums.G3Enums.gESpecies;
import de.george.lrentnode.iterator.ArchiveFileIterator;

public class ScriptSpotNonExistentRoutineGuids implements IScript {

	@Override
	public String getTitle() {
		return "Routinen mit nicht existenten Guids ermitteln";
	}

	@Override
	public String getDescription() {
		return "Überprüft die Routinen aller Entities auf nicht existenten Guids.";
	}

	@Override
	public boolean execute(IScriptEnvironment env) {
		ArchiveFileIterator worldFilesIterator = env.getFileManager().worldFilesIterator();
		Set<String> knownGuids = new HashSet<>();
		Map<String, G3Class> entityRoutineMap = new LinkedHashMap<>();

		while (worldFilesIterator.hasNext()) {
			ArchiveFile archiveFile = worldFilesIterator.next();

			for (eCEntity entity : archiveFile.getEntities()) {
				knownGuids.add(entity.getGuid());

				if (entity.hasClass(CD.gCNavigation_PS.class) && entity.hasClass(CD.gCNPC_PS.class)) {
					int species = entity.getClass(CD.gCNPC_PS.class).property(CD.gCNPC_PS.Species).getEnumValue();
					if (species == gESpecies.gESpecies_Human || species == gESpecies.gESpecies_Orc) {
						G3Class nav = entity.getClass(CD.gCNavigation_PS.class);
						String entityIdentifier = worldFilesIterator.nextFile().getName() + "#" + entity.toString() + " ("
								+ entity.getGuid() + ")";
						entityRoutineMap.put(entityIdentifier, nav);
					}
				}
			}
		}

		entityRoutineMap.entrySet().forEach(e -> {
			G3Class navigation = e.getValue();

			List<String> messages = new ArrayList<>();

			// Routinen laden
			List<String> routineNames = new ArrayList<>(navigation.property(CD.gCNavigation_PS.RoutineNames).getNativeEntries());
			List<String> workingPoints = new ArrayList<>(
					navigation.property(CD.gCNavigation_PS.WorkingPoints).getEntries(p -> p.getGuid()));
			List<String> relaxingPoints = new ArrayList<>(
					navigation.property(CD.gCNavigation_PS.RelaxingPoints).getEntries(p -> p.getGuid()));
			List<String> sleepingPoints = new ArrayList<>(
					navigation.property(CD.gCNavigation_PS.SleepingPoints).getEntries(p -> p.getGuid()));

			// Startroutine laden
			String routineName = navigation.property(CD.gCNavigation_PS.Routine).getString();
			String workingPoint = navigation.property(CD.gCNavigation_PS.WorkingPoint).getGuid();
			String relaxingPoint = navigation.property(CD.gCNavigation_PS.RelaxingPoint).getGuid();
			String sleepingPoint = navigation.property(CD.gCNavigation_PS.SleepingPoint).getGuid();

			int index = routineNames.indexOf(routineName);
			if (index != -1) {
				if (!workingPoint.equals(workingPoints.get(index)) || !relaxingPoint.equals(relaxingPoints.get(index))
						|| !sleepingPoint.equals(sleepingPoints.get(index))) {
					messages.add("Guids der Startroutine '" + routineName + "' unterscheiden sich von denen des Routinelisteneintrages.");
					routineNames.add(0, routineName + " (Startroutine)");
					workingPoints.add(0, workingPoint);
					relaxingPoints.add(0, relaxingPoint);
					sleepingPoints.add(0, sleepingPoint);
				}
			} else {
				messages.add("Startroutine '" + routineName + "' ist nicht in der Routinenliste enthalten.");
				routineNames.add(0, routineName + " (Startroutine)");
				workingPoints.add(0, workingPoint);
				relaxingPoints.add(0, relaxingPoint);
				sleepingPoints.add(0, sleepingPoint);
			}

			for (int i = 0; i < routineNames.size(); i++) {
				boolean validWP = knownGuids.contains(workingPoints.get(i));
				boolean validRP = knownGuids.contains(relaxingPoints.get(i));
				boolean validSP = knownGuids.contains(sleepingPoints.get(i));
				if (!validWP || !validRP || !validSP) {
					String message = routineNames.get(i) + ": ";
					message += Joiner.on(", ").skipNulls().join(validWP ? null : "WorkingPoint", validRP ? null : "RelaxingPoint",
							validSP ? null : "SleepingPoint");
					messages.add(message);
				}
			}

			if (!messages.isEmpty()) {
				env.log(e.getKey());
				messages.stream().map(s -> "  " + s).forEach(env::log);
				env.log("");
			}
		});

		return true;
	}
}
