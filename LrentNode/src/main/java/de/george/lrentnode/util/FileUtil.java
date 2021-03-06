package de.george.lrentnode.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.george.g3utils.io.G3FileReaderEx;
import de.george.g3utils.io.G3FileReaderVirtual;
import de.george.g3utils.io.G3FileWriterVirtual;
import de.george.g3utils.io.GenomeFile;
import de.george.g3utils.structure.GuidUtil;
import de.george.g3utils.structure.bCBox;
import de.george.g3utils.util.IOUtils;
import de.george.g3utils.util.Misc;
import de.george.lrentnode.archive.ArchiveFile;
import de.george.lrentnode.archive.OneClassGenomeFile;
import de.george.lrentnode.archive.SecDat;
import de.george.lrentnode.archive.animation.eCResourceAnimationActor_PS;
import de.george.lrentnode.archive.animation.eCResourceAnimationMotion_PS;
import de.george.lrentnode.archive.lrentdat.LrentdatFile;
import de.george.lrentnode.archive.node.NodeFile;
import de.george.lrentnode.classes.G3Class;
import de.george.lrentnode.classes.eCGeometrySpatialContext;
import de.george.lrentnode.classes.eCResourceCollisionMesh_PS;
import de.george.lrentnode.classes.eCResourceMeshComplex_PS;
import de.george.lrentnode.classes.eCResourceMeshLoD_PS;
import de.george.lrentnode.classes.eCResourceShaderMaterial_PS;
import de.george.lrentnode.classes.desc.CD;
import de.george.lrentnode.template.TemplateFile;

public class FileUtil {
	private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

	public static ArchiveFile openArchive(File file, boolean verifyEntityGraph) throws IOException {
		return openArchive(file, verifyEntityGraph, false);
	}

	public static ArchiveFile openArchive(File file, boolean verifyEntityGraph, boolean skipPropertySets) throws IOException {
		try (G3FileReaderEx reader = new G3FileReaderEx(file)) {
			return openArchive(reader, verifyEntityGraph, skipPropertySets);
		}
	}

	public static ArchiveFile openArchive(G3FileReaderEx reader, boolean verifyEntityGraph) throws IOException {
		return openArchive(reader, verifyEntityGraph, false);
	}

	public static ArchiveFile openArchive(G3FileReaderEx reader, boolean verifyEntityGraph, boolean skipPropertySets) throws IOException {
		reader.seek(0);
		if (!GenomeFile.isGenomeFile(reader) || reader.getSize() < 100) {
			throw new IOException("'" + reader.getFileName() + "' ist keine gültige .lrentdat/.node Datei.");
		}

		return reader.readSilent(14, 8).equals("47454E4F4D45444C") ? new LrentdatFile(reader, verifyEntityGraph, skipPropertySets)
				: new NodeFile(reader, verifyEntityGraph, skipPropertySets);
	}

	public static Optional<ArchiveFile> openArchiveSafe(File file, boolean verifyEntityGraph, boolean skipPropertySets) {
		try {
			return Optional.of(openArchive(file, verifyEntityGraph, skipPropertySets));
		} catch (Exception e) {
			logger.warn("Unable to open archive file {}.", file.getAbsolutePath(), e);
			return Optional.empty();
		}
	}

	public static SecDat openSecdat(File file) throws IOException {
		try (G3FileReaderEx reader = new G3FileReaderEx(file)) {
			return openSecdat(reader);
		}
	}

	public static SecDat openSecdat(G3FileReaderEx reader) throws IOException {
		return new SecDat(reader);
	}

	public static TemplateFile openTemplate(String file) throws IOException {
		return openTemplate(new File(file));
	}

	public static TemplateFile openTemplate(File file) throws IOException {
		try (G3FileReaderEx reader = new G3FileReaderEx(file)) {
			return openTemplate(reader);
		}
	}

	public static TemplateFile openTemplate(G3FileReaderEx reader) throws IOException {
		return new TemplateFile(reader);
	}

	public static Optional<TemplateFile> openTemplateSafe(File file) {
		try {
			return Optional.of(openTemplate(file));
		} catch (Exception e) {
			logger.warn("Unable to open template {}.", file.getAbsolutePath(), e);
			return Optional.empty();
		}
	}

	public static ArchiveFile createEmptyLrentdat() {
		return createArchive(
				"47454E4F4D464C450100CF01000047454E4F4D45444C5300010001010001000001000053005300A40100001E0002000000010002001E0014000000696E2F446174612F50726F6A656374730047335F030004001E00180000000000000000000000000000000000000000000000000000005300000000803F0000803F000000000000000000000000000000000000000000000000010000004000530000530001000000000000000000000000000000000000000000010100010001010000803F00000000000005000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F0000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000000000000000FFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFFFFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFF00000000000000000000000000000000FFFF7FFF0000000000000000000000000000803F000000803FFFFFFFFF0000803F000000000000FFFFFFFFFFFFFFFFEFBEADDE010600000016006543456E7469747944796E616D6963436F6E746578740200494406006243477569640A00436F6E74657874426F7805006243426F780A00526F6F74456E74697479");
	}

	public static ArchiveFile createEmptyNode() {
		return createArchive(
				"47454E4F4D464C4501009F0100005300010000000000230000FFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFF000000000000000000000000FFFF7FFFFFFF7FFFFFFF7FFF000000000000000000000000000000000000000000000000000000000000000000000000530001000000000000000000000000000000000000000000010100010001010000803F00000000000000000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F0000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F000000000000000000000000000000000000803F3E442AC64160A345C65B13468CDEC545F0133346060BCE46FFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFFFFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFF4DF54246F0A90EC50862024674DC8B46FFFF7FFF0000000000000000000000000000803F000000803FFFFFFFFF0000803F000000000000FFFFFFFFFFFFFFFFEFBEADDE01010000000400526F6F74");
	}

	public static ArchiveFile createArchive(String fileData) {
		G3FileReaderEx reader = new G3FileReaderEx(fileData);
		ArchiveFile file = null;
		try {
			file = openArchive(reader, false);
		} catch (Exception e) {
			logger.error("Fehler beim Erstellen der Archivedatei", e);
			return null;
		}
		file.getGraph().setGuid(GuidUtil.randomGUID());
		return file;
	}

	public static TemplateFile openTemplateByName(List<File> tples, String templateName) {
		for (File file : tples) {
			try {
				if (file.getName().contains(templateName) && !file.getName().startsWith("_deleted")
						&& !file.getName().startsWith("Testzeug_")) {
					TemplateFile tple = openTemplate(file);
					if (tple.getHeaderCount() > 0 && tple.getItemHeader().getName().equals(templateName)) {
						return tple;
					}
				}
			} catch (Exception e) {
				logger.info("Fehler beim Öffnen der Template {}", file.getAbsolutePath(), e);
			}
		}
		return null;
	}

	private static final byte[] HEX_LRENT = Misc.asByte(
			"47454E4F4D464C4501004E0000000100010100010000010000530053002D0000001E0003000000010002001E0006000000010000000000030004001E0000000000050006001E0001000000010100EFBEADDE01070000000E00674344796E616D69634C617965720A00456E74697479547970652600625450726F7065727479436F6E7461696E65723C656E756D206745456E74697479547970653E0900536563746F725074721C006254504F536D6172745074723C636C617373206743536563746F723E0D0049735065727369737461626C650400626F6F6C");

	/**
	 * Erstellt eine *.lrent Datei (gCDynamicLayer) an der Stelle {@code file}.
	 *
	 * @param file Speicherort
	 * @throws IOException
	 */
	public static void createLrent(File file) throws IOException {
		createFile(file, HEX_LRENT, "lrent");
	}

	private static final byte[] HEX_SEC = Misc.asByte(
			"47454E4F4D464C450100900000000100010100010000010000530053006F0000001E0009000000010002001E0000000000030004001E0000000000050004001E0000000000060007001E000100000000080007001E000100000000090007001E0001000000000A000B001E00040000000000803F0C000B001E00040000000000803F0D0007001E0001000000011B0001EFBEADDE010E00000008006743536563746F720800576F726C645074721B006254504F536D6172745074723C636C617373206743576F726C643E0E0047656F6D657472794C61796572732B00625452656650747241727261793C636C61737320624350726F70657274794F626A65637442617365202A3E0C00456E746974794C617965727306004D61726B65640400626F6F6C0700467265657A656406004C6F636B65640F0056697375616C4C6F44466163746F720500666C6F617410004F626A65637443756C6C466163746F720D0049735065727369737461626C65");

	/**
	 * Erstellt eine *.sec Datei (gCSector) an der Stelle {@code file}.
	 *
	 * @param file Speicherort
	 * @throws IOException
	 */
	public static void createSec(File file) throws IOException {
		createFile(file, HEX_SEC, "sec");
	}

	private static final byte[] HEX_LRGEO = Misc.asByte(
			"47454E4F4D464C4501005A000000010001010001000001000053005300390000001E0004000000010002001E00020000000300040005001E0006000000010000000000060007001E0000000000080009001E0001000000010100EFBEADDE010A0000000F00674347656F6D657472794C6179657210004F726967696E496D706F72744E616D6508006243537472696E6700000C0047656F6D65747279547970652800625450726F7065727479436F6E7461696E65723C656E756D20674547656F6D65747279547970653E0900536563746F725074721C006254504F536D6172745074723C636C617373206743536563746F723E0D0049735065727369737461626C650400626F6F6C");

	/**
	 * Erstellt eine *.lrgeo Datei (gCGeometryLayer) an der Stelle {@code file}.
	 *
	 * @param file Speicherort
	 * @throws IOException
	 */
	public static void createLrgeo(File file) throws IOException {
		createFile(file, HEX_LRGEO, "lrgeo");
	}

	private static final byte[] HEX_LRGEODAT = Misc.asByte(
			"47454E4F4D464C45010083000000010001010001000001000053005300620000001E0002000000010002001E0014000000435350476C7267656F6461743133333700000000030004001E0018000000FFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFF53000101FFFF7F7FFFFF7F7FFFFF7F7FFFFF7FFFFFFF7FFFFFFF7FFFEFBEADDE01050000001800654347656F6D657472795370617469616C436F6E746578740200494406006243477569640A00436F6E74657874426F7805006243426F78");

	public static eCGeometrySpatialContext createLrgeodat() throws IOException {
		try (InputStream is = new ByteArrayInputStream(HEX_LRGEODAT)) {
			return openLrgeodat(is);
		}
	}

	/**
	 * Erstellt eine *.lrgeodat Datei an der Stelle {@code file}.
	 *
	 * @param file Speicherort
	 * @param contentBox WorldTreeBoundary der zugehörigen node
	 * @throws IOException
	 */
	public static void createLrgeodat(File file, bCBox contentBox) throws IOException {
		eCGeometrySpatialContext lrgeodat = FileUtil.createLrgeodat();
		lrgeodat.setPropertyData(CD.eCContextBase.ContextBox, contentBox);
		FileUtil.saveLrgeodat(lrgeodat, IOUtils.changeExtension(file, "lrgeodat"));
	}

	public static eCGeometrySpatialContext openLrgeodat(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openLrgeodat(is);
		}
	}

	public static eCGeometrySpatialContext openLrgeodat(InputStream is) throws IOException {
		return openOneClassGenomeFile(is);
	}

	public static void saveLrgeodat(eCGeometrySpatialContext context, File file) throws IOException {
		saveOneClassGenomeFile(context, file);
	}

	private static final byte[] HEX_LRTPL = Misc.asByte(
			"47454E4F4D464C4501003E0000000100010100010000010000530053001D0000001E0002000000010002001E0000000000030004001E0001000000010100EFBEADDE01050000000F00674354656D706C6174654C617965720900536563746F725074721C006254504F536D6172745074723C636C617373206743536563746F723E0D0049735065727369737461626C650400626F6F6C");

	/**
	 * Erstellt eine *.lrtpl Datei (gCTemplateLayer) an der Stelle {@code file}.
	 *
	 * @param file Speicherort
	 * @throws IOException
	 */
	public static void createLrtpl(File file) throws IOException {
		createFile(file, HEX_LRTPL, "lrtpl");
	}

	private static final byte[] HEX_LRTPLDAT = Misc.asByte(
			"47454E4F4D464C45010069000000010001010001000001000053005300480000001E0002000000010002001E00140000000000000000000000000000000000000000000000030004001E00180000000000000000000000000000000000000000000000000000003800EFBEADDE010500000017006543456E7469747954656D706C617465436F6E746578740200494406006243477569640A00436F6E74657874426F7805006243426F78");

	/**
	 * Erstellt eine *.lrtpldat Datei (eCEntityTemplateContext) an der Stelle {@code file}.
	 *
	 * @param file Speicherort
	 * @throws IOException
	 */
	public static void createLrtpldat(File file) throws IOException {
		createFile(file, HEX_LRTPLDAT, "lrtpldat");
	}

	private static void createFile(File file, byte[] data, String extension) throws IOException {
		new G3FileWriterVirtual(data).save(IOUtils.changeExtension(file, extension));
	}

	public static eCResourceMeshComplex_PS openMesh(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openMesh(is);
		}
	}

	public static eCResourceMeshComplex_PS openMesh(InputStream is) throws IOException {
		return openOneClassGenomeFile(is);
	}

	public static eCResourceShaderMaterial_PS openMaterial(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openMaterial(is);
		}
	}

	public static eCResourceShaderMaterial_PS openMaterial(InputStream is) throws IOException {
		return openOneClassGenomeFile(is);
	}

	public static eCResourceMeshLoD_PS openLodMesh(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openLodMesh(is);
		}
	}

	public static eCResourceMeshLoD_PS openLodMesh(InputStream is) throws IOException {
		return openOneClassGenomeFile(is);
	}

	public static eCResourceCollisionMesh_PS openCollisionMesh(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openCollisionMesh(is);
		}
	}

	public static eCResourceCollisionMesh_PS openCollisionMesh(InputStream is) throws IOException {
		return openOneClassGenomeFile(is);
	}

	@SuppressWarnings("unchecked")
	private static <T extends G3Class> T openOneClassGenomeFile(InputStream is) throws IOException {
		G3FileReaderEx reader = new G3FileReaderEx(is);
		if (GenomeFile.isGenomeFile(reader)) {
			OneClassGenomeFile file = new OneClassGenomeFile(reader);
			return file.getContainedClass();
		} else {
			G3FileReaderVirtual virtualReader = new G3FileReaderVirtual(reader.getData());
			return (T) ClassUtil.readSubClass(virtualReader);
		}
	}

	private static <T extends G3Class> void saveOneClassGenomeFile(T data, File file) throws IOException {
		new OneClassGenomeFile(data).save(file);
	}

	public static eCResourceAnimationActor_PS openAnimationActor(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openAnimationActor(is);
		}
	}

	public static eCResourceAnimationActor_PS openAnimationActor(InputStream is) throws IOException {
		return new eCResourceAnimationActor_PS(new G3FileReaderEx(is));
	}

	public static eCResourceAnimationMotion_PS openAnimationMotion(File file) throws IOException {
		try (InputStream is = new FileInputStream(file)) {
			return openAnimationMotion(is);
		}
	}

	public static eCResourceAnimationMotion_PS openAnimationMotion(InputStream is) throws IOException {
		return new eCResourceAnimationMotion_PS(new G3FileReaderEx(is));
	}
}
