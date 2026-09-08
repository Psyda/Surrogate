package dev.psyda.surrogate.prologue;

import net.minecraft.text.Text;

/**
 * Everyone the scripts can put on stage: the two people already at Habitat Seven, the crew of the ship that
 * brings the player there, and the pilots riding in its hold. Lines live under {@code crew.surrogate.<key>}.
 * Ordinals are saved with the entities, so new characters go on the end.
 *
 * <p>Each of them is either suited or not, and that is a fact about the character rather than about a scene.
 * A pressure suit good for Sallow's air is a settler's tool: the people who live on the surface own one and
 * wear it to cross fifty metres of open ground, and the people who came down on a ship do not. It is the
 * whole reason the player dives — nobody handed them one — so a script that walks somebody out of a door has
 * to ask first. Anyone who cannot go outside has to be sent by radio, by chassis, or not at all.
 */
public enum Crew {
	HALLORAN("halloran", true),
	MARSH("marsh", true),
	/** Mags Castellanos, who flies the Provender. */
	CASTELLANOS("castellanos"),
	/** Dr. Nadia Ferreira, flight medical, who fits the port. */
	FERREIRA("ferreira"),
	/** Bram Teague, the ship's engineer. */
	TEAGUE("teague"),
	/** A pilot in transit: a body in a chair, already working on the surface. */
	SLEEPER("sleeper"),
	/** Mikkel Sorensen at Survey Two, who sends his chassis to the pad because his rover is dead. */
	SORENSEN("sorensen", true),
	/** Grace Okafor, who patches in from her own failing habitat while the payload is loaded. */
	OKAFOR("okafor", true),
	/** Yuki Tanaka of the Sulfur Works, past the mast's reach, who is a carrier before she is a voice. */
	TANAKA("tanaka", true),
	/**
	 * Whoever was in the room with you on Earth. Deliberately not a name and deliberately not a face: the
	 * flashback is the player inventing their own last night, and a figure with features would be the game
	 * telling them who it was.
	 */
	FIGURE("figure"),
	/** Whoever was at the next desk at eleven at night. Same rule as the figure: no face, no name. */
	COWORKER("coworker"),
	/** Whoever was behind the bar. */
	BARMAN("barman");

	private static final Crew[] VALUES = values();
	private final String key;
	private final boolean suited;

	Crew(String key) {
		this(key, false);
	}

	Crew(String key, boolean suited) {
		this.key = key;
		this.suited = suited;
	}

	public static Crew byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : HALLORAN;
	}

	public String key() {
		return key;
	}

	/**
	 * Whether this one owns a suit that holds in Sallow's air, and so can be walked out of a door.
	 *
	 * <p>Everybody who lives down here owns one, and docs/DESIGN-campaign.md ("Marsh's suit") says which:
	 * Halloran, Sorensen and Okafor have contractor-issue Terns, Marsh has the company Kestrel he was never
	 * supposed to need, and Tanaka runs the Sulfur Works on foot. The Provender's crew never landed and never
	 * had a reason to. Neither did the player, which is the point of them.
	 */
	public boolean suited() {
		return suited;
	}

	/** Whether this is one of the flashback's people: somebody being invented, with no face to fix on. */
	public boolean faceless() {
		return this == FIGURE || this == COWORKER || this == BARMAN;
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
