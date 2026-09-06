package dev.psyda.surrogate.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.TransparentBlock;

/** Glass in a frame. Airtight like any full block, so it is how a sealed base gets windows and skylights. */
public class ReinforcedGlassBlock extends TransparentBlock {
	public static final MapCodec<ReinforcedGlassBlock> CODEC = createCodec(ReinforcedGlassBlock::new);

	public ReinforcedGlassBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends TransparentBlock> getCodec() {
		return CODEC;
	}
}
