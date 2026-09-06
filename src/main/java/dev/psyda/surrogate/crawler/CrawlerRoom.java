package dev.psyda.surrogate.crawler;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.block.TerminalBlock;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.world.HabitatBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Arrays;

/**
 * The room inside a crawler: five wide, nine long, three high, plating all round, with the helm under a
 * one block camera monitor at the front (north), the hatch and the docking console at the back, a scrubber in the west wall,
 * a dive chair, and the chassis bay in the east wall. Everything else is the crew's to build.
 *
 * <p>Ahead of the porthole the ground the hull is looking at is painted block for block into the void: a
 * strip forty blocks deep and forty-nine wide, resampled from the overworld in the hull's own frame whenever
 * it has moved, so the view out of the glass is the valley the crawler is actually in.
 */
public final class CrawlerRoom {
	public static final BlockPos HELM = new BlockPos(0, 1, -5);
	public static final BlockPos HELM_STAND = new BlockPos(0, 1, -4);
	public static final BlockPos DOCK_CONSOLE = new BlockPos(1, 1, 5);
	public static final BlockPos DOCK_STAND = new BlockPos(1, 1, 4);
	public static final BlockPos HATCH = new BlockPos(0, 1, 5);
	public static final BlockPos HATCH_STAND = new BlockPos(0, 1, 4);
	public static final BlockPos LIFE_SUPPORT = new BlockPos(-3, 1, -2);
	public static final BlockPos CHAIR = new BlockPos(-2, 1, 2);
	public static final BlockPos BAY = new BlockPos(3, 1, 2);
	/** Where a cat rides: the corner by the chair. */
	public static final BlockPos CAT_SPOT = new BlockPos(-2, 1, 3);
	/** Where a rescued survivor stands for the ride home. */
	public static final BlockPos PASSENGER = new BlockPos(-1, 1, 0);

	/** The painted ground: this many blocks each side of centre, and this many ahead of the front wall. */
	public static final int VIEW_HALF_WIDTH = 24;
	public static final int VIEW_DEPTH = 40;
	private static final int VIEW_BELOW = 12;
	private static final int VIEW_ABOVE = 45;

	private CrawlerRoom() {
	}

	/** Everything that belongs to one cabin, room and painted ground included. */
	public static Box bounds(BlockPos origin) {
		return new Box(origin.getX() - VIEW_HALF_WIDTH - 4, origin.getY() - VIEW_BELOW - 2, origin.getZ() - 6 - VIEW_DEPTH - 2,
				origin.getX() + VIEW_HALF_WIDTH + 5, origin.getY() + VIEW_ABOVE + 2, origin.getZ() + 8);
	}

	/** Just the room, floor to ceiling, walls included. */
	public static Box room(BlockPos origin) {
		return new Box(origin.getX() - 3, origin.getY(), origin.getZ() - 5, origin.getX() + 4, origin.getY() + 5, origin.getZ() + 6);
	}

	public static void forceChunks(ServerWorld world, BlockPos origin, boolean forced) {
		Box box = bounds(origin);
		for (int cx = MathHelper.floor(box.minX) >> 4; cx <= MathHelper.floor(box.maxX) >> 4; cx++) {
			for (int cz = MathHelper.floor(box.minZ) >> 4; cz <= MathHelper.floor(box.maxZ) >> 4; cz++) {
				world.setChunkForced(cx, cz, forced);
			}
		}
	}

	public static void build(ServerWorld world, BlockPos origin) {
		BlockState plating = ModBlocks.HULL_PLATING.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = -3; x <= 3; x++) {
			for (int y = 0; y <= 4; y++) {
				for (int z = -5; z <= 5; z++) {
					boolean wall = x == -3 || x == 3 || y == 0 || y == 4 || z == -5 || z == 5;
					HabitatBuilder.set(world, origin.add(x, y, z), wall ? plating : air);
				}
			}
		}
		// The monitor over the helm (one block: the camera picture is painted on its glass), and the lamps.
		HabitatBuilder.set(world, origin.add(0, 2, -5), ModBlocks.REINFORCED_GLASS.getDefaultState());
		for (int z = -3; z <= 3; z += 3) HabitatBuilder.set(world, origin.add(0, 4, z), Blocks.SEA_LANTERN.getDefaultState());
		HabitatBuilder.set(world, origin.add(HELM), ModBlocks.CRAWLER_HELM.getDefaultState().with(TerminalBlock.FACING, Direction.SOUTH));
		HabitatBuilder.set(world, origin.add(DOCK_CONSOLE), ModBlocks.CRAWLER_DOCK_CONSOLE.getDefaultState().with(TerminalBlock.FACING, Direction.NORTH));
		HabitatBuilder.set(world, origin.add(HATCH), ModBlocks.CRAWLER_HATCH.getDefaultState());
		HabitatBuilder.set(world, origin.add(BAY), ModBlocks.CRAWLER_BAY.getDefaultState().with(TerminalBlock.FACING, Direction.WEST));
		HabitatBuilder.set(world, origin.add(CHAIR), ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.EAST));
		BlockPos scrubber = origin.add(LIFE_SUPPORT);
		HabitatBuilder.set(world, scrubber, ModBlocks.LIFE_SUPPORT.getDefaultState().with(LifeSupportBlock.FACING, Direction.EAST));
		if (world.getBlockEntity(scrubber) instanceof LifeSupportBlockEntity lifeSupport) {
			lifeSupport.addEnergy(Surrogate.CONFIG.lifeSupportEnergyCapacity);
			lifeSupport.getVolume().quality = 1f;
		}
		Surrogate.LOGGER.info("Crawler cabin built at {}", origin.toShortString());
	}

	/**
	 * Repaints the ground in front of the porthole from where the hull is and the way it faces. Cheap enough
	 * to run whenever the hull has moved a couple of blocks or turned ten degrees, at most every second.
	 */
	public static void updateDiorama(ServerWorld cabin, BlockPos origin, ServerWorld overworld, CrawlerEntity hull, CrawlerInterior.Runtime runtime) {
		Vec3d pos = hull.getPos();
		float yaw = hull.getYaw();
		long now = overworld.getTime();
		boolean due = runtime.lastPos == null || pos.squaredDistanceTo(runtime.lastPos) >= 4.0
				|| Math.abs(MathHelper.wrapDegrees(yaw - runtime.lastYaw)) >= 10f;
		if (!due || now - runtime.lastPainted < 20) return;
		runtime.lastPos = pos;
		runtime.lastYaw = yaw;
		runtime.lastPainted = now;
		int width = VIEW_HALF_WIDTH * 2 + 1;
		if (runtime.prevTop == null) {
			runtime.prevTop = new int[width * VIEW_DEPTH];
			Arrays.fill(runtime.prevTop, Integer.MIN_VALUE);
		}
		int hullY = MathHelper.floor(pos.y);
		BlockState air = Blocks.AIR.getDefaultState();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		for (int k = 0; k < VIEW_DEPTH; k++) {
			for (int dx = -VIEW_HALF_WIDTH; dx <= VIEW_HALF_WIDTH; dx++) {
				// The room faces north and the hull's own x runs to port, so a cell to the right of the
				// porthole is starboard of the hull.
				Vec3d sample = pos.add(hull.local(-dx, 0.0, CrawlerEntity.HALF_LENGTH + 1.0 + k));
				int wx = MathHelper.floor(sample.x);
				int wz = MathHelper.floor(sample.z);
				if (!overworld.isChunkLoaded(wx >> 4, wz >> 4)) continue;
				int ix = origin.getX() + dx;
				int iz = origin.getZ() - 6 - k;
				int index = (dx + VIEW_HALF_WIDTH) * VIEW_DEPTH + k;
				int h = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz);
				int top = MathHelper.clamp(origin.getY() + (h - hullY), origin.getY() - VIEW_BELOW, origin.getY() + VIEW_ABOVE);
				int prev = runtime.prevTop[index];
				if (prev == Integer.MIN_VALUE) {
					for (int y = origin.getY() - VIEW_BELOW; y <= origin.getY() + VIEW_ABOVE; y++) {
						cursor.set(ix, y, iz);
						if (!cabin.getBlockState(cursor).isAir()) HabitatBuilder.set(cabin, cursor.toImmutable(), air);
					}
				} else if (prev != top) {
					for (int y = Math.min(prev, top) - 2; y <= Math.max(prev, top); y++) {
						cursor.set(ix, y, iz);
						if (!cabin.getBlockState(cursor).isAir()) HabitatBuilder.set(cabin, cursor.toImmutable(), air);
					}
				}
				for (int j = 0; j < 3; j++) {
					cursor.set(wx, h - 1 - j, wz);
					BlockState state = overworld.getBlockState(cursor);
					if (state.isAir()) continue;
					if (state.hasBlockEntity()) state = Blocks.STONE.getDefaultState();
					HabitatBuilder.set(cabin, new BlockPos(ix, top - j, iz), state);
				}
				runtime.prevTop[index] = top;
			}
		}
	}
}
