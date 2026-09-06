package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.registry.ModComponents;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What is left of a chassis after it dies: a toppled hull holding the cargo, lying wherever the robot fell.
 * Right click to loot it, use a wrench to salvage it into a scrap chassis (the upgrades survive).
 */
public class RobotWreckEntity extends Entity {
	private static final TrackedData<Integer> PLATING = DataTracker.registerData(RobotWreckEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BATTERY = DataTracker.registerData(RobotWreckEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CARGO_TIER = DataTracker.registerData(RobotWreckEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> FABRICATOR = DataTracker.registerData(RobotWreckEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** The dead chassis' module mask, carried whole so a rebuild keeps every countermeasure it was paid for. */
	private static final TrackedData<Integer> MODULES = DataTracker.registerData(RobotWreckEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private final SimpleInventory inventory = new SimpleInventory(45) {
		@Override
		public boolean canPlayerUse(PlayerEntity player) {
			return !RobotWreckEntity.this.isRemoved() && player.squaredDistanceTo(RobotWreckEntity.this) <= 64.0;
		}
	};
	@Nullable
	private UUID robotUuid;
	@Nullable
	private Text robotName;

	public RobotWreckEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PLATING, 0);
		builder.add(BATTERY, 0);
		builder.add(CARGO_TIER, 0);
		builder.add(FABRICATOR, false);
		builder.add(MODULES, 0);
	}

	public void setSalvageData(UUID robotUuid, int plating, int battery, int cargoTier, boolean fabricator, int modules, @Nullable Text robotName) {
		this.robotUuid = robotUuid;
		this.robotName = robotName;
		this.dataTracker.set(PLATING, plating);
		this.dataTracker.set(BATTERY, battery);
		this.dataTracker.set(CARGO_TIER, cargoTier);
		this.dataTracker.set(FABRICATOR, fabricator);
		this.dataTracker.set(MODULES, modules);
		if (robotName != null) {
			setCustomName(Text.translatable("entity.surrogate.robot_wreck.named", robotName));
		}
	}

	public void fillFrom(List<ItemStack> stacks) {
		int slot = 0;
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) continue;
			if (slot >= inventory.size()) {
				dropStack(stack.copy());
				continue;
			}
			inventory.setStack(slot++, stack.copy());
		}
	}

	public SimpleInventory getInventory() {
		return inventory;
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	@Override
	protected double getGravity() {
		return 0.04;
	}

	@Override
	public void tick() {
		super.tick();
		applyGravity();
		move(MovementType.SELF, getVelocity());
		double friction = isOnGround() ? 0.4 : 0.98;
		setVelocity(getVelocity().multiply(friction, 0.98, friction));
		if (getWorld().isClient && this.random.nextInt(8) == 0) {
			getWorld().addParticle(ParticleTypes.SMOKE, getParticleX(0.4), getY() + 0.4, getParticleZ(0.4), 0.0, 0.03, 0.0);
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		boolean client = getWorld().isClient;
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(ModItems.WRENCH)) {
			if (!client) salvage(player);
			return ActionResult.success(client);
		}
		if (!client) {
			player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
					(syncId, playerInventory, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X5, syncId, playerInventory, inventory, 5),
					getDisplayName()));
		}
		return ActionResult.success(client);
	}

	private void salvage(PlayerEntity player) {
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty()) {
				dropStack(stack);
				inventory.setStack(i, ItemStack.EMPTY);
			}
		}
		ItemStack scrap = new ItemStack(ModItems.SCRAP_CHASSIS);
		NbtCompound data = new NbtCompound();
		if (robotUuid != null) data.putUuid("RobotUuid", robotUuid);
		data.putInt("Plating", this.dataTracker.get(PLATING));
		data.putInt("Battery", this.dataTracker.get(BATTERY));
		data.putInt("CargoTier", this.dataTracker.get(CARGO_TIER));
		data.putBoolean("Fabricator", this.dataTracker.get(FABRICATOR));
		data.putInt("Modules", this.dataTracker.get(MODULES));
		if (robotName != null) data.putString("RobotName", Text.Serialization.toJsonString(robotName, getRegistryManager()));
		scrap.set(ModComponents.ROBOT_DATA, data);
		dropStack(scrap);
		playSound(SoundEvents.BLOCK_ANVIL_USE, 0.7f, 0.8f);
		player.sendMessage(Text.translatable("message.surrogate.salvaged"), true);
		discard();
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		inventory.readNbtList(nbt.getList("Items", NbtElement.COMPOUND_TYPE), getRegistryManager());
		this.dataTracker.set(PLATING, nbt.getInt("Plating"));
		this.dataTracker.set(BATTERY, nbt.getInt("Battery"));
		this.dataTracker.set(CARGO_TIER, nbt.getInt("CargoTier"));
		this.dataTracker.set(FABRICATOR, nbt.getBoolean("Fabricator"));
		this.dataTracker.set(MODULES, nbt.getInt("Modules"));
		robotUuid = nbt.containsUuid("RobotUuid") ? nbt.getUuid("RobotUuid") : null;
		robotName = nbt.contains("RobotName", NbtElement.STRING_TYPE)
				? Text.Serialization.fromJson(nbt.getString("RobotName"), getRegistryManager()) : null;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.put("Items", inventory.toNbtList(getRegistryManager()));
		nbt.putInt("Plating", this.dataTracker.get(PLATING));
		nbt.putInt("Battery", this.dataTracker.get(BATTERY));
		nbt.putInt("CargoTier", this.dataTracker.get(CARGO_TIER));
		nbt.putBoolean("Fabricator", this.dataTracker.get(FABRICATOR));
		nbt.putInt("Modules", this.dataTracker.get(MODULES));
		if (robotUuid != null) nbt.putUuid("RobotUuid", robotUuid);
		if (robotName != null) nbt.putString("RobotName", Text.Serialization.toJsonString(robotName, getRegistryManager()));
	}
}
