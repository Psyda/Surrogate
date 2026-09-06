package dev.psyda.surrogate.client.render;

import dev.psyda.surrogate.prologue.CrewEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/** The vanilla player model with a seated pose for a crew member in a dive chair. */
@Environment(EnvType.CLIENT)
public class CrewEntityModel extends PlayerEntityModel<CrewEntity> {
	public CrewEntityModel(ModelPart root) {
		super(root, false);
	}

	@Override
	public void setAngles(CrewEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
		if (!entity.isSeated()) return;
		this.rightArm.pitch = -0.55f;
		this.rightArm.yaw = 0f;
		this.rightArm.roll = 0.08f;
		this.leftArm.pitch = -0.55f;
		this.leftArm.yaw = 0f;
		this.leftArm.roll = -0.08f;
		this.rightLeg.pitch = -1.45f;
		this.rightLeg.yaw = -0.1f;
		this.leftLeg.pitch = -1.45f;
		this.leftLeg.yaw = 0.1f;
		this.head.pitch = -0.1f;
		this.hat.copyTransform(this.head);
		this.jacket.copyTransform(this.body);
		this.rightSleeve.copyTransform(this.rightArm);
		this.leftSleeve.copyTransform(this.leftArm);
		this.rightPants.copyTransform(this.rightLeg);
		this.leftPants.copyTransform(this.leftLeg);
	}
}
