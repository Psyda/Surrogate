package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Remembers which machine the belt ate here, so a hull plate can put the same one back. */
public class CorrodedMachineBlockEntity extends BlockEntity {
	@Nullable
	private BlockState original;

	public CorrodedMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CORRODED_MACHINE, pos, state);
	}

	@Nullable
	public BlockState getOriginal() {
		return original;
	}

	public void setOriginal(@Nullable BlockState original) {
		this.original = original;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (original != null) nbt.put("Original", NbtHelper.fromBlockState(original));
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		original = nbt.contains("Original")
				? NbtHelper.toBlockState(registries.getWrapperOrThrow(net.minecraft.registry.RegistryKeys.BLOCK), nbt.getCompound("Original"))
				: null;
	}
}
