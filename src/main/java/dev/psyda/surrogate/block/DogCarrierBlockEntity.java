package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModComponents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * What is in the carrier: a whole saved animal, or nothing. Rides on the item as a component when the
 * carrier is picked up, which is the entire trick of smuggling a dog off a planet in a suitcase.
 */
public class DogCarrierBlockEntity extends BlockEntity {
	@Nullable
	private NbtCompound pet;

	public DogCarrierBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DOG_CARRIER, pos, state);
	}

	@Nullable
	public NbtCompound getPet() {
		return pet;
	}

	public void setPet(@Nullable NbtCompound pet) {
		this.pet = pet;
		markDirty();
	}

	public boolean isOccupied() {
		return pet != null;
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		pet = nbt.contains("Pet", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("Pet") : null;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		if (pet != null) nbt.put("Pet", pet.copy());
	}

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		NbtCompound carried = components.get(ModComponents.PET_DATA);
		pet = carried == null ? null : carried.copy();
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		if (pet != null) builder.add(ModComponents.PET_DATA, pet.copy());
	}

	@Override
	public void removeFromCopiedStackNbt(NbtCompound nbt) {
		super.removeFromCopiedStackNbt(nbt);
		nbt.remove("Pet");
	}
}
