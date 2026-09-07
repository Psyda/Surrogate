package dev.psyda.surrogate.hazard;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.entity.BorerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotModule;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.world.Valleys;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The disturbance field and what comes out of it. Every block broken below the line adds a little noise at a
 * position; the noise decays over a couple of minutes; past a threshold something forty to sixty blocks away
 * wakes up and comes to find out what is making it.
 *
 * <p>All of it is off unless {@code hazards} is on and the world is the Toxic Wastes. The field is bounded
 * and lives only in memory: it is gone in two minutes anyway, so a restart losing it costs nothing.
 */
public final class Borers {
	/** How often the field decays and a wake is considered. One second. */
	private static final int SWEEP = 20;
	/** Side of a disturbance cell, in blocks: work in one hole counts as one noise. */
	private static final int CELL = 8;
	/** How many noises one world may be keeping track of. The quietest is dropped to make room. */
	private static final int MAX_CELLS = 96;
	/** How far off one wakes, in blocks. Far enough that it is heard before it is a problem. */
	private static final double SPAWN_MIN = 40.0;
	private static final double SPAWN_MAX = 60.0;
	/** A noise nobody is standing near wakes nothing; there would be no one for it to come for. */
	private static final double NEAR_PLAYER = 80.0;
	/** Where the seismic bar reads full, and where it reads nothing. */
	private static final double THREAT_NEAR = 8.0;
	private static final double THREAT_FAR = 56.0;
	/** How far a chassis-mounted resonance damper quietens the ground. The beacon's reach is a config field. */
	private static final double DAMPER_RANGE = 12.0;
	/** A beacon holds a whole mine quiet, which is the point of planting one; the module only covers you. */
	private static final double BEACON_RANGE = Surrogate.CONFIG.damperBeaconRange;

	/** Noise per world, keyed by cell. */
	private static final Map<RegistryKey<World>, Long2ObjectOpenHashMap<Cell>> FIELD = new HashMap<>();
	/** Damper beacons that have registered themselves, per world. */
	private static final Map<RegistryKey<World>, Set<BlockPos>> BEACONS = new HashMap<>();
	/** Every borer currently ticking, so the HUD can ask how close the nearest one is without a world scan. */
	private static final Map<RegistryKey<World>, Map<UUID, BorerEntity>> LIVE = new HashMap<>();
	/** How many have been woken since the server came up. */
	private static int wakes;

	/** One noise: how loud it still is, and the freshest hole that made it. */
	private static final class Cell {
		private double amount;
		private BlockPos pos;

		private Cell(BlockPos pos) {
			this.pos = pos.toImmutable();
		}
	}

	private Borers() {
	}

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(Borers::tick);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld server) disturb(server, pos, Surrogate.CONFIG.borerDisturbancePerBlock);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	/** Whether anything under the rock is awake in this world at all. */
	public static boolean enabled(ServerWorld world) {
		return Surrogate.CONFIG.hazards && Valleys.isMesaWorld(world);
	}

	// ------------------------------------------------------------------ the field

	/**
	 * Noise at a position. Anything above the line is ignored outright, and a damper or a beacon covering the
	 * spot takes most of it away before it is ever written down.
	 */
	public static void disturb(ServerWorld world, BlockPos pos, double amount) {
		if (!enabled(world)) return;
		if (pos.getY() >= Surrogate.CONFIG.borerDepthY) return;
		double heard = amount * quiet(world, pos);
		if (heard <= 0.0) return;
		Long2ObjectOpenHashMap<Cell> field = FIELD.computeIfAbsent(world.getRegistryKey(), key -> new Long2ObjectOpenHashMap<>());
		long key = cellKey(pos);
		Cell cell = field.get(key);
		if (cell == null) {
			if (field.size() >= MAX_CELLS) dropQuietest(field);
			cell = new Cell(pos);
			field.put(key, cell);
		}
		cell.amount += heard;
		cell.pos = pos.toImmutable();
	}

	/** What this spot hears of a noise: all of it, or the damper's fraction. */
	private static double quiet(ServerWorld world, BlockPos pos) {
		double factor = Surrogate.CONFIG.borerDamperFactor;
		Set<BlockPos> beacons = BEACONS.get(world.getRegistryKey());
		if (beacons != null) {
			for (BlockPos beacon : beacons) {
				if (beacon.getSquaredDistance(pos) <= BEACON_RANGE * BEACON_RANGE) return factor;
			}
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!(player.getVehicle() instanceof RobotEntity robot) || !robot.hasModule(RobotModule.DAMPER)) continue;
			if (robot.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= DAMPER_RANGE * DAMPER_RANGE) return factor;
		}
		return 1.0;
	}

	/** True where a damper or a beacon is playing over the ground, which is where a borer loses its fix. */
	public static boolean damped(ServerWorld world, BlockPos pos) {
		return quiet(world, pos) < 1.0;
	}

	/**
	 * A damper beacon telling the ground it is here. The block that owns it is expected to call this while it
	 * has power and {@link #removeBeacon} when it loses it or is broken.
	 */
	public static void addBeacon(ServerWorld world, BlockPos pos) {
		BEACONS.computeIfAbsent(world.getRegistryKey(), key -> new HashSet<>()).add(pos.toImmutable());
	}

	public static void removeBeacon(ServerWorld world, BlockPos pos) {
		Set<BlockPos> beacons = BEACONS.get(world.getRegistryKey());
		if (beacons != null) beacons.remove(pos);
	}

	private static long cellKey(BlockPos pos) {
		return BlockPos.asLong(Math.floorDiv(pos.getX(), CELL), Math.floorDiv(pos.getY(), CELL), Math.floorDiv(pos.getZ(), CELL));
	}

	private static void dropQuietest(Long2ObjectOpenHashMap<Cell> field) {
		long worst = 0L;
		double amount = Double.MAX_VALUE;
		for (Long2ObjectMap.Entry<Cell> entry : field.long2ObjectEntrySet()) {
			if (entry.getValue().amount < amount) {
				amount = entry.getValue().amount;
				worst = entry.getLongKey();
			}
		}
		field.remove(worst);
	}

	// ------------------------------------------------------------------ the sweep

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % SWEEP != 0) return;
		if (!Surrogate.CONFIG.hazards) {
			if (!FIELD.isEmpty()) FIELD.clear();
			return;
		}
		for (Map<UUID, BorerEntity> live : LIVE.values()) {
			live.values().removeIf(borer -> borer.isRemoved());
		}
		for (ServerWorld world : server.getWorlds()) {
			Long2ObjectOpenHashMap<Cell> field = FIELD.get(world.getRegistryKey());
			if (field == null || field.isEmpty()) continue;
			if (!enabled(world)) {
				field.clear();
				continue;
			}
			double decay = Surrogate.CONFIG.borerDecayPerSecond * SWEEP / 20.0;
			double threshold = Surrogate.CONFIG.borerWakeThreshold;
			Iterator<Cell> cells = field.values().iterator();
			while (cells.hasNext()) {
				Cell cell = cells.next();
				cell.amount -= decay;
				if (cell.amount <= 0.0) {
					cells.remove();
					continue;
				}
				// Waking one spends most of the noise: the next has to be earned again.
				if (cell.amount >= threshold && wake(world, cell)) cell.amount = threshold * 0.4;
			}
		}
	}

	/** Puts one in the rock forty to sixty blocks off and points it at whoever made the noise. */
	private static boolean wake(ServerWorld world, Cell cell) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		ServerPlayerEntity nearest = null;
		double best = NEAR_PLAYER * NEAR_PLAYER;
		for (ServerPlayerEntity player : world.getPlayers()) {
			double distance = player.squaredDistanceTo(cell.pos.getX() + 0.5, cell.pos.getY() + 0.5, cell.pos.getZ() + 0.5);
			if (distance < best) {
				best = distance;
				nearest = player;
			}
		}
		if (nearest == null) return false;
		Entity focus = nearest.getVehicle() instanceof RobotEntity robot ? robot : nearest;
		if (count(world, focus) >= Math.max(1, cfg.borerMaxPerPlayer)) return false;
		BlockPos at = findRock(world, cell.pos);
		if (at == null) return false;
		BorerEntity borer = new BorerEntity(ModEntities.BORER, world);
		borer.refreshPositionAndAngles(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0f);
		borer.send(focus, Vec3d.ofCenter(cell.pos));
		world.spawnEntity(borer);
		track(borer);
		wakes++;
		return true;
	}

	/** Solid rock, well below the line, in a chunk that is already loaded. Nothing here loads one. */
	@Nullable
	private static BlockPos findRock(ServerWorld world, BlockPos noise) {
		Random random = world.getRandom();
		int floor = world.getBottomY() + 8;
		int ceiling = Surrogate.CONFIG.borerDepthY - 4;
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double range = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			int x = noise.getX() + (int) (Math.cos(angle) * range);
			int z = noise.getZ() + (int) (Math.sin(angle) * range);
			int y = noise.getY() - 4 - random.nextInt(12);
			if (y < floor || y > ceiling) continue;
			if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
			BlockPos at = new BlockPos(x, y, z);
			if (!world.getBlockState(at).isSolidBlock(world, at)) continue;
			return at;
		}
		return null;
	}

	// ------------------------------------------------------------------ the live ones

	/** A borer announcing itself, so the HUD and the cap can find it without asking the world. */
	public static void track(BorerEntity borer) {
		if (!(borer.getWorld() instanceof ServerWorld world)) return;
		LIVE.computeIfAbsent(world.getRegistryKey(), key -> new HashMap<>()).put(borer.getUuid(), borer);
	}

	public static void forget(BorerEntity borer) {
		Map<UUID, BorerEntity> live = LIVE.get(borer.getWorld().getRegistryKey());
		if (live != null) live.remove(borer.getUuid());
	}

	/**
	 * Borers woken since the server started. A script that wants to say something the first time one comes
	 * takes a mark when its stage opens and watches for the number to move; Contract Seven's core stage is the
	 * one that does.
	 */
	public static int wakes() {
		return wakes;
	}

	private static int count(ServerWorld world, Entity focus) {
		Map<UUID, BorerEntity> live = LIVE.get(world.getRegistryKey());
		if (live == null) return 0;
		int found = 0;
		for (BorerEntity borer : live.values()) {
			if (!borer.isRemoved() && focus.getUuid().equals(borer.getTarget())) found++;
		}
		return found;
	}

	/** How close the nearest one is, 0 to 1, for the seismic bar. Reads a handful of live entities, no scan. */
	public static float threat(ServerWorld world, Vec3d pos) {
		if (!Surrogate.CONFIG.hazards) return 0f;
		Map<UUID, BorerEntity> live = LIVE.get(world.getRegistryKey());
		if (live == null || live.isEmpty()) return 0f;
		double best = Double.MAX_VALUE;
		for (BorerEntity borer : live.values()) {
			if (borer.isRemoved()) continue;
			best = Math.min(best, borer.squaredDistanceTo(pos.x, pos.y, pos.z));
		}
		if (best == Double.MAX_VALUE) return 0f;
		double distance = Math.sqrt(best);
		if (distance >= THREAT_FAR) return 0f;
		if (distance <= THREAT_NEAR) return 1f;
		return (float) ((THREAT_FAR - distance) / (THREAT_FAR - THREAT_NEAR));
	}

	/** Drops everything on a server going away, so a restart in the same JVM starts quiet. */
	public static void clear() {
		FIELD.clear();
		BEACONS.clear();
		LIVE.clear();
		wakes = 0;
	}
}
