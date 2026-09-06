package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CompanyShipEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the company ship. Two things here are not like the other entity renderers, and both are because of
 * its size: the model is scaled up several times beyond its own numbers, and culling is switched off
 * entirely. Vanilla culls on the collision box, which for this entity is four blocks across while the hull
 * is nearly two hundred, so the ship would blink out whenever its centre left the frustum.
 */
@Environment(EnvType.CLIENT)
public class CompanyShipRenderer extends EntityRenderer<CompanyShipEntity> {
	private static final Identifier TEXTURE = Surrogate.id("textures/entity/company_ship.png");
	/** The model is authored small and flown large. */
	private static final float SCALE = 52.0f;

	private final CompanyShipModel model;

	public CompanyShipRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new CompanyShipModel(context.getPart(CompanyShipModel.LAYER));
		this.shadowRadius = 0f;
	}

	@Override
	public void render(CompanyShipEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - yaw));
		matrices.scale(-SCALE, -SCALE, SCALE);
		model.setAngles(entity, 0f, 0f, entity.age + tickDelta, 0f, 0f);
		VertexConsumer consumer = vertexConsumers.getBuffer(model.getLayer(TEXTURE));
		// Full-bright: it is above the weather with its own lights on, not lit by the valley it is crossing.
		model.render(matrices, consumer, 0xF000F0, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public boolean shouldRender(CompanyShipEntity entity, Frustum frustum, double x, double y, double z) {
		return true;
	}

	@Override
	public Identifier getTexture(CompanyShipEntity entity) {
		return TEXTURE;
	}
}
