package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.survey.SurveyNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The only thing a beacon does on its own: work out, now and then, whether anybody is listening, and set its
 * lamp accordingly.
 *
 * <p>Slowly. Walking the network costs a chunk scan, and a field of twenty beacons all doing that every tick
 * would be a real cost for a light that changes about twice a game. Five seconds, staggered by position so
 * they do not all check on the same tick.
 */
public class SurveyBeaconBlockEntity extends BlockEntity {
	private static final int CHECK = 100;

	private int checkIn;

	public SurveyBeaconBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SURVEY_BEACON, pos, state);
		// Stagger: a row of beacons planted in one trip would otherwise share a tick forever.
		this.checkIn = Math.floorMod(pos.hashCode(), CHECK);
	}

	public static void tick(World world, BlockPos pos, BlockState state, SurveyBeaconBlockEntity beacon) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (--beacon.checkIn > 0) return;
		beacon.checkIn = CHECK;
		boolean linked = SurveyNetwork.isLinked(serverWorld, pos);
		if (linked != state.get(SurveyBeaconBlock.LINKED)) {
			world.setBlockState(pos, state.with(SurveyBeaconBlock.LINKED, linked), Block.NOTIFY_ALL);
		}
	}
}
