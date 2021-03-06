package de.george.g3dit.util;

import static de.george.g3utils.util.ReflectionUtils.withModifier;
import static de.george.g3utils.util.ReflectionUtils.withParametersCount;
import static de.george.g3utils.util.ReflectionUtils.withPrefix;
import static de.george.g3utils.util.ReflectionUtils.withReturnType;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import de.george.g3dit.EditorContext;
import de.george.g3dit.jme.asset.IntermediateMesh;
import de.george.g3dit.jme.asset.MeshUtil;
import de.george.g3dit.jme.asset.MeshUtil.IllegalMeshException;
import de.george.g3utils.io.AbsoluteFileLocator;
import de.george.g3utils.io.CompositeFileLocator;
import de.george.g3utils.io.FileLocator;
import de.george.g3utils.util.IOUtils;
import de.george.g3utils.util.IndentPrintWriter;
import de.george.g3utils.util.Pair;
import de.george.g3utils.util.ReflectionUtils;
import de.george.lrentnode.archive.G3ClassContainer;
import de.george.lrentnode.archive.eCEntity;
import de.george.lrentnode.classes.eCColorSrcBase;
import de.george.lrentnode.classes.eCColorSrcCombiner;
import de.george.lrentnode.classes.eCColorSrcSampler;
import de.george.lrentnode.classes.eCResourceMeshLoD_PS;
import de.george.lrentnode.classes.eCResourceShaderMaterial_PS;
import de.george.lrentnode.classes.eCShaderBase;
import de.george.lrentnode.classes.eCShaderEllementBase;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.enums.G3Enums;
import de.george.lrentnode.enums.G3Enums.eEColorSrcSwitchRepeat;
import de.george.lrentnode.properties.bTPropertyContainer;
import de.george.lrentnode.structures.eCColorSrcProxy;
import de.george.lrentnode.util.EntityUtil;
import de.george.lrentnode.util.FileUtil;
import de.george.lrentnode.util.NPCUtil;

public class AssetResolver {
	private int materialSwitch;
	private eCShaderBase shader;

	private FileLocator meshLocator;
	private FileLocator animatedLocator;
	private FileLocator materialLocator;
	private FileLocator textureLocator;

	public AssetResolver(EditorContext ctx, boolean noRefreshOnCacheMiss) {
		this(ctx, noRefreshOnCacheMiss, false);
	}

	public AssetResolver(EditorContext ctx, boolean noRefreshOnCacheMiss, boolean supportAbsolutePaths) {
		FileManager fileManager = ctx.getFileManager();
		meshLocator = fileManager.getFileLocator(FileManager.RP_COMPILED_MESH, noRefreshOnCacheMiss);
		animatedLocator = fileManager.getFileLocator(FileManager.RP_COMPILED_ANIMATION, noRefreshOnCacheMiss);
		materialLocator = fileManager.getFileLocator(FileManager.RP_COMPILED_MATERIAL, noRefreshOnCacheMiss);
		textureLocator = fileManager.getFileLocator(FileManager.RP_COMPILED_IMAGE, noRefreshOnCacheMiss);

		if (supportAbsolutePaths) {
			meshLocator = new CompositeFileLocator(AbsoluteFileLocator.instance(), meshLocator);
			animatedLocator = new CompositeFileLocator(AbsoluteFileLocator.instance(), animatedLocator);
			materialLocator = new CompositeFileLocator(AbsoluteFileLocator.instance(), materialLocator);
			textureLocator = new CompositeFileLocator(AbsoluteFileLocator.instance(), textureLocator);
		}
	}

	public List<MeshAsset> resolveEntity(eCEntity entity) {
		List<MeshAsset> assets = new ArrayList<>();
		assets.add(resolveContainer(entity));
		if (NPCUtil.isNPC(entity)) {
			entity.getChilds().stream().map(this::resolveContainer).filter(MeshAsset::isFound).forEach(assets::add);
		}
		return assets;
	}

	public MeshAsset resolveContainer(G3ClassContainer container) {
		Pair<String, Integer> meshAndMaterialSwitch = EntityUtil.getMeshAndMaterialSwitch(container).orElse(null);
		if (meshAndMaterialSwitch != null) {
			int materialSwitch = meshAndMaterialSwitch.el1();
			String mesh = EntityUtil.cleanAnimatedMeshName(meshAndMaterialSwitch.el0());
			return resolveMesh(mesh, materialSwitch);
		}
		return new MeshAsset("", false, "Container hat kein Mesh.");
	}

	public MeshAsset resolveMesh(String meshFile, int materialSwitch) {
		List<IntermediateMesh> meshes;
		try {
			if (meshFile.toLowerCase().endsWith(".xcmsh")) {
				meshes = MeshUtil.toIntermediateMesh(FileUtil.openMesh(meshLocator.locate(meshFile).get()), true);
			} else if (meshFile.toLowerCase().endsWith(".xact")) {
				meshes = MeshUtil.toIntermediateMesh(FileUtil.openAnimationActor(animatedLocator.locate(meshFile).get()), true);
			} else if (meshFile.toLowerCase().endsWith(".xlmsh")) {
				eCResourceMeshLoD_PS lodMesh = FileUtil.openLodMesh(meshLocator.locate(meshFile).get());
				return resolveMesh(lodMesh.getMeshes().get(0), materialSwitch);
			} else {
				throw new NoSuchElementException();
			}
		} catch (IOException | IllegalMeshException e) {
			return new MeshAsset(meshFile, false, "Fehler beim Öffnen des Meshes: " + e.getMessage());
		} catch (NoSuchElementException e) {
			return new MeshAsset(meshFile, false, "Kein Mesh mit diesem Namen gefunden.");
		}

		MeshAsset asset = new MeshAsset(meshFile, true, null);
		for (IntermediateMesh mesh : meshes) {
			asset.getMaterials().add(resolveMaterial(mesh.materialName, materialSwitch));
		}
		return asset;
	}

	public MaterialAsset resolveMaterial(String materialFile, int materialSwitch) {
		try {
			eCResourceShaderMaterial_PS material = FileUtil.openMaterial(materialLocator.locate(materialFile).get());
			return parseMaterial(materialFile, material, materialSwitch);
		} catch (IOException e) {
			return new MaterialAsset(materialFile, materialSwitch, false, "Fehler beim Öffnen des Materials: " + e.getMessage());
		} catch (NoSuchElementException e) {
			return new MaterialAsset(materialFile, materialSwitch, false, "Kein Material mit diesem Namen gefunden.");
		}
	}

	public MaterialAsset parseMaterial(String materialName, eCResourceShaderMaterial_PS material, int materialSwitch) {
		shader = material.getShader();
		this.materialSwitch = materialSwitch;

		MaterialAsset asset = new MaterialAsset(materialName, materialSwitch, true, null);

		Set<Method> getters = ReflectionUtils.getAllMethods(shader.getClass(), withPrefix("getColorSrc"), withParametersCount(0),
				withReturnType(eCColorSrcProxy.class), withModifier(Modifier.PUBLIC));

		for (Method getter : getters) {
			String textureType = getter.getName().replaceFirst("getColorSrc", "");
			eCColorSrcProxy proxy = (eCColorSrcProxy) ReflectionUtils.invoke(getter, shader);
			if (proxy == null) {
				continue;
			}
			Optional<eCShaderEllementBase> element = shader.getElement(proxy.getGuid());
			if (element.isPresent()) {
				List<TextureAsset> textures = parseTexture((eCColorSrcBase) element.get());
				for (TextureAsset texture : textures) {
					texture.setUsageType(textureType);
					asset.getTextures().add(texture);
				}
			}
		}

		return asset;
	}

	private List<TextureAsset> parseTexture(eCColorSrcBase data) {
		if (data instanceof eCColorSrcSampler) {
			return ImmutableList.of(parseColorSampler((eCColorSrcSampler) data));
		} else if (data instanceof eCColorSrcCombiner) {
			return parseColorCombiner((eCColorSrcCombiner) data);
		}
		return ImmutableList.of();
	}

	private List<TextureAsset> parseColorCombiner(eCColorSrcCombiner data) {
		Optional<eCShaderEllementBase> colorSrc1 = shader.getElement(data.getColorSrc1().getGuid());
		Optional<eCShaderEllementBase> colorSrc2 = shader.getElement(data.getColorSrc2().getGuid());
		List<TextureAsset> textures = new LinkedList<>();
		if (colorSrc1.isPresent() && colorSrc1.get() instanceof eCColorSrcSampler) {
			textures.add(parseColorSampler((eCColorSrcSampler) colorSrc1.get()));
		}

		if (colorSrc2.isPresent() && colorSrc2.get() instanceof eCColorSrcSampler) {
			textures.add(parseColorSampler((eCColorSrcSampler) colorSrc2.get()));
		}

		return textures;
	}

	private TextureAsset parseColorSampler(eCColorSrcSampler data) {
		String strippedTextureName = data.property(CD.eCColorSrcSampler.ImageFilePath).getString();
		strippedTextureName = IOUtils.stripExtension(strippedTextureName);

		// strip paths
		strippedTextureName = strippedTextureName.replaceAll(".*/", "");
		String textureName = strippedTextureName + ".ximg";

		boolean switched = strippedTextureName.toLowerCase().endsWith("_s1");
		int switchRepeat = data.propertyNoThrow(CD.eCColorSrcSampler.SwitchRepeat).map(bTPropertyContainer::getEnumValue)
				.orElse(eEColorSrcSwitchRepeat.eEColorSrcSwitchRepeat_Repeat);

		// Switched
		if (switched && materialSwitch != 0) {
			String baseTextureName = strippedTextureName.substring(0, strippedTextureName.length() - 1);
			int textureCount = 0;
			do {
				if (!textureLocator.locate(baseTextureName + (textureCount + 1) + ".ximg").isPresent()) {
					break;
				}
				textureCount++;

				// Optimierung
				if (switchRepeat == eEColorSrcSwitchRepeat.eEColorSrcSwitchRepeat_Repeat && materialSwitch > 0
						&& materialSwitch < textureCount) {
					break;
				}
			} while (true);

			if (textureCount == 0) {
				return new TextureAsset(switched, textureName, switchRepeat, textureName, false,
						"Keine Textur mit diesem Namen gefunden.");
			}

			int textureIndex = 0;
			switch (switchRepeat) {
				case eEColorSrcSwitchRepeat.eEColorSrcSwitchRepeat_Repeat:
					textureIndex = materialSwitch % textureCount;
					break;

				case eEColorSrcSwitchRepeat.eEColorSrcSwitchRepeat_Clamp:
					textureIndex = materialSwitch;
					if (textureIndex < 0) {
						textureIndex = 0;
					}
					if (textureIndex > textureCount - 1) {
						textureIndex = textureCount - 1;
					}
					break;

				case eEColorSrcSwitchRepeat.eEColorSrcSwitchRepeat_PingPong:
					textureIndex = materialSwitch % textureCount;
					if ((textureIndex & 1) == 1) {
						textureIndex = textureCount - textureIndex - 1;
					}
					break;
			}

			String finalTextureName = baseTextureName + (textureIndex + 1) + ".ximg";
			return new TextureAsset(switched, textureName, switchRepeat, finalTextureName, true, null);
		}

		if (!textureLocator.locate(textureName).isPresent()) {
			return new TextureAsset(switched, textureName, switchRepeat, textureName, false, "Keine Textur mit diesem Namen gefunden.");
		}

		return new TextureAsset(switched, textureName, switchRepeat, textureName, true, null);
	}

	public static abstract class AbstractAsset {
		private boolean found;
		private String error;
		private String name;

		public AbstractAsset(String name, boolean found, String error) {
			this.found = found;
			this.error = error;
			this.name = name;
		}

		public boolean isFound() {
			return found;
		}

		public String getError() {
			return error;
		}

		public String getName() {
			return name;
		}

		public void setFound(boolean found) {
			this.found = found;
		}

		public void setError(String error) {
			this.error = error;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("found", found).add("error", error).add("name", name).toString();
		}

		public String print() {
			CharArrayWriter chars = new CharArrayWriter();
			print(new IndentPrintWriter(chars, "    "));
			return chars.toString();
		}

		public abstract void print(IndentPrintWriter writer);
	}

	public static class MeshAsset extends AbstractAsset {
		private List<MaterialAsset> materials = new LinkedList<>();

		public MeshAsset(String name, boolean found, String error) {
			super(name, found, error);
		}

		public List<MaterialAsset> getMaterials() {
			return materials;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("super", super.toString()).add("materials", materials).toString();
		}

		@Override
		public void print(IndentPrintWriter writer) {
			writer.println("Mesh: " + getName());
			writer.indent();
			if (isFound()) {
				materials.forEach(m -> {
					writer.println();
					m.print(writer);
				});
			} else {
				writer.println("!!! " + getError() + " !!!");
			}
			writer.unindent();
		}
	}

	public static class MaterialAsset extends AbstractAsset {
		private int materialSwitch;
		private List<TextureAsset> textures = new LinkedList<>();

		public MaterialAsset(String name, int materialSwitch, boolean found, String error) {
			super(name, found, error);
			this.materialSwitch = materialSwitch;
		}

		public int getMaterialSwitch() {
			return materialSwitch;
		}

		public List<TextureAsset> getTextures() {
			return textures;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("super", super.toString()).add("materialSwitch", materialSwitch)
					.add("textures", textures).toString();
		}

		@Override
		public void print(IndentPrintWriter writer) {
			writer.println("Material: " + getName());
			writer.indent();
			writer.println("MaterialSwitch: " + getMaterialSwitch());
			if (isFound()) {
				textures.forEach(t -> {
					writer.println();
					t.print(writer);
				});
			} else {
				writer.println("!!! " + getError() + " !!!");
			}
			writer.unindent();
		}
	}

	public static class TextureAsset extends AbstractAsset {
		private String useType;
		private boolean switched;
		private String baseName;
		private int switchRepeat;

		public TextureAsset(String name, boolean found, String error) {
			super(name, found, error);
		}

		public TextureAsset(boolean switched, String baseName, int switchRepeat, String name, boolean found, String error) {
			super(name, found, error);
			this.switched = switched;
			this.baseName = baseName;
			this.switchRepeat = switchRepeat;
		}

		public boolean isSwitched() {
			return switched;
		}

		public void setSwitched(boolean switched) {
			this.switched = switched;
		}

		public String getBaseName() {
			return baseName;
		}

		public void setBaseName(String baseName) {
			this.baseName = baseName;
		}

		public int getSwitchRepeat() {
			return switchRepeat;
		}

		public void setSwitchRepeat(int switchRepeat) {
			this.switchRepeat = switchRepeat;
		}

		public String getUseType() {
			return useType;
		}

		public void setUsageType(String useType) {
			this.useType = useType;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("super", super.toString()).add("useType", useType).add("switched", switched)
					.add("baseName", baseName).add("switchRepeat", switchRepeat).toString();
		}

		@Override
		public void print(IndentPrintWriter writer) {
			writer.println("Textur: " + getName());
			writer.indent();
			writer.println("Verwendung: " + getUseType());
			if (switched) {
				writer.println("Basisname: " + getBaseName());
				writer.println("SwitchRepeat: " + G3Enums.asString(eEColorSrcSwitchRepeat.class, switchRepeat));
			}
			if (!isFound()) {
				writer.println("!!! " + getError() + " !!!");
			}
			writer.unindent();
		}
	}
}
