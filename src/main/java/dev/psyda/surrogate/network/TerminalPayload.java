package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server to client: open the terminal screen for {@code unit}. */
public record TerminalPayload(String unit) implements CustomPayload {
	public static final Id<TerminalPayload> ID = new Id<>(Surrogate.id("terminal_open"));
	public static final PacketCodec<ByteBuf, TerminalPayload> CODEC = PacketCodec.of((p, buf) -> PacketCodecs.STRING.encode(buf, p.unit),
			buf -> new TerminalPayload(PacketCodecs.STRING.decode(buf)));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
