package dev.psyda.surrogate.errand;

import dev.psyda.surrogate.survivor.Survivor;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The optional work. None of it is on the critical path and all of it changes the ending, because act six
 * weighs what you actually did rather than what you were told to do.
 *
 * <p>Each entry knows four things: who asks, roughly when it becomes available, where the player has to be
 * to do it, and whether it is finished. The first three are here; the fourth lives in {@link Errands},
 * because "finished" is different for every one of them and most of them are watching the world rather than
 * counting an inventory.
 *
 * <p>The ordinal is the save format — a bitmask of offered, and another of done — so new errands go on the
 * end and never in the middle. Text lives under {@code errand.surrogate.<key>.title} and {@code .brief},
 * and each one's dialogue under {@code errand.surrogate.<key>.<line>}.
 */
public enum Errand {
	/**
	 * The one that pays for being neighbourly. Help Okafor and Sorensen both, come home, and the tiredness
	 * that follows is the game telling you to go to bed. You wake up with company: two chassis in your own
	 * airlock, and an offer to call down the module the drop ship never delivered.
	 */
	HOUSEWARMING("housewarming", Phase.EARLY, Where.HOME, null),
	/** A hot meal, carried the whole way, for a man who has been eating out of foil for a year. */
	HOT_MEAL("hot_meal", Phase.EARLY, Where.SURVIVOR, Survivor.SORENSEN),
	/**
	 * Okafor's survey. She hands over a bio-sampler for the chassis and a disk for the hub, and then wants a
	 * reading of everything on the planet, which turns out to be eight things and one of them is the cat.
	 */
	SURVEY("survey", Phase.MID, Where.SURVIVOR, Survivor.OKAFOR),
	/** Ballast has gone. She is a cat on a planet with no doors she respects. */
	BALLAST("ballast", Phase.MID, Where.CAT, null),
	/** A vent has opened under Tanaka's power run and is cooking the cable. Cap it. */
	VENT_CLEAR("vent_clear", Phase.MID, Where.SURVIVOR, Survivor.TANAKA),
	/**
	 * Reyes could not save him and cannot carry him. He is fifteen metres outside her airlock, where he fell,
	 * and her chassis has been dead since before you got here.
	 */
	BURIAL("burial", Phase.LATE, Where.SURVIVOR, Survivor.REYES),
	/** Three of Brandt's neighbours' machines, eaten by the rain. He has the plates and no way to carry them. */
	CORRODED("corroded", Phase.LATE, Where.SURVIVOR, Survivor.BRANDT),
	/**
	 * Brandt's ark. He has watched these animals for eleven years from behind a window and he is not going to
	 * be the last person who ever sees one. Six crates, six species, and a seat on the rocket for each.
	 */
	ARK("ark", Phase.LATE, Where.SURVIVOR, Survivor.BRANDT);

	private static final Errand[] VALUES = values();

	/** Roughly when one becomes available. The gate is really {@link Errands}; this is for sorting a list. */
	public enum Phase {
		EARLY, MID, LATE
	}

	/** What the test command's teleport aims at. There is no world state in an enum, so this is a tag. */
	public enum Where {
		/** The starter habitat. */
		HOME,
		/** The shelter of {@link Errand#who()}. */
		SURVIVOR,
		/** Wherever Ballast currently is, which is the errand. */
		CAT
	}

	private final String key;
	private final Phase phase;
	private final Where where;
	@Nullable
	private final Survivor who;

	Errand(String key, Phase phase, Where where, @Nullable Survivor who) {
		this.key = key;
		this.phase = phase;
		this.where = where;
		this.who = who;
	}

	public static Errand[] all() {
		return VALUES;
	}

	@Nullable
	public static Errand byKey(String key) {
		for (Errand e : VALUES) {
			if (e.key.equals(key)) return e;
		}
		return null;
	}

	public static String keys() {
		StringBuilder out = new StringBuilder();
		for (Errand e : VALUES) {
			if (out.length() > 0) out.append(", ");
			out.append(e.key);
		}
		return out.toString();
	}

	public String key() {
		return key;
	}

	public Phase phase() {
		return phase;
	}

	public Where where() {
		return where;
	}

	/** Who asks, where that is a survivor. Null for the two nobody in particular asks for. */
	@Nullable
	public Survivor who() {
		return who;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public MutableText title() {
		return Text.translatable("errand.surrogate." + key + ".title");
	}

	public MutableText brief() {
		return Text.translatable("errand.surrogate." + key + ".brief");
	}

	public MutableText line(String which) {
		return Text.translatable("errand.surrogate." + key + "." + which);
	}
}
