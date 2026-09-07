package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

/**
 * The vent that opened across the road to Ceramic Row, and the wall upstream that is holding it there.
 *
 * <p>It is laid across the approach rather than anywhere near Brandt, because the point of it is that a
 * crawler cannot get through and a crawler is what he is waiting for. There is no trick to it: the lava is
 * upstream, a basalt wall points it down the channel and across the road, and a basin sits behind that wall
 * with nothing in it. Cut the wall and the flow goes into the basin instead, the channel starves, and what
 * is left cools to a crust with one seam of it still hot.
 *
 * <p>Nothing here is simulated. Every block of lava is a source and the change is scripted, because
 * twenty-five blocks of flowing fluid over an unloaded chunk boundary is a lag spike and a puddle, not a
 * river. What the player sees is a river, and what the player does to it is what the design asked for.
 */
public final class MagmaFlow {
	/** How far back down the approach from Brandt's shelter the channel is cut. */
	public static final int OUT = 44;
	/** Half the channel's length, across the road. */
	private static final int HALF_LENGTH = 12;
	/** Half the channel's width, along the road. Five wide: a hull's width and then some. */
	private static final int HALF_WIDTH = 2;
	/** How many blocks of the wall have to go before the flow finds the basin. */
	public static final int WALL_CUT = 4;
	private static final int WALL_WIDTH = 2;
	private static final int WALL_HEIGHT = 3;
	/** Where the basin sits relative to the wall, along the road, and how big it is. */
	private static final int BASIN_OFFSET = 8;
	private static final int BASIN_HALF = 3;

	private MagmaFlow() {
	}

	/**
	 * Lays the channel on the line between {@code from} and {@code brandt}, {@link #OUT} blocks short of him,
	 * and records where it and its wall ended up. Does nothing when the state already has one.
	 */
	public static void build(ServerWorld world, RescueState rescue, BlockPos from, BlockPos brandt) {
		if (rescue.flow != null) return;
		// Two thousand block writes generate whatever chunks they land in, synchronously, in the tick that
		// asks for them. Wait until somebody is close enough that the ground is already there.
		// Two chunks either way covers the channel, the wall and the basin, all of which this writes.
		if (!Rescue.loaded(world, where(from, brandt), 2)) return;
		Direction along = bearing(from, brandt);
		Direction across = along.rotateYClockwise();
		BlockPos centre = surface(world, brandt.offset(along.getOpposite(), OUT));
		rescue.flow = centre;
		rescue.flowStepX = along.getOffsetX();
		rescue.flowStepZ = along.getOffsetZ();

		BlockState lava = Blocks.LAVA.getDefaultState();
		BlockState basalt = Blocks.BASALT.getDefaultState();
		// The trench: one block down, lava in it, basalt banks so the edges read as a channel and not a spill.
		for (int s = -HALF_LENGTH; s <= HALF_LENGTH + 6; s++) {
			for (int w = -HALF_WIDTH - 1; w <= HALF_WIDTH + 1; w++) {
				BlockPos pos = centre.offset(across, s).offset(along, w);
				BlockPos floor = new BlockPos(pos.getX(), centre.getY(), pos.getZ());
				boolean bank = Math.abs(w) > HALF_WIDTH;
				world.setBlockState(floor.down(), basalt, Block.NOTIFY_LISTENERS);
				world.setBlockState(floor, bank ? basalt : lava, Block.NOTIFY_LISTENERS);
				for (int up = 1; up <= 3; up++) world.setBlockState(floor.up(up), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		// The wall, upstream, three courses of it across the neck. This is the thing to cut.
		BlockPos wall = centre.offset(across, HALF_LENGTH + 3);
		rescue.flowWall = wall;
		for (int w = -WALL_WIDTH; w <= WALL_WIDTH; w++) {
			for (int up = 0; up < WALL_HEIGHT; up++) {
				world.setBlockState(wall.offset(along, w).up(up), basalt, Block.NOTIFY_LISTENERS);
			}
		}
		// The basin behind it, empty, and obviously somewhere for a river to go.
		BlockPos basin = wall.offset(along, BASIN_OFFSET);
		for (int a = -BASIN_HALF; a <= BASIN_HALF; a++) {
			for (int b = -BASIN_HALF; b <= BASIN_HALF; b++) {
				BlockPos pos = basin.offset(along, a).offset(across, b);
				BlockPos floor = new BlockPos(pos.getX(), centre.getY() - 2, pos.getZ());
				world.setBlockState(floor.down(), basalt, Block.NOTIFY_LISTENERS);
				boolean rim = Math.abs(a) == BASIN_HALF || Math.abs(b) == BASIN_HALF;
				for (int up = 0; up <= 2; up++) {
					world.setBlockState(floor.up(up), rim && up < 2 ? basalt : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		Surrogate.LOGGER.info("Magma flow across the approach to Ceramic Row at {}, wall at {}", centre.toShortString(), wall.toShortString());
	}

	/** How much of the wall is still standing. Anything that removes basalt counts, not just an arc cutter. */
	public static int wallStanding(ServerWorld world, RescueState rescue) {
		if (rescue.flowWall == null) return 0;
		// Never read blocks that are not there. The wall is a kilometre from the pod and the sweep runs every
		// second; a getBlockState on an absent chunk generates it, on the server thread, then and there.
		if (!Rescue.loaded(world, rescue.flowWall, 1)) return wallBlocks();
		Direction along = along(rescue);
		int standing = 0;
		for (int w = -WALL_WIDTH; w <= WALL_WIDTH; w++) {
			for (int up = 0; up < WALL_HEIGHT; up++) {
				BlockPos pos = rescue.flowWall.offset(along, w).up(up);
				if (world.getBlockState(pos).isOf(Blocks.BASALT)) standing++;
			}
		}
		return standing;
	}

	public static int wallBlocks() {
		return (WALL_WIDTH * 2 + 1) * WALL_HEIGHT;
	}

	/**
	 * The wall is open: the flow goes into the basin, the channel crusts over, and one seam of it stays hot
	 * down the middle so that driving across it and walking across it are still different things.
	 */
	public static void cut(ServerWorld world, RescueState rescue) {
		if (rescue.flow == null || rescue.flowCut) return;
		Direction along = along(rescue);
		Direction across = along.rotateYClockwise();
		BlockPos centre = rescue.flow;
		BlockState crust = Blocks.BASALT.getDefaultState();
		BlockState seam = Blocks.MAGMA_BLOCK.getDefaultState();
		for (int s = -HALF_LENGTH; s <= HALF_LENGTH + 6; s++) {
			for (int w = -HALF_WIDTH - 1; w <= HALF_WIDTH + 1; w++) {
				BlockPos at = centre.offset(across, s).offset(along, w);
				BlockPos floor = new BlockPos(at.getX(), centre.getY(), at.getZ());
				if (!world.getBlockState(floor).isOf(Blocks.LAVA)) continue;
				world.setBlockState(floor, w == 0 ? seam : crust, Block.NOTIFY_LISTENERS);
			}
			BlockPos steam = centre.offset(across, s).up();
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, steam.getX() + 0.5, centre.getY() + 1.2, steam.getZ() + 0.5, 6, 1.2, 0.3, 1.2, 0.02);
		}
		// And it ponds where it was always going to pond.
		BlockPos basin = rescue.flowWall == null ? centre : rescue.flowWall.offset(along, BASIN_OFFSET);
		for (int a = -BASIN_HALF + 1; a <= BASIN_HALF - 1; a++) {
			for (int b = -BASIN_HALF + 1; b <= BASIN_HALF - 1; b++) {
				BlockPos pos = basin.offset(along, a).offset(across, b);
				for (int up = 0; up <= 1; up++) {
					world.setBlockState(new BlockPos(pos.getX(), centre.getY() - 2 + up, pos.getZ()), Blocks.LAVA.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		world.playSound(null, centre, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 2.0f, 0.5f);
		world.playSound(null, centre, SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.BLOCKS, 1.6f, 0.6f);
		rescue.flowCut = true;
		rescue.markDirty();
		// The middle of the pond, at the level it was actually filled to, so a test can look at it.
		BlockPos pond = new BlockPos(basin.getX(), centre.getY() - 2, basin.getZ());
		Surrogate.LOGGER.info("Magma flow cut: the channel is crust and the basin at {} has it", pond.toShortString());
	}

	/**
	 * Where the channel goes, worked out without touching the world so a caller can ask whether the chunk is
	 * there before two thousand block writes decide to generate it.
	 */
	public static BlockPos where(BlockPos from, BlockPos brandt) {
		return brandt.offset(bearing(from, brandt).getOpposite(), OUT);
	}

	/** Whether {@code pos} is close enough to the channel for the radio to bring it up. */
	public static boolean near(RescueState rescue, BlockPos pos, int within) {
		if (rescue.flow == null) return false;
		return rescue.flow.isWithinDistance(pos, within);
	}

	private static Direction along(RescueState rescue) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (direction.getOffsetX() == rescue.flowStepX && direction.getOffsetZ() == rescue.flowStepZ) return direction;
		}
		return Direction.NORTH;
	}

	/** The cardinal that points from {@code from} at {@code to}. The road is never diagonal enough to care. */
	private static Direction bearing(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static BlockPos surface(ServerWorld world, BlockPos at) {
		return new BlockPos(at.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
	}
}
