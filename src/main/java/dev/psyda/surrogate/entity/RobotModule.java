package dev.psyda.surrogate.entity;

import java.util.Locale;

/**
 * The four countermeasures a chassis can carry, each one somebody's answer to one of the things on Sallow.
 * None of them makes a hazard go away; each turns a wall into a nuisance.
 *
 * <p>Ordinals are the bit positions in the chassis' module mask and are saved, so new modules go on the end.
 */
public enum RobotModule {
	/** Sorensen's: doubles the link and the radio, which is how Tanaka gets answered at all. */
	RELAY,
	/** Tanaka's: sings into the rock at the frequency borers steer by, so the drill counts for a fraction. */
	DAMPER,
	/** Brandt's pattern, cut down to a chassis: the belt's rain takes the coating instead of the hull. */
	COATING,
	/** Reyes': the storm still comes, but the picture holds together for twice as long. */
	SHIELD;

	private static final RobotModule[] VALUES = values();

	public static RobotModule[] all() {
		return VALUES;
	}

	public String translationKey() {
		return "module.surrogate." + name().toLowerCase(Locale.ROOT);
	}
}
