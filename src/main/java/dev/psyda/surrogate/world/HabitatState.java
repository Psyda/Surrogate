package dev.psyda.surrogate.world;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Remembers where (and whether) the starter habitat was built, so it is only ever built once per world; where
 * Halloran's station is; and how far the opening on the ground got, so it can resume or stay finished across
 * restarts.
 */
public class HabitatState extends PersistentState {
	private static final Type<HabitatState> TYPE = new Type<>(HabitatState::new, HabitatState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	@Nullable
	public BlockPos origin;

	/** Site Two, Halloran's station: x and z chosen on the first join, y resolved when its chunk first loads. */
	@Nullable
	public BlockPos siteTwo;
	public boolean siteTwoBuilt;

	/** One of the {@code Prologue.STAGE_*} constants. */
	public int prologueStage;
	/** The player the opening sequence is being told to. */
	@Nullable
	public UUID protagonist;
	/** The crew, the chassis and the cat the sequence is about, so it can find them again after a restart. */
	@Nullable
	public UUID halloran;
	@Nullable
	public UUID marsh;
	@Nullable
	public UUID halloranRobot;
	@Nullable
	public UUID cat;
	/** The overworld day (time / 24000) the pod came down on, or -1. The surface days count from it. */
	public long landedDay = -1L;
	/** Marsh's walk over: 0 not yet, 1 he is at the pod, 2 he has been and gone. */
	public int marshVisit;
	/** Samples logged to the assay crate for the company. */
	public int assaySamples;

	public static HabitatState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_habitat");
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		if (origin != null) {
			nbt.putInt("X", origin.getX());
			nbt.putInt("Y", origin.getY());
			nbt.putInt("Z", origin.getZ());
		}
		if (siteTwo != null) {
			nbt.putInt("SiteTwoX", siteTwo.getX());
			nbt.putInt("SiteTwoY", siteTwo.getY());
			nbt.putInt("SiteTwoZ", siteTwo.getZ());
		}
		nbt.putBoolean("SiteTwoBuilt", siteTwoBuilt);
		NbtCompound prologue = new NbtCompound();
		prologue.putInt("Stage", prologueStage);
		if (protagonist != null) prologue.putUuid("Protagonist", protagonist);
		if (halloran != null) prologue.putUuid("Halloran", halloran);
		if (marsh != null) prologue.putUuid("Marsh", marsh);
		if (halloranRobot != null) prologue.putUuid("HalloranRobot", halloranRobot);
		if (cat != null) prologue.putUuid("Cat", cat);
		prologue.putLong("LandedDay", landedDay);
		prologue.putInt("MarshVisit", marshVisit);
		prologue.putInt("AssaySamples", assaySamples);
		nbt.put("Prologue", prologue);
		return nbt;
	}

	private static HabitatState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		HabitatState state = new HabitatState();
		if (nbt.contains("X")) state.origin = new BlockPos(nbt.getInt("X"), nbt.getInt("Y"), nbt.getInt("Z"));
		if (nbt.contains("SiteTwoX")) state.siteTwo = new BlockPos(nbt.getInt("SiteTwoX"), nbt.getInt("SiteTwoY"), nbt.getInt("SiteTwoZ"));
		state.siteTwoBuilt = nbt.getBoolean("SiteTwoBuilt");
		if (nbt.contains("Prologue")) {
			NbtCompound prologue = nbt.getCompound("Prologue");
			state.prologueStage = prologue.getInt("Stage");
			state.protagonist = prologue.containsUuid("Protagonist") ? prologue.getUuid("Protagonist") : null;
			state.halloran = prologue.containsUuid("Halloran") ? prologue.getUuid("Halloran") : null;
			state.marsh = prologue.containsUuid("Marsh") ? prologue.getUuid("Marsh") : null;
			state.halloranRobot = prologue.containsUuid("HalloranRobot") ? prologue.getUuid("HalloranRobot") : null;
			state.cat = prologue.containsUuid("Cat") ? prologue.getUuid("Cat") : null;
			state.landedDay = prologue.contains("LandedDay") ? prologue.getLong("LandedDay") : -1L;
			state.marshVisit = prologue.getInt("MarshVisit");
			state.assaySamples = prologue.getInt("AssaySamples");
		}
		return state;
	}
}
