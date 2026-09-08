package dev.psyda.surrogate.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.psyda.surrogate.Surrogate;
import io.netty.buffer.ByteBuf;
import net.minecraft.component.ComponentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Uuids;

import java.util.UUID;

public final class ModComponents {
	/** Full saved entity NBT of a chassis carried as an item (wrench pick-up, scrap, dock storage). */
	public static final ComponentType<NbtCompound> ROBOT_DATA = register("robot_data",
			ComponentType.<NbtCompound>builder().codec(NbtCompound.CODEC).packetCodec(PacketCodecs.NBT_COMPOUND).build());

	/** A whole saved animal, in a dog carrier that has been picked up. */
	public static final ComponentType<NbtCompound> PET_DATA = register("pet_data",
			ComponentType.<NbtCompound>builder().codec(NbtCompound.CODEC).packetCodec(PacketCodecs.NBT_COMPOUND).build());

	/** Robot targeted by an uplink card. */
	public static final ComponentType<UplinkTarget> UPLINK_TARGET = register("uplink_target",
			ComponentType.<UplinkTarget>builder().codec(UplinkTarget.CODEC).packetCodec(UplinkTarget.PACKET_CODEC).build());

	public record UplinkTarget(UUID robot, String name) {
		public static final Codec<UplinkTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
				Uuids.CODEC.fieldOf("robot").forGetter(UplinkTarget::robot),
				Codec.STRING.fieldOf("name").forGetter(UplinkTarget::name)
		).apply(i, UplinkTarget::new));
		public static final PacketCodec<ByteBuf, UplinkTarget> PACKET_CODEC = PacketCodec.tuple(
				Uuids.PACKET_CODEC, UplinkTarget::robot,
				PacketCodecs.STRING, UplinkTarget::name,
				UplinkTarget::new);
	}

	private static <T> ComponentType<T> register(String name, ComponentType<T> type) {
		return Registry.register(Registries.DATA_COMPONENT_TYPE, Surrogate.id(name), type);
	}

	public static void register() {
	}

	private ModComponents() {
	}
}
