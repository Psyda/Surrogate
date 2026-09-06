package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.entity.CameraEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two small favours for the cinematic camera: no eye-height glide when the camera changes hands, and
 * a shake the server can ask for.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float cameraY;
	@Shadow
	private float lastCameraY;

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	@Inject(method = "update", at = @At("HEAD"))
	private void surrogate$snapEyeHeight(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		if (focusedEntity instanceof CameraEntity || CinematicState.consumeEyeSnap()) {
			this.cameraY = focusedEntity.getStandingEyeHeight();
			this.lastCameraY = this.cameraY;
		}
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void surrogate$shake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		float strength = CinematicState.shakeAmount(tickDelta);
		if (strength <= 0f) return;
		double t = System.nanoTime() / 1.0e9 * 31.0;
		float yaw = (float) (Math.sin(t * 1.7) * 0.6 + Math.sin(t * 3.3) * 0.4) * strength * 2.4f;
		float pitch = (float) (Math.sin(t * 2.3 + 1.0) * 0.6 + Math.sin(t * 4.1) * 0.4) * strength * 1.7f;
		setRotation(getYaw() + yaw, getPitch() + pitch);
	}
}
