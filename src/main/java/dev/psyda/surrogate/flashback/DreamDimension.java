package dev.psyda.surrogate.flashback;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Where the flashback happens: a void with no sky and no clock, defined in
 * {@code data/surrogate/dimension/dream.json}.
 *
 * <p>It is deliberately not the transit void. That one is space — its biome is tagged toxic, so standing in
 * it kills you, which is right for the outside of a hull and wrong for a memory of a kitchen. This one is
 *{@code natural: false} so nothing here counts as a night's sleep or a spawn point, and its air is ordinary
 * air, because the whole point of the place is that four years ago you did not need a suit to breathe.
 */
public final class DreamDimension {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Surrogate.id("dream"));

	/** The floor of the antechamber, where the three doors stand. Standing height is one above. */
	public static final BlockPos ORIGIN = new BlockPos(0, 100, 0);

	private DreamDimension() {
	}

	@Nullable
	public static ServerWorld world(MinecraftServer server) {
		return server.getWorld(WORLD);
	}

	public static boolean isDream(World world) {
		return world.getRegistryKey() == WORLD;
	}
}
