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

	/**
	 * The slow one. {@link SpawnGroup#CREATURE} for the sampler's sake; it is not on any biome list. Every
	 * animal here is placed by {@link dev.psyda.surrogate.fauna.Fauna}, which keeps the count.
	 */
	public static final EntityType<dev.psyda.surrogate.fauna.TrundleEntity> TRUNDLE = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("trundle"),
			EntityType.Builder.<dev.psyda.surrogate.fauna.TrundleEntity>create(dev.psyda.surrogate.fauna.TrundleEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.8f, 0.8f)
					.eyeHeight(0.5f)
					.maxTrackingRange(8)
					.build("trundle"));

	/** The rock that is not a rock. Placed by {@link dev.psyda.surrogate.fauna.Fauna}, near geysers. */
	public static final EntityType<dev.psyda.surrogate.fauna.SlagbackEntity> SLAGBACK = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("slagback"),
			EntityType.Builder.<dev.psyda.surrogate.fauna.SlagbackEntity>create(dev.psyda.surrogate.fauna.SlagbackEntity::new, SpawnGroup.CREATURE)
					.dimensions(1.0f, 0.7f)
					.eyeHeight(0.5f)
					.maxTrackingRange(8)
					.build("slagback"));

	/** The one that answers. */
	public static final EntityType<dev.psyda.surrogate.fauna.TockerEntity> TOCKER = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("tocker"),
			EntityType.Builder.<dev.psyda.surrogate.fauna.TockerEntity>create(dev.psyda.surrogate.fauna.TockerEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.5f, 0.7f)
					.eyeHeight(0.55f)
					.maxTrackingRange(8)
					.build("tocker"));

	/** The ceiling. Hung by {@link dev.psyda.surrogate.fauna.Fauna}, because a heightmap has no roofs on it. */
	public static final EntityType<dev.psyda.surrogate.fauna.LanternSlugEntity> LANTERN_SLUG = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("lantern_slug"),
			EntityType.Builder.<dev.psyda.surrogate.fauna.LanternSlugEntity>create(dev.psyda.surrogate.fauna.LanternSlugEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.5f, 0.4f)
					.eyeHeight(0.2f)
					.maxTrackingRange(10)
					.build("lantern_slug"));

	/** Something to sit on: invisible, weightless, alive only while somebody is on it. */
	public static final EntityType<dev.psyda.surrogate.entity.SeatEntity> SEAT = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("seat"),
			EntityType.Builder.<dev.psyda.surrogate.entity.SeatEntity>create(dev.psyda.surrogate.entity.SeatEntity::new, SpawnGroup.MISC)
					.dimensions(0.01f, 0.01f)
					.maxTrackingRange(8)
					.build("seat"));

	/** A dart in flight, between a hand and the board. */
	public static final EntityType<dev.psyda.surrogate.entity.DartEntity> DART = Registry.register(Registries.ENTITY_TYPE, Surrogate.id("dart"),
			EntityType.Builder.<dev.psyda.surrogate.entity.DartEntity>create(dev.psyda.surrogate.entity.DartEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build("dart"));

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
		FabricDefaultAttributeRegistry.register(TRUNDLE, dev.psyda.surrogate.fauna.TrundleEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SLAGBACK, dev.psyda.surrogate.fauna.SlagbackEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(TOCKER, dev.psyda.surrogate.fauna.TockerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LANTERN_SLUG, dev.psyda.surrogate.fauna.LanternSlugEntity.createAttributes());
	}

	private ModEntities() {
	}
}
