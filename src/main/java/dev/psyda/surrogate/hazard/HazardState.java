package dev.psyda.surrogate.hazard;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * The planet's two clocks: when the next magnetic storm is due and how far the current one has run, and when
 * the belt is next raining. Both are world-wide, so they hang off the overworld with every other saved state
 * in the mod.
 *
 * <p>The noise in the rock is deliberately not here. It bleeds away in about two minutes, so a restart that
 * forgot it would be indistinguishable from one that remembered it a moment later; {@link Borers} keeps it in
 * memory, per world, under a cell cap, and writes nothing to disk for it.
 */
public class HazardState extends PersistentState {
	private static final Type<HazardState> TYPE = new Type<>(HazardState::new, HazardState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** World time the next storm's run-up begins, or -1 before the first one is scheduled. */
	public long nextStorm = -1L;
	/** World time the current storm's run-up began, or -1 when the sky is quiet. */
	public long stormStart = -1L;
	/** World time the belt's next bout of rain begins, or -1 before the first one is scheduled. */
	public long nextRain = -1L;
	/** World time the current bout began, or -1 when it is dry over there. */
	public long rainStart = -1L;

	public static HazardState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_hazards");
	}

	// ------------------------------------------------------------------ persistence

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putLong("NextStorm", nextStorm);
		nbt.putLong("StormStart", stormStart);
		nbt.putLong("NextRain", nextRain);
		nbt.putLong("RainStart", rainStart);
		return nbt;
	}

	private static HazardState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		HazardState state = new HazardState();
		state.nextStorm = nbt.contains("NextStorm") ? nbt.getLong("NextStorm") : -1L;
		state.stormStart = nbt.contains("StormStart") ? nbt.getLong("StormStart") : -1L;
		state.nextRain = nbt.contains("NextRain") ? nbt.getLong("NextRain") : -1L;
		state.rainStart = nbt.contains("RainStart") ? nbt.getLong("RainStart") : -1L;
		return state;
	}
}
