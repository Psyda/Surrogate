package dev.psyda.surrogate.block;

import dev.psyda.surrogate.network.DocumentPayload;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * A monitor on a desk. Off, showing the contract, or showing the screensaver every screen on the floor
 * falls back to at eleven at night. With the contract up, a click reads it; otherwise a click is the power
 * button.
 */
public class MonitorBlock extends PropBlock {
	public static final EnumProperty<Screen> SCREEN = EnumProperty.of("screen", Screen.class);

	public enum Screen implements StringIdentifiable {
		OFF("off"), CONTRACT("contract"), SAVER("saver");

		private final String name;

		Screen(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	public MonitorBlock(Settings settings, VoxelShape north) {
		super(settings, Mount.FACING, north);
		setDefaultState(getDefaultState().with(SCREEN, Screen.OFF));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(SCREEN);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		Screen screen = state.get(SCREEN);
		if (screen == Screen.CONTRACT) {
			if (player instanceof ServerPlayerEntity served) ReadableBlock.open(served, "contract", DocumentPayload.SCREEN);
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.2f, 1.6f);
			return ActionResult.SUCCESS;
		}
		Screen next = screen == Screen.OFF ? Screen.SAVER : Screen.OFF;
		world.setBlockState(pos, state.with(SCREEN, next), Block.NOTIFY_ALL);
		world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3f, next == Screen.OFF ? 0.8f : 1.2f);
		return ActionResult.SUCCESS;
	}
}
