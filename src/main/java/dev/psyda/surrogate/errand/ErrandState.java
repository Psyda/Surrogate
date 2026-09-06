package dev.psyda.surrogate.errand;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.fauna.Specimen;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * What the player has been asked to do and what they have got round to. Three bitmasks and a handful of
 * counters, all of it keyed off enum ordinals, which is why those never move.
 *
 * <p>The two survey masks are separate on purpose: {@code read} is what Okafor's disk has a file on, and
 * {@code caged} is what is actually in a crate on the pad. A specimen can be one, the other, both or
 * neither, and act six asks about both.
 */
public class ErrandState extends PersistentState {
	private static final Type<ErrandState> TYPE = new Type<>(ErrandState::new, ErrandState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** Errands the player has been told about. */
	public int offered;
	/** Errands the player has finished. */
	public int done;

	/** Specimens Okafor's software has a reading of. See {@link Specimen#bit()}. */
	public int read;
	/** Specimens in a crate, alive, for the ark. */
	public int caged;

	/** Whether the hub has the analysis disk in it, which is what makes a reading readable. */
	public boolean disk;

	/**
	 * The housewarming runs on a night's sleep, so it needs to know a night has passed since the tiredness
	 * landed rather than just that the player has slept at some point. -1 until the tiredness lands.
	 */
	public long tiredDay = -1L;

	/** Where the body outside Clinic Nine is, once it has been placed, and where its grave went. */
	@Nullable
	public BlockPos bodyPos;
	@Nullable
	public BlockPos gravePos;
	/** Which armour stand is him. Kept by id, so one that gets nudged is still the right one. */
	@Nullable
	public java.util.UUID bodyId;

	/** Corroded machines put back for Brandt, of three. */
	public int machinesFixed;

	public static ErrandState get(MinecraftServer server) {
		ServerWorld world = server.getWorld(World.OVERWORLD);
		if (world == null) throw new IllegalStateException("no overworld");
		return world.getPersistentStateManager().getOrCreate(TYPE, Surrogate.MOD_ID + "_errands");
	}

	public boolean offered(Errand errand) {
		return (offered & errand.bit()) != 0;
	}

	public boolean done(Errand errand) {
		return (done & errand.bit()) != 0;
	}

	public void offer(Errand errand) {
		offered |= errand.bit();
		markDirty();
	}

	public void finish(Errand errand) {
		offered |= errand.bit();
		done |= errand.bit();
		markDirty();
	}

	/** How many are finished, for the ending's weighting and for the board's header. */
	public int doneCount() {
		return Integer.bitCount(done);
	}

	public boolean hasRead(Specimen specimen) {
		return (read & specimen.bit()) != 0;
	}

	public boolean hasCaged(Specimen specimen) {
		return (caged & specimen.bit()) != 0;
	}

	/** True when this reading is new, so the caller knows whether to say anything about it. */
	public boolean fileReading(Specimen specimen) {
		if (hasRead(specimen)) return false;
		read |= specimen.bit();
		markDirty();
		return true;
	}

	public boolean fileCage(Specimen specimen) {
		if (hasCaged(specimen)) return false;
		caged |= specimen.bit();
		markDirty();
		return true;
	}

	public int readCount() {
		return Integer.bitCount(read);
	}

	public int cagedCount() {
		return Integer.bitCount(caged);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("Offered", offered);
		nbt.putInt("Done", done);
		nbt.putInt("Read", read);
		nbt.putInt("Caged", caged);
		nbt.putBoolean("Disk", disk);
		nbt.putLong("TiredDay", tiredDay);
		nbt.putInt("MachinesFixed", machinesFixed);
		if (bodyPos != null) nbt.putLong("BodyPos", bodyPos.asLong());
		if (bodyId != null) nbt.putUuid("BodyId", bodyId);
		if (gravePos != null) nbt.putLong("GravePos", gravePos.asLong());
		return nbt;
	}

	private static ErrandState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		ErrandState state = new ErrandState();
		state.offered = nbt.getInt("Offered");
		state.done = nbt.getInt("Done");
		state.read = nbt.getInt("Read");
		state.caged = nbt.getInt("Caged");
		state.disk = nbt.getBoolean("Disk");
		state.tiredDay = nbt.contains("TiredDay") ? nbt.getLong("TiredDay") : -1L;
		state.machinesFixed = nbt.getInt("MachinesFixed");
		if (nbt.contains("BodyPos")) state.bodyPos = BlockPos.fromLong(nbt.getLong("BodyPos"));
		if (nbt.containsUuid("BodyId")) state.bodyId = nbt.getUuid("BodyId");
		if (nbt.contains("GravePos")) state.gravePos = BlockPos.fromLong(nbt.getLong("GravePos"));
		return state;
	}
}
