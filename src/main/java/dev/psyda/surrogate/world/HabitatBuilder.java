package dev.psyda.surrogate.world;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.block.ChargingDockBlock;
import dev.psyda.surrogate.block.ChargingDockBlockEntity;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.DockDoorBlock;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.block.TerminalBlock;
import dev.psyda.surrogate.block.TerminalBlockEntity;
import dev.psyda.surrogate.pilot.PilotData;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.prologue.SiteTwo;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.transit.Transit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the sealed starter pod at world spawn the first time anyone joins a toxic world: a 7x7 room with a
 * glass roof for the farm plot, a two door airlock on the south side, and everything needed for a first dive.
 * Around it, the things the first days are about: the landing pad, the company's assay crate, the slab where
 * module two should have been, and a docking collar with nothing docked to it. The same pod, with a crew annex
 * bolted on, is Halloran's station six hundred metres away ({@link SiteTwo}).
 *
 * <p>Layers are authored as text, north at the top. Columns run x = -4..4, rows run z = -4..7.
 */
public final class HabitatBuilder {
	private static final int WEST = -4;
	private static final int NORTH = -4;

	/** Where a chassis stands on the landing pad, outside the airlock. */
	public static final Vec3d ROBOT_PAD = new Vec3d(1.5, 1, 10.5);
	/** The company's sample crate, by the outer door. */
	public static final BlockPos ASSAY_CRATE = new BlockPos(2, 1, 8);
	/** The plated slab east of the pod where module two was meant to go; the room the crew want to build. */
	public static final BlockPos FOUNDATION_MIN = new BlockPos(5, 0, -4);
	public static final BlockPos FOUNDATION_MAX = new BlockPos(11, 0, 4);
	/**
	 * The docking collar in the west wall: the lower half of the collar door, in the middle of a three by three
	 * glass frame, with the apron outside it. A crawler backs onto the apron and couples here.
	 */
	public static final BlockPos DOCK_COLLAR = new BlockPos(-4, 1, 3);
	/** The apron: level ground west of the collar, a crawler long. */
	public static final BlockPos APRON_MIN = new BlockPos(-16, 0, 0);
	public static final BlockPos APRON_MAX = new BlockPos(-5, 0, 6);
	/** Things to point at on the first walk: a sulfur seam and a scrap heap south of the pad. */
	public static final Vec3d SULFUR_SEAM = new Vec3d(4.5, 0, 17.5);
	public static final Vec3d SCRAP_HEAP = new Vec3d(7.5, 0, 18.5);

	private static boolean devPrologueHandled;

	private static final String[][] LAYERS = {
			{ // y = 0: floor, with the farm plot sunk into it
					"#########",
					"#FF######",
					"#FF######",
					"#FF######",
					"#FF######",
					"#W#######",
					"#########",
					"#########",
					"#########",
					"   ###   ",
					"   ###   ",
					"   ###   "},
			{ // y = 1: furniture against the east wall; conduits in the north wall bring roof power to the dock and life support
					"###PL####",
					"#wwK...X#",
					"#ww....T#",
					"#pp....f#",
					"#pp.....#",
					"#.......#",
					"#C.....b#",
					"#......B#",
					"####D####",
					"   #.#   ",
					"   #.#   ",
					"   #D#   "},
			{ // y = 2: the site terminal above the chest, windows in the east and south walls
					"###PP#M##",
					"#.......#",
					"#.......G",
					"#.......#",
					"#.......#",
					"#.......G",
					"#.......#",
					"#.......#",
					"#G##d##G#",
					"   #.#   ",
					"   #.#   ",
					"   #d#   "},
			{ // y = 3: airlock roof with the decon shower over the chamber
					"###PP####",
					"#.......#",
					"#.......G",
					"#.......#",
					"#.......#",
					"#.......G",
					"#.......#",
					"#.......#",
					"#G#####G#",
					"   #H#   ",
					"   ###   ",
					"   ###   "},
			{ // y = 4
					"###PP####",
					"#.......#",
					"#.......#",
					"#.......#",
					"#.......#",
					"#.......#",
					"#.......#",
					"#.......#",
					"#########",
					"         ",
					"         ",
					"         "},
			{ // y = 5: glass roof with lights in the corners, conduits under the solar collectors
					"###PPP###",
					"#gGGGGGg#",
					"#GGGGGGG#",
					"#GGGGGGG#",
					"#GGGGGGG#",
					"#GGGGGGG#",
					"#GGGGGGG#",
					"#gGGGGGg#",
					"#########",
					"         ",
					"         ",
					"         "},
			{ // y = 6: solar collectors on the north rim
					"   SSS   ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         ",
					"         "}};

	private HabitatBuilder() {
	}

	/** Called on every join. Builds the pod once, then walks each new player into it. */
	public static void onJoin(ServerPlayerEntity player) {
		if (!Surrogate.CONFIG.spawnStarterHabitat) return;
		ServerWorld world = player.server.getOverworld();
		HabitatState state = HabitatState.get(player.server);
		if (state.origin == null) {
			BlockPos spawn = world.getSpawnPos();
			if (!Atmosphere.isToxic(world, spawn)) return;
			// On the mesa valleys: the nearest floor with room for the pod and a cliff behind it, then the
			// flattest ground around that.
			BlockPos start = Valleys.pickStart(world, spawn);
			if (!start.equals(spawn)) Surrogate.LOGGER.info("Valleys: pod moved from spawn {} to the floor at {}", spawn.toShortString(), start.toShortString());
			// The heightmap reads as the world bottom in a chunk that is not loaded, and the floor the pod
			// moved to can be well outside the spawn chunks: load the ground findSite will look at.
			for (int cx = (start.getX() - 40) >> 4; cx <= (start.getX() + 48) >> 4; cx++) {
				for (int cz = (start.getZ() - 40) >> 4; cz <= (start.getZ() + 48) >> 4; cz++) {
					world.getChunk(cx, cz);
				}
			}
			BlockPos origin = findSite(world, start);
			build(world, origin, "habitat", true);
			state.origin = origin;
			state.markDirty();
			world.setSpawnPos(origin.up(), 180f);
			Surrogate.LOGGER.info("Built the starter habitat at {}", origin);
		}
		// Worlds that predate the survivors get their shelters placed around the existing habitat.
		SurvivorManager.get(player.server).createSites(world, state.origin);
		SiteTwo.choose(world, state);
		dev.psyda.surrogate.assay.PadSite.choose(world, state, dev.psyda.surrogate.assay.AssayState.get(player.server));
		// Dev: `-PdevBoard=cabin|helm|dock` puts the player aboard the nearest crawler a moment after joining.
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			String board = System.getProperty("surrogate.devBoard");
			if (board != null && !board.isEmpty()) dev.psyda.surrogate.crawler.CrawlerInterior.devBoard(player, board);
		}
		// Dev: `-PdevPrologue=day2` (or day1, day3) skips the ship and starts the days on the ground there.
		if (!devPrologueHandled && FabricLoader.getInstance().isDevelopmentEnvironment()) {
			String dev = System.getProperty("surrogate.devPrologue");
			if (dev != null && !dev.isEmpty()) {
				devPrologueHandled = true;
				if (Boolean.getBoolean("surrogate.devFast")) Director.fast = true;
				PilotManager.data(player).welcomed = true;
				Prologue.restartAt(player, dev);
				return;
			}
		}
		// The first player goes aboard the ship for the week before the landing; the story continues here after.
		if (Transit.onJoin(player, state)) return;
		// The protagonist of a half-told opening picks it up where it stopped.
		if (Prologue.onJoin(player, state)) return;
		// The opening is over: acts one and two come first, and the contract waits behind them.
		if (dev.psyda.surrogate.research.Research.onJoin(player, state)) return;
		if (dev.psyda.surrogate.research.Research.shouldBegin(player.server, state) && !Transit.inProgress(player.server)) {
			dev.psyda.surrogate.research.Research.begin(player.server);
		}
		// A half-run contract resumes, and a finished opening with the research filed opens one.
		if (dev.psyda.surrogate.assay.Assay.onJoin(player, state)) return;
		if (dev.psyda.surrogate.assay.Assay.shouldBegin(player.server, state) && !Transit.inProgress(player.server)) {
			dev.psyda.surrogate.assay.Assay.begin(player.server);
		}
		// Acts four and five never take the join: they have one scene and it is behind a terminal. All this
		// puts back is the banner telling the player which terminal.
		dev.psyda.surrogate.rescue.Rescue.onJoin(player);
		PilotData data = PilotManager.data(player);
		if (data.welcomed) return;
		data.welcomed = true;
		BlockPos inside = state.origin.up();
		player.teleport(world, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 180f, 0f);
		player.setSpawnPoint(world.getRegistryKey(), inside, 180f, true, false);
		LightRefresh.schedule(player, World.OVERWORLD, state.origin, 2, 20);
		if (Prologue.shouldBegin(player.server, state) && !Transit.inProgress(player.server)) {
			Prologue.begin(player, state);
			return;
		}
		player.sendMessage(Text.translatable("message.surrogate.welcome").formatted(Formatting.AQUA), false);
		player.sendMessage(Text.translatable("message.surrogate.welcome.hint").formatted(Formatting.GRAY), false);
	}

	/**
	 * The flattest dry ground near spawn for the pod, the slab east of it and the landing pad south of it
	 * (a footprint of x = -4..11, z = -4..12). Candidates on a grid are scored by the spread of surface heights
	 * across that footprint, with water counted against them; the nearest of the best wins.
	 */
	public static BlockPos findSite(ServerWorld world, BlockPos spawn) {
		BlockPos best = spawn;
		int bestScore = Integer.MAX_VALUE;
		int bestDistance = Integer.MAX_VALUE;
		for (int dx = -32; dx <= 32; dx += 4) {
			for (int dz = -32; dz <= 32; dz += 4) {
				int cx = spawn.getX() + dx;
				int cz = spawn.getZ() + dz;
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				int wet = 0;
				for (int x = -4; x <= 11; x += 3) {
					for (int z = -4; z <= 12; z += 4) {
						int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + x, cz + z);
						min = Math.min(min, y);
						max = Math.max(max, y);
						if (!world.getFluidState(new BlockPos(cx + x, y - 1, cz + z)).isEmpty()) wet++;
					}
				}
				int score = (max - min) + wet * 8;
				int distance = Math.abs(dx) + Math.abs(dz);
				if (score < bestScore || (score == bestScore && distance < bestDistance)) {
					bestScore = score;
					bestDistance = distance;
					best = new BlockPos(cx, 0, cz);
				}
			}
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, best.getX(), best.getZ());
		return new BlockPos(best.getX(), top - 1, best.getZ());
	}

	/**
	 * Builds a pod. {@code unit} names the wall terminal's pages; {@code playerSite} adds the things around the
	 * starter pod that the opening is about (the wide pad, the assay crate, the slab, the collar, the seam).
	 */
	public static void build(ServerWorld world, BlockPos origin, String unit, boolean playerSite) {
		BlockState plating = ModBlocks.HULL_PLATING.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		// Foundation down into the ground, and clear sky above the pod so the solar collectors and farm see the sun.
		for (int z = NORTH; z < NORTH + LAYERS[0].length; z++) {
			for (int x = WEST; x < WEST + 9; x++) {
				if (LAYERS[0][z - NORTH].charAt(x - WEST) == ' ') continue;
				for (int y = -1; y >= -10; y--) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.getBlockState(pos).isSolidBlock(world, pos)) set(world, pos, plating);
				}
				for (int y = 6; y <= 14; y++) {
					set(world, origin.add(x, y, z), air);
				}
			}
		}
		// A landing pad outside the outer door, room for two chassis, on a plinth if the ground falls away.
		plinth(world, origin, -2, 8, 2, 12, ModBlocks.CAUSTIC_SANDSTONE.getDefaultState(), 4);

		List<Runnable> deferred = new ArrayList<>();
		for (int y = 0; y < LAYERS.length; y++) {
			String[] rows = LAYERS[y];
			for (int row = 0; row < rows.length; row++) {
				for (int col = 0; col < rows[row].length(); col++) {
					char c = rows[row].charAt(col);
					if (c == ' ') continue;
					BlockPos pos = origin.add(WEST + col, y, NORTH + row);
					place(world, pos, c, deferred, unit, playerSite);
				}
			}
		}
		// Doors, beds and crops depend on their neighbours, so they go in after the shell exists.
		deferred.forEach(Runnable::run);

		dockingStation(world, origin);
		if (playerSite) surroundings(world, origin);
	}

	/**
	 * The docking station every base has: a three by three frame of reinforced glass in the west wall with the
	 * collar door in the middle, an apron outside it long enough for a crawler to back onto, and the relay mast
	 * on the roof that the company's numbers go up.
	 */
	private static void dockingStation(ServerWorld world, BlockPos origin) {
		BlockPos collar = origin.add(DOCK_COLLAR);
		BlockState glass = ModBlocks.REINFORCED_GLASS.getDefaultState();
		for (int dz = -1; dz <= 1; dz++) {
			for (int dy = 0; dy <= 2; dy++) {
				if (dz == 0 && dy < 2) continue;
				set(world, collar.add(0, dy, dz), glass);
			}
		}
		set(world, collar, dockDoor(DoubleBlockHalf.LOWER));
		set(world, collar.up(), dockDoor(DoubleBlockHalf.UPPER));
		plinth(world, origin, APRON_MIN.getX(), APRON_MIN.getZ(), APRON_MAX.getX(), APRON_MAX.getZ(), ModBlocks.CAUSTIC_SANDSTONE.getDefaultState(), 6);
		mast(world, origin.add(4, 6, -4));
	}

	/** A thin mast with a lamp and an aerial, until there is a proper dish. */
	private static void mast(ServerWorld world, BlockPos foot) {
		for (int y = 0; y < 6; y++) set(world, foot.up(y), Blocks.IRON_BARS.getDefaultState());
		set(world, foot.up(6), Blocks.SEA_LANTERN.getDefaultState());
		set(world, foot.up(7), Blocks.LIGHTNING_ROD.getDefaultState());
	}

	/** A collar door half, facing west like every collar. */
	public static BlockState dockDoor(DoubleBlockHalf half) {
		return ModBlocks.DOCK_DOOR.getDefaultState()
				.with(DoorBlock.FACING, Direction.WEST)
				.with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.OPEN, false)
				.with(DoorBlock.POWERED, false)
				.with(DoorBlock.HALF, half)
				.with(DockDoorBlock.DOCKED, false);
	}

	/** The pad, the crate, the slab, the collar and the seam: what the first three days point at. */
	private static void surroundings(ServerWorld world, BlockPos origin) {
		// The company's crate, by the door, with a label nobody asked for.
		BlockPos crate = origin.add(ASSAY_CRATE);
		set(world, crate, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
		wallSign(world, crate.north(), Direction.NORTH, "assay");

		// Module two: a plated slab exactly where the annex is at Site Two, and nothing on it.
		plinth(world, origin, FOUNDATION_MIN.getX(), FOUNDATION_MIN.getZ(), FOUNDATION_MAX.getX(), FOUNDATION_MAX.getZ(), ModBlocks.HULL_PLATING.getDefaultState(), 5);
		standingSign(world, origin.add(6, 1, 0), 4, "module");

		// The docking collar's sign, at the pod end of the apron, for the vehicle that does not exist yet.
		standingSign(world, origin.add(-6, 1, 0), 0, "dock");

		// Something to point at on the walk: a sulfur seam and a scrap heap south of the pad.
		for (int[] d : new int[][]{{3, 17}, {4, 17}, {4, 18}, {5, 18}, {3, 18}}) {
			BlockPos top = surface(world, origin.add(d[0], 0, d[1]));
			set(world, top.down(), ModBlocks.SULFUR_CRUST.getDefaultState());
		}
		BlockPos heap = surface(world, origin.add(7, 0, 18));
		set(world, heap, ModBlocks.SCRAP_HEAP.getDefaultState());
	}

	/**
	 * A level floor of {@code fill} at y = 0 over the rectangle, filled down to the ground where it falls away
	 * and cleared {@code clear} high. The top layer is always laid, whatever was there: a slab is a slab.
	 */
	/** Levels a rectangle at the origin height: filled below, cleared {@code clear} blocks above. */
	public static void plinth(ServerWorld world, BlockPos origin, int x0, int z0, int x1, int z1, BlockState fill, int clear) {
		BlockState air = Blocks.AIR.getDefaultState();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				BlockPos floor = origin.add(x, 0, z);
				set(world, floor, fill);
				for (int y = -1; y >= -6; y--) {
					BlockPos pos = origin.add(x, y, z);
					if (!world.getBlockState(pos).isSolidBlock(world, pos)) set(world, pos, fill);
				}
				for (int y = 1; y <= clear; y++) set(world, origin.add(x, y, z), air);
			}
		}
	}

	private static void place(ServerWorld world, BlockPos pos, char c, List<Runnable> deferred, String unit, boolean playerSite) {
		switch (c) {
			case '#' -> set(world, pos, ModBlocks.HULL_PLATING.getDefaultState());
			case 'G' -> set(world, pos, ModBlocks.REINFORCED_GLASS.getDefaultState());
			case 'g' -> set(world, pos, Blocks.GLOWSTONE.getDefaultState());
			case '.' -> set(world, pos, Blocks.AIR.getDefaultState());
			case 'F' -> set(world, pos, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
			case 'W' -> set(world, pos, Blocks.WATER.getDefaultState());
			case 'w' -> deferred.add(() -> set(world, pos, Blocks.WHEAT.getDefaultState()));
			case 'p' -> deferred.add(() -> set(world, pos, Blocks.POTATOES.getDefaultState()));
			case 'L' -> {
				set(world, pos, ModBlocks.LIFE_SUPPORT.getDefaultState().with(LifeSupportBlock.FACING, Direction.SOUTH));
				deferred.add(() -> {
					// Nobody should wake up in a pod that is still scrubbing its first lungful.
					if (world.getBlockEntity(pos) instanceof LifeSupportBlockEntity lifeSupport) {
						lifeSupport.addEnergy(Surrogate.CONFIG.lifeSupportEnergyCapacity);
						lifeSupport.getVolume().quality = 1f;
					}
				});
			}
			case 'P' -> set(world, pos, ModBlocks.POWER_CONDUIT.getDefaultState());
			case 'H' -> set(world, pos, ModBlocks.DECON_SHOWER.getDefaultState());
			case 'S' -> set(world, pos, ModBlocks.SOLAR_COLLECTOR.getDefaultState());
			case 'C' -> set(world, pos, ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.EAST));
			case 'K' -> {
				set(world, pos, ModBlocks.CHARGING_DOCK.getDefaultState().with(ChargingDockBlock.FACING, Direction.SOUTH));
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof ChargingDockBlockEntity dock) dock.addEnergy(Surrogate.CONFIG.dockEnergyCapacity / 2);
				});
			}
			case 'T' -> set(world, pos, Blocks.CRAFTING_TABLE.getDefaultState());
			case 'M' -> {
				set(world, pos, ModBlocks.TERMINAL.getDefaultState().with(TerminalBlock.FACING, Direction.SOUTH));
				if (world.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) terminal.setUnit(unit);
			}
			case 'f' -> set(world, pos, Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
			case 'X' -> {
				set(world, pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.WEST));
				deferred.add(() -> {
					if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
						if (playerSite) fillChest(chest);
						else fillStationChest(chest);
					}
				});
			}
			// The pillow end is against the south wall: the bed points south, foot to the north of the head.
			case 'B' -> deferred.add(() -> set(world, pos, Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.HEAD)));
			case 'b' -> deferred.add(() -> set(world, pos, Blocks.WHITE_BED.getDefaultState().with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.FOOT)));
			case 'D' -> deferred.add(() -> set(world, pos, door(DoubleBlockHalf.LOWER)));
			case 'd' -> deferred.add(() -> set(world, pos, door(DoubleBlockHalf.UPPER)));
			default -> throw new IllegalArgumentException("Unknown habitat glyph " + c);
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

	public static void set(ServerWorld world, BlockPos pos, BlockState state) {
		// No shape updates (FORCE_STATE): the world drops SKIP_DROPS from the updates it sends to neighbours, so
		// a door half or a crop placed before its partner or its light would pop off as an item. Every pair is
		// placed whole, so nothing needs them. No drops: a rebuild over an existing pod must not scatter it.
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
	}

	public static BlockPos surface(ServerWorld world, BlockPos column) {
		return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
	}

	// ------------------------------------------------------------------ signs and books

	/** A wall sign whose four lines come from {@code sign.surrogate.<key>.1..4}; missing lines stay blank. */
	public static void wallSign(ServerWorld world, BlockPos pos, Direction facing, String key) {
		BlockState state = Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing);
		set(world, pos, state);
		label(world, pos, state, key);
	}

	/** A standing sign; {@code rotation} 0 faces south, 4 west, 8 north, 12 east. */
	public static void standingSign(ServerWorld world, BlockPos pos, int rotation, String key) {
		BlockState state = Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, rotation);
		set(world, pos, state);
		label(world, pos, state, key);
	}

	private static void label(ServerWorld world, BlockPos pos, BlockState state, String key) {
		if (!(world.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
		SignText text = new SignText().withColor(DyeColor.WHITE).withGlowing(false);
		for (int i = 0; i < 4; i++) {
			String lineKey = "sign.surrogate." + key + "." + (i + 1);
			Text line = Text.translatable(lineKey);
			text = text.withMessage(i, line.getString().equals(lineKey) ? Text.empty() : line);
		}
		sign.setText(text, true);
		sign.setWaxed(true);
		sign.markDirty();
		world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
	}

	/** A written book from {@code book.surrogate.<key>.title/author/page1..N}. */
	public static ItemStack book(String key, int pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<RawFilteredPair<Text>> content = new ArrayList<>();
		for (int i = 1; i <= pages; i++) content.add(RawFilteredPair.of(Text.translatable("book.surrogate." + key + ".page" + i)));
		book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
				RawFilteredPair.of(Text.translatable("book.surrogate." + key + ".title").getString()),
				Text.translatable("book.surrogate." + key + ".author").getString(), 0, content, true));
		return book;
	}

	// ------------------------------------------------------------------ chests

	private static void fillChest(ChestBlockEntity chest) {
		int slot = 0;
		List<ItemStack> kit = List.of(
				book("guide", 8),
				new ItemStack(ModItems.ROBOT_CHASSIS),
				new ItemStack(ModItems.UPLINK_CARD),
				new ItemStack(ModItems.FIELD_RADIO),
				new ItemStack(ModItems.MINING_DRILL),
				new ItemStack(ModItems.ARC_CUTTER),
				new ItemStack(ModItems.ATMO_SCANNER),
				new ItemStack(ModItems.WRENCH),
				new ItemStack(ModItems.POWER_CELL, 4),
				new ItemStack(ModItems.REPAIR_KIT, 2),
				new ItemStack(ModItems.HULL_PLATING, 48),
				new ItemStack(ModItems.REINFORCED_GLASS, 12),
				new ItemStack(ModItems.AIRLOCK_DOOR, 2),
				new ItemStack(ModItems.POWER_CONDUIT, 8),
				new ItemStack(Items.BREAD, 16),
				new ItemStack(Items.OAK_LOG, 16),
				new ItemStack(Items.IRON_INGOT, 8),
				new ItemStack(Items.TORCH, 16),
				new ItemStack(Items.WATER_BUCKET),
				new ItemStack(Items.WHEAT_SEEDS, 8),
				new ItemStack(Items.BONE_MEAL, 8));
		for (ItemStack stack : kit) {
			if (slot >= chest.size()) break;
			chest.setStack(slot++, stack);
		}
		chest.markDirty();
	}

	/** Halloran's pod chest at Site Two: two hundred days of a tidy person. */
	private static void fillStationChest(ChestBlockEntity chest) {
		int slot = 0;
		List<ItemStack> kit = List.of(
				book("guide", 8),
				new ItemStack(ModItems.REPAIR_KIT, 3),
				new ItemStack(ModItems.POWER_CELL, 2),
				new ItemStack(ModItems.HULL_PLATING, 24),
				new ItemStack(ModItems.REINFORCED_GLASS, 6),
				new ItemStack(Items.BREAD, 10),
				new ItemStack(Items.IRON_INGOT, 6),
				new ItemStack(Items.TORCH, 8),
				new ItemStack(Items.WHEAT_SEEDS, 4));
		for (ItemStack stack : kit) {
			if (slot >= chest.size()) break;
			chest.setStack(slot++, stack);
		}
		chest.markDirty();
	}
}
