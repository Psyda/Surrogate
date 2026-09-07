package dev.psyda.surrogate.survey;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Turning surveyed ground into something a screen can draw.
 *
 * <p>The scan is a heightmap on a coarse lattice — one sample every {@link #STEP} blocks — plus a list of
 * anything worth a light on it. That is all the survey ever knows: it is a radar picture of a surface, not a
 * copy of the world, and the gaps between samples are why the render looks the way it does.
 *
 * <p>A column outside every disc of the network comes back as {@link #UNKNOWN}, and the screen leaves those
 * dark. That is the point of the beacons: the shape of what you have surveyed is visibly the shape of where
 * you have walked.
 */
public final class SurveyScan {
	/** Blocks between samples. Eight is coarse enough to be cheap and fine enough to read as terrain. */
	public static final int STEP = 8;
	/** Samples across the picture, either way from the middle. 64 at step 8 is a 1024 block square. */
	public static final int HALF = 64;
	public static final int SIZE = HALF * 2 + 1;
	/** A column the network does not cover. */
	public static final short UNKNOWN = Short.MIN_VALUE;

	/** What a light on the picture is. Ordinals cross the wire, so new kinds go on the end. */
	public enum Mark {
		/** The player's own base. */
		HOME,
		/** A survivor's shelter, reached or not. */
		SHELTER,
		/** A station beacon: one of the things holding the picture up. */
		BEACON,
		/** A beacon that is not connected to anything. */
		ORPHAN,
		/** The assay pad. */
		PAD
	}

	public record Light(int x, int z, Mark mark, String label) {
	}

	/**
	 * One picture: where it is centred, how coarse it is, the heights, and the lights.
	 *
	 * <p>{@code heights} is row-major, {@code SIZE} by {@code SIZE}, indexed {@code z * SIZE + x} with the
	 * station in the middle. Heights are absolute world Y, so the screen has to find its own floor.
	 */
	public record Picture(BlockPos centre, int step, int reach, short[] heights, List<Light> lights) {
	}

	private SurveyScan() {
	}

	public static Picture scan(ServerWorld world, BlockPos station, SurveyNetwork.Result network) {
		short[] heights = new short[SIZE * SIZE];
		int reach = network.reach(station);
		for (int row = 0; row < SIZE; row++) {
			for (int col = 0; col < SIZE; col++) {
				int x = station.getX() + (col - HALF) * STEP;
				int z = station.getZ() + (row - HALF) * STEP;
				short value = UNKNOWN;
				// Two reasons a column is unknown, and they read the same on screen: outside the network, or
				// in a chunk nobody has loaded. Neither is worth generating terrain for.
				if (network.covers(x, z) && world.isChunkLoaded(x >> 4, z >> 4)) {
					value = (short) world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
				}
				heights[row * SIZE + col] = value;
			}
		}
		return new Picture(station, STEP, reach, heights, lights(world, station, network));
	}

	/** Everything the survey puts a glowing dot on, filtered to what the network can actually see. */
	private static List<Light> lights(ServerWorld world, BlockPos station, SurveyNetwork.Result network) {
		List<Light> lights = new ArrayList<>();
		HabitatState habitat = HabitatState.get(world.getServer());
		if (habitat.origin != null && network.covers(habitat.origin.getX(), habitat.origin.getZ())) {
			lights.add(new Light(habitat.origin.getX(), habitat.origin.getZ(), Mark.HOME, "station.surrogate.home"));
		}
		for (SurvivorManager.Site site : SurvivorManager.get(world.getServer()).sites()) {
			if (!network.covers(site.x, site.z)) continue;
			lights.add(new Light(site.x, site.z, Mark.SHELTER, site.survivor().nameKey()));
		}
		dev.psyda.surrogate.assay.AssayState assay = dev.psyda.surrogate.assay.AssayState.get(world.getServer());
		if (assay.padSite != null && network.covers(assay.padSite.getX(), assay.padSite.getZ())) {
			lights.add(new Light(assay.padSite.getX(), assay.padSite.getZ(), Mark.PAD, "station.surrogate.pad"));
		}
		for (BlockPos beacon : network.linked()) {
			lights.add(new Light(beacon.getX(), beacon.getZ(), Mark.BEACON, "station.surrogate.beacon"));
		}
		for (BlockPos orphan : network.orphans()) {
			lights.add(new Light(orphan.getX(), orphan.getZ(), Mark.ORPHAN, "station.surrogate.orphan"));
		}
		return lights;
	}

	/**
	 * Whether the network can see every survivor's shelter, which is the long-range scanner's win condition
	 * and the thing the advancement is for.
	 */
	public static boolean seesEverything(ServerWorld world, SurveyNetwork.Result network) {
		List<SurvivorManager.Site> sites = SurvivorManager.get(world.getServer()).sites();
		if (sites.isEmpty()) return false;
		for (SurvivorManager.Site site : sites) {
			if (!network.covers(site.x, site.z)) return false;
		}
		return true;
	}

	/** How far a scanner reaches on the energy it has been fed. Square root, so range costs more than linear. */
	public static int rangeFor(long energy) {
		double base = Surrogate.CONFIG.longRangeScannerBaseRadius;
		double perUnit = Surrogate.CONFIG.longRangeScannerBlocksPerRoot;
		return (int) Math.min(Surrogate.CONFIG.longRangeScannerMaxRadius, base + perUnit * Math.sqrt(Math.max(0, energy)));
	}
}
