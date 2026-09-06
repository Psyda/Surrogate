package dev.psyda.surrogate.client;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.client.transit.ShipAmbientSound;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.HazardPayload;
import dev.psyda.surrogate.registry.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of the weather, fed by {@link HazardPayload}. The readings arrive in steps every second
 * and are eased here so nothing snaps on; the rain particles and the three ambient loops run off this tick
 * too. With the hazard layer off in the config every field stays at zero and the whole thing costs a
 * comparison a tick.
 */
@Environment(EnvType.CLIENT)
public final class HazardClientState {
	/** Storm intensity, 0 to 1, and 0 when there is no storm. */
	public static float storm;
	/** How close the nearest borer is, 0 to 1, for the seismic bar. */
	public static float seismic;
	/** How far gone the uplink picture is, 0 to 1. */
	public static float linkNoise;
	/** True through the run-up before a storm arrives. */
	public static boolean warning;
	/** True while acid is falling on the player's own square. */
	public static boolean acidRain;

	// What the eye sees: the readings above, walked towards rather than jumped to.
	private static float stormEased;
	private static float prevStormEased;
	private static float seismicEased;
	private static float prevSeismicEased;
	private static float noiseEased;
	private static float prevNoiseEased;
	private static float rainEased;
	private static float prevRainEased;

	@Nullable
	private static ShipAmbientSound wind;
	@Nullable
	private static ShipAmbientSound rain;
	@Nullable
	private static ShipAmbientSound hiss;

	private static final Random RANDOM = Random.create();
	private static final BlockPos.Mutable SPAWN = new BlockPos.Mutable();

	private HazardClientState() {
	}

	public static void onPayload(HazardPayload payload) {
		// A client with the hazard layer switched off never hears about the weather, whatever the server says.
		if (!Surrogate.CONFIG.hazards) {
			reset();
			return;
		}
		storm = MathHelper.clamp(payload.storm(), 0f, 1f);
		warning = payload.warning();
		acidRain = payload.acidRain();
		seismic = MathHelper.clamp(payload.seismic(), 0f, 1f);
		linkNoise = MathHelper.clamp(payload.linkNoise(), 0f, 1f);
	}

	public static void reset() {
		storm = 0f;
		seismic = 0f;
		linkNoise = 0f;
		warning = false;
		acidRain = false;
		stormEased = 0f;
		prevStormEased = 0f;
		seismicEased = 0f;
		prevSeismicEased = 0f;
		noiseEased = 0f;
		prevNoiseEased = 0f;
		rainEased = 0f;
		prevRainEased = 0f;
		// Fade rather than orphan: a loop dropped on the floor keeps playing until the sound engine stops.
		if (wind != null) wind.fadeOut();
		if (rain != null) rain.fadeOut();
		if (hiss != null) hiss.fadeOut();
		wind = null;
		rain = null;
		hiss = null;
	}

	// ------------------------------------------------------------------ queries

	/** Storm intensity for a frame, lerped between the last two ticks. */
	public static float stormLevel(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevStormEased, stormEased);
	}

	public static float seismicLevel(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevSeismicEased, seismicEased);
	}

	public static float noiseLevel(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevNoiseEased, noiseEased);
	}

	/** How established the rain is, 0 to 1: it comes on over a couple of seconds and goes the same way. */
	public static float rainLevel(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevRainEased, rainEased);
	}

	/** The same two readings for the pilot HUD, which does not need them any smoother than a tick. */
	public static float stormReading() {
		return stormEased;
	}

	public static float seismicReading() {
		return seismicEased;
	}

	// ------------------------------------------------------------------ tick

	public static void tick(MinecraftClient client) {
		if (idle()) return;
		prevStormEased = stormEased;
		prevSeismicEased = seismicEased;
		prevNoiseEased = noiseEased;
		prevRainEased = rainEased;
		boolean live = Surrogate.CONFIG.hazards && client.world != null && client.player != null;
		stormEased = approach(stormEased, live ? storm : 0f, 0.02f);
		seismicEased = approach(seismicEased, live ? seismic : 0f, 0.04f);
		noiseEased = approach(noiseEased, live ? linkNoise : 0f, 0.05f);
		rainEased = approach(rainEased, live && acidRain ? 1f : 0f, 0.02f);
		if (client.world == null) return;
		sounds(client);
		if (rainEased > 0.05f && client.player != null) rainParticles(client);
	}

	/** Nothing is on and nothing is left to fade out. */
	private static boolean idle() {
		return storm <= 0f && seismic <= 0f && linkNoise <= 0f && !acidRain
				&& stormEased <= 0f && seismicEased <= 0f && noiseEased <= 0f && rainEased <= 0f
				&& wind == null && rain == null && hiss == null;
	}

	/** Walk a reading towards its target at a fixed rate: the packets step, the picture does not. */
	private static float approach(float current, float target, float rate) {
		return current < target ? Math.min(target, current + rate) : Math.max(target, current - rate);
	}

	// ------------------------------------------------------------------ sounds

	private static void sounds(MinecraftClient client) {
		boolean wantWind = stormEased > 0.02f;
		if (wantWind && (wind == null || wind.isDone())) {
			wind = new ShipAmbientSound(ModSounds.STORM_WIND, 0.7f);
			client.getSoundManager().play(wind);
		} else if (!wantWind && wind != null && !wind.isDone()) {
			wind.fadeOut();
		}
		if (wind != null && !wind.isDone()) wind.setTarget(0.15f + 0.55f * stormEased);

		boolean wantRain = rainEased > 0.02f;
		if (wantRain && (rain == null || rain.isDone())) {
			rain = new ShipAmbientSound(ModSounds.ACID_RAIN, 0.6f);
			client.getSoundManager().play(rain);
		} else if (!wantRain && rain != null && !rain.isDone()) {
			rain.fadeOut();
		}
		if (rain != null && !rain.isDone()) rain.setTarget(0.6f * rainEased);

		// The hiss is the uplink's own, so it only exists while somebody is on the other end of one.
		boolean wantHiss = noiseEased > 0.02f && RobotEntity.isPiloting(client.player);
		if (wantHiss && (hiss == null || hiss.isDone())) {
			hiss = new ShipAmbientSound(ModSounds.LINK_HISS, 0.1f);
			client.getSoundManager().play(hiss);
		} else if (!wantHiss && hiss != null && !hiss.isDone()) {
			hiss.fadeOut();
		}
		if (hiss != null && !hiss.isDone()) hiss.setTarget(0.1f + 0.7f * noiseEased);

		// Let go of a loop that has finished fading, so the tick can go back to costing nothing.
		if (wind != null && wind.isDone()) wind = null;
		if (rain != null && rain.isDone()) rain = null;
		if (hiss != null && hiss.isDone()) hiss = null;
	}

	// ------------------------------------------------------------------ rain

	/**
	 * Fine streaks around the camera while the belt is raining. Bounded to a couple of dozen a tick inside a
	 * box around the viewer, and skipped where the spawn square is solid, so it falls on a roof instead of
	 * through it.
	 */
	private static void rainParticles(MinecraftClient client) {
		ParticlesMode particles = client.options.getParticles().getValue();
		if (particles == ParticlesMode.MINIMAL) return;
		ClientWorld world = client.world;
		Entity view = client.getCameraEntity() == null ? client.player : client.getCameraEntity();
		if (world == null || view == null) return;
		Vec3d eye = view.getEyePos();
		boolean thin = particles == ParticlesMode.DECREASED;
		int count = (thin ? 2 : 4) + (int) (rainEased * (thin ? 6f : 14f));
		for (int i = 0; i < count; i++) {
			double x = eye.x + (RANDOM.nextDouble() - 0.5) * 22.0;
			double z = eye.z + (RANDOM.nextDouble() - 0.5) * 22.0;
			double y = eye.y + 5.0 + RANDOM.nextDouble() * 6.0;
			SPAWN.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
			if (!world.getBlockState(SPAWN).isAir()) continue;
			// Gravity does the falling; the drop only needs somewhere to start.
			world.addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, x, y, z, 0.0, 0.0, 0.0);
		}
		// The odd splash at boot level, so the ground is being hit and not only the air above it.
		if (RANDOM.nextFloat() < rainEased * 0.5f) {
			double x = eye.x + (RANDOM.nextDouble() - 0.5) * 8.0;
			double z = eye.z + (RANDOM.nextDouble() - 0.5) * 8.0;
			world.addParticle(ParticleTypes.RAIN, x, eye.y - 1.4, z, 0.0, 0.0, 0.0);
		}
	}
}
