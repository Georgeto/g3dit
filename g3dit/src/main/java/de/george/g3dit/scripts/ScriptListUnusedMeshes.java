package de.george.g3dit.scripts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

import de.george.g3dit.util.FileManager;
import de.george.lrentnode.archive.ArchiveFile;
import de.george.lrentnode.archive.G3ClassContainer;
import de.george.lrentnode.archive.eCEntity;
import de.george.lrentnode.classes.eCResourceMeshLoD_PS;
import de.george.lrentnode.classes.eCVisualAnimation_PS;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.iterator.ArchiveFileIterator;
import de.george.lrentnode.iterator.TemplateFileIterator;
import de.george.lrentnode.template.TemplateFile;
import de.george.lrentnode.util.EntityUtil;
import de.george.lrentnode.util.FileUtil;

public class ScriptListUnusedMeshes implements IScript {

	@Override
	public String getTitle() {
		return "Nicht verwendete Meshes auflisten";
	}

	@Override
	public String getDescription() {
		return "Erstellt eine Liste aller nicht verwendeten Meshes.";
	}

	@Override
	public boolean execute(IScriptEnvironment env) {
		Map<String, File> meshes = env.getFileManager()
				.listFiles(FileManager.RP_COMPILED_MESH, (file) -> file.getName().endsWith(".xcmsh")).stream()
				.collect(Collectors.toMap(f -> f.getName().toLowerCase(), f -> f));

		meshes.putAll(env.getFileManager().listFiles(FileManager.RP_COMPILED_ANIMATION, (file) -> file.getName().endsWith(".xact"))
				.stream().collect(Collectors.toMap(f -> f.getName().toLowerCase(), f -> f)));

		List<File> lodMeshes = env.getFileManager().listFiles(FileManager.RP_COMPILED_MESH, (file) -> file.getName().endsWith(".xlmsh"));

		meshes.putAll(lodMeshes.stream().collect(Collectors.toMap(f -> f.getName().toLowerCase(), f -> f)));

		for (File lodMeshFile : lodMeshes) {
			try {
				eCResourceMeshLoD_PS lodMesh = FileUtil.openLodMesh(new FileInputStream(lodMeshFile));
				lodMesh.getMeshes().forEach(m -> meshes.remove(m.toLowerCase()));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}

		Consumer<String> meshConsumer = mesh -> {
			if (mesh != null) {
				meshes.remove(EntityUtil.cleanAnimatedMeshName(mesh.toLowerCase()));
			}
		};

		Consumer<G3ClassContainer> containerConsumer = container -> {
			meshConsumer.accept(EntityUtil.getMesh(container).orElse(null));

			eCVisualAnimation_PS animation = EntityUtil.getAnimatedMeshClass(container);
			if (animation != null) {
				meshConsumer.accept(animation.fxaSlot.fxaFile);
				meshConsumer.accept(animation.fxaSlot.fxaFile2);
				meshConsumer.accept(animation.property(CD.eCVisualAnimation_PS.ResourceFilePath).getString());
				meshConsumer.accept(animation.property(CD.eCVisualAnimation_PS.FacialAnimFilePath).getString());
			}
		};

		ArchiveFileIterator worldFilesIterator = env.getFileManager().worldFilesIterator();
		while (worldFilesIterator.hasNext()) {
			ArchiveFile aFile = worldFilesIterator.next();
			for (eCEntity entity : aFile.getEntities()) {
				containerConsumer.accept(entity);
			}
		}

		TemplateFileIterator tpleFilesIterator = env.getFileManager().templateFilesIterator();
		while (tpleFilesIterator.hasNext()) {
			TemplateFile aFile = tpleFilesIterator.next();
			containerConsumer.accept(aFile.getReferenceHeader());
		}

		env.log("----------\nÜbersicht\n----------");

		String path = null;
		for (File e : Sets.newTreeSet(meshes.values())) {
			String tempPath = e.getAbsolutePath().replaceFirst(".*\\\\_compiled", "_compiled");
			tempPath = tempPath.substring(0, tempPath.lastIndexOf("\\"));

			if (path == null || !path.equals(tempPath)) {
				path = tempPath;
				env.log("\n" + path);
			}

			env.log("\t" + e.getName());
		}

		env.log("\n\n----------\nListe\n----------");
		Sets.newTreeSet(meshes.values()).forEach(e -> env.log(e.getAbsolutePath()));

		return true;
	}

}
