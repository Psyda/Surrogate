package dev.psyda.surrogate.block;

import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class DiveChairBlockEntity extends BlockEntity {
	@Nullable
	private UUID linkedRobot;
	private String linkedName = "";
	@Nullable
	private UUID occupant;
	private String occupantName = "";

	public DiveChairBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DIVE_CHAIR, pos, state);
	}

	@Nullable
	public UUID getLinkedRobot() {
		return linkedRobot;
	}

	public String getLinkedName() {
		return linkedName;
	}

	@Nullable
	public UUID getOccupant() {
		return occupant;
	}

	public String getOccupantName() {
		return occupantName;
	}

	public void setLink(@Nullable UUID robot, String name) {
		this.linkedRobot = robot;
		this.linkedName = name;
		sync();
	}

	public void setOccupant(UUID player, String name) {
		this.occupant = player;
		this.occupantName = name;
		sync();
	}

	public void clearOccupant(UUID expected) {
		if (occupant != null && occupant.equals(expected)) {
			occupant = null;
			occupantName = "";
			sync();
		}
	}

	/** Breaking the chair under a diving pilot severs the link: their body has nowhere to lie. */
	public void onRemoved() {
		if (occupant != null && world instanceof ServerWorld serverWorld) {
			ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(occupant);
			if (player != null && PilotManager.isPiloting(player)) {
				PilotManager.disconnect(player, false, Text.translatable("message.surrogate.chair_destroyed"), true);
			}
		}
	}

	public void sendInfo(ServerPlayerEntity player) {
		if (linkedRobot == null) {
			player.sendMessage(Text.translatable("message.surrogate.chair_info_unlinked"), false);
			return;
		}
		Optional<RobotRegistry.Record> record = RobotRegistry.get(player.server).get(linkedRobot);
		if (record.isEmpty()) {
			player.sendMessage(Text.translatable("message.surrogate.chair_info_unknown", linkedName), false);
			return;
		}
		RobotRegistry.Record r = record.get();
		String where = r.pos().getX() + ", " + r.pos().getY() + ", " + r.pos().getZ();
		Text status = r.wrecked() ? Text.translatable("message.surrogate.chair_status.wrecked", where)
				: r.docked() ? Text.translatable("message.surrogate.chair_status.docked", where)
				: Text.translatable("message.surrogate.chair_status.deployed", where);
		player.sendMessage(Text.translatable("message.surrogate.chair_info", r.name(), status), false);
	}

	private void sync() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (linkedRobot != null) nbt.putUuid("Robot", linkedRobot);
		nbt.putString("RobotName", linkedName);
		if (occupant != null) nbt.putUuid("Occupant", occupant);
		nbt.putString("OccupantName", occupantName);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		linkedRobot = nbt.containsUuid("Robot") ? nbt.getUuid("Robot") : null;
		linkedName = nbt.getString("RobotName");
		occupant = nbt.containsUuid("Occupant") ? nbt.getUuid("Occupant") : null;
		occupantName = nbt.getString("OccupantName");
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}
}
