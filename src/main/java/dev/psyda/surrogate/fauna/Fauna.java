package dev.psyda.surrogate.fauna;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * What lives here, and where. Registration, spawn rules, and the two populations the biome spawner cannot
 * place on its own.
 *
 * <p>Trundles and tockers come off the biome spawner lists like any animal, which is why they are in the
 * generated biome files. The other two do not, and for opposite reasons: a slagback wants to be within sight
 * of a geyser and the spawner has no idea where those are, and a lantern slug wants a cave ceiling, which is
 * a surface the spawner cannot aim at at all. Both are seeded by the sweep below, on a slow clock, near
 * players, and both stop once the local population is at its cap — this is scenery, not pressure.
 */
public final class Fauna {
	/** Ticks between sweeps. Three seconds: nothing here is urgent. */
	private static final int SWEEP = 60;

	/** How far from a player the sweep will place something. Outside view distance and it is wasted work. */
	private static final int NEAR = 48;
	/** And how close it refuses to, so nothing pops into existence in front of somebody. */
	private static final int NO_NEARER = 16;

	/** Slagbacks within this of a geyser, and no more than this many in the radius. */
	private static final int GEYSER_REACH = 12;
	private static final int SLAGBACK_CAP = 3;

	/** Lantern slugs live under this. Above it, the caves are not caves. */
	private static final int SLUG_CEILING = 40;
	private static final int SLUG_CAP = 4;

	private static int sweepIn = SWEEP;

	private Fauna() {
	}

	public static void registerEvents() {
		// Two of the four are ordinary ground spawners; the biome files carry their weights.
		SpawnRestriction.register(ModEntities.TRUNDLE, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Fauna::onOpenGround);
		SpawnRestriction.register(ModEntities.TOCKER, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Fauna::onOpenGround);
		SpawnRestriction.register(ModEntities.SLAGBACK, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Fauna::onOpenGround);
		SpawnRestriction.register(ModEntities.LANTERN_SLUG, SpawnLocationTypes.UNRESTRICTED,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Fauna::anywhere);
		ServerTickEvents.END_SERVER_TICK.register(Fauna::tick);
	}

	/** Solid under, room above, and not in the middle of somebody's floor. Light is not a factor here. */
	private static <T extends MobEntity> boolean onOpenGround(EntityType<T> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		if (reason == SpawnReason.SPAWNER || reason == SpawnReason.SPAWN_EGG) return true;
		return world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) && world.isAir(pos) && world.isAir(pos.up());
	}

	/** The slug is placed by hand below; the spawner is never the thing that puts one on a ceiling. */
	private static <T extends MobEntity> boolean anywhere(EntityType<T> type, ServerWorldAccess world,
			SpawnReason reason, BlockPos pos, Random random) {
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (--sweepIn > 0) return;
		sweepIn = SWEEP;
		if (!Surrogate.CONFIG.fauna) return;
		for (ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey() != World.OVERWORLD || !Valleys.isMesaWorld(world)) continue;
			for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
				if (player.isSpectator()) continue;
				seedSlagback(world, player.getBlockPos());
				seedSlug(world, player.getBlockPos());
			}
		}
	}

	/**
	 * One slagback per geyser, near enough that crossing the field means crossing it. Placed folded, which
	 * is its resting state, so the first a player knows of it is standing on it.
	 */
	private static void seedSlagback(ServerWorld world, BlockPos around) {
		if (world.random.nextInt(6) != 0) return;
		BlockPos geyser = findGeyser(world, around);
		if (geyser == null) return;
		List<SlagbackEntity> here = world.getEntitiesByClass(SlagbackEntity.class,
				new net.minecraft.util.math.Box(geyser).expand(GEYSER_REACH), e -> true);
		if (here.size() >= SLAGBACK_CAP) return;
		BlockPos at = scatter(world, geyser, GEYSER_REACH);
		if (at == null) return;
		SlagbackEntity mob = ModEntities.SLAGBACK.create(world);
		if (mob == null) return;
		mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
		world.spawnEntity(mob);
	}

	/** The nearest geyser worth guarding, or null. A short scan: they are features, so they cluster. */
	private static BlockPos findGeyser(ServerWorld world, BlockPos around) {
		for (int tries = 0; tries < 12; tries++) {
			BlockPos at = around.add(world.random.nextInt(2 * NEAR) - NEAR, 0, world.random.nextInt(2 * NEAR) - NEAR);
			at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at).down();
			if (world.getBlockState(at).isOf(ModBlocks.GEYSER)) return at;
		}
		return null;
	}

	/** A spot on open ground within {@code reach} of the middle, or null if nothing there will take one. */
	private static BlockPos scatter(ServerWorld world, BlockPos middle, int reach) {
		for (int tries = 0; tries < 10; tries++) {
			BlockPos at = middle.add(world.random.nextInt(2 * reach) - reach, 0, world.random.nextInt(2 * reach) - reach);
			at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at);
			if (world.isAir(at) && world.isAir(at.up()) && world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
				return at;
			}
		}
		return null;
	}

	/**
	 * A slug wants a ceiling, which is the one thing the ordinary spawner cannot look for: it works from a
	 * heightmap down, and a cave roof is not on any heightmap. So this walks up from a random point below
	 * ground until it finds air with rock over it, and hangs one there.
	 */
	private static void seedSlug(ServerWorld world, BlockPos around) {
		if (world.random.nextInt(4) != 0) return;
		BlockPos at = around.add(world.random.nextInt(2 * NEAR) - NEAR, 0, world.random.nextInt(2 * NEAR) - NEAR);
		if (Math.abs(at.getX() - around.getX()) < NO_NEARER && Math.abs(at.getZ() - around.getZ()) < NO_NEARER) return;
		int y = world.getBottomY() + 8 + world.random.nextInt(Math.max(1, SLUG_CEILING - world.getBottomY() - 8));
		BlockPos probe = new BlockPos(at.getX(), y, at.getZ());
		if (!world.isAir(probe)) return;
		// Up to the roof of whatever pocket this is. A long climb means it was not a cave, so give up.
		int climbed = 0;
		while (world.isAir(probe.up()) && climbed++ < 12) probe = probe.up();
		if (climbed == 0 || climbed >= 12) return;
		if (!world.getBlockState(probe.up()).isSolidBlock(world, probe.up())) return;
		if (world.getLightLevel(probe) > 7) return;
		List<LanternSlugEntity> here = world.getEntitiesByClass(LanternSlugEntity.class,
				new net.minecraft.util.math.Box(probe).expand(24.0), e -> true);
		if (here.size() >= SLUG_CAP) return;
		LanternSlugEntity mob = ModEntities.LANTERN_SLUG.create(world);
		if (mob == null) return;
		// Hung from the block above, not standing on the one below: the offset is the whole read of it.
		mob.refreshPositionAndAngles(probe.getX() + 0.5, probe.getY() + 0.55, probe.getZ() + 0.5,
				world.random.nextFloat() * 360f, 0f);
		mob.setNoGravity(true);
		world.spawnEntity(mob);
	}

	/** Whether this entity is one of ours, for the sampler and the bag. */
	public static boolean isFauna(Entity entity) {
		return entity instanceof FaunaEntity;
	}

}
