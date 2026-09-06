package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.client.cinematic.CinematicState;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a cutscene has the controls, clicks and hotkeys go nowhere. Escape still pauses. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
	private void surrogate$lockInput(CallbackInfo ci) {
		if (CinematicState.isInputLocked()) ci.cancel();
	}
}
