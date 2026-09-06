package dev.psyda.surrogate.atmosphere;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * The pocket of air a life support unit is responsible for: every reachable non-airtight cell in front of it.
 * Quality is the fraction of breathable air, 1 being fully scrubbed and 0 being the same as outside.
 */
public final class SealedVolume {
	public final BlockPos origin;
	public LongOpenHashSet cells = new LongOpenHashSet();
	public boolean sealed;
	public int size;
	@Nullable
	public BlockPos leak;
	public float quality;
	public boolean powered;
	/** Bounding box of the cells, for finding what is inside. */
	public Box bounds = new Box(0, 0, 0, 0, 0, 0);
	/** The room as it was the last time it was airtight: what a leaking room still counts as, until it reseals. */
	public LongOpenHashSet sealedCells = new LongOpenHashSet();
	public Box sealedBounds = new Box(0, 0, 0, 0, 0, 0);
	/** Caustic blocks (sand, sulfur, ash) that border the air. */
	public int causticBlocks;
	/** Contamination sources found on the last sample. */
	public int dirtyChassis;
	public int causticItems;
	/** Air quality lost per tick to contamination, applied between samples. */
	public float contaminationDrain;

	public SealedVolume(BlockPos origin) {
		this.origin = origin;
	}

	public boolean contains(BlockPos pos) {
		return cells.contains(pos.asLong());
	}

	public boolean isContaminated() {
		return contaminationDrain > 0f;
	}
}
