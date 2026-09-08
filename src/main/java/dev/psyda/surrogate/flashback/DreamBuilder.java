package dev.psyda.surrogate.flashback;

import dev.psyda.surrogate.block.ConnectedPropBlock;
import dev.psyda.surrogate.block.DogCarrierBlock;
import dev.psyda.surrogate.block.HouseContainerBlock;
import dev.psyda.surrogate.block.HouseContainerBlockEntity;
import dev.psyda.surrogate.block.HouseLightBlock;
import dev.psyda.surrogate.block.LightSwitchBlock;
import dev.psyda.surrogate.block.MonitorBlock;
import dev.psyda.surrogate.block.PintGlassBlock;
import dev.psyda.surrogate.block.PizzaBoxBlock;
import dev.psyda.surrogate.block.PropBlock;
import dev.psyda.surrogate.block.SetBlock;
import dev.psyda.surrogate.block.SuitcaseBlock;
import dev.psyda.surrogate.block.TelevisionBlock;
import dev.psyda.surrogate.block.WallClockBlock;
import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/**
 * The places the flashback is made of: a corridor with three doors in it, and behind each door a whole
 * building. Coordinates are relative to {@link DreamDimension#ORIGIN}; each of the three sites is far enough
 * from the others that nothing shares a wall, and far enough from the corridor that the fade between them
 * hides nothing that is still there.
 *
 * <p>The shells are vanilla, on purpose: oak and concrete and glass panes, in a game where everything else
 * is hull plating. The furniture is the mod's own, because a house needs a couch and a fridge and a dog,
 * and all of it comes off in your hands. Nothing in {@code #surrogate:dream_fixed} does.
 */
public final class DreamBuilder {
	// ------------------------------------------------------------------ the corridor

	/**
	 * Where the player stands when the dream opens: the south end of the corridor, facing north. Ten blocks
	 * from the doors, which is as far as the dream's fog lets you see a lit thing clearly.
	 */
	public static final Vec3d WAKES = new Vec3d(0.5, 1, 5.5);
	/** The three doorways, west to east, in the order {@link DreamPlace} declares them. */
	private static final int[] DOOR_X = {-4, 0, 4};
	private static final int DOOR_Z = -5;
	/** Two steps beyond a doorway: crossing this is the answer. */
	public static final int THRESHOLD_Z = DOOR_Z - 2;

	// ------------------------------------------------------------------ the three sites

	public static final BlockPos SITE_HOME = new BlockPos(0, 0, -70);
	public static final BlockPos SITE_WORK = new BlockPos(70, 0, -70);
	public static final BlockPos SITE_BAR = new BlockPos(-70, 0, -70);

	// The house, relative to its site. The ground floor stands on y=0, the upper floor on y=4.
	public static final BlockPos HOME_COUCH_SEAT = new BlockPos(-5, 1, 3);
	public static final BlockPos HOME_TV = new BlockPos(-8, 2, 3);
	public static final BlockPos HOME_PIZZA = new BlockPos(-6, 2, 4);
	public static final BlockPos HOME_CLOCK = new BlockPos(-7, 2, 0);
	public static final BlockPos HOME_SUITCASE = new BlockPos(-6, 6, -4);
	public static final BlockPos HOME_CARRIER = new BlockPos(-3, 1, -3);
	public static final BlockPos HOME_BILLS = new BlockPos(-8, 2, -3);
	public static final Vec3d HOME_FIGURE = new Vec3d(-4.5, 1, -2.5);
	public static final Vec3d HOME_FIGURE_LEAVES = new Vec3d(1.5, 1, 4.5);
	public static final Vec3d HOME_DOG = new Vec3d(-5.5, 1, -4.5);
	public static final Vec3d HOME_UPSTAIRS_NOISE = new Vec3d(0.5, 6, -5.5);
	public static final Vec3d HOME_STREET = new Vec3d(-4.5, 2, 9.5);
	public static final Vec3d HOME_EXIT = new Vec3d(0.5, 1, 9.5);
	private static final int HOME_PORCH_Z = 8;

	// The office.
	public static final BlockPos WORK_CHAIR = new BlockPos(-3, 1, 1);
	public static final BlockPos WORK_MONITOR = new BlockPos(-3, 2, 0);
	public static final BlockPos WORK_BOX = new BlockPos(-2, 1, 1);
	public static final BlockPos WORK_COWORKER_CHAIR = new BlockPos(2, 1, 1);
	public static final BlockPos WORK_COWORKER_MONITOR = new BlockPos(2, 2, 0);
	public static final BlockPos WORK_PRINTER = new BlockPos(9, 2, -7);
	public static final BlockPos WORK_PRINTOUT = new BlockPos(8, 2, -7);
	public static final Vec3d WORK_COWORKER_STANDS = new Vec3d(-1.5, 1, 2.5);
	public static final Vec3d WORK_LIFT = new Vec3d(1.0, 1, 8.5);
	public static final Vec3d WORK_EXIT = new Vec3d(1.0, 1, 10.5);
	private static final BlockPos[] WORK_LIFT_DOORS = {new BlockPos(0, 1, 9), new BlockPos(1, 1, 9)};

	// The bar.
	public static final BlockPos BAR_STOOL = new BlockPos(1, 1, -2);
	public static final BlockPos BAR_PINT = new BlockPos(1, 2, -3);
	public static final BlockPos BAR_TV = new BlockPos(-7, 3, -6);
	public static final BlockPos BAR_BOARD = new BlockPos(8, 2, 2);
	public static final BlockPos BAR_SUITCASE = new BlockPos(5, 1, 5);
	public static final BlockPos BAR_LAMP_KEPT = new BlockPos(5, 3, 4);
	public static final Vec3d BAR_BARMAN = new Vec3d(-0.5, 1, -4.5);
	public static final Vec3d BAR_BARMAN_LEAVES = new Vec3d(-7.5, 1, -3.5);
	public static final Vec3d BAR_EXIT = new Vec3d(6.5, 1, 9.5);
	private static final int BAR_PORCH_Z = 8;

	private DreamBuilder() {
	}

	// ------------------------------------------------------------------ where things are, in world terms

	/** The threshold beyond door {@code place}, in world coordinates. */
	public static Vec3d threshold(BlockPos origin, DreamPlace place) {
		return new Vec3d(origin.getX() + DOOR_X[place.ordinal()] + 0.5, origin.getY() + 1, origin.getZ() + THRESHOLD_Z + 0.5);
	}

	/** The corner the whole place is measured from. */
	public static BlockPos site(BlockPos origin, DreamPlace place) {
		return origin.add(switch (place) {
			case HOME -> SITE_HOME;
			case WORK -> SITE_WORK;
			case BAR -> SITE_BAR;
		});
	}

	/** Where the player is put down when the room appears around them: on the couch, in the chair, on the stool. */
	public static BlockPos seat(BlockPos origin, DreamPlace place) {
		return site(origin, place).add(switch (place) {
			case HOME -> HOME_COUCH_SEAT;
			case WORK -> WORK_CHAIR;
			case BAR -> BAR_STOOL;
		});
	}

	public static Direction seatFacing(DreamPlace place) {
		return switch (place) {
			case HOME -> Direction.WEST;
			case WORK, BAR -> Direction.NORTH;
		};
	}

	public static double seatHeight(DreamPlace place) {
		return switch (place) {
			case HOME -> 0.5;
			case WORK -> 0.56;
			case BAR -> 0.62;
		};
	}

	/** Standing on the floor beside the seat, for when the seat is gone: the fallback every teleport uses. */
	public static Vec3d floorBesideSeat(BlockPos origin, DreamPlace place) {
		BlockPos seat = seat(origin, place);
		Direction facing = seatFacing(place);
		BlockPos beside = seat.offset(facing.getOpposite());
		return new Vec3d(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);
	}

	/** Where whoever is in the room with you is standing, and which way they face. */
	public static Vec3d figureAt(BlockPos origin, DreamPlace place) {
		return Vec3d.of(site(origin, place)).add(switch (place) {
			case HOME -> HOME_FIGURE;
			case WORK -> Vec3d.ofBottomCenter(WORK_COWORKER_CHAIR);
			case BAR -> BAR_BARMAN;
		});
	}

	public static float figureYaw(DreamPlace place) {
		return switch (place) {
			case HOME -> 0f;
			case WORK -> 180f;
			case BAR -> 0f;
		};
	}

	/** Where the figure goes when they leave the room. */
	public static Vec3d figureLeavesTo(BlockPos origin, DreamPlace place) {
		return Vec3d.of(site(origin, place)).add(switch (place) {
			case HOME -> HOME_FIGURE_LEAVES;
			case WORK -> WORK_LIFT;
			case BAR -> BAR_BARMAN_LEAVES;
		});
	}

	/** Out through the front door, the lift, the street door: the end of it. */
	public static Vec3d exit(BlockPos origin, DreamPlace place) {
		return Vec3d.of(site(origin, place)).add(switch (place) {
			case HOME -> HOME_EXIT;
			case WORK -> WORK_EXIT;
			case BAR -> BAR_EXIT;
		});
	}

	public static BlockPos suitcase(BlockPos origin, DreamPlace place) {
		return site(origin, place).add(switch (place) {
			case HOME -> HOME_SUITCASE;
			case WORK -> WORK_BOX;
			case BAR -> BAR_SUITCASE;
		});
	}

	public static BlockPos television(BlockPos origin, DreamPlace place) {
		return site(origin, place).add(switch (place) {
			case HOME -> HOME_TV;
			case WORK -> WORK_MONITOR;
			case BAR -> BAR_TV;
		});
	}

	/** Whether {@code pos} is on the upper floor of the house. */
	public static boolean isUpstairs(BlockPos origin, Vec3d pos) {
		BlockPos site = site(origin, DreamPlace.HOME);
		return pos.y >= site.getY() + 4.9 && pos.x > site.getX() - 9 && pos.x < site.getX() + 3
				&& pos.z > site.getZ() - 7 && pos.z < site.getZ() + 7;
	}

	/** Whether {@code pos} is in the bedroom, where the case is. */
	public static boolean isInBedroom(BlockPos origin, Vec3d pos) {
		BlockPos site = site(origin, DreamPlace.HOME);
		return pos.y >= site.getY() + 4.9 && pos.x > site.getX() - 9 && pos.x < site.getX() - 2
				&& pos.z > site.getZ() - 7 && pos.z < site.getZ() + 1;
	}

	// ------------------------------------------------------------------ the corridor

	/**
	 * A corridor with three doors at the end of it, each with its name over it and a glimpse of the place
	 * behind. The corridor is dark and the doors are the only light, so the question is answered before it is
	 * asked and the player is only confirming.
	 */
	public static void hall(ServerWorld world, BlockPos origin, Predicate<DreamPlace> open) {
		Site s = new Site(world, origin);
		BlockState wall = Blocks.BLACK_CONCRETE.getDefaultState();
		BlockState floor = Blocks.POLISHED_DEEPSLATE.getDefaultState();
		// The corridor: three wide, five long, the player at the south end.
		s.box(-2, 0, 1, 2, 4, 7, wall);
		s.box(-1, 1, 2, 1, 3, 6, Blocks.AIR.getDefaultState());
		s.box(-1, 0, 2, 1, 0, 6, floor);
		// The landing at its north end, with the three doors in its north wall.
		s.box(-7, 0, -5, 7, 4, 2, wall);
		s.box(-6, 1, -4, 6, 3, 1, Blocks.AIR.getDefaultState());
		s.box(-6, 0, -4, 6, 0, 1, floor);
		// The corridor opens onto the landing.
		s.box(-1, 1, 1, 1, 3, 2, Blocks.AIR.getDefaultState());
		s.box(-1, 0, 1, 1, 0, 2, floor);
		// A runner down the middle, up to the landing, and the lamps that let you see it.
		for (int z = 6; z >= -3; z--) s.set(0, 1, z, Blocks.RED_CARPET.getDefaultState());
		BlockState lantern = Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
		s.set(0, 3, 5, lantern);
		s.set(0, 3, 2, lantern);
		s.set(-3, 3, -2, lantern);
		s.set(3, 3, -2, lantern);
		for (DreamPlace place : DreamPlace.all()) {
			if (open.test(place)) doorway(s, place);
			else shutDoorway(s, place);
		}
	}

	/**
	 * A door already gone through: bricked up and unlit, in a wall that is black anyway, with the name still
	 * over it. You went that way. You are not going that way again.
	 */
	private static void shutDoorway(Site s, DreamPlace place) {
		int x = DOOR_X[place.ordinal()];
		s.box(x - 1, 0, DOOR_Z - 4, x + 1, 3, DOOR_Z, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
		s.set(x, 1, DOOR_Z - 4, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
		sign(s, x, 3, DOOR_Z + 1, place, false);
		s.set(x, 1, DOOR_Z + 1, Blocks.GRAY_CARPET.getDefaultState());
	}

	/** One door, standing open, with its name over it and three blocks of the place behind it, lit. */
	private static void doorway(Site s, DreamPlace place) {
		int x = DOOR_X[place.ordinal()];
		BlockState wallpaper = switch (place) {
			case HOME -> ModBlocks.WALLPAPER_FLORAL.getDefaultState();
			case WORK -> Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
			case BAR -> ModBlocks.WALLPAPER_STRIPE.getDefaultState();
		};
		BlockState floor = switch (place) {
			case HOME -> Blocks.OAK_PLANKS.getDefaultState();
			case WORK -> Blocks.GRAY_CONCRETE.getDefaultState();
			case BAR -> Blocks.DARK_OAK_PLANKS.getDefaultState();
		};
		BlockState light = switch (place) {
			// Warm, cold, and the colour of a room you should have left an hour ago.
			case HOME -> Blocks.OCHRE_FROGLIGHT.getDefaultState();
			case WORK -> Blocks.SEA_LANTERN.getDefaultState();
			case BAR -> Blocks.SHROOMLIGHT.getDefaultState();
		};
		Block door = switch (place) {
			case HOME -> Blocks.OAK_DOOR;
			case WORK -> Blocks.SPRUCE_DOOR;
			case BAR -> Blocks.DARK_OAK_DOOR;
		};
		BlockState mat = switch (place) {
			case HOME -> Blocks.RED_CARPET.getDefaultState();
			case WORK -> Blocks.LIGHT_GRAY_CARPET.getDefaultState();
			case BAR -> Blocks.BLACK_CARPET.getDefaultState();
		};
		// The frame, in the material of the place, so the door reads as belonging to it from across the hall,
		// and the place's own light set over the door, so it can be read from the far end of the corridor.
		s.box(x - 1, 0, DOOR_Z, x + 1, 3, DOOR_Z, wallpaper);
		s.set(x, 3, DOOR_Z, light);
		// The vestibule behind it: floor, papered walls, a lit far wall.
		s.box(x - 2, 0, DOOR_Z - 5, x + 2, 4, DOOR_Z - 1, wallpaper);
		s.box(x - 1, 1, DOOR_Z - 4, x + 1, 3, DOOR_Z - 1, Blocks.AIR.getDefaultState());
		s.box(x - 1, 0, DOOR_Z - 4, x + 1, 0, DOOR_Z - 1, floor);
		s.box(x - 1, 1, DOOR_Z - 5, x + 1, 2, DOOR_Z - 5, light);
		s.set(x, 4, DOOR_Z - 2, light);
		// The door itself, open, and the mat in front of it.
		door(s, x, 1, DOOR_Z, door, Direction.NORTH, true);
		s.set(x, 1, DOOR_Z + 1, mat);
		sign(s, x, 3, DOOR_Z + 1, place, true);
	}

	/** The name of the place over its door, on a sign facing the player. */
	private static void sign(Site s, int x, int y, int z, DreamPlace place, boolean lit) {
		BlockPos pos = s.set(x, y, z, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH));
		if (s.world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			Text name = Text.translatable("flashback.surrogate.sign." + place.key());
			sign.setText(sign.getText(true).withMessage(1, name).withColor(lit ? DyeColor.WHITE : DyeColor.GRAY).withGlowing(lit), true);
			sign.setWaxed(true);
		}
	}

	// ------------------------------------------------------------------ the places

	/** Whichever one they walked towards. */
	public static void build(ServerWorld world, BlockPos origin, DreamPlace place) {
		Site s = new Site(world, site(origin, place));
		switch (place) {
			case HOME -> house(s);
			case WORK -> office(s);
			case BAR -> bar(s);
		}
	}

	/**
	 * A two-storey house. Living room and kitchen on the west side of the hall, the stairs on the east; the
	 * bedroom over the kitchen, the bathroom over the living room, and a door at the end of the landing that
	 * does not open. The front door opens onto a porch that is, until the end of the night, a wall.
	 */
	private static void house(Site s) {
		BlockState floral = ModBlocks.WALLPAPER_FLORAL.getDefaultState();
		BlockState stripe = ModBlocks.WALLPAPER_STRIPE.getDefaultState();
		BlockState oak = Blocks.OAK_PLANKS.getDefaultState();
		BlockState tile = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
		BlockState white = Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState pane = Blocks.GLASS_PANE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		// The shell: two floors of it, papered inside.
		s.box(-9, 0, -7, 2, 8, 7, floral);
		s.box(-8, 1, -6, 1, 3, 6, air);
		s.box(-8, 5, -6, 1, 7, 6, air);
		s.box(-8, 0, -6, 1, 0, 6, oak);
		s.box(-8, 4, -6, 1, 4, 6, oak);
		s.box(-8, 8, -6, 1, 8, 6, white);
		// Kitchen and bathroom floors.
		s.box(-8, 0, -6, -3, 0, -2, tile);
		s.box(-8, 4, 2, -3, 4, 6, tile);

		// Ground floor partitions: hall walls at x=-2, with doorways; the kitchen arch at z=-1.
		s.box(-2, 1, -6, -2, 3, 6, floral);
		s.box(-2, 1, 3, -2, 2, 3, air);
		s.box(-2, 1, -4, -2, 2, -4, air);
		s.box(-8, 1, -1, -3, 3, -1, stripe);
		s.box(-6, 1, -1, -4, 2, -1, air);
		s.box(-8, 1, -6, -3, 3, -2, air);
		for (int z = -6; z <= -2; z++) {
			s.set(-8, 1, z, air);
		}
		// The kitchen walls are striped rather than floral: the strip of the shell that faces the kitchen.
		s.box(-9, 1, -6, -9, 3, -2, stripe);
		s.box(-8, 1, -7, -3, 3, -7, stripe);

		// The stairs, against the east wall of the hall, going up northwards.
		// Four steps: the last one is set into the upper floor, so the top of the stairs is the landing.
		s.box(1, 4, 1, 1, 4, 3, air);
		s.set(1, 1, 3, stairs(Blocks.OAK_STAIRS, Direction.NORTH));
		s.set(1, 2, 2, stairs(Blocks.OAK_STAIRS, Direction.NORTH));
		s.set(1, 3, 1, stairs(Blocks.OAK_STAIRS, Direction.NORTH));
		s.set(1, 4, 0, stairs(Blocks.OAK_STAIRS, Direction.NORTH));
		s.set(1, 1, 2, oak);
		s.set(1, 1, 1, oak);
		s.set(1, 2, 1, oak);
		for (int z = 0; z <= 3; z++) s.set(0, 5, z, Blocks.OAK_FENCE.getDefaultState());

		// Upstairs partitions: the bedroom and bathroom walls, and the door at the end of the landing.
		s.box(-2, 5, -6, -2, 7, 6, floral);
		s.box(-8, 5, 1, -3, 7, 1, floral);
		s.box(-1, 5, -3, 1, 7, -3, floral);
		door(s, -2, 5, -2, Blocks.OAK_DOOR, Direction.EAST, true);
		door(s, -2, 5, 3, Blocks.OAK_DOOR, Direction.EAST, true);
		door(s, 0, 5, -3, Blocks.SPRUCE_DOOR, Direction.SOUTH, false);
		// Bathroom walls are white.
		s.box(-9, 5, 2, -9, 7, 6, white);
		s.box(-8, 5, 7, -3, 7, 7, white);
		s.box(-8, 5, 1, -3, 7, 1, white);
		s.box(-2, 5, 2, -2, 7, 6, white);
		s.set(-2, 5, 3, air);
		s.set(-2, 6, 3, air);
		door(s, -2, 5, 3, Blocks.OAK_DOOR, Direction.EAST, true);

		// Windows: black behind every one of them, which is the correct amount of Earth.
		s.box(-9, 2, 5, -9, 3, 6, pane);
		s.box(-6, 2, 7, -5, 3, 7, pane);
		s.set(-6, 2, -7, pane);
		s.set(-6, 3, -7, pane);
		s.box(-9, 6, -2, -9, 7, -1, pane);
		s.set(-9, 6, 4, pane);

		// The front door and the porch behind it, walled off until the night is over.
		door(s, 0, 1, 7, Blocks.OAK_DOOR, Direction.NORTH, false);
		s.box(-2, 0, HOME_PORCH_Z, 2, 4, HOME_PORCH_Z + 3, Blocks.BLACK_CONCRETE.getDefaultState());
		s.box(-1, 1, HOME_PORCH_Z + 1, 1, 3, HOME_PORCH_Z + 2, air);
		s.box(-1, 0, HOME_PORCH_Z + 1, 1, 0, HOME_PORCH_Z + 2, Blocks.POLISHED_DEEPSLATE.getDefaultState());

		// ---- The living room.
		s.set(-8, 1, 3, facing(ModBlocks.NIGHTSTAND, HouseContainerBlock.FACING, Direction.EAST));
		s.set(HOME_TV, ModBlocks.TELEVISION.getDefaultState().with(TelevisionBlock.FACING, Direction.EAST).with(TelevisionBlock.CHANNEL, TelevisionBlock.Channel.NEWS));
		for (int z = 2; z <= 4; z++) s.set(-5, 1, z, ModBlocks.COUCH.getDefaultState().with(ConnectedPropBlock.FACING, Direction.WEST));
		connect(s, -5, 1, 2, -5, 1, 4);
		s.set(-6, 1, 3, ModBlocks.COFFEE_TABLE.getDefaultState());
		s.set(-6, 1, 4, ModBlocks.COFFEE_TABLE.getDefaultState());
		// The box is on the near end of the table with its lid to the south, out of the line between the couch
		// and the set: an open lid in that line is a wall with a pizza on it.
		s.set(HOME_PIZZA, ModBlocks.PIZZA_BOX.getDefaultState().with(PropBlock.FACING, Direction.NORTH).with(PizzaBoxBlock.SLICES, PizzaBoxBlock.SLICES_MAX));
		s.set(-3, 2, 6, prop(ModBlocks.MUG, Direction.WEST));
		for (int z = 2; z <= 4; z++) s.set(-7, 1, z, Blocks.BROWN_CARPET.getDefaultState());
		s.set(-3, 1, 6, Blocks.BOOKSHELF.getDefaultState());
		s.set(-4, 1, 6, Blocks.BOOKSHELF.getDefaultState());
		s.set(-4, 2, 6, ModBlocks.SNOW_GLOBE.getDefaultState());
		s.set(-8, 1, 6, ModBlocks.HOUSEPLANT.getDefaultState());
		s.set(-5, 2, 6, wall(ModBlocks.PHOTO_FRAME, Direction.NORTH));
		s.set(HOME_CLOCK, wall(ModBlocks.WALL_CLOCK, Direction.SOUTH));
		s.set(-5, 3, 3, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(-3, 2, 4, wall(ModBlocks.LIGHT_SWITCH, Direction.WEST).with(LightSwitchBlock.ON, true));
		s.set(-3, 2, 1, wall(ModBlocks.PHOTO_FRAME, Direction.WEST));

		// ---- The kitchen.
		s.set(-3, 1, -6, facing(ModBlocks.FRIDGE, HouseContainerBlock.FACING, Direction.SOUTH));
		fill(s, -3, 1, -6, Items.MILK_BUCKET, Items.APPLE, Items.BREAD, Items.COOKED_CHICKEN, Items.CAKE);
		s.set(-4, 1, -6, prop(ModBlocks.KITCHEN_COUNTER, Direction.SOUTH));
		s.set(-5, 1, -6, prop(ModBlocks.STOVE, Direction.SOUTH));
		s.set(-6, 1, -6, prop(ModBlocks.KITCHEN_SINK, Direction.SOUTH));
		s.set(-7, 1, -6, prop(ModBlocks.KITCHEN_COUNTER, Direction.SOUTH));
		s.set(-8, 1, -6, prop(ModBlocks.KITCHEN_COUNTER, Direction.SOUTH));
		s.set(-7, 2, -6, ModBlocks.RADIO_SET.getDefaultState().with(SetBlock.FACING, Direction.SOUTH).with(SetBlock.ON, true));
		s.set(-8, 2, -6, prop(ModBlocks.MUG, Direction.SOUTH));
		s.set(-4, 2, -6, prop(ModBlocks.TELEPHONE, Direction.SOUTH));
		s.set(-8, 1, -3, ModBlocks.DINING_TABLE.getDefaultState());
		s.set(-7, 1, -3, ModBlocks.DINING_TABLE.getDefaultState());
		s.set(-8, 1, -2, prop(ModBlocks.DINING_CHAIR, Direction.NORTH));
		s.set(-7, 1, -4, prop(ModBlocks.DINING_CHAIR, Direction.SOUTH));
		s.set(HOME_BILLS, ModBlocks.BILLS.getDefaultState());
		s.set(-7, 2, -3, prop(ModBlocks.MUG, Direction.WEST));
		s.set(-3, 2, -3, wall(ModBlocks.CALENDAR, Direction.WEST));
		s.set(-3, 2, -5, wall(ModBlocks.LIGHT_SWITCH, Direction.WEST).with(LightSwitchBlock.ON, true));
		s.set(-3, 1, -2, ModBlocks.DOG_BOWL.getDefaultState());
		s.set(HOME_CARRIER, ModBlocks.DOG_CARRIER.getDefaultState().with(DogCarrierBlock.FACING, Direction.WEST));
		s.set(-5, 3, -4, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(-8, 1, -5, ModBlocks.HOUSEPLANT.getDefaultState());

		// ---- The hall.
		s.set(-1, 1, 5, facing(ModBlocks.NIGHTSTAND, HouseContainerBlock.FACING, Direction.EAST));
		s.set(-1, 2, 5, prop(ModBlocks.TELEPHONE, Direction.EAST));
		s.set(0, 3, -1, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(1, 2, 6, wall(ModBlocks.LIGHT_SWITCH, Direction.EAST).with(LightSwitchBlock.ON, true));
		s.set(-1, 2, -1, wall(ModBlocks.PHOTO_FRAME, Direction.EAST));
		for (int z = -5; z <= 5; z += 5) s.set(0, 1, z, Blocks.RED_CARPET.getDefaultState());

		// ---- Upstairs: the landing, the bedroom, the bathroom, and the room that is not yours.
		s.set(0, 7, 0, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(1, 6, 0, wall(ModBlocks.LIGHT_SWITCH, Direction.EAST).with(LightSwitchBlock.ON, true));
		s.set(0, 7, -5, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(0, 5, -5, Blocks.WHITE_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
		s.set(0, 5, -4, Blocks.WHITE_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));

		s.set(-6, 5, -5, Blocks.WHITE_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.HEAD));
		s.set(-6, 5, -4, Blocks.WHITE_BED.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(BedBlock.PART, BedPart.FOOT));
		s.set(HOME_SUITCASE, ModBlocks.SUITCASE.getDefaultState().with(HouseContainerBlock.FACING, Direction.WEST).with(SuitcaseBlock.ON_BED, true));
		fill(s, HOME_SUITCASE, Items.PAPER, Items.BREAD, Items.WHITE_WOOL, Items.LEATHER_BOOTS);
		s.set(-7, 5, -5, facing(ModBlocks.NIGHTSTAND, HouseContainerBlock.FACING, Direction.EAST));
		fill(s, -7, 5, -5, Items.CLOCK, Items.BOOK);
		s.set(-7, 6, -5, prop(ModBlocks.DESK_LAMP, Direction.EAST));
		s.set(-4, 5, -6, facing(ModBlocks.WARDROBE, HouseContainerBlock.FACING, Direction.SOUTH));
		fill(s, -4, 5, -6, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.BLACK_WOOL, Items.STRING);
		s.set(-8, 6, -3, wall(ModBlocks.PHOTO_FRAME, Direction.EAST));
		s.set(-5, 7, -3, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(-3, 6, -1, wall(ModBlocks.LIGHT_SWITCH, Direction.WEST).with(LightSwitchBlock.ON, true));
		s.set(-8, 5, 0, Blocks.BOOKSHELF.getDefaultState());
		s.set(-3, 5, 0, ModBlocks.HOUSEPLANT.getDefaultState());
		s.set(-5, 5, -3, Blocks.LIGHT_GRAY_CARPET.getDefaultState());
		s.set(-5, 5, -2, Blocks.LIGHT_GRAY_CARPET.getDefaultState());

		s.set(-8, 5, 6, prop(ModBlocks.TOILET, Direction.EAST));
		s.set(-8, 5, 3, prop(ModBlocks.WASHBASIN, Direction.EAST));
		s.set(-8, 6, 3, wall(ModBlocks.MIRROR, Direction.EAST));
		s.set(-5, 5, 6, ModBlocks.BATHTUB.getDefaultState().with(ConnectedPropBlock.FACING, Direction.NORTH));
		s.set(-4, 5, 6, ModBlocks.BATHTUB.getDefaultState().with(ConnectedPropBlock.FACING, Direction.NORTH));
		connect(s, -5, 5, 6, -4, 5, 6);
		s.set(-5, 7, 4, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(-3, 6, 4, wall(ModBlocks.LIGHT_SWITCH, Direction.WEST).with(LightSwitchBlock.ON, true));
		s.set(-3, 5, 2, ModBlocks.HOUSEPLANT.getDefaultState());
	}

	/** Later: the lights off, the set on the credits, the clock further round, the box lighter by three. */
	public static void houseLater(ServerWorld world, BlockPos origin) {
		Site s = new Site(world, site(origin, DreamPlace.HOME));
		lightsOut(s, -9, 0, -7, 2, 8, 7, pos -> false);
		// The landing light and the light in the room at the end of it stay on: somebody is up there.
		s.set(0, 7, -5, ModBlocks.PENDANT_LAMP.getDefaultState().with(HouseLightBlock.LIT, true));
		tune(s, HOME_TV, TelevisionBlock.Channel.CREDITS);
		BlockState clock = s.get(HOME_CLOCK);
		if (clock.getBlock() instanceof WallClockBlock) s.set(HOME_CLOCK, clock.with(WallClockBlock.LATE, true));
		BlockState box = s.get(HOME_PIZZA);
		if (box.getBlock() instanceof PizzaBoxBlock) s.set(HOME_PIZZA, box.with(PizzaBoxBlock.SLICES, Math.max(0, box.get(PizzaBoxBlock.SLICES) - 3)));
		// Their mug, left on the arm of the couch.
		s.set(-5, 2, 2, prop(ModBlocks.MUG, Direction.WEST));
	}

	/** The porch beyond the front door becomes a porch rather than a wall. */
	public static void openHouse(ServerWorld world, BlockPos origin) {
		Site s = new Site(world, site(origin, DreamPlace.HOME));
		s.box(-1, 1, HOME_PORCH_Z, 1, 3, HOME_PORCH_Z, Blocks.AIR.getDefaultState());
		s.box(-1, 0, HOME_PORCH_Z, 1, 0, HOME_PORCH_Z, Blocks.POLISHED_DEEPSLATE.getDefaultState());
	}

	/**
	 * An open-plan floor at eleven at night: rows of desks, one of them yours, a coworker two desks over,
	 * and a lift in the south wall that will not open until it is time.
	 */
	private static void office(Site s) {
		BlockState wall = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
		BlockState floor = Blocks.GRAY_CONCRETE.getDefaultState();
		BlockState ceiling = Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState pane = Blocks.GLASS_PANE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		s.box(-11, 0, -9, 11, 4, 9, wall);
		s.box(-10, 1, -8, 10, 3, 8, air);
		s.box(-10, 0, -8, 10, 0, 8, floor);
		s.box(-10, 4, -8, 10, 4, 8, ceiling);
		// The north wall is glass, and the city behind it is off.
		s.box(-10, 1, -9, 10, 3, -9, pane);
		s.box(-11, 0, -10, 11, 4, -10, Blocks.BLACK_CONCRETE.getDefaultState());
		for (int x = -8; x <= 8; x += 4) {
			for (int z = -6; z <= 6; z += 4) s.set(x, 3, z, ModBlocks.PANEL_LIGHT.getDefaultState());
		}

		// Desks in pairs, three rows, a chair and a screen at each.
		int[][] pairs = {{-8, -7}, {-4, -3}, {1, 2}, {5, 6}};
		int[] rows = {-5, 0, 5};
		int i = 0;
		for (int z : rows) {
			for (int[] pair : pairs) {
				for (int x : pair) {
					s.set(x, 1, z, prop(ModBlocks.OFFICE_DESK, Direction.SOUTH));
					s.set(x, 1, z + 1, prop(ModBlocks.OFFICE_CHAIR, Direction.NORTH));
					MonitorBlock.Screen screen = (i++ % 3 == 0) ? MonitorBlock.Screen.SAVER : MonitorBlock.Screen.OFF;
					s.set(x, 2, z, prop(ModBlocks.MONITOR, Direction.SOUTH).with(MonitorBlock.SCREEN, screen));
				}
			}
		}
		s.set(WORK_MONITOR, prop(ModBlocks.MONITOR, Direction.SOUTH).with(MonitorBlock.SCREEN, MonitorBlock.Screen.CONTRACT));
		s.set(WORK_COWORKER_MONITOR, prop(ModBlocks.MONITOR, Direction.SOUTH).with(MonitorBlock.SCREEN, MonitorBlock.Screen.SAVER));
		s.set(-4, 2, 0, prop(ModBlocks.DESK_LAMP, Direction.SOUTH));
		s.set(-2, 2, 0, prop(ModBlocks.MUG, Direction.SOUTH));
		s.set(-2, 1, 0, prop(ModBlocks.OFFICE_DESK, Direction.SOUTH));
		s.set(-3, 2, 5, prop(ModBlocks.TELEPHONE, Direction.SOUTH));
		s.set(6, 2, -5, prop(ModBlocks.MUG, Direction.SOUTH));
		s.set(WORK_BOX, facing(ModBlocks.CARDBOARD_BOX, HouseContainerBlock.FACING, Direction.WEST));
		fill(s, WORK_BOX, Items.PAPER, Items.BOOK, Items.WRITABLE_BOOK);

		// The rest of the floor: cabinets, the cooler, the printer, the board nobody has wiped.
		for (int z = -7; z <= -5; z++) s.set(-10, 1, z, facing(ModBlocks.FILING_CABINET, HouseContainerBlock.FACING, Direction.EAST));
		fill(s, -10, 1, -6, Items.PAPER, Items.MAP, Items.INK_SAC);
		s.set(-10, 1, 7, ModBlocks.WATER_COOLER.getDefaultState());
		s.set(8, 1, -7, prop(ModBlocks.OFFICE_DESK, Direction.SOUTH));
		s.set(9, 1, -7, prop(ModBlocks.OFFICE_DESK, Direction.SOUTH));
		s.set(WORK_PRINTER, prop(ModBlocks.PRINTER, Direction.SOUTH));
		s.set(10, 2, 0, wall(ModBlocks.WHITEBOARD, Direction.WEST));
		s.set(10, 2, 1, wall(ModBlocks.WHITEBOARD, Direction.WEST));
		s.set(10, 2, -3, wall(ModBlocks.WALL_CLOCK, Direction.WEST));
		s.set(10, 1, -8, ModBlocks.HOUSEPLANT.getDefaultState());
		s.set(-10, 1, 3, ModBlocks.HOUSEPLANT.getDefaultState());
		s.set(9, 1, 8, Blocks.BOOKSHELF.getDefaultState());
		s.set(10, 2, 7, wall(ModBlocks.LIGHT_SWITCH, Direction.WEST).with(LightSwitchBlock.ON, true));

		// The lift: two iron doors in the south wall, shut, and the car behind them.
		s.box(-1, 0, 9, 2, 4, 12, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
		s.box(0, 1, 10, 1, 3, 11, air);
		s.box(0, 0, 10, 1, 0, 11, Blocks.POLISHED_DEEPSLATE.getDefaultState());
		s.set(0, 3, 10, ModBlocks.PANEL_LIGHT.getDefaultState());
		door(s, 0, 1, 9, Blocks.IRON_DOOR, Direction.NORTH, false, DoorHinge.LEFT);
		door(s, 1, 1, 9, Blocks.IRON_DOOR, Direction.NORTH, false, DoorHinge.RIGHT);
	}

	/** Later: every panel off but the two over your desk, the screens dark, and the contract in the printer tray. */
	public static void officeLater(ServerWorld world, BlockPos origin) {
		Site s = new Site(world, site(origin, DreamPlace.WORK));
		lightsOut(s, -11, 0, -9, 11, 4, 12, pos -> pos.equals(s.at(-4, 3, -2)) || pos.equals(s.at(-4, 3, 2)));
		for (BlockPos pos : BlockPos.iterate(s.at(-10, 2, -8), s.at(10, 2, 8))) {
			BlockState state = world.getBlockState(pos);
			if (state.getBlock() instanceof MonitorBlock && !pos.equals(s.at(WORK_MONITOR))) {
				world.setBlockState(pos, state.with(MonitorBlock.SCREEN, MonitorBlock.Screen.OFF), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
		s.set(WORK_PRINTOUT, ModBlocks.PRINTOUT.getDefaultState());
	}

	/** The lift arrives. */
	public static void openOffice(ServerWorld world, BlockPos origin) {
		Site s = new Site(world, site(origin, DreamPlace.WORK));
		for (BlockPos door : WORK_LIFT_DOORS) {
			for (int y = 0; y <= 1; y++) {
				BlockPos pos = s.at(door.getX(), door.getY() + y, door.getZ());
				BlockState state = world.getBlockState(pos);
				if (state.getBlock() instanceof DoorBlock) world.setBlockState(pos, state.with(DoorBlock.OPEN, true), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
	}

	/** The bar on the corner: a long counter, the stools along it, the board on the wall, the set in the corner. */
	private static void bar(Site s) {
		BlockState dado = Blocks.DARK_OAK_PLANKS.getDefaultState();
		BlockState stripe = ModBlocks.WALLPAPER_STRIPE.getDefaultState();
		BlockState pane = Blocks.GLASS_PANE.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		s.box(-9, 0, -7, 9, 4, 7, stripe);
		s.box(-9, 0, -7, 9, 1, 7, dado);
		s.box(-9, 4, -7, 9, 4, 7, dado);
		s.box(-8, 1, -6, 8, 3, 6, air);
		s.box(-8, 0, -6, 8, 0, 6, dado);
		s.box(-4, 2, 7, -3, 3, 7, pane);
		s.box(3, 2, 7, 4, 3, 7, pane);

		// The counter, the taps, the glasses, and what is behind it.
		for (int x = -6; x <= 3; x++) s.set(x, 1, -3, ModBlocks.BAR_COUNTER.getDefaultState().with(ConnectedPropBlock.FACING, Direction.SOUTH));
		connect(s, -6, 1, -3, 3, 1, -3);
		for (int x = -6; x <= 3; x++) {
			s.set(x, 1, -6, prop(ModBlocks.KITCHEN_COUNTER, Direction.SOUTH));
			s.set(x, 2, -6, wall(ModBlocks.BACK_BAR, Direction.SOUTH));
			s.set(x, 3, -6, wall(ModBlocks.BACK_BAR, Direction.SOUTH));
		}
		s.set(-4, 2, -3, prop(ModBlocks.BEER_TAP, Direction.SOUTH));
		s.set(0, 2, -3, prop(ModBlocks.BEER_TAP, Direction.SOUTH));
		s.set(BAR_PINT, ModBlocks.PINT_GLASS.getDefaultState().with(PintGlassBlock.FULL, true));
		s.set(-2, 2, -3, ModBlocks.PINT_GLASS.getDefaultState().with(PintGlassBlock.FULL, false));
		s.set(-6, 2, -3, ModBlocks.PINT_GLASS.getDefaultState().with(PintGlassBlock.FULL, true));
		s.set(-8, 2, -5, ModBlocks.PINT_GLASS.getDefaultState().with(PintGlassBlock.FULL, false));
		for (int x = -5; x <= 3; x += 2) s.set(x, 1, -2, prop(ModBlocks.BAR_STOOL, Direction.NORTH));
		s.set(BAR_TV, ModBlocks.TELEVISION.getDefaultState().with(TelevisionBlock.FACING, Direction.SOUTH).with(TelevisionBlock.CHANNEL, TelevisionBlock.Channel.NEWS));
		s.set(-8, 1, -6, Blocks.BARREL.getDefaultState());
		s.set(-8, 1, -5, Blocks.BARREL.getDefaultState());

		// The board, the line you throw from, and the tables.
		s.set(BAR_BOARD, ModBlocks.DARTBOARD.getDefaultState().with(PropBlock.FACING, Direction.WEST));
		s.set(5, 1, 2, Blocks.BLACK_CARPET.getDefaultState());
		s.set(-5, 1, 3, ModBlocks.DINING_TABLE.getDefaultState());
		s.set(-6, 1, 3, prop(ModBlocks.BAR_STOOL, Direction.EAST));
		s.set(-4, 1, 3, prop(ModBlocks.BAR_STOOL, Direction.WEST));
		s.set(-5, 2, 3, ModBlocks.PINT_GLASS.getDefaultState().with(PintGlassBlock.FULL, false));
		s.set(2, 1, 4, ModBlocks.DINING_TABLE.getDefaultState());
		s.set(1, 1, 4, prop(ModBlocks.BAR_STOOL, Direction.EAST));
		s.set(3, 1, 4, prop(ModBlocks.BAR_STOOL, Direction.WEST));
		s.set(-8, 1, 5, Blocks.JUKEBOX.getDefaultState());
		s.set(0, 3, 6, wall(ModBlocks.NEON_SIGN, Direction.NORTH));
		s.set(-8, 2, 0, wall(ModBlocks.PHOTO_FRAME, Direction.EAST));
		s.set(-8, 2, 2, wall(ModBlocks.PHOTO_FRAME, Direction.EAST));
		s.set(8, 2, -2, wall(ModBlocks.WALL_CLOCK, Direction.WEST));
		s.set(8, 1, -6, ModBlocks.HOUSEPLANT.getDefaultState());
		s.set(-4, 3, 0, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(2, 3, 0, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(BAR_LAMP_KEPT, ModBlocks.PENDANT_LAMP.getDefaultState());
		s.set(7, 2, 6, wall(ModBlocks.LIGHT_SWITCH, Direction.NORTH).with(LightSwitchBlock.ON, true));

		// Your case, by the door, because you came straight from the port office and are going straight back.
		s.set(BAR_SUITCASE, ModBlocks.SUITCASE.getDefaultState().with(HouseContainerBlock.FACING, Direction.WEST));
		fill(s, BAR_SUITCASE, Items.PAPER, Items.BREAD, Items.WHITE_WOOL, Items.LEATHER_BOOTS);

		// The street door and the porch beyond it, walled off until the night is over.
		door(s, 6, 1, 7, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		s.box(4, 0, BAR_PORCH_Z, 8, 4, BAR_PORCH_Z + 3, Blocks.BLACK_CONCRETE.getDefaultState());
		s.box(5, 1, BAR_PORCH_Z + 1, 7, 3, BAR_PORCH_Z + 2, air);
		s.box(5, 0, BAR_PORCH_Z + 1, 7, 0, BAR_PORCH_Z + 2, Blocks.POLISHED_DEEPSLATE.getDefaultState());
	}

	/** Later: last orders. The lights down to one, the stools up on the counter, the set on the credits. */
	public static void barLater(ServerWorld world, BlockPos origin) {
		Site s = new Site(world, site(origin, DreamPlace.BAR));
		lightsOut(s, -9, 0, -7, 9, 4, 7, pos -> pos.equals(s.at(BAR_LAMP_KEPT)));
		tune(s, BAR_TV, TelevisionBlock.Channel.CREDITS);
		for (int x = -5; x <= -1; x += 2) {
			s.set(x, 1, -2, Blocks.AIR.getDefaultState());
			s.set(x, 2, -3, prop(ModBlocks.BAR_STOOL, Direction.NORTH));
		}
		s.set(-6, 2, -3, ModBlocks.PINT_GLASS.getDefaultState().with(PintGlassBlock.FULL, false));
		BlockState clock = s.get(8, 2, -2);
		if (clock.getBlock() instanceof WallClockBlock) s.set(8, 2, -2, clock.with(WallClockBlock.LATE, true));
	}

	public static void openBar(ServerWorld world, BlockPos origin) {
		Site s = new Site(world, site(origin, DreamPlace.BAR));
		s.box(5, 1, BAR_PORCH_Z, 7, 3, BAR_PORCH_Z, Blocks.AIR.getDefaultState());
		s.box(5, 0, BAR_PORCH_Z, 7, 0, BAR_PORCH_Z, Blocks.POLISHED_DEEPSLATE.getDefaultState());
	}

	// ------------------------------------------------------------------ what "later" does to a room

	/** Every house light in the box off, except the ones {@code keep} says to leave. */
	private static void lightsOut(Site s, int x0, int y0, int z0, int x1, int y1, int z1, Predicate<BlockPos> keep) {
		for (BlockPos pos : BlockPos.iterate(s.at(x0, y0, z0), s.at(x1, y1, z1))) {
			BlockState state = s.world.getBlockState(pos);
			if (state.getBlock() instanceof HouseLightBlock && state.get(HouseLightBlock.LIT) && !keep.test(pos)) {
				s.world.setBlockState(pos, state.with(HouseLightBlock.LIT, false), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
			if (state.getBlock() instanceof LightSwitchBlock && state.get(LightSwitchBlock.ON)) {
				s.world.setBlockState(pos, state.with(LightSwitchBlock.ON, false), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
	}

	private static void tune(Site s, BlockPos rel, TelevisionBlock.Channel channel) {
		BlockPos pos = s.at(rel);
		BlockState state = s.world.getBlockState(pos);
		if (state.getBlock() instanceof TelevisionBlock) TelevisionBlock.tune(s.world, pos, state, channel, false);
	}

	// ------------------------------------------------------------------ small vocabulary

	/** A container with a few ordinary things in it, for a player who takes the hint about packing. */
	private static void fill(Site s, BlockPos rel, net.minecraft.item.Item... items) {
		fill(s, rel.getX(), rel.getY(), rel.getZ(), items);
	}

	private static void fill(Site s, int x, int y, int z, net.minecraft.item.Item... items) {
		if (!(s.world.getBlockEntity(s.at(x, y, z)) instanceof HouseContainerBlockEntity container)) return;
		for (int i = 0; i < items.length && i * 2 < container.size(); i++) {
			container.setStack(i * 2, new ItemStack(items[i], items[i].getMaxCount() > 1 ? 1 + (i % 3) : 1));
		}
		container.markDirty();
	}

	/** Recomputes the connected parts of a run of couch or counter or bath, after the whole run is placed. */
	private static void connect(Site s, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (BlockPos pos : BlockPos.iterate(s.at(x0, y0, z0), s.at(x1, y1, z1))) {
			BlockState state = s.world.getBlockState(pos);
			if (!(state.getBlock() instanceof ConnectedPropBlock)) continue;
			Direction facing = state.get(ConnectedPropBlock.FACING);
			boolean left = sameRun(s.world, pos.offset(facing.rotateYClockwise()), state);
			boolean right = sameRun(s.world, pos.offset(facing.rotateYCounterclockwise()), state);
			ConnectedPropBlock.Part part = left && right ? ConnectedPropBlock.Part.MIDDLE
					: left ? ConnectedPropBlock.Part.RIGHT : right ? ConnectedPropBlock.Part.LEFT : ConnectedPropBlock.Part.SINGLE;
			s.world.setBlockState(pos, state.with(ConnectedPropBlock.PART, part), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		}
	}

	private static boolean sameRun(ServerWorld world, BlockPos pos, BlockState like) {
		BlockState other = world.getBlockState(pos);
		return other.isOf(like.getBlock()) && other.get(ConnectedPropBlock.FACING) == like.get(ConnectedPropBlock.FACING);
	}

	/** A prop that faces a way. */
	private static BlockState prop(Block block, Direction facing) {
		return block.getDefaultState().with(PropBlock.FACING, facing);
	}

	/** Something hung on a wall, its front facing {@code facing} and the wall behind it. */
	private static BlockState wall(Block block, Direction facing) {
		return block.getDefaultState().with(PropBlock.FACING, facing);
	}

	private static BlockState facing(Block block, net.minecraft.state.property.DirectionProperty property, Direction facing) {
		return block.getDefaultState().with(property, facing);
	}

	private static BlockState stairs(Block block, Direction facing) {
		return block.getDefaultState().with(StairsBlock.FACING, facing);
	}

	private static void door(Site s, int x, int y, int z, Block door, Direction facing, boolean open) {
		door(s, x, y, z, door, facing, open, DoorHinge.LEFT);
	}

	private static void door(Site s, int x, int y, int z, Block door, Direction facing, boolean open, DoorHinge hinge) {
		BlockState lower = door.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.OPEN, open)
				.with(DoorBlock.HINGE, hinge).with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		s.set(x, y, z, lower);
		s.set(x, y + 1, z, lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A corner of the world to build from, with the placement flags every room here needs. */
	private static final class Site {
		final ServerWorld world;
		final BlockPos base;

		Site(ServerWorld world, BlockPos base) {
			this.world = world;
			this.base = base;
		}

		BlockPos at(int x, int y, int z) {
			return base.add(x, y, z);
		}

		BlockPos at(BlockPos rel) {
			return base.add(rel);
		}

		BlockState get(int x, int y, int z) {
			return world.getBlockState(at(x, y, z));
		}

		BlockState get(BlockPos rel) {
			return world.getBlockState(at(rel));
		}

		/** No neighbour updates and no drops: a room that builds itself must not rain furniture. */
		BlockPos set(int x, int y, int z, BlockState state) {
			BlockPos pos = at(x, y, z);
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
			return pos;
		}

		BlockPos set(BlockPos rel, BlockState state) {
			return set(rel.getX(), rel.getY(), rel.getZ(), state);
		}

		/** Fills the box from one corner to the other, inclusive. */
		void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, state);
				}
			}
		}
	}
}
