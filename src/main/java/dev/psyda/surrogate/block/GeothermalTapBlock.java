package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModSounds;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * The cap that goes on a live geyser: a plug, a turbine and a pair of terminals. It only seats during the
 * quiet part of the cycle, and a vent under one stops erupting, so the sulfur stops with it.
 */
public class GeothermalTapBlock extends BlockWithEntity {
	public static final MapCodec<GeothermalTapBlock> CODEC = createCodec(GeothermalTapBlock::new);

	public GeothermalTapBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<GeothermalTapBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	/** There is nothing to tap anywhere else, so it goes on a geyser or it does not go. */
	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return world.getBlockState(pos.down()).isOf(ModBlocks.GEYSER);
	}

	/** Dig the vent out from under a tap and the tap comes with it. */
	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !neighborState.isOf(ModBlocks.GEYSER)) return Blocks.AIR.getDefaultState();
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (world.isClient) return;
		if (world.getBlockEntity(pos.down()) instanceof GeyserBlockEntity geyser && geyser.cap()) {
			world.playSound(null, pos, ModSounds.TAP_CAP, SoundCategory.BLOCKS, 1.0f, 1.0f);
			tell(placer, "message.surrogate.tap.capped");
			return;
		}
		// The throat is already working. The cap comes straight back off and the vent goes anyway.
		boolean drop = !(placer instanceof PlayerEntity player && player.getAbilities().creativeMode);
		world.breakBlock(pos, drop);
		world.playSound(null, pos, ModSounds.DOCK_ERROR, SoundCategory.BLOCKS, 1.0f, 0.8f);
		tell(placer, "message.surrogate.tap.too_late");
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos.down()) instanceof GeyserBlockEntity geyser) {
			geyser.uncap();
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GeothermalTapBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.GEOTHERMAL_TAP, GeothermalTapBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof GeothermalTapBlockEntity tap && player instanceof ServerPlayerEntity serverPlayer) {
			tap.sendInfo(serverPlayer);
		}
		return ActionResult.CONSUME;
	}

	/** Bleed steam from the seal. A cap on a working vent is never quite tight. */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(3) != 0) return;
		double x = pos.getX() + (random.nextBoolean() ? -0.05 : 1.05);
		double z = pos.getZ() + random.nextDouble();
		world.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 0.35, z, 0.0, 0.02, 0.0);
	}

	private static void tell(@Nullable LivingEntity placer, String key) {
		if (placer instanceof ServerPlayerEntity player) player.sendMessage(Text.translatable(key), false);
	}
}
