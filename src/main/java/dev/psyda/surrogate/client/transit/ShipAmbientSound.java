package dev.psyda.surrogate.client.transit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/** A loop with no position that fades in when the ship needs it and out when it does not. */
@Environment(EnvType.CLIENT)
public class ShipAmbientSound extends MovingSoundInstance {
	private float target;
	private boolean fading;

	public ShipAmbientSound(SoundEvent sound, float volume) {
		super(sound, SoundCategory.AMBIENT, SoundInstance.createRandom());
		this.repeat = true;
		this.repeatDelay = 0;
		this.volume = 0f;
		this.target = volume;
		this.relative = true;
		this.attenuationType = SoundInstance.AttenuationType.NONE;
	}

	public void fadeOut() {
		fading = true;
	}

	/** Move the level the loop is creeping towards, for a loop whose source rises and falls as it plays. */
	public void setTarget(float volume) {
		this.target = volume;
	}

	@Override
	public void tick() {
		if (fading) {
			volume -= 0.04f;
			if (volume <= 0f) setDone();
		} else if (volume < target) {
			volume = Math.min(target, volume + 0.015f);
		} else if (volume > target) {
			volume = Math.max(target, volume - 0.015f);
		}
	}
}
