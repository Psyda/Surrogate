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
	/**
	 * The shell of the flashback's rooms: floors, walls, ceilings, windows and the lamps built into them.
	 * Nothing in this tag can be broken inside the dream, which is what stops a player packing the house
	 * itself and, more to the point, stops them opening a window and walking into two hundred blocks of
	 * void. Everything else in there is theirs if they can carry it.
	 */
	public static final TagKey<Block> DREAM_FIXED = TagKey.of(RegistryKeys.BLOCK, Surrogate.id("dream_fixed"));
	/** Machines the belt's rain eats when they stand in it with nothing over them. */
	public static final TagKey<Block> CORRODIBLE = TagKey.of(RegistryKeys.BLOCK, Surrogate.id("corrodible"));

	private ModTags() {
	}
}
