package dev.psyda.surrogate.assay;

import dev.psyda.surrogate.block.VehicleFabricatorBlock;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.world.HabitatBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The company's launch pad, built a course at a time so it grows on screen as each contributor arrives.
 *
 * <p>Layers are authored as text, north at the top, the same way {@link HabitatBuilder} does it. Columns run
 * x = -6..6, rows run z = -6..6, and the origin is the middle of the apron at ground level. Each course is a
 * whole visible stage of the build, not a y layer: the apron, the legs and frame, the mast and lights, and
 * the gantry last.
 */
public final class PadBuilder {
	public static final int RADIUS = 6;
	/** How many courses the pad has. */
	public static final int COURSES = 4;

	/** Where the gantry ends up, relative to the pad origin. The rocket is built north of it. */
	public static final BlockPos GANTRY = new BlockPos(0, 1, 2);
	/** Where the rocket stands once the gantry has built it. */
	public static final BlockPos ROCKET = new BlockPos(0, 1, -2);
	/**
	 * The relay crate on the pad's east edge, where the company leaves the plans and the assay's samples are
	 * collected. Must match the 'C' in course three, or nothing the script puts in it is ever findable.
	 */
	public static final BlockPos CRATE = new BlockPos(5, 1, -1);

	// . nothing   D deck plating   H hazard plating   F hull frame   L pad light
	// M antenna mast   C supply crate   S survey marker   R chem drum   G the gantry
	private static final String[][] COURSE_ROWS = {
			{ // 1: the apron the player lays, hazard border and a plated floor
					"HHHHHHHHHHHHH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HDDDDDDDDDDDH",
					"HHHHHHHHHHHHH"},
			{ // 2: Sorensen's chassis brings the legs and the frame: four uprights framing the rocket stand
					".............",
					".............",
					"..F.......F..",
					".............",
					".............",
					".............",
					".............",
					".............",
					".............",
					"..F.......F..",
					".............",
					".............",
					"............."},
			{ // 3: the corner lights on the border, the mast and the drums standing on the apron
					"L...........L",
					"......M......",
					".............",
					".............",
					".............",
					"...........C.",
					".............",
					".............",
					".............",
					".R.........R.",
					".............",
					".............",
					"L...........L"},
			{ // 4: Halloran sets the gantry down in the middle
					".............",
					".............",
					".............",
					".............",
					".............",
					".............",
					".............",
					".............",
					"......G......",
					".............",
					".............",
					".............",
					"............."}};

	/** The y each course sits at, above the pad origin. */
	private static final int[] COURSE_Y = {0, 1, 1, 1};

	private PadBuilder() {
	}

	/** Levels the ground and marks the square out with survey stakes at the corners. Stage one's payoff. */
	public static void stake(ServerWorld world, BlockPos origin) {
		BlockState fill = ModBlocks.CAUSTIC_SANDSTONE.getDefaultState();
		for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
			for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
				// Solid ground under the whole apron, and clear air above it.
				for (int y = 0; y >= -6; y--) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.getBlockState(pos).isSolidBlock(world, pos)) HabitatBuilder.set(world, pos, fill);
				}
				for (int y = 1; y <= 12; y++) {
					HabitatBuilder.set(world, origin.add(x, y, z), Blocks.AIR.getDefaultState());
				}
			}
		}
		// Just outside the apron, so course one's hazard border does not bury them.
		BlockState marker = ModBlocks.SURVEY_MARKER.getDefaultState();
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx += (RADIUS + 1) * 2) {
			for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz += (RADIUS + 1) * 2) {
				HabitatBuilder.set(world, origin.add(dx, 1, dz), marker);
			}
		}
	}

	/**
	 * Puts the sample vehicle kit and some plate in the relay crate, because the script tells the player the
	 * plans are in there and until now nothing ever put them there.
	 */
	public static void stockCrate(ServerWorld world, BlockPos origin) {
		BlockPos crate = origin.add(CRATE);
		// A chest, not the decorative supply_crate: the script tells the player the plans are in here, so it
		// has to be something they can actually open.
		HabitatBuilder.set(world, crate, net.minecraft.block.Blocks.CHEST.getDefaultState()
				.with(net.minecraft.block.ChestBlock.FACING, Direction.WEST));
		if (!(world.getBlockEntity(crate) instanceof net.minecraft.block.entity.ChestBlockEntity chest)) return;
		// Only stock an empty crate: re-running a stage must not keep handing out fresh kits.
		if (chest.count(dev.psyda.surrogate.registry.ModItems.ROCKET_KIT) > 0) return;
		chest.setStack(0, new net.minecraft.item.ItemStack(dev.psyda.surrogate.registry.ModItems.ROCKET_KIT));
		chest.setStack(1, new net.minecraft.item.ItemStack(dev.psyda.surrogate.registry.ModItems.HULL_PLATING, 16));
		chest.markDirty();
	}

	/**
	 * Empties the pad envelope back to air. A stage command rebuilds from course one, so anything left from
	 * a previous run has to go first or the two builds interleave into one misaligned mess.
	 */
	public static void clear(ServerWorld world, BlockPos origin) {
		BlockState air = Blocks.AIR.getDefaultState();
		for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
			for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
				for (int y = 1; y <= 12; y++) {
					HabitatBuilder.set(world, origin.add(x, y, z), air);
				}
			}
		}
		// The rocket stands clear of the apron on its own column.
		for (int y = 0; y <= 6; y++) {
			HabitatBuilder.set(world, origin.add(ROCKET.getX(), ROCKET.getY() + y, ROCKET.getZ()), air);
		}
	}

	/**
	 * Places course {@code n} (1-based) with a puff of dust and a clank at each block.
	 *
	 * @return false when {@code n} is past the last course
	 */
	public static boolean course(ServerWorld world, BlockPos origin, int n) {
		if (n < 1 || n > COURSES) return false;
		String[] rows = COURSE_ROWS[n - 1];
		int y = COURSE_Y[n - 1];
		for (int row = 0; row < rows.length; row++) {
			for (int col = 0; col < rows[row].length(); col++) {
				char c = rows[row].charAt(col);
				if (c == '.') continue;
				place(world, origin.add(-RADIUS + col, y, -RADIUS + row), c);
			}
		}
		BlockPos centre = origin.up();
		world.playSound(null, centre, SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, SoundCategory.BLOCKS, 1.0f, 0.7f);
		world.spawnParticles(ParticleTypes.CLOUD, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 40, RADIUS * 0.6, 0.3, RADIUS * 0.6, 0.02);
		return true;
	}

	/** Everything up to and including course {@code n}, with no ceremony. Used when resuming a saved world. */
	public static void rebuild(ServerWorld world, BlockPos origin, int courses) {
		for (int n = 1; n <= Math.min(courses, COURSES); n++) {
			String[] rows = COURSE_ROWS[n - 1];
			int y = COURSE_Y[n - 1];
			for (int row = 0; row < rows.length; row++) {
				for (int col = 0; col < rows[row].length(); col++) {
					char c = rows[row].charAt(col);
					if (c != '.') place(world, origin.add(-RADIUS + col, y, -RADIUS + row), c);
				}
			}
		}
		// A rebuilt pad still has to have the plans in it. Skipping a stage or resuming a saved world both
		// land here, and without this the objective that says "the plans are in the relay crate" is a lie.
		if (courses >= 3) stockCrate(world, origin);
	}

	/** How much of the apron the player has laid themselves, as a fraction of the plated middle. */
	public static int apronLaid(ServerWorld world, BlockPos origin) {
		int laid = 0;
		for (int x = -RADIUS + 1; x <= RADIUS - 1; x++) {
			for (int z = -RADIUS + 1; z <= RADIUS - 1; z++) {
				if (world.getBlockState(origin.add(x, 0, z)).isOf(ModBlocks.DECK_PLATING)) laid++;
			}
		}
		return laid;
	}

	/** How many plated blocks a finished apron has. */
	public static int apronTotal() {
		int side = RADIUS * 2 - 1;
		return side * side;
	}

	private static void place(ServerWorld world, BlockPos pos, char c) {
		BlockState state = switch (c) {
			case 'D' -> ModBlocks.DECK_PLATING.getDefaultState();
			case 'H' -> ModBlocks.HAZARD_PLATING.getDefaultState();
			case 'F' -> ModBlocks.HULL_FRAME.getDefaultState();
			case 'L' -> ModBlocks.PAD_LIGHT.getDefaultState();
			case 'M' -> ModBlocks.ANTENNA_MAST.getDefaultState();
			// The relay crate is a real chest so the plans and the samples have somewhere to live.
			case 'C' -> net.minecraft.block.Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.WEST);
			case 'S' -> ModBlocks.SURVEY_MARKER.getDefaultState();
			case 'R' -> ModBlocks.CHEM_DRUM.getDefaultState();
			case 'G' -> ModBlocks.VEHICLE_FABRICATOR.getDefaultState().with(VehicleFabricatorBlock.FACING, Direction.NORTH);
			default -> null;
		};
		if (state == null) return;
		HabitatBuilder.set(world, pos, state);
		// The mast is three blocks tall; the frame legs stand two.
		if (c == 'M') {
			HabitatBuilder.set(world, pos.up(), state);
			HabitatBuilder.set(world, pos.up(2), state);
		} else if (c == 'F') {
			HabitatBuilder.set(world, pos.up(), state);
		}
	}
}
