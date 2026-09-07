package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server to client: open the terminal screen for {@code unit}. */
public record TerminalPayload(String unit, int survey) implements CustomPayload {
	public static final Id<TerminalPayload> ID = new Id<>(Surrogate.id("terminal_open"));
	/**
	 * {@code survey} is the bitmask of specimens Okafor's software has a file on, or -1 when the disk has
	 * not been installed in this hub. It rides along with the unit name because the survey page is part of
	 * the terminal and asking for it separately would mean a second round trip for one integer.
	 */
	public static final PacketCodec<ByteBuf, TerminalPayload> CODEC = PacketCodec.of(
			(p, buf) -> {
				PacketCodecs.STRING.encode(buf, p.unit);
				PacketCodecs.INTEGER.encode(buf, p.survey);
			},
			buf -> new TerminalPayload(PacketCodecs.STRING.decode(buf), PacketCodecs.INTEGER.decode(buf)));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
