package de.george.lrentnode.classes;

import de.george.g3utils.io.G3FileReader;
import de.george.g3utils.io.G3FileWriter;
import de.george.lrentnode.properties.bTObjArray_eCEntityProxy;

public class gCParty_PS extends G3Class {
	public bTObjArray_eCEntityProxy members;

	public gCParty_PS(String className, G3FileReader reader) {
		super(className, reader);
	}

	@Override
	protected void readPostClassVersion(G3FileReader reader) {
		members = reader.read(bTObjArray_eCEntityProxy.class);
	}

	@Override
	protected void writePostClassVersion(G3FileWriter writer) {
		writer.write(members);
	}

	public bTObjArray_eCEntityProxy getMembers() {
		return members;
	}

	public void setMembers(bTObjArray_eCEntityProxy members) {
		this.members = members;
	}
}
