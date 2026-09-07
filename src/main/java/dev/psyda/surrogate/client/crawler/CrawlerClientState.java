package dev.psyda.surrogate.client.crawler;

import dev.psyda.surrogate.crawler.CrawlerDocking;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.network.CrawlerPayloads;
import dev.psyda.surrogate.registry.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.MathHelper;

/**
 * What the client knows about the crawler it is in, and the keys it is holding at a console. The keyboard
 * mixin hands the movement keys here instead of to the player while a console has them; a click at the
 * docking console is the lock. The console also beeps: slow with a collar far off, quicker as the ring
 * closes, steady once it is inside the coupling window.
 */
@Environment(EnvType.CLIENT)
public final class CrawlerClientState {
	public static int seat;
	public static boolean lost;
	public static float heading;
	public static float speed;
	public static int charge;
	public static boolean docked;
	public static boolean collar;
	public static float dockOffset;
	public static float dockAngle;
	/** Whether the ceramic is on the hull; the readout says so, because the belt does not warn twice. */
	public static boolean cladding;
	/** How far the rain has got with the hull, 0 to 100. The gauge, so the four warnings are not the only word. */
	public static int wear;
	/** Client ticks since the last readout; the HUD goes away when it stops coming. */
	private static int staleTicks = 1000;

	private static float throttle;
	private static float steer;
	private static boolean leaving;
	private static boolean lockPending;
	private static boolean useWasDown;
	private static int beepTimer;

	/** The latest sensor picture, or null. */
	public static CrawlerPayloads.Scan scan;
	/** The latest camera scan, or null. */
	public static CrawlerPayloads.Camera camera;

	public static void onCamera(CrawlerPayloads.Camera payload) {
		camera = payload;
	}

	public static void onScan(CrawlerPayloads.Scan payload) {
		scan = payload;
	}

	private CrawlerClientState() {
	}

	public static void onState(CrawlerPayloads.State state) {
		seat = state.seat();
		lost = state.lost();
		heading = state.heading();
		speed = state.speed();
		charge = state.charge();
		docked = state.docked();
		collar = state.collar();
		dockOffset = state.dockOffset();
		dockAngle = state.dockAngle();
		cladding = state.cladding();
		wear = state.wear();
		staleTicks = 0;
	}

	public static void reset() {
		seat = 0;
		cladding = false;
		wear = 0;
		scan = null;
		camera = null;
		staleTicks = 1000;
		throttle = 0f;
		steer = 0f;
		leaving = false;
		lockPending = false;
		beepTimer = 0;
	}

	/** True while a readout arrived within the last few seconds: the player is aboard. */
	public static boolean aboard() {
		return staleTicks < 60;
	}

	public static boolean atConsole() {
		return seat > 0 && aboard();
	}

	/** The ring is inside the coupling window: a click now locks. */
	public static boolean aligned() {
		return collar && !docked && dockOffset <= CrawlerDocking.COUPLE_OFFSET && dockAngle <= CrawlerDocking.COUPLE_ANGLE;
	}

	/** Called by the keyboard mixin: takes the keys for the console and leaves the player standing still. */
	public static void capture(Input input) {
		throttle = input.movementForward;
		steer = input.movementSideways;
		leaving = input.sneaking;
		input.pressingForward = false;
		input.pressingBack = false;
		input.pressingLeft = false;
		input.pressingRight = false;
		input.movementForward = 0f;
		input.movementSideways = 0f;
		input.jumping = false;
		input.sneaking = false;
	}

	public static void tick(MinecraftClient client) {
		if (staleTicks < 1000) staleTicks++;
		if (client.player == null || !atConsole()) {
			beepTimer = 0;
			useWasDown = false;
			return;
		}
		// The use key is the click: its rising edge, once per press.
		boolean useDown = client.options.useKey.isPressed();
		if (useDown && !useWasDown && seat == CrawlerInterior.SEAT_DOCK && client.currentScreen == null) lockPending = true;
		useWasDown = useDown;
		ClientPlayNetworking.send(new CrawlerPayloads.Control(throttle, steer, leaving, lockPending));
		lockPending = false;
		if (leaving) {
			seat = 0;
			leaving = false;
		}
		tickBeeps(client);
	}

	private static void tickBeeps(MinecraftClient client) {
		if (seat != CrawlerInterior.SEAT_DOCK || !collar || docked) {
			beepTimer = 0;
			return;
		}
		if (--beepTimer > 0) return;
		boolean aligned = aligned();
		// Forty ticks between beeps ten blocks out, six at the window, a steady tick inside it.
		float closeness = 1f - MathHelper.clamp(dockOffset / 10f, 0f, 1f);
		beepTimer = aligned ? 3 : Math.round(MathHelper.lerp(closeness, 40f, 6f));
		float pitch = aligned ? 1.5f : 0.9f + 0.4f * closeness;
		client.getSoundManager().play(PositionedSoundInstance.master(ModSounds.DOCK_BEEP, pitch, aligned ? 0.45f : 0.35f));
	}
}
