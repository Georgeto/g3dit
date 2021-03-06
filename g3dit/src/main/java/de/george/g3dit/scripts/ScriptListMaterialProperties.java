package de.george.g3dit.scripts;

import java.io.File;
import java.io.FileInputStream;

import de.george.g3dit.util.FileManager;
import de.george.lrentnode.classes.eCResourceShaderMaterial_PS;
import de.george.lrentnode.classes.eCShaderBase;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.enums.G3Enums;
import de.george.lrentnode.enums.G3Enums.eEShaderMaterialBlendMode;
import de.george.lrentnode.util.FileUtil;

public class ScriptListMaterialProperties implements IScript {

	@Override
	public String getTitle() {
		return "Material Eigenschaften auflisten";
	}

	@Override
	public String getDescription() {
		return "Material Eigenschaften auflisten";
	}

	@Override
	public boolean execute(IScriptEnvironment env) {
		for (File file : env.getFileManager().listFiles(FileManager.RP_COMPILED_MATERIAL, (file) -> file.getName().endsWith(".xshmat"))) {
			try (FileInputStream is = new FileInputStream(file)) {
				eCResourceShaderMaterial_PS material = FileUtil.openMaterial(is);
				eCShaderBase shader = material.getShader();
				int blendMode = shader.property(CD.eCShaderBase.BlendMode).getEnumValue();
				int maskReference = shader.property(CD.eCShaderBase.MaskReference).getChar() & 0xFF;
				String useDethBias = shader.hasProperty(CD.eCShaderBase.UseDepthBias)
						? String.valueOf(shader.property(CD.eCShaderBase.UseDepthBias).isBool())
						: "-";
				env.log(file.getName() + ": " + G3Enums.asString(eEShaderMaterialBlendMode.class, blendMode) + ", " + maskReference + ", "
						+ useDethBias);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return true;
	}

}
