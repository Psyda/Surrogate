package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.BorerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the body off the borer's own position history, so the segments follow where the head has actually
 * been and the whole thing reads as one animal rather than a train. Six rings, each tapering, each pointed
 * at the one in front of it.
 */
@Environment(EnvType.CLIENT)
public class BorerEntityRenderer extends EntityRenderer<BorerEntity> {
	private static final Identifier TEXTURE = Surrogate.id("textures/entity/borer.png");
	/** Body rings, and how many remembered steps apart they sit. */
	private static final int SEGMENTS = 6;
	private static final int SPACING = 2;
	/**
	 * A floor under the light it is drawn at. In solid rock the light level is nothing at all, and a wholly
	 * black shape against black rock is not a shape. Block light 4, no sky: the lightmap packs block light
	 * into bits 4 and up.
	 */
	private static final int MIN_LIGHT = 4 << 4;

	private final BorerEntityModel model;

	public BorerEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new BorerEntityModel(context.getPart(BorerEntityModel.LAYER));
		this.shadowRadius = 0f;
	}

	@Override
	public void render(BorerEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		VertexConsumer consumer = vertexConsumers.getBuffer(model.getLayer(TEXTURE));
		int lit = Math.max(light, MIN_LIGHT);
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - yaw));
		matrices.scale(-1f, -1f, 1f);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getPitch()));
		model.head().render(matrices, consumer, lit, OverlayTexture.DEFAULT_UV);
		matrices.pop();

		Vec3d origin = entity.getPos();
		for (int i = 1; i <= SEGMENTS; i++) {
			Vec3d at = entity.trailAt(i * SPACING);
			Vec3d along = entity.trailAt((i - 1) * SPACING).subtract(at);
			if (along.lengthSquared() < 1.0E-6) along = new Vec3d(0.0, 0.0, 1.0);
			float segmentYaw = (float) (MathHelper.atan2(along.z, along.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
			float segmentPitch = (float) (-MathHelper.atan2(along.y, along.horizontalLength()) * MathHelper.DEGREES_PER_RADIAN);
			float taper = 1f - i * 0.09f;
			matrices.push();
			matrices.translate(at.x - origin.x, at.y - origin.y, at.z - origin.z);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - segmentYaw));
			matrices.scale(-taper, -taper, taper);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(segmentPitch));
			model.segment().render(matrices, consumer, lit, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(BorerEntity entity) {
		return TEXTURE;
	}
}
