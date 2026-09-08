package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Eighteen slots in a case, a box, a fridge or a wardrobe: two rows, which is what fits in a suitcase and is
 * enough to make a player choose. Opens with the ordinary chest screen under the name of whatever it is.
 */
public class HouseContainerBlockEntity extends LootableContainerBlockEntity {
	public static final int ROWS = 2;
	public static final int SIZE = 9 * ROWS;

	private DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

	public HouseContainerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.HOUSE_CONTAINER, pos, state);
	}

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	protected DefaultedList<ItemStack> getHeldStacks() {
		return stacks;
	}

	@Override
	protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
		this.stacks = inventory;
	}

	@Override
	protected Text getContainerName() {
		return Text.translatable(getCachedState().getBlock().getTranslationKey());
	}

	@Override
	protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
		return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X2, syncId, playerInventory, this, ROWS);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
		if (!readLootTable(nbt)) Inventories.readNbt(nbt, stacks, registries);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (!writeLootTable(nbt)) Inventories.writeNbt(nbt, stacks, registries);
	}
}
