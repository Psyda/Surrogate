package dev.psyda.surrogate.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.shape.VoxelShape;

/** The clock on the wall. Its hands say one thing when the evening starts and another when it has gone. */
public class WallClockBlock extends PropBlock {
	public static final BooleanProperty LATE = BooleanProperty.of("late");

	public WallClockBlock(Settings settings, VoxelShape north) {
		super(settings, Mount.WALL, north);
		setDefaultState(getDefaultState().with(LATE, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(LATE);
	}
}
