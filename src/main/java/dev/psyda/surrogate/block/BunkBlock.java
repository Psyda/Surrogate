package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A crew bunk: two blocks long, like the beds it stands beside. A one-block bunk read as a dog bed.
 *
 * <p>It is furniture, not a spawn point. Nobody sleeps through a night on Sallow in a room that needs its
 * scrubber watched, and the pod's own bed is the one that sets a spawn.
 */
public class BunkBlock extends Block {
	public static final MapCodec<BunkBlock> CODEC = createCodec(BunkBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<BedPart> PART = Properties.BED_PART;

	private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontal(Block.createCuboidShape(0, 0, 0, 16, 9, 16));

	public BunkBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART, BedPart.FOOT));
	}

	@Override
	public MapCodec<BunkBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	/** The head goes one step further along the way the player is facing; both halves need room. */
	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction facing = ctx.getHorizontalPlayerFacing();
		BlockPos head = ctx.getBlockPos().offset(facing);
		World world = ctx.getWorld();
		if (!world.getBlockState(head).canReplace(ctx) || !world.getWorldBorder().contains(head)) return null;
		return getDefaultState().with(FACING, facing).with(PART, BedPart.FOOT);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (world.isClient) return;
		BlockPos head = pos.offset(state.get(FACING));
		world.setBlockState(head, state.with(PART, BedPart.HEAD), Block.NOTIFY_ALL);
	}

	/** Breaking either half takes the other with it, the way a bed does. */
	@Override
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == partDirection(state)) {
			return neighborState.isOf(this) && neighborState.get(PART) != state.get(PART)
					? state : net.minecraft.block.Blocks.AIR.getDefaultState();
		}
		return state;
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		// Break the half the player did not hit, without dropping a second item for it.
		if (!world.isClient && player.isCreative()) {
			BlockPos other = pos.offset(partDirection(state));
			BlockState otherState = world.getBlockState(other);
			if (otherState.isOf(this) && otherState.get(PART) != state.get(PART)) {
				world.setBlockState(other, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
			}
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
				|| world.getBlockState(pos.down()).isOf(this);
	}

	/** Which way the other half of this bunk lies. */
	private static Direction partDirection(BlockState state) {
		Direction facing = state.get(FACING);
		return state.get(PART) == BedPart.FOOT ? facing : facing.getOpposite();
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPES.get(state.get(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
