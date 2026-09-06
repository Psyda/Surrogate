package dev.psyda.surrogate.mixin;

import dev.psyda.surrogate.entity.RobotEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	/** A linked pilot fits inside the one block chassis, with the camera at the robot's lens. */
	@Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
	private void surrogate$pilotDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (RobotEntity.isPiloting((PlayerEntity) (Object) this)) {
			cir.setReturnValue(RobotEntity.PILOT_DIMENSIONS);
		}
	}

	/** Sneaking is a careful-driving modifier while piloting, not a way to fall out of the chassis. */
	@Inject(method = "shouldDismount", at = @At("HEAD"), cancellable = true)
	private void surrogate$noSneakDismount(CallbackInfoReturnable<Boolean> cir) {
		if (RobotEntity.isPiloting((PlayerEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
