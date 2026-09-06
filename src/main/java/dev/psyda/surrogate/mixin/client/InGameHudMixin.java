package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.client.cinematic.CinematicState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No crosshair or experience bar in the middle of a cutscene. The hotbar and status bars already stay
 * away on their own when the camera is not a player; these two do not.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void surrogate$hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (CinematicState.isCameraDetached()) ci.cancel();
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
	private void surrogate$hideExperienceBar(DrawContext context, int x, CallbackInfo ci) {
		if (CinematicState.isCameraDetached()) ci.cancel();
	}

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void surrogate$hideExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (CinematicState.isCameraDetached()) ci.cancel();
	}

	/** Lines are mirrored to chat so they can be read back later; the log has no place on the screen mid-scene. */
	@Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
	private void surrogate$hideChat(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (CinematicState.isInputLocked()) ci.cancel();
	}
}
