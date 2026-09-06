package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Where the pilot's body stays. Use with an uplink card to bind a chassis, then use it bare-handed to dive.
 */
public class DiveChairBlock extends BlockWithEntity {
	public static final MapCodec<DiveChairBlock> CODEC = createCodec(DiveChairBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontal(VoxelShapes.union(
			Block.createCuboidShape(0, 0, 0, 16, 7, 16),
			Block.createCuboidShape(0, 7, 12, 16, 16, 16),
			Block.createCuboidShape(0, 7, 4, 2, 10, 13),
			Block.createCuboidShape(14, 7, 4, 16, 10, 13)));

	public DiveChairBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<DiveChairBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable
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

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPES.get(state.get(FACING));
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DiveChairBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(world.getBlockEntity(pos) instanceof DiveChairBlockEntity chair) || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}
		if (player.isSneaking()) {
			chair.sendInfo(serverPlayer);
		} else {
			PilotManager.requestDive(serverPlayer, chair);
		}
		return ActionResult.CONSUME;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof DiveChairBlockEntity chair)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (stack.isOf(ModItems.UPLINK_CARD)) {
			if (!world.isClient) {
				ModComponents.UplinkTarget target = stack.get(ModComponents.UPLINK_TARGET);
				if (target == null) {
					player.sendMessage(Text.translatable("message.surrogate.card_blank").formatted(Formatting.RED), true);
				} else {
					chair.setLink(target.robot(), target.name());
					player.sendMessage(Text.translatable("message.surrogate.chair_linked", target.name()), true);
				}
			}
			return ItemActionResult.success(world.isClient);
		}
		if (stack.isOf(ModItems.WRENCH) && player.isSneaking()) {
			if (!world.isClient) {
				chair.setLink(null, "");
				player.sendMessage(Text.translatable("message.surrogate.chair_unlinked"), true);
			}
			return ItemActionResult.success(world.isClient);
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof DiveChairBlockEntity chair) {
				chair.onRemoved();
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}
}
