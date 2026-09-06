package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.effect.RebreatherEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

public final class ModEffects {
	public static final RegistryEntry<StatusEffect> REBREATHER = Registry.registerReference(Registries.STATUS_EFFECT, Surrogate.id("rebreather"), new RebreatherEffect());

	public static void register() {
	}

	private ModEffects() {
	}
}
