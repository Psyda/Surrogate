package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to client: what the sky and the rock are doing where this player is. Sent every second, and again
 * the moment a storm's run-up or the belt's rain flips, so the HUD never lags the warning.
 *
 * @param storm     magnetic storm intensity, 0 to 1; 0 when there is no storm
 * @param warning   true through the run-up only, while there is still time to park
 * @param acidRain  true when it is raining on this player's chassis, not merely somewhere in the belt
 * @param seismic   how close the nearest borer has got, 0 to 1
 * @param linkNoise how far gone the uplink picture is, 0 to 1
 */
public record HazardPayload(float storm, boolean warning, boolean acidRain, float seismic, float linkNoise) implements CustomPayload {
	public static final Id<HazardPayload> ID = new Id<>(Surrogate.id("hazard"));
	public static final PacketCodec<ByteBuf, HazardPayload> CODEC = PacketCodec.of((p, buf) -> {
		buf.writeFloat(p.storm);
		buf.writeBoolean(p.warning);
		buf.writeBoolean(p.acidRain);
		buf.writeFloat(p.seismic);
		buf.writeFloat(p.linkNoise);
	}, buf -> new HazardPayload(buf.readFloat(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readFloat()));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
