package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A light switch by a door. Flicks every {@link HouseLightBlock} in the room around it, where "the room" is
 * everything within a few blocks on the same floor: a house whose rooms are eight blocks across does not
 * need anything cleverer, and a switch that occasionally reaches through a wall is a fault every real house
 * has somewhere.
 */
public class LightSwitchBlock extends PropBlock {
	public static final BooleanProperty ON = Properties.POWERED;
	private static final int REACH = 5;
	private static final int REACH_UP = 2;

	public LightSwitchBlock(Settings settings, VoxelShape north) {
		super(settings, Mount.WALL, north);
		setDefaultState(getDefaultState().with(ON, true));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(ON);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = super.getPlacementState(ctx);
		return state == null ? null : state.with(ON, false);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		boolean on = !state.get(ON);
		world.setBlockState(pos, state.with(ON, on), Block.NOTIFY_ALL);
		world.playSound(null, pos, ModSounds.FLASHBACK_LIGHT_SWITCH, SoundCategory.BLOCKS, 0.6f, on ? 1.0f : 0.9f);
		if (!world.isClient) {
			for (BlockPos near : BlockPos.iterate(pos.add(-REACH, -1, -REACH), pos.add(REACH, REACH_UP, REACH))) {
				BlockState light = world.getBlockState(near);
				if (light.getBlock() instanceof HouseLightBlock) HouseLightBlock.set(world, near.toImmutable(), light, on);
			}
		}
		return ActionResult.SUCCESS;
	}
}
