package dev.psyda.surrogate.entity;

import java.util.Locale;

/**
 * What can be bolted to the crawler's hull. There is one of these for now and the mask has room for
 * thirty-one more, which is the whole reason it is a mask.
 *
 * <p>Ordinals are the bit positions and are saved, so new modules go on the end.
 */
public enum CrawlerModule {
	/** Brandt's sloped ceramic, in panels: the belt's rain runs off the hull instead of into it. */
	CLADDING;

	private static final CrawlerModule[] VALUES = values();

	public static CrawlerModule[] all() {
		return VALUES;
	}

	public String translationKey() {
		return "module.surrogate.crawler." + name().toLowerCase(Locale.ROOT);
	}
}
