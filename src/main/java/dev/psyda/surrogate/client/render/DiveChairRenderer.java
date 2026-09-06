package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.block.DiveChairBlock;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

import java.util.UUID;

/** Shows the pilot's body slumped in the chair while they are out in a chassis. */
@Environment(EnvType.CLIENT)
public class DiveChairRenderer implements BlockEntityRenderer<DiveChairBlockEntity> {
	private final PlayerEntityModel<AbstractClientPlayerEntity> wide;
	private final PlayerEntityModel<AbstractClientPlayerEntity> slim;

	public DiveChairRenderer(BlockEntityRendererFactory.Context context) {
		this.wide = new PlayerEntityModel<>(context.getLayerModelPart(EntityModelLayers.PLAYER), false);
		this.slim = new PlayerEntityModel<>(context.getLayerModelPart(EntityModelLayers.PLAYER_SLIM), true);
	}

	@Override
	public void render(DiveChairBlockEntity chair, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		UUID occupant = chair.getOccupant();
		if (occupant == null) return;

		MinecraftClient client = MinecraftClient.getInstance();
		SkinTextures skin = null;
		if (client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(occupant);
			if (entry != null) skin = entry.getSkinTextures();
		}
		if (skin == null) skin = DefaultSkinHelper.getSkinTextures(occupant);
		PlayerEntityModel<AbstractClientPlayerEntity> model = skin.model() == SkinTextures.Model.SLIM ? slim : wide;

		Direction facing = chair.getCachedState().contains(DiveChairBlock.FACING) ? chair.getCachedState().get(DiveChairBlock.FACING) : Direction.NORTH;
		matrices.push();
		// Hips on the seat cushion (7/16 high); the model's hip is 0.75 above its feet.
		matrices.translate(0.5, 7.0 / 16.0 - 0.75 + 0.06, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(15f));
		matrices.scale(-1f, -1f, 1f);
		matrices.translate(0.0, -1.501, 0.0);
		pose(model);
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin.texture()));
		model.render(matrices, consumer, light, overlay);
		matrices.pop();
	}

	private static void pose(PlayerEntityModel<?> model) {
		// EntityModel.child defaults to true and only LivingEntityRenderer clears it; left set, the body renders
		// at half size shoved down into the block with an oversized head poking out of the seat.
		model.child = false;
		model.sneaking = false;
		model.head.pitch = -0.15f;
		model.head.yaw = 0f;
		model.head.roll = 0.05f;
		model.body.pitch = 0f;
		model.body.yaw = 0f;
		model.rightArm.pitch = -0.55f;
		model.rightArm.yaw = 0f;
		model.rightArm.roll = 0.08f;
		model.leftArm.pitch = -0.55f;
		model.leftArm.yaw = 0f;
		model.leftArm.roll = -0.08f;
		model.rightLeg.pitch = -1.45f;
		model.rightLeg.yaw = -0.1f;
		model.leftLeg.pitch = -1.45f;
		model.leftLeg.yaw = 0.1f;
		model.hat.copyTransform(model.head);
		model.jacket.copyTransform(model.body);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightPants.copyTransform(model.rightLeg);
		model.leftPants.copyTransform(model.leftLeg);
	}
}
