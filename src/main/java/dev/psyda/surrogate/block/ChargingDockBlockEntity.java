package dev.psyda.surrogate.block;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.item.RobotChassisItem;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModBlockEntities;
import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

import java.util.UUID;

public class ChargingDockBlockEntity extends BlockEntity {
	@Nullable
	private NbtCompound robotNbt;
	private final SimpleEnergyStorage energy;
	private boolean syncPending;

	// Client-only: a detached entity instance used to draw the parked chassis.
	@Nullable
	private RobotEntity clientRobot;
	private boolean clientRobotDirty = true;

	public ChargingDockBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHARGING_DOCK, pos, state);
		SurrogateConfig cfg = Surrogate.CONFIG;
		this.energy = new SimpleEnergyStorage(cfg.dockEnergyCapacity, cfg.dockMaxInsertPerTick, 0) {
			@Override
			protected void onFinalCommit() {
				markDirty();
				syncPending = true;
			}
		};
	}

	public EnergyStorage getEnergyStorage() {
		return energy;
	}

	public long getStoredEnergy() {
		return energy.getAmount();
	}

	public int getStoredPercent() {
		return energy.getCapacity() <= 0 ? 0 : (int) (energy.getAmount() * 100 / energy.getCapacity());
	}

	public boolean hasRobot() {
		return robotNbt != null;
	}

	@Nullable
	public NbtCompound getRobotNbt() {
		return robotNbt;
	}

	@Nullable
	public UUID getRobotUuid() {
		return robotNbt != null && robotNbt.containsUuid("UUID") ? robotNbt.getUuid("UUID") : null;
	}

	public Text getRobotName() {
		if (robotNbt != null && robotNbt.contains("CustomName", NbtElement.STRING_TYPE) && world != null) {
			try {
				Text name = Text.Serialization.fromJson(robotNbt.getString("CustomName"), world.getRegistryManager());
				if (name != null) return name;
			} catch (RuntimeException ignored) {
			}
		}
		return ModEntities.ROBOT.getName();
	}

	public int getRobotEnergy() {
		return robotNbt == null ? 0 : robotNbt.getInt("Energy");
	}

	public int getRobotEnergyCapacity() {
		return robotNbt == null ? 0 : Surrogate.CONFIG.capacityForBatteryTier(robotNbt.getInt("Battery"));
	}

	public float getRobotHealth() {
		return robotNbt == null ? 0f : robotNbt.getFloat("Health");
	}

	public float getRobotMaxHealth() {
		return robotNbt == null ? 0f : Surrogate.CONFIG.maxHealthForPlatingTier(robotNbt.getInt("Plating"));
	}

	/** Parks a live chassis: it is powered down, serialised, and removed from the world. */
	public boolean dock(RobotEntity robot) {
		if (robotNbt != null || robot.isPiloted() || robot.hasPassengers() || !(world instanceof ServerWorld serverWorld)) {
			return false;
		}
		// An NPC's chassis is not the player's to park.
		if (!robot.isClaimable()) return false;
		robot.forceOffline();
		NbtCompound nbt = new NbtCompound();
		robot.writeNbt(nbt);
		nbt.remove("Passengers");
		robotNbt = nbt;
		robot.discard();
		RobotRegistry.get(serverWorld.getServer()).updateDocked(robot.getUuid(), serverWorld, pos, robot.getName().getString());
		serverWorld.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.9f);
		sync();
		return true;
	}

	public boolean dockFromItem(ItemStack chassis) {
		if (robotNbt != null || !(world instanceof ServerWorld serverWorld)) return false;
		RobotEntity robot = RobotChassisItem.createRobot(serverWorld, chassis);
		if (robot == null) return false;
		robot.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		NbtCompound nbt = new NbtCompound();
		robot.writeNbt(nbt);
		robotNbt = nbt;
		RobotRegistry.get(serverWorld.getServer()).updateDocked(robot.getUuid(), serverWorld, pos, robot.getName().getString());
		serverWorld.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.9f);
		sync();
		return true;
	}

	/** Puts the parked chassis back into the world in front of the dock. @return null if there is no room. */
	@Nullable
	public RobotEntity undock() {
		if (robotNbt == null || !(world instanceof ServerWorld serverWorld)) return null;
		RobotEntity robot = ModEntities.ROBOT.create(serverWorld);
		if (robot == null) return null;
		robot.readNbt(robotNbt);
		robot.setPilot(null);
		robot.forceOffline();

		Direction facing = getCachedState().contains(ChargingDockBlock.FACING) ? getCachedState().get(ChargingDockBlock.FACING) : Direction.NORTH;
		float yaw = facing.asRotation();
		Vec3d front = Vec3d.ofBottomCenter(pos.offset(facing));
		robot.refreshPositionAndAngles(front.x, front.y, front.z, yaw, 0f);
		if (!serverWorld.isSpaceEmpty(robot)) {
			Vec3d onPad = Vec3d.ofBottomCenter(pos).add(facing.getOffsetX() * 0.1, 3.0 / 16.0, facing.getOffsetZ() * 0.1);
			robot.refreshPositionAndAngles(onPad.x, onPad.y, onPad.z, yaw, 0f);
			if (!serverWorld.isSpaceEmpty(robot)) return null;
		}
		robot.setBodyYaw(yaw);
		robot.setHeadYaw(yaw);
		if (!serverWorld.spawnEntity(robot)) {
			Surrogate.LOGGER.warn("Could not eject chassis {} from dock at {}", robot.getUuid(), pos);
			return null;
		}
		robotNbt = null;
		clientRobotDirty = true;
		RobotRegistry.get(serverWorld.getServer()).update(robot);
		serverWorld.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.8f, 0.9f);
		sync();
		return robot;
	}

	/** Used when the dock is broken: the parked chassis becomes an item. */
	public ItemStack takeAsItem() {
		if (robotNbt == null) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(ModItems.ROBOT_CHASSIS);
		NbtCompound nbt = robotNbt.copy();
		nbt.remove("Pos");
		nbt.remove("Motion");
		stack.set(ModComponents.ROBOT_DATA, nbt);
		if (nbt.contains("CustomName", NbtElement.STRING_TYPE)) stack.set(DataComponentTypes.CUSTOM_NAME, getRobotName());
		robotNbt = null;
		clientRobotDirty = true;
		sync();
		return stack;
	}

	public int addEnergy(int amount) {
		long space = energy.getCapacity() - energy.getAmount();
		int accepted = (int) Math.min(space, amount);
		if (accepted > 0) {
			energy.amount += accepted;
			markDirty();
			syncPending = true;
		}
		return accepted;
	}

	public boolean repairDocked(PlayerEntity player, ItemStack kit) {
		if (robotNbt == null) {
			player.sendMessage(Text.translatable("message.surrogate.dock_empty"), true);
			return false;
		}
		float health = robotNbt.getFloat("Health");
		float max = getRobotMaxHealth();
		if (health >= max) {
			player.sendMessage(Text.translatable("message.surrogate.no_damage"), true);
			return false;
		}
		health = Math.min(max, health + Surrogate.CONFIG.repairKitHealth);
		robotNbt.putFloat("Health", health);
		if (!player.getAbilities().creativeMode) kit.decrement(1);
		if (world != null) world.playSound(null, pos, SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.BLOCKS, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.surrogate.repaired", format(health), format(max)), true);
		sync();
		return true;
	}

	public static void tick(World world, BlockPos pos, BlockState state, ChargingDockBlockEntity dock) {
		if (world.isClient) return;
		if (dock.robotNbt != null && dock.energy.getAmount() > 0) {
			int have = dock.robotNbt.getInt("Energy");
			int capacity = dock.getRobotEnergyCapacity();
			if (have < capacity) {
				int transfer = (int) Math.min(Math.min(Surrogate.CONFIG.dockChargePerTick, capacity - have), dock.energy.getAmount());
				dock.robotNbt.putInt("Energy", have + transfer);
				dock.energy.amount -= transfer;
				dock.markDirty();
				dock.syncPending = true;
			}
		}
		if (dock.syncPending && world.getTime() % 10 == 0) {
			dock.syncPending = false;
			dock.sync();
		}
	}

	public void sendInfo(ServerPlayerEntity player) {
		if (robotNbt == null) {
			player.sendMessage(Text.translatable("message.surrogate.dock_info_empty", getStoredPercent()), false);
			return;
		}
		int robotPercent = getRobotEnergyCapacity() <= 0 ? 0 : getRobotEnergy() * 100 / getRobotEnergyCapacity();
		player.sendMessage(Text.translatable("message.surrogate.dock_info", getRobotName(),
				format(getRobotHealth()), format(getRobotMaxHealth()), robotPercent, getStoredPercent()), false);
	}

	private static String format(float value) {
		return value == (int) value ? Integer.toString((int) value) : String.format("%.1f", value);
	}

	private void sync() {
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
		}
	}

	/** Client-side entity used by the renderer to show the parked chassis. */
	@Nullable
	public RobotEntity getClientRobot() {
		if (clientRobotDirty) {
			clientRobotDirty = false;
			if (robotNbt == null || world == null) {
				clientRobot = null;
			} else {
				RobotEntity robot = ModEntities.ROBOT.create(world);
				if (robot != null) {
					try {
						robot.readNbt(robotNbt);
					} catch (RuntimeException e) {
						Surrogate.LOGGER.warn("Could not build preview chassis for dock at {}", pos, e);
					}
				}
				clientRobot = robot;
			}
		}
		return clientRobot;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		if (robotNbt != null) nbt.put("Robot", robotNbt.copy());
		nbt.putLong("Energy", energy.getAmount());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		robotNbt = nbt.contains("Robot", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("Robot") : null;
		energy.amount = Math.min(nbt.getLong("Energy"), energy.getCapacity());
		clientRobotDirty = true;
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
