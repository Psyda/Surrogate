package dev.psyda.surrogate.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * A minute of your own air. While it lasts the outside atmosphere fills the lungs much more slowly and
 * cannot kill; the dizziness and the dark still come, so nobody mistakes it for a suit.
 */
public class RebreatherEffect extends StatusEffect {
	public RebreatherEffect() {
		super(StatusEffectCategory.BENEFICIAL, 0x5FD7E6);
	}
}
