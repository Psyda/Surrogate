package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * A metre of pole with a lamp on top, pushed into the dirt at the edge of what the table can see.
 *
 * <p>Green means it is talking to the station, directly or through another beacon, and the ground around it
 * is on the picture. Red means it is on its own out there and doing nothing at all. There is no menu and no
 * pairing: the light is the whole interface, and the fix for a red one is always to plant another between it
 * and the last green one.
 */
public class SurveyBeaconBlock extends BlockWithEntity {
	public static final MapCodec<SurveyBeaconBlock> CODEC = createCodec(SurveyBeaconBlock::new);
	/** Green when the network reaches it. The only state it has. */
	public static final BooleanProperty LINKED = BooleanProperty.of("linked");

	private static final VoxelShape SHAPE = Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

	public SurveyBeaconBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(LINKED, false));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LINKED);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/**
	 * Pushed into the ground, not stuck to a wall or floated over a hole. The player has to walk it out
	 * there and find somewhere to put it, which is the errand.
	 */
	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		BlockPos below = pos.down();
		return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		return !state.canPlaceAt(world, pos) ? net.minecraft.block.Blocks.AIR.getDefaultState()
				: super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	/** Asking a beacon what it is doing. It knows exactly one thing. */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		boolean linked = state.get(LINKED);
		player.sendMessage(Text.translatable(linked ? "message.surrogate.beacon.linked" : "message.surrogate.beacon.orphan")
				.formatted(linked ? Formatting.GREEN : Formatting.RED), true);
		return ActionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SurveyBeaconBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return validateTicker(type, ModBlockEntities.SURVEY_BEACON, SurveyBeaconBlockEntity::tick);
	}
}
