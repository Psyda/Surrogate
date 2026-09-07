package dev.psyda.surrogate.block;

import dev.psyda.surrogate.research.Research;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * A weather stake: a pole, a cup anemometer and a card that fills up over a night. Worth nothing on its own.
 * Planting one tells the wind count where it is, and the count wants four of them, far apart, on ground that
 * is not the same ground twice.
 */
public class SurveyStakeBlock extends PropBlock {
	public SurveyStakeBlock(Settings settings, VoxelShape shape) {
		super(settings, Mount.FIXED, shape);
	}

	/**
	 * A stake needs ground under it. Without this it could be driven into an acid channel, where the flow
	 * washed it out a tick later and the count was left holding a stake that is not there.
	 */
	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
				&& world.getFluidState(pos).isEmpty();
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor,
												   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
		return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (world instanceof ServerWorld server && placer instanceof ServerPlayerEntity player) {
			Research.stakePlanted(server, pos, player);
		}
	}
}
