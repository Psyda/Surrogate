package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
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
 * Boxy one block tall chassis: twin treads, a body with two stub arms, and a sensor head with a lens and antenna.
 * UV layout must match tools/gen_textures.py.
 */
@Environment(EnvType.CLIENT)
public class RobotEntityModel extends SinglePartEntityModel<RobotEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Surrogate.id("robot"), "main");
	private static final float RAD = (float) (Math.PI / 180.0);

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart leftArm;
	private final ModelPart rightArm;

	public RobotEntityModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.leftArm = root.getChild("left_arm");
		this.rightArm = root.getChild("right_arm");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-6f, -13f, -6f, 12f, 9f, 12f), ModelTransform.pivot(0f, 24f, 0f));
		root.addChild("head", ModelPartBuilder.create()
						.uv(0, 21).cuboid(-4f, -3f, -4f, 8f, 3f, 8f)
						.uv(32, 21).cuboid(-2f, -2.5f, -5f, 4f, 2f, 1f)
						.uv(44, 21).cuboid(2.5f, -6f, 2.5f, 1f, 3f, 1f),
				ModelTransform.pivot(0f, 11f, 0f));
		root.addChild("left_arm", ModelPartBuilder.create().uv(48, 0).cuboid(-1f, -1f, -1f, 2f, 6f, 2f), ModelTransform.pivot(-7f, 13f, 0f));
		root.addChild("right_arm", ModelPartBuilder.create().uv(56, 0).cuboid(-1f, -1f, -1f, 2f, 6f, 2f), ModelTransform.pivot(7f, 13f, 0f));
		root.addChild("left_tread", ModelPartBuilder.create().uv(0, 32).cuboid(-7f, -4f, -6f, 4f, 4f, 12f), ModelTransform.pivot(0f, 24f, 0f));
		root.addChild("right_tread", ModelPartBuilder.create().uv(0, 48).cuboid(3f, -4f, -6f, 4f, 4f, 12f), ModelTransform.pivot(0f, 24f, 0f));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(RobotEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		if (entity.getState() == RobotState.OFFLINE) {
			head.yaw = 0f;
			head.pitch = 0.35f;
			leftArm.pitch = 0f;
			rightArm.pitch = 0f;
			return;
		}
		head.yaw = headYaw * RAD;
		head.pitch = headPitch * RAD;
		float swing = MathHelper.cos(limbAngle * 0.6662f) * 0.6f * limbDistance;
		leftArm.pitch = swing;
		rightArm.pitch = -swing;
	}

	/** Static pose for wrecks and previews. */
	public void setStaticPose(float headPitch, float armPitch) {
		head.yaw = 0f;
		head.pitch = headPitch;
		leftArm.pitch = armPitch;
		rightArm.pitch = armPitch;
	}

	@Override
	public ModelPart getPart() {
		return root;
	}
}
