package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** A run of pipe along one axis, six wide, flanged at both ends. */
public class PipeBlock extends PillarBlock {
	public static final MapCodec<PipeBlock> CODEC = createCodec(PipeBlock::new);
	private static final VoxelShape Y = Block.createCuboidShape(5, 0, 5, 11, 16, 11);
	private static final VoxelShape X = Block.createCuboidShape(0, 5, 5, 16, 11, 11);
	private static final VoxelShape Z = Block.createCuboidShape(5, 5, 0, 11, 11, 16);

	public PipeBlock(Settings settings) {
		super(settings);
	}

	@Override
	public MapCodec<PipeBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		Direction.Axis axis = state.get(AXIS);
		return axis == Direction.Axis.Y ? Y : axis == Direction.Axis.X ? X : Z;
	}
}
