package dev.psyda.surrogate;

import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.hazard.Hazards;
import dev.psyda.surrogate.hazard.NoMonsters;
import dev.psyda.surrogate.network.ModNetworking;
import dev.psyda.surrogate.registry.ModGameRules;
import dev.psyda.surrogate.world.LightRefresh;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.prologue.PrologueCommand;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModEffects;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItemGroup;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModRecipes;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.prologue.SiteTwo;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.transit.Transit;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Surrogate: pilot remote robot chassis from a dive chair while your body stays safe at base.
 */
public class Surrogate implements ModInitializer {
	public static final String MOD_ID = "surrogate";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static SurrogateConfig CONFIG = new SurrogateConfig();

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		CONFIG = SurrogateConfig.load();
		ModGameRules.register();
		ModComponents.register();
		ModEffects.register();
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		ModBlockEntities.register();
		ModRecipes.register();
		ModItemGroup.register();
		ModSounds.register();
		ModNetworking.register();
		PilotManager.registerEvents();
		SurvivorManager.registerEvents();
		Prologue.registerEvents();
		SiteTwo.registerEvents();
		dev.psyda.surrogate.assay.Assay.registerEvents();
		dev.psyda.surrogate.assay.PadSite.registerEvents();
		dev.psyda.surrogate.hazard.Borers.registerEvents();
		dev.psyda.surrogate.research.Research.registerEvents();
		Hazards.registerEvents();
		dev.psyda.surrogate.hazard.AcidRain.registerEvents();
		NoMonsters.registerEvents();
		Transit.registerEvents();
		CrawlerInterior.registerEvents();
		LightRefresh.registerEvents();
		PrologueCommand.register();
		LOGGER.info("Surrogate loaded: robots online, bodies stay home.");
	}
}
