package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CrawlerEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class CrawlerEntityRenderer extends EntityRenderer<CrawlerEntity> {
	private static final Identifier TEXTURE = Surrogate.id("textures/entity/crawler.png");
	private final CrawlerEntityModel model;

	public CrawlerEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.model = new CrawlerEntityModel(context.getPart(CrawlerEntityModel.LAYER));
		this.shadowRadius = 2.5f;
	}

	@Override
	public void render(CrawlerEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - yaw));
		// The model is built like a mob's, y down with the ground at 24, at half scale.
		matrices.scale(-2f, -2f, 2f);
		matrices.translate(0.0, -1.501, 0.0);
		model.setAngles(entity, 0f, 0f, entity.age + tickDelta, 0f, 0f);
		VertexConsumer consumer = vertexConsumers.getBuffer(model.getLayer(TEXTURE));
		model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(CrawlerEntity entity) {
		return TEXTURE;
	}
}
