package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroup {
	public static final ItemGroup GROUP = FabricItemGroup.builder()
			.icon(() -> new ItemStack(ModItems.ROBOT_CHASSIS))
			.displayName(Text.translatable("itemGroup.surrogate"))
			.entries((context, entries) -> {
				entries.add(ModItems.ROBOT_CHASSIS);
				entries.add(ModItems.DIVE_CHAIR);
				entries.add(ModItems.CHARGING_DOCK);
				entries.add(ModItems.UPLINK_CARD);
				entries.add(ModItems.WRENCH);
				entries.add(ModItems.REPAIR_KIT);
				entries.add(ModItems.POWER_CELL);
				entries.add(ModItems.REBREATHER);
				entries.add(ModItems.MINING_DRILL);
				entries.add(ModItems.ARC_CUTTER);
				entries.add(ModItems.ATMO_SCANNER);
				entries.add(ModItems.FIELD_RADIO);
				entries.add(ModItems.TOMATO_SEEDS);
				entries.add(ModItems.PLATING_MK1);
				entries.add(ModItems.PLATING_MK2);
				entries.add(ModItems.BATTERY_UPGRADE);
				entries.add(ModItems.CARGO_BAY);
				entries.add(ModItems.FABRICATOR);
				entries.add(ModItems.ROBOT_CORE);
				entries.add(ModItems.SERVO_MOTOR);
				entries.add(ModItems.SCRAP_CHASSIS);
				entries.add(ModItems.LIFE_SUPPORT);
				entries.add(ModItems.SOLAR_COLLECTOR);
				entries.add(ModItems.POWER_CONDUIT);
				entries.add(ModItems.DECON_SHOWER);
				entries.add(ModItems.AIRLOCK_DOOR);
				entries.add(ModItems.CRAWLER_KIT);
				entries.add(ModItems.CRAWLER_BLUEPRINT);
				entries.add(ModItems.ROCKET_KIT);
				entries.add(ModItems.CHASSIS_PORT);
				entries.add(ModItems.HULL_PLATING);
				entries.add(ModItems.REINFORCED_GLASS);
				entries.add(ModItems.POSTER);
				entries.add(ModItems.MICROWAVE);
				entries.add(ModItems.TERMINAL);
				entries.add(ModItems.SULFUR);
				entries.add(ModItems.CAUSTIC_SAND);
				entries.add(ModItems.CAUSTIC_SANDSTONE);
				entries.add(ModItems.ASH);
				entries.add(ModItems.SULFUR_CRUST);
				entries.add(ModItems.SCRAP_HEAP);
				entries.add(ModItems.VENT);
				entries.add(ModItems.VEHICLE_FABRICATOR);
				entries.add(ModItems.SUPPLY_CRATE);
				entries.add(ModItems.LOCKER);
				entries.add(ModItems.DATA_RACK);
				entries.add(ModItems.DECK_GRATING);
				entries.add(ModItems.HAZARD_PLATING);
				entries.add(ModItems.DECK_PLATING);
				entries.add(ModItems.HULL_FRAME);
				entries.add(ModItems.CEILING_LAMP);
				entries.add(ModItems.PIPE);
				entries.add(ModItems.HANDRAIL);
				entries.add(ModItems.CREW_SEAT);
				entries.add(ModItems.MESS_TABLE);
				entries.add(ModItems.HYDROPONIC_TRAY);
				entries.add(ModItems.MED_CABINET);
				entries.add(ModItems.FIRE_EXTINGUISHER);
				entries.add(ModItems.WALL_VENT);
				entries.add(ModItems.COOLANT_TANK);
				entries.add(ModItems.BUNK);
				entries.add(ModItems.CHEM_DRUM);
				entries.add(ModItems.SURVEY_MARKER);
				entries.add(ModItems.PAD_LIGHT);
				entries.add(ModItems.ANTENNA_MAST);
				entries.add(ModItems.CINNABAR_ORE);
				entries.add(ModItems.HALITE_ORE);
				entries.add(ModItems.COBALT_ORE);
				entries.add(ModItems.DEEPSLATE_COBALT_ORE);
				entries.add(ModItems.TELLURIUM_ORE);
				entries.add(ModItems.CINNABAR);
				entries.add(ModItems.SALT);
				entries.add(ModItems.RAW_COBALT);
				entries.add(ModItems.COBALT_INGOT);
				entries.add(ModItems.TELLURIUM_CRYSTAL);
			})
			.build();

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, Surrogate.id("main"), GROUP);
	}

	private ModItemGroup() {
	}
}
