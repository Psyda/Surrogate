package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
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
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parks one chassis, powers it down and charges it. Drive a piloted chassis up to it and use it to dock,
 * use it bare-handed to park an idle chassis standing next to it, sneak-use to eject the parked chassis.
 */
public class ChargingDockBlock extends BlockWithEntity {
	public static final MapCodec<ChargingDockBlock> CODEC = createCodec(ChargingDockBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	private static final Map<Direction, VoxelShape> SHAPES = ShapeUtil.horizontal(VoxelShapes.union(
			Block.createCuboidShape(0, 0, 0, 16, 3, 16),
			Block.createCuboidShape(2, 3, 13, 14, 16, 16),
			Block.createCuboidShape(0, 3, 0, 2, 5, 13),
			Block.createCuboidShape(14, 3, 0, 16, 5, 13)));

	public ChargingDockBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<ChargingDockBlock> getCodec() {
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
		return new ChargingDockBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.CHARGING_DOCK, ChargingDockBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(world.getBlockEntity(pos) instanceof ChargingDockBlockEntity dock) || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.PASS;
		}

		// A pilot driving up to the dock parks the chassis they are in and returns to their body.
		if (player.getVehicle() instanceof RobotEntity ridden && ridden.isPilot(player)) {
			if (dock.hasRobot()) {
				fail(player, "dock_occupied");
				return ActionResult.CONSUME;
			}
			PilotManager.disconnect(serverPlayer, false, Text.translatable("message.surrogate.docked"), false);
			if (!dock.dock(ridden)) fail(player, "dock_failed");
			return ActionResult.CONSUME;
		}

		if (player.isSneaking()) {
			if (!dock.hasRobot()) {
				fail(player, "dock_empty");
			} else if (dock.undock() == null) {
				fail(player, "dock_blocked");
			} else {
				player.sendMessage(Text.translatable("message.surrogate.undocked"), true);
			}
			return ActionResult.CONSUME;
		}

		if (!dock.hasRobot()) {
			List<RobotEntity> nearby = world.getEntitiesByClass(RobotEntity.class, new Box(pos).expand(1.5),
					robot -> !robot.isPiloted() && !robot.hasPassengers());
			if (!nearby.isEmpty()) {
				RobotEntity nearest = nearby.stream().min(Comparator.comparingDouble(robot -> robot.squaredDistanceTo(pos.toCenterPos()))).get();
				if (dock.dock(nearest)) player.sendMessage(Text.translatable("message.surrogate.docked"), true);
				else fail(player, "dock_failed");
				return ActionResult.CONSUME;
			}
		}
		dock.sendInfo(serverPlayer);
		return ActionResult.CONSUME;
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof ChargingDockBlockEntity dock)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		boolean client = world.isClient;
		if (stack.isOf(ModItems.POWER_CELL)) {
			if (!client) {
				int accepted = dock.addEnergy(Surrogate.CONFIG.powerCellEnergy);
				if (accepted <= 0) {
					fail(player, "dock_full");
				} else {
					if (!player.getAbilities().creativeMode) stack.decrement(1);
					player.sendMessage(Text.translatable("message.surrogate.dock_charged", dock.getStoredPercent()), true);
				}
			}
			return ItemActionResult.success(client);
		}
		if (stack.isOf(ModItems.REPAIR_KIT)) {
			if (!client) dock.repairDocked(player, stack);
			return ItemActionResult.success(client);
		}
		if (stack.isOf(ModItems.ROBOT_CHASSIS)) {
			if (!client) {
				if (dock.hasRobot()) {
					fail(player, "dock_occupied");
				} else if (dock.dockFromItem(stack)) {
					if (!player.getAbilities().creativeMode) stack.decrement(1);
					player.sendMessage(Text.translatable("message.surrogate.docked"), true);
				}
			}
			return ItemActionResult.success(client);
		}
		if (stack.isOf(ModItems.UPLINK_CARD)) {
			if (!client) {
				UUID robot = dock.getRobotUuid();
				if (robot == null) {
					fail(player, "dock_empty");
				} else {
					stack.set(ModComponents.UPLINK_TARGET, new ModComponents.UplinkTarget(robot, dock.getRobotName().getString()));
					player.sendMessage(Text.translatable("message.surrogate.card_linked", dock.getRobotName()), true);
				}
			}
			return ItemActionResult.success(client);
		}
		return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock())) {
			if (world.getBlockEntity(pos) instanceof ChargingDockBlockEntity dock && dock.hasRobot()) {
				UUID uuid = dock.getRobotUuid();
				ItemStack chassis = dock.takeAsItem();
				ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, chassis);
				if (uuid != null && world instanceof ServerWorld serverWorld) {
					RobotRegistry.get(serverWorld.getServer()).remove(uuid);
				}
			}
			super.onStateReplaced(state, world, pos, newState, moved);
		}
	}

	private static void fail(PlayerEntity player, String key) {
		player.sendMessage(Text.translatable("message.surrogate." + key).formatted(Formatting.RED), true);
	}
}
