package dev.psyda.surrogate.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The overworld here is the Toxic Wastes and there is a fourth dimension for the ship, so vanilla marks the
 * dimension set experimental twice over: once per non-vanilla dimension and once for the count. Neither is
 * a reason to warn the player on every world they make. See {@link RegistryLoaderMixin} for the other half.
 */
@Mixin(DimensionOptionsRegistryHolder.class)
public abstract class DimensionOptionsRegistryHolderMixin {
	@Inject(method = "getLifecycle(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/world/dimension/DimensionOptions;)Lcom/mojang/serialization/Lifecycle;",
			at = @At("HEAD"), cancellable = true)
	private static void surrogate$stableDimension(RegistryKey<DimensionOptions> key, DimensionOptions options, CallbackInfoReturnable<Lifecycle> cir) {
		cir.setReturnValue(Lifecycle.stable());
	}

	@Redirect(method = "toConfig", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Lifecycle;experimental()Lcom/mojang/serialization/Lifecycle;"))
	private Lifecycle surrogate$stableDimensionSet() {
		return Lifecycle.stable();
	}
}
