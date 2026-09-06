package dev.psyda.surrogate.atmosphere;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.AirlockDoorBlock;
import dev.psyda.surrogate.block.LifeSupportBlockEntity;
import dev.psyda.surrogate.registry.ModTags;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side index of every sealed volume, keyed by the life support unit that maintains it, plus the
 * flood fill that decides whether a room is airtight.
 *
 * <p>A block is airtight when it fills its whole cell: any full cube (stone, planks, glass, hull plating),
 * any liquid, or a closed airlock door. Ordinary doors, trapdoors, slabs, stairs and fences leak.
 */
public final class Atmosphere {
	private static final Map<RegistryKey<World>, Map<BlockPos, LifeSupportBlockEntity>> UNITS = new HashMap<>();

	private Atmosphere() {
	}

	public static void register(World world, LifeSupportBlockEntity unit) {
		UNITS.computeIfAbsent(world.getRegistryKey(), key -> new HashMap<>()).put(unit.getPos().toImmutable(), unit);
	}

	public static void unregister(World world, BlockPos pos) {
		Map<BlockPos, LifeSupportBlockEntity> units = UNITS.get(world.getRegistryKey());
		if (units != null) units.remove(pos);
	}

	public static void clear() {
		UNITS.clear();
	}

	public static Collection<LifeSupportBlockEntity> units(World world) {
		Map<BlockPos, LifeSupportBlockEntity> units = UNITS.get(world.getRegistryKey());
		return units == null ? List.of() : units.values();
	}

	/** The best volume containing {@code pos}, or null when it is open to the atmosphere (or no unit covers it). */
	@Nullable
	public static SealedVolume volumeAt(World world, BlockPos pos) {
		Map<BlockPos, LifeSupportBlockEntity> units = UNITS.get(world.getRegistryKey());
		if (units == null) return null;
		SealedVolume best = null;
		for (LifeSupportBlockEntity unit : units.values()) {
			SealedVolume volume = unit.getVolume();
			if (volume.contains(pos) && (best == null || volume.quality > best.quality)) best = volume;
		}
		return best;
	}

	/** Something at {@code pos} changed (a door moved): any unit whose volume touches it should rescan now. */
	public static void invalidateAround(World world, BlockPos pos) {
		Map<BlockPos, LifeSupportBlockEntity> units = UNITS.get(world.getRegistryKey());
		if (units == null) return;
		for (LifeSupportBlockEntity unit : units.values()) {
			SealedVolume volume = unit.getVolume();
			if (volume.contains(pos)) {
				unit.requestScan();
				continue;
			}
			for (Direction direction : Direction.values()) {
				if (volume.contains(pos.offset(direction))) {
					unit.requestScan();
					break;
				}
			}
		}
	}

	public static boolean isToxic(World world, BlockPos pos) {
		return Surrogate.CONFIG.toxicAtmosphere && Surrogate.CONFIG.surfaceEffects && world.getBiome(pos).isIn(ModTags.TOXIC);
	}

	public static boolean isAirtight(World world, BlockPos pos) {
		return isAirtight(world, pos, world.getBlockState(pos));
	}

	public static boolean isAirtight(World world, BlockPos pos, BlockState state) {
		if (state.isAir()) return false;
		if (state.getBlock() instanceof AirlockDoorBlock) return !state.get(DoorBlock.OPEN);
		if (!state.getFluidState().isEmpty()) return true;
		return state.isFullCube(world, pos);
	}

	/**
	 * Flood fills from {@code start}. Stops, and reports a leak, the moment the fill reaches a cell with nothing
	 * above it (the hole, or the open air beyond it), spills past the volume cap, or leaves the world. A room
	 * that leaks keeps the shape it had when it was last airtight, so the people in it are breathing a leaking
	 * room rather than the outside, and so a breach does not send the fill off across the whole map.
	 */
	public static void scan(World world, BlockPos start, SealedVolume volume) {
		int cap = Math.max(64, Surrogate.CONFIG.maxSealedVolume);
		LongOpenHashSet visited = new LongOpenHashSet();
		LongOpenHashSet caustic = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		BlockPos.Mutable neighbor = new BlockPos.Mutable();
		BlockPos skyLeak = null;
		long last = start.asLong();
		int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
		int maxX = minX, maxY = minY, maxZ = minZ;

		if (!isAirtight(world, start)) {
			visited.add(start.asLong());
			queue.enqueue(start.asLong());
		}
		boolean sealed = true;
		scan:
		while (!queue.isEmpty()) {
			long packed = queue.dequeueLong();
			last = packed;
			cursor.set(packed);
			minX = Math.min(minX, cursor.getX());
			minY = Math.min(minY, cursor.getY());
			minZ = Math.min(minZ, cursor.getZ());
			maxX = Math.max(maxX, cursor.getX());
			maxY = Math.max(maxY, cursor.getY());
			maxZ = Math.max(maxZ, cursor.getZ());
			// Nothing solid above the cell means it is outside; sky light alone would also fire under a glass roof.
			// That is the verdict: there is no point walking the rest of the world.
			if (cursor.getY() >= world.getTopY(Heightmap.Type.MOTION_BLOCKING, cursor.getX(), cursor.getZ())) {
				skyLeak = cursor.toImmutable();
				sealed = false;
				break;
			}
			if (visited.size() > cap) {
				sealed = false;
				break;
			}
			for (Direction direction : Direction.values()) {
				neighbor.set(cursor, direction);
				if (neighbor.getY() < world.getBottomY() || neighbor.getY() >= world.getTopY()) {
					sealed = false;
					break scan;
				}
				long next = neighbor.asLong();
				if (visited.contains(next)) continue;
				// Unloaded chunks are treated as walls: better a stale seal than a false alarm.
				if (!world.isChunkLoaded(neighbor.getX() >> 4, neighbor.getZ() >> 4)) continue;
				BlockState state = world.getBlockState(neighbor);
				if (isAirtight(world, neighbor, state)) {
					if (state.isIn(ModTags.CAUSTIC_BLOCKS)) caustic.add(next);
					continue;
				}
				visited.add(next);
				queue.enqueue(next);
			}
		}
		volume.sealed = sealed;
		volume.leak = sealed ? null : skyLeak != null ? skyLeak : BlockPos.fromLong(last);
		volume.causticBlocks = caustic.size();
		Box bounds = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
		if (sealed || volume.sealedCells.isEmpty()) {
			volume.cells = visited;
			volume.size = visited.size();
			volume.bounds = bounds;
			if (sealed) {
				volume.sealedCells = visited;
				volume.sealedBounds = bounds;
			}
		} else {
			// Leaking: the room is still the room. Its air is going, but its walls are where they were.
			volume.cells = volume.sealedCells;
			volume.size = volume.sealedCells.size();
			volume.bounds = volume.sealedBounds;
		}
	}

	/** Cells reachable from {@code start} without passing an airtight block, or null if more than {@code cap}. */
	@Nullable
	public static LongOpenHashSet enclosure(World world, BlockPos start, int cap) {
		LongOpenHashSet visited = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		BlockPos.Mutable neighbor = new BlockPos.Mutable();
		if (isAirtight(world, start)) return visited;
		visited.add(start.asLong());
		queue.enqueue(start.asLong());
		while (!queue.isEmpty()) {
			cursor.set(queue.dequeueLong());
			if (visited.size() > cap) return null;
			for (Direction direction : Direction.values()) {
				neighbor.set(cursor, direction);
				long next = neighbor.asLong();
				if (visited.contains(next) || !world.isChunkLoaded(neighbor.getX() >> 4, neighbor.getZ() >> 4)) continue;
				if (isAirtight(world, neighbor, world.getBlockState(neighbor))) continue;
				visited.add(next);
				queue.enqueue(next);
			}
		}
		return visited;
	}

	/** How many caustic items an inventory holds. */
	public static int causticCount(net.minecraft.inventory.Inventory inventory) {
		int count = 0;
		for (int i = 0; i < inventory.size(); i++) {
			net.minecraft.item.ItemStack stack = inventory.getStack(i);
			if (stack.isIn(ModTags.CAUSTIC_ITEMS)) count += stack.getCount();
		}
		return count;
	}
}
