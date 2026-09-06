package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CameraEntity;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotWreckEntity;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.survivor.SurvivorEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<RobotEntity> ROBOT = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("robot"),
			EntityType.Builder.create(RobotEntity::new, SpawnGroup.MISC)
					.dimensions(0.7f, 0.95f)
					.eyeHeight(0.8f)
					.maxTrackingRange(10)
					.build("robot"));

	public static final EntityType<RobotWreckEntity> ROBOT_WRECK = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("robot_wreck"),
			EntityType.Builder.create(RobotWreckEntity::new, SpawnGroup.MISC)
					.dimensions(0.9f, 0.5f)
					.maxTrackingRange(10)
					.build("robot_wreck"));

	public static final EntityType<SurvivorEntity> SURVIVOR = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("survivor"),
			EntityType.Builder.create(SurvivorEntity::new, SpawnGroup.MISC)
					.dimensions(0.6f, 1.8f)
					.eyeHeight(1.62f)
					.maxTrackingRange(10)
					.build("survivor"));

	public static final EntityType<CrewEntity> CREW = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("crew"),
			EntityType.Builder.create(CrewEntity::new, SpawnGroup.MISC)
					.dimensions(0.6f, 1.8f)
					.eyeHeight(1.62f)
					.maxTrackingRange(10)
					.build("crew"));

	/** The crawler's hull: the outside of the slow, heavy mobile base. */
	public static final EntityType<CrawlerEntity> CRAWLER = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("crawler"),
			EntityType.Builder.create(CrawlerEntity::new, SpawnGroup.MISC)
					.dimensions(CrawlerEntity.WIDTH, CrawlerEntity.HEIGHT)
					.eyeHeight(2.5f)
					.maxTrackingRange(12)
					.trackingTickInterval(2)
					.build("crawler"));

	/** The sample vehicle the gantry builds for Contract Seven. Static until it is lit, then it climbs away. */
	public static final EntityType<dev.psyda.surrogate.entity.RocketEntity> ROCKET = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("rocket"),
			EntityType.Builder.<dev.psyda.surrogate.entity.RocketEntity>create(dev.psyda.surrogate.entity.RocketEntity::new, SpawnGroup.MISC)
					.dimensions(dev.psyda.surrogate.entity.RocketEntity.WIDTH, dev.psyda.surrogate.entity.RocketEntity.HEIGHT)
					.maxTrackingRange(24)
					.build("rocket"));

	/**
	 * The company ship. Tracked far further than anything else in the mod, because the point of it is that it
	 * is seen coming and seen leaving from most of a valley away.
	 */
	public static final EntityType<dev.psyda.surrogate.entity.CompanyShipEntity> COMPANY_SHIP = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("company_ship"),
			EntityType.Builder.<dev.psyda.surrogate.entity.CompanyShipEntity>create(dev.psyda.surrogate.entity.CompanyShipEntity::new, SpawnGroup.MISC)
					.dimensions(dev.psyda.surrogate.entity.CompanyShipEntity.WIDTH, dev.psyda.surrogate.entity.CompanyShipEntity.HEIGHT)
					.maxTrackingRange(64)
					.trackingTickInterval(1)
					.build("company_ship"));

	/**
	 * What is under the rock. Nothing in the world spawns one: {@link dev.psyda.surrogate.hazard.Borers}
	 * decides when one wakes. Tracked every tick because the body is drawn off its position history, and a
	 * history sampled twice a second is a train rather than an animal.
	 */
	public static final EntityType<dev.psyda.surrogate.entity.BorerEntity> BORER = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("borer"),
			EntityType.Builder.<dev.psyda.surrogate.entity.BorerEntity>create(dev.psyda.surrogate.entity.BorerEntity::new, SpawnGroup.MISC)
					.dimensions(dev.psyda.surrogate.entity.BorerEntity.WIDTH, dev.psyda.surrogate.entity.BorerEntity.HEIGHT)
					.maxTrackingRange(8)
					.trackingTickInterval(1)
					.build("borer"));

	/** Client-side cinematic camera. Never spawned in a world, never saved. */
	public static final EntityType<CameraEntity> CAMERA = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("camera"),
			EntityType.Builder.create(CameraEntity::new, SpawnGroup.MISC)
					.dimensions(0.1f, 0.1f)
					.eyeHeight(0f)
					.disableSaving()
					.disableSummon()
					.build("camera"));

	public static void register() {
		FabricDefaultAttributeRegistry.register(ROBOT, RobotEntity.createRobotAttributes());
		FabricDefaultAttributeRegistry.register(SURVIVOR, SurvivorEntity.createSurvivorAttributes());
		FabricDefaultAttributeRegistry.register(CREW, CrewEntity.createCrewAttributes());
	}

	private ModEntities() {
	}
}
