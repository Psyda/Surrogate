package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RocketEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;

/**
 * The sample vehicle. Built at half scale like the crawler, so one unit is an eighth of a block: the ground
 * is at y = 24 and the nose reaches up from there. Front is -z.
 *
 * <p>The UV layout must match {@code paint_rocket} in {@code tools/gen_textures.py}.
 */
@Environment(EnvType.CLIENT)
public class RocketEntityModel extends SinglePartEntityModel<RocketEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("rocket"), "main");

	private final ModelPart root;

	public RocketEntityModel(ModelPart root) {
		this.root = root;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();

		// The stack, bottom to top: engine skirt, body, capsule, nose. Ground is y = 24.
		root.addChild("skirt", ModelPartBuilder.create().uv(0, 0).cuboid(-5f, 18f, -5f, 10f, 6f, 10f), ModelTransform.NONE);
		root.addChild("body", ModelPartBuilder.create().uv(0, 20).cuboid(-4f, -6f, -4f, 8f, 24f, 8f), ModelTransform.NONE);
		root.addChild("capsule", ModelPartBuilder.create().uv(56, 20).cuboid(-3f, -14f, -3f, 6f, 8f, 6f), ModelTransform.NONE);
		root.addChild("nose", ModelPartBuilder.create().uv(56, 0).cuboid(-2f, -20f, -2f, 4f, 6f, 4f), ModelTransform.NONE);

		// Four fins around the skirt, so it reads as a rocket from any angle.
		root.addChild("fin_north", ModelPartBuilder.create().uv(48, 0).cuboid(-1f, 14f, -8f, 2f, 10f, 4f), ModelTransform.NONE);
		root.addChild("fin_south", ModelPartBuilder.create().uv(48, 0).cuboid(-1f, 14f, 4f, 2f, 10f, 4f), ModelTransform.NONE);
		root.addChild("fin_east", ModelPartBuilder.create().uv(48, 0).cuboid(4f, 14f, -1f, 4f, 10f, 2f), ModelTransform.NONE);
		root.addChild("fin_west", ModelPartBuilder.create().uv(48, 0).cuboid(-8f, 14f, -1f, 4f, 10f, 2f), ModelTransform.NONE);

		return TexturedModelData.of(data, 128, 64);
	}

	@Override
	public void setAngles(RocketEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}

	@Override
	public ModelPart getPart() {
		return root;
	}
}
