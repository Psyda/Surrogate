package dev.psyda.surrogate.block;

import dev.psyda.surrogate.crawler.CrawlerInterior;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * A console in a crawler cabin's wall. Using it stands the player in front of it and hands their movement
 * keys to the hull: the helm drives, the docking console creeps in reverse and couples on a click. Sneak to
 * step away.
 */
public class HelmBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private final int seat;

	public HelmBlock(int seat, Settings settings) {
		super(settings);
		this.seat = seat;
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
		// Already at this console: a click is the lock control (the docking console couples on it).
		dev.psyda.surrogate.Surrogate.LOGGER.debug("Console at {} used by {}", pos.toShortString(), player.getName().getString());
		if (CrawlerInterior.isSeatedAt(serverPlayer, pos)) CrawlerInterior.lockRequested(serverPlayer);
		else CrawlerInterior.sitAt(serverPlayer, pos, state.get(FACING), seat);
		return ActionResult.CONSUME;
	}
}
