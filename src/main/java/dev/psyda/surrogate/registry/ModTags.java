package dev.psyda.surrogate.registry;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;

public final class ModTags {
	/** Biomes whose open air poisons an unprotected body. */
	public static final TagKey<Biome> TOXIC = TagKey.of(RegistryKeys.BIOME, Surrogate.id("toxic"));
	/** Everything the mining drill can break: pickaxe plus shovel blocks. */
	public static final TagKey<Block> DRILL_MINEABLE = TagKey.of(RegistryKeys.BLOCK, Surrogate.id("mineable/drill"));
	/** Items that foul the air of a sealed room while carried or lying inside it. */
	public static final TagKey<Item> CAUSTIC_ITEMS = TagKey.of(RegistryKeys.ITEM, Surrogate.id("caustic"));
	/** Blocks that foul the air of a sealed room they border. */
	public static final TagKey<Block> CAUSTIC_BLOCKS = TagKey.of(RegistryKeys.BLOCK, Surrogate.id("caustic"));

	private ModTags() {
	}
}
