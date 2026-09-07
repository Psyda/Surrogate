package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The mission board: everyone still out there, what state they are in, what their shelter's air is doing,
 * and the one thing standing between them and a seat.
 *
 * <p>It is sent just before the terminal opens rather than kept live on the client, because it is a page in
 * a filing system and not a HUD. An empty row list means this hub has no board on it, which is every
 * terminal in the game until the conference in act four happens.
 */
public record MissionPayload(List<Row> rows, List<String> notes) implements CustomPayload {
	public static final Id<MissionPayload> ID = new Id<>(Surrogate.id("mission_board"));

	/** Nobody has reached them and nobody has heard anything that changes that. */
	public static final int UNREACHED = 0;
	/** Reached: somebody has stood at their port and given them what they asked for. */
	public static final int REACHED = 1;
	/** Riding in a cabin right now. */
	public static final int ABOARD = 2;
	/** Home, at the pod, with a wall around them. */
	public static final int HOME = 3;

	/**
	 * One person on the board.
	 *
	 * @param survivor the {@link dev.psyda.surrogate.survivor.Survivor} ordinal
	 * @param state    one of the four constants above
	 * @param scrubber what is left of their shelter's scrubber, as a percentage
	 * @param blocked  a translation key naming what is in the way, or empty when nothing is
	 */
	public record Row(int survivor, int state, int scrubber, String blocked) {
		static void write(Row row, ByteBuf buf) {
			VarInts.write(buf, row.survivor);
			VarInts.write(buf, row.state);
			VarInts.write(buf, row.scrubber);
			PacketCodecs.STRING.encode(buf, row.blocked);
		}

		static Row read(ByteBuf buf) {
			return new Row(VarInts.read(buf), VarInts.read(buf), VarInts.read(buf), PacketCodecs.STRING.decode(buf));
		}
	}

	/** The board is a fixed roster and a handful of notes; nothing legitimate is anywhere near this. */
	private static final int CAP = 64;

	public static final PacketCodec<ByteBuf, MissionPayload> CODEC = PacketCodec.of((p, buf) -> {
		VarInts.write(buf, p.rows.size());
		for (Row row : p.rows) Row.write(row, buf);
		VarInts.write(buf, p.notes.size());
		for (String note : p.notes) PacketCodecs.STRING.encode(buf, note);
	}, buf -> {
		// A length off the wire is not a length until it has been looked at. Read straight into an
		// ArrayList it is an allocation somebody else chose.
		int count = bounded(VarInts.read(buf));
		List<Row> rows = new ArrayList<>(count);
		for (int i = 0; i < count; i++) rows.add(Row.read(buf));
		int noteCount = bounded(VarInts.read(buf));
		List<String> notes = new ArrayList<>(noteCount);
		for (int i = 0; i < noteCount; i++) notes.add(PacketCodecs.STRING.decode(buf));
		return new MissionPayload(rows, notes);
	});

	private static int bounded(int count) {
		if (count < 0 || count > CAP) throw new IllegalArgumentException("Mission board length out of range: " + count);
		return count;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
