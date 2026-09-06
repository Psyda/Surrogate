package dev.psyda.surrogate.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.psyda.surrogate.client.HazardClientState;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What a magnetic storm does to the air: the fog turns the colour of rust and closes in until the far side
 * of the valley is gone. Both injects return on the first line while there is no storm, so a world that
 * never has one pays for a float comparison a frame.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
	@Shadow
	private static float red;
	@Shadow
	private static float green;
	@Shadow
	private static float blue;

	@Inject(method = "render", at = @At("RETURN"))
	private static void surrogate$stormFogColour(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
												 float skyDarkness, CallbackInfo ci) {
		float storm = HazardClientState.stormLevel(tickDelta);
		if (storm <= 0f) return;
		float t = Math.min(1f, storm * 0.85f);
		red = MathHelper.lerp(t, red, 0.30f);
		green = MathHelper.lerp(t, green, 0.26f);
		blue = MathHelper.lerp(t, blue, 0.14f);
		// render() has already handed the old colour to the driver; hand it the new one.
		RenderSystem.clearColor(red, green, blue, 0f);
	}

	@Inject(method = "applyFog", at = @At("RETURN"))
	private static void surrogate$stormFogDepth(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
												boolean thickFog, float tickDelta, CallbackInfo ci) {
		float storm = HazardClientState.stormLevel(tickDelta);
		// Only the terrain fog: thickening the sky's would put a wall across the horizon instead of in it.
		if (storm <= 0f || fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
		float end = viewDistance * MathHelper.lerp(storm, 1f, 0.18f);
		RenderSystem.setShaderFogStart(end * MathHelper.lerp(storm, 0.9f, 0.08f));
		RenderSystem.setShaderFogEnd(end);
	}
}
