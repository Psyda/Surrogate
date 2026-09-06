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

	private ModDamageTypes() {
	}

	public static DamageSource toxin(World world) {
		return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(TOXIN));
	}
}
