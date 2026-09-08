package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import dev.psyda.surrogate.world.Valleys;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The bridge over the Rift, laid a course at a time off a span anchor the way the pad is laid off a stake.
 *
 * <p>Five wide, because the hull is five wide and a deck a crawler cannot get both tracks onto is a
 * footbridge. The deck sits at the anchor's own level and runs dead ahead on the anchor's bearing, so where
 * it goes is a decision the player made when they planted the block, and a badly aimed anchor produces a
 * badly aimed bridge rather than a refusal.
 *
 * <p>None of this is required. Four hundred blocks of plating laid by hand crosses the same gap, and the
 * crawler cannot tell the difference — the kit is a way of spending an afternoon's materials instead of an
 * afternoon.
 */
public final class SpanBuilder {
	/** The deck's width, in blocks. Odd, and centred on the anchor. */
	public static final int WIDTH = 5;
	public static final int HALF = WIDTH / 2;
	/** How much deck one course lays. */
	public static final int COURSE = 4;
	/** The furthest a kit will look for the far lip. Sallow's narrows run sixteen to twenty-five. */
	public static final int MAX_SPAN = 48;
	/** Blocks of nothing that make a gap rather than a step. */
	public static final int MIN_GAP = 3;
	/** How far below or above deck level the far lip may be and still count as somewhere to land. */
	private static final int LANDING_SLACK = 6;
	/** How far below the deck a column has to be before it counts as the void rather than as a slope. */
	private static final int DROP = 10;
	/** How far a survey looks around the narrows for somewhere a deck could actually leave from. */
	public static final int SURVEY_SPREAD = 24;

	private SpanBuilder() {
	}

	/**
	 * How far the gap in front of {@code anchor} is, in blocks of deck, or 0 when there is nothing in front
	 * of it worth spanning.
	 *
	 * <p>A gap is not "air": it is ground, then no ground, then ground again. The middle has to be at least
	 * {@link #MIN_GAP} wide or this is a kerb, and the far side has to come back up to within
	 * {@link #LANDING_SLACK} of the deck or the bridge ends in a wall.
	 *
	 * <p>It reads the heightmap rather than probing blocks. The chasm is thirty-four deep and its two lips
	 * are rarely level with each other, so "is there a solid block within two of the deck" answers no all the
	 * way across a perfectly good crossing and then no again on the far lip, which reads as no crossing at all.
	 *
	 * <p>And it reads the ocean floor rather than the motion-blocking surface, because the chasm cuts well
	 * below sea level and fills. A surface heightmap answers sixty-three all the way across a twenty block
	 * chasm with thirty feet of water in it, which reads as flat ground and no crossing anywhere.
	 */
	public static int measure(ServerWorld world, BlockPos anchor, Direction facing) {
		int deckY = anchor.getY();
		int gap = 0;
		for (int i = 1; i <= MAX_SPAN; i++) {
			BlockPos ahead = anchor.offset(facing, i);
			int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR, ahead.getX(), ahead.getZ());
			if (floor <= deckY - DROP) {
				gap++;
				continue;
			}
			// Ground of some sort. It is a landing only if there was a real gap behind it and it is level
			// with the deck; a wall the deck would end against is not somewhere to land.
			if (gap >= MIN_GAP && Math.abs(floor - deckY) <= LANDING_SLACK) return i - 1;
			// Otherwise it is the shoulder on the way down, or a ledge partway across. Neither is a gap and
			// neither is the far side, so the count starts again. The chasm's near lip falls away over five
			// or six blocks, and without this the first of those reads as "ground again" and every crossing
			// on the map measures as nothing.
			gap = 0;
		}
		return 0;
	}

	/**
	 * Somewhere within {@code spread} of {@code near} on this bearing that a deck could actually leave from,
	 * or null when there is nowhere.
	 *
	 * <p>The narrows are found off the terrain noise, which knows where the chasm is and nothing at all about
	 * where the ground is: the point it hands back can easily be on a cliff shoulder twenty blocks above the
	 * floor, from which every direction is a drop and no span measures. So a survey looks around a bit,
	 * which is what a survey is.
	 */
	@Nullable
	public static BlockPos anchorNear(ServerWorld world, BlockPos near, Direction facing, int spread) {
		Valleys.Masks masks = Valleys.masks(world);
		Direction side = facing.rotateYClockwise();
		// The lip is a slope, not an edge. A candidate already six blocks down it is on the chasm wall, and
		// a deck laid from there sits in a slot with the valley floor above it on both sides; so the far end
		// of the search area is taken as the real ground level and anything well below it is passed over.
		BlockPos rimAt = near.offset(facing, -spread);
		int rim = world.getTopY(Heightmap.Type.OCEAN_FLOOR, rimAt.getX(), rimAt.getZ());
		for (int back = 0; back <= spread; back += 2) {
			for (int lateral = 0; lateral <= spread; lateral += 4) {
				for (int sign = 1; sign >= -1; sign -= 2) {
					if (lateral == 0 && sign < 0) continue;
					BlockPos at = near.offset(facing, -back).offset(side, lateral * sign);
					// On the lip, not down in it. Without this the search happily walks twenty blocks into
					// the chasm, finds the far wall the same distance away again, and lays a deck along the
					// bottom of the thing it was supposed to cross.
					if (masks != null && Valleys.classify(masks, at.getX(), at.getZ()) != Valleys.Kind.FLOOR) continue;
					BlockPos ground = new BlockPos(at.getX(),
							world.getTopY(Heightmap.Type.OCEAN_FLOOR, at.getX(), at.getZ()), at.getZ());
					if (ground.getY() < rim - 3) continue;
					if (measure(world, ground, facing) > 0) return ground;
				}
			}
		}
		return null;
	}

	/** An anchor the survey found: where it goes, which way it points and how far it has to reach. */
	public record Survey(BlockPos anchor, Direction facing, int length) {
	}

	/**
	 * Looks for somewhere near {@code near} that a deck could leave from, trying {@code preferred} first and
	 * then the other three cardinals. Null when none of them has a gap in front of it.
	 */
	@Nullable
	public static Survey survey(ServerWorld world, BlockPos near, Direction preferred, int spread) {
		BlockPos found = anchorNear(world, near, preferred, spread);
		if (found != null) return new Survey(found, preferred, measure(world, found, preferred));
		for (Direction facing : Direction.Type.HORIZONTAL) {
			if (facing == preferred) continue;
			found = anchorNear(world, near, facing, spread);
			if (found != null) return new Survey(found, facing, measure(world, found, facing));
		}
		return null;
	}

	/** How many courses a gap of {@code length} takes, rounded up. Always at least one. */
	public static int courses(int length) {
		return Math.max(1, (length + COURSE - 1) / COURSE);
	}

	/**
	 * Lays course {@code course} (one-based) of a span of {@code length} blocks. Returns the number of deck
	 * blocks it actually put down, which is zero when the course is past the end of the span.
	 */
	public static int lay(ServerWorld world, BlockPos anchor, Direction facing, int length, int course) {
		Direction side = facing.rotateYClockwise();
		BlockState deck = ModBlocks.DECK_PLATING.getDefaultState();
		BlockState rail = ModBlocks.HANDRAIL.getDefaultState();
		int from = (course - 1) * COURSE + 1;
		int to = Math.min(length, course * COURSE);
		int laid = 0;
		for (int i = from; i <= to; i++) {
			BlockPos centre = anchor.offset(facing, i);
			for (int w = -HALF; w <= HALF; w++) {
				BlockPos pos = centre.offset(side, w);
				world.setBlockState(pos, deck, Block.NOTIFY_LISTENERS);
				laid++;
				// Headroom, so a deck laid across a ledge is not a tunnel a hull cannot get into.
				for (int up = 1; up <= 4; up++) {
					BlockPos above = pos.up(up);
					if (up <= 1 && Math.abs(w) == HALF) {
						world.setBlockState(above, rail, Block.NOTIFY_LISTENERS);
						continue;
					}
					if (!world.getBlockState(above).isAir()) world.setBlockState(above, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
			world.spawnParticles(ParticleTypes.CRIT, centre.getX() + 0.5, centre.getY() + 1.2, centre.getZ() + 0.5, 8, 1.6, 0.2, 1.6, 0.02);
		}
		if (laid > 0) {
			world.playSound(null, anchor, SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, SoundCategory.BLOCKS, 1.0f, 0.7f);
			world.playSound(null, anchor, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.7f, 0.8f);
		}
		// The last course walks the deck onto whatever the far lip actually is, so the bridge ends in ground
		// and not in a one-block wall nobody can drive over.
		if (to >= length) ramp(world, anchor, facing, length);
		return laid;
	}

	/** Cuts the far lip back to deck level for a hull's length, and fills a step down. */
	private static void ramp(ServerWorld world, BlockPos anchor, Direction facing, int length) {
		Direction side = facing.rotateYClockwise();
		BlockState deck = ModBlocks.DECK_PLATING.getDefaultState();
		for (int i = length + 1; i <= length + 4; i++) {
			BlockPos centre = anchor.offset(facing, i);
			for (int w = -HALF; w <= HALF; w++) {
				BlockPos pos = centre.offset(side, w);
				BlockPos under = pos.down();
				if (!world.getBlockState(under).isSolidBlock(world, under)) world.setBlockState(under, deck, Block.NOTIFY_LISTENERS);
				for (int up = 0; up <= 3; up++) {
					BlockPos clear = pos.up(up);
					if (!world.getBlockState(clear).isAir()) world.setBlockState(clear, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}
}
