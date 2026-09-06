package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.entity.RobotEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A pilot looking out of a chassis sees the tool it is holding, never their own arm: the arm is in the chair.
 * Held items draw without an arm anyway; this is the empty-handed case, where vanilla draws the bare arm.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderArmHoldingItem", at = @At("HEAD"), cancellable = true)
	private void surrogate$hidePilotArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress,
										float swingProgress, Arm arm, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && RobotEntity.isPiloting(client.player)) ci.cancel();
	}
}
