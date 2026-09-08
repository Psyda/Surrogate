package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.atmosphere.Exposure;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.math.BlockPos;

/**
 * The one rule for the dog and the cat: they never go outside.
 *
 * <p>A tamed animal follows its owner and, past twelve blocks, teleports to them. On any other map that is
 * the whole point of having one. Here the owner is usually a pilot, and the pilot is usually a chassis a
 * kilometre out on the sand, and a cat that keeps up with that is a cat standing in the open air with
 * nothing to breathe. So following is refused while the owner is piloting, and while the owner's own body is
 * anywhere the air would kill it. Inside the pod, and inside the crawler, they follow like anything else.
 */
public final class Pets {
	private Pets() {
	}

	/** Whether {@code pet} should leave its owner alone for now. */
	public static boolean ownerOutOfReach(TameableEntity pet) {
		LivingEntity owner = pet.getOwner();
		if (owner == null) return false;
		if (RobotEntity.isPiloting(owner)) return true;
		return Exposure.inspect(owner.getWorld(), BlockPos.ofFloored(owner.getEyePos())).state() == Exposure.EXPOSED;
	}
}
