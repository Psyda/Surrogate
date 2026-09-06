package dev.psyda.surrogate.transit;

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
import java.util.UUID;

/**
 * Where the week aboard the Provender has got to, so it can resume across restarts and never run twice.
 * Days are stages 1 to 7; {@link #STAGE_DROP} is the descent and {@link #STAGE_DONE} means the player is
 * on the ground.
 */
public class TransitState extends PersistentState {
	private static final Type<TransitState> TYPE = new Type<>(TransitState::new, TransitState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static final int STAGE_NONE = 0;
	public static final int STAGE_DROP = 8;
	public static final int STAGE_DONE = 9;

	public int stage;
	@Nullable
	public UUID protagonist;
	public boolean built;
	/** The label the script resumes at after a restart. */
	public String label = "";

	// The people aboard.
	@Nullable
	public UUID castellanos;
	@Nullable
	public UUID ferreira;
	@Nullable
	public UUID teague;
	@Nullable
	public UUID calibrationRobot;
	/** Ballast, who is not cargo whatever the manifest says. */
	@Nullable
	public UUID cat;
	public final List<UUID> sleepers = new ArrayList<>();

	// The clock and the ship.
	public float hours = 6f;
	public long flipStart = -1L;
	public boolean flipped;
	public int lights;
	public boolean breached;
	public boolean coolantClosed;
	public boolean vasquezDead;
	public boolean bodyRemoved;

	public static TransitState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_transit");
	}

	public boolean inProgress() {
		return stage > STAGE_NONE && stage < STAGE_DONE;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("Stage", stage);
		if (protagonist != null) nbt.putUuid("Protagonist", protagonist);
		nbt.putBoolean("Built", built);
		nbt.putString("Label", label);
		if (castellanos != null) nbt.putUuid("Castellanos", castellanos);
		if (ferreira != null) nbt.putUuid("Ferreira", ferreira);
		if (teague != null) nbt.putUuid("Teague", teague);
		if (calibrationRobot != null) nbt.putUuid("CalibrationRobot", calibrationRobot);
		if (cat != null) nbt.putUuid("Cat", cat);
		NbtList list = new NbtList();
		for (UUID id : sleepers) {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("Id", id);
			list.add(entry);
		}
		nbt.put("Sleepers", list);
		nbt.putFloat("Hours", hours);
		nbt.putLong("FlipStart", flipStart);
		nbt.putBoolean("Flipped", flipped);
		nbt.putInt("Lights", lights);
		nbt.putBoolean("Breached", breached);
		nbt.putBoolean("CoolantClosed", coolantClosed);
		nbt.putBoolean("VasquezDead", vasquezDead);
		nbt.putBoolean("BodyRemoved", bodyRemoved);
		return nbt;
	}

	private static TransitState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		TransitState state = new TransitState();
		state.stage = nbt.getInt("Stage");
		state.protagonist = nbt.containsUuid("Protagonist") ? nbt.getUuid("Protagonist") : null;
		state.built = nbt.getBoolean("Built");
		state.label = nbt.getString("Label");
		state.castellanos = nbt.containsUuid("Castellanos") ? nbt.getUuid("Castellanos") : null;
		state.ferreira = nbt.containsUuid("Ferreira") ? nbt.getUuid("Ferreira") : null;
		state.teague = nbt.containsUuid("Teague") ? nbt.getUuid("Teague") : null;
		state.calibrationRobot = nbt.containsUuid("CalibrationRobot") ? nbt.getUuid("CalibrationRobot") : null;
		state.cat = nbt.containsUuid("Cat") ? nbt.getUuid("Cat") : null;
		NbtList list = nbt.getList("Sleepers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			if (entry.containsUuid("Id")) state.sleepers.add(entry.getUuid("Id"));
		}
		state.hours = nbt.contains("Hours") ? nbt.getFloat("Hours") : 6f;
		state.flipStart = nbt.contains("FlipStart") ? nbt.getLong("FlipStart") : -1L;
		state.flipped = nbt.getBoolean("Flipped");
		state.lights = nbt.getInt("Lights");
		state.breached = nbt.getBoolean("Breached");
		state.coolantClosed = nbt.getBoolean("CoolantClosed");
		state.vasquezDead = nbt.getBoolean("VasquezDead");
		state.bodyRemoved = nbt.getBoolean("BodyRemoved");
		return state;
	}

	/** Which crew a saved id belongs to, for the helpers that look them up. */
	@Nullable
	public UUID crewId(dev.psyda.surrogate.prologue.Crew who) {
		return switch (who) {
			case CASTELLANOS -> castellanos;
			case FERREIRA -> ferreira;
			case TEAGUE -> teague;
			default -> null;
		};
	}

	/** Unused ORIGIN placeholder kept for symmetry with HabitatState; the ship always sits at {@link TransitDimension#ORIGIN}. */
	public BlockPos origin() {
		return TransitDimension.ORIGIN;
	}
}
