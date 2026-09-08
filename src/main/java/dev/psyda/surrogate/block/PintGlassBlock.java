package dev.psyda.surrogate.block;

import dev.psyda.surrogate.flashback.Flashback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** A pint on the bar. Full until somebody drinks it, and then a glass. */
public class PintGlassBlock extends PropBlock {
	public static final BooleanProperty FULL = BooleanProperty.of("full");

	public PintGlassBlock(Settings settings, VoxelShape shape) {
		super(settings, Mount.FIXED, shape);
		setDefaultState(getDefaultState().with(FULL, true));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(FULL);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!state.get(FULL)) return ActionResult.PASS;
		world.setBlockState(pos, state.with(FULL, false), Block.NOTIFY_ALL);
		world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.7f, 1.0f);
		if (player instanceof ServerPlayerEntity served) {
			served.getHungerManager().add(1, 0.2f);
			Flashback.noteDrank(served);
		}
		return ActionResult.SUCCESS;
	}
}
