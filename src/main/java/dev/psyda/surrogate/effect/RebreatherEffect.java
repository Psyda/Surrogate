package dev.psyda.surrogate.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * A minute of your own air. While it lasts the outside atmosphere fills the lungs much more slowly and
 * cannot kill; the dizziness and the dark still come, so nobody mistakes it for a suit.
 *
 * <p>It keeps the lungs topped up under water as well. That is not a new power so much as the name of the
 * thing: a rebreather is a rebreather, and the one place in the game it was ever going to be needed for its
 * whole minute is the floor of a chasm that turns out to be flooded to sea level. The clock is unchanged —
 * sixty seconds is still the whole of it.
 */
public class RebreatherEffect extends StatusEffect {
	public RebreatherEffect() {
		super(StatusEffectCategory.BENEFICIAL, 0x5FD7E6);
	}

	@Override
	public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
		if (entity.getAir() < entity.getMaxAir()) entity.setAir(entity.getMaxAir());
		return true;
	}

	@Override
	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		return true;
	}
}
