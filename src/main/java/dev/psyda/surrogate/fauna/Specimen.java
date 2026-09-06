package dev.psyda.surrogate.fauna;

/**
 * Everything the bio-sampler can take a reading of, and what the hub's analysis software has to say about it.
 *
 * <p>Two kinds live in here and the difference matters twice. A specimen with {@link #live} true is an
 * animal: Ferreira's survey wants a reading of it, and the endgame collection wants the animal itself, in a
 * bag, in a slot on the rocket. One with {@link #live} false is a reading and nothing more — nobody is
 * bagging a litre of seep water — so it counts towards the survey and never towards the manifest.
 *
 * <p>The ordinal is the save format for both of those bitmasks, so new subjects go on the end. The text
 * lives in the lang file under {@code specimen.surrogate.<key>.name} and {@code .note}, the second being
 * what the terminal prints once the disk is in and a reading has been filed.
 */
public enum Specimen {
	/** The slow one. Ferreira's first and easiest reading, and the one she uses to teach the sampler. */
	TRUNDLE("trundle", true),
	/** The rock that is not a rock. Reading one means getting close enough to a thing that objects to it. */
	SLAGBACK("slagback", true),
	/** The small one that answers you. */
	TOCKER("tocker", true),
	/** The ceiling. Reading one is easy; the trick is doing it without killing it. */
	LANTERN_SLUG("lantern_slug", true),
	/**
	 * What is under the rock. A borer will not hold still to be read, so this one is taken off a live animal
	 * mid-pass, which is exactly as sensible as it sounds and exactly what Ferreira asks for.
	 */
	BORER("borer", true),
	/** Ballast. Off-world, and the only specimen that comes when called. */
	CAT("cat", true),
	/** Standing water out of the belt, which is not water. */
	SEEP_WATER("seep_water", false),
	/** The crust on the pan floors: the closest thing to a plant Sallow has. */
	BIOMATTER("biomatter", false);

	private static final Specimen[] VALUES = values();

	private final String key;
	private final boolean live;

	Specimen(String key, boolean live) {
		this.key = key;
		this.live = live;
	}

	public static Specimen[] all() {
		return VALUES;
	}

	public static Specimen byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : TRUNDLE;
	}

	/** The subjects the endgame manifest wants alive: one seat's worth of crate for each. */
	public static int liveCount() {
		int n = 0;
		for (Specimen s : VALUES) {
			if (s.live) n++;
		}
		return n;
	}

	public String key() {
		return key;
	}

	/** Whether this one can be bagged, or is only ever a reading. */
	public boolean live() {
		return live;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public String nameKey() {
		return "specimen.surrogate." + key + ".name";
	}

	/** What the hub prints about it once the disk is in and the reading is filed. */
	public String noteKey() {
		return "specimen.surrogate." + key + ".note";
	}
}
