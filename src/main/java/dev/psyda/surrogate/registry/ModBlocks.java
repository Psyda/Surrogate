package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.BreachedPlatingBlock;
import dev.psyda.surrogate.block.PipeBlock;
import dev.psyda.surrogate.block.PropBlock;
import dev.psyda.surrogate.block.SetBlock;
import dev.psyda.surrogate.block.VehicleFabricatorBlock;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.util.shape.VoxelShapes;
import dev.psyda.surrogate.block.MicrowaveBlock;
import dev.psyda.surrogate.block.TerminalBlock;
import dev.psyda.surrogate.block.AirlockDoorBlock;
import dev.psyda.surrogate.block.ChargingDockBlock;
import dev.psyda.surrogate.block.ConduitBlock;
import dev.psyda.surrogate.block.DeconShowerBlock;
import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.DockDoorBlock;
import dev.psyda.surrogate.block.ChassisPortBlock;
import dev.psyda.surrogate.block.CrawlerBayBlock;
import dev.psyda.surrogate.block.CrawlerHatchBlock;
import dev.psyda.surrogate.block.HelmBlock;
import dev.psyda.surrogate.block.LifeSupportBlock;
import dev.psyda.surrogate.block.PosterBlock;
import dev.psyda.surrogate.block.ReinforcedGlassBlock;
import dev.psyda.surrogate.block.SolarCollectorBlock;
import dev.psyda.surrogate.block.VentBlock;
import dev.psyda.surrogate.block.GeyserBlock;
import dev.psyda.surrogate.block.GeothermalTapBlock;
import dev.psyda.surrogate.block.DamperBeaconBlock;
import dev.psyda.surrogate.block.RelayMastBlock;
import dev.psyda.surrogate.block.SpanAnchorBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ColoredFallingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ColorCode;

public final class ModBlocks {
	// Machines
	public static final Block DIVE_CHAIR = register("dive_chair", new DiveChairBlock(machine()));
	public static final Block CHARGING_DOCK = register("charging_dock", new ChargingDockBlock(machine()));
	public static final Block LIFE_SUPPORT = register("life_support", new LifeSupportBlock(machine().luminance(state -> state.get(LifeSupportBlock.ACTIVE) ? 4 : 0)));
	public static final Block SOLAR_COLLECTOR = register("solar_collector", new SolarCollectorBlock(machine()));
	public static final Block POWER_CONDUIT = register("power_conduit", new ConduitBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(2.5f, 8.0f).requiresTool().sounds(BlockSoundGroup.COPPER)));
	public static final Block DECON_SHOWER = register("decon_shower", new DeconShowerBlock(machine().luminance(state -> state.get(DeconShowerBlock.LIT) ? 6 : 0)));
	/** The galley unit: heats what is put in it, and objects to being hurried. */
	public static final Block MICROWAVE = register("microwave", new MicrowaveBlock(machine().luminance(state -> state.get(MicrowaveBlock.LIT) ? 7 : 0)));
	/** A wall plate with a screen: the company network, one unit per room. Airtight. */
	public static final Block TERMINAL = register("terminal", new TerminalBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(3.5f, 12.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE).luminance(state -> 5)));

	// Base structure
	public static final Block HULL_PLATING = register("hull_plating", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(3.5f, 12.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE)));
	public static final Block REINFORCED_GLASS = register("reinforced_glass", new ReinforcedGlassBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.PALE_GREEN).strength(2.5f, 12.0f).requiresTool().sounds(BlockSoundGroup.GLASS).nonOpaque()
			.allowsSpawning((state, world, pos, type) -> false).solidBlock((state, world, pos) -> false)
			.suffocates((state, world, pos) -> false).blockVision((state, world, pos) -> false)));
	public static final Block AIRLOCK_DOOR = register("airlock_door", new AirlockDoorBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(4.0f, 12.0f).requiresTool().sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.DESTROY)));
	/** The crawler cabin's fittings: consoles, hatch and bay are part of the hull, so unbreakable and airtight. */
	public static final Block CRAWLER_HELM = register("crawler_helm", new HelmBlock(1, AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.NETHERITE).luminance(state -> 5)));
	public static final Block CRAWLER_DOCK_CONSOLE = register("crawler_dock_console", new HelmBlock(2, AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.NETHERITE).luminance(state -> 5)));
	public static final Block CRAWLER_HATCH = register("crawler_hatch", new CrawlerHatchBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL)));
	public static final Block CRAWLER_BAY = register("crawler_bay", new CrawlerBayBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.NETHERITE)));
	/** A shelter's chassis port: the plate a chassis talks to the room through. Part of the wall, so unbreakable. */
	public static final Block CHASSIS_PORT = register("chassis_port", new ChassisPortBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.NETHERITE).luminance(state -> 5)));
	/** The door in a base's docking collar: sealed until a crawler is on the collar, and part of the hull, so unbreakable. */
	public static final Block DOCK_DOOR = register("dock_door", new DockDoorBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(-1.0f, 3600000.0f).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK)));
	/** A hull plate that has torn open: not airtight until a plate is used on it. */
	public static final Block BREACHED_PLATING = register("breached_plating", new BreachedPlatingBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(4.0f, 12.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE).nonOpaque()));
	/** A hull plate with a print on one face: posters, charts and console screens for the ship. */
	public static final Block POSTER = register("poster", new PosterBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(3.5f, 12.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE)
			.luminance(state -> state.get(PosterBlock.PRINT) == PosterBlock.Print.CONSOLE || state.get(PosterBlock.PRINT) == PosterBlock.Print.CONSOLE_ALERT ? 5 : 0)));

	// Toxic wastes terrain
	public static final Block CAUSTIC_SAND = register("caustic_sand", new ColoredFallingBlock(new ColorCode(0xB3A65A), AbstractBlock.Settings.create()
			.mapColor(MapColor.PALE_YELLOW).instrument(NoteBlockInstrument.SNARE).strength(0.5f).sounds(BlockSoundGroup.SAND)));
	public static final Block CAUSTIC_SANDSTONE = register("caustic_sandstone", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.PALE_YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresTool().strength(0.8f).sounds(BlockSoundGroup.STONE)));
	public static final Block ASH = register("ash", new ColoredFallingBlock(new ColorCode(0x5B5955), AbstractBlock.Settings.create()
			.mapColor(MapColor.GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.5f).sounds(BlockSoundGroup.SNOW).velocityMultiplier(0.8f)));
	public static final Block SULFUR_CRUST = register("sulfur_crust", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.YELLOW).instrument(NoteBlockInstrument.BASEDRUM).requiresTool().strength(1.5f, 3.0f).sounds(BlockSoundGroup.NETHER_ORE)));
	public static final Block SCRAP_HEAP = register("scrap_heap", new Block(AbstractBlock.Settings.create()
			.mapColor(MapColor.BROWN).strength(1.0f, 2.0f).sounds(BlockSoundGroup.COPPER)));
	public static final Block VENT = register("vent", new VentBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresTool().strength(2.0f, 6.0f).sounds(BlockSoundGroup.BASALT)
			.luminance(state -> 3)));
	/** The fumaroles that still work. Ninety seconds of clock and eight of column (docs/DESIGN-hazards.md). */
	public static final Block GEYSER = register("geyser", new GeyserBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK).instrument(NoteBlockInstrument.BASEDRUM).requiresTool().strength(2.5f, 6.0f).sounds(BlockSoundGroup.BASALT)
			.luminance(state -> switch (state.get(GeyserBlock.STAGE)) {
				case QUIET -> 3;
				case STEAM -> 5;
				case RUMBLE -> 8;
				case ERUPTING -> 13;
			})));
	/** The cap that turns one of them into a hundred solar collectors, and stops the sulfur. */
	public static final Block GEOTHERMAL_TAP = register("geothermal_tap", new GeothermalTapBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(4.0f, 12.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE).luminance(state -> 6)));

	// The vehicle fabricator: where the crawler, and later the ship, get built.
	public static final Block VEHICLE_FABRICATOR = register("vehicle_fabricator", new VehicleFabricatorBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY).strength(4.0f, 12.0f).requiresTool().sounds(BlockSoundGroup.NETHERITE).nonOpaque()
			.luminance(state -> state.get(VehicleFabricatorBlock.BUILDING) ? 12 : 3)));

	// Countermeasures: the three things you plant on the ground to make a hazard somebody else's problem.
	/** Sorensen's repeater: hears the pod's band and says it again, for as long as something feeds it. */
	public static final Block RELAY_MAST = register("relay_mast", new RelayMastBlock(hazard()));

	// The survey tier: a table that draws you the ground, the beacons that decide how much of it, and the
	// pillar you pour power into when you want the rest (docs/DESIGN-survey.md).
	/** The table. Reading it is one right-click and the picture is built on the spot. */
	public static final Block SURVEY_STATION = register("survey_station",
			new dev.psyda.surrogate.block.SurveyStationBlock(prop(BlockSoundGroup.METAL, true).luminance(state -> 6)));
	/** A pole with a lamp on it, pushed into the dirt. Green if the network reaches it, red if it does not. */
	public static final Block SURVEY_BEACON = register("survey_beacon",
			new dev.psyda.surrogate.block.SurveyBeaconBlock(prop(BlockSoundGroup.METAL, false).noCollision()
					.luminance(state -> state.get(dev.psyda.surrogate.block.SurveyBeaconBlock.LINKED) ? 9 : 5)));
	/** The pillar. Range as the square root of everything ever fed into it. */
	public static final Block LONG_RANGE_SCANNER = register("long_range_scanner",
			new dev.psyda.surrogate.block.LongRangeScannerBlock(prop(BlockSoundGroup.NETHERITE, true)
					.luminance(state -> state.get(dev.psyda.surrogate.block.LongRangeScannerBlock.LIT) ? 10 : 0)));
	/** Tanaka's damper with a cable behind it: holds the rock quiet around a mine that has to stay put. */
	public static final Block DAMPER_BEACON = register("damper_beacon", new DamperBeaconBlock(hazard()));
	/** The near end of a bridge nobody has built yet. It remembers which way the span goes. */
	public static final Block SPAN_ANCHOR = register("span_anchor", new SpanAnchorBlock(hazard()));
	/** What the belt leaves of a machine that stood in its rain. Not a full cube, so it is also a hole. */
	public static final Block CORRODED_MACHINE = register("corroded_machine", new dev.psyda.surrogate.block.CorrodedMachineBlock(
			AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_GREEN).strength(1.5f, 3.0f).requiresTool()
					.sounds(BlockSoundGroup.COPPER).nonOpaque()));

	// Props: the furniture of a habitat. Full cubes stay opaque; anything smaller is see-through.
	public static final Block SUPPLY_CRATE = register("supply_crate", new PropBlock(prop(BlockSoundGroup.COPPER, true), PropBlock.Mount.FACING));
	public static final Block LOCKER = register("locker", new PropBlock(prop(BlockSoundGroup.METAL, true), PropBlock.Mount.FACING));
	public static final Block DATA_RACK = register("data_rack", new PropBlock(prop(BlockSoundGroup.METAL, true).luminance(state -> 4), PropBlock.Mount.FACING));
	public static final Block DECK_GRATING = register("deck_grating", new PropBlock(prop(BlockSoundGroup.CHAIN, false), PropBlock.Mount.FIXED));
	public static final Block HAZARD_PLATING = register("hazard_plating", new Block(prop(BlockSoundGroup.NETHERITE, true)));
	public static final Block DECK_PLATING = register("deck_plating", new Block(prop(BlockSoundGroup.NETHERITE, true)));
	public static final Block HULL_FRAME = register("hull_frame", new Block(prop(BlockSoundGroup.NETHERITE, true)));
	public static final Block CEILING_LAMP = register("ceiling_lamp", new PropBlock(prop(BlockSoundGroup.GLASS, false).luminance(state -> 15), PropBlock.Mount.FIXED,
			Block.createCuboidShape(2, 14, 2, 14, 16, 14)));
	public static final Block PIPE = register("pipe", new PipeBlock(prop(BlockSoundGroup.COPPER, false)));
	public static final Block HANDRAIL = register("handrail", new PropBlock(prop(BlockSoundGroup.CHAIN, false), PropBlock.Mount.FACING, VoxelShapes.union(
			Block.createCuboidShape(1, 0, 1, 3, 12, 3), Block.createCuboidShape(13, 0, 1, 15, 12, 3), Block.createCuboidShape(0, 10, 1, 16, 12, 3))));
	public static final Block CREW_SEAT = register("crew_seat", new PropBlock(prop(BlockSoundGroup.WOOL, false), PropBlock.Mount.FACING, VoxelShapes.union(
			Block.createCuboidShape(2, 0, 2, 14, 9, 14), Block.createCuboidShape(2, 9, 11, 14, 18, 14))));
	public static final Block MESS_TABLE = register("mess_table", new PropBlock(prop(BlockSoundGroup.METAL, false), PropBlock.Mount.FIXED, VoxelShapes.union(
			Block.createCuboidShape(0, 13, 0, 16, 16, 16), Block.createCuboidShape(6, 0, 6, 10, 13, 10), Block.createCuboidShape(3, 0, 3, 13, 1, 13))));
	public static final Block HYDROPONIC_TRAY = register("hydroponic_tray", new PropBlock(prop(BlockSoundGroup.METAL, false).luminance(state -> 3), PropBlock.Mount.FIXED,
			Block.createCuboidShape(0, 0, 0, 16, 6, 16)));
	public static final Block MED_CABINET = register("med_cabinet", new PropBlock(prop(BlockSoundGroup.METAL, false), PropBlock.Mount.WALL,
			Block.createCuboidShape(2, 2, 12, 14, 14, 16)));
	public static final Block FIRE_EXTINGUISHER = register("fire_extinguisher", new PropBlock(prop(BlockSoundGroup.METAL, false), PropBlock.Mount.WALL,
			Block.createCuboidShape(5, 2, 11, 11, 14, 16)));
	public static final Block WALL_VENT = register("wall_vent", new PropBlock(prop(BlockSoundGroup.METAL, false), PropBlock.Mount.WALL,
			Block.createCuboidShape(0, 0, 15, 16, 16, 16)));
	public static final Block COOLANT_TANK = register("coolant_tank", new PropBlock(prop(BlockSoundGroup.METAL, false).luminance(state -> 5), PropBlock.Mount.FIXED,
			Block.createCuboidShape(2, 0, 2, 14, 16, 14)));
	public static final Block BUNK = register("bunk", new dev.psyda.surrogate.block.BunkBlock(prop(BlockSoundGroup.WOOL, false)));
	public static final Block CHEM_DRUM = register("chem_drum", new PropBlock(prop(BlockSoundGroup.COPPER, false), PropBlock.Mount.FIXED,
			Block.createCuboidShape(2, 0, 2, 14, 16, 14)));
	public static final Block SURVEY_MARKER = register("survey_marker", new PropBlock(prop(BlockSoundGroup.METAL, false).noCollision(), PropBlock.Mount.FIXED,
			Block.createCuboidShape(5, 0, 5, 11, 16, 11)));
	public static final Block PAD_LIGHT = register("pad_light", new PropBlock(prop(BlockSoundGroup.METAL, false).luminance(state -> 12), PropBlock.Mount.FIXED,
			Block.createCuboidShape(4, 0, 4, 12, 5, 12)));
	public static final Block ANTENNA_MAST = register("antenna_mast", new PropBlock(prop(BlockSoundGroup.CHAIN, false), PropBlock.Mount.FIXED,
			Block.createCuboidShape(6, 0, 6, 10, 16, 10)));
	// ------------------------------------------------------------------ Earth, 2189
	// The flashback's furniture (docs/DESIGN-flashback.md). None of it is made of anything found on Sallow,
	// none of it can be crafted here, and all of it can be carried out of a dream in a suitcase. That is the
	// only way any of it exists in this game, which is the point of it.
	public static final Block WALLPAPER_STRIPE = register("wallpaper_stripe", new Block(paper()));
	public static final Block WALLPAPER_FLORAL = register("wallpaper_floral", new Block(paper()));
	public static final Block RADIO_SET = register("radio_set", new SetBlock(house(BlockSoundGroup.WOOD),
			Block.createCuboidShape(2, 0, 4, 14, 10, 13), () -> ModSounds.FLASHBACK_RADIO, 160));
	/** The set. Four channels, one of them off; the scripts tune it and the player can too. */
	public static final Block TELEVISION = register("television", new dev.psyda.surrogate.block.TelevisionBlock(
			house(BlockSoundGroup.WOOD).luminance(state -> state.get(dev.psyda.surrogate.block.TelevisionBlock.CHANNEL)
					== dev.psyda.surrogate.block.TelevisionBlock.Channel.OFF ? 0 : 9),
			Block.createCuboidShape(1, 0, 4, 15, 13, 12)));
	public static final Block DESK_LAMP = register("desk_lamp", new PropBlock(house(BlockSoundGroup.METAL).luminance(state -> 13),
			PropBlock.Mount.FACING, Block.createCuboidShape(4, 0, 4, 12, 14, 12)));
	public static final Block PHOTO_FRAME = register("photo_frame", new PropBlock(house(BlockSoundGroup.WOOD).noCollision(),
			PropBlock.Mount.WALL, Block.createCuboidShape(3, 3, 15, 13, 13, 16)));
	public static final Block WALL_CLOCK = register("wall_clock", new dev.psyda.surrogate.block.WallClockBlock(house(BlockSoundGroup.WOOD).noCollision(),
			Block.createCuboidShape(3, 3, 15, 13, 13, 16)));
	public static final Block SNOW_GLOBE = register("snow_globe", new PropBlock(house(BlockSoundGroup.GLASS).luminance(state -> 3),
			PropBlock.Mount.FIXED, Block.createCuboidShape(5, 0, 5, 11, 9, 11)));
	public static final Block MUG = register("mug", new PropBlock(house(BlockSoundGroup.STONE).noCollision(),
			PropBlock.Mount.FACING, Block.createCuboidShape(5, 0, 5, 11, 6, 11)));
	public static final Block HOUSEPLANT = register("houseplant", new PropBlock(house(BlockSoundGroup.GRASS).noCollision(),
			PropBlock.Mount.FIXED, Block.createCuboidShape(4, 0, 4, 12, 15, 12)));
	public static final Block TELEPHONE = register("telephone", new PropBlock(house(BlockSoundGroup.WOOD),
			PropBlock.Mount.FACING, Block.createCuboidShape(3, 0, 5, 13, 6, 12)));

	// The living room, the kitchen, and upstairs (2026-09-07). Everything a house has that a pod does not.
	/** Three blocks of it make one couch. Sit with a right click. */
	public static final Block COUCH = register("couch", new dev.psyda.surrogate.block.ConnectedPropBlock(house(BlockSoundGroup.WOOL),
			Block.createCuboidShape(0, 0, 2, 16, 14, 16), 0.5));
	public static final Block COFFEE_TABLE = register("coffee_table", new PropBlock(house(BlockSoundGroup.WOOD), PropBlock.Mount.FIXED));
	public static final Block DINING_TABLE = register("dining_table", new PropBlock(house(BlockSoundGroup.WOOD), PropBlock.Mount.FIXED));
	public static final Block DINING_CHAIR = register("dining_chair", new dev.psyda.surrogate.block.ChairBlock(house(BlockSoundGroup.WOOD),
			VoxelShapes.union(Block.createCuboidShape(2, 0, 2, 14, 8, 14), Block.createCuboidShape(2, 8, 11, 14, 16, 14)), 0.5));
	/** Eight slices when the evening starts. */
	public static final Block PIZZA_BOX = register("pizza_box", new dev.psyda.surrogate.block.PizzaBoxBlock(house(BlockSoundGroup.WOOL),
			VoxelShapes.union(Block.createCuboidShape(1, 0, 2, 15, 3, 14), Block.createCuboidShape(1, 0, 13, 15, 13, 15))));
	/** The light over the table, and the switch by the door that works it. */
	public static final Block PENDANT_LAMP = register("pendant_lamp", new dev.psyda.surrogate.block.HouseLightBlock(
			house(BlockSoundGroup.GLASS).noCollision().luminance(state -> state.get(dev.psyda.surrogate.block.HouseLightBlock.LIT) ? 14 : 0),
			Block.createCuboidShape(3, 3, 3, 13, 16, 13)));
	public static final Block PANEL_LIGHT = register("panel_light", new dev.psyda.surrogate.block.HouseLightBlock(
			house(BlockSoundGroup.GLASS).noCollision().luminance(state -> state.get(dev.psyda.surrogate.block.HouseLightBlock.LIT) ? 15 : 0),
			Block.createCuboidShape(0, 14, 0, 16, 16, 16)));
	public static final Block LIGHT_SWITCH = register("light_switch", new dev.psyda.surrogate.block.LightSwitchBlock(
			house(BlockSoundGroup.STONE).noCollision(), Block.createCuboidShape(6, 5, 14, 10, 11, 16)));
	/** The post on the kitchen table, and the date circled on the wall. Both can be read; both can be taken. */
	public static final Block BILLS = register("bills", new dev.psyda.surrogate.block.ReadableBlock(house(BlockSoundGroup.WOOL).noCollision(),
			PropBlock.Mount.FIXED, Block.createCuboidShape(2, 0, 3, 14, 1, 13), "bills", dev.psyda.surrogate.network.DocumentPayload.PAPER));
	public static final Block CALENDAR = register("calendar", new dev.psyda.surrogate.block.ReadableBlock(house(BlockSoundGroup.WOOL).noCollision(),
			PropBlock.Mount.WALL, Block.createCuboidShape(4, 2, 15, 12, 14, 16), "calendar", dev.psyda.surrogate.network.DocumentPayload.PAPER));
	public static final Block PRINTOUT = register("printout", new dev.psyda.surrogate.block.ReadableBlock(house(BlockSoundGroup.WOOL).noCollision(),
			PropBlock.Mount.FIXED, Block.createCuboidShape(3, 0, 3, 13, 1, 13), "contract_copy", dev.psyda.surrogate.network.DocumentPayload.PAPER));
	/** The dog, and where the dog goes. */
	public static final Block DOG_CARRIER = register("dog_carrier", new dev.psyda.surrogate.block.DogCarrierBlock(house(BlockSoundGroup.WOOD),
			Block.createCuboidShape(2, 0, 2, 14, 11, 14)));
	public static final Block DOG_BOWL = register("dog_bowl", new PropBlock(house(BlockSoundGroup.STONE).noCollision(),
			PropBlock.Mount.FIXED, Block.createCuboidShape(4, 0, 4, 12, 3, 12)));
	public static final Block FRIDGE = register("fridge", new dev.psyda.surrogate.block.HouseContainerBlock(house(BlockSoundGroup.METAL),
			Block.createCuboidShape(1, 0, 1, 15, 16, 15)));
	public static final Block KITCHEN_COUNTER = register("kitchen_counter", new PropBlock(house(BlockSoundGroup.WOOD), PropBlock.Mount.FACING));
	public static final Block KITCHEN_SINK = register("kitchen_sink", new PropBlock(house(BlockSoundGroup.WOOD), PropBlock.Mount.FACING));
	public static final Block STOVE = register("stove", new PropBlock(house(BlockSoundGroup.METAL), PropBlock.Mount.FACING));
	public static final Block WARDROBE = register("wardrobe", new dev.psyda.surrogate.block.HouseContainerBlock(house(BlockSoundGroup.WOOD),
			Block.createCuboidShape(0, 0, 2, 16, 16, 16)));
	public static final Block NIGHTSTAND = register("nightstand", new dev.psyda.surrogate.block.HouseContainerBlock(house(BlockSoundGroup.WOOD),
			Block.createCuboidShape(1, 0, 1, 15, 16, 15)));
	public static final Block TOILET = register("toilet", new PropBlock(house(BlockSoundGroup.STONE), PropBlock.Mount.FACING,
			Block.createCuboidShape(3, 0, 2, 13, 14, 14)));
	public static final Block WASHBASIN = register("washbasin", new PropBlock(house(BlockSoundGroup.STONE), PropBlock.Mount.FACING,
			Block.createCuboidShape(2, 0, 5, 14, 15, 16)));
	public static final Block BATHTUB = register("bathtub", new dev.psyda.surrogate.block.ConnectedPropBlock(house(BlockSoundGroup.STONE),
			Block.createCuboidShape(0, 0, 1, 16, 9, 15), 0.3));
	public static final Block MIRROR = register("mirror", new PropBlock(house(BlockSoundGroup.GLASS).noCollision(),
			PropBlock.Mount.WALL, Block.createCuboidShape(3, 2, 15, 13, 14, 16)));
	/** The case. Whatever is in it when you leave the house is what you brought. */
	public static final Block SUITCASE = register("suitcase", new dev.psyda.surrogate.block.SuitcaseBlock(house(BlockSoundGroup.WOOL),
			Block.createCuboidShape(2, 0, 3, 14, 13, 13), Block.createCuboidShape(2, 0, 3, 14, 5, 13)));

	// The office, eleven at night.
	public static final Block OFFICE_DESK = register("office_desk", new PropBlock(house(BlockSoundGroup.WOOD), PropBlock.Mount.FACING));
	public static final Block OFFICE_CHAIR = register("office_chair", new dev.psyda.surrogate.block.ChairBlock(house(BlockSoundGroup.WOOL),
			VoxelShapes.union(Block.createCuboidShape(3, 0, 3, 13, 9, 13), Block.createCuboidShape(3, 9, 10, 13, 16, 13)), 0.56));
	public static final Block MONITOR = register("monitor", new dev.psyda.surrogate.block.MonitorBlock(
			house(BlockSoundGroup.METAL).luminance(state -> state.get(dev.psyda.surrogate.block.MonitorBlock.SCREEN)
					== dev.psyda.surrogate.block.MonitorBlock.Screen.OFF ? 0 : 7),
			Block.createCuboidShape(2, 0, 5, 14, 12, 11)));
	public static final Block FILING_CABINET = register("filing_cabinet", new dev.psyda.surrogate.block.HouseContainerBlock(house(BlockSoundGroup.METAL),
			Block.createCuboidShape(2, 0, 2, 14, 16, 14)));
	public static final Block WATER_COOLER = register("water_cooler", new PropBlock(house(BlockSoundGroup.GLASS), PropBlock.Mount.FIXED,
			Block.createCuboidShape(4, 0, 4, 12, 16, 12)));
	public static final Block WHITEBOARD = register("whiteboard", new PropBlock(house(BlockSoundGroup.METAL).noCollision(),
			PropBlock.Mount.WALL, Block.createCuboidShape(0, 1, 15, 16, 15, 16)));
	public static final Block PRINTER = register("printer", new PropBlock(house(BlockSoundGroup.METAL), PropBlock.Mount.FACING,
			Block.createCuboidShape(2, 0, 3, 14, 7, 13)));
	/** Eleven years, in one box, with room left over. */
	public static final Block CARDBOARD_BOX = register("cardboard_box", new dev.psyda.surrogate.block.HouseContainerBlock(house(BlockSoundGroup.WOOL),
			Block.createCuboidShape(2, 0, 2, 14, 10, 14)));

	// The bar on the corner, last orders.
	public static final Block BAR_COUNTER = register("bar_counter", new dev.psyda.surrogate.block.ConnectedPropBlock(house(BlockSoundGroup.WOOD),
			VoxelShapes.fullCube(), 0));
	public static final Block BAR_STOOL = register("bar_stool", new dev.psyda.surrogate.block.ChairBlock(house(BlockSoundGroup.WOOD),
			Block.createCuboidShape(4, 0, 4, 12, 10, 12), 0.62));
	public static final Block PINT_GLASS = register("pint_glass", new dev.psyda.surrogate.block.PintGlassBlock(house(BlockSoundGroup.GLASS).noCollision(),
			Block.createCuboidShape(6, 0, 6, 10, 7, 10)));
	public static final Block DARTBOARD = register("dartboard", new dev.psyda.surrogate.block.DartboardBlock(house(BlockSoundGroup.WOOL).noCollision(),
			Block.createCuboidShape(2, 2, 14, 14, 14, 16)));
	public static final Block BACK_BAR = register("back_bar", new PropBlock(house(BlockSoundGroup.GLASS), PropBlock.Mount.WALL,
			Block.createCuboidShape(0, 0, 10, 16, 16, 16)));
	public static final Block BEER_TAP = register("beer_tap", new PropBlock(house(BlockSoundGroup.METAL).noCollision(), PropBlock.Mount.FACING,
			Block.createCuboidShape(5, 0, 6, 11, 12, 10)));
	public static final Block NEON_SIGN = register("neon_sign", new PropBlock(house(BlockSoundGroup.GLASS).noCollision().luminance(state -> 10),
			PropBlock.Mount.WALL, Block.createCuboidShape(1, 4, 15, 15, 12, 16)));

	/** The wind count's stake: planting one tells the research arc where it is (docs/DESIGN-campaign.md, act I). */
	public static final Block SURVEY_STAKE = register("survey_stake", new dev.psyda.surrogate.block.SurveyStakeBlock(
			prop(BlockSoundGroup.METAL, false).noCollision(), Block.createCuboidShape(5, 0, 5, 11, 16, 11)));

	// Ores of Sallow: cinnabar in the mesa beds, rock salt near the surface, cobalt in the stone, tellurium deep down.
	public static final Block CINNABAR_ORE = register("cinnabar_ore", new ExperienceDroppingBlock(UniformIntProvider.create(1, 3), ore(BlockSoundGroup.TUFF, 3.0f)));
	public static final Block HALITE_ORE = register("halite_ore", new ExperienceDroppingBlock(UniformIntProvider.create(0, 2), ore(BlockSoundGroup.STONE, 1.5f)));
	public static final Block COBALT_ORE = register("cobalt_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 5), ore(BlockSoundGroup.STONE, 3.0f)));
	public static final Block DEEPSLATE_COBALT_ORE = register("deepslate_cobalt_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 5), ore(BlockSoundGroup.DEEPSLATE, 4.5f)));
	public static final Block TELLURIUM_ORE = register("tellurium_ore", new ExperienceDroppingBlock(UniformIntProvider.create(4, 8), ore(BlockSoundGroup.DEEPSLATE, 4.5f).luminance(state -> 3)));

	/**
	 * Wallpaper and the like: soft, quiet, and nothing to do with the iron-grey the rest of this file is.
	 * Breaks by hand, because a house is not a hull.
	 */
	/**
	 * Furniture from a house. Comes off in your hands, and that is the whole feature: {@link #prop} sets
	 * {@code requiresTool}, so a mug registered with it drops nothing at all unless somebody remembers to put
	 * it in a mineable tag, and a suitcase you cannot fill is not a scene.
	 */
	private static AbstractBlock.Settings house(BlockSoundGroup sounds) {
		return AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(0.6f).sounds(sounds).nonOpaque();
	}

	private static AbstractBlock.Settings paper() {
		return AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW).strength(0.8f).sounds(BlockSoundGroup.WOOL);
	}

	private static AbstractBlock.Settings prop(BlockSoundGroup sounds, boolean fullCube) {
		AbstractBlock.Settings settings = AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(2.0f, 6.0f).requiresTool().sounds(sounds);
		return fullCube ? settings : settings.nonOpaque();
	}

	private static AbstractBlock.Settings ore(BlockSoundGroup sounds, float hardness) {
		return AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(hardness, 3.0f).requiresTool().sounds(sounds);
	}

	private static AbstractBlock.Settings machine() {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.IRON_GRAY)
				.strength(3.0f, 6.0f)
				.requiresTool()
				.sounds(BlockSoundGroup.METAL)
				.nonOpaque();
	}

	/** A countermeasure planted outdoors: a machine's steel, but a full cube, because it is mostly ballast. */
	private static AbstractBlock.Settings hazard() {
		return AbstractBlock.Settings.create()
				.mapColor(MapColor.IRON_GRAY)
				.strength(3.0f, 6.0f)
				.requiresTool()
				.sounds(BlockSoundGroup.METAL);
	}

	private static Block register(String name, Block block) {
		return Registry.register(Registries.BLOCK, Surrogate.id(name), block);
	}

	public static void register() {
	}

	private ModBlocks() {
	}
}
