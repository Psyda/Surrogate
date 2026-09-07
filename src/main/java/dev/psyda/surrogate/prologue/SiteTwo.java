package dev.psyda.surrogate.prologue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.function.Predicate;

/**
 * Site Two: Halloran's station, five or six hundred metres from the starter pod. The same pod with the crew
 * annex bolted on, her chassis on its pad, and the two of them inside. The voices on the radio come from here;
 * the walk over is the first real trip. Its spot is chosen on the first join and it is built the first time
 * its chunk loads, like the survivors' shelters.
 */
public final class SiteTwo {
	private static final int MIN_DISTANCE = 460;
	private static final int MAX_DISTANCE = 620;
	private static boolean pending;

	private SiteTwo() {
	}

	public static void registerEvents() {
		ServerChunkEvents.CHUNK_LOAD.register(SiteTwo::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(SiteTwo::tick);
	}

	/**
	 * Picks where the station is, once per world: away from the pod, not on top of a survivor, and on a valley
	 * floor the crawler can reach from the pod without a climb ({@link Valleys}), with a cliff behind it when
	 * one is close. Falls back to any point at the right distance on worlds where nothing fits.
	 */
	public static void choose(ServerWorld world, HabitatState state) {
		if (state.siteTwo != null || state.origin == null) return;
		// Off the world seed, so the same seed always puts Site Two in the same place. See SurvivorManager.
		Random random = Random.create(world.getSeed() ^ 0x53495445L);
		SurvivorManager survivors = SurvivorManager.get(world.getServer());
		Predicate<BlockPos> awayFromShelters = pos -> survivors.sites().stream()
				.noneMatch(site -> Math.abs(site.x - pos.getX()) < 80 && Math.abs(site.z - pos.getZ()) < 80);
		Valleys.Reach reach = Valleys.reach(world, state.origin.getX(), state.origin.getZ(), MAX_DISTANCE + 64);
		BlockPos best = Valleys.pickSite(world, reach, state.origin, random, random.nextDouble() * Math.PI * 2.0, Math.PI,
				MIN_DISTANCE, MAX_DISTANCE, 64, awayFromShelters);
		boolean onTheFlat = best != null;
		for (int attempt = 0; attempt < 12 && best == null; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			int x = state.origin.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = state.origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
			BlockPos candidate = new BlockPos(x, 0, z);
			if (awayFromShelters.test(candidate)) best = candidate;
		}
		if (best == null) best = new BlockPos(state.origin.getX() + MIN_DISTANCE, 0, state.origin.getZ());
		state.siteTwo = best;
		state.siteTwoBuilt = false;
		state.markDirty();
		Surrogate.LOGGER.info("Site Two (Halloran) at x={}, z={}{}", best.getX(), best.getZ(), onTheFlat ? "" : " (no drivable site found)");
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		HabitatState state = HabitatState.get(world.getServer());
		if (state.siteTwo == null || state.siteTwoBuilt) return;
		ChunkPos pos = chunk.getPos();
		if (state.siteTwo.getX() >> 4 == pos.x && state.siteTwo.getZ() >> 4 == pos.z) pending = true;
	}

	private static void tick(MinecraftServer server) {
		if (!pending) return;
		ServerWorld world = server.getOverworld();
		HabitatState state = HabitatState.get(server);
		if (state.siteTwo == null || state.siteTwoBuilt) {
			pending = false;
			return;
		}
		if (!world.isChunkLoaded(state.siteTwo.getX() >> 4, state.siteTwo.getZ() >> 4)) return;
		pending = false;
		build(world, state);
	}

	/** Builds the station on the ground and puts the crew in it. Idempotent through {@code siteTwoBuilt}. */
	public static void build(ServerWorld world, HabitatState state) {
		if (state.siteTwo == null) return;
		// The whole footprint has to be loaded before the pod and annex go down.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.getChunk((state.siteTwo.getX() >> 4) + dx, (state.siteTwo.getZ() >> 4) + dz);
			}
		}
		BlockPos flat = HabitatBuilder.findSite(world, state.siteTwo);
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, flat.getX(), flat.getZ());
		BlockPos origin = new BlockPos(flat.getX(), top - 1, flat.getZ());
		HabitatBuilder.build(world, origin, "site02", false);
		AnnexBuilder.build(world, origin);
		HabitatBuilder.wallSign(world, origin.add(1, 2, -4), net.minecraft.util.math.Direction.SOUTH, "site02");
		AnnexBuilder.spawnStationCrew(world, origin, state);
		state.siteTwo = origin;
		state.siteTwoBuilt = true;
		state.markDirty();
		Surrogate.LOGGER.info("Site Two built at {}", origin);
	}

	/** What the field radio hears from Site Two: a steady carrier and a bearing. */
	public static Text signal(ServerPlayerEntity player, HabitatState state) {
		if (state.siteTwo == null) return null;
		double dx = state.siteTwo.getX() + 0.5 - player.getX();
		double dz = state.siteTwo.getZ() + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		String strengthKey = distance < 60 ? "very_strong" : distance < 250 ? "strong" : distance < 600 ? "weak" : "faint";
		Text strength = Text.translatable("message.surrogate.radio.strength." + strengthKey);
		Text bearing = Text.translatable("message.surrogate.radio.bearing." + SurvivorManager.bearing(dx, dz));
		int rounded = (int) (Math.round(distance / 50.0) * 50);
		return Text.translatable("message.surrogate.radio.signal", Text.translatable("message.surrogate.radio.site_two"), strength, rounded, bearing,
				Text.translatable("message.surrogate.radio.on_station")).formatted(Formatting.AQUA);
	}
}
