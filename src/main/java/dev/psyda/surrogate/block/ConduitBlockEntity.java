package dev.psyda.surrogate.block;

import dev.psyda.surrogate.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * A small buffer that levels itself against neighbouring conduits and empties into anything else that takes
 * power. Levelling rather than pushing keeps a line of conduits from sloshing energy back and forth.
 */
public class ConduitBlockEntity extends BlockEntity {
	public static final int CAPACITY = 2000;
	public static final int RATE = 1000;

	private final SimpleEnergyStorage energy = new SimpleEnergyStorage(CAPACITY, RATE, RATE) {
		@Override
		protected void onFinalCommit() {
			markDirty();
		}
	};

	public ConduitBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.POWER_CONDUIT, pos, state);
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	public static void tick(World world, BlockPos pos, BlockState state, ConduitBlockEntity conduit) {
		if (world.isClient || conduit.energy.getAmount() <= 0) return;
		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = pos.offset(direction);
			EnergyStorage target = EnergyStorage.SIDED.find(world, neighborPos, direction.getOpposite());
			if (target == null || !target.supportsInsertion()) continue;
			long amount;
			if (world.getBlockEntity(neighborPos) instanceof ConduitBlockEntity other) {
				long difference = conduit.energy.getAmount() - other.energy.getAmount();
				if (difference < 2) continue;
				amount = difference / 2;
			} else {
				amount = RATE;
			}
			try (Transaction transaction = Transaction.openOuter()) {
				EnergyStorageUtil.move(conduit.energy, target, amount, transaction);
				transaction.commit();
			}
			if (conduit.energy.getAmount() <= 0) break;
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putLong("Energy", energy.getAmount());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		energy.amount = Math.min(nbt.getLong("Energy"), energy.getCapacity());
	}
}
