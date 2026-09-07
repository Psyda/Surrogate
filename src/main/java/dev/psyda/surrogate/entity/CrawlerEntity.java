package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.hazard.AcidRain;
import dev.psyda.surrogate.item.CrawlerModuleItem;
import dev.psyda.surrogate.registry.ModItems;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The crawler's hull: a big, slow, indestructible tracked body that the server drives from whatever is
 * holding its controls (a rider in the driver's seat for now; the helm inside the crawler's own room once
 * that exists, see docs/DESIGN-crawler.md). It has a heading, a speed and a charge. It turns like a tank,
 * only while the tracks are moving; it takes a one block step as a ramp and stops dead at anything taller;
 * it cannot be hurt, only run flat. The inside is not in here: the hull is the outside of the crawler.
 */
public class CrawlerEntity extends Entity {
	public static final float WIDTH = 5f;
	public static final float HEIGHT = 3f;
	/** How far the model reaches ahead of and behind the centre; the collision box is only {@link #WIDTH} square. */
	public static final float HALF_LENGTH = 4f;
	private static final float RAD = (float) (Math.PI / 180.0);

	private static final TrackedData<Integer> ENERGY = DataTracker.registerData(CrawlerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> SPEED = DataTracker.registerData(CrawlerEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> DOCKED = DataTracker.registerData(CrawlerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** What is bolted to the hull, one bit per {@link CrawlerModule}. One field: the hull's sync slots are not free. */
	private static final TrackedData<Integer> MODULES = DataTracker.registerData(CrawlerEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** The controls, as last set: throttle ahead positive, steer left positive; they lapse if nobody holds them. */
	private float throttle;
	private float steer;
	private int controlAge;
	/** Which cabin in the pocket dimension is this hull's inside, or -1 before anyone has boarded. */
	private int interior = -1;

	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private double lerpYaw;
	private double lerpPitch;

	public CrawlerEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(ENERGY, 0);
		builder.add(SPEED, 0f);
		builder.add(DOCKED, false);
		builder.add(MODULES, 0);
	}

	// ------------------------------------------------------------------ state

	public int getEnergy() {
		return this.dataTracker.get(ENERGY);
	}

	public int getEnergyCapacity() {
		return Math.max(1, Surrogate.CONFIG.crawlerEnergyCapacity);
	}

	public float getEnergyFraction() {
		return MathHelper.clamp((float) getEnergy() / getEnergyCapacity(), 0f, 1f);
	}

	public void setEnergy(int energy) {
		this.dataTracker.set(ENERGY, MathHelper.clamp(energy, 0, getEnergyCapacity()));
	}

	/** Adds what fits and returns how much did. */
	public int addEnergy(int amount) {
		int before = getEnergy();
		setEnergy(before + amount);
		return getEnergy() - before;
	}

	public void drainEnergy(int amount) {
		setEnergy(getEnergy() - amount);
	}

	/** Signed ground speed in blocks per tick, ahead positive. */
	public float getSpeed() {
		return this.dataTracker.get(SPEED);
	}

	private void setSpeed(float speed) {
		this.dataTracker.set(SPEED, speed);
	}

	public boolean isDocked() {
		return this.dataTracker.get(DOCKED);
	}

	public void setDocked(boolean docked) {
		this.dataTracker.set(DOCKED, docked);
		if (docked) setSpeed(0f);
	}

	public boolean hasModule(CrawlerModule module) {
		return (this.dataTracker.get(MODULES) & (1 << module.ordinal())) != 0;
	}

	public void setModule(CrawlerModule module, boolean fitted) {
		int mask = this.dataTracker.get(MODULES);
		this.dataTracker.set(MODULES, fitted ? mask | (1 << module.ordinal()) : mask & ~(1 << module.ordinal()));
	}

	/** Ceramic panels over the hull: the belt's rain runs off instead of in. */
	public boolean hasCladding() {
		return hasModule(CrawlerModule.CLADDING);
	}

	public void setCladding(boolean fitted) {
		setModule(CrawlerModule.CLADDING, fitted);
	}

	public int getInterior() {
		return interior;
	}

	public void setInterior(int slot) {
		this.interior = slot;
	}

	/** The heading a compass would show, 0 north, 90 east. */
	public float getHeading() {
		return MathHelper.floorMod(getYaw() + 180f, 360f);
	}

	/** Sets the controls for this tick; they go slack two ticks after the last call. */
	public void setControls(float throttle, float steer) {
		this.throttle = MathHelper.clamp(throttle, -1f, 1f);
		this.steer = MathHelper.clamp(steer, -1f, 1f);
		this.controlAge = 0;
	}

	/** A point in the hull's own frame (x to port, z ahead), rotated into the world. */
	public Vec3d local(double x, double y, double z) {
		return new Vec3d(x, y, z).rotateY(-getYaw() * RAD);
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) {
			lerpTick();
			return;
		}
		if (controlAge++ > 1) {
			throttle = 0f;
			steer = 0f;
		}
		drive();
	}

	private void drive() {
		SurrogateConfig cfg = Surrogate.CONFIG;
		float max = (float) Math.max(0.01, cfg.crawlerSpeed);
		boolean powered = getEnergy() > 0 && !isDocked();
		float speed = getSpeed();
		float target = !powered ? 0f : throttle < 0f ? throttle * max * (float) cfg.crawlerReverseFactor : throttle * max;
		float accel = max / 40f;
		speed += MathHelper.clamp(target - speed, -accel, accel);
		if (target == 0f && Math.abs(speed) < accel) speed = 0f;

		// A tank turns by driving one track faster than the other: no motion, no turn.
		if (speed != 0f && steer != 0f) {
			float rate = (float) cfg.crawlerTurnDegreesPerTick * MathHelper.clamp(Math.abs(speed) / max, 0.3f, 1f);
			setYaw(getYaw() - steer * rate * Math.signum(speed));
		}

		Vec3d forward = local(0.0, 0.0, 1.0);
		double vy = getVelocity().y - 0.04;
		setVelocity(forward.x * speed, vy, forward.z * speed);
		move(MovementType.SELF, getVelocity());
		if (this.horizontalCollision) speed = 0f;
		setSpeed(speed);

		if (powered && (throttle != 0f || speed != 0f)) drainEnergy(cfg.crawlerDrivePerTick);
		else if (getEnergy() > 0) drainEnergy(cfg.crawlerIdleDrainPerTick);
		if (cfg.crawlerSolarPerTick > 0 && getWorld().isDay() && getWorld().isSkyVisible(getBlockPos().up((int) HEIGHT))) addEnergy(cfg.crawlerSolarPerTick);
	}

	// ------------------------------------------------------------------ physics

	@Override
	public float getStepHeight() {
		// A one block step is a ramp; two is a wall.
		return 1.0f;
	}

	@Override
	public boolean isCollidable() {
		return true;
	}

	@Override
	public boolean collidesWith(Entity other) {
		return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other);
	}

	@Override
	public boolean canHit() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Nothing on Sallow dents it; it can only run flat.
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0 * 256.0;
	}

	// ------------------------------------------------------------------ seats

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		// Nobody rides the outside: the crew is in the cabin.
		return false;
	}

	/**
	 * Using the hull: a power cell charges it; a piloted chassis is taken back aboard into the bay; anyone
	 * else goes in through the door, which is to say into the cabin.
	 */
	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(ModItems.POWER_CELL)) {
			if (getWorld().isClient) return ActionResult.SUCCESS;
			int added = addEnergy(Surrogate.CONFIG.powerCellEnergy);
			if (added <= 0) {
				player.sendMessage(Text.translatable("message.surrogate.crawler.full").formatted(Formatting.GRAY), true);
				return ActionResult.CONSUME;
			}
			if (!player.isCreative()) stack.decrement(1);
			playSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
			player.sendMessage(Text.translatable("message.surrogate.crawler.charged", Math.round(getEnergyFraction() * 100f)).formatted(Formatting.AQUA), true);
			return ActionResult.CONSUME;
		}
		if (stack.isOf(ModItems.HULL_PLATING) || stack.isOf(ModItems.REPAIR_KIT)) {
			// The only way a hull's corrosion ever comes down, and the only way a seized one moves again.
			if (getWorld().isClient) return ActionResult.SUCCESS;
			if (AcidRain.mend(player, stack, this)) return ActionResult.CONSUME;
			player.sendMessage(Text.translatable("message.surrogate.acid.hull_sound").formatted(Formatting.GRAY), true);
			return ActionResult.CONSUME;
		}
		if (stack.getItem() instanceof CrawlerModuleItem item) {
			if (getWorld().isClient) return ActionResult.SUCCESS;
			return tryFit(player, stack, item.getModule()) ? ActionResult.CONSUME : ActionResult.PASS;
		}
		if (player.shouldCancelInteraction()) return ActionResult.PASS;
		if (getWorld().isClient) return ActionResult.SUCCESS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
		if (RobotEntity.isPiloting(player)) return CrawlerInterior.recall(serverPlayer, this) ? ActionResult.CONSUME : ActionResult.PASS;
		return CrawlerInterior.board(serverPlayer, this) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	/** Bolting a module to the hull: it has to be standing still, and it only takes one of each. */
	public boolean tryFit(PlayerEntity player, ItemStack stack, CrawlerModule module) {
		if (getSpeed() != 0f) {
			player.sendMessage(Text.translatable("message.surrogate.crawler.module_moving").formatted(Formatting.GRAY), true);
			return false;
		}
		if (hasModule(module)) {
			player.sendMessage(Text.translatable("message.surrogate.crawler.module_fitted",
					Text.translatable(module.translationKey())).formatted(Formatting.GRAY), true);
			return false;
		}
		Text name = stack.getName();
		setModule(module, true);
		if (!player.isCreative()) stack.decrement(1);
		playSound(SoundEvents.BLOCK_ANVIL_USE, 0.8f, 1.3f);
		player.sendMessage(Text.translatable("message.surrogate.crawler.module_added", name).formatted(Formatting.AQUA), true);
		return true;
	}

	// ------------------------------------------------------------------ client interpolation

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		this.lerpX = x;
		this.lerpY = y;
		this.lerpZ = z;
		this.lerpYaw = yaw;
		this.lerpPitch = pitch;
		this.lerpSteps = interpolationSteps;
	}

	private void lerpTick() {
		if (this.lerpSteps > 0) {
			lerpPosAndRotation(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYaw, this.lerpPitch);
			this.lerpSteps--;
		}
	}

	@Override
	public double getLerpTargetX() {
		return this.lerpSteps > 0 ? this.lerpX : getX();
	}

	@Override
	public double getLerpTargetY() {
		return this.lerpSteps > 0 ? this.lerpY : getY();
	}

	@Override
	public double getLerpTargetZ() {
		return this.lerpSteps > 0 ? this.lerpZ : getZ();
	}

	@Override
	public float getLerpTargetYaw() {
		return this.lerpSteps > 0 ? (float) this.lerpYaw : getYaw();
	}

	@Override
	public float getLerpTargetPitch() {
		return this.lerpSteps > 0 ? (float) this.lerpPitch : getPitch();
	}

	// ------------------------------------------------------------------ persistence

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Energy", getEnergy());
		nbt.putFloat("Speed", getSpeed());
		nbt.putBoolean("Docked", isDocked());
		nbt.putInt("Modules", this.dataTracker.get(MODULES));
		nbt.putInt("Interior", interior);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		setEnergy(nbt.getInt("Energy"));
		setSpeed(nbt.getFloat("Speed"));
		setDocked(nbt.getBoolean("Docked"));
		this.dataTracker.set(MODULES, nbt.getInt("Modules"));
		interior = nbt.contains("Interior") ? nbt.getInt("Interior") : -1;
	}

	@Override
	public void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
		if (!isSilent()) getWorld().playSound(null, getX(), getY(), getZ(), sound, SoundCategory.NEUTRAL, volume, pitch);
	}
}
