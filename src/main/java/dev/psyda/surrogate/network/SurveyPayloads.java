package dev.psyda.surrogate.network;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.survey.SurveyScan;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The survey picture, server to client, in one packet.
 *
 * <p>It is sent whole and only when somebody leans on the table. About seventeen thousand shorts plus a
 * short list of lights: big for a packet and trivial for a thing that happens once a minute at most, and far
 * better than a screen that fills itself in while the player watches.
 */
public final class SurveyPayloads {
	/** One glowing dot: where, what kind, and what to call it. */
	public record Light(int x, int z, int mark, String label) {
		public static final PacketCodec<RegistryByteBuf, Light> CODEC = PacketCodec.tuple(
				PacketCodecs.INTEGER, Light::x,
				PacketCodecs.INTEGER, Light::z,
				PacketCodecs.VAR_INT, Light::mark,
				PacketCodecs.STRING, Light::label,
				Light::new);
	}

	/**
	 * The picture. {@code heights} is row-major and {@code SurveyScan.SIZE} square; entries equal to
	 * {@link SurveyScan#UNKNOWN} are ground the network does not cover.
	 */
	public record Survey(BlockPos centre, int step, int reach, List<Integer> heights, List<Light> lights) implements CustomPayload {
		public static final CustomPayload.Id<Survey> ID = new CustomPayload.Id<>(Surrogate.id("survey"));
		public static final PacketCodec<RegistryByteBuf, Survey> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, Survey::centre,
				PacketCodecs.VAR_INT, Survey::step,
				PacketCodecs.VAR_INT, Survey::reach,
				// Heights go as a plain list of ints rather than a short[]: the codec vocabulary has no
				// short array, and the packet is written once a minute at most.
				PacketCodecs.INTEGER.collect(PacketCodecs.toList()), Survey::heights,
				Light.CODEC.collect(PacketCodecs.toList()), Survey::lights,
				Survey::new);

		public static Survey of(SurveyScan.Picture picture) {
			List<Integer> heights = new ArrayList<>(picture.heights().length);
			for (short height : picture.heights()) heights.add((int) height);
			List<Light> lights = new ArrayList<>(picture.lights().size());
			for (SurveyScan.Light light : picture.lights()) {
				lights.add(new Light(light.x(), light.z(), light.mark().ordinal(), light.label()));
			}
			return new Survey(picture.centre(), picture.step(), picture.reach(), heights, lights);
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private SurveyPayloads() {
	}
}
