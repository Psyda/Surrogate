package dev.psyda.surrogate.atmosphere;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

public final class ModDamageTypes {
	/** Breathing the outside air. Defined in data/surrogate/damage_type/toxin.json. */
	public static final RegistryKey<DamageType> TOXIN = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Surrogate.id("toxin"));
	/** Standing in a magnetic storm with nothing over you. Defined in data/surrogate/damage_type/storm.json. */
	public static final RegistryKey<DamageType> STORM = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Surrogate.id("storm"));
	/** Standing in a geyser's column. Defined in data/surrogate/damage_type/geyser.json. */
	public static final RegistryKey<DamageType> GEYSER = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Surrogate.id("geyser"));
	/** Bitten by what lives in the rock. Defined in data/surrogate/damage_type/borer.json. */
	public static final RegistryKey<DamageType> BORER = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Surrogate.id("borer"));
	/** Standing in the belt's rain with nothing over you. Defined in data/surrogate/damage_type/acid.json. */
	public static final RegistryKey<DamageType> ACID = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Surrogate.id("acid"));

	private ModDamageTypes() {
	}

	public static DamageSource toxin(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(TOXIN));
	}

	public static DamageSource storm(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(STORM));
	}

	public static DamageSource geyser(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(GEYSER));
	}

	public static DamageSource borer(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BORER));
	}

	public static DamageSource acid(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ACID));
	}
}
