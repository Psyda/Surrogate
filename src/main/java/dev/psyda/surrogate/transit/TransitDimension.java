package dev.psyda.surrogate.transit;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The void the ship flies through. Defined in {@code data/surrogate/dimension/transit.json}: no terrain, no
 * sky light, a biome tagged toxic so that outside the hull is as deadly as it should be, and its own sky
 * renderer on the client.
 */
public final class TransitDimension {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Surrogate.id("transit"));
	/** Where the ship's deck sits. Standing height is one above. */
	public static final BlockPos ORIGIN = new BlockPos(0, 100, 0);

	private TransitDimension() {
	}

	@Nullable
	public static ServerWorld world(MinecraftServer server) {
		return server.getWorld(WORLD);
	}

	public static boolean isTransit(World world) {
		return world.getRegistryKey() == WORLD;
	}
}
