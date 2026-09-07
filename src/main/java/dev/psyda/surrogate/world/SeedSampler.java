package dev.psyda.surrogate.world;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * The masks of a seed with no world behind it. {@link NoiseConfig#create} wants two registries and a number,
 * which is all a seed is, and the mod's datapack is already in the server's registries; so reading a candidate
 * map costs what reading the one underfoot costs, and nothing is saved to disk. {@link SeedSearch} sweeps with
 * these. Winding one costs a few milliseconds of noise samplers; every sample after that is pure and
 * thread-safe.
 */
public final class SeedSampler {
	private final long seed;
	private final NoiseConfig config;
	private final Valleys.Masks masks;

	private SeedSampler(long seed, NoiseConfig config) {
		this.seed = seed;
		this.config = config;
		this.masks = Valleys.masks(config.getNoiseRouter());
	}

	public static SeedSampler of(MinecraftServer server, long seed) {
		return of(server.getRegistryManager().createRegistryLookup(), seed);
	}

	/** The lookup is worth hoisting out of a sweep: it is the same one for every seed. */
	public static SeedSampler of(RegistryEntryLookup.RegistryLookup lookup, long seed) {
		return new SeedSampler(seed, NoiseConfig.create(lookup, Valleys.SETTINGS, seed));
	}

	public long seed() {
		return seed;
	}

	public Valleys.Masks masks() {
		return masks;
	}

	/**
	 * Where this seed would put world spawn, to the middle of a chunk: the same climate search the server runs
	 * on a new world, against the spawn_target in the mesa settings. The server then walks that column down to
	 * solid ground, which needs chunks, so this is the column and not the block.
	 */
	public BlockPos spawn() {
		return new ChunkPos(config.getMultiNoiseSampler().findBestSpawnPosition()).getStartPos().add(8, 0, 8);
	}
}
