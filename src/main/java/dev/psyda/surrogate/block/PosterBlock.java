package dev.psyda.surrogate.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

/**
 * A hull plate with something printed on its room-facing side: a company poster, a chart, a console screen.
 * A full cube, so it is airtight and can be part of a wall. The scripts pick the print; a player who crafts
 * one gets whatever is on the item.
 */
public class PosterBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<Print> PRINT = EnumProperty.of("print", Print.class);

	/** What is on the plate. The texture is {@code block/poster_<name>.png}. */
	public enum Print implements StringIdentifiable {
		BODY_TOP("body_top"),
		BODY_BOTTOM("body_bottom"),
		SALLOW_TOP("sallow_top"),
		SALLOW_BOTTOM("sallow_bottom"),
		PROVENDER("provender"),
		CONSOLE("console"),
		CONSOLE_ALERT("console_alert"),
		MANIFEST("manifest");

		private final String name;

		Print(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	public PosterBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PRINT, Print.BODY_TOP));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, PRINT);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
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
