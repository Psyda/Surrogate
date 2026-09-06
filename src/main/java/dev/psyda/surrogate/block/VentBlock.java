package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** A fissure in the ash dunes that never stops smoking. Purely atmospheric, and a landmark. */
public class VentBlock extends Block {
	public static final MapCodec<VentBlock> CODEC = createCodec(VentBlock::new);

	public VentBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<VentBlock> getCodec() {
		return CODEC;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!world.getBlockState(pos.up()).isAir()) return;
		double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
		double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
		world.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, pos.getY() + 1.0, z, 0.0, 0.08, 0.0);
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, pos.getY() + 1.0, z, 0.0, 0.05, 0.0);
		}
		if (random.nextInt(24) == 0) {
			world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_LAVA_AMBIENT,
					SoundCategory.BLOCKS, 0.3f, 0.6f + random.nextFloat() * 0.3f, false);
		}
	}
}
