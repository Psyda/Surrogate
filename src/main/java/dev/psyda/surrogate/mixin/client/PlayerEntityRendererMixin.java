package dev.psyda.surrogate.mixin.client;

import dev.psyda.surrogate.entity.RobotEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pilot's body is in the chair; only the chassis should be visible in the field. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"), cancellable = true)
	private void surrogate$hidePilot(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
									 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if (RobotEntity.isPiloting(player)) {
			ci.cancel();
		}
	}
}
