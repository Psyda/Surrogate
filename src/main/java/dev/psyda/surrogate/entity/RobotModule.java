package dev.psyda.surrogate.entity;

import java.util.Locale;

/**
 * What a chassis can carry in its bays. Four of them are countermeasures, each somebody's answer to one of
 * the things on Sallow: none makes a hazard go away, each turns a wall into a nuisance. The last two are
 * Okafor's and Brandt's, and they are not countermeasures at all — they are how a machine with no hands
 * takes a reading of an animal, and how it picks one up without hurting it.
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
	SHIELD,
	/** Okafor's: a probe and a spectrometer, for the survey. Reads a specimen; never takes one. */
	SAMPLER,
	/** Brandt's: a padded arm and a crate, for the ark. Takes one, alive, and only ever one of each. */
	COLLECTOR;

	private static final RobotModule[] VALUES = values();

	public static RobotModule[] all() {
		return VALUES;
	}

	public String translationKey() {
		return "module.surrogate." + name().toLowerCase(Locale.ROOT);
	}
}
