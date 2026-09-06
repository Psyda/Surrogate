package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to client: where the ship is in its week. The client extrapolates the clock from {@code hours} and
 * {@code hoursPerTick}, draws the sky from the day and the turnover, and shows the rest on the HUD.
 *
 * @param aboard       whether the player is on the ship at all; false clears everything
 * @param day          transit day, 1 to 7; 8 once the drop has begun
 * @param hours        ship time when this was sent, in hours since the start of the day
 * @param hoursPerTick how fast the clock runs
 * @param flipStart    world time the turnover began, or -1 if it has not
 * @param flipTicks    how long the turnover takes
 * @param gravity      whether the main engine is giving the ship its weight
 * @param lights       0 day, 1 night, 2 alarm
 * @param alarm        whether the klaxon is sounding
 * @param engine       whether the main engine is burning
 * @param phase        0 in transit, 1 on the way down
 * @param breach       whether the hold is open to space (the klaxon without this is somebody at the button)
 */
public record TransitPayload(boolean aboard, int day, float hours, float hoursPerTick, long flipStart, int flipTicks,
							 boolean gravity, int lights, boolean alarm, boolean engine, int phase, boolean breach) implements CustomPayload {
	public static final Id<TransitPayload> ID = new Id<>(Surrogate.id("transit"));
	public static final PacketCodec<ByteBuf, TransitPayload> CODEC = PacketCodec.of((p, buf) -> {
		buf.writeBoolean(p.aboard);
		VarInts.write(buf, p.day);
		buf.writeFloat(p.hours);
		buf.writeFloat(p.hoursPerTick);
		buf.writeLong(p.flipStart);
		VarInts.write(buf, p.flipTicks);
		buf.writeBoolean(p.gravity);
		VarInts.write(buf, p.lights);
		buf.writeBoolean(p.alarm);
		buf.writeBoolean(p.engine);
		VarInts.write(buf, p.phase);
		buf.writeBoolean(p.breach);
	}, buf -> new TransitPayload(buf.readBoolean(), VarInts.read(buf), buf.readFloat(), buf.readFloat(), buf.readLong(), VarInts.read(buf),
			buf.readBoolean(), VarInts.read(buf), buf.readBoolean(), buf.readBoolean(), VarInts.read(buf), buf.readBoolean()));

	public static TransitPayload none() {
		return new TransitPayload(false, 0, 0f, 0f, -1L, 0, true, 0, false, false, 0, false);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
