package de.george.g3utils.io;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import de.george.g3utils.structure.Stringtable;

public abstract class GenomeFile implements Saveable {

	protected Stringtable stringtable;

	public Stringtable getStringtable() {
		return stringtable;
	}

	protected final void read(G3FileReaderEx reader) throws IOException {
		if (reader.getSize() < 8 || !reader.read(8).equals("47454E4F4D464C45")) {
			throw new IOException("'" + reader.getFileName() + "' ist keine gültige Genome-Datei.");
		}

		reader.skip(2); // Skip Version
		int deadbeefOffset = reader.readInt();

		reader.readStringtable(deadbeefOffset + 4);
		stringtable = reader.getStringtable();

		readInternal(reader);
	}

	protected final void write(G3FileWriterEx writer) throws IOException {
		writer.write("47454E4F4D464C450100FFFFFFFF");

		writeInternal(writer);

		writer.write("EFBEADDE");
		int deadbeef = writer.getSize() - 4;
		writer.replaceInt(deadbeef, 10); // DEADBEEF Platzhalter mit dem richtigen Wert ersetzen

		writeInternalAfterDeadbeef(writer, deadbeef);

		// Stringtable schreiben
		writer.writeStringtable();
	}

	protected abstract void readInternal(G3FileReaderEx reader) throws IOException;

	protected abstract void writeInternal(G3FileWriterEx writer) throws IOException;

	protected void writeInternalAfterDeadbeef(G3FileWriterEx writer, int deadbeef) {

	};

	private G3FileWriterEx prepareSave() throws IOException {
		G3FileWriterEx writer = new G3FileWriterEx("");
		if (stringtable == null) {
			stringtable = new Stringtable();
		}
		writer.setStringtable(stringtable);
		write(writer);
		return writer;
	}

	@Override
	public void save(File file) throws IOException {
		prepareSave().save(file);
	}

	@Override
	public void save(OutputStream out) throws IOException {
		prepareSave().save(out);
	}

	public static boolean isGenomeFile(G3FileReader reader) {
		return reader.getSize() >= 8 && reader.readSilent(8).equals("47454E4F4D464C45");
	}
}
