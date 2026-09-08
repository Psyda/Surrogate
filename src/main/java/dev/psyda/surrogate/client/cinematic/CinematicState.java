package dev.psyda.surrogate.client.cinematic;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.CameraEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Client side of the cinematic layer: what the overlay draws, whether the player may move, and the
 * detached camera. Everything here is driven by {@link CinematicPayloads} from the server.
 */
@Environment(EnvType.CLIENT)
public final class CinematicState {
	public static final int SKIP_HOLD_TICKS = 30;

	private static boolean active;
	private static boolean lockInput;
	private static boolean wasLocked;
	private static boolean letterboxOn;
	private static float letterbox;
	private static float prevLetterbox;

	private static float fade;
	private static float prevFade;
	private static float fadeFrom;
	private static float fadeTo;
	private static int fadeTicks;
	private static int fadeElapsed;

	// The subtitle on screen.
	private static boolean lineActive;
	public static String speakerKey = "";
	public static String textKey = "";
	public static String lineArg = "";
	public static int lineStyle;
	public static int lineTicks;
	public static int lineElapsed;

	// The objective banner.
	public static String objectiveKey = "";
	public static int objectiveState;
	public static int objectiveElapsed;
	/** The objective in plain words, a translation key, or empty. */
	public static String hintKey = "";

	/** One line heard, kept for the log screen. */
	public record LogLine(String speaker, String text, String arg, int style) {
	}

	/** Everything said so far, oldest first, capped. */
	public static final Deque<LogLine> log = new ArrayDeque<>();
	private static final int LOG_CAP = 60;

	// The conference call on the hub terminal. Two masks over CallPanel's ordinals and whoever is talking;
	// `callAge` is what the static and the scanlines are drawn off, and `callFade` eases the grid in and out
	// so that closing the call is not a frame of eight faces and then nothing.
	public static int callLive;
	public static int callSnow;
	public static int callSpeaking = -1;
	public static int callAge;
	// What is drawn while the grid fades out. The masks go to zero the instant the call closes and the fade
	// takes two seconds, so drawing from the live ones turns eight faces into eight NO CARRIER cards for the
	// whole of it, which is a different scene ending than the one anybody wrote.
	private static int shownLive;
	private static int shownSnow;
	private static float callFade;
	private static float prevCallFade;

	private static float shakeStrength;
	private static int shakeTicks;
	private static int shakeElapsed;

	@Nullable
	private static CameraEntity camera;
	private static boolean eyeSnapPending;
	@Nullable
	private static Perspective savedPerspective;

	public static int skipHeld;
	/** Whether the advance key was already down last tick, so a held key does not eat a whole conversation. */
	private static boolean advanceHeld;
	/** Whether the line on screen said it could be pressed through. */
	private static boolean lineAdvance = true;

	private CinematicState() {
	}

	// ------------------------------------------------------------------ queries

	public static boolean isActive() {
		return active;
	}

	public static boolean isInputLocked() {
		return active && lockInput;
	}

	public static boolean isCameraDetached() {
		return camera != null;
	}

	public static boolean hasLine() {
		return lineActive;
	}

	/** Whether the line on screen may be pressed through. False for scenes that are only their lines. */
	public static boolean canAdvance() {
		return lineActive && lineAdvance;
	}

	/** How far the call grid has faded in, 0 to 1. Zero when there is no call and nothing to draw. */
	public static float call(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevCallFade, callFade);
	}

	public static int callShownLive() {
		return shownLive;
	}

	public static int callShownSnow() {
		return shownSnow;
	}

	public static float letterbox(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLetterbox, letterbox);
	}

	public static float fade(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevFade, fade);
	}

	public static float shakeAmount(float tickDelta) {
		if (shakeTicks <= 0 || shakeElapsed >= shakeTicks) return 0f;
		float t = MathHelper.clamp((shakeElapsed + tickDelta) / shakeTicks, 0f, 1f);
		return shakeStrength * (1f - t) * (1f - t);
	}

	/** True exactly once after the camera is handed back, so the eye height does not glide into place. */
	public static boolean consumeEyeSnap() {
		if (!eyeSnapPending) return false;
		eyeSnapPending = false;
		return true;
	}

	private static boolean isVoiceStyle(int style) {
		return style == CinematicPayloads.RADIO || style == CinematicPayloads.INTERCOM;
	}

	// ------------------------------------------------------------------ packets

	public static void onState(CinematicPayloads.State payload) {
		active = payload.active();
		lockInput = payload.lockInput();
		letterboxOn = payload.letterbox();
		if (!active) {
			releaseCamera(MinecraftClient.getInstance());
			lineActive = false;
			letterboxOn = false;
			// Every director ends a scene by sending this and nothing else, so the call has to die off it
			// as well as off its own payload. Otherwise a skipped conference leaves eight faces on the HUD.
			callLive = 0;
			callSnow = 0;
			callSpeaking = -1;
		}
	}

	public static void onCamera(CinematicPayloads.Camera payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (payload.frames().isEmpty()) {
			releaseCamera(client);
			return;
		}
		if (client.world == null || client.player == null) return;
		if (camera == null) {
			camera = new CameraEntity(ModEntities.CAMERA, client.world);
			savedPerspective = client.options.getPerspective();
			client.options.setPerspective(Perspective.FIRST_PERSON);
		}
		camera.setPath(payload.frames());
		client.setCameraEntity(camera);
	}

	private static void releaseCamera(MinecraftClient client) {
		if (camera == null) return;
		camera = null;
		eyeSnapPending = true;
		if (client.player != null) client.setCameraEntity(client.player);
		if (savedPerspective != null) {
			client.options.setPerspective(savedPerspective);
			savedPerspective = null;
		}
	}

	public static void onLine(CinematicPayloads.Line payload) {
		speakerKey = payload.speaker();
		textKey = payload.text();
		lineArg = payload.arg();
		lineStyle = payload.style();
		lineTicks = Math.max(1, payload.ticks());
		lineElapsed = 0;
		lineActive = true;
		lineAdvance = payload.advance();
		if (!textKey.isEmpty() && lineStyle != CinematicPayloads.TITLE) {
			log.addLast(new LogLine(speakerKey, textKey, lineArg, lineStyle));
			while (log.size() > LOG_CAP) log.removeFirst();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		switch (lineStyle) {
			case CinematicPayloads.RADIO -> play(client, ModSounds.RADIO_OPEN, 0.6f, 1.0f);
			case CinematicPayloads.INTERCOM -> play(client, ModSounds.INTERCOM, 0.7f, 1.0f);
			case CinematicPayloads.SYSTEM -> play(client, ModSounds.OBJECTIVE, 0.5f, 0.6f);
			default -> {
			}
		}
		if (!payload.voice().isEmpty()) {
			Identifier voice = Surrogate.id(payload.voice());
			if (client.getSoundManager().get(voice) != null) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(voice), 1.0f, 1.0f));
			}
		}
	}

	public static void onCall(CinematicPayloads.Call payload) {
		if (callLive == 0 && payload.live() != 0) callAge = 0;
		callLive = payload.live();
		callSnow = payload.snow();
		callSpeaking = payload.speaking();
		if (callLive != 0) {
			shownLive = callLive;
			shownSnow = callSnow;
		}
	}

	public static void onFade(CinematicPayloads.Fade payload) {
		fadeFrom = fade;
		fadeTo = MathHelper.clamp(payload.alpha() / 255f, 0f, 1f);
		fadeTicks = payload.ticks();
		fadeElapsed = 0;
		if (fadeTicks <= 0) {
			fade = fadeTo;
			prevFade = fadeTo;
		}
	}

	public static void onHint(CinematicPayloads.Hint payload) {
		hintKey = payload.text();
	}

	public static void onObjective(CinematicPayloads.Objective payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		switch (payload.state()) {
			case CinematicPayloads.OBJECTIVE_SHOW -> {
				objectiveKey = payload.text();
				objectiveState = CinematicPayloads.OBJECTIVE_SHOW;
				objectiveElapsed = 0;
				play(client, ModSounds.OBJECTIVE, 0.7f, 1.0f);
			}
			case CinematicPayloads.OBJECTIVE_DONE -> {
				if (objectiveState == CinematicPayloads.OBJECTIVE_CLEAR) return;
				objectiveState = CinematicPayloads.OBJECTIVE_DONE;
				objectiveElapsed = 0;
				play(client, ModSounds.OBJECTIVE, 0.8f, 1.4f);
			}
			default -> objectiveState = CinematicPayloads.OBJECTIVE_CLEAR;
		}
	}

	public static void onEffect(CinematicPayloads.Effect payload) {
		if (payload.kind() == CinematicPayloads.EFFECT_SHAKE) {
			shakeStrength = payload.strength();
			shakeTicks = Math.max(1, payload.ticks());
			shakeElapsed = 0;
		}
	}

	private static void play(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
		client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftClient client) {
		prevLetterbox = letterbox;
		float target = active && letterboxOn ? 1f : 0f;
		letterbox += (target - letterbox) * 0.18f;
		if (Math.abs(target - letterbox) < 0.005f) letterbox = target;

		prevFade = fade;
		if (fadeElapsed < fadeTicks) {
			fadeElapsed++;
			fade = MathHelper.lerp((float) fadeElapsed / fadeTicks, fadeFrom, fadeTo);
		} else {
			fade = fadeTo;
		}

		if (lineActive) {
			lineElapsed++;
			if (lineElapsed > lineTicks + 10) {
				lineActive = false;
				if (isVoiceStyle(lineStyle)) play(client, ModSounds.RADIO_CLOSE, 0.5f, 1.0f);
			}
		}
		if (objectiveState != CinematicPayloads.OBJECTIVE_CLEAR) {
			objectiveElapsed++;
			if (objectiveState == CinematicPayloads.OBJECTIVE_DONE && objectiveElapsed > 70) objectiveState = CinematicPayloads.OBJECTIVE_CLEAR;
		}
		prevCallFade = callFade;
		float callTarget = callLive == 0 ? 0f : 1f;
		callFade += (callTarget - callFade) * 0.12f;
		if (Math.abs(callTarget - callFade) < 0.004f) callFade = callTarget;
		if (callLive != 0 || callFade > 0f) callAge++;

		if (shakeElapsed < shakeTicks) shakeElapsed++;
		if (camera != null) camera.step();

		ClientPlayerEntity player = client.player;
		boolean locked = isInputLocked();
		if (player != null && locked) {
			player.input.movementForward = 0f;
			player.input.movementSideways = 0f;
			player.input.jumping = false;
			player.input.sneaking = false;
			player.forwardSpeed = 0f;
			player.sidewaysSpeed = 0f;
			player.setJumping(false);
			player.setSprinting(false);
			if (client.currentScreen == null && client.options.jumpKey.isPressed()) {
				if (++skipHeld >= SKIP_HOLD_TICKS) {
					skipHeld = 0;
					ClientPlayNetworking.send(new CinematicPayloads.Skip());
				}
			} else {
				skipHeld = Math.max(0, skipHeld - 2);
			}
		} else {
			skipHeld = 0;
		}
		// Out of a shot the player has their hands, so the skip key is theirs to jump with and the offer is a
		// smaller one: press on through the line you have finished reading. Edge triggered, because a held
		// key would run the length of a conversation in a second and a half.
		boolean advance = player != null && !locked && canAdvance() && client.currentScreen == null
				&& client.options.sneakKey.isPressed();
		if (advance && !advanceHeld) {
			lineActive = false;
			ClientPlayNetworking.send(new CinematicPayloads.Advance());
		}
		advanceHeld = advance;
		// Key presses buffered while locked would all fire at once on release.
		if (wasLocked && !locked) KeyBinding.unpressAll();
		wasLocked = locked;
	}

	public static void reset() {
		// The camera's own release restores the perspective it took; a disconnect in the middle of a scene
		// skips that and used to leave the player in forced first person for the rest of the session.
		MinecraftClient client = MinecraftClient.getInstance();
		if (savedPerspective != null && client.options != null) client.options.setPerspective(savedPerspective);
		hintKey = "";
		log.clear();
		active = false;
		lockInput = false;
		wasLocked = false;
		letterboxOn = false;
		letterbox = 0f;
		prevLetterbox = 0f;
		fade = 0f;
		prevFade = 0f;
		fadeTo = 0f;
		fadeTicks = 0;
		lineActive = false;
		lineArg = "";
		objectiveState = CinematicPayloads.OBJECTIVE_CLEAR;
		callLive = 0;
		callSnow = 0;
		callSpeaking = -1;
		callAge = 0;
		shownLive = 0;
		shownSnow = 0;
		callFade = 0f;
		prevCallFade = 0f;
		shakeTicks = 0;
		camera = null;
		eyeSnapPending = false;
		savedPerspective = null;
		skipHeld = 0;
		advanceHeld = false;
	}
}
