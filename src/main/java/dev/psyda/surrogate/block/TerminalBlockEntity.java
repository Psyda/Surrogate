package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

/** Which unit on the network this screen belongs to. The unit names the pages under {@code terminal.surrogate.<unit>}. */
public class TerminalBlockEntity extends BlockEntity {
	/** The unit a player-placed terminal is on: the pilot's own manual and not much else. */
	public static final String PERSONAL = "personal";

	private String unit = PERSONAL;

	public TerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TERMINAL, pos, state);
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit == null || unit.isEmpty() ? PERSONAL : unit;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putString("Unit", unit);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		unit = nbt.contains("Unit") ? nbt.getString("Unit") : PERSONAL;
		if (unit.isEmpty()) unit = PERSONAL;
	}
}
