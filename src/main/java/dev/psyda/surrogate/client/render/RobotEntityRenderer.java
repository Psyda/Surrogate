package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotPaint;
import dev.psyda.surrogate.entity.RobotState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class RobotEntityRenderer extends MobEntityRenderer<RobotEntity, RobotEntityModel> {
	/** One texture per paint, built once: chassis are numerous, so this must not allocate per frame. */
	private static final Identifier[] ONLINE = buildTextures();
	private static final Identifier OFFLINE = Surrogate.id("textures/entity/robot_offline.png");

	private static Identifier[] buildTextures() {
		RobotPaint[] paints = RobotPaint.values();
		Identifier[] textures = new Identifier[paints.length];
		for (int i = 0; i < paints.length; i++) {
			textures[i] = Surrogate.id("textures/entity/" + paints[i].texture() + ".png");
		}
		return textures;
	}

	public RobotEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new RobotEntityModel(context.getPart(RobotEntityModel.LAYER)), 0.4f);
	}

	/**
	 * The pilot's camera sits inside the chassis, so in first person the chassis itself must not be drawn.
	 * This has to happen in {@code render} rather than {@code shouldRender}: the world renderer always draws
	 * an entity the client player is riding, regardless of what {@code shouldRender} says.
	 */
	@Override
	public void render(RobotEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		if (isViewedFromInside(entity)) return;
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	private static boolean isViewedFromInside(RobotEntity entity) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity camera = client.getCameraEntity();
		return camera != null && camera.getVehicle() == entity && entity.isPilot(camera)
				&& client.options.getPerspective().isFirstPerson();
	}

	@Override
	public Identifier getTexture(RobotEntity entity) {
		// A dark chassis is a dark chassis whoever owns it; the paint only shows on a live one.
		return entity.getState() == RobotState.OFFLINE ? OFFLINE : ONLINE[entity.getPaint().ordinal()];
	}
}
