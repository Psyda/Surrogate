package dev.psyda.surrogate.crawler;

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

/** Which crawler owns which cabin, where its hull was last seen, and whether the room has been built. */
public class CrawlerInteriors extends PersistentState {
	private static final Type<CrawlerInteriors> TYPE = new Type<>(CrawlerInteriors::new, CrawlerInteriors::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
	/** Cabins sit this far apart along x, so a room and the ground painted in front of it never meet the next. */
	public static final int SPACING = 64;
	public static final int FLOOR_Y = 100;

	public static final class Slot {
		public final int index;
		@Nullable
		public UUID hull;
		public int hullChunkX;
		public int hullChunkZ;
		public boolean built;
		/** Centre of the 3x3 chunks force-loaded around the hull while the cabin is occupied, or MIN_VALUE for none. */
		public int forcedX = Integer.MIN_VALUE;
		public int forcedZ = Integer.MIN_VALUE;

		Slot(int index, @Nullable UUID hull) {
			this.index = index;
			this.hull = hull;
		}
	}

	private final List<Slot> slots = new ArrayList<>();

	public static CrawlerInteriors get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_crawlers");
	}

	/** The floor block at the centre of a cabin. */
	public static BlockPos origin(int index) {
		return new BlockPos(index * SPACING + 8, FLOOR_Y, 8);
	}

	/** The cabin index a position in the cabin dimension falls in, whether or not it is allocated. */
	public static int indexAt(BlockPos pos) {
		return Math.floorDiv(pos.getX() - 8 + SPACING / 2, SPACING);
	}

	public List<Slot> slots() {
		return slots;
	}

	@Nullable
	public Slot byIndex(int index) {
		for (Slot slot : slots) if (slot.index == index) return slot;
		return null;
	}

	@Nullable
	public Slot forHull(UUID hull) {
		for (Slot slot : slots) if (hull.equals(slot.hull)) return slot;
		return null;
	}

	public Slot allocate(UUID hull) {
		Slot slot = new Slot(slots.size(), hull);
		slots.add(slot);
		markDirty();
		return slot;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		for (Slot slot : slots) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("Index", slot.index);
			if (slot.hull != null) entry.putUuid("Hull", slot.hull);
			entry.putInt("ChunkX", slot.hullChunkX);
			entry.putInt("ChunkZ", slot.hullChunkZ);
			entry.putBoolean("Built", slot.built);
			if (slot.forcedX != Integer.MIN_VALUE) {
				entry.putInt("ForcedX", slot.forcedX);
				entry.putInt("ForcedZ", slot.forcedZ);
			}
			list.add(entry);
		}
		nbt.put("Cabins", list);
		return nbt;
	}

	private static CrawlerInteriors fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		CrawlerInteriors state = new CrawlerInteriors();
		for (NbtElement element : nbt.getList("Cabins", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) element;
			Slot slot = new Slot(entry.getInt("Index"), entry.containsUuid("Hull") ? entry.getUuid("Hull") : null);
			slot.hullChunkX = entry.getInt("ChunkX");
			slot.hullChunkZ = entry.getInt("ChunkZ");
			slot.built = entry.getBoolean("Built");
			if (entry.contains("ForcedX")) {
				slot.forcedX = entry.getInt("ForcedX");
				slot.forcedZ = entry.getInt("ForcedZ");
			}
			state.slots.add(slot);
		}
		return state;
	}
}
