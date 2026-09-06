package dev.psyda.surrogate.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * The company ship, seen once. It comes in low and fast over the pad to take the payload, and it does not
 * stop, does not descend, and does not answer. Nobody aboard it ever speaks to the people who built the pad.
 *
 * <p>Physically it is a prop on rails: a heading, a speed and a countdown. It flies straight through on the
 * course it was given and discards itself at the far end, so the whole flyover is one spawn and no upkeep.
 * The hull is far bigger than its collision box, which is deliberate - nothing should ever collide with it.
 */
public class CompanyShipEntity extends Entity {
	/** The collision box, which is nothing like the size of the hull that is drawn. */
	public static final float WIDTH = 4.0f;
	public static final float HEIGHT = 4.0f;
	/** How far the hull reaches from the centre, for the renderer's culling and for the sound. */
	public static final float HULL_LENGTH = 180f;
	public static final float HULL_WIDTH = 60f;

	/** Blocks per tick along its heading. Fast: it is not stopping. */
	private static final TrackedData<Float> SPEED = DataTracker.registerData(CompanyShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	/** Ticks left before it is gone. */
	private static final TrackedData<Integer> LIFE = DataTracker.registerData(CompanyShipEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public CompanyShipEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SPEED, 2.4f);
		builder.add(LIFE, 400);
	}

	public void setSpeed(float speed) {
		this.dataTracker.set(SPEED, speed);
	}

	public float getSpeed() {
		return this.dataTracker.get(SPEED);
	}

	public void setLife(int ticks) {
		this.dataTracker.set(LIFE, ticks);
	}

	/**
	 * Flies straight through on its heading. It never turns, because it was never coming for the people below.
	 *
	 * <p>The motion runs on both sides. The path is a heading and a speed with nothing to diverge, so letting
	 * the client move it too keeps it gliding between the server's position updates; a position set only on
	 * the server arrives as occasional teleports, which for something this size reads as a hull frozen in the
	 * sky. Yaw follows Minecraft's convention: 0 faces +z, 90 faces -x, 180 faces -z, 270 faces +x, so a ship
	 * entering from the west and crossing east flies at yaw 270.
	 */
	@Override
	public void tick() {
		super.tick();
		float yaw = getYaw();
		double speed = getSpeed();
		double dx = -Math.sin(Math.toRadians(yaw)) * speed;
		double dz = Math.cos(Math.toRadians(yaw)) * speed;
		setPosition(getX() + dx, getY(), getZ() + dz);
		// The renderer reads velocity for nothing, but the client's own movement bookkeeping does.
		setVelocity(dx, 0.0, dz);
		if (getWorld().isClient) return;
		int life = this.dataTracker.get(LIFE) - 1;
		this.dataTracker.set(LIFE, life);
		if (life <= 0) discard();
	}

	/** The server owns the path; a position packet must not fight the client's own copy of the same motion. */
	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
		setPosition(x, y, z);
		setYaw(yaw);
		setPitch(pitch);
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canHit() {
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

	/** It is enormous and it is meant to be seen coming from a long way off. */
	@Override
	public boolean shouldRender(double distance) {
		return distance < 2048.0 * 2048.0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(SPEED, nbt.getFloat("Speed"));
		this.dataTracker.set(LIFE, nbt.getInt("Life"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putFloat("Speed", getSpeed());
		nbt.putInt("Life", this.dataTracker.get(LIFE));
	}
}
