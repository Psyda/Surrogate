package dev.psyda.surrogate.block;

import dev.psyda.surrogate.flashback.Flashback;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * The board on the wall of the bar. Holds up to three darts; a thrown one that hits its face stays in it, and
 * a click on the board hands them all back.
 */
public class DartboardBlock extends PropBlock {
	public static final int DARTS_MAX = 3;
	public static final IntProperty DARTS = IntProperty.of("darts", 0, DARTS_MAX);

	public DartboardBlock(Settings settings, VoxelShape north) {
		super(settings, Mount.WALL, north);
		setDefaultState(getDefaultState().with(DARTS, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(DARTS);
	}

	/** A dart arrived on the face. @return whether there was room for it */
	public boolean stick(World world, BlockPos pos, BlockState state) {
		int darts = state.get(DARTS);
		if (darts >= DARTS_MAX) return false;
		world.setBlockState(pos, state.with(DARTS, darts + 1), Block.NOTIFY_ALL);
		Flashback.noteDart(pos);
		return true;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		int darts = state.get(DARTS);
		if (darts <= 0) return ActionResult.PASS;
		if (!world.isClient) {
			world.setBlockState(pos, state.with(DARTS, 0), Block.NOTIFY_ALL);
			player.giveItemStack(new ItemStack(ModItems.DART, darts));
		}
		world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 1.2f);
		return ActionResult.SUCCESS;
	}
}
