package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Sounds for the cinematic layer. The files are synthesized by {@code tools/gen_sounds.py}; drop a real
 * recording over any of them (same name, same place) and it is picked up without a code change.
 */
public final class ModSounds {
	/** Low swell under the title card. */
	public static final SoundEvent TITLE = register("cinematic.title");
	/** Squelch at the start and end of a radio line. */
	public static final SoundEvent RADIO_OPEN = register("cinematic.radio_open");
	public static final SoundEvent RADIO_CLOSE = register("cinematic.radio_close");
	/** Dead air after a transmission cuts out. */
	public static final SoundEvent STATIC = register("cinematic.static");
	/** Objective shown or completed. */
	public static final SoundEvent OBJECTIVE = register("cinematic.objective");
	/** The ground under the annex letting go. */
	public static final SoundEvent QUAKE = register("prologue.quake");
	/** The floor and roof going. */
	public static final SoundEvent BREACH = register("prologue.breach");
	/** The annex scrubber's alarm. */
	public static final SoundEvent ALARM = register("prologue.alarm");
	/** A chassis losing its pilot. */
	public static final SoundEvent SIGNAL_LOST = register("prologue.signal_lost");

	// The ship.
	/** The main engine and the air plant, looping under everything aboard. */
	public static final SoundEvent HUM = register("transit.hum");
	/** The breach klaxon, looping. */
	public static final SoundEvent KLAXON = register("transit.klaxon");
	/** The main engine winding down to nothing. */
	public static final SoundEvent ENGINE_CUT = register("transit.engine_cut");
	/** The main engine lighting again, and the hull complaining about it. */
	public static final SoundEvent ENGINE_RELIGHT = register("transit.engine_relight");
	/** The monitor beside the thaw cot. */
	public static final SoundEvent THAW = register("transit.thaw");
	/** A chair alarm, then nothing. */
	public static final SoundEvent FLATLINE = register("transit.flatline");
	/** The chime before an intercom line. */
	public static final SoundEvent INTERCOM = register("transit.intercom");
	/** A hatch sealing. */
	public static final SoundEvent HATCH = register("transit.hatch");
	/** Air leaving through a hole in the hull. */
	public static final SoundEvent DECOMPRESS = register("transit.decompress");
	/** Another channel bleeding into the link. */
	public static final SoundEvent BLEED = register("transit.bleed");
	/** The pod falling away and the air starting to bite. */
	public static final SoundEvent DESCENT = register("transit.descent");
	/** Sedation taking hold. */
	public static final SoundEvent SEDATE = register("transit.sedate");

	/** The docking console: the range beep that quickens as the ring closes, the lock going home, and a clunk when it does not. */
	public static final SoundEvent DOCK_BEEP = register("crawler.dock_beep");
	public static final SoundEvent DOCK_LOCK = register("crawler.dock_lock");
	public static final SoundEvent DOCK_ERROR = register("crawler.dock_error");

	/** The galley unit running, one second of it, and the ding at the end. */
	public static final SoundEvent MICROWAVE_HUM = register("block.microwave_hum");
	public static final SoundEvent MICROWAVE_DING = register("block.microwave_ding");

	/** Prefix for optional recorded voice lines: {@code surrogate:voice.<line key>}. */
	public static final String VOICE_PREFIX = "voice.";

	private static SoundEvent register(String name) {
		Identifier id = Surrogate.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void register() {
	}

	private ModSounds() {
	}
}
