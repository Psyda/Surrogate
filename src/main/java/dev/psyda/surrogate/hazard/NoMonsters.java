package dev.psyda.surrogate.hazard;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.crawler.CrawlerDimension;
import dev.psyda.surrogate.transit.TransitDimension;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.SpawnSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Nothing lives on Sallow. The biomes carry no spawner lists, so the sweep here only ever catches what
 * arrives by some other route: a spawn egg, a dungeon left under the floor, another mod. It never touches a
 * world that is not one of the mod's own.
 *
 * <p>{@code vanillaMonsters} is both halves of one switch. It turns the sweep off, and it puts the overworld's
 * own spawner lists back into Sallow's biomes as they load, because empty lists with the sweep off would only
 * have made the wastes quietly empty. Biomes are loaded per world, well after the config is read, so the flag
 * is a real switch: change it and the next world load spawns what any other overworld would.
 */
public final class NoMonsters {
	// Sending an entity away inside the load event would take it out from under the code that is still
	// putting it in, so the discard waits for the next tick the way every other arrival in this mod does.
	private static final List<Entity> PENDING = new ArrayList<>();

	private NoMonsters() {
	}

	public static void registerEvents() {
		ServerEntityEvents.ENTITY_LOAD.register(NoMonsters::onLoad);
		ServerTickEvents.END_SERVER_TICK.register(NoMonsters::tick);
		BiomeModifications.create(Surrogate.id("vanilla_monsters"))
				.add(ModificationPhase.ADDITIONS, NoMonsters::wanted, NoMonsters::restore);
	}

	/** Sallow's own biomes, and only for a world whose owner has asked for the mobs back. */
	private static boolean wanted(BiomeSelectionContext context) {
		return Surrogate.CONFIG.vanillaMonsters
				&& context.getBiomeKey().getValue().getNamespace().equals(Surrogate.MOD_ID);
	}

	/**
	 * The overworld's own list, weights and group sizes and all, so a world with the flag on spawns what any
	 * other overworld spawns. Bats go back with them: the biomes lose the ambient list as well.
	 */
	private static void restore(BiomeModificationContext context) {
		BiomeModificationContext.SpawnSettingsContext spawns = context.getSpawnSettings();
		spawns.addSpawn(SpawnGroup.AMBIENT, new SpawnSettings.SpawnEntry(EntityType.BAT, 10, 8, 8));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.SPIDER, 100, 4, 4));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE, 95, 2, 4));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE_VILLAGER, 5, 1, 1));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 100, 4, 4));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.CREEPER, 100, 4, 4));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.SLIME, 100, 4, 4));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.ENDERMAN, 10, 1, 4));
		spawns.addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.WITCH, 5, 1, 1));
	}

	private static void onLoad(Entity entity, ServerWorld world) {
		if (!(entity instanceof HostileEntity)) return;
		if (Surrogate.CONFIG.vanillaMonsters) return;
		if (!ours(world)) return;
		PENDING.add(entity);
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		for (Entity entity : PENDING) {
			if (entity.isAlive()) entity.discard();
		}
		PENDING.clear();
	}

	/** The wastes, the cabins and the ship. Anywhere else is somebody else's world. */
	private static boolean ours(ServerWorld world) {
		if (CrawlerDimension.isCabin(world) || TransitDimension.isTransit(world)) return true;
		return world.getRegistryKey() == World.OVERWORLD && Valleys.isMesaWorld(world);
	}
}
