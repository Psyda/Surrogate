package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.survey.SurveyScan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * The pillar's insides: a buffer that anything can push into, and a running total of everything that ever
 * has. The total is what buys range — it is a sunk cost, not a fuel tank, so a scanner that runs dry keeps
 * the range it has already paid for and simply stops climbing.
 *
 * <p>The buffer exists so power can be delivered in whatever lumps the player's grid produces; every tick it
 * empties what is in it into the total. That is the whole mechanism: keep pushing power in, keep seeing
 * further, and there is nothing to configure and nothing to break.
 */
public class LongRangeScannerBlockEntity extends BlockEntity {
	private final SimpleEnergyStorage energy;

	/** Everything ever fed in. This is the number the range is a function of. */
	private long charged;

	public LongRangeScannerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LONG_RANGE_SCANNER, pos, state);
		this.energy = new SimpleEnergyStorage(Surrogate.CONFIG.longRangeScannerBuffer, 4096, 0) {
			@Override
			protected void onFinalCommit() {
				markDirty();
			}
		};
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	public long charge() {
		return charged;
	}

	public int range() {
		return SurveyScan.rangeFor(charged);
	}

	/** A round lump of power to quote in the readout, so "keep feeding it" has a unit attached. */
	public long stepCost() {
		return Math.max(1L, Surrogate.CONFIG.longRangeScannerBuffer);
	}

	public static void tick(World world, BlockPos pos, BlockState state, LongRangeScannerBlockEntity scanner) {
		if (!(world instanceof ServerWorld)) return;
		long taken = scanner.energy.amount;
		if (taken > 0) {
			scanner.energy.amount = 0;
			scanner.charged += taken;
			scanner.markDirty();
		}
		boolean lit = scanner.charged > 0;
		if (lit != state.get(LongRangeScannerBlock.LIT)) {
			world.setBlockState(pos, state.with(LongRangeScannerBlock.LIT, lit), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putLong("Charged", charged);
		nbt.putLong("Buffer", energy.amount);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		charged = nbt.getLong("Charged");
		energy.amount = nbt.getLong("Buffer");
	}
}
