package dev.psyda.surrogate.prologue;

import net.minecraft.text.Text;

/**
 * Everyone the scripts can put on stage: the two people already at Habitat Seven, the crew of the ship that
 * brings the player there, and the pilots riding in its hold. Lines live under {@code crew.surrogate.<key>}.
 * Ordinals are saved with the entities, so new characters go on the end.
 */
public enum Crew {
	HALLORAN("halloran"),
	MARSH("marsh"),
	/** Mags Castellanos, who flies the Provender. */
	CASTELLANOS("castellanos"),
	/** Dr. Nadia Ferreira, flight medical, who fits the port. */
	FERREIRA("ferreira"),
	/** Bram Teague, the ship's engineer. */
	TEAGUE("teague"),
	/** A pilot in transit: a body in a chair, already working on the surface. */
	SLEEPER("sleeper"),
	/** Mikkel Sorensen at Survey Two, who sends his chassis to the pad because his rover is dead. */
	SORENSEN("sorensen"),
	/** Grace Okafor, who patches in from her own failing habitat while the payload is loaded. */
	OKAFOR("okafor");

	private static final Crew[] VALUES = values();
	private final String key;

	Crew(String key) {
		this.key = key;
	}

	public static Crew byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : HALLORAN;
	}

	public String key() {
		return key;
	}

	public String nameKey() {
		return "crew.surrogate." + key + ".name";
	}

	public Text displayName() {
		return Text.translatable(nameKey());
	}

	public Text line(String which) {
		return Text.translatable("crew.surrogate." + key + "." + which);
	}
}
