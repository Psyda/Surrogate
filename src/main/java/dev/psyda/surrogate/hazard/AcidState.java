package dev.psyda.surrogate.hazard;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How far the belt has got with each machine and each crawler hull. Nothing in here ever falls on its own:
 * a hull plate or a repair kit is the only way a number goes down. Saved as surrogate_acid on the overworld,
 * with everything else, because the belt is only ever in the overworld.
 */
public class AcidState extends PersistentState {
	private static final Type<AcidState> TYPE = new Type<>(AcidState::new, AcidState::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** A hull's wear and how many warnings its driver has already had, so the same one is not read twice. */
	public static final class Hull {
		public double wear;
		public int warned;
	}

	/** Corrosion by block position. Only positions that have actually been rained on are ever in here. */
	private final Long2DoubleOpenHashMap machines = new Long2DoubleOpenHashMap();
	private final Map<UUID, Hull> hulls = new HashMap<>();

	public static AcidState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_acid");
	}

	/** Nothing on the planet has been touched yet: the per-tick readers can stop here. */
	public boolean isEmpty() {
		return machines.isEmpty() && hulls.isEmpty();
	}

	public double machine(BlockPos pos) {
		return machines.get(pos.asLong());
	}

	public void setMachine(BlockPos pos, double wear) {
		if (wear <= 0.0) machines.remove(pos.asLong());
		else machines.put(pos.asLong(), wear);
		markDirty();
	}

	public double hullWear(UUID hull) {
		Hull entry = hulls.get(hull);
		return entry == null ? 0.0 : entry.wear;
	}

	public int hullWarned(UUID hull) {
		Hull entry = hulls.get(hull);
		return entry == null ? 0 : entry.warned;
	}

	public void setHull(UUID hull, double wear, int warned) {
		if (wear <= 0.0 && warned <= 0) {
			hulls.remove(hull);
		} else {
			Hull entry = hulls.computeIfAbsent(hull, key -> new Hull());
			entry.wear = Math.max(0.0, wear);
			entry.warned = warned;
		}
		markDirty();
	}

	// ------------------------------------------------------------------ persistence

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		ObjectIterator<Long2DoubleMap.Entry> iterator = machines.long2DoubleEntrySet().iterator();
		while (iterator.hasNext()) {
			Long2DoubleMap.Entry entry = iterator.next();
			NbtCompound machine = new NbtCompound();
			machine.putLong("Pos", entry.getLongKey());
			machine.putDouble("Wear", entry.getDoubleValue());
			list.add(machine);
		}
		nbt.put("Machines", list);
		NbtList fleet = new NbtList();
		for (Map.Entry<UUID, Hull> entry : hulls.entrySet()) {
			NbtCompound hull = new NbtCompound();
			hull.putUuid("Id", entry.getKey());
			hull.putDouble("Wear", entry.getValue().wear);
			hull.putInt("Warned", entry.getValue().warned);
			fleet.add(hull);
		}
		nbt.put("Hulls", fleet);
		return nbt;
	}

	private static AcidState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		AcidState state = new AcidState();
		NbtList list = nbt.getList("Machines", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound machine = list.getCompound(i);
			state.machines.put(machine.getLong("Pos"), machine.getDouble("Wear"));
		}
		NbtList fleet = nbt.getList("Hulls", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < fleet.size(); i++) {
			NbtCompound hull = fleet.getCompound(i);
			if (!hull.containsUuid("Id")) continue;
			Hull entry = new Hull();
			entry.wear = hull.getDouble("Wear");
			entry.warned = hull.getInt("Warned");
			state.hulls.put(hull.getUuid("Id"), entry);
		}
		return state;
	}
}
