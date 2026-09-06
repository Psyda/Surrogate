package dev.psyda.surrogate.block;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.survivor.SurvivorManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * A chassis port on the outside wall of a shelter: a plate with a screen, airtight, that a chassis docks
 * to. Nobody opens an airlock; the chassis and whoever is inside talk through the terminal. The researcher
 * sends the crawler blueprint over it. A person without a chassis gets nothing out of it.
 */
public class ChassisPortBlock extends Block {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public ChassisPortBlock(Settings settings) {
		super(settings);
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
		if (!RobotEntity.isPiloting(player)) {
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.8f, 0.4f);
			player.sendMessage(Text.translatable("message.surrogate.port.needs_chassis").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		SurvivorManager survivors = SurvivorManager.get(serverPlayer.server);
		SurvivorManager.Site site = survivors.siteNear(pos, 8);
		if (site == null) {
			player.sendMessage(Text.translatable("message.surrogate.port.dead").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		survivors.portUsed(serverPlayer, site);
		return ActionResult.CONSUME;
	}
}
