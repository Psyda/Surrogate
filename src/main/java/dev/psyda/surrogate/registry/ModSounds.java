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

	/** A magnetic storm: the band washing out under the run-up, and the upper air letting go at the peak. */
	public static final SoundEvent STORM_WASH = register("hazard.storm_wash");
	public static final SoundEvent STORM_CRACK = register("hazard.storm_crack");

	/** A geyser: four seconds of ground moving, then the column, then the cap going on and shutting it up. */
	public static final SoundEvent GEYSER_RUMBLE = register("hazard.geyser_rumble");
	public static final SoundEvent GEYSER_ERUPT = register("hazard.geyser_erupt");
	public static final SoundEvent TAP_CAP = register("hazard.tap_cap");

	// The weather on Sallow. All three loop, and all three are held at a level that follows the reading.
	/** The storm blowing over the site. */
	public static final SoundEvent STORM_WIND = register("weather.storm_wind");
	/** The belt's rain going to work on the plating. */
	public static final SoundEvent ACID_RAIN = register("weather.acid_rain");
	/** The uplink losing its picture, under the snow. */
	public static final SoundEvent LINK_HISS = register("weather.link_hiss");

	/** Something working through the rock towards you, and the moment it arrives. */
	public static final SoundEvent BORER_GRIND = register("hazard.borer_grind");
	public static final SoundEvent BORER_LUNGE = register("hazard.borer_lunge");

	/** The galley unit running, one second of it, and the ding at the end. */
	public static final SoundEvent MICROWAVE_HUM = register("block.microwave_hum");
	public static final SoundEvent MICROWAVE_DING = register("block.microwave_ding");

	/** The two things in the flashback that are still switched on. */
	public static final SoundEvent FLASHBACK_RADIO = register("flashback.radio");
	public static final SoundEvent FLASHBACK_TELEVISION = register("flashback.television");
	/** The rest of a house at night, and a bar, and an office: recorded effects (tools/gen_sfx.py). */
	public static final SoundEvent FLASHBACK_CAR_PASS = register("flashback.car_pass");
	public static final SoundEvent FLASHBACK_CAR_HORN = register("flashback.car_horn");
	public static final SoundEvent FLASHBACK_CREAK = register("flashback.creak");
	public static final SoundEvent FLASHBACK_FOOTSTEPS_UPSTAIRS = register("flashback.footsteps_upstairs");
	public static final SoundEvent FLASHBACK_DOOR_UPSTAIRS = register("flashback.door_upstairs");
	public static final SoundEvent FLASHBACK_TV_SWITCH = register("flashback.tv_switch");
	public static final SoundEvent FLASHBACK_TV_CREDITS = register("flashback.tv_credits");
	public static final SoundEvent FLASHBACK_NEWS_STING = register("flashback.news_sting");
	public static final SoundEvent FLASHBACK_DART_HIT = register("flashback.dart_hit");
	public static final SoundEvent FLASHBACK_GLASS_CLINK = register("flashback.glass_clink");
	public static final SoundEvent FLASHBACK_FRONT_DOOR = register("flashback.front_door");
	public static final SoundEvent FLASHBACK_FLUORESCENT_HUM = register("flashback.fluorescent_hum");
	public static final SoundEvent FLASHBACK_PUB_MURMUR = register("flashback.pub_murmur");
	public static final SoundEvent FLASHBACK_PIZZA_BOX = register("flashback.pizza_box");
	public static final SoundEvent FLASHBACK_LIFT_DING = register("flashback.lift_ding");
	public static final SoundEvent FLASHBACK_PRINTER = register("flashback.printer");
	public static final SoundEvent FLASHBACK_LIGHT_SWITCH = register("flashback.light_switch");

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
