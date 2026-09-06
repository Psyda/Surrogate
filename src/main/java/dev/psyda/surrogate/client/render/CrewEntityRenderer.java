package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.client.cinematic.CinematicState;
import dev.psyda.surrogate.prologue.CrewEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * A crew member in a hazmat suit, with a skin per character. Seated crew are lowered onto the chair
 * cushion and tilted back like the pilot in {@link DiveChairRenderer}; collapsed crew use the sleeping pose.
 */
@Environment(EnvType.CLIENT)
public class CrewEntityRenderer extends MobEntityRenderer<CrewEntity, CrewEntityModel> {
	public CrewEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new CrewEntityModel(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
		// Marsh takes the wrench on day three; the crew should be seen holding what they are handed.
		this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
	}

	@Override
	public Identifier getTexture(CrewEntity entity) {
		return Surrogate.id("textures/entity/crew_" + entity.getCharacter().key() + ".png");
	}

	@Override
	public void render(CrewEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		this.model.rightArmPose = entity.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		this.model.leftArmPose = entity.getOffHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
		// Hips on the seat cushion (7/16 high); the model's hip is 0.75 above its feet.
		if (entity.isSeated()) matrices.translate(0.0, 7.0 / 16.0 - 0.75 + 0.06, 0.0);
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
		matrices.pop();
	}

	@Override
	protected void setupTransforms(CrewEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
		super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
		if (entity.isSeated()) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(15f));
	}

	@Override
	protected boolean hasLabel(CrewEntity entity) {
		// No floating names in a shot: the camera is somebody else's eye.
		return !entity.isCollapsed() && !CinematicState.isCameraDetached() && super.hasLabel(entity);
	}
}
