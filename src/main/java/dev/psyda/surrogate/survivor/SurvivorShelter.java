package dev.psyda.surrogate.survivor;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.LifeSupportBlock;
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
 * A survivor's shelter: a cramped sealed box with a dead scrubber, a bed, a chest and one airlock door,
 * and the survivor standing in the middle of it. A chassis port sits in the south wall beside the door
 * (a chassis talks to the room through it without anyone opening anything), and a docking collar in the
 * west wall with a levelled apron in front of it, so a crawler can back on and take the survivor aboard.
 * Built on the spot the first time the chunk loads.
 *
 * <p>Columns run x = -3..3, rows run z = -3..3 with north at the top.
 */
public final class SurvivorShelter {
	/** The lower half of the collar door, in the west wall. */
	public static final BlockPos COLLAR = new BlockPos(-3, 1, 0);
	/** The chassis port, in the south wall east of the airlock. */
	public static final BlockPos PORT = new BlockPos(2, 1, 3);
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

	private SurvivorShelter() {
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
		for (int y = 0; y < LAYERS.length; y++) {
			for (int row = 0; row < 7; row++) {
				for (int col = 0; col < 7; col++) {
					char c = LAYERS[y][row].charAt(col);
					if (c == ' ') continue;
					place(world, origin.add(col - 3, y, row - 3), c, site, deferred);
				}
			}
		}
		deferred.forEach(Runnable::run);

		// A few scrap heaps outside: the wreck of whatever brought them here. Not on the apron.
		Random random = world.getRandom();
		for (int i = 0; i < 3; i++) {
			int x = random.nextBetween(-7, 7);
			int z = random.nextBetween(-7, 7);
			if (Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;
			if (x < -3) continue;
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
			default -> throw new IllegalArgumentException("Unknown shelter glyph " + c);
		}
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
