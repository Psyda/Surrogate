package dev.psyda.surrogate.world;

import dev.psyda.surrogate.block.BunkBlock;
import dev.psyda.surrogate.block.PropBlock;
import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The room that never came down. The drop ship carried two modules and delivered one, and the plated slab
 * east of the pod — {@link HabitatBuilder#FOUNDATION_MIN} to {@link HabitatBuilder#FOUNDATION_MAX} — has been
 * sitting there empty since the prologue with nothing on it but Halloran's draft of what it was meant to be.
 *
 * <p>Okafor and Sorensen between them have enough standing with the orbital platform to ask for it, which is
 * the payoff of the housewarming: the module comes down on the slab and this is what is inside it. Six bunks,
 * a table, lockers. Everyone act five brings home has to sleep somewhere, and this is where.
 *
 * <p>Coordinates are relative to the habitat origin, the same frame the pod uses, so the slab constants line
 * up without arithmetic. Rows run north to south and columns west to east.
 */
public final class ModuleTwo {
	/**
	 * The head block of each bunk, which is where act seven hangs a name. West wall then east wall, north to
	 * south. Six of them: the pod has one bed in it, and act five brings home six people.
	 */
	public static final BlockPos[] BUNKS = {
			new BlockPos(6, 1, -1), new BlockPos(6, 1, 1), new BlockPos(6, 1, 3),
			new BlockPos(10, 1, -1), new BlockPos(10, 1, 1), new BlockPos(10, 1, 3),
	};

	/**
	 * The connecting door, in the module's west wall at its northern end. It is at the north end rather than
	 * the middle so that walking in does not put you in somebody's bunk: the whole of row z=-3 is the lobby,
	 * and the corridor runs south from it between the two rows.
	 */
	public static final BlockPos DOOR = new BlockPos(5, 1, -3);

	/** Where the pod ends and the module starts, for the drop's aim. */
	public static final BlockPos CENTRE = new BlockPos(8, 1, 0);

	private static final int WEST = 5;
	private static final int NORTH = -4;

	/**
	 * Rows are z = -4 .. 4, columns x = 5 .. 11.
	 *
	 * <p>{@code #} plating, {@code .} air, {@code G} reinforced glass, {@code g} ceiling lamp, {@code =} deck
	 * plating, {@code B}/{@code b} a bunk's head and foot, {@code L} a locker, {@code T} the table,
	 * {@code D}/{@code d} the door into the pod.
	 */
	private static final String[][] LAYERS = {
			{ // y = 0: the floor, laid over the slab that was already there
					"#######",
					"#=====#",
					"#=====#",
					"#=====#",
					"#=====#",
					"#=====#",
					"#=====#",
					"#=====#",
					"#######",
			},
			{ // y = 1: three bunks down each long wall, the lobby across the north end, the table at the south
					"#######",
					"D....L#",
					"#b...b#",
					"#B...B#",
					"#b...b#",
					"#B...B#",
					"#b...b#",
					"#B.T.B#",
					"#######",
			},
			{ // y = 2: head height. A window over every second bunk, because a bunkroom with no sky is a cell
					"#######",
					"d.....#",
					"#.....#",
					"G.....G",
					"#.....#",
					"G.....G",
					"#.....#",
					"#.....#",
					"#######",
			},
			{ // y = 3: clear
					"#######",
					"#.....#",
					"#.....#",
					"#.....#",
					"#.....#",
					"#.....#",
					"#.....#",
					"#.....#",
					"#######",
			},
			{ // y = 4: the ceiling, with three lamps down the corridor
					"#######",
					"#######",
					"###g###",
					"#######",
					"###g###",
					"#######",
					"###g###",
					"#######",
					"#######",
			},
	};

	private ModuleTwo() {
	}

	/**
	 * Whether it is already there, so a second drop is a no-op rather than a second room.
	 *
	 * <p>The corner of the ceiling, not its middle: the middle of that layer is a lamp, and asking for
	 * plating there was always false, which would have let the module be built on top of itself.
	 */
	public static boolean exists(ServerWorld world, BlockPos origin) {
		return world.getBlockState(origin.add(WEST, 4, NORTH)).isOf(ModBlocks.HULL_PLATING);
	}

	public static void build(ServerWorld world, BlockPos origin) {
		List<Runnable> deferred = new ArrayList<>();
		for (int y = 0; y < LAYERS.length; y++) {
			String[] rows = LAYERS[y];
			for (int row = 0; row < rows.length; row++) {
				for (int col = 0; col < rows[row].length(); col++) {
					char c = rows[row].charAt(col);
					if (c == ' ') continue;
					place(world, origin.add(WEST + col, y, NORTH + row), c, deferred);
				}
			}
		}
		// Bunk halves, lamps and the door all need their neighbours in place first, the same way the pod's do.
		deferred.forEach(Runnable::run);
		// The pod's east wall is still solid where the new door is. Cut it, or the room is sealed and the
		// only way in is to mine through your own hull.
		HabitatBuilder.set(world, origin.add(4, 1, DOOR.getZ()), Blocks.AIR.getDefaultState());
		HabitatBuilder.set(world, origin.add(4, 2, DOOR.getZ()), Blocks.AIR.getDefaultState());
	}

	private static void place(ServerWorld world, BlockPos pos, char c, List<Runnable> deferred) {
		switch (c) {
			case '#' -> HabitatBuilder.set(world, pos, ModBlocks.HULL_PLATING.getDefaultState());
			case '=' -> HabitatBuilder.set(world, pos, ModBlocks.DECK_PLATING.getDefaultState());
			case 'G' -> HabitatBuilder.set(world, pos, ModBlocks.REINFORCED_GLASS.getDefaultState());
			case '.' -> HabitatBuilder.set(world, pos, Blocks.AIR.getDefaultState());
			case 'g' -> HabitatBuilder.set(world, pos, ModBlocks.CEILING_LAMP.getDefaultState());
			case 'T' -> HabitatBuilder.set(world, pos, ModBlocks.MESS_TABLE.getDefaultState());
			case 'L' -> HabitatBuilder.set(world, pos, ModBlocks.LOCKER.getDefaultState()
					.with(PropBlock.FACING, Direction.EAST));
			// Bunks lie north-south with the pillow at the south end, so the foot is the northern half and
			// FACING points from foot to head the way a vanilla bed does.
			case 'b' -> deferred.add(() -> HabitatBuilder.set(world, pos, bunk(BedPart.FOOT)));
			case 'B' -> deferred.add(() -> HabitatBuilder.set(world, pos, bunk(BedPart.HEAD)));
			case 'D' -> deferred.add(() -> HabitatBuilder.set(world, pos, door(DoubleBlockHalf.LOWER)));
			case 'd' -> deferred.add(() -> HabitatBuilder.set(world, pos, door(DoubleBlockHalf.UPPER)));
			default -> throw new IllegalArgumentException("Unknown module glyph " + c);
		}
	}

	private static BlockState bunk(BedPart part) {
		return ModBlocks.BUNK.getDefaultState().with(BunkBlock.FACING, Direction.SOUTH).with(BunkBlock.PART, part);
	}

	private static BlockState door(DoubleBlockHalf half) {
		return ModBlocks.AIRLOCK_DOOR.getDefaultState()
				.with(DoorBlock.FACING, Direction.EAST)
				.with(DoorBlock.HINGE, net.minecraft.block.enums.DoorHinge.LEFT)
				.with(DoorBlock.OPEN, false)
				.with(DoorBlock.POWERED, false)
				.with(DoorBlock.HALF, half);
	}

	/** A block the drop can safely clear out of the sky above the slab before it comes down. */
	public static void clearApproach(ServerWorld world, BlockPos origin) {
		for (int x = WEST; x < WEST + 7; x++) {
			for (int z = NORTH; z < NORTH + 9; z++) {
				for (int y = 5; y <= 24; y++) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.isAir(pos)) HabitatBuilder.set(world, pos, Blocks.AIR.getDefaultState());
				}
			}
		}
	}

	/** Blocks the module is made of, for the landing dust. */
	public static Block skin() {
		return ModBlocks.HULL_PLATING;
	}
}
