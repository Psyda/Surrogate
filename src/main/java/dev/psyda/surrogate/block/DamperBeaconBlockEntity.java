package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.hazard.Borers;
import dev.psyda.surrogate.registry.ModBlockEntities;
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
 * Tanaka's damper with a cable behind it instead of a chassis. It sings into the rock for as long as it is
 * paid for; the tick the buffer runs out the site goes back to being a dinner bell.
 */
public class DamperBeaconBlockEntity extends BlockEntity {
	private final SimpleEnergyStorage energy;
	private boolean singing;

	public DamperBeaconBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DAMPER_BEACON, pos, state);
		this.energy = new SimpleEnergyStorage(buffer(), 1000, 0) {
			@Override
			protected void onFinalCommit() {
				markDirty();
			}
		};
	}

	/** A minute of tone, so a solar site rides out a cloud without waking anything. */
	private static long buffer() {
		return Math.max(1000L, Surrogate.CONFIG.damperBeaconDrainPerTick * 1200L);
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	public boolean isSinging() {
		return singing;
	}

	public int getCharge() {
		return energy.getCapacity() <= 0 ? 0 : (int) (energy.getAmount() * 100 / energy.getCapacity());
	}

	public static void tick(World world, BlockPos pos, BlockState state, DamperBeaconBlockEntity beacon) {
		if (!(world instanceof ServerWorld server)) return;
		int drain = Math.max(0, Surrogate.CONFIG.damperBeaconDrainPerTick);
		// Nothing is listening on a world with no borers in it, so nothing is spent there either.
		boolean singing = Borers.enabled(server) && beacon.energy.getAmount() >= drain;
		if (singing && drain > 0) {
			beacon.energy.amount -= drain;
			beacon.markDirty();
		}
		if (singing != beacon.singing) {
			beacon.singing = singing;
			if (singing) {
				Borers.addBeacon(server, pos);
			} else {
				Borers.removeBeacon(server, pos);
			}
		}
	}

	/** Broken or unloaded: the tone stops here rather than leaving a dead position in the borers' table. */
	@Override
	public void markRemoved() {
		if (singing && getWorld() instanceof ServerWorld server) Borers.removeBeacon(server, getPos());
		singing = false;
		super.markRemoved();
	}

	public void sendInfo(ServerPlayerEntity player) {
		player.sendMessage(Text.translatable(singing ? "message.surrogate.damper_beacon.singing" : "message.surrogate.damper_beacon.silent",
				Surrogate.CONFIG.damperBeaconRange, getCharge()), false);
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
