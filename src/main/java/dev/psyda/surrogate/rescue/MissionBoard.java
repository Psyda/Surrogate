package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.network.MissionPayload;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The page act four leaves behind: every person still out there, what their air is doing, and the one thing
 * standing between them and a seat.
 *
 * <p>It is worked out fresh each time the hub is opened rather than kept anywhere, because every fact on it
 * is already saved somewhere better — who is home is the survivor manager's, what is bridged is the rescue
 * state's — and a board with its own copy of those would be a second answer to the same question.
 */
public final class MissionBoard {
	private MissionBoard() {
	}

	public static void send(ServerPlayerEntity player) {
		MinecraftServer server = player.server;
		ServerPlayNetworking.send(player, new MissionPayload(rows(server), notes(server)));
	}

	/** Clears whatever board the client is holding, so it does not appear on somebody else's screen. */
	public static void clear(ServerPlayerEntity player) {
		ServerPlayNetworking.send(player, new MissionPayload(List.of(), List.of()));
	}

	public static List<MissionPayload.Row> rows(MinecraftServer server) {
		SurvivorManager survivors = SurvivorManager.get(server);
		RescueState rescue = RescueState.get(server);
		List<MissionPayload.Row> rows = new ArrayList<>();
		boolean crossed = crossed(rescue, survivors);
		for (SurvivorManager.Site site : survivors.sites()) {
			Survivor who = site.survivor();
			int state = site.rescued ? MissionPayload.HOME
					: site.aboard ? MissionPayload.ABOARD
					: site.blueprint ? MissionPayload.REACHED
					: MissionPayload.UNREACHED;
			// Novak has no shelter and therefore no scrubber. A reading of nought percent next to his name
			// would say he was suffocating, which he is not; he is cold, and that is a different report.
			int scrubber = site.wreck ? -1 : (int) Math.round(site.scrubber * 100.0);
			rows.add(new MissionPayload.Row(who.ordinal(), state, scrubber, blocked(rescue, survivors, site, crossed)));
		}
		return rows;
	}

	/**
	 * Whether the far side is reachable. A finished span says so, and so does anybody actually standing at a
	 * far-side port: four hundred blocks of plating laid by hand crosses the same gap, and a board that went
	 * on saying "the Rift" to somebody who was demonstrably on the other side of it would be lying.
	 */
	private static boolean crossed(RescueState rescue, SurvivorManager survivors) {
		if (rescue.spanDone) return true;
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.gated() && (site.blueprint || site.aboard || site.rescued)) return true;
		}
		return false;
	}

	/** What is in the way of this one, in a phrase, or empty when the answer is "go and get them". */
	private static String blocked(RescueState rescue, SurvivorManager survivors, SurvivorManager.Site site, boolean crossed) {
		if (site.rescued) return "";
		if (site.aboard) return "board.surrogate.block.riding";
		if (!site.gated()) return site.built ? "board.surrogate.block.collar" : "board.surrogate.block.unfound";
		// Everything past this point is on the far side of the Rift, in the order the world puts it in.
		if (!crossed) return "board.surrogate.block.rift";
		Survivor who = site.survivor();
		if (who == Survivor.BRANDT) {
			if (rescue.flow != null && !rescue.flowCut) return "board.surrogate.block.flow";
			return "board.surrogate.block.belt";
		}
		if (who == Survivor.REYES) {
			return rescue.airlock ? "board.surrogate.block.collar" : "board.surrogate.block.airlock";
		}
		SurvivorManager.Site reyes = Rescue.site(survivors, Survivor.REYES);
		boolean doctor = reyes != null && (reyes.aboard || reyes.rescued);
		return doctor ? "board.surrogate.block.carry" : "board.surrogate.block.doctor";
	}

	/** The state of the act itself, under the names: what is built, what is cut, what is still hot. */
	public static List<String> notes(MinecraftServer server) {
		RescueState rescue = RescueState.get(server);
		List<String> notes = new ArrayList<>();
		notes.add(rescue.spanDone ? "board.surrogate.note.span_done"
				: rescue.anchor == null ? "board.surrogate.note.span_none"
				: "board.surrogate.note.span_part");
		if (rescue.flow != null) notes.add(rescue.flowCut ? "board.surrogate.note.flow_cut" : "board.surrogate.note.flow");
		if (!rescue.airlock) notes.add("board.surrogate.note.airlock");
		if (rescue.stage >= RescueState.STAGE_DONE) notes.add("board.surrogate.note.done");
		return notes;
	}

	/** A one-line summary for the dev command and the headless tests. */
	public static String summary(MinecraftServer server) {
		RescueState rescue = RescueState.get(server);
		SurvivorManager survivors = SurvivorManager.get(server);
		int home = 0;
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.rescued) home++;
		}
		BlockPos crossing = rescue.crossing;
		return "stage " + rescue.stage
				+ (rescue.called ? ", called" : ", not called")
				+ ", span " + rescue.spanCourses + "/" + (rescue.spanLength <= 0 ? 0 : SpanBuilder.courses(rescue.spanLength))
				+ (rescue.spanDone ? " (done)" : "")
				+ ", flow " + (rescue.flow == null ? "unlaid" : rescue.flowCut ? "cut" : "running")
				+ (rescue.airlock ? ", airlock plated" : ", airlock open")
				+ (rescue.novakLifted ? ", novak lifted" : "")
				+ ", home " + home + "/" + survivors.sites().size()
				+ ", crossing " + (crossing == null ? "unknown" : crossing.getX() + "," + crossing.getZ());
	}
}
