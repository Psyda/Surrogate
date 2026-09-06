package dev.psyda.surrogate.survey;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * How far a survey station can see, and how the beacons extend it.
 *
 * <p>A station on its own reads a disc around itself. Every beacon within reach of the station, or within
 * reach of a beacon that is itself connected, adds its own disc — so range grows by walking outwards and
 * planting one at the edge of what you can already see, and the chain is exactly as long as you are willing
 * to walk. A beacon nobody can reach is dark: it lights red, it contributes nothing, and it says so.
 *
 * <p>The whole thing is recomputed on demand rather than kept. There are never many beacons, the walk is a
 * breadth-first search over a handful of positions, and a cache would only ever be wrong when somebody moved
 * one, which is the only time anybody looks.
 */
public final class SurveyNetwork {
	/** The result of a walk: which beacons are lit, and the discs the survey is the union of. */
	public record Result(List<BlockPos> linked, List<BlockPos> orphans, List<Disc> discs) {
		/** The furthest any part of the network reaches from the station, for a one-number readout. */
		public int reach(BlockPos station) {
			double best = 0;
			for (Disc disc : discs) {
				best = Math.max(best, Math.sqrt(disc.centre().getSquaredDistance(station)) + disc.radius());
			}
			return (int) Math.round(best);
		}

		/** Whether a column is inside any of the discs. */
		public boolean covers(int x, int z) {
			for (Disc disc : discs) {
				int dx = x - disc.centre().getX();
				int dz = z - disc.centre().getZ();
				if (dx * dx + dz * dz <= disc.radius() * disc.radius()) return true;
			}
			return false;
		}
	}

	/** One circle of surveyed ground: a station's or a beacon's. */
	public record Disc(BlockPos centre, int radius) {
	}

	private SurveyNetwork() {
	}

	/**
	 * Walk the network out from a station.
	 *
	 * <p>Beacons are found by scanning the loaded chunks around the station out to the chain's maximum
	 * possible extent, which is bounded because a chain that leaves loaded chunks cannot be verified anyway:
	 * a beacon in an unloaded chunk is a beacon nobody has been near since the server started.
	 */
	public static Result walk(ServerWorld world, BlockPos station, int stationRadius) {
		List<BlockPos> beacons = findBeacons(world, station);
		int link = Surrogate.CONFIG.surveyBeaconLinkRange;
		int beaconRadius = Surrogate.CONFIG.surveyBeaconRadius;

		List<BlockPos> linked = new ArrayList<>();
		List<Disc> discs = new ArrayList<>();
		discs.add(new Disc(station, stationRadius));

		// Breadth-first from the station: a beacon joins when it is within link range of something already
		// in, which is what makes this a chain rather than a radius around the table.
		Set<BlockPos> in = new HashSet<>();
		Deque<BlockPos> frontier = new ArrayDeque<>();
		frontier.add(station);
		while (!frontier.isEmpty()) {
			BlockPos from = frontier.poll();
			for (BlockPos beacon : beacons) {
				if (in.contains(beacon)) continue;
				if (!beacon.isWithinDistance(from, link)) continue;
				in.add(beacon);
				linked.add(beacon);
				discs.add(new Disc(beacon, beaconRadius));
				frontier.add(beacon);
			}
		}

		List<BlockPos> orphans = new ArrayList<>();
		for (BlockPos beacon : beacons) {
			if (!in.contains(beacon)) orphans.add(beacon);
		}
		return new Result(linked, orphans, discs);
	}

	/**
	 * Every survey beacon in the loaded chunks around a station. Block entities are asked chunk by chunk
	 * rather than by scanning positions, because the alternative at this radius is millions of block reads.
	 */
	private static List<BlockPos> findBeacons(ServerWorld world, BlockPos station) {
		int reach = Surrogate.CONFIG.surveyBeaconLinkRange * Surrogate.CONFIG.surveyBeaconChainMax;
		int chunks = Math.max(1, reach >> 4);
		ChunkPos middle = new ChunkPos(station);
		List<BlockPos> found = new ArrayList<>();
		for (int cx = middle.x - chunks; cx <= middle.x + chunks; cx++) {
			for (int cz = middle.z - chunks; cz <= middle.z + chunks; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				net.minecraft.world.chunk.Chunk chunk = world.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (BlockPos pos : chunk.getBlockEntityPositions()) {
					if (world.getBlockState(pos).isOf(ModBlocks.SURVEY_BEACON)) found.add(pos.toImmutable());
				}
			}
		}
		return found;
	}

	/** Whether one particular beacon is connected, which is the only thing its own light needs to know. */
	public static boolean isLinked(ServerWorld world, BlockPos beacon) {
		BlockPos station = findStation(world, beacon);
		if (station == null) return false;
		return walk(world, station, Surrogate.CONFIG.surveyStationRadius).linked().contains(beacon);
	}

	/** The nearest station that could possibly own this beacon, or null. */
	public static BlockPos findStation(ServerWorld world, BlockPos from) {
		int reach = Surrogate.CONFIG.surveyBeaconLinkRange * Surrogate.CONFIG.surveyBeaconChainMax;
		int chunks = Math.max(1, reach >> 4);
		ChunkPos middle = new ChunkPos(from);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int cx = middle.x - chunks; cx <= middle.x + chunks; cx++) {
			for (int cz = middle.z - chunks; cz <= middle.z + chunks; cz++) {
				if (!world.isChunkLoaded(cx, cz)) continue;
				net.minecraft.world.chunk.Chunk chunk = world.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (BlockPos pos : chunk.getBlockEntityPositions()) {
					if (!world.getBlockState(pos).isOf(ModBlocks.SURVEY_STATION)) continue;
					double distance = pos.getSquaredDistance(from);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = pos.toImmutable();
					}
				}
			}
		}
		return best;
	}
}
