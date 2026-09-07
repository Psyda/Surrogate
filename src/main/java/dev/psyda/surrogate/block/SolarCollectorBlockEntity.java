package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.hazard.AcidRain;
import dev.psyda.surrogate.hazard.Hazards;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;
import team.reborn.energy.api.base.SimpleEnergyStorage;

public class SolarCollectorBlockEntity extends BlockEntity {
	private final SimpleEnergyStorage energy;
	private float carry;
	private int lastOutput;

	public SolarCollectorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOLAR_COLLECTOR, pos, state);
		this.energy = new SimpleEnergyStorage(Surrogate.CONFIG.solarBufferCapacity, 0, 1000) {
			@Override
			protected void onFinalCommit() {
				markDirty();
			}
		};
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	/** Output this tick in energy units, before rounding. Zero at night, under a roof, or in heavy rain. */
	private float output(World world) {
		if (!world.isSkyVisible(pos.up())) return 0f;
		long time = world.getTimeOfDay() % 24000L;
		double sun = Math.cos((time - 6000L) * (2.0 * Math.PI / 24000.0));
		if (sun <= 0.0) return 0f;
		float weather = 1f - world.getRainGradient(1f) * 0.7f;
		// A magnetic storm all but stops the panel, so a base runs on its buffer until it passes, and the
		// belt takes its own cut: a panel it has been eating makes less every day it stands out in it.
		if (world instanceof ServerWorld server) {
			float storm = Hazards.storm(server);
			if (storm > 0f) weather *= 1f - storm * (1f - (float) Surrogate.CONFIG.stormSolarFactor);
			weather *= AcidRain.output(server, pos);
		}
		return (float) (Surrogate.CONFIG.solarOutputPerTick * sun * weather);
	}

	public static void tick(World world, BlockPos pos, BlockState state, SolarCollectorBlockEntity solar) {
		if (world.isClient) return;
		SurrogateConfig cfg = Surrogate.CONFIG;
		float output = solar.output(world);
		solar.carry += output;
		int whole = (int) solar.carry;
		if (whole > 0) {
			solar.carry -= whole;
			solar.energy.amount = Math.min(solar.energy.getCapacity(), solar.energy.getAmount() + whole);
			solar.markDirty();
		}
		solar.lastOutput = Math.round(output);

		if (solar.energy.getAmount() > 0) {
			for (Direction direction : Direction.values()) {
				EnergyStorage target = EnergyStorage.SIDED.find(world, pos.offset(direction), direction.getOpposite());
				if (target == null || !target.supportsInsertion()) continue;
				try (Transaction transaction = Transaction.openOuter()) {
					EnergyStorageUtil.move(solar.energy, target, cfg.dockMaxInsertPerTick, transaction);
					transaction.commit();
				}
				if (solar.energy.getAmount() <= 0) break;
			}
		}

		boolean lit = output > 0.05f;
		if (world.getTime() % 20 == 0 && state.get(SolarCollectorBlock.LIT) != lit) {
			world.setBlockState(pos, state.with(SolarCollectorBlock.LIT, lit), Block.NOTIFY_ALL);
		}
	}

	public void sendInfo(ServerPlayerEntity player) {
		int percent = energy.getCapacity() <= 0 ? 0 : (int) (energy.getAmount() * 100 / energy.getCapacity());
		player.sendMessage(Text.translatable("message.surrogate.solar_info", lastOutput, percent), false);
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
