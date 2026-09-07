package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.survivor.SurvivorManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * The working half of a relay mast: a small buffer that anything can push into and a trickle out of it every
 * tick. While the trickle is paid the mast reports itself to the radio; the tick it cannot pay, the band
 * drops and whoever was talking through it stops being heard.
 */
public class RelayMastBlockEntity extends BlockEntity {
	private final SimpleEnergyStorage energy;
	private boolean live;

	public RelayMastBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RELAY_MAST, pos, state);
		this.energy = new SimpleEnergyStorage(buffer(), 1000, 0) {
			@Override
			protected void onFinalCommit() {
				markDirty();
			}
		};
	}

	/** A minute of running, so an hour of cloud is not an hour of silence. */
	private static long buffer() {
		return Math.max(1000L, Surrogate.CONFIG.relayMastDrainPerTick * 1200L);
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	/** True while the mast has the charge to repeat. */
	public boolean isLive() {
		return live;
	}

	public int getCharge() {
		return energy.getCapacity() <= 0 ? 0 : (int) (energy.getAmount() * 100 / energy.getCapacity());
	}

	public static void tick(World world, BlockPos pos, BlockState state, RelayMastBlockEntity mast) {
		if (!(world instanceof ServerWorld)) return;
		int drain = Math.max(0, Surrogate.CONFIG.relayMastDrainPerTick);
		boolean live = mast.energy.getAmount() >= drain;
		if (live && drain > 0) {
			mast.energy.amount -= drain;
			mast.markDirty();
		}
		if (live != mast.live) {
			mast.live = live;
			SurvivorManager.mastPowered(pos, live);
		}
	}

	/** Broken or unloaded: the band drops here rather than leaving the radio talking to a ghost. */
	@Override
	public void markRemoved() {
		if (live) SurvivorManager.mastPowered(getPos(), false);
		live = false;
		super.markRemoved();
	}

	public void sendInfo(ServerPlayerEntity player) {
		player.sendMessage(Text.translatable(live ? "message.surrogate.relay_mast.live" : "message.surrogate.relay_mast.dark",
				Surrogate.CONFIG.relayMastRange, getCharge()), false);
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
