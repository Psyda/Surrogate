package dev.psyda.surrogate.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.entry.RegistryEntryInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla files every registry entry that comes from a data pack it does not ship itself as experimental,
 * which is every biome, noise setting, dimension and world preset of this mod, and then warns about
 * "experimental settings" on every world created or opened with them. They are the game, not an experiment.
 */
@Mixin(RegistryLoader.class)
public abstract class RegistryLoaderMixin {
	@ModifyArg(method = "loadFromResource(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/registry/RegistryOps$RegistryInfoGetter;"
			+ "Lnet/minecraft/registry/MutableRegistry;Lcom/mojang/serialization/Decoder;Ljava/util/Map;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/RegistryLoader;parseAndAdd(Lnet/minecraft/registry/MutableRegistry;"
					+ "Lcom/mojang/serialization/Decoder;Lnet/minecraft/registry/RegistryOps;Lnet/minecraft/registry/RegistryKey;"
					+ "Lnet/minecraft/resource/Resource;Lnet/minecraft/registry/entry/RegistryEntryInfo;)V"), index = 5)
	private static RegistryEntryInfo surrogate$stableEntries(RegistryEntryInfo info) {
		return info.lifecycle() == Lifecycle.stable() ? info : new RegistryEntryInfo(info.knownPackInfo(), Lifecycle.stable());
	}
}
