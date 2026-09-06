package dev.psyda.surrogate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tunables for the mod, written to config/surrogate.json on first launch.
 * All energy values share one unit, which is also what the Team Reborn Energy API sees (1:1).
 */
public class SurrogateConfig {
	// Chassis
	public int robotBaseHealth = 10;
	public int platingMk1Health = 20;
	public int platingMk2Health = 40;
	public double robotSpeed = 0.11;
	public double sprintMultiplier = 1.5;
	public double sneakMultiplier = 0.45;
	public double fallDamageMultiplier = 0.5;

	// Power
	public int robotBaseCapacity = 36000;
	public int batteryTierMultiplier = 2;
	public int idleDrainPerTick = 1;
	public int sprintDrainPerTick = 3;
	public int bootCost = 1200;
	public int bootTicks = 200;
	public int shutdownTicks = 100;
	public int newChassisEnergy = 7200;
	public int powerCellEnergy = 6000;

	// Repair
	public int repairKitHealth = 4;

	// Dock
	public int dockEnergyCapacity = 100000;
	public int dockMaxInsertPerTick = 1000;
	public int dockChargePerTick = 60;

	// Pilot body
	public int maxFatigueTicks = 60000;
	public int unconsciousTicks = 600;
	public double wakeFatigueFraction = 0.7;
	public float pilotExhaustionPerTick = 0.002f;
	public int linkSearchTicks = 80;

	// Atmosphere (only matters in biomes tagged surrogate:toxic, i.e. the Toxic Wastes world type)
	public boolean toxicAtmosphere = true;
	/** Off: no air, toxin or exposure anywhere (the same as toxicAtmosphere, named for what it does). */
	public boolean surfaceEffects = true;
	public int maxSealedVolume = 8000;
	public int lifeSupportScanInterval = 20;
	public int lifeSupportEnergyCapacity = 60000;
	public int lifeSupportMaxInsertPerTick = 1000;
	public int lifeSupportDrainPerTick = 2;
	public double airRecoverySeconds = 30;
	public double airLeakSeconds = 15;
	public double airStaleSeconds = 180;
	public double exposureSeconds = 10;
	public double toxinRecoverySeconds = 120;
	public double contaminationSeconds = 15;
	public double contaminationDrainPerSecond = 0.04;
	public double causticItemDrainPerSecond = 0.002;
	public double causticBlockDrainPerSecond = 0.001;
	public int deconTicks = 60;
	public int airlockCloseTicks = 60;
	public int solarOutputPerTick = 6;
	public int solarBufferCapacity = 4000;
	public boolean spawnStarterHabitat = true;

	// Robot tools
	public int drillEnergyPerBlock = 30;
	public int cutterEnergyPerHit = 60;

	// Cargo
	public int cargoBaseSlots = 18;
	public int cargoSlotsPerBay = 9;
	public int cargoBayMaxTier = 2;

	// Sleep: a nap advances the clock by napMinTicks plus napFatigueTicks scaled by how tired you are
	public int napMinTicks = 2000;
	public int napFatigueTicks = 8000;

	// Survivors
	public int survivorCount = 6;
	public int survivorMinDistance = 300;
	public int survivorMaxDistance = 900;
	/** The first shelter (the researcher with the crawler blueprint) sits within a chassis walk of the pod. */
	public int firstSurvivorMinDistance = 160;
	public int firstSurvivorMaxDistance = 280;
	/** Tanaka, past the mast's reach: a carrier and no voice until a relay module or a mast covers him. */
	public int outOfRangeSurvivorMinDistance = 1100;
	public int outOfRangeSurvivorMaxDistance = 1400;
	/** Brandt, Reyes and Novak: the far side of the Rift, off the floor the pod's crawler can reach. */
	public int farSurvivorMinDistance = 1500;
	public int farSurvivorMaxDistance = 2400;
	/** What every shelter's scrubber loses a day once the ship has gone, and how low it is allowed to go. */
	public double shelterDeclinePerDay = 0.01;
	public double shelterDeclineFloor = 0.3;

	// Crawler: the slow, heavy mobile base (docs/DESIGN-crawler.md). Speed in blocks per tick, under walking pace.
	public double crawlerSpeed = 0.14;
	public double crawlerReverseFactor = 0.5;
	public double crawlerTurnDegreesPerTick = 1.2;
	public int crawlerEnergyCapacity = 400000;
	public int crawlerDrivePerTick = 12;
	public int crawlerIdleDrainPerTick = 0;
	public int crawlerSolarPerTick = 2;
	/** Paint the ground ahead of the hull into the cabin porthole (thousands of block updates a second; off by default). */
	public boolean crawlerPorthole = false;
	public int radioIntervalTicks = 2400;

	// Prologue: the opening sequence the first player of a fresh Toxic Wastes world is walked through.
	// Objectives that the player does not finish in time are done for them by the crew after this long.
	public boolean prologue = true;
	/** Acts one and two: the neighbours' three research runs and the drive out to Tanaka, before the contract. */
	public boolean research = true;
	/** Contract Seven: the corporation's research task, after the opening days. */
	public boolean assay = true;
	public int prologueObjectiveTimeoutTicks = 1500;
	/** Reading speed used to time subtitles, in characters per second. */
	public double prologueReadSpeed = 18.0;

	// Transit: the week aboard the Provender before the landing. Off, and the story starts in the pod.
	public boolean transit = true;
	/** How long one ship day runs in real ticks while the player is awake; sleeping ends it early. */
	public int transitDayTicks = 9600;
	/** How long the crew wait for a ship objective before doing it for the player. */
	public int transitObjectiveTimeoutTicks = 2400;
	/** How long the player may stay up after being told to rest before the doctor ends the day for them. */
	public int transitRestTimeoutTicks = 12000;

	// Hazards: the four things on Sallow (docs/DESIGN-hazards.md). Every one of them is also a gate on the
	// campaign, so these numbers decide how long each act's wall stands up.
	/**
	 * Vanilla hostiles. Off: the biomes carry no spawner lists and the server discards any that arrive
	 * anyway. On: the overworld's own lists are added back to Sallow's biomes as the world loads and the
	 * sweep stands down. Biomes load once, so a change to this lands on the next world load.
	 */
	public boolean vanillaMonsters = false;
	/** The whole hazard layer: borers, storms, geysers and the belt's rain. */
	public boolean hazards = true;

	// Borers: they hunt vibration through the rock and never come above the line.
	public int borerDepthY = 8;
	/** Disturbance one broken block below the line is worth, and what it takes to wake one. */
	public double borerDisturbancePerBlock = 12.0;
	public double borerWakeThreshold = 240.0;
	public double borerDecayPerSecond = 3.0;
	/** What the resonance damper multiplies your own disturbance by. */
	public double borerDamperFactor = 0.25;
	public int borerMaxPerPlayer = 3;
	public double borerSpeed = 0.09;
	/** Damage a borer does to the rock it passes through, per tick, against a block's own hardness. */
	public double borerChewPerTick = 0.55;
	public int borerLungeDamage = 12;
	/** A damper beacon does for a fixed site what the module does for a chassis, out to here, while it has power. */
	public int damperBeaconRange = 48;
	public int damperBeaconDrainPerTick = 6;

	// Magnetic storms: they take the link, the radio, the radar and the sun.
	public int stormIntervalTicks = 36000;
	public int stormIntervalJitterTicks = 24000;
	public int stormWarningTicks = 1800;
	public int stormPeakTicks = 18000;
	public int stormTailTicks = 6000;
	/** Intensity at which an unshielded link outdoors drops. */
	public double stormLinkDropAt = 0.75;
	/** What the shielded uplink multiplies the storm's effect on the link by. */
	public double stormShieldFactor = 0.5;
	public double stormSolarFactor = 0.05;
	public int stormChassisDamage = 1;

	// Geysers: ninety seconds of clock, eight of column.
	public int geyserQuietTicks = 1400;
	public int geyserSteamTicks = 240;
	public int geyserRumbleTicks = 80;
	public int geyserEruptTicks = 160;
	public int geyserHeight = 10;
	public float geyserDamage = 6.0f;
	public int geothermalTapPerTick = 400;
	/** What the tap holds when nothing is drawing on it. A hundred seconds of its own output. */
	public int geothermalTapCapacity = 40000;

	// The belt: a region, not a weather state, with its own clock.
	public int acidRainIntervalTicks = 9000;
	public int acidRainJitterTicks = 9000;
	public int acidRainLengthTicks = 6000;
	/** Corrosion a machine picks up per second in the open, and how much of it a machine survives. */
	public double corrosionPerSecond = 0.6;
	public double corrosionLimit = 100.0;
	public float acidChassisDamagePerSecond = 0.5f;
	public int acidCrawlerDrainMultiplier = 4;
	/** What acid coating multiplies the belt's bite on a chassis by, and ceramic cladding on the crawler. */
	public double acidCoatingFactor = 0.25;
	public double acidCladdingFactor = 0.2;

	// The map. Sallow comes out of the seed, and one seed was searched for and chosen so the campaign could
	// be designed against a map that does not move (docs/DESIGN-campaign.md, "The map"). The new-world screen
	// offers it for a Toxic Wastes world unless the player types their own; blank offers nothing.
	// 3878 won a sweep of four thousand: 98.9 of 100, every site placed, a drive with some character to each,
	// seventeen thousand cells of floor behind the Rift with a twenty-five block crossing into it, and
	// eighty-five percent of that far side in the belt.
	public String lockedSeed = "3878";

	// Range: what a chassis can hear and how far a pilot can be from their body.
	public int radioRange = 700;
	public int relayModuleRangeBonus = 700;
	public int relayMastRange = 400;
	/** What a mast costs to keep the band up. Its buffer holds a minute of it, so a cloudy hour is not silence. */
	public int relayMastDrainPerTick = 2;

	public int cargoSlots(int tier) {
		return Math.min(36, cargoBaseSlots + cargoSlotsPerBay * Math.max(0, tier));
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static SurrogateConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(Surrogate.MOD_ID + ".json");
		SurrogateConfig config = new SurrogateConfig();
		if (Files.exists(path)) {
			try {
				config = GSON.fromJson(Files.readString(path), SurrogateConfig.class);
				if (config == null) config = new SurrogateConfig();
			} catch (IOException | RuntimeException e) {
				Surrogate.LOGGER.error("Could not read {}; using defaults", path, e);
				config = new SurrogateConfig();
			}
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(config));
		} catch (IOException e) {
			Surrogate.LOGGER.error("Could not write {}", path, e);
		}
		return config;
	}

	public int capacityForBatteryTier(int tier) {
		int capacity = robotBaseCapacity;
		for (int i = 0; i < tier; i++) capacity *= batteryTierMultiplier;
		return capacity;
	}

	public float maxHealthForPlatingTier(int tier) {
		return switch (tier) {
			case 0 -> robotBaseHealth;
			case 1 -> platingMk1Health;
			default -> platingMk2Health;
		};
	}
}
