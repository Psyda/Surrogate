package dev.psyda.surrogate.transit;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.BreachedPlatingBlock;
import dev.psyda.surrogate.block.MicrowaveBlock;
import dev.psyda.surrogate.block.TerminalBlock;
import dev.psyda.surrogate.block.TerminalBlockEntity;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.block.PosterBlock;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LightBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.block.ButtonBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.registry.Registries;
import net.minecraft.util.Clearable;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The SSV Provender: a supply hulk with a bridge at the bow, a spine of a corridor with the crew rooms off
 * it, a cargo hold full of pilots asleep in chairs, and a drop pod bolted on behind. Built in the void of
 * the transit dimension around {@link TransitDimension#ORIGIN}, which is deck level; standing height is
 * one above. The bow points north.
 *
 * <p>Rooms are boxes of interior cells; the hull is whatever touches them. Windows are hull cells swapped
 * for glass. Everything else is furniture placed by coordinate.
 */
public final class ShipBuilder {
	/** An inclusive box of interior cells, relative to the deck origin. */
	public record Room(int x1, int y1, int z1, int x2, int y2, int z2) {
		public Box box(BlockPos origin) {
			return new Box(origin.getX() + x1, origin.getY() + y1, origin.getZ() + z1,
					origin.getX() + x2 + 1, origin.getY() + y2 + 1, origin.getZ() + z2 + 1);
		}

		public boolean contains(BlockPos origin, Vec3d pos) {
			return box(origin).contains(pos);
		}

		void cells(List<BlockPos> out) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					for (int x = x1; x <= x2; x++) out.add(new BlockPos(x, y, z));
				}
			}
		}
	}

	public static final Room BRIDGE = new Room(-4, 1, -20, 4, 3, -14);
	public static final Room BRIDGE_DOOR = new Room(-1, 1, -13, 1, 2, -13);
	public static final Room CORRIDOR = new Room(-1, 1, -12, 1, 3, 3);
	public static final Room MEDBAY = new Room(3, 1, -12, 7, 3, -8);
	public static final Room MEDBAY_DOOR = new Room(2, 1, -10, 2, 2, -10);
	public static final Room BUNKS = new Room(-7, 1, -12, -3, 3, -8);
	public static final Room BUNKS_DOOR = new Room(-2, 1, -10, -2, 2, -10);
	public static final Room GALLEY = new Room(-7, 1, -5, -3, 3, 0);
	public static final Room GALLEY_DOOR = new Room(-2, 1, -3, -2, 2, -3);
	public static final Room ENGINEERING = new Room(3, 1, -5, 7, 3, 0);
	public static final Room ENGINEERING_DOOR = new Room(2, 1, -3, 2, 2, -3);
	public static final Room HOLD_DOOR = new Room(0, 1, 4, 0, 2, 4);
	public static final Room HOLD = new Room(-6, 1, 5, 6, 4, 20);
	public static final Room HATCH = new Room(5, 1, 21, 5, 2, 21);
	public static final Room DROPBAY = new Room(3, 1, 22, 7, 3, 25);

	private static final Room[] ROOMS = {BRIDGE, BRIDGE_DOOR, CORRIDOR, MEDBAY, MEDBAY_DOOR, BUNKS, BUNKS_DOOR, GALLEY, GALLEY_DOOR,
			ENGINEERING, ENGINEERING_DOOR, HOLD_DOOR, HOLD, HATCH, DROPBAY};

	/** Hull cells that are glass instead. */
	private static final Room[] WINDOWS = {
			new Room(-3, 1, -21, 3, 3, -21),      // bridge, forward
			new Room(-5, 2, -19, -5, 3, -16),     // bridge, port
			new Room(5, 2, -19, 5, 3, -16),       // bridge, starboard
			new Room(-8, 1, -4, -8, 3, -1),       // galley, port: the turnover window
			new Room(8, 2, -4, 8, 3, -2),         // engineering
			new Room(-8, 2, -10, -8, 2, -9),      // bunks porthole
			new Room(8, 2, -9, 8, 2, -8),         // med bay porthole
			new Room(-7, 2, 7, -7, 3, 9),         // hold, port forward
			new Room(-7, 2, 15, -7, 3, 17),       // hold, port aft
			new Room(7, 2, 7, 7, 3, 9),           // hold, starboard forward
			new Room(7, 2, 15, 7, 3, 17),         // hold, starboard aft
			new Room(-4, 1, 21, 2, 3, 21),        // hold, aft: Sallow after turnover
			new Room(4, 1, 26, 6, 3, 26),         // drop pod, aft
			new Room(8, 2, 23, 8, 2, 24),         // drop pod, side
	};

	// ---- Places. Vec3d for feet, BlockPos for blocks.
	public static final Vec3d PLAYER_WAKE = new Vec3d(4.5, 1, -10.5);
	public static final BlockPos COT_HEAD = new BlockPos(6, 1, -11);
	public static final BlockPos COT_FOOT = new BlockPos(5, 1, -11);
	public static final BlockPos LINK_CHAIR = new BlockPos(6, 1, -9);
	public static final BlockPos MEDBAY_LECTERN = new BlockPos(3, 1, -8);
	public static final BlockPos MEDBAY_LOCKER = new BlockPos(7, 1, -12);
	public static final Vec3d FERREIRA_START = new Vec3d(5.5, 1, -9.5);
	public static final Vec3d FERREIRA_DESK = new Vec3d(4.5, 1, -8.5);
	public static final Vec3d FERREIRA_CHAIRSIDE = new Vec3d(5.5, 1, -8.5);

	public static final BlockPos CAPTAIN_CHAIR = new BlockPos(0, 1, -18);
	public static final BlockPos BRIDGE_LECTERN = new BlockPos(3, 1, -15);
	public static final Vec3d CASTELLANOS_START = new Vec3d(0.5, 1, -16.5);
	public static final Vec3d CASTELLANOS_WINDOW = new Vec3d(-1.5, 1, -19.5);
	public static final Vec3d CASTELLANOS_CHAIR = new Vec3d(0.5, 1, -18.5);
	public static final Vec3d BRIDGE_PLAYER = new Vec3d(1.5, 1, -17.5);
	public static final Vec3d TEAGUE_BRIDGE = new Vec3d(3.5, 1, -14.5);
	public static final Vec3d FERREIRA_BRIDGE = new Vec3d(-3.5, 1, -14.5);

	public static final BlockPos PLAYER_BED_HEAD = new BlockPos(-7, 1, -12);
	public static final BlockPos PLAYER_BED_FOOT = new BlockPos(-6, 1, -12);
	public static final Vec3d BUNK_STAND = new Vec3d(-4.5, 1, -11.5);
	public static final BlockPos BUNK_LOCKER = new BlockPos(-3, 1, -12);
	public static final Vec3d TEAGUE_BUNK_DOOR = new Vec3d(-3.5, 1, -9.5);
	/** Seven lamps along the corridor wall by the bunks, lit one per day. */
	public static final BlockPos[] DAY_LAMPS = new BlockPos[7];

	public static final BlockPos GALLEY_TABLE = new BlockPos(-7, 1, -5);
	/** The galley unit, on a counter by the table. */
	public static final BlockPos GALLEY_UNIT = new BlockPos(-7, 2, -4);
	/** The bridge terminal: the one console on the port side that is not a print. */
	public static final BlockPos BRIDGE_TERMINAL = new BlockPos(-5, 1, -19);
	public static final BlockPos GALLEY_CHEST_A = new BlockPos(-7, 1, 0);
	public static final BlockPos GALLEY_CHEST_B = new BlockPos(-6, 1, 0);
	public static final Vec3d GALLEY_WINDOW_STAND = new Vec3d(-6.5, 1, -2.5);
	public static final Vec3d CAT_SPOT = new Vec3d(-4.5, 1, -1.5);
	/** The general alarm, under a sign that says not to. */
	public static final BlockPos ALARM_BUTTON = new BlockPos(4, 1, -20);

	public static final BlockPos FORWARD_LIFE_SUPPORT = new BlockPos(7, 1, -5);
	public static final Vec3d TEAGUE_START = new Vec3d(5.5, 1, -2.5);

	public static final BlockPos PATCH_LOCKER = new BlockPos(1, 1, 3);
	public static final BlockPos HOLD_DOOR_POS = new BlockPos(0, 1, 4);
	public static final BlockPos HOLD_LIFE_SUPPORT = new BlockPos(6, 1, 5);
	public static final int SLEEPER_COUNT = 12;
	public static final int VASQUEZ = 6;
	public static final Vec3d VASQUEZ_FLOOR = new Vec3d(4.5, 1, 7.5);
	public static final BlockPos COOLANT_LEVER = new BlockPos(-6, 2, 19);
	public static final BlockPos BREACH = new BlockPos(7, 2, 12);
	public static final BlockPos MANIFEST_LECTERN = new BlockPos(0, 1, 19);
	public static final Vec3d CALIBRATION_ROBOT = new Vec3d(0.5, 1, 7.5);
	public static final Vec3d TEAGUE_HOLD = new Vec3d(-3.5, 1, 18.5);
	public static final Vec3d TEAGUE_HOLD_ENTRY = new Vec3d(0.5, 1, 6.5);
	public static final Vec3d FERREIRA_HOLD = new Vec3d(4.5, 1, 8.5);
	public static final Vec3d TEAGUE_VASQUEZ = new Vec3d(4.5, 1, 6.5);
	public static final Vec3d TEAGUE_BREACH = new Vec3d(5.5, 1, 11.5);
	public static final Vec3d CASTELLANOS_AFT_WINDOW = new Vec3d(-3.5, 1, 19.5);
	public static final Vec3d PLAYER_AFT_WINDOW = new Vec3d(0.5, 1, 19.5);
	public static final Room AFT_WINDOW_AREA = new Room(-4, 1, 17, 2, 4, 20);

	public static final BlockPos HATCH_POS = new BlockPos(5, 1, 21);
	public static final BlockPos POD_SEAT = new BlockPos(5, 1, 24);
	public static final Vec3d POD_SEAT_POS = new Vec3d(5.5, 1, 24.5);
	public static final BlockPos DROPBAY_LIFE_SUPPORT = new BlockPos(7, 1, 22);
	public static final Vec3d CASTELLANOS_DROPBAY = new Vec3d(3.5, 1, 23.5);
	public static final Vec3d FERREIRA_DROPBAY = new Vec3d(6.5, 1, 23.5);
	public static final Vec3d TEAGUE_DROPBAY = new Vec3d(4.5, 1, 22.5);

	/** Ceiling lamps. The dim night lights go in the cell below each. */
	private static final BlockPos[] BULBS = {
			new BlockPos(0, 4, -17), new BlockPos(-3, 4, -17), new BlockPos(3, 4, -17), new BlockPos(0, 4, -15),
			new BlockPos(0, 4, -11), new BlockPos(0, 4, -7), new BlockPos(0, 4, -3), new BlockPos(0, 4, 1),
			new BlockPos(5, 4, -10), new BlockPos(-5, 4, -10), new BlockPos(-5, 4, -2), new BlockPos(5, 4, -2),
			new BlockPos(-3, 5, 7), new BlockPos(3, 5, 7), new BlockPos(-3, 5, 12), new BlockPos(3, 5, 12),
			new BlockPos(-3, 5, 17), new BlockPos(3, 5, 17), new BlockPos(5, 4, 23)};

	/** The bridge screens that turn red when something is wrong. */
	private static final BlockPos[] BRIDGE_CONSOLES = {
			new BlockPos(-4, 1, -21), new BlockPos(-4, 2, -21), new BlockPos(4, 1, -21), new BlockPos(4, 2, -21),
			new BlockPos(-5, 1, -18), new BlockPos(5, 1, -19), new BlockPos(5, 1, -18)};
	private static final Direction[] BRIDGE_CONSOLE_FACING = {
			Direction.SOUTH, Direction.SOUTH, Direction.SOUTH, Direction.SOUTH,
			Direction.EAST, Direction.WEST, Direction.WEST};

	static {
		for (int i = 0; i < 7; i++) DAY_LAMPS[i] = new BlockPos(-2, 3, -12 + i);
	}

	private ShipBuilder() {
	}

	// ------------------------------------------------------------------ chairs

	public static BlockPos sleeperChair(int index) {
		boolean east = index >= 6;
		int z = 7 + (index % 6) * 2;
		return new BlockPos(east ? 6 : -6, 1, z);
	}

	public static Direction sleeperFacing(int index) {
		return index >= 6 ? Direction.WEST : Direction.EAST;
	}

	private static BlockPos sleeperSign(int index) {
		BlockPos chair = sleeperChair(index);
		return new BlockPos(chair.getX(), 4, chair.getZ());
	}

	// ------------------------------------------------------------------ building

	public static void build(ServerWorld world, BlockPos origin) {
		BlockState plating = ModBlocks.HULL_PLATING.getDefaultState();
		BlockState glass = ModBlocks.REINFORCED_GLASS.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		wipe(world, origin, air);

		// Interior cells, then a hull around all of them, including corners.
		List<BlockPos> cells = new ArrayList<>();
		for (Room room : ROOMS) room.cells(cells);
		Set<Long> interior = new HashSet<>();
		for (BlockPos cell : cells) interior.add(cell.asLong());
		Set<Long> hull = new HashSet<>();
		for (BlockPos cell : cells) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						long packed = cell.add(dx, dy, dz).asLong();
						if (!interior.contains(packed)) hull.add(packed);
					}
				}
			}
		}
		for (long packed : hull) set(world, origin.add(BlockPos.fromLong(packed)), plating);
		for (BlockPos cell : cells) {
			// A rebuild over an existing ship: empty the lockers and lecterns first or they scatter their contents.
			BlockPos pos = origin.add(cell);
			if (world.getBlockEntity(pos) instanceof Clearable clearable) clearable.clear();
			set(world, pos, air);
		}
		List<BlockPos> windowCells = new ArrayList<>();
		for (Room window : WINDOWS) window.cells(windowCells);
		for (BlockPos cell : windowCells) {
			if (hull.contains(cell.asLong())) set(world, origin.add(cell), glass);
		}

		// Prints on the walls.
		for (int i = 0; i < BRIDGE_CONSOLES.length; i++) poster(world, origin, BRIDGE_CONSOLES[i], PosterBlock.Print.CONSOLE, BRIDGE_CONSOLE_FACING[i]);
		terminal(world, origin, BRIDGE_TERMINAL, "bridge", Direction.EAST);
		poster(world, origin, new BlockPos(-3, 2, -13), PosterBlock.Print.PROVENDER, Direction.NORTH);
		poster(world, origin, new BlockPos(8, 3, -12), PosterBlock.Print.BODY_TOP, Direction.WEST);
		poster(world, origin, new BlockPos(8, 2, -12), PosterBlock.Print.BODY_BOTTOM, Direction.WEST);
		terminal(world, origin, new BlockPos(8, 1, -9), "medbay", Direction.WEST);
		poster(world, origin, new BlockPos(-8, 3, -8), PosterBlock.Print.SALLOW_TOP, Direction.EAST);
		poster(world, origin, new BlockPos(-8, 2, -8), PosterBlock.Print.SALLOW_BOTTOM, Direction.EAST);
		poster(world, origin, new BlockPos(-8, 3, -5), PosterBlock.Print.BODY_TOP, Direction.EAST);
		poster(world, origin, new BlockPos(-8, 2, -5), PosterBlock.Print.BODY_BOTTOM, Direction.EAST);
		terminal(world, origin, new BlockPos(8, 1, -1), "engineering", Direction.WEST);
		poster(world, origin, new BlockPos(-3, 2, 4), PosterBlock.Print.MANIFEST, Direction.SOUTH);
		terminal(world, origin, new BlockPos(7, 2, 19), "hold", Direction.WEST);
		terminal(world, origin, new BlockPos(2, 2, 23), "pod", Direction.EAST);
		poster(world, origin, new BlockPos(8, 1, 23), PosterBlock.Print.CONSOLE, Direction.WEST);

		// Lamps in the ceilings, day lamps along the corridor.
		for (BlockPos bulb : BULBS) set(world, origin.add(bulb), Blocks.WAXED_COPPER_BULB.getDefaultState().with(Properties.LIT, true));
		for (BlockPos lamp : DAY_LAMPS) set(world, origin.add(lamp), Blocks.WAXED_COPPER_BULB.getDefaultState().with(Properties.LIT, false));

		// Bridge.
		set(world, origin.add(CAPTAIN_CHAIR), ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.NORTH));
		lectern(world, origin.add(BRIDGE_LECTERN), Direction.WEST, book("captain", 3));
		set(world, origin.add(-3, 1, -20), Blocks.WAXED_CUT_COPPER_SLAB.getDefaultState());
		set(world, origin.add(3, 1, -20), Blocks.WAXED_CUT_COPPER_SLAB.getDefaultState());
		set(world, origin.add(-2, 1, -20), Blocks.WAXED_CUT_COPPER_SLAB.getDefaultState());
		set(world, origin.add(2, 1, -20), Blocks.WAXED_CUT_COPPER_SLAB.getDefaultState());
		set(world, origin.add(ALARM_BUTTON), Blocks.STONE_BUTTON.getDefaultState().with(WallMountedBlock.FACE, BlockFace.WALL).with(ButtonBlock.FACING, Direction.SOUTH));
		sign(world, origin.add(4, 2, -20), Direction.SOUTH, "alarm");
		sign(world, origin.add(0, 3, -12), Direction.SOUTH, "bridge");

		// Med bay: the thaw cot, the link chair, the doctor's desk.
		bed(world, origin.add(COT_HEAD), origin.add(COT_FOOT), Direction.EAST, Blocks.WHITE_BED);
		set(world, origin.add(LINK_CHAIR), ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.WEST));
		lectern(world, origin.add(MEDBAY_LECTERN), Direction.NORTH, book("port", 3));
		chest(world, origin.add(MEDBAY_LOCKER), Direction.SOUTH, List.of(new ItemStack(Items.BREAD, 4), new ItemStack(Items.APPLE, 2), new ItemStack(Items.PAPER, 3)));
		set(world, origin.add(3, 1, -12), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		set(world, origin.add(4, 1, -12), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		sign(world, origin.add(1, 3, -10), Direction.WEST, "medbay");

		// Bunks: yours by the door with your name on it, the crew's beyond.
		bed(world, origin.add(PLAYER_BED_HEAD), origin.add(PLAYER_BED_FOOT), Direction.WEST, Blocks.WHITE_BED);
		bed(world, origin.add(-7, 1, -10), origin.add(-6, 1, -10), Direction.WEST, Blocks.LIGHT_GRAY_BED);
		bed(world, origin.add(-7, 1, -8), origin.add(-6, 1, -8), Direction.WEST, Blocks.LIGHT_GRAY_BED);
		chest(world, origin.add(BUNK_LOCKER), Direction.WEST, List.of(book("contract", 2), new ItemStack(Items.LEATHER, 1)));
		sign(world, origin.add(-7, 2, -12), Direction.EAST, "pilot");
		sign(world, origin.add(-1, 3, -10), Direction.EAST, "bunks");

		// Galley.
		set(world, origin.add(GALLEY_TABLE), Blocks.CRAFTING_TABLE.getDefaultState());
		set(world, origin.add(GALLEY_UNIT).down(), Blocks.WAXED_CUT_COPPER.getDefaultState());
		set(world, origin.add(GALLEY_UNIT), ModBlocks.MICROWAVE.getDefaultState().with(MicrowaveBlock.FACING, Direction.EAST));
		chest(world, origin.add(GALLEY_CHEST_A), Direction.NORTH, ChestType.LEFT, List.of(new ItemStack(Items.BREAD, 12), new ItemStack(Items.BAKED_POTATO, 8), new ItemStack(Items.COOKED_BEEF, 6)));
		chest(world, origin.add(GALLEY_CHEST_B), Direction.NORTH, ChestType.RIGHT, List.of(new ItemStack(Items.APPLE, 6), new ItemStack(Items.CARROT, 6), new ItemStack(Items.COOKED_CHICKEN, 4)));
		set(world, origin.add(-5, 1, -2), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		set(world, origin.add(-4, 1, -2), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		set(world, origin.add(-5, 1, -1), Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
		set(world, origin.add(-4, 1, -3), Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
		set(world, origin.add(-3, 1, 0), Blocks.BARREL.getDefaultState());
		sign(world, origin.add(-1, 3, -3), Direction.EAST, "galley");

		// Engineering: the scrubber for the forward compartments and the thing that feeds it.
		lifeSupport(world, origin.add(FORWARD_LIFE_SUPPORT), Direction.WEST);
		for (int x = 5; x <= 7; x++) set(world, origin.add(x, 1, 0), Blocks.WAXED_COPPER_BLOCK.getDefaultState());
		set(world, origin.add(6, 2, 0), Blocks.SEA_LANTERN.getDefaultState());
		set(world, origin.add(5, 2, 0), Blocks.WAXED_COPPER_GRATE.getDefaultState());
		set(world, origin.add(7, 2, 0), Blocks.WAXED_COPPER_GRATE.getDefaultState());
		set(world, origin.add(6, 3, 0), Blocks.LIGHTNING_ROD.getDefaultState());
		for (int z = -4; z <= -1; z++) set(world, origin.add(7, 3, z), ModBlocks.POWER_CONDUIT.getDefaultState());
		set(world, origin.add(3, 1, 0), Blocks.BARREL.getDefaultState());
		set(world, origin.add(3, 1, -1), Blocks.IRON_BLOCK.getDefaultState());
		sign(world, origin.add(1, 3, -3), Direction.WEST, "engineering");

		// Corridor: the locker by the hold door, and the door itself.
		chest(world, origin.add(PATCH_LOCKER), Direction.WEST, List.of(new ItemStack(ModItems.HULL_PLATING, 4), new ItemStack(ModItems.REBREATHER, 1)));
		sign(world, origin.add(1, 2, 3), Direction.NORTH, "patch");
		door(world, origin.add(HOLD_DOOR_POS), Direction.SOUTH);
		sign(world, origin.add(0, 3, 3), Direction.NORTH, "hold");

		// Hold: the rack, the coolant line, the crates, the manifest, the seam that will not hold.
		lifeSupport(world, origin.add(HOLD_LIFE_SUPPORT), Direction.WEST);
		for (int i = 0; i < SLEEPER_COUNT; i++) {
			set(world, origin.add(sleeperChair(i)), ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, sleeperFacing(i)));
			sleeperSign(world, origin, i, false);
		}
		for (int z = 5; z <= 19; z++) {
			if (z >= 7 && z <= 17 && z % 2 == 1) continue;
			set(world, origin.add(-6, 4, z), ModBlocks.POWER_CONDUIT.getDefaultState());
		}
		set(world, origin.add(COOLANT_LEVER), lever(false));
		sign(world, origin.add(-6, 3, 19), Direction.EAST, "coolant");
		for (BlockPos crate : new BlockPos[]{new BlockPos(-3, 1, 10), new BlockPos(-3, 1, 11), new BlockPos(3, 1, 10), new BlockPos(3, 1, 11), new BlockPos(-3, 2, 10), new BlockPos(3, 2, 11)}) {
			set(world, origin.add(crate), Blocks.BARREL.getDefaultState());
		}
		set(world, origin.add(-2, 1, 11), Blocks.IRON_BLOCK.getDefaultState());
		set(world, origin.add(2, 1, 10), Blocks.WAXED_COPPER_BLOCK.getDefaultState());
		lectern(world, origin.add(MANIFEST_LECTERN), Direction.NORTH, book("manifest", 3));
		sign(world, origin.add(6, 3, 12), Direction.WEST, "frame");
		door(world, origin.add(HATCH_POS), Direction.SOUTH);
		sign(world, origin.add(5, 3, 20), Direction.NORTH, "pod");

		// Drop pod.
		lifeSupport(world, origin.add(DROPBAY_LIFE_SUPPORT), Direction.WEST);
		set(world, origin.add(POD_SEAT), ModBlocks.DIVE_CHAIR.getDefaultState().with(DiveChairBlock.FACING, Direction.SOUTH));

		exterior(world, origin);
		sweepItems(world, new Box(origin).expand(40.0));
		Surrogate.LOGGER.info("Transit: built the Provender at {}", origin);
	}

	/** Clears the space the ship occupies, so a rebuild does not leave an older ship's parts outside the windows. */
	private static void wipe(ServerWorld world, BlockPos origin, BlockState air) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = -18; x <= 18; x++) {
			for (int z = -28; z <= 32; z++) {
				for (int y = -4; y <= 10; y++) {
					pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (world.getBlockState(pos).isAir()) continue;
					if (world.getBlockEntity(pos) instanceof Clearable clearable) clearable.clear();
					world.setBlockState(pos, air, FLAGS);
				}
			}
		}
		// Whatever still fell out is swept up.
		sweepItems(world, new Box(origin).expand(40.0));
	}

	/**
	 * Removes every dropped item in {@code box}. {@code getOtherEntities} only sees entities in chunk sections
	 * the server is already tracking, and a dimension that was just loaded to build in has none of those yet,
	 * so anything that fell during the build would survive a sweep through it and turn up on the deck later.
	 */
	static void sweepItems(ServerWorld world, Box box) {
		List<Entity> found = new ArrayList<>();
		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof ItemEntity && box.contains(entity.getPos())) found.add(entity);
		}
		for (Entity item : found) item.discard();
	}

	/** What is outside the windows: a nose, radiator fins, an engine bell, running lights. */
	private static void exterior(ServerWorld world, BlockPos origin) {
		BlockState plating = ModBlocks.HULL_PLATING.getDefaultState();
		BlockState dark = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		for (int dz = 0; dz <= 3; dz++) {
			int half = 3 - dz;
			for (int x = -half; x <= half; x++) {
				for (int y = -1; y <= 0; y++) setIfAir(world, origin.add(x, y, -22 - dz), plating);
			}
		}
		// Radiator fins at deck level, below the windows and below the seam at rib 12, so the breach looks out at stars.
		for (int x = 9; x <= 15; x++) {
			for (int z = 10; z <= 14; z++) {
				setIfAir(world, origin.add(x, 0, z), dark);
				setIfAir(world, origin.add(-x, 0, z), dark);
			}
		}
		for (int x = -13; x <= -9; x++) {
			for (int z = 18; z <= 24; z++) {
				for (int y = 0; y <= 4; y++) setIfAir(world, origin.add(x, y, z), Blocks.DEEPSLATE_TILES.getDefaultState());
			}
		}
		for (int x = -12; x <= -10; x++) {
			for (int y = 1; y <= 3; y++) setIfAir(world, origin.add(x, y, 25), Blocks.WAXED_COPPER_BLOCK.getDefaultState());
		}
		setIfAir(world, origin.add(-11, 2, 25), Blocks.SEA_LANTERN.getDefaultState());
		for (int z = 16; z <= 18; z++) setIfAir(world, origin.add(-8, 2, z), Blocks.DEEPSLATE_TILES.getDefaultState());
		setIfAir(world, origin.add(0, 5, -17), Blocks.WAXED_COPPER_BLOCK.getDefaultState());
		setIfAir(world, origin.add(0, 6, -17), Blocks.LIGHTNING_ROD.getDefaultState());
		setIfAir(world, origin.add(9, 1, -14), Blocks.VERDANT_FROGLIGHT.getDefaultState());
		setIfAir(world, origin.add(-9, 1, -14), Blocks.MAGMA_BLOCK.getDefaultState());
		setIfAir(world, origin.add(9, 1, 20), Blocks.OCHRE_FROGLIGHT.getDefaultState());
	}

	// ------------------------------------------------------------------ placement helpers

	/**
	 * Placement flags for everything the builders do. No shape updates ({@code FORCE_STATE}): the world strips
	 * {@code SKIP_DROPS} from the updates it sends to neighbours, so a sign whose wall is not there yet, a bed
	 * half without its partner or a door half on its own would pop off as an item the moment it was placed.
	 * Every pair here is placed whole and in order, so nothing needs the updates.
	 */
	static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

	private static void set(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, FLAGS);
	}

	private static void setIfAir(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.getBlockState(pos).isAir()) set(world, pos, state);
	}

	private static void poster(ServerWorld world, BlockPos origin, BlockPos rel, PosterBlock.Print print, Direction facing) {
		set(world, origin.add(rel), ModBlocks.POSTER.getDefaultState().with(PosterBlock.PRINT, print).with(PosterBlock.FACING, facing));
	}

	private static void terminal(ServerWorld world, BlockPos origin, BlockPos rel, String unit, Direction facing) {
		BlockPos pos = origin.add(rel);
		set(world, pos, ModBlocks.TERMINAL.getDefaultState().with(TerminalBlock.FACING, facing));
		if (world.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) terminal.setUnit(unit);
	}

	private static void bed(ServerWorld world, BlockPos head, BlockPos foot, Direction facing, Block bed) {
		set(world, foot, bed.getDefaultState().with(BedBlock.FACING, facing).with(BedBlock.PART, BedPart.FOOT));
		set(world, head, bed.getDefaultState().with(BedBlock.FACING, facing).with(BedBlock.PART, BedPart.HEAD));
	}

	private static void chest(ServerWorld world, BlockPos pos, Direction facing, List<ItemStack> contents) {
		chest(world, pos, facing, ChestType.SINGLE, contents);
	}

	/** Without shape updates two chests side by side stay single unless told which half of a pair they are. */
	private static void chest(ServerWorld world, BlockPos pos, Direction facing, ChestType type, List<ItemStack> contents) {
		set(world, pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing).with(ChestBlock.CHEST_TYPE, type));
		if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			int slot = 0;
			for (ItemStack stack : contents) {
				if (slot >= chest.size()) break;
				chest.setStack(slot++, stack);
			}
			chest.markDirty();
		}
	}

	private static void lectern(ServerWorld world, BlockPos pos, Direction facing, ItemStack book) {
		BlockState state = Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, facing);
		set(world, pos, state);
		LecternBlock.putBookIfAbsent(null, world, pos, world.getBlockState(pos), book);
	}

	private static void lifeSupport(ServerWorld world, BlockPos pos, Direction facing) {
		set(world, pos, ModBlocks.LIFE_SUPPORT.getDefaultState().with(LifeSupportBlock.FACING, facing));
		if (world.getBlockEntity(pos) instanceof LifeSupportBlockEntity unit) {
			unit.addEnergy(Surrogate.CONFIG.lifeSupportEnergyCapacity);
			unit.getVolume().quality = 1f;
		}
	}

	private static void door(ServerWorld world, BlockPos lower, Direction facing) {
		BlockState base = ModBlocks.AIRLOCK_DOOR.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HINGE, DoorHinge.LEFT)
				.with(DoorBlock.OPEN, false).with(DoorBlock.POWERED, false);
		set(world, lower, base.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(world, lower.up(), base.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private static BlockState lever(boolean on) {
		return Blocks.LEVER.getDefaultState().with(WallMountedBlock.FACE, BlockFace.WALL).with(LeverBlock.FACING, Direction.EAST).with(LeverBlock.POWERED, on);
	}

	/** A glowing wall label. The lines live under {@code sign.surrogate.<key>.1..4}. */
	private static void sign(ServerWorld world, BlockPos pos, Direction facing, String key) {
		Text[] lines = new Text[4];
		for (int i = 0; i < 4; i++) {
			String lineKey = "sign.surrogate." + key + "." + (i + 1);
			Text line = Text.translatable(lineKey);
			lines[i] = line.getString().equals(lineKey) ? Text.empty() : line;
		}
		sign(world, pos, facing, lines);
	}

	private static void sign(ServerWorld world, BlockPos pos, Direction facing, Text[] lines) {
		sign(world, pos, facing, lines, true);
	}

	/** Glowing text is drawn nine times a frame; a rack of twelve labels does not need that. */
	private static void sign(ServerWorld world, BlockPos pos, Direction facing, Text[] lines, boolean glowing) {
		BlockState state = Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing);
		set(world, pos, state);
		if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SignText text = new SignText().withColor(DyeColor.WHITE).withGlowing(glowing);
			for (int i = 0; i < 4; i++) text = text.withMessage(i, lines[i] == null ? Text.empty() : lines[i]);
			sign.setText(text, true);
			sign.setWaxed(true);
			sign.markDirty();
			world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
		}
	}

	private static void sleeperSign(ServerWorld world, BlockPos origin, int index, boolean dead) {
		BlockPos pos = origin.add(sleeperSign(index));
		Direction facing = sleeperFacing(index);
		String number = String.format("%02d", index + 1);
		Text[] lines;
		if (index == VASQUEZ) {
			lines = new Text[]{Text.literal(number), Text.translatable("crew.surrogate.sleeper.vasquez"),
					Text.translatable("sign.surrogate.rotation", 3), dead ? Text.translatable("sign.surrogate.deceased") : Text.translatable("sign.surrogate.day", 611)};
		} else {
			int rotation = 1 + (index * 7) % 3;
			int days = 40 + (index * 137) % 500;
			lines = new Text[]{Text.literal(number), Text.translatable("crew.surrogate.sleeper.numbered", number),
					Text.translatable("sign.surrogate.rotation", rotation), Text.translatable("sign.surrogate.day", days)};
		}
		sign(world, pos, facing, lines, false);
	}

	private static ItemStack book(String key, int pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<RawFilteredPair<Text>> content = new ArrayList<>();
		for (int i = 1; i <= pages; i++) content.add(RawFilteredPair.of(Text.translatable("book.surrogate." + key + ".page" + i)));
		book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
				RawFilteredPair.of(Text.translatable("book.surrogate." + key + ".title").getString()),
				Text.translatable("book.surrogate." + key + ".author").getString(), 0, content, true));
		return book;
	}

	// ------------------------------------------------------------------ people and machines

	/** Spawns the crew at their stations, the pilots in their chairs, the calibration unit in the hold, and the cat. */
	public static void spawnCrew(ServerWorld world, BlockPos origin, TransitState state, @Nullable UUID owner) {
		clearCrew(world, origin, state);
		CatEntity cat = spawnCat(world, Vec3d.of(origin).add(CAT_SPOT), owner);
		state.cat = cat == null ? null : cat.getUuid();
		state.castellanos = idOf(spawnPerson(world, origin, Crew.CASTELLANOS, null, CASTELLANOS_START, 180f));
		state.ferreira = idOf(spawnPerson(world, origin, Crew.FERREIRA, null, FERREIRA_START, 90f));
		state.teague = idOf(spawnPerson(world, origin, Crew.TEAGUE, null, TEAGUE_START, 0f));
		state.sleepers.clear();
		for (int i = 0; i < SLEEPER_COUNT; i++) {
			Text name = i == VASQUEZ ? Text.translatable("crew.surrogate.sleeper.vasquez")
					: Text.translatable("crew.surrogate.sleeper.numbered", String.format("%02d", i + 1));
			BlockPos chair = sleeperChair(i);
			Vec3d at = new Vec3d(chair.getX() + 0.5, chair.getY(), chair.getZ() + 0.5);
			CrewEntity sleeper = spawnPerson(world, origin, Crew.SLEEPER, name, at, sleeperFacing(i).asRotation());
			if (sleeper != null) {
				state.sleepers.add(sleeper.getUuid());
				sleeper.sitAt(Vec3d.of(origin).add(at), sleeperFacing(i).asRotation());
			}
		}

		RobotEntity robot = ModEntities.ROBOT.create(world);
		if (robot != null) {
			robot.setCustomName(Text.translatable("crew.surrogate.calibration"));
			robot.setHealth(robot.getMaxHealth());
			robot.setEnergy(robot.getEnergyCapacity());
			Vec3d at = Vec3d.of(origin).add(CALIBRATION_ROBOT);
			robot.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
			robot.setBodyYaw(0f);
			robot.setHeadYaw(0f);
			world.spawnEntity(robot);
			RobotRegistry.get(world.getServer()).update(robot);
			state.calibrationRobot = robot.getUuid();
			if (world.getBlockEntity(origin.add(LINK_CHAIR)) instanceof DiveChairBlockEntity chair) {
				chair.setLink(robot.getUuid(), robot.getName().getString());
			}
		}
		state.markDirty();
	}

	@Nullable
	private static CrewEntity spawnPerson(ServerWorld world, BlockPos origin, Crew who, @Nullable Text name, Vec3d rel, float yaw) {
		CrewEntity person = ModEntities.CREW.create(world);
		if (person == null) return null;
		if (name == null) person.setCharacter(who);
		else person.setCharacter(who, name);
		Vec3d at = Vec3d.of(origin).add(rel);
		person.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		person.face(yaw);
		world.spawnEntity(person);
		return person;
	}

	@Nullable
	private static UUID idOf(@Nullable Entity entity) {
		return entity == null ? null : entity.getUuid();
	}

	/** Ballast: black, tame, sitting, and yours whether you like it or not. */
	@Nullable
	public static CatEntity spawnCat(ServerWorld world, Vec3d at, @Nullable UUID owner) {
		CatEntity cat = EntityType.CAT.create(world);
		if (cat == null) return null;
		cat.setCustomName(Text.translatable("crew.surrogate.cat"));
		cat.setVariant(Registries.CAT_VARIANT.entryOf(CatVariant.ALL_BLACK));
		cat.setPersistent();
		if (owner != null) {
			cat.setOwnerUuid(owner);
			cat.setTamed(true, true);
		}
		cat.setSitting(true);
		cat.setInSittingPose(true);
		cat.refreshPositionAndAngles(at.x, at.y, at.z, 180f, 0f);
		world.spawnEntity(cat);
		return cat;
	}

	/** Removes everyone aboard, for a replay. */
	public static void clearCrew(ServerWorld world, BlockPos origin, TransitState state) {
		Box around = new Box(origin).expand(64.0);
		// Through the whole index, not the tracked sections: see sweepItems. A missed crew member here is a
		// duplicate captain on the next replay.
		List<Entity> aboard = new ArrayList<>();
		for (Entity entity : world.iterateEntities()) {
			if ((entity instanceof CrewEntity || entity instanceof RobotEntity || entity instanceof ItemEntity || entity instanceof CatEntity) && around.contains(entity.getPos())) aboard.add(entity);
		}
		for (Entity entity : aboard) {
			if (entity instanceof RobotEntity robot) RobotRegistry.get(world.getServer()).remove(robot.getUuid());
			entity.discard();
		}
		state.castellanos = null;
		state.ferreira = null;
		state.teague = null;
		state.calibrationRobot = null;
		state.cat = null;
		state.sleepers.clear();
	}

	// ------------------------------------------------------------------ the ship changing over the week

	/** Day lighting: bulbs on and the dim night lights gone; or the reverse. All at once. */
	public static void setLights(ServerWorld world, BlockPos origin, boolean day) {
		for (Runnable change : lightChanges(world, origin, day)) change.run();
	}

	/**
	 * The same change as one step per lamp, so the caller can spread it over several ticks: nineteen light
	 * sources changing in one tick is a visible hitch on the client while the lighting catches up.
	 */
	public static List<Runnable> lightChanges(ServerWorld world, BlockPos origin, boolean day) {
		List<Runnable> changes = new ArrayList<>();
		for (BlockPos bulb : BULBS) {
			BlockPos pos = origin.add(bulb);
			changes.add(() -> {
				BlockState state = world.getBlockState(pos);
				if (state.isOf(Blocks.WAXED_COPPER_BULB)) set(world, pos, state.with(Properties.LIT, day));
				BlockPos below = pos.down();
				BlockState current = world.getBlockState(below);
				if (day) {
					if (current.isOf(Blocks.LIGHT)) set(world, below, Blocks.AIR.getDefaultState());
				} else if (current.isAir() || current.isOf(Blocks.LIGHT)) {
					set(world, below, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, 4));
				}
			});
		}
		return changes;
	}

	/** One corridor lamp per day survived. */
	public static void setDayLamps(ServerWorld world, BlockPos origin, int day) {
		for (int i = 0; i < DAY_LAMPS.length; i++) {
			BlockPos pos = origin.add(DAY_LAMPS[i]);
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.WAXED_COPPER_BULB)) set(world, pos, state.with(Properties.LIT, i < day));
		}
	}

	public static void setConsoles(ServerWorld world, BlockPos origin, boolean alert) {
		PosterBlock.Print print = alert ? PosterBlock.Print.CONSOLE_ALERT : PosterBlock.Print.CONSOLE;
		for (int i = 0; i < BRIDGE_CONSOLES.length; i++) poster(world, origin, BRIDGE_CONSOLES[i], print, BRIDGE_CONSOLE_FACING[i]);
	}

	public static boolean isLeverClosed(ServerWorld world, BlockPos origin) {
		BlockState state = world.getBlockState(origin.add(COOLANT_LEVER));
		return state.isOf(Blocks.LEVER) && state.get(LeverBlock.POWERED);
	}

	public static void closeLever(ServerWorld world, BlockPos origin) {
		BlockPos pos = origin.add(COOLANT_LEVER);
		set(world, pos, lever(true));
		world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.5f, 0.6f);
	}

	public static boolean isBreached(ServerWorld world, BlockPos origin) {
		return !world.getBlockState(origin.add(BREACH)).isFullCube(world, origin.add(BREACH));
	}

	/**
	 * A seam in the starboard hold wall lets go: the plate becomes a torn frame with the stars through it, which
	 * the scrubber in the hold reads as a hole on its next scan. A hull plate used on it makes it a wall again.
	 */
	public static void breach(ServerWorld world, BlockPos origin) {
		BlockPos pos = origin.add(BREACH);
		set(world, pos, ModBlocks.BREACHED_PLATING.getDefaultState().with(BreachedPlatingBlock.FACING, Direction.WEST));
		Vec3d at = Vec3d.ofCenter(pos);
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x - 0.6, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.spawnParticles(ParticleTypes.CLOUD, at.x - 1.0, at.y, at.z, 40, 0.8, 0.8, 0.8, 0.08);
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 2.5f, 0.8f);
		world.playSound(null, pos, ModSounds.DECOMPRESS, SoundCategory.BLOCKS, 3.0f, 1.0f);
		Surrogate.LOGGER.info("Transit: the hold is breached at {}", pos);
	}

	/** Air and loose dust streaming out through the hole, while it is a hole. */
	public static void ventParticles(ServerWorld world, BlockPos origin) {
		Vec3d at = Vec3d.ofCenter(origin.add(BREACH));
		world.spawnParticles(ParticleTypes.CLOUD, at.x - 2.5, at.y, at.z, 3, 1.5, 0.8, 1.5, 0.0);
		world.spawnParticles(ParticleTypes.POOF, at.x - 0.6, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.02);
	}

	public static void patch(ServerWorld world, BlockPos origin) {
		BlockPos pos = origin.add(BREACH);
		set(world, pos, ModBlocks.HULL_PLATING.getDefaultState());
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
	}

	public static void markVasquezDead(ServerWorld world, BlockPos origin) {
		sleeperSign(world, origin, VASQUEZ, true);
	}

	public static void putDescentProtocol(ServerWorld world, BlockPos origin) {
		BlockPos pos = origin.add(MEDBAY_LECTERN);
		if (world.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
			lectern.setBook(book("descent", 2));
			lectern.markDirty();
		}
	}

	/** The reactor feeds the scrubbers: nothing aboard runs on sunlight. */
	public static void topUpAir(ServerWorld world, BlockPos origin) {
		for (BlockPos unit : new BlockPos[]{FORWARD_LIFE_SUPPORT, HOLD_LIFE_SUPPORT, DROPBAY_LIFE_SUPPORT}) {
			if (world.getBlockEntity(origin.add(unit)) instanceof LifeSupportBlockEntity entity) entity.addEnergy(Surrogate.CONFIG.lifeSupportEnergyCapacity);
		}
	}

	/** Keeps the whole ship ticking while the story runs, even the end the player is not at. */
	public static void forceChunks(ServerWorld world, BlockPos origin, boolean forced) {
		for (int cx = (origin.getX() - 20) >> 4; cx <= (origin.getX() + 20) >> 4; cx++) {
			for (int cz = (origin.getZ() - 28) >> 4; cz <= (origin.getZ() + 32) >> 4; cz++) {
				world.setChunkForced(cx, cz, forced);
			}
		}
	}
}
