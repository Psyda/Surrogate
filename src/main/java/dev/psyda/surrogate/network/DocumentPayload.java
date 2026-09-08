package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to client: something to read. A letter on a table, a page on a screen, a date on a calendar.
 *
 * <p>{@code title} and {@code body} are translation keys; the body may contain newlines. {@code style} picks
 * how the client draws it: paper for anything printed, screen for anything lit.
 */
public record DocumentPayload(String title, String body, int style) implements CustomPayload {
	public static final int PAPER = 0;
	public static final int SCREEN = 1;

	public static final Id<DocumentPayload> ID = new Id<>(Surrogate.id("document"));
	public static final PacketCodec<ByteBuf, DocumentPayload> CODEC = PacketCodec.of((p, buf) -> {
		PacketCodecs.STRING.encode(buf, p.title);
		PacketCodecs.STRING.encode(buf, p.body);
		VarInts.write(buf, p.style);
	}, buf -> new DocumentPayload(PacketCodecs.STRING.decode(buf), PacketCodecs.STRING.decode(buf), VarInts.read(buf)));

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
