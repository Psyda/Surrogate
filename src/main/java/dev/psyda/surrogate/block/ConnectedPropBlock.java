package dev.psyda.surrogate.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.Map;

/**
 * Furniture that runs along a wall: the couch, the bar counter, the bath. Authored facing north. Each block
 * looks at its neighbours to the left and right (as seen from the front) and becomes an end, a middle or a
 * piece on its own, so a couch is three blocks long and reads as one couch rather than three chairs.
 *
 * <p>A seat height above zero makes it something you can sit on.
 */
public class ConnectedPropBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<Part> PART = EnumProperty.of("part", Part.class);

	public enum Part implements StringIdentifiable {
		SINGLE("single"), LEFT("left"), MIDDLE("middle"), RIGHT("right");

		private final String name;

		Part(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	private final Map<Direction, VoxelShape> shapes;
	private final double seat;

	public ConnectedPropBlock(Settings settings, VoxelShape north, double seat) {
		super(settings);
		this.shapes = ShapeUtil.horizontal(north);
		this.seat = seat;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART, Part.SINGLE));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
		return connect(ctx.getWorld(), ctx.getBlockPos(), getDefaultState().with(FACING, facing));
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		return direction.getAxis().isHorizontal() ? connect(world, pos, state) : state;
	}

	/** The part this block is, from what is beside it: an end is a block with a neighbour on one side only. */
	private BlockState connect(BlockView world, BlockPos pos, BlockState state) {
		Direction facing = state.get(FACING);
		// From the front, the viewer's left is the block's clockwise side.
		boolean left = same(world, pos.offset(facing.rotateYClockwise()), facing);
		boolean right = same(world, pos.offset(facing.rotateYCounterclockwise()), facing);
		Part part = left && right ? Part.MIDDLE : left ? Part.RIGHT : right ? Part.LEFT : Part.SINGLE;
		return state.with(PART, part);
	}

	private boolean same(BlockView world, BlockPos pos, Direction facing) {
		BlockState other = world.getBlockState(pos);
		return other.isOf(this) && other.get(FACING) == facing;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes.get(state.get(FACING));
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (seat <= 0) return ActionResult.PASS;
		if (!(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity served)) return ActionResult.SUCCESS;
		ChairBlock.sit(server, pos, state.get(FACING), seat, served);
		return ActionResult.CONSUME;
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
