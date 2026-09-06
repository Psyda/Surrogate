package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.BreachedPlatingBlock;
import dev.psyda.surrogate.block.PipeBlock;
import dev.psyda.surrogate.block.PropBlock;
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
	/** The wind count's stake: planting one tells the research arc where it is (docs/DESIGN-campaign.md, act I). */
	public static final Block SURVEY_STAKE = register("survey_stake", new dev.psyda.surrogate.block.SurveyStakeBlock(
			prop(BlockSoundGroup.METAL, false).noCollision(), Block.createCuboidShape(5, 0, 5, 11, 16, 11)));

	// Ores of Sallow: cinnabar in the mesa beds, rock salt near the surface, cobalt in the stone, tellurium deep down.
	public static final Block CINNABAR_ORE = register("cinnabar_ore", new ExperienceDroppingBlock(UniformIntProvider.create(1, 3), ore(BlockSoundGroup.TUFF, 3.0f)));
	public static final Block HALITE_ORE = register("halite_ore", new ExperienceDroppingBlock(UniformIntProvider.create(0, 2), ore(BlockSoundGroup.STONE, 1.5f)));
	public static final Block COBALT_ORE = register("cobalt_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 5), ore(BlockSoundGroup.STONE, 3.0f)));
	public static final Block DEEPSLATE_COBALT_ORE = register("deepslate_cobalt_ore", new ExperienceDroppingBlock(UniformIntProvider.create(2, 5), ore(BlockSoundGroup.DEEPSLATE, 4.5f)));
	public static final Block TELLURIUM_ORE = register("tellurium_ore", new ExperienceDroppingBlock(UniformIntProvider.create(4, 8), ore(BlockSoundGroup.DEEPSLATE, 4.5f).luminance(state -> 3)));

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
