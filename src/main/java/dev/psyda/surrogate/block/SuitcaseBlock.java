package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * The suitcase. A {@link HouseContainerBlock} that knows when it is lying on a bed, so it can sit on the
 * mattress rather than float a hand above it: a bed is nine sixteenths high and the case drops to meet it.
 */
public class SuitcaseBlock extends HouseContainerBlock {
	public static final BooleanProperty ON_BED = BooleanProperty.of("on_bed");

	private final VoxelShape onBed;
	private final MapCodec<? extends SuitcaseBlock> codec;

	public SuitcaseBlock(Settings settings, VoxelShape north, VoxelShape onBed) {
		super(settings, north);
		this.onBed = onBed;
		this.codec = createCodec(s -> new SuitcaseBlock(s, north, onBed));
		setDefaultState(getDefaultState().with(ON_BED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return codec;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(ON_BED);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = super.getPlacementState(ctx);
		return state == null ? null : state.with(ON_BED, isBed(ctx.getWorld(), ctx.getBlockPos().down()));
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		return direction == Direction.DOWN ? state.with(ON_BED, neighborState.getBlock() instanceof BedBlock) : state;
	}

	private static boolean isBed(BlockView world, BlockPos pos) {
		return world.getBlockState(pos).getBlock() instanceof BedBlock;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(ON_BED) ? onBed : super.getOutlineShape(state, world, pos, context);
	}
}
