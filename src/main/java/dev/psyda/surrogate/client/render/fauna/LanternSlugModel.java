package dev.psyda.surrogate.client.render.fauna;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.fauna.LanternSlugEntity;
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
import net.minecraft.util.math.MathHelper;

/**
 * A flat soft thing with four lights on the front of it. UV layout must match tools/gen_textures.py.
 *
 * <p>Clinging, it is pressed to the ceiling and the body breathes slowly. The head end turns to follow
 * whoever is nearest, which given four eyes and no other moving part is the entire performance.
 *
 * <p>Falling, it tumbles. That state lasts about a second and never ends well.
 */
@Environment(EnvType.CLIENT)
public class LanternSlugModel extends SinglePartEntityModel<LanternSlugEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("lantern_slug"), "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;

	public LanternSlugModel(ModelPart root) {
		this.root = root;
		this.body = root.getChild("body");
		this.head = body.getChild("head");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData body = root.addChild("body",
				ModelPartBuilder.create().uv(0, 0).cuboid(-3f, -2f, -4f, 6f, 2f, 8f),
				ModelTransform.pivot(0f, 24f, 0f));
		// The eyes ride on the head so they turn with it; the eyes layer lights exactly these four faces.
		body.addChild("head",
				ModelPartBuilder.create().uv(0, 10).cuboid(-2.5f, -1.5f, -1.5f, 5f, 1f, 2f),
				ModelTransform.pivot(0f, 0f, -4f));
		return TexturedModelData.of(data, 32, 16);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(LanternSlugEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		if (!entity.isClinging()) {
			// Coming down. Two axes so it does not look like a dropped plank.
			body.pitch = animationProgress * 0.4f;
			body.roll = animationProgress * 0.25f;
			head.yaw = 0f;
			head.pitch = 0f;
			return;
		}
		body.pitch = 0f;
		body.roll = 0f;
		// Breathing: a slow swell in the body's thickness, which on a shape this flat is most of what says
		// it is alive at all.
		body.yScale = 1f + MathHelper.sin(animationProgress * 0.08f) * 0.12f;
		head.yaw = headYaw * ((float) Math.PI / 180f) * 0.6f;
		head.pitch = headPitch * ((float) Math.PI / 180f) * 0.4f;
	}
}
