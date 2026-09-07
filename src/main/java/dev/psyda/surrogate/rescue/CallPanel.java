package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * One tile of the conference call: everybody who is still alive on Sallow, in the order they sit on the
 * screen. Four across and two down, reading left to right and top to bottom, with Marsh in the last corner
 * because that is where the company's man has ended up.
 *
 * <p>The ordinal is the wire format — the call travels as two bitmasks and an index — so new faces go on the
 * end and never in the middle. The face is the head off their own skin, which means the people on the screen
 * are the same people standing in the shelters and not a second set of portraits that could drift from them.
 */
public enum CallPanel {
	HALLORAN("halloran", "crew_halloran"),
	OKAFOR("okafor", "survivor_okafor"),
	SORENSEN("sorensen", "survivor_sorensen"),
	TANAKA("tanaka", "survivor_tanaka"),
	BRANDT("brandt", "survivor_brandt"),
	REYES("reyes", "survivor_reyes"),
	/** Dark from the first frame. Nobody on the call knows why, and that is the scene's last line. */
	NOVAK("novak", "survivor_novak"),
	MARSH("marsh", "crew_marsh");

	private static final CallPanel[] VALUES = values();
	/** Everyone who is expected to answer. Novak is not one of them. */
	public static final int EVERYONE = (1 << VALUES.length) - 1 & ~NOVAK.bit();
	/** The far side of the Rift, which is what a storm takes off the band. */
	public static final int FAR_SIDE = BRANDT.bit() | REYES.bit() | NOVAK.bit();

	public static final int COLUMNS = 4;
	public static final int ROWS = 2;

	private final String key;
	private final String skin;

	CallPanel(String key, String skin) {
		this.key = key;
		this.skin = skin;
	}

	public static CallPanel[] all() {
		return VALUES;
	}

	public static CallPanel byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : HALLORAN;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public String key() {
		return key;
	}

	/** The skin their head is cropped out of: a 64 by 64 sheet, face at (8, 8) and hat at (40, 8). */
	public Identifier skin() {
		return Surrogate.id("textures/entity/" + skin + ".png");
	}

	/**
	 * What the tile is labelled. Halloran and Marsh are crew and everybody else is a survivor, and the two
	 * live under different lang prefixes, so the panel asks here rather than guessing.
	 */
	public String nameKey() {
		return switch (this) {
			case HALLORAN, MARSH -> "crew.surrogate." + key + ".name";
			default -> "survivor.surrogate." + key + ".name";
		};
	}

	public Text displayName() {
		return Text.translatable(nameKey());
	}
}
