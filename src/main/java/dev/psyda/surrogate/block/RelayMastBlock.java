package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Sorensen's answer to a planet that is too big: a mast that hears the pod's band and says it again, another
 * few hundred blocks out. It costs a trickle of power for as long as it stands, and a mast with a flat buffer
 * is a mast nobody is talking through.
 */
public class RelayMastBlock extends BlockWithEntity {
	public static final MapCodec<RelayMastBlock> CODEC = createCodec(RelayMastBlock::new);

	public RelayMastBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<RelayMastBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new RelayMastBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.RELAY_MAST, RelayMastBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof RelayMastBlockEntity mast && player instanceof ServerPlayerEntity serverPlayer) {
			mast.sendInfo(serverPlayer);
		}
		return ActionResult.CONSUME;
	}
}
