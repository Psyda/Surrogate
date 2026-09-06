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

	private static float shakeStrength;
	private static int shakeTicks;
	private static int shakeElapsed;

	@Nullable
	private static CameraEntity camera;
	private static boolean eyeSnapPending;
	@Nullable
	private static Perspective savedPerspective;

	public static int skipHeld;

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
		// Key presses buffered while locked would all fire at once on release.
		if (wasLocked && !locked) KeyBinding.unpressAll();
		wasLocked = locked;
	}

	public static void reset() {
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
		shakeTicks = 0;
		camera = null;
		eyeSnapPending = false;
		savedPerspective = null;
		skipHeld = 0;
	}
}
