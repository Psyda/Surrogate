package dev.psyda.surrogate.crawler;

import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.network.CrawlerPayloads;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * What the hull's sensors see: a square of cells around it, in its own frame (ahead is up), each holding
 * the ground's height relative to the hull, and whether it is acid, something alive, or out of range. The
 * cabin draws it as a sonar sweep at the helm and the docking console, with the nearest collar marked, and
 * with the radar's blips: every base within {@link #RADAR_RANGE} blocks, so a site shows up as you get close.
 */
public final class CrawlerSonar {
	/** Cells each side of centre, and blocks per cell: a 33 by 33 grid covering 48 blocks around the hull. */
	public static final int HALF = 16;
	public static final int STEP = 3;
	public static final int SIZE = HALF * 2 + 1;
	public static final int FLAG_WATER = 0x10;
	public static final int FLAG_ENTITY = 0x20;
	public static final int FLAG_UNKNOWN = 0x40;
	public static final int HEIGHT_MASK = 0x0F;
	public static final int HEIGHT_ZERO = 8;
	/** How far off the radar picks a base up. */
	public static final double RADAR_RANGE = 200.0;

	private CrawlerSonar() {
	}

	public static void send(MinecraftServer server, ServerPlayerEntity player, CrawlerEntity hull) {
		ServerWorld world = server.getOverworld();
		Vec3d pos = hull.getPos();
		int hullY = MathHelper.floor(pos.y);
		byte[] cells = new byte[SIZE * SIZE];
		for (int cz = -HALF; cz <= HALF; cz++) {
			for (int cx = -HALF; cx <= HALF; cx++) {
				// Right on the screen is starboard, which is the hull's negative x.
				Vec3d sample = pos.add(hull.local(-cx * STEP, 0.0, cz * STEP));
				int wx = MathHelper.floor(sample.x);
				int wz = MathHelper.floor(sample.z);
				int value;
				if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
					value = FLAG_UNKNOWN;
				} else {
					int ground = world.getTopY(Heightmap.Type.OCEAN_FLOOR, wx, wz);
					int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz);
					value = MathHelper.clamp(ground - hullY + HEIGHT_ZERO, 0, 15);
					if (surface > ground) value |= FLAG_WATER;
				}
				cells[(cz + HALF) * SIZE + (cx + HALF)] = (byte) value;
			}
		}
		double reach = HALF * STEP;
		Box around = new Box(pos.x - reach, pos.y - 8, pos.z - reach, pos.x + reach, pos.y + 8, pos.z + reach);
		for (Entity entity : world.getOtherEntities(hull, around, e -> e.isAlive() && !(e instanceof net.minecraft.entity.ItemEntity))) {
			int[] cell = toCell(hull, entity.getPos());
			if (cell != null) cells[cell[1] * SIZE + cell[0]] |= FLAG_ENTITY;
		}
		CrawlerDocking.Collar collar = CrawlerDocking.nearest(server, hull, 64.0);
		float targetX = 0f;
		float targetZ = 0f;
		boolean hasTarget = collar != null;
		if (hasTarget) {
			Vec3d local = toLocal(hull, collar.target());
			targetX = (float) -local.x;
			targetZ = (float) local.z;
		}
		ServerPlayNetworking.send(player, new CrawlerPayloads.Scan(SIZE, STEP, hasTarget, targetX, targetZ, cells, radar(server, hull)));
	}

	/** The bases within radar range, as {@code x, z, kind} triples in the hull's frame (right positive). */
	private static float[] radar(MinecraftServer server, CrawlerEntity hull) {
		List<Float> blips = new ArrayList<>();
		HabitatState state = HabitatState.get(server);
		if (state.origin != null) blip(blips, hull, state.origin.getX(), state.origin.getZ(), CrawlerPayloads.Scan.POI_HOME);
		if (state.siteTwo != null) blip(blips, hull, state.siteTwo.getX(), state.siteTwo.getZ(), CrawlerPayloads.Scan.POI_SITE_TWO);
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			blip(blips, hull, site.x, site.z, site.rescued || site.aboard ? CrawlerPayloads.Scan.POI_SHELTER_DONE : CrawlerPayloads.Scan.POI_SHELTER);
		}
		float[] packed = new float[blips.size()];
		for (int i = 0; i < packed.length; i++) packed[i] = blips.get(i);
		return packed;
	}

	private static void blip(List<Float> blips, CrawlerEntity hull, int x, int z, int kind) {
		Vec3d local = toLocal(hull, new Vec3d(x + 0.5, hull.getY(), z + 0.5));
		if (local.horizontalLength() > RADAR_RANGE) return;
		blips.add((float) -local.x);
		blips.add((float) local.z);
		blips.add((float) kind);
	}

	/** A world point in the hull's frame: x to port, z ahead. */
	public static Vec3d toLocal(CrawlerEntity hull, Vec3d point) {
		Vec3d d = point.subtract(hull.getPos());
		double yaw = Math.toRadians(hull.getYaw());
		double lx = d.x * Math.cos(yaw) + d.z * Math.sin(yaw);
		double lz = -d.x * Math.sin(yaw) + d.z * Math.cos(yaw);
		return new Vec3d(lx, d.y, lz);
	}

	private static int[] toCell(CrawlerEntity hull, Vec3d point) {
		Vec3d local = toLocal(hull, point);
		int cx = (int) Math.round(-local.x / STEP) + HALF;
		int cz = (int) Math.round(local.z / STEP) + HALF;
		if (cx < 0 || cz < 0 || cx >= SIZE || cz >= SIZE) return null;
		return new int[]{cx, cz};
	}
}
