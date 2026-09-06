package dev.psyda.surrogate.assay;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

/**
 * How far Contract Seven has got: where the company designated its pad, how much of it stands, and whether
 * the ship has been overhead yet. Kept apart from {@link dev.psyda.surrogate.world.HabitatState} because the
 * assay outlives the opening and the rescue arc reads it.
 */
public class AssayState extends PersistentState {
	private static final Type<AssayState> TYPE = new Type<>(AssayState::new, AssayState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static final int STAGE_NONE = 0;
	/** Get to the designated area and plant the marker. */
	public static final int STAGE_STAKE = 1;
	/** Build the pad; the others arrive with their pieces. */
	public static final int STAGE_PAD = 2;
	/** Bring up the deep sample. */
	public static final int STAGE_CORE = 3;
	/** Build the rocket at the gantry and load the capsule. */
	public static final int STAGE_PAYLOAD = 4;
	/** Ignite it, and watch what comes for the payload. */
	public static final int STAGE_IGNITION = 5;
	public static final int STAGE_DONE = 6;

	/** One of the {@code STAGE_*} constants. */
	public int stage;
	/** The company's designated site: x and z chosen on the first join, y resolved when its chunk loads. */
	@Nullable
	public BlockPos padSite;
	/**
	 * Whether {@link #padSite} is real ground rather than the placeholder y of 0. Once set, the terrain
	 * search must never run again: it re-centres on whatever it is handed, so a second pass drifts the
	 * origin and rebuilds the pad offset from the courses already standing.
	 */
	public boolean padResolved;
	/** How many courses of the pad stand. */
	public int courses;
	/** Tellurium crystals handed over for the core. */
	public int coreSamples;
	/** Whether the payload capsule has been loaded. */
	public boolean payloadLoaded;
	/** Whether the company ship has been seen overhead. Once true, the rescue arc may begin. */
	public boolean shipSeen;

	public static AssayState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_assay");
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("Stage", stage);
		if (padSite != null) {
			nbt.putInt("PadX", padSite.getX());
			nbt.putInt("PadY", padSite.getY());
			nbt.putInt("PadZ", padSite.getZ());
		}
		nbt.putBoolean("PadResolved", padResolved);
		nbt.putInt("Courses", courses);
		nbt.putInt("CoreSamples", coreSamples);
		nbt.putBoolean("PayloadLoaded", payloadLoaded);
		nbt.putBoolean("ShipSeen", shipSeen);
		return nbt;
	}

	private static AssayState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		AssayState state = new AssayState();
		state.stage = nbt.getInt("Stage");
		if (nbt.contains("PadX")) state.padSite = new BlockPos(nbt.getInt("PadX"), nbt.getInt("PadY"), nbt.getInt("PadZ"));
		// Worlds saved before the flag existed keep the origin their pad was actually built on.
		state.padResolved = nbt.getBoolean("PadResolved") || (state.padSite != null && state.padSite.getY() != 0);
		state.courses = nbt.getInt("Courses");
		state.coreSamples = nbt.getInt("CoreSamples");
		state.payloadLoaded = nbt.getBoolean("PayloadLoaded");
		state.shipSeen = nbt.getBoolean("ShipSeen");
		return state;
	}
}
