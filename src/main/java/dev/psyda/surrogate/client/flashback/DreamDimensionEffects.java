package dev.psyda.surrogate.client.flashback;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The dream: no sky, no sun, no horizon, and fog the colour of the inside of your own eyelids.
 *
 * <p>Deliberately thick. Everything in the flashback is a lit room in the dark, and the fog is what makes the
 * dark stop at arm's length rather than showing the player a void with three lit boxes sitting in it.
 */
@Environment(EnvType.CLIENT)
public class DreamDimensionEffects extends DimensionEffects {
	private static final Vec3d NEARLY_BLACK = new Vec3d(0.024, 0.024, 0.031);

	public DreamDimensionEffects() {
		super(Float.NaN, false, SkyType.NONE, false, false);
	}

	@Override
	public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
		return NEARLY_BLACK;
	}

	@Override
	public boolean useThickFog(int camX, int camY) {
		return true;
	}

	@Override
	@Nullable
	public float[] getFogColorOverride(float skyAngle, float tickDelta) {
		return null;
	}
}
