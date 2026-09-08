package dev.psyda.surrogate.flashback;

import dev.psyda.surrogate.prologue.Crew;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The three answers to the first question — where were you — and, for each, the two questions somebody in
 * that room asks and the three ways each can be answered.
 *
 * <p>Nothing here is true until the player says it is. The flashback does not know where you were the night
 * before you shipped; it asks, and whatever you walk towards becomes what happened. That is the whole
 * conceit, so the enum is the memory rather than a lookup of one.
 *
 * <p>The second question in every room is also the reason you were there, which is why its three answers
 * are the three reasons: the answer to "and after?" is the reason you were at home; the answer to "why sign
 * it?" is the reason you were at the office. The ordinal is the save format. New places go on the end.
 */
public enum DreamPlace {
	HOME("home", Crew.FIGURE, "return", "money", "gone"),
	WORK("work", Crew.COWORKER, "money", "nothing", "see"),
	BAR("bar", Crew.BARMAN, "laugh", "truth", "nothing");

	private static final DreamPlace[] VALUES = values();

	private final String key;
	private final Crew who;
	private final String[] reasons;

	DreamPlace(String key, Crew who, String... reasons) {
		this.key = key;
		this.who = who;
		this.reasons = reasons;
	}

	@Nullable
	public static DreamPlace byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : null;
	}

	@Nullable
	public static DreamPlace byKey(String key) {
		for (DreamPlace place : VALUES) {
			if (place.key.equals(key)) return place;
		}
		return null;
	}

	public static DreamPlace[] all() {
		return VALUES;
	}

	public String key() {
		return key;
	}

	/** Whoever is in the room with you: the figure in the kitchen, the coworker, the barman. */
	public Crew who() {
		return who;
	}

	public int reasonCount() {
		return reasons.length;
	}

	public String reasonKey(int which) {
		return reasons[Math.floorMod(which, reasons.length)];
	}

	/** The room's own name, for the log and the ending's reckoning. */
	public Text displayName() {
		return Text.translatable("flashback.surrogate.place." + key);
	}

	public Text reasonName(int which) {
		return Text.translatable("flashback.surrogate.reason." + key + "." + reasonKey(which));
	}

	/** A line under {@code cinematic.surrogate.flashback.<key>.<which>}. */
	public String line(String which) {
		return "cinematic.surrogate.flashback." + key + "." + which;
	}

	// ------------------------------------------------------------------ what they ask

	/** How many questions each place asks, and how many ways there are to answer one. */
	public static final int QUESTIONS = 2;
	public static final int OPTIONS = 3;

	/** What an answer is filed under, for the rest of the game to read. */
	public String askId(int which) {
		return key + "." + which;
	}

	public String askPrompt(int which) {
		return "cinematic.surrogate.flashback.ask." + key + "." + which;
	}

	public String askOption(int which, int option) {
		return askPrompt(which) + "." + option;
	}

	/** Every option for one question, in order. */
	public java.util.List<String> askOptions(int which) {
		java.util.List<String> out = new java.util.ArrayList<>(OPTIONS);
		for (int i = 0; i < OPTIONS; i++) out.add(askOption(which, i));
		return out;
	}
}
