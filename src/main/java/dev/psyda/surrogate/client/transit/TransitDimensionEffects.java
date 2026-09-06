package dev.psyda.surrogate.client.transit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Space: no clouds, no fog worth the name, no vanilla sky at all. {@link TransitSkyRenderer} draws the rest. */
@Environment(EnvType.CLIENT)
public class TransitDimensionEffects extends DimensionEffects {
	public TransitDimensionEffects() {
		super(Float.NaN, false, SkyType.NONE, false, false);
	}

	@Override
	public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
		return Vec3d.ZERO;
	}

	@Override
	public boolean useThickFog(int camX, int camY) {
		return false;
	}

	@Override
	@Nullable
	public float[] getFogColorOverride(float skyAngle, float tickDelta) {
		return null;
	}
}
