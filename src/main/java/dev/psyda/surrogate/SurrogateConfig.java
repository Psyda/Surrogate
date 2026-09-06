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
	public int survivorCount = 4;
	public int survivorMinDistance = 300;
	public int survivorMaxDistance = 900;
	/** The first shelter (the researcher with the crawler blueprint) sits within a chassis walk of the pod. */
	public int firstSurvivorMinDistance = 160;
	public int firstSurvivorMaxDistance = 280;

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
