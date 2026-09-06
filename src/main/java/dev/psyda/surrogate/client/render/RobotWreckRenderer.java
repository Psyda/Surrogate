package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotWreckEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Draws the chassis model toppled on its side with the scorched skin. */
@Environment(EnvType.CLIENT)
public class RobotWreckRenderer extends EntityRenderer<RobotWreckEntity> {
	private static final Identifier TEXTURE = Surrogate.id("textures/entity/robot_wreck.png");
	private final RobotEntityModel model;

	public RobotWreckRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new RobotEntityModel(context.getPart(RobotEntityModel.LAYER));
		this.shadowRadius = 0.5f;
	}

	@Override
	public void render(RobotWreckEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - yaw));
		matrices.translate(0.5, 0.42, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(75f));
		matrices.scale(-1f, -1f, 1f);
		matrices.translate(0.0, -1.501, 0.0);
		model.setStaticPose(0.6f, 0.9f);
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(RobotWreckEntity entity) {
		return TEXTURE;
	}
}
