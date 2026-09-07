package dev.psyda.surrogate.research;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How far the neighbours' work has got: the three research runs, the range gate at Tanaka, and which of the
 * errands around them the player bothered with. The errands outlive this arc, because the vote in act six
 * weighs every opinion by what was done for the person holding it.
 */
public class ResearchState extends PersistentState {
	private static final Type<ResearchState> TYPE = new Type<>(ResearchState::new, ResearchState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static final int STAGE_NONE = 0;
	/** Run one: twelve blocks straight down in one place, and the column back to the crate. */
	public static final int STAGE_CORE = 1;
	/** Run two: four stakes on four biomes, and a night for them to stand through. */
	public static final int STAGE_WIND = 2;
	/** Run three: a sealed sample out of the acid, home without breaking the seal. */
	public static final int STAGE_SEEP = 3;
	/** Act two: fit the relay module and drive to the man the mast cannot hear. */
	public static final int STAGE_RANGE = 4;
	public static final int STAGE_DONE = 5;

	/** Blocks down the shallow core has to go, and blocks of the column the crate wants back. */
	public static final int CORE_DEPTH = 12;
	public static final int CORE_COLUMN = 12;
	/** Stakes the wind count wants, how far apart, and how many errands there are to notice. */
	public static final int STAKES = 4;
	public static final int STAKE_SPACING = 80;
	public static final int ERRANDS = 5;

	/** One stake in the ground, and the biome it is counting the wind on. */
	public record Stake(BlockPos pos, String biome) {
	}

	/** One of the {@code STAGE_*} constants. */
	public int stage;
	/** Where the shaft was started, so a second hole somewhere else does not count as the same one. */
	@Nullable
	public BlockPos coreHole;
	/** Deepest the player has stood below the ground around that shaft. */
	public int coreDepth;
	/** The stakes that stand, in the order they were planted. */
	public final List<Stake> stakes = new ArrayList<>();
	/** The surface day the fourth stake went in; the count wants the next dawn after it. */
	public long stakeDay = -1L;
	/** A sealed sample is out of the acid and in somebody's hands. */
	public boolean seepTaken;
	/** It reached the crate with the seal still on. */
	public boolean seepHome;
	/** Sorensen has handed over the relay schematic. */
	public boolean schematic;
	/** A relay module has been seen fitted to a chassis. */
	public boolean relay;
	/** Tanaka has handed over the resonance damper. */
	public boolean damper;

	// The errands. None of them was ever an objective and every one of them was noticed.
	/** Castellanos' tomato seeds went to Okafor. */
	public boolean seeds;
	/** Sorensen's outer door has a plate on it again. */
	public boolean airlock;
	public boolean powerCells;
	public boolean repairKits;
	public boolean sulfur;

	public static ResearchState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_research");
	}

	/** How many of the five errands were done. Act six reads this. */
	public int errandsDone() {
		int done = 0;
		if (seeds) done++;
		if (airlock) done++;
		if (powerCells) done++;
		if (repairKits) done++;
		if (sulfur) done++;
		return done;
	}

	/** Whether a stake here would be a fifth reading: far enough out, and on ground nobody has counted yet. */
	public boolean stakeFits(BlockPos pos, String biome) {
		return stakeRefusal(pos, biome) == null;
	}

	/**
	 * Why a stake will not count here, or null when it does. The two refusals are different things and used to
	 * share one message: a player four hundred blocks out being told the stake was "too close" reads as the
	 * mod being wrong, when what it means is that they are standing on ground they have already counted.
	 *
	 * @return the translation key of the refusal, or null
	 */
	@Nullable
	public String stakeRefusal(BlockPos pos, String biome) {
		for (Stake stake : stakes) {
			if (stake.biome().equals(biome)) return "message.surrogate.stake.same_biome";
			double dx = pos.getX() - stake.pos().getX();
			double dz = pos.getZ() - stake.pos().getZ();
			if (dx * dx + dz * dz < (double) STAKE_SPACING * STAKE_SPACING) return "message.surrogate.stake.close";
		}
		return null;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("Stage", stage);
		if (coreHole != null) {
			nbt.putInt("HoleX", coreHole.getX());
			nbt.putInt("HoleY", coreHole.getY());
			nbt.putInt("HoleZ", coreHole.getZ());
		}
		nbt.putInt("CoreDepth", coreDepth);
		NbtList list = new NbtList();
		for (Stake stake : stakes) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("X", stake.pos().getX());
			entry.putInt("Y", stake.pos().getY());
			entry.putInt("Z", stake.pos().getZ());
			entry.putString("Biome", stake.biome());
			list.add(entry);
		}
		nbt.put("Stakes", list);
		nbt.putLong("StakeDay", stakeDay);
		nbt.putBoolean("SeepTaken", seepTaken);
		nbt.putBoolean("SeepHome", seepHome);
		nbt.putBoolean("Schematic", schematic);
		nbt.putBoolean("Relay", relay);
		nbt.putBoolean("Damper", damper);
		nbt.putBoolean("Seeds", seeds);
		nbt.putBoolean("Airlock", airlock);
		nbt.putBoolean("PowerCells", powerCells);
		nbt.putBoolean("RepairKits", repairKits);
		nbt.putBoolean("Sulfur", sulfur);
		return nbt;
	}

	private static ResearchState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		ResearchState state = new ResearchState();
		state.stage = nbt.getInt("Stage");
		if (nbt.contains("HoleX")) state.coreHole = new BlockPos(nbt.getInt("HoleX"), nbt.getInt("HoleY"), nbt.getInt("HoleZ"));
		state.coreDepth = nbt.getInt("CoreDepth");
		NbtList list = nbt.getList("Stakes", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			state.stakes.add(new Stake(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")), entry.getString("Biome")));
		}
		state.stakeDay = nbt.contains("StakeDay") ? nbt.getLong("StakeDay") : -1L;
		state.seepTaken = nbt.getBoolean("SeepTaken");
		state.seepHome = nbt.getBoolean("SeepHome");
		state.schematic = nbt.getBoolean("Schematic");
		state.relay = nbt.getBoolean("Relay");
		state.damper = nbt.getBoolean("Damper");
		state.seeds = nbt.getBoolean("Seeds");
		state.airlock = nbt.getBoolean("Airlock");
		state.powerCells = nbt.getBoolean("PowerCells");
		state.repairKits = nbt.getBoolean("RepairKits");
		state.sulfur = nbt.getBoolean("Sulfur");
		return state;
	}
}
