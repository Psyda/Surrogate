package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.ChargingDockBlockEntity;
import dev.psyda.surrogate.block.ConduitBlockEntity;
import dev.psyda.surrogate.block.DeconShowerBlockEntity;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.block.MicrowaveBlockEntity;
import dev.psyda.surrogate.block.TerminalBlockEntity;
import dev.psyda.surrogate.block.SolarCollectorBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import team.reborn.energy.api.EnergyStorage;

public final class ModBlockEntities {
	public static final BlockEntityType<DiveChairBlockEntity> DIVE_CHAIR = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("dive_chair"),
			BlockEntityType.Builder.create(DiveChairBlockEntity::new, ModBlocks.DIVE_CHAIR).build(null));

	public static final BlockEntityType<ChargingDockBlockEntity> CHARGING_DOCK = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("charging_dock"),
			BlockEntityType.Builder.create(ChargingDockBlockEntity::new, ModBlocks.CHARGING_DOCK).build(null));

	public static final BlockEntityType<LifeSupportBlockEntity> LIFE_SUPPORT = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("life_support"),
			BlockEntityType.Builder.create(LifeSupportBlockEntity::new, ModBlocks.LIFE_SUPPORT).build(null));

	public static final BlockEntityType<SolarCollectorBlockEntity> SOLAR_COLLECTOR = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("solar_collector"),
			BlockEntityType.Builder.create(SolarCollectorBlockEntity::new, ModBlocks.SOLAR_COLLECTOR).build(null));

	public static final BlockEntityType<ConduitBlockEntity> POWER_CONDUIT = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("power_conduit"),
			BlockEntityType.Builder.create(ConduitBlockEntity::new, ModBlocks.POWER_CONDUIT).build(null));

	public static final BlockEntityType<DeconShowerBlockEntity> DECON_SHOWER = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("decon_shower"),
			BlockEntityType.Builder.create(DeconShowerBlockEntity::new, ModBlocks.DECON_SHOWER).build(null));

	public static final BlockEntityType<MicrowaveBlockEntity> MICROWAVE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("microwave"),
			BlockEntityType.Builder.create(MicrowaveBlockEntity::new, ModBlocks.MICROWAVE).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.CrawlerBayBlockEntity> CRAWLER_BAY = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("crawler_bay"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.CrawlerBayBlockEntity::new, ModBlocks.CRAWLER_BAY).build(null));

	public static final BlockEntityType<TerminalBlockEntity> TERMINAL = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("terminal"),
			BlockEntityType.Builder.create(TerminalBlockEntity::new, ModBlocks.TERMINAL).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.VehicleFabricatorBlockEntity> VEHICLE_FABRICATOR = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("vehicle_fabricator"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.VehicleFabricatorBlockEntity::new, ModBlocks.VEHICLE_FABRICATOR).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.GeyserBlockEntity> GEYSER = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("geyser"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.GeyserBlockEntity::new, ModBlocks.GEYSER).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.GeothermalTapBlockEntity> GEOTHERMAL_TAP = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("geothermal_tap"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.GeothermalTapBlockEntity::new, ModBlocks.GEOTHERMAL_TAP).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.RelayMastBlockEntity> RELAY_MAST = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("relay_mast"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.RelayMastBlockEntity::new, ModBlocks.RELAY_MAST).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.SurveyStationBlockEntity> SURVEY_STATION = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("survey_station"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.SurveyStationBlockEntity::new, ModBlocks.SURVEY_STATION).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.SurveyBeaconBlockEntity> SURVEY_BEACON = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("survey_beacon"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.SurveyBeaconBlockEntity::new, ModBlocks.SURVEY_BEACON).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.LongRangeScannerBlockEntity> LONG_RANGE_SCANNER = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("long_range_scanner"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.LongRangeScannerBlockEntity::new, ModBlocks.LONG_RANGE_SCANNER).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.DamperBeaconBlockEntity> DAMPER_BEACON = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("damper_beacon"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.DamperBeaconBlockEntity::new, ModBlocks.DAMPER_BEACON).build(null));

	public static final BlockEntityType<dev.psyda.surrogate.block.CorrodedMachineBlockEntity> CORRODED_MACHINE = Registry.register(Registries.BLOCK_ENTITY_TYPE, Surrogate.id("corroded_machine"),
			BlockEntityType.Builder.create(dev.psyda.surrogate.block.CorrodedMachineBlockEntity::new, ModBlocks.CORRODED_MACHINE).build(null));

	public static void register() {
		// Any Team Reborn Energy producer or cable can push power into a dock or life support unit from any side.
		EnergyStorage.SIDED.registerForBlockEntity((dock, direction) -> dock.getEnergyStorage(), CHARGING_DOCK);
		EnergyStorage.SIDED.registerForBlockEntity((unit, direction) -> unit.getEnergyStorage(), LIFE_SUPPORT);
		EnergyStorage.SIDED.registerForBlockEntity((conduit, direction) -> conduit.getEnergyStorage(), POWER_CONDUIT);
		// Solar collectors only give: cables can pull from them, nothing can push in.
		EnergyStorage.SIDED.registerForBlockEntity((solar, direction) -> solar.getEnergyStorage(), SOLAR_COLLECTOR);
		// So does a geothermal tap, and it does not care what the sky is doing.
		EnergyStorage.SIDED.registerForBlockEntity((tap, direction) -> tap.getEnergyStorage(), GEOTHERMAL_TAP);
		// The two countermeasures only take: they burn a trickle and give nothing back.
		EnergyStorage.SIDED.registerForBlockEntity((mast, direction) -> mast.getEnergyStorage(), RELAY_MAST);
		// The pillar is the only survey block with a hole in it: the table and the beacons run on nothing.
		EnergyStorage.SIDED.registerForBlockEntity((scanner, direction) -> scanner.getEnergyStorage(), LONG_RANGE_SCANNER);
		EnergyStorage.SIDED.registerForBlockEntity((beacon, direction) -> beacon.getEnergyStorage(), DAMPER_BEACON);
	}

	private ModBlockEntities() {
	}
}
