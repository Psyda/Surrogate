package dev.psyda.surrogate.survivor;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.BreachedPlatingBlock;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.PropBlock;
import dev.psyda.surrogate.block.TerminalBlock;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.world.HabitatBuilder;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * A survivor's shelter: a cramped sealed box with a dead scrubber, a bed, a chest and an airlock porch,
 * and the survivor standing in the middle of it. A chassis port sits in the south wall beside the door
 * (a chassis talks to the room through it without anyone opening anything), and a docking collar in the
 * west wall with a levelled apron in front of it, so a crawler can back on and take the survivor aboard.
 * Built on the spot the first time the chunk loads.
 *
 * <p>There are two kits. Everywhere on the near side gets the plating box; the acid belt past the Rift gets
 * the ceramic one, which is the same room under a sloped roof. Novak has neither: his site is a wreck.
 *
 * <p>Columns run x = -3..3, rows run z = -3..3 with north at the top. The porch is the exception: it hangs
 * off the south wall outside that grid, at x = -1..1 and z = 4..6, and it is what makes the two damaged
 * shelters make sense. Sorensen's missing plate and Reyes' eaten frame are holes in a wall — an airtight
 * block that is not there — so with a single door between them and the room, both of those people were
 * standing in the open air of a planet that kills a lung in ten seconds and reporting it by radio. In a
 * porch the hole is in the outer wall: the inner door holds, they are alive, and the lock will not cycle
 * against open sky, which is the actual reason neither of them has walked out. Plate it from outside and it
 * cycles.
 */
public final class SurvivorShelter {
	/** The lower half of the collar door, in the west wall. */
	public static final BlockPos COLLAR = new BlockPos(-3, 1, 0);
	/** The chassis port, in the south wall east of the airlock. */
	public static final BlockPos PORT = new BlockPos(2, 1, 3);
	/** The porch: three wide and three deep off the south wall, with the outer door in the far face. */
	private static final int PORCH_HALF_X = 1;
	private static final int PORCH_NEAR_Z = 4;
	private static final int PORCH_FAR_Z = 6;
	private static final int PORCH_ROOF_Y = 3;
	/** The lower half of the outer door, which is the one that will not cycle while the frame is open. */
	public static final BlockPos OUTER_DOOR = new BlockPos(0, 1, PORCH_FAR_Z);
	/**
	 * The plate over Sorensen's porch that let go. Only his shelter has it, and it is one of act one's
	 * errands (docs/DESIGN-campaign.md): until somebody puts a hull plate on it his lock will not cycle.
	 */
	public static final BlockPos BREACH = new BlockPos(1, 2, PORCH_FAR_Z);
	/**
	 * Clinic Nine's frame. Her outer door is lying in the porch and both jambs are eaten through, top and
	 * bottom, which is four plates from outside and the reason she has not left in nineteen days
	 * (docs/DESIGN-campaign.md, act five). Only Reyes has these.
	 */
	public static final BlockPos[] FRAME = {
			new BlockPos(-1, 1, PORCH_FAR_Z), new BlockPos(1, 1, PORCH_FAR_Z),
			new BlockPos(-1, 2, PORCH_FAR_Z), new BlockPos(1, 2, PORCH_FAR_Z)};
	/** The overhang around Novak's wreck: half its width, half its depth, and its ceiling. */
	private static final int POCKET_X = 6;
	private static final int POCKET_Z = 5;
	private static final int POCKET_UP = 6;
	private static final int APRON_MIN_X = -15;
	private static final int APRON_MAX_X = -4;
	private static final int APRON_HALF_Z = 2;

	private static final String[][] LAYERS = {
			{ // y = 0: floor
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######"},
			{ // y = 1
					"###L###",
					"#B...X#",
					"Gb....#",
					"K..V..#",
					"G.....#",
					"#T...c#",
					"###D#P#"},
			{ // y = 2
					"#######",
					"#.....#",
					"G.....#",
					"k.....#",
					"G.....#",
					"#.....#",
					"###d###"},
			{ // y = 3: roof with a skylight and lamps
					"#######",
					"#g###g#",
					"###G###",
					"##GGG##",
					"###G###",
					"#g###g#",
					"#######"}};

	/**
	 * The kit they build with in the belt, where anything the rain lands on is eaten. Same footprint, same
	 * collar in the west wall, same port by the door, but ceramic instead of plating, a solid roof instead
	 * of a skylight, a gutter round the eaves and the panels under glass. Brandt built the first one and
	 * everyone downwind copied it.
	 */
	private static final String[][] BELT = {
			{ // y = 0: floor
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######",
					"#######"},
			{ // y = 1
					"CCCLCCC",
					"CB...XC",
					"Gb....C",
					"K..V..C",
					"G.....C",
					"CT...cC",
					"CCCDCPC"},
			{ // y = 2
					"CCCCCCC",
					"C.....C",
					"G.....C",
					"k.....C",
					"G.....C",
					"C.....C",
					"CCCdCCC"},
			{ // y = 3: no skylight; the rain gets nothing
					"CCCCCCC",
					"CgCCCgC",
					"CCCCCCC",
					"CCCCCCC",
					"CCCCCCC",
					"CgCCCgC",
					"CCCCCCC"},
			{ // y = 4: the gutter round the eaves, and four panels standing in it
					"uuuuuuu",
					"u.....u",
					"u.A.A.u",
					"u.....u",
					"u.A.A.u",
					"u.....u",
					"uuuuuuu"},
			{ // y = 5: the sloped roof, glazed over the panels so they still see the sun
					"nnnnnnn",
					"wCCCCCe",
					"wCGCGCe",
					"wCCCCCe",
					"wCGCGCe",
					"wCCCCCe",
					"sssssss"}};

	private SurvivorShelter() {
	}

	/** Which shape goes up here: the belt has its own, and everywhere else has the plating box. */
	private static String[][] kit(SurvivorManager.Site site) {
		return site.belt ? BELT : LAYERS;
	}

	/** The collar door of a built shelter, in world coordinates. */
	public static BlockPos collar(SurvivorManager.Site site) {
		return site.origin().add(COLLAR);
	}

	public static void build(ServerWorld world, SurvivorManager.Site site) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, site.x, site.z);
		BlockPos origin = new BlockPos(site.x, top - 1, site.z);
		site.y = origin.getY();
		BlockState plating = ModBlocks.HULL_PLATING.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		for (int z = -3; z <= 3; z++) {
			for (int x = -3; x <= 3; x++) {
				for (int y = -1; y >= -5; y--) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.getBlockState(pos).isSolidBlock(world, pos)) set(world, pos, plating);
				}
				for (int y = 4; y <= 8; y++) set(world, origin.add(x, y, z), air);
			}
		}
		for (int z = 4; z <= 6; z++) {
			for (int x = -1; x <= 1; x++) {
				BlockPos floor = origin.add(x, 0, z);
				if (!world.getBlockState(floor).isSolidBlock(world, floor)) set(world, floor, ModBlocks.CAUSTIC_SANDSTONE.getDefaultState());
				for (int y = 1; y <= 3; y++) set(world, origin.add(x, y, z), air);
			}
		}
		// The apron west of the collar, level with the floor, for a hull to back down.
		HabitatBuilder.plinth(world, origin, APRON_MIN_X, -APRON_HALF_Z, APRON_MAX_X, APRON_HALF_Z, ModBlocks.CAUSTIC_SANDSTONE.getDefaultState(), 5);

		List<Runnable> deferred = new ArrayList<>();
		String[][] kit = kit(site);
		for (int y = 0; y < kit.length; y++) {
			for (int row = 0; row < 7; row++) {
				for (int col = 0; col < 7; col++) {
					char c = kit[y][row].charAt(col);
					if (c == ' ') continue;
					place(world, origin.add(col - 3, y, row - 3), c, site, deferred);
				}
			}
		}
		porch(world, origin, site, deferred);
		deferred.forEach(Runnable::run);

		// Sorensen's outer frame is one plate short. It goes in after the glyphs, over the wall they laid.
		if (site.survivor() == Survivor.SORENSEN) {
			set(world, origin.add(BREACH), ModBlocks.BREACHED_PLATING.getDefaultState().with(BreachedPlatingBlock.FACING, Direction.SOUTH));
		}
		// Reyes' is worse: the whole frame, both jambs, top and bottom. The door is still there and it will
		// not cycle against open air, which is why she is behind it and not walking about.
		if (site.survivor() == Survivor.REYES) {
			for (BlockPos offset : FRAME) {
				set(world, origin.add(offset), ModBlocks.BREACHED_PLATING.getDefaultState().with(BreachedPlatingBlock.FACING, Direction.SOUTH));
			}
		}

		// A few scrap heaps outside: the wreck of whatever brought them here. Not on the apron.
		Random random = world.getRandom();
		for (int i = 0; i < 3; i++) {
			int x = random.nextBetween(-7, 7);
			int z = random.nextBetween(-7, 7);
			if (Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;
			if (x < -3) continue;
			// Nor on the porch roof, which the heightmap is perfectly happy to call the ground.
			if (Math.abs(x) <= PORCH_HALF_X && z >= PORCH_NEAR_Z && z <= PORCH_FAR_Z) continue;
			BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, origin.add(x, 0, z));
			if (world.getBlockState(surface).isAir() && world.getBlockState(surface.down()).isSolidBlock(world, surface.down())) {
				set(world, surface, ModBlocks.SCRAP_HEAP.getDefaultState());
			}
		}

		SurvivorEntity survivor = ModEntities.SURVIVOR.create(world);
		if (survivor != null) {
			survivor.setCharacter(site.survivor());
			survivor.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 180f, 0f);
			world.spawnEntity(survivor);
		}
		Surrogate.LOGGER.info("Built {}'s shelter at {}", site.survivor().key(), origin);
	}

	/**
	 * Novak's site, which is not a shelter: no collar, no port, no air. A crawler that went over the edge
	 * eleven days ago, lying on the floor of the Rift with one lamp still burning off its buffer, and Novak
	 * beside it. Act five is the only thing that reaches it: a body on foot, a rebreather, and sixty seconds.
	 *
	 * <p>The overhang around it is built rather than found. The Rift cuts thirty-four blocks below a valley
	 * floor that is itself barely above sea level, so the chasm fills, and the wreck used to be laid on the
	 * water's surface thirty blocks above where it was supposed to be — the heightmap that placed it counts
	 * water as ground. It goes on the real floor now, in a sealed pocket that is exactly the overhang Reyes
	 * and Novak both describe on the terminals, so there is air down there to be running out of.
	 */
	public static void buildWreck(ServerWorld world, SurvivorManager.Site site) {
		// The ocean floor, not the surface: the chasm is flooded and the surface of it is not the bottom.
		int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR, site.x, site.z);
		BlockPos origin = new BlockPos(site.x, top - 1, site.z);
		site.y = origin.getY();
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState rock = ModBlocks.CAUSTIC_SANDSTONE.getDefaultState();
		// The pocket: a floor, four walls and a roof, every one of them solid, because a hole in the side of
		// it fills with the chasm in about four seconds and there is nothing to run out of after that.
		for (int x = -POCKET_X; x <= POCKET_X; x++) {
			for (int z = -POCKET_Z; z <= POCKET_Z; z++) {
				for (int y = 0; y <= POCKET_UP; y++) {
					boolean shell = Math.abs(x) == POCKET_X || Math.abs(z) == POCKET_Z || y == 0 || y == POCKET_UP;
					set(world, origin.add(x, y, z), shell ? rock : air);
				}
			}
		}
		// The hull on its side, a hull's width of it, torn open along the top.
		for (int x = -2; x <= 1; x++) {
			set(world, origin.add(x, 1, -1), ModBlocks.HULL_FRAME.getDefaultState());
			set(world, origin.add(x, 1, 0), ModBlocks.DECK_PLATING.getDefaultState());
			set(world, origin.add(x, 1, 1), ModBlocks.HAZARD_PLATING.getDefaultState());
			set(world, origin.add(x, 2, 0), ModBlocks.HULL_FRAME.getDefaultState());
		}
		set(world, origin.add(-2, 2, -1), ModBlocks.SCRAP_HEAP.getDefaultState());
		set(world, origin.add(1, 2, 1), ModBlocks.SCRAP_HEAP.getDefaultState());
		set(world, origin.add(2, 1, 0), ModBlocks.PAD_LIGHT.getDefaultState().with(PropBlock.FACING, Direction.EAST));
		// What came out of it when it rolled, thrown down the scree.
		Random random = world.getRandom();
		for (int i = 0; i < 4; i++) {
			int x = random.nextBetween(-6, 6);
			int z = random.nextBetween(-6, 6);
			if (Math.abs(x) <= 2 && Math.abs(z) <= 2) continue;
			BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, origin.add(x, 0, z));
			if (world.getBlockState(surface).isAir() && world.getBlockState(surface.down()).isSolidBlock(world, surface.down())) {
				set(world, surface, ModBlocks.SCRAP_HEAP.getDefaultState());
			}
		}
		SurvivorEntity survivor = ModEntities.SURVIVOR.create(world);
		if (survivor != null) {
			survivor.setCharacter(site.survivor());
			survivor.refreshPositionAndAngles(origin.getX() + 2.5, origin.getY() + 1, origin.getZ() + 0.5, 90f, 0f);
			world.spawnEntity(survivor);
		}
		// The mask reading goes in the line because the two have disagreed before: the noise says chasm and
		// the ground says a dip in the floor, and the difference is a set piece at the bottom of a canyon
		// against a man lying in a ditch.
		dev.psyda.surrogate.world.Valleys.Masks masks = dev.psyda.surrogate.world.Valleys.masks(world);
		Surrogate.LOGGER.info("Laid {}'s wreck on the Rift floor at {} (rift mask {}, ground {})",
				site.survivor().key(), origin,
				masks == null ? "?" : String.format("%.2f", masks.rift(site.x, site.z)),
				world.getTopY(Heightmap.Type.OCEAN_FLOOR, site.x, site.z));
	}

	private static void place(ServerWorld world, BlockPos pos, char c, SurvivorManager.Site site, List<Runnable> deferred) {
		switch (c) {
			case '#' -> set(world, pos, ModBlocks.HULL_PLATING.getDefaultState());
			case 'G' -> set(world, pos, ModBlocks.REINFORCED_GLASS.getDefaultState());
			case 'g' -> set(world, pos, Blocks.GLOWSTONE.getDefaultState());
			case '.' -> set(world, pos, Blocks.AIR.getDefaultState());
			case 'V' -> set(world, pos, Blocks.AIR.getDefaultState());
			case 'L' -> set(world, pos, ModBlocks.LIFE_SUPPORT.getDefaultState().with(LifeSupportBlock.FACING, Direction.SOUTH));
			case 'T' -> set(world, pos, Blocks.CRAFTING_TABLE.getDefaultState());
			case 'c' -> set(world, pos, ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.WEST));
			case 'P' -> set(world, pos, ModBlocks.CHASSIS_PORT.getDefaultState().with(TerminalBlock.FACING, Direction.SOUTH));
			case 'X' -> {
				set(world, pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) fillChest(world, chest, site);
				});
			}
			case 'B' -> deferred.add(() -> set(world, pos, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD)));
			case 'b' -> deferred.add(() -> set(world, pos, Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT)));
			case 'D' -> deferred.add(() -> set(world, pos, door(DoubleBlockHalf.LOWER)));
			case 'd' -> deferred.add(() -> set(world, pos, door(DoubleBlockHalf.UPPER)));
			case 'K' -> deferred.add(() -> set(world, pos, HabitatBuilder.dockDoor(DoubleBlockHalf.LOWER)));
			case 'k' -> deferred.add(() -> set(world, pos, HabitatBuilder.dockDoor(DoubleBlockHalf.UPPER)));
			// The belt kit. Fired clay holds where plating does not, and the roof runs the water off it.
			case 'C' -> set(world, pos, Blocks.BRICKS.getDefaultState());
			case 'u' -> set(world, pos, Blocks.BRICK_SLAB.getDefaultState());
			case 'A' -> set(world, pos, ModBlocks.SOLAR_COLLECTOR.getDefaultState());
			case 'n' -> set(world, pos, roof(Direction.NORTH));
			case 'e' -> set(world, pos, roof(Direction.EAST));
			case 's' -> set(world, pos, roof(Direction.SOUTH));
			case 'w' -> set(world, pos, roof(Direction.WEST));
			default -> throw new IllegalArgumentException("Unknown shelter glyph " + c);
		}
	}

	/**
	 * The airlock porch, bolted onto the south wall over the strip the levelling already cleared: two cells
	 * of chamber, a roof, and an outer door in the far face facing the way the inner one does.
	 *
	 * <p>Built out of the same material as the room it hangs off, so the belt's shelters get a brick one. It
	 * is not in the glyph grid because it is not in the grid's footprint, and stretching a seven by seven
	 * kit to cover a three by three annexe would have cost more to read than this.
	 */
	private static void porch(ServerWorld world, BlockPos origin, SurvivorManager.Site site, List<Runnable> deferred) {
		BlockState wall = site.belt ? Blocks.BRICKS.getDefaultState() : ModBlocks.HULL_PLATING.getDefaultState();
		for (int z = PORCH_NEAR_Z; z <= PORCH_FAR_Z; z++) {
			for (int x = -PORCH_HALF_X; x <= PORCH_HALF_X; x++) {
				set(world, origin.add(x, 0, z), wall);
				set(world, origin.add(x, PORCH_ROOF_Y, z), wall);
				// The lane down the middle is the chamber; everything either side of it, and the far face, is
				// wall. The one hole in the far face is the door, which goes in with the other pairs.
				boolean lane = x == 0 && z < PORCH_FAR_Z;
				for (int y = 1; y < PORCH_ROOF_Y; y++) {
					set(world, origin.add(x, y, z), lane ? Blocks.AIR.getDefaultState() : wall);
				}
			}
		}
		deferred.add(() -> {
			set(world, origin.add(OUTER_DOOR), door(DoubleBlockHalf.LOWER));
			set(world, origin.add(OUTER_DOOR).up(), door(DoubleBlockHalf.UPPER));
		});
	}

	/** A course of the sloped roof, falling away towards {@code towards}. */
	private static BlockState roof(Direction towards) {
		return Blocks.BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, towards);
	}

	private static BlockState door(DoubleBlockHalf half) {
		return ModBlocks.AIRLOCK_DOOR.getDefaultState()
				.with(DoorBlock.FACING, Direction.SOUTH)
				.with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.OPEN, false)
				.with(DoorBlock.POWERED, false)
				.with(DoorBlock.HALF, half);
	}

	private static void set(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}

	private static void fillChest(ServerWorld world, ChestBlockEntity chest, SurvivorManager.Site site) {
		Random random = world.getRandom();
		List<ItemStack> pool = List.of(
				new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(4)),
				new ItemStack(Items.COPPER_INGOT, 2 + random.nextInt(4)),
				new ItemStack(Items.BREAD, 2 + random.nextInt(4)),
				new ItemStack(Items.REDSTONE, 3 + random.nextInt(5)),
				new ItemStack(Items.GLASS, 4 + random.nextInt(6)),
				new ItemStack(ModItems.REPAIR_KIT, 1),
				new ItemStack(ModItems.HULL_PLATING, 6 + random.nextInt(8)));
		int slot = 0;
		for (ItemStack stack : pool) {
			if (random.nextInt(3) == 0) continue;
			chest.setStack(slot, stack);
			slot += 1 + random.nextInt(3);
			if (slot >= chest.size()) break;
		}
		chest.markDirty();
	}
}
