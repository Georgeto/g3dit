package de.george.g3dit.check.checks;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;

import de.george.g3dit.check.EntityDescriptor;
import de.george.g3dit.check.problem.ProblemConsumer;
import de.george.g3dit.check.problem.Severity;
import de.george.g3dit.util.HtmlCreator;
import de.george.lrentnode.archive.ArchiveFile;
import de.george.lrentnode.archive.eCEntity;
import de.george.lrentnode.classes.gCParty_PS;
import de.george.lrentnode.classes.desc.CD;

public class CheckInvalidParties extends AbstractEntityCheck {
	private static class PartyLeader {
		private final EntityDescriptor descriptor;
		private final Set<String> members;

		public PartyLeader(EntityDescriptor descriptor, Set<String> members) {
			this.descriptor = descriptor;
			this.members = members;
		}
	}

	private static class PartyMember {
		private final EntityDescriptor descriptor;
		private final String leader;

		public PartyMember(EntityDescriptor descriptor, String leader) {
			this.descriptor = descriptor;
			this.leader = leader;
		}
	}

	private Map<String, PartyLeader> leaders = new HashMap<>();
	private Map<String, PartyMember> members = new HashMap<>();
	private Set<String> guids = new HashSet<>();

	public CheckInvalidParties() {
		super("Ungültige Gruppen ermitteln", "Ermittelt Inkosistenzen in der Zuordnung von PartyMembern zu PartyLeadern.", 0, 1);
	}

	@Override
	protected EntityPassStatus processEntity(ArchiveFile archiveFile, File dataFile, eCEntity entity, int entityPosition, int pass,
			Supplier<EntityDescriptor> descriptor, StringProblemConsumer problemConsumer) {

		guids.add(entity.getGuid());

		if (entity.hasClass(CD.gCParty_PS.class)) {
			gCParty_PS party = entity.getClass(CD.gCParty_PS.class);

			String leader = party.property(CD.gCParty_PS.PartyLeaderEntity).getGuid();
			if (leader != null && !party.members.getEntries().isEmpty()) {
				problemConsumer.warning("Entity ist sowohl PartyLeader als auch PartyMember.");
			}

			if (!party.members.getEntries().isEmpty()) {
				leaders.put(entity.getGuid(), new PartyLeader(descriptor.get(), ImmutableSet.copyOf(party.members.getNativeEntries())));
			}

			if (leader != null) {
				members.put(entity.getGuid(), new PartyMember(descriptor.get(), leader));
			}
		}

		return EntityPassStatus.Next;
	}

	@Override
	public void reportProblems(ProblemConsumer problemConsumer) {
		for (PartyLeader leader : leaders.values()) {
			for (String memberGuid : leader.members) {
				PartyMember member = members.get(memberGuid);
				if (member != null) {
					if (member.leader == null) {
						postEntityProblem(problemConsumer, leader.descriptor, Severity.Fatal,
								"PartyLeader verweist auf PartyMember, der keinen PartyLeader eingetragen hat.",
								HtmlCreator.renderEntity(member.descriptor));
					} else if (!leader.descriptor.getGuid().equals(member.leader)) {
						postEntityProblem(problemConsumer, leader.descriptor, Severity.Fatal,
								"PartyLeader verweist auf PartyMember, der abweichenden PartyLeader eingetragen hat.",
								HtmlCreator.renderEntity(member.descriptor));
					}
				} else {
					if (guids.contains(memberGuid)) {
						postEntityProblem(problemConsumer, leader.descriptor, Severity.Fatal,
								"PartyLeader verweist auf Entity, die kein PartyMember ist.", memberGuid);
					} else {
						postEntityProblem(problemConsumer, leader.descriptor, Severity.Fatal,
								"PartyLeader verweist auf nicht existente Entity.", memberGuid);
					}
				}
			}
		}

		for (PartyMember member : members.values()) {
			if (member.leader == null) {
				continue;
			}

			PartyLeader leader = leaders.get(member.leader);
			if (leader != null) {
				if (!leader.members.contains(member.descriptor.getGuid())) {
					postEntityProblem(problemConsumer, member.descriptor, Severity.Fatal,
							"PartyMember verweist auf PartyLeader, der diesen nicht eingetragen hat.",
							HtmlCreator.renderEntity(leader.descriptor));
				}
			} else {
				if (guids.contains(member.leader)) {
					postEntityProblem(problemConsumer, member.descriptor, Severity.Fatal,
							"PartyMember verweist auf Entity, die kein PartyLeader ist.", member.leader);
				} else {
					postEntityProblem(problemConsumer, member.descriptor, Severity.Fatal,
							"PartyMember verweist auf nicht existente Entity.", member.leader);
				}
			}
		}
	}

	@Override
	public void reset() {
		leaders.clear();
		members.clear();
		guids.clear();
	}
}
