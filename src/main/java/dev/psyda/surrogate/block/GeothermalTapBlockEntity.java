package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/**
 * A buffer with a hole in the ground under it. While the vent below is capped it makes
 * {@code geothermalTapPerTick} every tick, day or night and through any weather, and pushes it into whatever
 * it touches the way a solar collector does.
 */
public class GeothermalTapBlockEntity extends BlockEntity {
	/** How often the cap looks down to see whether there is still a vent under it. */
	private static final int CHECK_INTERVAL = 40;

	private final SimpleEnergyStorage energy;
	private boolean live;
	private int check;

	public GeothermalTapBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.GEOTHERMAL_TAP, pos, state);
		this.energy = new SimpleEnergyStorage(Surrogate.CONFIG.geothermalTapCapacity, 0, Surrogate.CONFIG.dockMaxInsertPerTick) {
			@Override
			protected void onFinalCommit() {
				markDirty();
			}
		};
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	public static void tick(World world, BlockPos pos, BlockState state, GeothermalTapBlockEntity tap) {
		if (world.isClient) return;
		SurrogateConfig cfg = Surrogate.CONFIG;
		if (--tap.check <= 0) {
			tap.check = CHECK_INTERVAL;
			tap.refresh(world, pos);
		}
		if (tap.live) {
			long output = Math.max(0, cfg.geothermalTapPerTick);
			long amount = Math.min(tap.energy.getCapacity(), tap.energy.getAmount() + output);
			if (amount != tap.energy.getAmount()) {
				tap.energy.amount = amount;
				tap.markDirty();
			}
		}

		if (tap.energy.getAmount() > 0) {
			for (Direction direction : Direction.values()) {
				EnergyStorage target = EnergyStorage.SIDED.find(world, pos.offset(direction), direction.getOpposite());
				if (target == null || !target.supportsInsertion()) continue;
				try (Transaction transaction = Transaction.openOuter()) {
					EnergyStorageUtil.move(tap.energy, target, cfg.dockMaxInsertPerTick, transaction);
					transaction.commit();
				}
				if (tap.energy.getAmount() <= 0) break;
			}
		}
	}

	/**
	 * Look at what is under the cap. A vent that is quiet gets capped here as well as on placement, so a tap
	 * that came back with a chunk rather than out of a player's hand still holds it shut.
	 */
	private void refresh(World world, BlockPos pos) {
		live = world.getBlockEntity(pos.down()) instanceof GeyserBlockEntity geyser && (geyser.isCapped() || geyser.cap());
	}

	public void sendInfo(ServerPlayerEntity player) {
		if (!live) {
			player.sendMessage(Text.translatable("message.surrogate.tap.dead"), false);
			return;
		}
		int percent = energy.getCapacity() <= 0 ? 0 : (int) (energy.getAmount() * 100 / energy.getCapacity());
		player.sendMessage(Text.translatable("message.surrogate.tap.info", Surrogate.CONFIG.geothermalTapPerTick, percent), false);
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
