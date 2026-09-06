package dev.psyda.surrogate.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * The sample vehicle: a company-pattern sounding rocket that stands on the pad, takes the assay's samples
 * into its capsule, and goes up exactly once.
 *
 * <p>It is not a vehicle anyone rides. It exists so the gantry has something to build, the payload has
 * somewhere to sit, and the launch has something to leave. The climb is run here on the server so the client
 * sees it through ordinary entity interpolation instead of a fake particle column.
 */
public class RocketEntity extends Entity {
	public static final float WIDTH = 2.0f;
	public static final float HEIGHT = 7.0f;
	/** How long the climb lasts before the rocket is out of sight and discards itself. */
	public static final int CLIMB_TICKS = 220;

	/** Whether the capsule has its payload. Drives the capsule light. */
	private static final TrackedData<Boolean> LOADED = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	/** Ticks since ignition, or 0 while it is still standing on the pad. */
	private static final TrackedData<Integer> CLIMB = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public RocketEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(LOADED, false);
		builder.add(CLIMB, 0);
	}

	public boolean isLoaded() {
		return this.dataTracker.get(LOADED);
	}

	public void setLoaded(boolean loaded) {
		this.dataTracker.set(LOADED, loaded);
	}

	public boolean isLaunching() {
		return this.dataTracker.get(CLIMB) > 0;
	}

	/** Lights it. The rocket climbs on its own from here and is gone in {@link #CLIMB_TICKS}. */
	public void ignite() {
		if (isLaunching()) return;
		this.dataTracker.set(CLIMB, 1);
		if (getWorld() instanceof ServerWorld world) {
			world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 3.0f, 0.5f);
		}
	}

	/** How far through the climb, 0 to 1. The script uses this to time the beats against the ascent. */
	public float climbProgress() {
		return Math.min(1f, this.dataTracker.get(CLIMB) / (float) CLIMB_TICKS);
	}

	@Override
	public void tick() {
		super.tick();
		int climb = this.dataTracker.get(CLIMB);
		if (climb <= 0) return;
		this.dataTracker.set(CLIMB, climb + 1);
		// Slow off the pad and still accelerating when it leaves view, the way a real one goes.
		double speed = 0.06 + 0.85 * Math.pow(climb / (double) CLIMB_TICKS, 1.6);
		setPosition(getX(), getY() + speed, getZ());
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.FLAME, getX(), getY() - 1.0, getZ(), 12, 0.3, 0.2, 0.3, 0.02);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() - 1.4, getZ(), 8, 0.5, 0.2, 0.5, 0.01);
		}
		if (climb >= CLIMB_TICKS) discard();
	}

	@Override
	public boolean isCollidable() {
		return !isLaunching();
	}

	@Override
	public boolean canHit() {
		return !isLaunching();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(net.minecraft.entity.damage.DamageSource source) {
		return true;
	}

	/** Tall and far from anywhere; it has to stay drawn while it climbs out of the valley. */
	@Override
	public boolean shouldRender(double distance) {
		return distance < 512.0 * 512.0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(LOADED, nbt.getBoolean("Loaded"));
		this.dataTracker.set(CLIMB, nbt.getInt("Climb"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("Loaded", isLoaded());
		nbt.putInt("Climb", this.dataTracker.get(CLIMB));
	}
}
