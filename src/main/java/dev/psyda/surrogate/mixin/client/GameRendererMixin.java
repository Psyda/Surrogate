package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.client.cinematic.CinematicState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player's hand has no business in a shot the player is not taking. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void surrogate$hideHand(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
		if (CinematicState.isCameraDetached()) ci.cancel();
	}
}
