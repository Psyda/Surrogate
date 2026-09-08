package dev.psyda.surrogate.mixin;

import dev.psyda.surrogate.entity.Pets;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.passive.TameableEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The dog and the cat do not follow a pilot out of the pod. See {@link Pets}. */
@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin {
	@Shadow
	@Final
	private TameableEntity tameable;

	@Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
	private void surrogate$stayHome(CallbackInfoReturnable<Boolean> cir) {
		if (Pets.ownerOutOfReach(tameable)) cir.setReturnValue(false);
	}

	@Inject(method = "shouldContinue", at = @At("HEAD"), cancellable = true)
	private void surrogate$stopFollowing(CallbackInfoReturnable<Boolean> cir) {
		if (Pets.ownerOutOfReach(tameable)) cir.setReturnValue(false);
	}
}
