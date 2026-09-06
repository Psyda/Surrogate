package dev.psyda.surrogate.pilot;

import dev.psyda.surrogate.entity.RobotEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-wide index of where every chassis was last seen, so a dive chair can find its robot even when the
 * robot's chunk is unloaded or the robot is parked inside a charging dock.
 */
public class RobotRegistry extends PersistentState {
	public record Record(RegistryKey<World> dimension, BlockPos pos, String name, boolean docked, boolean wrecked, long lastSeen) {
	}

	private static final Type<RobotRegistry> TYPE = new Type<>(RobotRegistry::new, RobotRegistry::fromNbt, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
	private final Map<UUID, Record> records = new HashMap<>();

	public static RobotRegistry get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "surrogate_robots");
	}

	public Optional<Record> get(UUID uuid) {
		return Optional.ofNullable(records.get(uuid));
	}

	public void update(RobotEntity robot) {
		// A scripted chassis stays out of the registry entirely, so even a forged chair link cannot resolve
		// one. AnnexBuilder.killRobot clears the scripted flag before calling this, which is deliberate: a
		// chassis the script has finished with does become claimable salvage.
		if (robot.isScripted()) return;
		records.put(robot.getUuid(), new Record(robot.getWorld().getRegistryKey(), robot.getBlockPos(),
				robot.getName().getString(), false, false, robot.getWorld().getTime()));
		markDirty();
	}

	public void updateDocked(UUID uuid, ServerWorld world, BlockPos dockPos, String name) {
		records.put(uuid, new Record(world.getRegistryKey(), dockPos, name, true, false, world.getTime()));
		markDirty();
	}

	public void markWrecked(UUID uuid) {
		Record old = records.get(uuid);
		if (old != null) {
			records.put(uuid, new Record(old.dimension(), old.pos(), old.name(), false, true, old.lastSeen()));
			markDirty();
		}
	}

	public void remove(UUID uuid) {
		if (records.remove(uuid) != null) markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtList list = new NbtList();
		records.forEach((uuid, record) -> {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("Id", uuid);
			entry.putString("Dimension", record.dimension().getValue().toString());
			entry.putInt("X", record.pos().getX());
			entry.putInt("Y", record.pos().getY());
			entry.putInt("Z", record.pos().getZ());
			entry.putString("Name", record.name());
			entry.putBoolean("Docked", record.docked());
			entry.putBoolean("Wrecked", record.wrecked());
			entry.putLong("LastSeen", record.lastSeen());
			list.add(entry);
		});
		nbt.put("Robots", list);
		return nbt;
	}

	private static RobotRegistry fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		RobotRegistry registry = new RobotRegistry();
		NbtList list = nbt.getList("Robots", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			if (!entry.containsUuid("Id")) continue;
			RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(entry.getString("Dimension")));
			registry.records.put(entry.getUuid("Id"), new Record(dimension,
					new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
					entry.getString("Name"), entry.getBoolean("Docked"), entry.getBoolean("Wrecked"), entry.getLong("LastSeen")));
		}
		return registry;
	}
}
