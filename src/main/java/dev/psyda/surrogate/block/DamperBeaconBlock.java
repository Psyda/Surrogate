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
 * A damper bolted to the floor of a mine instead of a chassis. Powered, it holds the rock quiet for a few
 * dozen blocks, which is how a fixed tellurium face gets worked at all; unpowered it is a lump of copper.
 */
public class DamperBeaconBlock extends BlockWithEntity {
	public static final MapCodec<DamperBeaconBlock> CODEC = createCodec(DamperBeaconBlock::new);

	public DamperBeaconBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<DamperBeaconBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DamperBeaconBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.DAMPER_BEACON, DamperBeaconBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof DamperBeaconBlockEntity beacon && player instanceof ServerPlayerEntity serverPlayer) {
			beacon.sendInfo(serverPlayer);
		}
		return ActionResult.CONSUME;
	}
}
