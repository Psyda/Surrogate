package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.item.ArcCutterItem;
import dev.psyda.surrogate.item.AtmoScannerItem;
import dev.psyda.surrogate.entity.CrawlerModule;
import dev.psyda.surrogate.item.CrawlerBlueprintItem;
import dev.psyda.surrogate.item.CrawlerKitItem;
import dev.psyda.surrogate.item.CrawlerModuleItem;
import dev.psyda.surrogate.item.FieldRadioItem;
import dev.psyda.surrogate.item.TomatoSeedsItem;
import dev.psyda.surrogate.item.LockedBayItem;
import dev.psyda.surrogate.item.MiningDrillItem;
import dev.psyda.surrogate.item.PowerCellItem;
import dev.psyda.surrogate.item.RebreatherItem;
import dev.psyda.surrogate.item.RepairKitItem;
import dev.psyda.surrogate.item.RobotChassisItem;
import dev.psyda.surrogate.item.RobotUpgradeItem;
import dev.psyda.surrogate.item.ScrapChassisItem;
import dev.psyda.surrogate.item.SealedSampleItem;
import dev.psyda.surrogate.item.UplinkCardItem;
import dev.psyda.surrogate.item.WrenchItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.TallBlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	// Components
	public static final Item ROBOT_CORE = register("robot_core", new Item(new Item.Settings()));
	public static final Item SERVO_MOTOR = register("servo_motor", new Item(new Item.Settings()));
	public static final Item SULFUR = register("sulfur", new Item(new Item.Settings()));

	// Chassis
	public static final Item ROBOT_CHASSIS = register("robot_chassis", new RobotChassisItem(new Item.Settings().maxCount(1)));
	/** The crawler in a crate; use it on the ground to assemble the hull. */
	public static final Item CRAWLER_KIT = register("crawler_kit", new CrawlerKitItem(new Item.Settings().maxCount(1)));
	/** The sample vehicle in a crate; only a fabricator gantry will build one. */
	public static final Item ROCKET_KIT = register("rocket_kit", new dev.psyda.surrogate.item.RocketKitItem(new Item.Settings().maxCount(1)));
	/** The plans for the kit, from the researcher; the bench keeps handing it back. */
	public static final Item CRAWLER_BLUEPRINT = register("crawler_blueprint", new CrawlerBlueprintItem(new Item.Settings().maxCount(1)));
	public static final Item CHASSIS_PORT = register("chassis_port", new BlockItem(ModBlocks.CHASSIS_PORT, new Item.Settings()));
	public static final Item SCRAP_CHASSIS = register("scrap_chassis", new ScrapChassisItem(new Item.Settings().maxCount(1)));

	// Consumables and tools
	public static final Item REPAIR_KIT = register("repair_kit", new RepairKitItem(new Item.Settings().maxCount(16)));
	public static final Item POWER_CELL = register("power_cell", new PowerCellItem(new Item.Settings().maxCount(16)));
	public static final Item WRENCH = register("wrench", new WrenchItem(new Item.Settings().maxCount(1)));
	public static final Item UPLINK_CARD = register("uplink_card", new UplinkCardItem(new Item.Settings().maxCount(1)));
	/** A minute of clean air for a body that has to go outside. */
	public static final Item REBREATHER = register("rebreather", new RebreatherItem(new Item.Settings().maxCount(4)));

	// Chassis tools
	public static final Item MINING_DRILL = register("mining_drill", new MiningDrillItem(new Item.Settings().maxCount(1)));
	public static final Item ARC_CUTTER = register("arc_cutter", new ArcCutterItem(new Item.Settings().maxCount(1)));
	public static final Item ATMO_SCANNER = register("atmo_scanner", new AtmoScannerItem(new Item.Settings().maxCount(1)));
	public static final Item FIELD_RADIO = register("field_radio", new FieldRadioItem(new Item.Settings().maxCount(1)));
	/** A packet from the Provender's galley. Halloran asked; the company said no; Castellanos said nothing. */
	public static final Item TOMATO_SEEDS = register("tomato_seeds", new TomatoSeedsItem(new Item.Settings().maxCount(1)));
	/** A litre of the seep, crimped shut. Opening it outdoors is the only way to waste the trip. */
	public static final Item SEALED_SAMPLE = register("sealed_sample", new SealedSampleItem(new Item.Settings().maxCount(4)));
	/** Placeholder for inventory slots without a cargo bay; never obtainable on purpose. */
	public static final Item LOCKED_BAY = register("locked_bay", new LockedBayItem(new Item.Settings().maxCount(1)));

	// Upgrades
	public static final Item PLATING_MK1 = register("plating_mk1", new RobotUpgradeItem(RobotUpgradeItem.Kind.PLATING_MK1, new Item.Settings()));
	public static final Item PLATING_MK2 = register("plating_mk2", new RobotUpgradeItem(RobotUpgradeItem.Kind.PLATING_MK2, new Item.Settings()));
	public static final Item BATTERY_UPGRADE = register("battery_upgrade", new RobotUpgradeItem(RobotUpgradeItem.Kind.BATTERY, new Item.Settings()));
	public static final Item CARGO_BAY = register("cargo_bay", new RobotUpgradeItem(RobotUpgradeItem.Kind.CARGO_BAY, new Item.Settings()));
	public static final Item FABRICATOR = register("fabricator", new RobotUpgradeItem(RobotUpgradeItem.Kind.FABRICATOR, new Item.Settings()));

	// Countermeasures: one for each of the four things on Sallow, and one bolted to the crawler instead.
	/** Sorensen's, from act one: the link and the radio reach the far side of a table. */
	public static final Item RELAY_MODULE = register("relay_module", new RobotUpgradeItem(RobotUpgradeItem.Kind.RELAY, new Item.Settings()));
	/** Tanaka's, from act two: forty seconds of drilling below the line instead of eight. */
	public static final Item RESONANCE_DAMPER = register("resonance_damper", new RobotUpgradeItem(RobotUpgradeItem.Kind.DAMPER, new Item.Settings()));
	/** Brandt's pattern, cut down to a chassis: the belt's rain takes the coating first. */
	public static final Item ACID_COATING = register("acid_coating", new RobotUpgradeItem(RobotUpgradeItem.Kind.COATING, new Item.Settings()));
	/** Reyes', from act five: a storm still costs the picture, but it holds twice as long. */
	public static final Item SHIELDED_UPLINK = register("shielded_uplink", new RobotUpgradeItem(RobotUpgradeItem.Kind.SHIELD, new Item.Settings()));
	/** Brandt's pattern at full size, in panels, for the hull that has to live over there. */
	public static final Item CERAMIC_CLADDING = register("ceramic_cladding", new CrawlerModuleItem(CrawlerModule.CLADDING, new Item.Settings()));

	// Okafor's survey and Brandt's ark. Neither is a countermeasure; both are bays on the same chassis.
	/** Okafor's probe. Fitted, a chassis can take a reading off anything living by touching it. */
	public static final Item BIO_SAMPLER = register("bio_sampler", new RobotUpgradeItem(RobotUpgradeItem.Kind.SAMPLER, new Item.Settings()));
	/** Brandt's crate and padded arm. Fitted, a chassis can pick one of each up without hurting it. */
	public static final Item SPECIMEN_BAG = register("specimen_bag", new RobotUpgradeItem(RobotUpgradeItem.Kind.COLLECTOR, new Item.Settings()));
	/**
	 * The other half of the sampler: without this in a terminal, a reading is a number nobody can read.
	 * Used on any hub terminal, once.
	 */
	public static final Item ANALYSIS_DISK = register("analysis_disk", new dev.psyda.surrogate.item.AnalysisDiskItem(new Item.Settings().maxCount(1)));

	// Blocks
	public static final Item DIVE_CHAIR = register("dive_chair", new BlockItem(ModBlocks.DIVE_CHAIR, new Item.Settings()));
	public static final Item CHARGING_DOCK = register("charging_dock", new BlockItem(ModBlocks.CHARGING_DOCK, new Item.Settings()));
	public static final Item LIFE_SUPPORT = register("life_support", new BlockItem(ModBlocks.LIFE_SUPPORT, new Item.Settings()));
	public static final Item SOLAR_COLLECTOR = register("solar_collector", new BlockItem(ModBlocks.SOLAR_COLLECTOR, new Item.Settings()));
	public static final Item POWER_CONDUIT = register("power_conduit", new BlockItem(ModBlocks.POWER_CONDUIT, new Item.Settings()));
	public static final Item DECON_SHOWER = register("decon_shower", new BlockItem(ModBlocks.DECON_SHOWER, new Item.Settings()));
	public static final Item HULL_PLATING = register("hull_plating", new BlockItem(ModBlocks.HULL_PLATING, new Item.Settings()));
	public static final Item REINFORCED_GLASS = register("reinforced_glass", new BlockItem(ModBlocks.REINFORCED_GLASS, new Item.Settings()));
	public static final Item AIRLOCK_DOOR = register("airlock_door", new TallBlockItem(ModBlocks.AIRLOCK_DOOR, new Item.Settings()));
	public static final Item POSTER = register("poster", new BlockItem(ModBlocks.POSTER, new Item.Settings()));
	public static final Item MICROWAVE = register("microwave", new BlockItem(ModBlocks.MICROWAVE, new Item.Settings()));
	public static final Item TERMINAL = register("terminal", new BlockItem(ModBlocks.TERMINAL, new Item.Settings()));
	public static final Item CAUSTIC_SAND = register("caustic_sand", new BlockItem(ModBlocks.CAUSTIC_SAND, new Item.Settings()));
	public static final Item CAUSTIC_SANDSTONE = register("caustic_sandstone", new BlockItem(ModBlocks.CAUSTIC_SANDSTONE, new Item.Settings()));
	public static final Item ASH = register("ash", new BlockItem(ModBlocks.ASH, new Item.Settings()));
	public static final Item SULFUR_CRUST = register("sulfur_crust", new BlockItem(ModBlocks.SULFUR_CRUST, new Item.Settings()));
	public static final Item SCRAP_HEAP = register("scrap_heap", new BlockItem(ModBlocks.SCRAP_HEAP, new Item.Settings()));
	public static final Item VENT = register("vent", new BlockItem(ModBlocks.VENT, new Item.Settings()));
	public static final Item GEYSER = register("geyser", new BlockItem(ModBlocks.GEYSER, new Item.Settings()));
	public static final Item GEOTHERMAL_TAP = register("geothermal_tap", new BlockItem(ModBlocks.GEOTHERMAL_TAP, new Item.Settings()));
	public static final Item RELAY_MAST = register("relay_mast", new BlockItem(ModBlocks.RELAY_MAST, new Item.Settings()));
	public static final Item DAMPER_BEACON = register("damper_beacon", new BlockItem(ModBlocks.DAMPER_BEACON, new Item.Settings()));
	public static final Item SPAN_ANCHOR = register("span_anchor", new BlockItem(ModBlocks.SPAN_ANCHOR, new Item.Settings()));
	/** A bridge in a box: one course of five-wide deck a use, out of the plating in your pockets. */
	public static final Item SPAN_KIT = register("span_kit", new dev.psyda.surrogate.item.SpanKitItem(new Item.Settings().maxCount(1)));

	// The fabricator, the props and the ores
	public static final Item VEHICLE_FABRICATOR = register("vehicle_fabricator", new BlockItem(ModBlocks.VEHICLE_FABRICATOR, new Item.Settings()));
	public static final Item SUPPLY_CRATE = register("supply_crate", new BlockItem(ModBlocks.SUPPLY_CRATE, new Item.Settings()));
	public static final Item LOCKER = register("locker", new BlockItem(ModBlocks.LOCKER, new Item.Settings()));
	public static final Item DATA_RACK = register("data_rack", new BlockItem(ModBlocks.DATA_RACK, new Item.Settings()));
	public static final Item DECK_GRATING = register("deck_grating", new BlockItem(ModBlocks.DECK_GRATING, new Item.Settings()));
	public static final Item HAZARD_PLATING = register("hazard_plating", new BlockItem(ModBlocks.HAZARD_PLATING, new Item.Settings()));
	public static final Item DECK_PLATING = register("deck_plating", new BlockItem(ModBlocks.DECK_PLATING, new Item.Settings()));
	public static final Item HULL_FRAME = register("hull_frame", new BlockItem(ModBlocks.HULL_FRAME, new Item.Settings()));
	public static final Item CEILING_LAMP = register("ceiling_lamp", new BlockItem(ModBlocks.CEILING_LAMP, new Item.Settings()));
	public static final Item PIPE = register("pipe", new BlockItem(ModBlocks.PIPE, new Item.Settings()));
	public static final Item HANDRAIL = register("handrail", new BlockItem(ModBlocks.HANDRAIL, new Item.Settings()));
	public static final Item CREW_SEAT = register("crew_seat", new BlockItem(ModBlocks.CREW_SEAT, new Item.Settings()));
	public static final Item MESS_TABLE = register("mess_table", new BlockItem(ModBlocks.MESS_TABLE, new Item.Settings()));
	public static final Item HYDROPONIC_TRAY = register("hydroponic_tray", new BlockItem(ModBlocks.HYDROPONIC_TRAY, new Item.Settings()));
	public static final Item MED_CABINET = register("med_cabinet", new BlockItem(ModBlocks.MED_CABINET, new Item.Settings()));
	public static final Item FIRE_EXTINGUISHER = register("fire_extinguisher", new BlockItem(ModBlocks.FIRE_EXTINGUISHER, new Item.Settings()));
	public static final Item WALL_VENT = register("wall_vent", new BlockItem(ModBlocks.WALL_VENT, new Item.Settings()));
	public static final Item COOLANT_TANK = register("coolant_tank", new BlockItem(ModBlocks.COOLANT_TANK, new Item.Settings()));
	public static final Item BUNK = register("bunk", new BlockItem(ModBlocks.BUNK, new Item.Settings()));
	public static final Item CHEM_DRUM = register("chem_drum", new BlockItem(ModBlocks.CHEM_DRUM, new Item.Settings()));
	public static final Item SURVEY_MARKER = register("survey_marker", new BlockItem(ModBlocks.SURVEY_MARKER, new Item.Settings()));

	// Earth, 2189: the flashback's furniture, carried home in a suitcase and craftable nowhere.
	public static final Item WALLPAPER_STRIPE = register("wallpaper_stripe", new BlockItem(ModBlocks.WALLPAPER_STRIPE, new Item.Settings()));
	public static final Item WALLPAPER_FLORAL = register("wallpaper_floral", new BlockItem(ModBlocks.WALLPAPER_FLORAL, new Item.Settings()));
	public static final Item RADIO_SET = register("radio_set", new BlockItem(ModBlocks.RADIO_SET, new Item.Settings()));
	public static final Item TELEVISION = register("television", new BlockItem(ModBlocks.TELEVISION, new Item.Settings()));
	// The house, the office and the bar (2026-09-07).
	public static final Item COUCH = register("couch", new BlockItem(ModBlocks.COUCH, new Item.Settings()));
	public static final Item COFFEE_TABLE = register("coffee_table", new BlockItem(ModBlocks.COFFEE_TABLE, new Item.Settings()));
	public static final Item DINING_TABLE = register("dining_table", new BlockItem(ModBlocks.DINING_TABLE, new Item.Settings()));
	public static final Item DINING_CHAIR = register("dining_chair", new BlockItem(ModBlocks.DINING_CHAIR, new Item.Settings()));
	public static final Item PIZZA_BOX = register("pizza_box", new BlockItem(ModBlocks.PIZZA_BOX, new Item.Settings()));
	public static final Item PIZZA_SLICE = register("pizza_slice", new dev.psyda.surrogate.item.PizzaSliceItem(new Item.Settings()));
	public static final Item PENDANT_LAMP = register("pendant_lamp", new BlockItem(ModBlocks.PENDANT_LAMP, new Item.Settings()));
	public static final Item PANEL_LIGHT = register("panel_light", new BlockItem(ModBlocks.PANEL_LIGHT, new Item.Settings()));
	public static final Item LIGHT_SWITCH = register("light_switch", new BlockItem(ModBlocks.LIGHT_SWITCH, new Item.Settings()));
	public static final Item BILLS = register("bills", new BlockItem(ModBlocks.BILLS, new Item.Settings()));
	public static final Item CALENDAR = register("calendar", new BlockItem(ModBlocks.CALENDAR, new Item.Settings()));
	public static final Item PRINTOUT = register("printout", new BlockItem(ModBlocks.PRINTOUT, new Item.Settings()));
	public static final Item DOG_CARRIER = register("dog_carrier", new dev.psyda.surrogate.item.DogCarrierItem(ModBlocks.DOG_CARRIER, new Item.Settings().maxCount(1)));
	public static final Item DOG_BOWL = register("dog_bowl", new BlockItem(ModBlocks.DOG_BOWL, new Item.Settings()));
	public static final Item FRIDGE = register("fridge", new BlockItem(ModBlocks.FRIDGE, new Item.Settings()));
	public static final Item KITCHEN_COUNTER = register("kitchen_counter", new BlockItem(ModBlocks.KITCHEN_COUNTER, new Item.Settings()));
	public static final Item KITCHEN_SINK = register("kitchen_sink", new BlockItem(ModBlocks.KITCHEN_SINK, new Item.Settings()));
	public static final Item STOVE = register("stove", new BlockItem(ModBlocks.STOVE, new Item.Settings()));
	public static final Item WARDROBE = register("wardrobe", new BlockItem(ModBlocks.WARDROBE, new Item.Settings()));
	public static final Item NIGHTSTAND = register("nightstand", new BlockItem(ModBlocks.NIGHTSTAND, new Item.Settings()));
	public static final Item TOILET = register("toilet", new BlockItem(ModBlocks.TOILET, new Item.Settings()));
	public static final Item WASHBASIN = register("washbasin", new BlockItem(ModBlocks.WASHBASIN, new Item.Settings()));
	public static final Item BATHTUB = register("bathtub", new BlockItem(ModBlocks.BATHTUB, new Item.Settings()));
	public static final Item MIRROR = register("mirror", new BlockItem(ModBlocks.MIRROR, new Item.Settings()));
	public static final Item SUITCASE = register("suitcase", new BlockItem(ModBlocks.SUITCASE, new Item.Settings().maxCount(1)));
	public static final Item OFFICE_DESK = register("office_desk", new BlockItem(ModBlocks.OFFICE_DESK, new Item.Settings()));
	public static final Item OFFICE_CHAIR = register("office_chair", new BlockItem(ModBlocks.OFFICE_CHAIR, new Item.Settings()));
	public static final Item MONITOR = register("monitor", new BlockItem(ModBlocks.MONITOR, new Item.Settings()));
	public static final Item FILING_CABINET = register("filing_cabinet", new BlockItem(ModBlocks.FILING_CABINET, new Item.Settings()));
	public static final Item WATER_COOLER = register("water_cooler", new BlockItem(ModBlocks.WATER_COOLER, new Item.Settings()));
	public static final Item WHITEBOARD = register("whiteboard", new BlockItem(ModBlocks.WHITEBOARD, new Item.Settings()));
	public static final Item PRINTER = register("printer", new BlockItem(ModBlocks.PRINTER, new Item.Settings()));
	public static final Item CARDBOARD_BOX = register("cardboard_box", new BlockItem(ModBlocks.CARDBOARD_BOX, new Item.Settings().maxCount(1)));
	public static final Item BAR_COUNTER = register("bar_counter", new BlockItem(ModBlocks.BAR_COUNTER, new Item.Settings()));
	public static final Item BAR_STOOL = register("bar_stool", new BlockItem(ModBlocks.BAR_STOOL, new Item.Settings()));
	public static final Item PINT_GLASS = register("pint_glass", new BlockItem(ModBlocks.PINT_GLASS, new Item.Settings()));
	public static final Item DARTBOARD = register("dartboard", new BlockItem(ModBlocks.DARTBOARD, new Item.Settings()));
	public static final Item DART = register("dart", new dev.psyda.surrogate.item.DartItem(new Item.Settings().maxCount(16)));
	public static final Item BACK_BAR = register("back_bar", new BlockItem(ModBlocks.BACK_BAR, new Item.Settings()));
	public static final Item BEER_TAP = register("beer_tap", new BlockItem(ModBlocks.BEER_TAP, new Item.Settings()));
	public static final Item NEON_SIGN = register("neon_sign", new BlockItem(ModBlocks.NEON_SIGN, new Item.Settings()));
	public static final Item DESK_LAMP = register("desk_lamp", new BlockItem(ModBlocks.DESK_LAMP, new Item.Settings()));
	public static final Item PHOTO_FRAME = register("photo_frame", new BlockItem(ModBlocks.PHOTO_FRAME, new Item.Settings()));
	public static final Item WALL_CLOCK = register("wall_clock", new BlockItem(ModBlocks.WALL_CLOCK, new Item.Settings()));
	public static final Item SNOW_GLOBE = register("snow_globe", new BlockItem(ModBlocks.SNOW_GLOBE, new Item.Settings()));
	public static final Item MUG = register("mug", new BlockItem(ModBlocks.MUG, new Item.Settings()));
	public static final Item HOUSEPLANT = register("houseplant", new BlockItem(ModBlocks.HOUSEPLANT, new Item.Settings()));
	public static final Item TELEPHONE = register("telephone", new BlockItem(ModBlocks.TELEPHONE, new Item.Settings()));
	public static final Item SURVEY_STATION = register("survey_station", new BlockItem(ModBlocks.SURVEY_STATION, new Item.Settings()));
	public static final Item SURVEY_BEACON = register("survey_beacon", new BlockItem(ModBlocks.SURVEY_BEACON, new Item.Settings()));
	public static final Item LONG_RANGE_SCANNER = register("long_range_scanner", new BlockItem(ModBlocks.LONG_RANGE_SCANNER, new Item.Settings()));
	public static final Item PAD_LIGHT = register("pad_light", new BlockItem(ModBlocks.PAD_LIGHT, new Item.Settings()));
	public static final Item ANTENNA_MAST = register("antenna_mast", new BlockItem(ModBlocks.ANTENNA_MAST, new Item.Settings()));
	public static final Item SURVEY_STAKE = register("survey_stake", new BlockItem(ModBlocks.SURVEY_STAKE, new Item.Settings()));
	public static final Item CINNABAR_ORE = register("cinnabar_ore", new BlockItem(ModBlocks.CINNABAR_ORE, new Item.Settings()));
	public static final Item HALITE_ORE = register("halite_ore", new BlockItem(ModBlocks.HALITE_ORE, new Item.Settings()));
	public static final Item COBALT_ORE = register("cobalt_ore", new BlockItem(ModBlocks.COBALT_ORE, new Item.Settings()));
	public static final Item DEEPSLATE_COBALT_ORE = register("deepslate_cobalt_ore", new BlockItem(ModBlocks.DEEPSLATE_COBALT_ORE, new Item.Settings()));
	public static final Item TELLURIUM_ORE = register("tellurium_ore", new BlockItem(ModBlocks.TELLURIUM_ORE, new Item.Settings()));

	// What the ores give up
	public static final Item CINNABAR = register("cinnabar", new Item(new Item.Settings()));
	public static final Item SALT = register("salt", new Item(new Item.Settings()));
	public static final Item RAW_COBALT = register("raw_cobalt", new Item(new Item.Settings()));
	public static final Item COBALT_INGOT = register("cobalt_ingot", new Item(new Item.Settings()));
	public static final Item TELLURIUM_CRYSTAL = register("tellurium_crystal", new Item(new Item.Settings()));

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Surrogate.id(name), item);
	}

	public static void register() {
	}

	private ModItems() {
	}
}
