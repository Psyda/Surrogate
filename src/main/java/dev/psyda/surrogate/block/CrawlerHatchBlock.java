package dev.psyda.surrogate.block;

import dev.psyda.surrogate.crawler.CrawlerInterior;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The hatch in the back of a crawler cabin. It never opens: using it steps the player out beside the hull,
 * or through the collar into the pod when the hull is coupled. Airtight either way.
 */
public class CrawlerHatchBlock extends Block {
	public CrawlerHatchBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (player instanceof ServerPlayerEntity serverPlayer) CrawlerInterior.leave(serverPlayer, pos);
		return ActionResult.CONSUME;
	}
}
