package dev.psyda.surrogate.mixin;

import dev.psyda.surrogate.entity.Pets;
import net.minecraft.entity.passive.TameableEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The teleport itself, refused on the same terms as the following. See {@link Pets}. */
@Mixin(TameableEntity.class)
public abstract class TameableEntityMixin {
	@Inject(method = "tryTeleportToOwner", at = @At("HEAD"), cancellable = true)
	private void surrogate$noTeleportOutside(CallbackInfo ci) {
		if (Pets.ownerOutOfReach((TameableEntity) (Object) this)) ci.cancel();
	}
}
