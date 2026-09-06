package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.item.RobotUpgradeItem;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.pilot.RobotRegistry;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A one block tall piloted chassis. It has no AI of its own: when a pilot is linked the player rides it and
 * steers it exactly like a horse, when nobody is linked it just sits there burning (or not burning) power.
 *
 * <p>Robots never regenerate. Health only comes back through repair kits or a charging dock, and both only
 * work on a powered-down chassis. When health hits zero the robot is replaced by a {@link RobotWreckEntity}
 * holding whatever cargo it carried.
 */
public class RobotEntity extends MobEntity {
	public static final int CARGO_SIZE = 41;
	public static final int OFFHAND_SLOT = 40;
	/** Hitbox used for the pilot while linked: small enough to fit inside the chassis, camera at the lens. */
	public static final EntityDimensions PILOT_DIMENSIONS = EntityDimensions.changing(0.6f, 0.9f).withEyeHeight(0.75f);

	private static final TrackedData<Integer> STATE = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> ENERGY = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> PROGRESS = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> PLATING = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BATTERY = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CARGO_TIER = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> FABRICATOR = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** The countermeasures bolted on, one bit per {@link RobotModule}. One field, because sync slots are not free. */
	private static final TrackedData<Integer> MODULES = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> CONTAMINATION = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Optional<UUID>> PILOT = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	/** Driven by a script rather than a pilot: the vanilla move and look controls are live. */
	private static final TrackedData<Boolean> SCRIPTED = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Which paint this chassis wears. Index into {@link dev.psyda.surrogate.entity.RobotPaint}; 0 is the player's own. */
	private static final TrackedData<Integer> PAINT = DataTracker.registerData(RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private final DefaultedList<ItemStack> cargo = DefaultedList.ofSize(CARGO_SIZE, ItemStack.EMPTY);
	private long lastTickTime = -1L;
	private boolean catchUpPending;

	public RobotEntity(EntityType<? extends RobotEntity> type, World world) {
		super(type, world);
		this.setPersistent();
		installPilotControls();
	}

	/** Vanilla controls would fight the pilot for the head and body; replace them with inert ones. */
	private void installPilotControls() {
		this.lookControl = new LookControl(this) {
			@Override
			public void tick() {
			}
		};
		this.moveControl = new MoveControl(this) {
			@Override
			public void tick() {
			}
		};
	}

	// ------------------------------------------------------------------ scripted driving

	public boolean isScripted() {
		return this.dataTracker.get(SCRIPTED);
	}

	/**
	 * Whether a player may take this chassis: key a card to it, dock it, wrench it up, or dive into it. A
	 * scripted chassis belongs to an NPC and is driven by a script, so binding a chair to one would strand
	 * the pilot in a body no dive session can ever release.
	 */
	public boolean isClaimable() {
		return !isScripted();
	}

	/** The paint this chassis wears, so an NPC's is never mistaken for the player's. */
	public RobotPaint getPaint() {
		return RobotPaint.byId(this.dataTracker.get(PAINT));
	}

	public void setPaint(RobotPaint paint) {
		this.dataTracker.set(PAINT, paint.ordinal());
	}

	/**
	 * Hands the chassis to the vanilla mob AI so a script can drive it with {@link #driveTo}. Nobody can
	 * open its hold or fit anything to it while it is scripted.
	 */
	public void setScripted(boolean scripted) {
		this.dataTracker.set(SCRIPTED, scripted);
		if (scripted) {
			this.moveControl = new MoveControl(this);
			this.lookControl = new LookControl(this);
		} else {
			getNavigation().stop();
			installPilotControls();
		}
	}

	/** Pathfinds to {@code target}. The speed multiplier compensates for the chassis' low walk attribute. */
	public void driveTo(Vec3d target) {
		if (!isScripted() || getState() != RobotState.ONLINE) return;
		getNavigation().startMovingTo(target.x, target.y, target.z, 2.75);
	}

	public void stopDriving() {
		getNavigation().stop();
	}

	public boolean hasArrived(Vec3d target) {
		double dx = getX() - target.x;
		double dz = getZ() - target.z;
		return dx * dx + dz * dz < 1.2;
	}

	public void lookAt(Entity target) {
		if (isScripted()) getLookControl().lookAt(target, 30f, 30f);
	}

	public static DefaultAttributeContainer.Builder createRobotAttributes() {
		SurrogateConfig cfg = Surrogate.CONFIG;
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, cfg.robotBaseHealth)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, cfg.robotSpeed)
				.add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0)
				// Scripted chassis walk in from the dunes; vanilla paths stop at 16 blocks.
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6)
				.add(EntityAttributes.GENERIC_ARMOR, 2.0)
				.add(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, cfg.fallDamageMultiplier)
				.add(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 4.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(STATE, RobotState.OFFLINE.ordinal());
		builder.add(PAINT, 0);
		builder.add(ENERGY, 0);
		builder.add(PROGRESS, 0);
		builder.add(PLATING, 0);
		builder.add(BATTERY, 0);
		builder.add(CARGO_TIER, 0);
		builder.add(FABRICATOR, false);
		builder.add(MODULES, 0);
		builder.add(CONTAMINATION, 0f);
		builder.add(PILOT, Optional.empty());
		builder.add(SCRIPTED, false);
	}

	// ------------------------------------------------------------------ state accessors

	public RobotState getState() {
		return RobotState.byId(this.dataTracker.get(STATE));
	}

	public void setState(RobotState state) {
		this.dataTracker.set(STATE, state.ordinal());
	}

	public int getProgress() {
		return this.dataTracker.get(PROGRESS);
	}

	private void setProgress(int progress) {
		this.dataTracker.set(PROGRESS, progress);
	}

	public int getEnergy() {
		return this.dataTracker.get(ENERGY);
	}

	public int getEnergyCapacity() {
		return Surrogate.CONFIG.capacityForBatteryTier(getBatteryTier());
	}

	public float getEnergyFraction() {
		int capacity = getEnergyCapacity();
		return capacity <= 0 ? 0f : (float) getEnergy() / capacity;
	}

	public void setEnergy(int energy) {
		this.dataTracker.set(ENERGY, MathHelper.clamp(energy, 0, getEnergyCapacity()));
	}

	/** @return how much was actually accepted */
	public int addEnergy(int amount) {
		int before = getEnergy();
		setEnergy(before + amount);
		return getEnergy() - before;
	}

	public void drainEnergy(int amount) {
		setEnergy(getEnergy() - amount);
	}

	public int getPlatingTier() {
		return this.dataTracker.get(PLATING);
	}

	public void setPlatingTier(int tier) {
		this.dataTracker.set(PLATING, tier);
		applyMaxHealth();
	}

	public int getBatteryTier() {
		return this.dataTracker.get(BATTERY);
	}

	public void setBatteryTier(int tier) {
		this.dataTracker.set(BATTERY, tier);
		setEnergy(getEnergy());
	}

	public int getCargoTier() {
		return this.dataTracker.get(CARGO_TIER);
	}

	public void setCargoTier(int tier) {
		this.dataTracker.set(CARGO_TIER, tier);
	}

	public boolean hasFabricator() {
		return this.dataTracker.get(FABRICATOR);
	}

	public void setFabricator(boolean fitted) {
		this.dataTracker.set(FABRICATOR, fitted);
	}

	public boolean hasModule(RobotModule module) {
		return (this.dataTracker.get(MODULES) & (1 << module.ordinal())) != 0;
	}

	public void setModule(RobotModule module, boolean fitted) {
		int mask = this.dataTracker.get(MODULES);
		this.dataTracker.set(MODULES, fitted ? mask | (1 << module.ordinal()) : mask & ~(1 << module.ordinal()));
	}

	/** Every module fitted, in enum order, for a status line or a menu. */
	public List<RobotModule> getModules() {
		List<RobotModule> fitted = new ArrayList<>();
		for (RobotModule module : RobotModule.all()) {
			if (hasModule(module)) fitted.add(module);
		}
		return fitted;
	}

	/** Outside grime on the hull, 0 to 1. Only a decon shower takes it off. */
	public float getContamination() {
		return this.dataTracker.get(CONTAMINATION);
	}

	public void setContamination(float amount) {
		this.dataTracker.set(CONTAMINATION, MathHelper.clamp(amount, 0f, 1f));
	}

	/** Main inventory slots the pilot can use; the rest are sealed bays until a cargo bay upgrade is fitted. */
	public int getCargoSlots() {
		return Surrogate.CONFIG.cargoSlots(getCargoTier());
	}

	public boolean isCargoSlotOpen(int slot) {
		return slot < getCargoSlots() || slot == OFFHAND_SLOT;
	}

	private void applyMaxHealth() {
		float max = Surrogate.CONFIG.maxHealthForPlatingTier(getPlatingTier());
		EntityAttributeInstance instance = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (instance != null && instance.getBaseValue() != max) {
			instance.setBaseValue(max);
		}
		if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
	}

	public Optional<UUID> getPilotUuid() {
		return this.dataTracker.get(PILOT);
	}

	public void setPilot(@Nullable UUID pilot) {
		this.dataTracker.set(PILOT, Optional.ofNullable(pilot));
	}

	public boolean isPiloted() {
		return getPilotUuid().isPresent();
	}

	public boolean isPilot(Entity entity) {
		return getPilotUuid().map(uuid -> uuid.equals(entity.getUuid())).orElse(false);
	}

	@Nullable
	public ServerPlayerEntity getPilotPlayer() {
		if (!(getWorld() instanceof ServerWorld world)) return null;
		return getPilotUuid().map(uuid -> world.getServer().getPlayerManager().getPlayer(uuid)).orElse(null);
	}

	/** True when {@code entity} is currently linked into a chassis and riding it. Works on both sides. */
	public static boolean isPiloting(@Nullable Entity entity) {
		return entity != null && entity.getVehicle() instanceof RobotEntity robot && robot.isPilot(entity);
	}

	// ------------------------------------------------------------------ cargo

	public ItemStack getCargo(int slot) {
		return cargo.get(slot);
	}

	public void setCargo(int slot, ItemStack stack) {
		cargo.set(slot, stack);
	}

	public DefaultedList<ItemStack> getCargoList() {
		return cargo;
	}

	public void clearCargo() {
		Collections.fill(cargo, ItemStack.EMPTY);
	}

	/** Opens the hold as a chest for a player standing next to a parked chassis. */
	public void openCargo(ServerPlayerEntity player) {
		RobotCargoInventory inventory = new RobotCargoInventory(this);
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, playerInventory, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X5, syncId, playerInventory, inventory, 5),
				Text.translatable("screen.surrogate.cargo", getName())));
	}

	// ------------------------------------------------------------------ power lifecycle

	public boolean canBoot() {
		return getState() == RobotState.OFFLINE && getEnergy() >= Surrogate.CONFIG.bootCost;
	}

	public boolean beginBoot() {
		if (!canBoot()) return false;
		drainEnergy(Surrogate.CONFIG.bootCost);
		setState(RobotState.BOOTING);
		setProgress(0);
		playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
		return true;
	}

	public void beginShutdown() {
		RobotState state = getState();
		if (state == RobotState.ONLINE || state == RobotState.BOOTING) {
			setState(RobotState.SHUTTING_DOWN);
			setProgress(0);
			playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.1f);
		}
	}

	public void forceOffline() {
		setState(RobotState.OFFLINE);
		setProgress(0);
	}

	private void powerFailure() {
		ServerPlayerEntity pilot = getPilotPlayer();
		forceOffline();
		setEnergy(0);
		playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.6f);
		if (pilot != null) {
			PilotManager.disconnect(pilot, false, Text.translatable("message.surrogate.power_failure"), true);
		}
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			clientTick();
		} else {
			serverTick();
		}
	}

	private void serverTick() {
		SurrogateConfig cfg = Surrogate.CONFIG;
		if (catchUpPending) {
			// A running chassis in an unloaded chunk still burns power: settle the bill when it loads.
			catchUpPending = false;
			long elapsed = getWorld().getTime() - lastTickTime;
			if (elapsed > 0 && getState() == RobotState.ONLINE) {
				drainEnergy((int) Math.min(Integer.MAX_VALUE, elapsed * cfg.idleDrainPerTick));
			}
		}

		switch (getState()) {
			case BOOTING -> {
				int progress = getProgress() + 1;
				setProgress(progress);
				drainEnergy(cfg.idleDrainPerTick);
				if (progress >= cfg.bootTicks) {
					setState(RobotState.ONLINE);
					setProgress(0);
					playSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
					ServerPlayerEntity pilot = getPilotPlayer();
					if (pilot != null) pilot.sendMessage(Text.translatable("message.surrogate.link_established"), true);
				}
			}
			case ONLINE -> {
				ServerPlayerEntity pilot = getPilotPlayer();
				drainEnergy(pilot != null && pilot.isSprinting() ? cfg.sprintDrainPerTick : cfg.idleDrainPerTick);
			}
			case SHUTTING_DOWN -> {
				int progress = getProgress() + 1;
				setProgress(progress);
				if (progress >= cfg.shutdownTicks) {
					forceOffline();
					playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.9f);
				}
			}
			case OFFLINE -> {
			}
		}

		if (getEnergy() <= 0 && getState() != RobotState.OFFLINE) {
			powerFailure();
		}

		// The wastes stick to the hull. Sitting out in the open air, dust and sulfur build up until washed off.
		if (this.age % 20 == 0 && Atmosphere.isToxic(getWorld(), getBlockPos()) && Atmosphere.volumeAt(getWorld(), getBlockPos()) == null) {
			double seconds = Math.max(1.0, cfg.contaminationSeconds);
			setContamination(getContamination() + (float) (1.0 / seconds));
		}

		// A pilot that is no longer aboard (death, foreign teleport, logout) drops the link.
		Optional<UUID> pilot = getPilotUuid();
		if (pilot.isPresent()) {
			if (!(getFirstPassenger() instanceof PlayerEntity player) || !player.getUuid().equals(pilot.get())) {
				setPilot(null);
			}
		}

		// Report in on the first tick (a chassis summoned by command has no other way in) and every five seconds after.
		if ((this.age == 1 || this.age % 100 == 0) && getServer() != null) {
			RobotRegistry.get(getServer()).update(this);
		}
	}

	private void clientTick() {
		RobotState state = getState();
		if (state == RobotState.BOOTING && this.random.nextInt(3) == 0) {
			getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, getParticleX(0.6), getRandomBodyY(), getParticleZ(0.6), 0.0, 0.02, 0.0);
		}
		if (state != RobotState.OFFLINE && getHealth() < getMaxHealth() * 0.34f && this.random.nextInt(6) == 0) {
			getWorld().addParticle(ParticleTypes.SMOKE, getParticleX(0.5), getY() + 0.9, getParticleZ(0.5), 0.0, 0.05, 0.0);
		}
		if (getContamination() > 0.5f && this.random.nextInt(10) == 0) {
			getWorld().addParticle(ParticleTypes.MYCELIUM, getParticleX(0.7), getRandomBodyY(), getParticleZ(0.7), 0.0, 0.0, 0.0);
		}
	}

	// ------------------------------------------------------------------ riding / steering

	@Nullable
	@Override
	public LivingEntity getControllingPassenger() {
		if (getState() == RobotState.ONLINE && getFirstPassenger() instanceof PlayerEntity player && isPilot(player)) {
			return player;
		}
		return null;
	}

	@Override
	protected Vec3d getControlledMovementInput(PlayerEntity pilot, Vec3d movementInput) {
		return new Vec3d(pilot.sidewaysSpeed * 0.7, 0.0, pilot.forwardSpeed);
	}

	@Override
	protected void tickControlled(PlayerEntity pilot, Vec3d movementInput) {
		super.tickControlled(pilot, movementInput);
		setRotation(pilot.getYaw(), pilot.getPitch() * 0.5f);
		this.prevYaw = this.bodyYaw = this.headYaw = getYaw();
	}

	@Override
	protected float getSaddledSpeed(PlayerEntity pilot) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		float speed = (float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (pilot.isSprinting()) speed *= (float) cfg.sprintMultiplier;
		if (pilot.isSneaking()) speed *= (float) cfg.sneakMultiplier;
		return speed;
	}

	@Override
	public boolean canSprintAsVehicle() {
		return true;
	}

	@Override
	protected Vec3d getPassengerAttachmentPos(Entity passenger, EntityDimensions dimensions, float scaleFactor) {
		// The pilot sits at the chassis' feet so the (shrunken) player hitbox and camera live inside the robot.
		return new Vec3d(0.0, 0.01, 0.0);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return !hasPassengers() && passenger instanceof PlayerEntity;
	}

	/** Sneaking while piloting keeps the chassis from driving off ledges, same as a crouching player. */
	@Override
	protected Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type) {
		if (!(getControllingPassenger() instanceof PlayerEntity pilot) || !pilot.isSneaking() || movement.y > 0.0
				|| (type != MovementType.SELF && type != MovementType.PLAYER)) {
			return movement;
		}
		float step = getStepHeight();
		boolean aboveGround = isOnGround() || this.fallDistance < step && !isSpaceAroundEmpty(0.0, 0.0, step - this.fallDistance);
		if (!aboveGround) return movement;

		double d = movement.x;
		double e = movement.z;
		double h = Math.signum(d) * 0.05;
		double i = Math.signum(e) * 0.05;
		while (d != 0.0 && isSpaceAroundEmpty(d, 0.0, step)) {
			if (Math.abs(d) <= 0.05) {
				d = 0.0;
				break;
			}
			d -= h;
		}
		while (e != 0.0 && isSpaceAroundEmpty(0.0, e, step)) {
			if (Math.abs(e) <= 0.05) {
				e = 0.0;
				break;
			}
			e -= i;
		}
		while (d != 0.0 && e != 0.0 && isSpaceAroundEmpty(d, e, step)) {
			if (Math.abs(d) <= 0.05) d = 0.0;
			else d -= h;
			if (Math.abs(e) <= 0.05) e = 0.0;
			else e -= i;
		}
		return new Vec3d(d, movement.y, e);
	}

	private boolean isSpaceAroundEmpty(double offsetX, double offsetZ, float depth) {
		Box box = getBoundingBox();
		return getWorld().isSpaceEmpty(this, new Box(box.minX + offsetX, box.minY - depth - 1.0E-5F, box.minZ + offsetZ,
				box.maxX + offsetX, box.minY, box.maxZ + offsetZ));
	}

	// ------------------------------------------------------------------ interaction

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		boolean client = getWorld().isClient;
		if (isScripted()) {
			// Somebody else's machine, and they are using it.
			if (!client) sendStatus(player);
			return ActionResult.success(client);
		}
		if (stack.isOf(ModItems.REPAIR_KIT)) {
			if (!client) tryRepair(player, stack);
			return ActionResult.success(client);
		}
		if (stack.isOf(ModItems.POWER_CELL)) {
			if (!client) tryCharge(player, stack);
			return ActionResult.success(client);
		}
		if (stack.getItem() instanceof RobotUpgradeItem upgrade) {
			if (!client) tryUpgrade(player, stack, upgrade.getKind());
			return ActionResult.success(client);
		}
		if (stack.isEmpty() && player.isSneaking() && !isPiloted()) {
			if (player instanceof ServerPlayerEntity serverPlayer) openCargo(serverPlayer);
			return ActionResult.success(client);
		}
		if (stack.isEmpty() && !player.isSneaking()) {
			if (!client) sendStatus(player);
			return ActionResult.success(client);
		}
		return super.interactMob(player, hand);
	}

	public boolean tryRepair(PlayerEntity player, ItemStack kit) {
		if (getState() != RobotState.OFFLINE || isPiloted()) {
			player.sendMessage(Text.translatable("message.surrogate.repair_requires_offline"), true);
			return false;
		}
		if (getHealth() >= getMaxHealth()) {
			player.sendMessage(Text.translatable("message.surrogate.no_damage"), true);
			return false;
		}
		repair(Surrogate.CONFIG.repairKitHealth);
		if (!player.getAbilities().creativeMode) kit.decrement(1);
		playSound(SoundEvents.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.0f);
		player.sendMessage(Text.translatable("message.surrogate.repaired", formatHealth(getHealth()), formatHealth(getMaxHealth())), true);
		return true;
	}

	public boolean tryCharge(PlayerEntity player, ItemStack cell) {
		if (getEnergy() >= getEnergyCapacity()) {
			player.sendMessage(Text.translatable("message.surrogate.already_charged"), true);
			return false;
		}
		addEnergy(Surrogate.CONFIG.powerCellEnergy);
		if (!player.getAbilities().creativeMode) cell.decrement(1);
		playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.6f);
		player.sendMessage(Text.translatable("message.surrogate.charged", Math.round(getEnergyFraction() * 100)), true);
		return true;
	}

	public boolean tryUpgrade(PlayerEntity player, ItemStack stack, RobotUpgradeItem.Kind kind) {
		if (getState() != RobotState.OFFLINE || isPiloted()) {
			player.sendMessage(Text.translatable("message.surrogate.upgrade_requires_offline"), true);
			return false;
		}
		SurrogateConfig cfg = Surrogate.CONFIG;
		switch (kind) {
			case PLATING_MK1 -> {
				if (getPlatingTier() != 0) {
					player.sendMessage(Text.translatable("message.surrogate.plating_wrong_tier"), true);
					return false;
				}
				float oldMax = getMaxHealth();
				setPlatingTier(1);
				setHealth(getHealth() + (getMaxHealth() - oldMax));
			}
			case PLATING_MK2 -> {
				if (getPlatingTier() != 1) {
					player.sendMessage(Text.translatable("message.surrogate.plating_wrong_tier"), true);
					return false;
				}
				float oldMax = getMaxHealth();
				setPlatingTier(2);
				setHealth(getHealth() + (getMaxHealth() - oldMax));
			}
			case BATTERY -> {
				if (getBatteryTier() >= 2) {
					player.sendMessage(Text.translatable("message.surrogate.battery_max"), true);
					return false;
				}
				setBatteryTier(getBatteryTier() + 1);
			}
			case CARGO_BAY -> {
				if (getCargoTier() >= cfg.cargoBayMaxTier) {
					player.sendMessage(Text.translatable("message.surrogate.cargo_max"), true);
					return false;
				}
				setCargoTier(getCargoTier() + 1);
			}
			case FABRICATOR -> {
				if (hasFabricator()) {
					player.sendMessage(Text.translatable("message.surrogate.fabricator_fitted"), true);
					return false;
				}
				setFabricator(true);
			}
			case RELAY -> {
				if (!fitModule(player, RobotModule.RELAY)) return false;
			}
			case DAMPER -> {
				if (!fitModule(player, RobotModule.DAMPER)) return false;
			}
			case COATING -> {
				if (!fitModule(player, RobotModule.COATING)) return false;
			}
			case SHIELD -> {
				if (!fitModule(player, RobotModule.SHIELD)) return false;
			}
		}
		if (!player.getAbilities().creativeMode) stack.decrement(1);
		playSound(SoundEvents.BLOCK_ANVIL_USE, 0.8f, 1.3f);
		player.sendMessage(Text.translatable("message.surrogate.upgraded", stack.getName()), true);
		return true;
	}

	/** One bay per module: the second one has nowhere to go. */
	private boolean fitModule(PlayerEntity player, RobotModule module) {
		if (hasModule(module)) {
			player.sendMessage(Text.translatable("message.surrogate.module_fitted", Text.translatable(module.translationKey())), true);
			return false;
		}
		setModule(module, true);
		return true;
	}

	private void sendStatus(PlayerEntity player) {
		player.sendMessage(Text.translatable("message.surrogate.status",
				getName(),
				Text.translatable(getState().translationKey()),
				formatHealth(getHealth()), formatHealth(getMaxHealth()),
				Math.round(getEnergyFraction() * 100),
				getPlatingTier(), getBatteryTier(), getCargoSlots(),
				Text.translatable(hasFabricator() ? "message.surrogate.status.fabricator" : "message.surrogate.status.no_fabricator"),
				Math.round(getContamination() * 100)), false);
		List<RobotModule> modules = getModules();
		if (!modules.isEmpty()) {
			MutableText list = Text.empty();
			for (int i = 0; i < modules.size(); i++) {
				if (i > 0) list.append(Text.literal(", "));
				list.append(Text.translatable(modules.get(i).translationKey()));
			}
			player.sendMessage(Text.translatable("message.surrogate.status.modules", list), false);
		}
	}

	private static String formatHealth(float health) {
		return health == (int) health ? Integer.toString((int) health) : String.format("%.1f", health);
	}

	// ------------------------------------------------------------------ damage / death

	/** Robots do not regenerate. See {@link #repair(float)}. */
	@Override
	public void heal(float amount) {
	}

	public void repair(float amount) {
		setHealth(Math.min(getHealth() + amount, getMaxHealth()));
	}

	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		return false;
	}

	/**
	 * A chassis nobody is linked to is inert scrap as far as anything with teeth or fists is concerned: it
	 * cannot be attacked. Explosions, fire, lava and falls still get it, so leaving it outside is not free.
	 */
	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (super.isInvulnerableTo(source)
				|| source.isIn(DamageTypeTags.IS_DROWNING)
				|| source.isIn(DamageTypeTags.IS_FREEZING)
				|| source.isOf(DamageTypes.STARVE)) {
			return true;
		}
		return !isPiloted() && source.getAttacker() != null && !source.isIn(DamageTypeTags.IS_EXPLOSION);
	}

	/**
	 * While piloted the pilot's own (shrunken) hitbox is the target for attacks and clicks; hits on it are
	 * redirected to the chassis. Hiding the chassis from raycasts also keeps the pilot's crosshair from
	 * landing on their own vehicle, since the camera sits inside its hitbox.
	 */
	@Override
	public boolean canHit() {
		return !isRemoved() && !isPiloted();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected boolean shouldSwimInFluids() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean isPersistent() {
		return true;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isAffectedBySplashPotions() {
		return false;
	}

	/** A pilot cannot follow through a portal, so a crewed chassis stays put instead of stranding them. */
	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return !hasPassengers() && super.canUsePortals(allowVehicles);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 0.35f, 1.4f);
	}

	@Override
	protected void dropLoot(DamageSource source, boolean causedByPlayer) {
		// The wreck holds everything; there is no loot table.
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		if (getWorld().isClient || this.dead) return;
		this.dead = true;
		wreck();
	}

	/** Replaces this chassis with a wreck entity carrying its cargo and ejects the pilot. */
	public void wreck() {
		if (!(getWorld() instanceof ServerWorld world)) return;
		RobotWreckEntity wreckEntity = new RobotWreckEntity(ModEntities.ROBOT_WRECK, world);
		wreckEntity.refreshPositionAndAngles(getX(), getY(), getZ(), getYaw(), 0.0f);
		wreckEntity.setSalvageData(getUuid(), getPlatingTier(), getBatteryTier(), getCargoTier(), hasFabricator(),
				this.dataTracker.get(MODULES), getCustomName());

		ServerPlayerEntity pilot = getPilotPlayer();
		if (pilot != null) {
			wreckEntity.fillFrom(PilotManager.takeCargoFromPilot(pilot));
			PilotManager.disconnect(pilot, false, Text.translatable("message.surrogate.chassis_destroyed"), true);
		} else {
			wreckEntity.fillFrom(cargo);
		}
		setPilot(null);
		clearCargo();

		world.spawnEntity(wreckEntity);
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.NEUTRAL, 0.8f, 1.3f);
		world.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		RobotRegistry.get(world.getServer()).markWrecked(getUuid());
		discard();
	}

	// ------------------------------------------------------------------ persistence

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("State", getState().name());
		nbt.putInt("Energy", getEnergy());
		nbt.putInt("Progress", getProgress());
		nbt.putInt("Plating", getPlatingTier());
		nbt.putInt("Battery", getBatteryTier());
		nbt.putInt("CargoTier", getCargoTier());
		nbt.putBoolean("Fabricator", hasFabricator());
		nbt.putInt("Modules", this.dataTracker.get(MODULES));
		nbt.putFloat("Contamination", getContamination());
		nbt.putBoolean("Scripted", isScripted());
		nbt.putInt("Paint", this.dataTracker.get(PAINT));
		getPilotUuid().ifPresent(uuid -> nbt.putUuid("Pilot", uuid));
		NbtCompound cargoNbt = new NbtCompound();
		Inventories.writeNbt(cargoNbt, cargo, getRegistryManager());
		nbt.put("Cargo", cargoNbt);
		nbt.putLong("LastTickTime", getWorld().getTime());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(PLATING, nbt.getInt("Plating"));
		this.dataTracker.set(BATTERY, nbt.getInt("Battery"));
		this.dataTracker.set(CARGO_TIER, nbt.getInt("CargoTier"));
		this.dataTracker.set(FABRICATOR, nbt.getBoolean("Fabricator"));
		this.dataTracker.set(MODULES, nbt.getInt("Modules"));
		setContamination(nbt.getFloat("Contamination"));
		if (nbt.getBoolean("Scripted") != isScripted()) setScripted(nbt.getBoolean("Scripted"));
		this.dataTracker.set(PAINT, nbt.getInt("Paint"));
		applyMaxHealth();
		if (nbt.contains("State")) setState(RobotState.byName(nbt.getString("State")));
		setEnergy(nbt.getInt("Energy"));
		setProgress(nbt.getInt("Progress"));
		setPilot(nbt.containsUuid("Pilot") ? nbt.getUuid("Pilot") : null);
		clearCargo();
		if (nbt.contains("Cargo")) Inventories.readNbt(nbt.getCompound("Cargo"), cargo, getRegistryManager());
		if (nbt.contains("LastTickTime")) {
			lastTickTime = nbt.getLong("LastTickTime");
			catchUpPending = true;
		}
	}
}
