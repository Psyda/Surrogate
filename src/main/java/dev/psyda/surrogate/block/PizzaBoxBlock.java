package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * The box on the coffee table. Eight slices when it arrives; a click takes one. It stays on the table when it
 * is empty, because that is what happens to pizza boxes.
 */
public class PizzaBoxBlock extends PropBlock {
	public static final int SLICES_MAX = 8;
	public static final IntProperty SLICES = IntProperty.of("slices", 0, SLICES_MAX);

	public PizzaBoxBlock(Settings settings, VoxelShape north) {
		super(settings, Mount.FACING, north);
		setDefaultState(getDefaultState().with(SLICES, SLICES_MAX));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(SLICES);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		int left = state.get(SLICES);
		if (left <= 0) {
			world.playSound(null, pos, ModSounds.FLASHBACK_PIZZA_BOX, SoundCategory.BLOCKS, 0.4f, 0.8f);
			return ActionResult.CONSUME;
		}
		if (!world.isClient) {
			world.setBlockState(pos, state.with(SLICES, left - 1), Block.NOTIFY_ALL);
			player.giveItemStack(new ItemStack(ModItems.PIZZA_SLICE));
		}
		world.playSound(null, pos, ModSounds.FLASHBACK_PIZZA_BOX, SoundCategory.BLOCKS, 0.5f, 1.0f);
		return ActionResult.SUCCESS;
	}
}
