package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to client: pilot body state plus the timing constants the HUD needs.
 *
 * @param toxin      body toxin load in tenths of a percent (0 to 1000)
 * @param airState   what the body is breathing, see {@link dev.psyda.surrogate.atmosphere.Exposure}
 * @param airQuality air quality of the enclosure the body is in, 0 to 100
 */
public record PilotStatusPayload(int fatigue, int maxFatigue, int unconscious, int bootTicks, int shutdownTicks, int idleDrainPerTick,
								 int toxin, int airState, int airQuality) implements CustomPayload {
	public static final Id<PilotStatusPayload> ID = new Id<>(Surrogate.id("pilot_status"));
	public static final PacketCodec<ByteBuf, PilotStatusPayload> CODEC = PacketCodec.of((payload, buf) -> payload.write(buf), PilotStatusPayload::read);

	private void write(ByteBuf buf) {
		VarInts.write(buf, fatigue);
		VarInts.write(buf, maxFatigue);
		VarInts.write(buf, unconscious);
		VarInts.write(buf, bootTicks);
		VarInts.write(buf, shutdownTicks);
		VarInts.write(buf, idleDrainPerTick);
		VarInts.write(buf, toxin);
		VarInts.write(buf, airState);
		VarInts.write(buf, airQuality);
	}

	private static PilotStatusPayload read(ByteBuf buf) {
		return new PilotStatusPayload(VarInts.read(buf), VarInts.read(buf), VarInts.read(buf), VarInts.read(buf), VarInts.read(buf),
				VarInts.read(buf), VarInts.read(buf), VarInts.read(buf), VarInts.read(buf));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
