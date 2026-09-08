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
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What lives here, and where. Registration, spawn rules, and the two populations the biome spawner cannot
 * place on its own.
 *
 * <p>All four are seeded by the sweep below, on a slow clock, near players, and each stops once the local
 * population is at its cap. Trundles and tockers used to come off the biome spawner lists like any animal,
 * and that is how a player came home to a few hundred of them: nothing here ever despawns, and vanilla does
 * not count a mob that cannot despawn towards its creature cap, so the biome spawner never thought it had
 * enough. Seeding them here means the count is ours. Three animals in sight is company. Ten is a herd.
 */
public final class Fauna {
	/** Ticks between sweeps. Three seconds: nothing here is urgent. */
	private static final int SWEEP = 60;

	/** How far from a player the sweep will place something. Outside view distance and it is wasted work. */
	private static final int NEAR = 48;
	/** And how close it refuses to, so nothing pops into existence in front of somebody. */
	private static final int NO_NEARER = 16;

	/** Slagbacks within this of a geyser. They arrive as a group, this big at most, and a geyser gets one group. */
	private static final int GEYSER_REACH = 12;
	private static final int SLAGBACK_GROUP_MIN = 2;
	private static final int SLAGBACK_GROUP_MAX = 5;

	/** Where the two open-ground animals live, by biome id. */
	private static final Set<Identifier> TRUNDLE_BIOMES = Set.of(Surrogate.id("toxic_desert"), Surrogate.id("ash_dunes"),
			Surrogate.id("salt_pans"), Surrogate.id("dead_grove"));
	private static final Set<Identifier> TOCKER_BIOMES = Set.of(Surrogate.id("toxic_desert"), Surrogate.id("acid_flats"),
			Surrogate.id("salt_pans"), Surrogate.id("dead_grove"));
	/** How many of each may be within {@link #NEAR} of a player before the sweep stops adding. */
	private static final int TRUNDLE_CAP = 2;
	private static final int TOCKER_CAP = 2;
	/** One try in this many sweeps, per player, per species: a new animal every half a minute or so at most. */
	private static final int GROUND_CHANCE = 10;

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
				seedGround(world, player.getBlockPos(), ModEntities.TRUNDLE, TrundleEntity.class, TRUNDLE_BIOMES, TRUNDLE_CAP);
				seedGround(world, player.getBlockPos(), ModEntities.TOCKER, TockerEntity.class, TOCKER_BIOMES, TOCKER_CAP);
				seedSlagback(world, player.getBlockPos());
				seedSlug(world, player.getBlockPos());
			}
		}
	}

	/**
	 * One of the ground animals, on open ground in one of its own biomes, somewhere between the near and far
	 * radius of the player, and only while there are fewer than its cap within sight.
	 */
	private static <T extends FaunaEntity> void seedGround(ServerWorld world, BlockPos around, EntityType<T> type,
			Class<T> species, Set<Identifier> biomes, int cap) {
		if (world.random.nextInt(GROUND_CHANCE) != 0) return;
		List<T> here = world.getEntitiesByClass(species, new Box(around).expand(NEAR), e -> true);
		if (here.size() >= cap) return;
		for (int tries = 0; tries < 6; tries++) {
			BlockPos at = around.add(world.random.nextInt(2 * NEAR) - NEAR, 0, world.random.nextInt(2 * NEAR) - NEAR);
			if (Math.abs(at.getX() - around.getX()) < NO_NEARER && Math.abs(at.getZ() - around.getZ()) < NO_NEARER) continue;
			at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at);
			BlockPos under = at.down();
			if (!world.isAir(at) || !world.isAir(at.up())) continue;
			if (!world.getBlockState(under).isSolidBlock(world, under) || !world.getFluidState(under).isEmpty()) continue;
			Optional<RegistryKey<Biome>> biome = world.getBiome(at).getKey();
			if (biome.isEmpty() || !biomes.contains(biome.get().getValue())) continue;
			T mob = type.create(world);
			if (mob == null) return;
			mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
			world.spawnEntity(mob);
			return;
		}
	}

	/**
	 * A group of slagbacks per geyser, near enough that crossing the field means crossing them. Placed folded,
	 * which is their resting state, so the first a player knows of one is standing on it. A geyser that has
	 * any at all is left alone: the group is placed once, and thinned by whoever thins it.
	 */
	private static void seedSlagback(ServerWorld world, BlockPos around) {
		if (world.random.nextInt(6) != 0) return;
		BlockPos geyser = findGeyser(world, around);
		if (geyser == null) return;
		List<SlagbackEntity> here = world.getEntitiesByClass(SlagbackEntity.class, new Box(geyser).expand(GEYSER_REACH), e -> true);
		if (!here.isEmpty()) return;
		int group = SLAGBACK_GROUP_MIN + world.random.nextInt(SLAGBACK_GROUP_MAX - SLAGBACK_GROUP_MIN + 1);
		for (int i = 0; i < group; i++) {
			BlockPos at = scatter(world, geyser, GEYSER_REACH);
			if (at == null) continue;
			SlagbackEntity mob = ModEntities.SLAGBACK.create(world);
			if (mob == null) return;
			mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
			world.spawnEntity(mob);
		}
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
