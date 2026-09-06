package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client to server: a choice made in the pilot menu. */
public record PilotActionPayload(int action) implements CustomPayload {
	public static final int DISCONNECT = 0;
	public static final int SHUTDOWN = 1;
	public static final int FABRICATOR = 2;

	public static final Id<PilotActionPayload> ID = new Id<>(Surrogate.id("pilot_action"));
	public static final PacketCodec<ByteBuf, PilotActionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, PilotActionPayload::action, PilotActionPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
