package dev.psyda.surrogate.crawler;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The pocket the crawler cabins live in: a void with a sky, defined in {@code data/surrogate/dimension/crawler_cabin.json},
 * one sealed room per crawler, sixty-four blocks apart along x. Its biome is tagged toxic, so outside a room
 * is as deadly as the wastes and the room needs its scrubber like any other.
 */
public final class CrawlerDimension {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, Surrogate.id("crawler_cabin"));

	private CrawlerDimension() {
	}

	@Nullable
	public static ServerWorld world(MinecraftServer server) {
		return server.getWorld(WORLD);
	}

	public static boolean isCabin(World world) {
		return world.getRegistryKey() == WORLD;
	}
}
