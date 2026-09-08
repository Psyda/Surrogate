package dev.psyda.surrogate.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * A light in a house: the pendant over the kitchen table, the panel in the office ceiling. Hangs in the top
 * of its cell, is on or off, and answers to the switch on the wall ({@link LightSwitchBlock}) as well as to a
 * click on the lamp itself.
 */
public class HouseLightBlock extends Block {
	public static final BooleanProperty LIT = Properties.LIT;

	private final VoxelShape shape;

	public HouseLightBlock(Settings settings, VoxelShape shape) {
		super(settings);
		this.shape = shape;
		setDefaultState(getStateManager().getDefaultState().with(LIT, true));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shape;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		set(world, pos, state, !state.get(LIT));
		return ActionResult.SUCCESS;
	}

	public static void set(World world, BlockPos pos, BlockState state, boolean lit) {
		if (state.get(LIT) == lit) return;
		world.setBlockState(pos, state.with(LIT, lit), Block.NOTIFY_ALL);
		world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3f, lit ? 1.3f : 1.0f);
	}
}
