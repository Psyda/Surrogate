package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The chassis bay in a crawler cabin's wall. Use a chassis on it and the chassis is assembled outside beside
 * the hull, with the cabin's dive chair keyed to it; a chassis that comes back aboard waits in here, and
 * using the empty-handed bay sends it out again.
 */
public class CrawlerBayBlock extends BlockWithEntity {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final MapCodec<CrawlerBayBlock> CODEC = createCodec(CrawlerBayBlock::new);

	public CrawlerBayBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
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
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CrawlerBayBlockEntity(pos, state);
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!stack.isOf(ModItems.ROBOT_CHASSIS)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (world.isClient) return ItemActionResult.SUCCESS;
		dev.psyda.surrogate.Surrogate.LOGGER.info("Crawler bay: used with a chassis at {}", pos.toShortString());
		if (player instanceof ServerPlayerEntity serverPlayer && CrawlerInterior.deploy(serverPlayer, (ServerWorld) world, pos, stack.copy())) {
			if (!player.isCreative()) stack.decrement(1);
		}
		return ItemActionResult.CONSUME;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
		if (!(world.getBlockEntity(pos) instanceof CrawlerBayBlockEntity bay)) return ActionResult.PASS;
		ItemStack held = bay.getStack();
		if (held.isEmpty()) {
			player.sendMessage(Text.translatable("message.surrogate.crawler.bay_empty").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		if (CrawlerInterior.deploy(serverPlayer, (ServerWorld) world, pos, held.copy())) bay.setStack(ItemStack.EMPTY);
		return ActionResult.CONSUME;
	}
}
