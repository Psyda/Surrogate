package dev.psyda.surrogate.client;

import dev.psyda.surrogate.atmosphere.Exposure;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.PilotStatusPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/** Client copy of the pilot's body state, fed by {@link PilotStatusPayload}. */
@Environment(EnvType.CLIENT)
public final class ClientPilotState {
	public static int fatigue;
	public static int maxFatigue = 1;
	public static int unconscious;
	public static int bootTicks = 200;
	public static int shutdownTicks = 100;
	public static int idleDrainPerTick = 1;
	/** Body toxin load, 0 to 1. */
	public static float toxin;
	/** One of the {@link Exposure} state constants. */
	public static int airState = Exposure.SAFE;
	/** Air quality of the enclosure around the body, 0 to 1. */
	public static float airQuality = 1f;
	private static float lastToxin;
	/** Whether toxin went up on the last update: the body is breathing bad air right now. */
	public static boolean toxinRising;
	private static boolean wasPiloting;

	private ClientPilotState() {
	}

	public static void update(PilotStatusPayload payload) {
		fatigue = payload.fatigue();
		maxFatigue = Math.max(1, payload.maxFatigue());
		unconscious = payload.unconscious();
		bootTicks = Math.max(1, payload.bootTicks());
		shutdownTicks = Math.max(1, payload.shutdownTicks());
		idleDrainPerTick = Math.max(1, payload.idleDrainPerTick());
		toxin = MathHelper.clamp(payload.toxin() / 1000f, 0f, 1f);
		toxinRising = toxin > lastToxin + 1e-4f;
		lastToxin = toxin;
		airState = payload.airState();
		airQuality = MathHelper.clamp(payload.airQuality() / 100f, 0f, 1f);
	}

	public static void reset() {
		fatigue = 0;
		unconscious = 0;
		toxin = 0f;
		lastToxin = 0f;
		toxinRising = false;
		airState = Exposure.SAFE;
		airQuality = 1f;
		wasPiloting = false;
	}

	public static float fatigueFraction() {
		return MathHelper.clamp((float) fatigue / maxFatigue, 0f, 1f);
	}

	/** Recomputes the player's hitbox the moment a link starts or ends so the camera snaps to the right height. */
	public static void tick(MinecraftClient client) {
		boolean piloting = client.player != null && RobotEntity.isPiloting(client.player);
		if (piloting != wasPiloting) {
			wasPiloting = piloting;
			if (client.player != null) client.player.calculateDimensions();
		}
	}
}
