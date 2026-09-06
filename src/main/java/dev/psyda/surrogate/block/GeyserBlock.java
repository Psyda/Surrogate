package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A fumarole with something still coming up it. Ninety seconds of clock, readable the whole way: quiet, then
 * steam at the lip, then four seconds of rumble, then eight seconds of a scalding column, then quiet again.
 * The column costs a chassis most of its hull and a body all of it, and it leaves sulfur behind.
 */
public class GeyserBlock extends BlockWithEntity {
	public static final MapCodec<GeyserBlock> CODEC = createCodec(GeyserBlock::new);
	public static final EnumProperty<Stage> STAGE = EnumProperty.of("stage", Stage.class);

	/** Where the vent is in its cycle. Two models between the four of them; the rest is particles and sound. */
	public enum Stage implements StringIdentifiable {
		QUIET("quiet"),
		STEAM("steam"),
		RUMBLE("rumble"),
		ERUPTING("erupting");

		private final String name;

		Stage(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return name;
		}
	}

	public GeyserBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(STAGE, Stage.QUIET));
	}

	@Override
	protected MapCodec<GeyserBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(STAGE);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GeyserBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, ModBlockEntities.GEYSER, GeyserBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof GeyserBlockEntity geyser && player instanceof ServerPlayerEntity serverPlayer) {
			geyser.sendInfo(serverPlayer);
		}
		return ActionResult.CONSUME;
	}

	/**
	 * The lip, seen from the outside. The column itself is spawned by the block entity so that what a player
	 * sees and what the server is willing to burn are the same thing.
	 */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!world.getBlockState(pos.up()).isAir()) return;
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
		double y = pos.getY() + 1.0;
		switch (state.get(STAGE)) {
			case QUIET -> {
				world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0.0, 0.03, 0.0);
				if (random.nextInt(30) == 0) {
					world.playSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.BLOCK_LAVA_AMBIENT,
							SoundCategory.BLOCKS, 0.25f, 0.6f + random.nextFloat() * 0.2f, false);
				}
			}
			case STEAM -> {
				world.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 0.0, 0.1, 0.0);
				world.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.05, 0.0);
			}
			case RUMBLE -> {
				world.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.12, 0.0);
				if (random.nextInt(2) == 0) world.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
			}
			case ERUPTING -> {
				int height = Math.max(1, Surrogate.CONFIG.geyserHeight);
				world.addParticle(ParticleTypes.CLOUD, x, y + random.nextDouble() * height, z, 0.0, 0.6, 0.0);
			}
		}
	}
}
