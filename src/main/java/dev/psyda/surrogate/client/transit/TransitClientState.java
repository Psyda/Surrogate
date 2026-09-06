package dev.psyda.surrogate.client.transit;

import dev.psyda.surrogate.network.TransitPayload;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.transit.Transit;
import dev.psyda.surrogate.transit.TransitDimension;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of where the ship is in its week, fed by {@link TransitPayload}. The clock is
 * extrapolated between packets, the turnover is timed off the world clock, and the sounds of the ship
 * start and stop from here.
 */
@Environment(EnvType.CLIENT)
public final class TransitClientState {
	private static TransitPayload data = TransitPayload.none();
	private static long sentAt;
	@Nullable
	private static ShipAmbientSound hum;
	@Nullable
	private static ShipAmbientSound klaxon;

	private TransitClientState() {
	}

	public static void onPayload(TransitPayload payload) {
		data = payload;
		MinecraftClient client = MinecraftClient.getInstance();
		sentAt = client.world == null ? 0L : client.world.getTime();
	}

	public static void reset() {
		data = TransitPayload.none();
		sentAt = 0L;
		hum = null;
		klaxon = null;
	}

	// ------------------------------------------------------------------ queries

	/** Whether the player is on the ship and the server has told us about it. */
	public static boolean aboard() {
		MinecraftClient client = MinecraftClient.getInstance();
		return data.aboard() && client.world != null && client.world.getRegistryKey() == TransitDimension.WORLD;
	}

	public static boolean inTransitWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null && client.world.getRegistryKey() == TransitDimension.WORLD;
	}

	public static int day() {
		return Math.max(1, data.day());
	}

	public static float hours() {
		MinecraftClient client = MinecraftClient.getInstance();
		long now = client.world == null ? sentAt : client.world.getTime();
		return Math.min(23.5f, data.hours() + (now - sentAt) * data.hoursPerTick());
	}

	public static float weekFraction() {
		return Transit.weekFraction(Math.min(7, day()), hours());
	}

	public static int distanceKm() {
		return Transit.distanceKm(Math.min(7, day()), hours());
	}

	/** Hours of flight left, from the last morning aboard back to now. */
	public static float etaHours() {
		return (1f - weekFraction()) * 160f;
	}

	/** How far the ship has turned, in degrees about its vertical: 0 before turnover, 180 after. */
	public static float attitudeYaw(float tickDelta) {
		if (data.flipStart() < 0L) return 0f;
		MinecraftClient client = MinecraftClient.getInstance();
		long now = client.world == null ? data.flipStart() : client.world.getTime();
		float t = (now + tickDelta - data.flipStart()) / Math.max(1f, data.flipTicks());
		t = MathHelper.clamp(t, 0f, 1f);
		return 180f * t * t * (3f - 2f * t);
	}

	public static boolean turningOver(float tickDelta) {
		float yaw = attitudeYaw(tickDelta);
		return yaw > 0.5f && yaw < 179.5f;
	}

	public static boolean gravity() {
		return data.gravity();
	}

	public static boolean engine() {
		return data.engine();
	}

	public static boolean alarm() {
		return data.alarm();
	}

	public static boolean breach() {
		return data.breach();
	}

	public static int lights() {
		return data.lights();
	}

	public static boolean descending() {
		return data.phase() == 1;
	}

	// ------------------------------------------------------------------ sounds

	public static void tick(MinecraftClient client) {
		boolean aboard = aboard();
		boolean wantHum = aboard && data.engine();
		if (wantHum && (hum == null || hum.isDone())) {
			hum = new ShipAmbientSound(ModSounds.HUM, 0.55f);
			client.getSoundManager().play(hum);
		} else if (!wantHum && hum != null && !hum.isDone()) {
			hum.fadeOut();
		}
		boolean wantKlaxon = aboard && data.alarm();
		if (wantKlaxon && (klaxon == null || klaxon.isDone())) {
			klaxon = new ShipAmbientSound(ModSounds.KLAXON, 0.7f);
			client.getSoundManager().play(klaxon);
		} else if (!wantKlaxon && klaxon != null && !klaxon.isDone()) {
			klaxon.fadeOut();
		}
	}
}
