package dev.psyda.surrogate.assay;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.Predicate;

/**
 * Where the company says the pad goes. Chosen once per world like {@link dev.psyda.surrogate.prologue.SiteTwo}:
 * a flat valley floor a crawler can reach from the pod without a climb, far enough out that driving there is
 * the trip that makes the crawler worth having, and clear of anyone's shelter.
 *
 * <p>Nothing is built here until the assay reaches it. The site is only levelled and staked when the player
 * arrives, so a world that never starts the contract is never scarred by it.
 */
public final class PadSite {
	private static final int MIN_DISTANCE = 620;
	private static final int MAX_DISTANCE = 900;
	private static boolean pendingGround;

	private PadSite() {
	}

	public static void registerEvents() {
		ServerChunkEvents.CHUNK_LOAD.register(PadSite::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(PadSite::tick);
	}

	/** Picks the site, once per world. Safe to call on every join. */
	public static void choose(ServerWorld world, HabitatState habitat, AssayState assay) {
		if (assay.padSite != null || habitat.origin == null) return;
		// Off the world seed, so the pad lands in the same valley every time. See SurvivorManager.
		Random random = Random.create(world.getSeed() ^ 0x50414421L);
		SurvivorManager survivors = SurvivorManager.get(world.getServer());
		BlockPos siteTwo = habitat.siteTwo;
		Predicate<BlockPos> clearOfEveryone = pos -> {
			if (siteTwo != null && Math.abs(siteTwo.getX() - pos.getX()) < 120 && Math.abs(siteTwo.getZ() - pos.getZ()) < 120) return false;
			return survivors.sites().stream().noneMatch(site -> Math.abs(site.x - pos.getX()) < 120 && Math.abs(site.z - pos.getZ()) < 120);
		};
		// Away from Site Two, so the drive out is its own trip rather than a detour past Halloran's door.
		double away = siteTwo == null ? random.nextDouble() * Math.PI * 2.0
				: Math.atan2(siteTwo.getZ() - habitat.origin.getZ(), siteTwo.getX() - habitat.origin.getX()) + Math.PI;
		Valleys.Reach reach = Valleys.reach(world, habitat.origin.getX(), habitat.origin.getZ(), MAX_DISTANCE + 96);
		BlockPos best = Valleys.pickSite(world, reach, habitat.origin, random, away, Math.PI * 0.6,
				MIN_DISTANCE, MAX_DISTANCE, 96, clearOfEveryone);
		// The cone away from Site Two is a preference, not a requirement. On a seed where that quarter is
		// table or acid the pad used to fall straight through to a blind guess, and act three's whole
		// centrepiece would stand somewhere the crawler could not reach. Ask the rest of the compass first,
		// then a wider band, and only then give up on drivable ground.
		if (best == null) {
			best = Valleys.pickSite(world, reach, habitat.origin, random, away, Math.PI,
					MIN_DISTANCE, MAX_DISTANCE, 160, clearOfEveryone);
		}
		if (best == null) {
			best = Valleys.pickSite(world, reach, habitat.origin, random, away, Math.PI,
					MIN_DISTANCE / 2, MAX_DISTANCE + 300, 192, clearOfEveryone);
		}
		boolean drivable = best != null;
		for (int attempt = 0; attempt < 16 && best == null; attempt++) {
			double angle = away + (random.nextDouble() * 2.0 - 1.0) * Math.PI * 0.6;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			BlockPos candidate = new BlockPos(habitat.origin.getX() + (int) Math.round(Math.cos(angle) * distance), 0,
					habitat.origin.getZ() + (int) Math.round(Math.sin(angle) * distance));
			if (clearOfEveryone.test(candidate)) best = candidate;
		}
		if (best == null) best = habitat.origin.add(MIN_DISTANCE, 0, 0);
		assay.padSite = best;
		assay.markDirty();
		Surrogate.LOGGER.info("Assay: pad site at x={}, z={}{}", best.getX(), best.getZ(), drivable ? "" : " (no drivable site found)");
	}

	/** Resolves the site's ground level the first time its chunk loads, so the y is right before anyone drives out. */
	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		AssayState assay = AssayState.get(world.getServer());
		if (assay.padSite == null || assay.padResolved) return;
		ChunkPos pos = chunk.getPos();
		if (assay.padSite.getX() >> 4 == pos.x && assay.padSite.getZ() >> 4 == pos.z) pendingGround = true;
	}

	private static void tick(MinecraftServer server) {
		if (!pendingGround) return;
		ServerWorld world = server.getOverworld();
		AssayState assay = AssayState.get(server);
		if (assay.padSite == null || assay.padResolved) {
			pendingGround = false;
			return;
		}
		if (!world.isChunkLoaded(assay.padSite.getX() >> 4, assay.padSite.getZ() >> 4)) return;
		pendingGround = false;
		resolve(world, assay);
	}

	/**
	 * The pad's origin, at ground level. The terrain search runs exactly once per world and the answer is
	 * then frozen in {@link AssayState#padSite}: {@link HabitatBuilder#findSite} re-centres its search on
	 * whatever it is handed, so calling this twice would move the pad out from under the courses that are
	 * already standing on it. Every caller goes through here.
	 */
	public static BlockPos resolve(ServerWorld world, AssayState assay) {
		if (assay.padResolved && assay.padSite != null) return assay.padSite;
		BlockPos site = assay.padSite == null ? BlockPos.ORIGIN : assay.padSite;
		assay.padSite = search(world, site);
		assay.padResolved = true;
		assay.markDirty();
		Surrogate.LOGGER.info("Assay: pad site ground resolved at {}", assay.padSite);
		return assay.padSite;
	}

	/**
	 * The flattest spot at the site, at ground level. Only {@link #resolve} may call this.
	 *
	 * <p>Every chunk the search can reach has to be loaded first: the heightmap reads as the world bottom in
	 * a chunk that is not loaded, so a search over unloaded ground resolves the pad to bedrock.
	 * {@link HabitatBuilder#findSite} looks 32 blocks out and the pad itself is another 7, so that is three
	 * chunks in every direction, not one.
	 */
	private static BlockPos search(ServerWorld world, BlockPos site) {
		int radius = 3;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				world.getChunk((site.getX() >> 4) + dx, (site.getZ() >> 4) + dz);
			}
		}
		BlockPos flat = HabitatBuilder.findSite(world, site);
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, flat.getX(), flat.getZ());
		return new BlockPos(flat.getX(), top - 1, flat.getZ());
	}
}
