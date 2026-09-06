package dev.psyda.surrogate.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A fitting with no behaviour: crates, lockers, rails, lamps, the furniture of a habitat. Authored facing
 * north; a directional prop turns to face the player, a wall-mounted one hangs flat on the wall it is placed
 * against. Anything not a full cube is non-opaque so the room behind it still lights.
 */
public class PropBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public enum Mount {
		/** One model, every way round. */
		FIXED,
		/** Turns its front to the player. */
		FACING,
		/** Hangs on the wall it is placed against, front out. */
		WALL
	}

	private final Mount mount;
	private final VoxelShape fixedShape;
	@Nullable
	private final Map<Direction, VoxelShape> shapes;

	public PropBlock(Settings settings, Mount mount, VoxelShape north) {
		super(settings);
		this.mount = mount;
		this.fixedShape = north;
		this.shapes = mount == Mount.FIXED ? null : ShapeUtil.horizontal(north);
		if (mount != Mount.FIXED) {
			setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
		}
	}

	/** A prop that fills its block and needs no shape of its own. */
	public PropBlock(Settings settings, Mount mount) {
		this(settings, mount, VoxelShapes.fullCube());
	}

	public Mount getMount() {
		return mount;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		if (mount != Mount.FIXED) builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return switch (mount) {
			case FIXED -> getDefaultState();
			case FACING -> getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
			case WALL -> {
				Direction side = ctx.getSide();
				yield getDefaultState().with(FACING, side.getAxis().isHorizontal() ? side : ctx.getHorizontalPlayerFacing().getOpposite());
			}
		};
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapes == null ? fixedShape : shapes.get(state.get(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return shapes == null ? state : state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return shapes == null ? state : state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
