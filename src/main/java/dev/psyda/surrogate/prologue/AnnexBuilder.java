package dev.psyda.surrogate.prologue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.ChargingDockBlock;
import dev.psyda.surrogate.block.ChargingDockBlockEntity;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotPaint;
import dev.psyda.surrogate.entity.RobotState;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatState;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The crew annex at Site Two: a second sealed room bolted to the east wall of Halloran's pod, with its own
 * scrubber, dock and dive chair, a window wall into the pod, and the crew inside it. The starter pod has the
 * slab this would stand on and nothing else. Also knows how to wreck it, for later.
 *
 * <p>Columns run x = 4..11 (x = 4 is the pod's east wall, shared), rows run z = -4..4, north at the top.
 */
public final class AnnexBuilder {
	private static final int WEST = 4;
	private static final int NORTH = -4;

	private static final String[][] LAYERS = {
			{ // y = 0: floor
					"########",
					"########",
					"########",
					"########",
					"########",
					"########",
					"########",
					"########",
					"########"},
			{ // y = 1: scrubber and dock on the north wall, chest and bench west, the chair in the south-east corner
					"##PL####",
					"#TK....#",
					"#X.....#",
					"#......#",
					"D......#",
					"#......#",
					"#B....c#",
					"#b.....#",
					"########"},
			{ // y = 2: the wall shared with the pod is glass, so the rooms can see each other
					"##PP####",
					"G......#",
					"G......#",
					"G......#",
					"d......#",
					"G......#",
					"G......#",
					"G......#",
					"########"},
			{ // y = 3
					"##PP####",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"########"},
			{ // y = 4
					"##PP####",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"#......#",
					"########"},
			{ // y = 5: solid roof with lamps in the corners
					"##PPP###",
					"#g####g#",
					"########",
					"########",
					"########",
					"########",
					"########",
					"#g####g#",
					"########"},
			{ // y = 6: solar collectors over the conduits
					"  SSS   ",
					"        ",
					"        ",
					"        ",
					"        ",
					"        ",
					"        ",
					"        ",
					"        "}};

	/** Halloran's chassis waits on the landing pad outside the airlock. */
	public static final Vec3d ROBOT_PAD = new Vec3d(1.5, 1, 10.5);
	/** The floor block the vent comes up through. */
	public static final BlockPos VENT = new BlockPos(9, 0, -2);

	private AnnexBuilder() {
	}

	// ------------------------------------------------------------------ building

	public static void build(ServerWorld world, BlockPos origin) {
		BlockState plating = ModBlocks.HULL_PLATING.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		for (int z = NORTH; z < NORTH + LAYERS[0].length; z++) {
			for (int x = WEST; x < WEST + 8; x++) {
				for (int y = -1; y >= -10; y--) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.getBlockState(pos).isSolidBlock(world, pos)) set(world, pos, plating);
				}
				for (int y = 6; y <= 14; y++) set(world, origin.add(x, y, z), air);
			}
		}
		// A wider landing pad outside the airlock, room for two chassis and a lesson, on a plinth if need be.
		for (int z = 8; z <= 12; z++) {
			for (int x = -2; x <= 2; x++) {
				BlockPos floor = origin.add(x, 0, z);
				if (!world.getBlockState(floor).isSolidBlock(world, floor)) set(world, floor, ModBlocks.CAUSTIC_SANDSTONE.getDefaultState());
				for (int y = -1; y >= -6; y--) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.getBlockState(pos).isSolidBlock(world, pos)) set(world, pos, ModBlocks.CAUSTIC_SANDSTONE.getDefaultState());
				}
				for (int y = 1; y <= 4; y++) set(world, origin.add(x, y, z), air);
			}
		}

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
		deferred.forEach(Runnable::run);

		// Something to point at on the walk: a sulfur seam and a scrap heap south of the pad.
		for (int[] d : new int[][]{{3, 17}, {4, 17}, {4, 18}, {5, 18}, {3, 18}}) {
			BlockPos top = surface(world, origin.add(d[0], 0, d[1]));
			set(world, top.down(), ModBlocks.SULFUR_CRUST.getDefaultState());
		}
		BlockPos heap = surface(world, origin.add(7, 0, 18));
		set(world, heap, ModBlocks.SCRAP_HEAP.getDefaultState());
	}

	private static void place(ServerWorld world, BlockPos pos, char c, List<Runnable> deferred) {
		switch (c) {
			case '#' -> set(world, pos, ModBlocks.HULL_PLATING.getDefaultState());
			case 'G' -> set(world, pos, ModBlocks.REINFORCED_GLASS.getDefaultState());
			case 'g' -> set(world, pos, Blocks.GLOWSTONE.getDefaultState());
			case '.' -> set(world, pos, Blocks.AIR.getDefaultState());
			case 'P' -> set(world, pos, ModBlocks.POWER_CONDUIT.getDefaultState());
			case 'S' -> set(world, pos, ModBlocks.SOLAR_COLLECTOR.getDefaultState());
			case 'T' -> set(world, pos, Blocks.CRAFTING_TABLE.getDefaultState());
			case 'L' -> {
				set(world, pos, ModBlocks.LIFE_SUPPORT.getDefaultState().with(LifeSupportBlock.FACING, Direction.SOUTH));
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof LifeSupportBlockEntity unit) {
						unit.addEnergy(Surrogate.CONFIG.lifeSupportEnergyCapacity);
						unit.getVolume().quality = 1f;
					}
				});
			}
			case 'K' -> {
				set(world, pos, ModBlocks.CHARGING_DOCK.getDefaultState().with(ChargingDockBlock.FACING, Direction.SOUTH));
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof ChargingDockBlockEntity dock) dock.addEnergy(Surrogate.CONFIG.dockEnergyCapacity / 3);
				});
			}
			case 'c' -> set(world, pos, ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.WEST));
			case 'X' -> {
				set(world, pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) fillChest(chest);
				});
			}
			case 'B' -> deferred.add(() -> set(world, pos, Blocks.LIGHT_GRAY_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD)));
			case 'b' -> deferred.add(() -> set(world, pos, Blocks.LIGHT_GRAY_BED.getDefaultState().with(BedBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT)));
			case 'D' -> deferred.add(() -> set(world, pos, door(DoubleBlockHalf.LOWER)));
			case 'd' -> deferred.add(() -> set(world, pos, door(DoubleBlockHalf.UPPER)));
			default -> throw new IllegalArgumentException("Unknown annex glyph " + c);
		}
	}

	private static BlockState door(DoubleBlockHalf half) {
		return ModBlocks.AIRLOCK_DOOR.getDefaultState()
				.with(DoorBlock.FACING, Direction.EAST)
				.with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.OPEN, false)
				.with(DoorBlock.POWERED, false)
				.with(DoorBlock.HALF, half);
	}

	private static void set(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
	}

	private static BlockPos surface(ServerWorld world, BlockPos column) {
		return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
	}

	private static void fillChest(ChestBlockEntity chest) {
		List<ItemStack> kit = List.of(
				log(),
				new ItemStack(ModItems.REPAIR_KIT, 2),
				new ItemStack(ModItems.POWER_CELL, 2),
				new ItemStack(ModItems.HULL_PLATING, 16),
				new ItemStack(ModItems.REINFORCED_GLASS, 4),
				new ItemStack(Items.IRON_INGOT, 4),
				new ItemStack(Items.BREAD, 6),
				new ItemStack(Items.COPPER_INGOT, 3));
		int slot = 0;
		for (ItemStack stack : kit) {
			if (slot >= chest.size()) break;
			chest.setStack(slot++, stack);
		}
		chest.markDirty();
	}

	private static ItemStack log() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<RawFilteredPair<Text>> pages = new ArrayList<>();
		for (int i = 1; i <= 3; i++) pages.add(RawFilteredPair.of(Text.translatable("book.surrogate.log.page" + i)));
		book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
				RawFilteredPair.of(Text.translatable("book.surrogate.log.title").getString()),
				Text.translatable("book.surrogate.log.author").getString(), 0, pages, true));
		return book;
	}

	// ------------------------------------------------------------------ people and machines

	/** A crew member standing at {@code at}, facing {@code yaw}. */
	@Nullable
	public static CrewEntity spawnPerson(ServerWorld world, Crew who, Vec3d at, float yaw) {
		CrewEntity person = ModEntities.CREW.create(world);
		if (person == null) return null;
		person.setCharacter(who);
		person.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		person.face(yaw);
		world.spawnEntity(person);
		return person;
	}

	/**
	 * Halloran's chassis: mark one plating, a repair kit and a scanner in the hold, scripted so nobody can
	 * open it. Online and ready to drive, or parked dark on a pad.
	 */
	@Nullable
	public static RobotEntity spawnHalloranChassis(ServerWorld world, Vec3d at, float yaw, boolean online) {
		return spawnCrewChassis(world, Crew.HALLORAN, RobotPaint.HALLORAN, at, yaw, online);
	}

	/**
	 * A crew member's chassis: named for them, wearing their paint, and scripted, so nobody can key a card to
	 * it, dock it, wrench it up or dive into it ({@link RobotEntity#isClaimable}).
	 */
	@Nullable
	public static RobotEntity spawnCrewChassis(ServerWorld world, Crew who, RobotPaint paint, Vec3d at, float yaw, boolean online) {
		RobotEntity robot = ModEntities.ROBOT.create(world);
		if (robot == null) return null;
		robot.setCustomName(Text.translatable("crew.surrogate." + who.key() + ".chassis"));
		robot.setPaint(paint);
		robot.setPlatingTier(1);
		robot.setHealth(robot.getMaxHealth());
		robot.setEnergy(robot.getEnergyCapacity());
		robot.setCargo(0, new ItemStack(ModItems.REPAIR_KIT));
		robot.setCargo(1, new ItemStack(ModItems.ATMO_SCANNER));
		robot.setScripted(true);
		robot.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		robot.setBodyYaw(yaw);
		robot.setHeadYaw(yaw);
		world.spawnEntity(robot);
		if (online) robot.setState(RobotState.ONLINE);
		RobotRegistry.get(world.getServer()).update(robot);
		return robot;
	}

	/**
	 * The station crew at home: Halloran in her chair in the annex, Marsh by the scrubber unless he is out
	 * walking, and her chassis dark on the pad with her chair keyed to it.
	 */
	public static void spawnStationCrew(ServerWorld world, BlockPos origin, HabitatState state) {
		clearCrew(world, origin, state);
		CrewEntity halloran = spawnPerson(world, Crew.HALLORAN, Vec3d.of(origin).add(HALLORAN_CHAIR), 90f);
		if (halloran != null) {
			halloran.sitAt(Vec3d.of(origin).add(HALLORAN_CHAIR), 90f);
			state.halloran = halloran.getUuid();
		}
		if (state.marshVisit != 1) {
			CrewEntity marsh = spawnPerson(world, Crew.MARSH, Vec3d.of(origin).add(MARSH_ANNEX), 180f);
			if (marsh != null) state.marsh = marsh.getUuid();
		}
		RobotEntity robot = spawnHalloranChassis(world, Vec3d.of(origin).add(ROBOT_PAD), 0f, false);
		if (robot != null) {
			state.halloranRobot = robot.getUuid();
			BlockPos chair = origin.add(10, 1, 2);
			if (world.getBlockEntity(chair) instanceof DiveChairBlockEntity chairEntity) {
				chairEntity.setLink(robot.getUuid(), robot.getName().getString());
			}
		}
		state.markDirty();
	}

	/** Removes any crew or scripted chassis around {@code origin}, and the ones the state remembers wherever they are. */
	public static void clearCrew(ServerWorld world, BlockPos origin, HabitatState state) {
		Box around = new Box(origin).expand(64.0);
		List<Entity> found = new ArrayList<>();
		for (Entity entity : world.iterateEntities()) {
			boolean ours = entity instanceof CrewEntity || (entity instanceof RobotEntity robot && robot.isScripted());
			boolean remembered = entity.getUuid().equals(state.halloran) || entity.getUuid().equals(state.marsh) || entity.getUuid().equals(state.halloranRobot);
			if ((ours && around.contains(entity.getPos())) || remembered) found.add(entity);
		}
		for (Entity entity : found) {
			if (entity instanceof RobotEntity robot) RobotRegistry.get(world.getServer()).remove(robot.getUuid());
			entity.discard();
		}
		state.halloran = null;
		state.marsh = null;
		state.halloranRobot = null;
	}

	/** Halloran's chair in the annex, and where the crew stand when they are home. */
	public static final Vec3d HALLORAN_CHAIR = new Vec3d(10.5, 1, 2.5);
	public static final Vec3d MARSH_ANNEX = new Vec3d(8.5, 1, -2.5);

	// ------------------------------------------------------------------ the breach

	public static boolean isBreached(ServerWorld world, BlockPos origin) {
		return world.getBlockState(origin.add(VENT)).isOf(ModBlocks.VENT);
	}

	/**
	 * The vent under the annex floor lets go: it punches up through the floor, the blast takes the north-east
	 * corner of the roof and wall with it, and the room fills with rubble and smoke. Idempotent.
	 */
	public static void breach(ServerWorld world, BlockPos origin) {
		if (isBreached(world, origin)) return;
		BlockState air = Blocks.AIR.getDefaultState();
		BlockState tuff = Blocks.TUFF.getDefaultState();
		BlockState deepslate = Blocks.COBBLED_DEEPSLATE.getDefaultState();

		set(world, origin.add(VENT), ModBlocks.VENT.getDefaultState());
		for (int[] d : new int[][]{{8, -2}, {9, -3}, {10, -2}, {9, -1}}) {
			set(world, origin.add(d[0], 0, d[1]), ModBlocks.SULFUR_CRUST.getDefaultState());
		}
		// Roof and the top of the north and east walls.
		for (int x = 8; x <= 10; x++) {
			for (int z = -3; z <= -1; z++) set(world, origin.add(x, 5, z), air);
			set(world, origin.add(x, 4, -4), air);
		}
		set(world, origin.add(9, 3, -4), air);
		set(world, origin.add(10, 3, -4), air);
		for (int z = -3; z <= -1; z++) set(world, origin.add(11, 4, z), air);
		set(world, origin.add(11, 3, -2), air);
		// Rubble where the roof came down. Marsh stands at (8, 1, -2); the rubble lands around him, not on him.
		set(world, origin.add(9, 1, -3), tuff);
		set(world, origin.add(9, 2, -3), deepslate);
		set(world, origin.add(10, 1, -3), ModBlocks.ASH.getDefaultState());
		set(world, origin.add(10, 1, -1), tuff);
		set(world, origin.add(7, 1, -1), deepslate);
		set(world, origin.add(6, 1, -2), tuff);
		set(world, origin.add(10, 2, -1), ModBlocks.ASH.getDefaultState());
		// And a little thrown clear of the building.
		for (int[] d : new int[][]{{12, -3}, {13, -1}, {12, 1}}) {
			BlockPos top = surface(world, origin.add(d[0], 0, d[1]));
			if (world.getBlockState(top).isAir()) set(world, top, d[1] == -1 ? deepslate : tuff);
		}

		Vec3d blast = Vec3d.ofCenter(origin.add(VENT).up());
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, blast.x, blast.y, blast.z, 2, 0.5, 0.5, 0.5, 0.0);
		world.spawnParticles(ParticleTypes.LAVA, blast.x, blast.y, blast.z, 30, 0.8, 0.5, 0.8, 0.0);
		world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, blast.x, blast.y + 1.5, blast.z, 60, 1.5, 1.5, 1.5, 0.05);
		world.spawnParticles(ParticleTypes.ASH, blast.x, blast.y + 3.0, blast.z, 120, 3.0, 2.5, 3.0, 0.02);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, blast.x, blast.y + 4.5, blast.z, 40, 1.5, 1.0, 1.5, 0.05);
		world.playSound(null, blast.x, blast.y, blast.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.0f, 0.7f);
		world.playSound(null, blast.x, blast.y, blast.z, ModSounds.BREACH, SoundCategory.BLOCKS, 4.0f, 1.0f);
		world.playSound(null, blast.x, blast.y + 3, blast.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 2.0f, 0.8f);
		Surrogate.LOGGER.info("Prologue: the annex at {} is breached", origin);
	}

	/** Puts Halloran's chassis to sleep for good: sparks, a dying tone, and the hull going dark. */
	public static void killRobot(ServerWorld world, RobotEntity robot) {
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, robot.getX(), robot.getY() + 0.6, robot.getZ(), 40, 0.4, 0.4, 0.4, 0.15);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, robot.getX(), robot.getY() + 0.9, robot.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
		world.playSound(null, robot.getX(), robot.getY(), robot.getZ(), ModSounds.SIGNAL_LOST, SoundCategory.NEUTRAL, 1.5f, 1.0f);
		world.playSound(null, robot.getX(), robot.getY(), robot.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.NEUTRAL, 1.0f, 0.5f);
		robot.stopDriving();
		robot.setScripted(false);
		if (robot.getState() != RobotState.OFFLINE) robot.forceOffline();
		robot.setEnergy(0);
		RobotRegistry.get(world.getServer()).update(robot);
	}
}
