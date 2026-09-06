package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** The bay in a crawler cabin's wall: room for one chassis, waiting to be deployed. */
public class CrawlerBayBlockEntity extends BlockEntity {
	private ItemStack chassis = ItemStack.EMPTY;

	public CrawlerBayBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CRAWLER_BAY, pos, state);
	}

	public ItemStack getStack() {
		return chassis;
	}

	public void setStack(ItemStack stack) {
		this.chassis = stack;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (!chassis.isEmpty()) nbt.put("Chassis", chassis.encode(registries));
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		chassis = nbt.contains("Chassis") ? ItemStack.fromNbt(registries, nbt.get("Chassis")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
	}
}
