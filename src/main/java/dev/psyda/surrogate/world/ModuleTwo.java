package dev.psyda.surrogate.world;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.BunkBlock;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.block.PropBlock;
import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.inventory.Inventory;
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
 *
 * <p>Two things about the shell are load-bearing rather than decorative. The lamps hang in the top air layer
 * with plating over them: a ceiling lamp is a twelve by two by twelve slab at the top of its cell, so one
 * laid <em>as</em> the ceiling is not a full cube, and {@link dev.psyda.surrogate.atmosphere.Atmosphere}'s
 * fill walked straight up through all three of them and out at the sky. And the room has a scrubber of its
 * own, because the connecting door is airtight when shut and a sealed volume is whatever one unit can reach:
 * without it, closing the door behind you left the bunkroom off every volume in the world, which reads to
 * {@link dev.psyda.surrogate.atmosphere.Exposure} as standing outside.
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

	/** The module's own scrubber, in the north wall of the lobby, and the collectors that run it. */
	public static final BlockPos LIFE_SUPPORT = new BlockPos(8, 1, -4);

	/**
	 * Rows are z = -4 .. 4, columns x = 5 .. 11.
	 *
	 * <p>{@code #} plating, {@code .} air, {@code G} reinforced glass, {@code g} ceiling lamp, {@code =} deck
	 * plating, {@code B}/{@code b} a bunk's head and foot, {@code L} a locker, {@code T} the table,
	 * {@code D}/{@code d} the door into the pod, {@code S} the scrubber, {@code P} a power conduit and
	 * {@code A} a solar collector.
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
					"###S###",
					"D....L#",
					"#b...b#",
					"#B...B#",
					"#b...b#",
					"#B...B#",
					"#b...b#",
					"#B.T.B#",
					"#######",
			},
			{ // y = 2: head height. Windows over the east bunks and across the south end, which are the two
			  // walls with anything behind them: the west wall is six inches from the pod's own plating.
					"###P###",
					"d.....#",
					"#.....G",
					"#.....#",
					"#.....G",
					"#.....#",
					"#.....G",
					"#.....#",
					"##GGG##",
			},
			{ // y = 3: clear, with three lamps hanging off the ceiling down the corridor
					"###P###",
					"#.....#",
					"#..g..#",
					"#.....#",
					"#..g..#",
					"#.....#",
					"#..g..#",
					"#.....#",
					"#######",
			},
			{ // y = 4: the ceiling, solid, with the conduit run to the collectors along the north edge
					"##PPP##",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
			},
			{ // y = 5: three collectors on the north rim, over the conduits
					"  AAA  ",
					"       ",
					"       ",
					"       ",
					"       ",
					"       ",
					"       ",
					"       ",
					"       ",
			},
	};

	private ModuleTwo() {
	}

	/**
	 * Whether it is already there, so a second drop is a no-op rather than a second room.
	 *
	 * <p>The corner of the ceiling, not its middle: the middle of that layer used to be a lamp, and asking
	 * for plating there was always false, which would have let the module be built on top of itself.
	 */
	public static boolean exists(ServerWorld world, BlockPos origin) {
		return world.getBlockState(origin.add(WEST, 4, NORTH)).isOf(ModBlocks.HULL_PLATING);
	}

	/**
	 * The two cells on the pod's side of the new doorway, emptied.
	 *
	 * <p>Habitats built before the pod's chest moved have it standing in exactly this spot, which turns the
	 * housewarming's payoff into a room you can see into and not walk into. Anything with an inventory in the
	 * way is emptied into the pod's own chest rather than voided or dropped: it is four hundred days of
	 * someone's belongings and it is not the game's to throw on the floor.
	 */
	private static void clearDoorway(ServerWorld world, BlockPos origin) {
		BlockPos chest = origin.add(HabitatBuilder.POD_CHEST);
		for (int y = 1; y <= 2; y++) {
			BlockPos pos = origin.add(WEST - 2, y, DOOR.getZ());
			if (world.isAir(pos)) continue;
			if (world.getBlockEntity(pos) instanceof Inventory inventory) {
				// On a habitat old enough to need this, the chest's new home is bare floor. Stand one there
				// first, so a migration ends with the belongings in a chest rather than in a heap.
				if (world.isAir(chest)) {
					HabitatBuilder.set(world, chest, Blocks.CHEST.getDefaultState()
							.with(ChestBlock.FACING, Direction.WEST));
				}
				HabitatBuilder.moveContents(world, inventory, chest);
			}
			HabitatBuilder.set(world, pos, Blocks.AIR.getDefaultState());
		}
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
		clearDoorway(world, origin);
	}

	private static void place(ServerWorld world, BlockPos pos, char c, List<Runnable> deferred) {
		switch (c) {
			case '#' -> HabitatBuilder.set(world, pos, ModBlocks.HULL_PLATING.getDefaultState());
			case '=' -> HabitatBuilder.set(world, pos, ModBlocks.DECK_PLATING.getDefaultState());
			case 'G' -> HabitatBuilder.set(world, pos, ModBlocks.REINFORCED_GLASS.getDefaultState());
			case '.' -> HabitatBuilder.set(world, pos, Blocks.AIR.getDefaultState());
			case 'g' -> HabitatBuilder.set(world, pos, ModBlocks.CEILING_LAMP.getDefaultState());
			case 'T' -> HabitatBuilder.set(world, pos, ModBlocks.MESS_TABLE.getDefaultState());
			case 'P' -> HabitatBuilder.set(world, pos, ModBlocks.POWER_CONDUIT.getDefaultState());
			case 'A' -> HabitatBuilder.set(world, pos, ModBlocks.SOLAR_COLLECTOR.getDefaultState());
			case 'S' -> {
				HabitatBuilder.set(world, pos, ModBlocks.LIFE_SUPPORT.getDefaultState()
						.with(LifeSupportBlock.FACING, Direction.SOUTH));
				// Charged and already scrubbing, the way the pod's own unit lands. A module dropped with an
				// empty scrubber is a room that poisons the first person to walk into it and shut the door.
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof LifeSupportBlockEntity unit) {
						unit.addEnergy(Surrogate.CONFIG.lifeSupportEnergyCapacity);
						unit.getVolume().quality = 1f;
					}
				});
			}
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
