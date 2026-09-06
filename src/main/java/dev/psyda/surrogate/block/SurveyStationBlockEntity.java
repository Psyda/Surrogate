package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.survey.SurveyNetwork;
import dev.psyda.surrogate.survey.SurveyScan;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The table's own bookkeeping. It holds almost nothing, because the network is walked fresh every time it is
 * asked: what it does keep is the last reach it worked out, so the block can say how far it sees without
 * doing the walk, and so the long-range pillar next to it has somewhere to publish its own range.
 */
public class SurveyStationBlockEntity extends BlockEntity {
	/** How far this table saw the last time anybody asked, for the tooltip and the pillar's readout. */
	private int lastReach;
	/** Extra radius lent by a long-range scanner in the same room, in blocks. Recomputed on a slow clock. */
	private int lent;

	/** Ticks between looking for a pillar. Feeding one is a slow business; noticing it can be too. */
	private static final int RESCAN = 100;
	private int rescanIn;

	public SurveyStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SURVEY_STATION, pos, state);
	}

	/** The table's own disc, before any beacons: its base radius plus whatever a pillar is lending it. */
	public int radius(ServerWorld world) {
		return Surrogate.CONFIG.surveyStationRadius + lent;
	}

	public int lastReach() {
		return lastReach;
	}

	public static void tick(World world, BlockPos pos, BlockState state, SurveyStationBlockEntity station) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (--station.rescanIn > 0) return;
		station.rescanIn = RESCAN;
		int lent = 0;
		// A pillar within a short walk of the table lends it everything the pillar's charge is worth. Two
		// pillars do not stack: the table takes the best one, because the fantasy is one big antenna.
		int look = Surrogate.CONFIG.surveyScannerLinkRange;
		for (BlockPos at : BlockPos.iterateOutwards(pos, look, look / 2, look)) {
			if (!serverWorld.getBlockState(at).isOf(dev.psyda.surrogate.registry.ModBlocks.LONG_RANGE_SCANNER)) continue;
			if (serverWorld.getBlockEntity(at) instanceof LongRangeScannerBlockEntity pillar) {
				lent = Math.max(lent, pillar.range());
			}
		}
		if (lent != station.lent) {
			station.lent = lent;
			station.markDirty();
		}
		// The reach readout only matters when somebody is nearby to read it, and the walk is not free.
		if (serverWorld.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16.0, false) == null) return;
		SurveyNetwork.Result network = SurveyNetwork.walk(serverWorld, pos, station.radius(serverWorld));
		int reach = network.reach(pos);
		if (reach != station.lastReach) {
			station.lastReach = reach;
			station.markDirty();
		}
		// The whole-map achievement is checked here rather than at the pillar, because it is a question about
		// the network and the table is the only thing that knows the network.
		if (SurveyScan.seesEverything(serverWorld, network)) {
			dev.psyda.surrogate.survey.SurveyAdvancement.award(serverWorld, pos);
		}
	}

	@Override
	protected void writeNbt(net.minecraft.nbt.NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putInt("LastReach", lastReach);
		nbt.putInt("Lent", lent);
	}

	@Override
	protected void readNbt(net.minecraft.nbt.NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		lastReach = nbt.getInt("LastReach");
		lent = nbt.getInt("Lent");
	}
}
