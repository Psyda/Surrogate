package dev.psyda.surrogate.entity;

/**
 * The paint a chassis wears. The player's is the company grey everyone is issued; the rest belong to people
 * who are not standing next to you, so a chassis working on a pad reads at a glance as somebody else's.
 *
 * <p>Ordinals are saved with the entity, so new colours go on the end.
 */
public enum RobotPaint {
	/** Company issue: the one the player dives into. */
	PLAYER("robot"),
	/** Halloran's, at Site Two. Amber, and scuffed from four years of it. */
	HALLORAN("robot_halloran"),
	/** Sorensen's, at Survey Two. Green, because his rover was. */
	SORENSEN("robot_sorensen"),
	/** Okafor's, at Habitat Four. */
	OKAFOR("robot_okafor"),
	/** The company representative's. Navy, and cleaner than it has any right to be. */
	MARSH("robot_marsh");

	private static final RobotPaint[] VALUES = values();
	private final String texture;

	RobotPaint(String texture) {
		this.texture = texture;
	}

	public static RobotPaint byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : PLAYER;
	}

	/** The texture file name under {@code textures/entity/}, without the extension. */
	public String texture() {
		return texture;
	}
}
