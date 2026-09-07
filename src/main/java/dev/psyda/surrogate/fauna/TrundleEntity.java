package dev.psyda.surrogate.fauna;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A knee-high ball of grey fibre that spends most of the day asleep and the rest of it going the other way.
 *
 * <p>It has no attack and no intention of learning one. Left alone it dozes, which is a real state and not
 * an animation: a dozing trundle does not path, does not turn, and does not notice a player until one is
 * close enough to step on it. Woken, it rolls — the renderer spins it about its own axis by the distance it
 * has covered, which is the only reason the shape is a ball — and keeps rolling until whatever woke it is
 * eight blocks behind. Then it stops where it stopped, waits a while, and goes back to sleep.
 *
 * <p>Hitting one is possible and pointless. It has no revenge goal; it just runs, and it is slower than the
 * thing chasing it, so mostly it gets caught. That is the intended feeling.
 */
public class TrundleEntity extends FaunaEntity {
	/** Asleep: tucked in, not moving, not looking. The renderer draws it as a closed ball. */
	private static final TrackedData<Boolean> DOZING = DataTracker.registerData(TrundleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** How close something has to get before it stirs. */
	private static final double STIR = 6.0;
	/** How far it wants that something to be before it settles again. */
	private static final double SETTLE = 12.0;
	/** Ticks of nothing bothering it before it tucks in. Twenty seconds is a long time to stand still for. */
	private static final int DOZE_AFTER = 400;

	private int calmFor;
	/** Ground covered since it woke, in blocks. The renderer reads this to know how far to spin the ball. */
	private double rolled;

	public TrundleEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
				// Slower than a walking player, and it knows it. Fleeing is a gesture, not an escape.
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.16)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
	}

	@Override
	public Specimen specimen() {
		return Specimen.TRUNDLE;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(DOZING, true);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new net.minecraft.entity.ai.goal.SwimGoal(this));
		// Priority order is the animal: get away first, then look at what it is getting away from, then
		// wander. Every one of these is gated on being awake by the checks in tick().
		this.goalSelector.add(2, new net.minecraft.entity.ai.goal.FleeEntityGoal<>(this, PlayerEntity.class, (float) STIR, 1.0, 1.25));
		this.goalSelector.add(3, new net.minecraft.entity.ai.goal.WanderAroundFarGoal(this, 0.7));
		this.goalSelector.add(4, new net.minecraft.entity.ai.goal.LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(5, new net.minecraft.entity.ai.goal.LookAroundGoal(this));
	}

	public boolean isDozing() {
		return this.dataTracker.get(DOZING);
	}

	/** How far it has rolled since waking, for the renderer's spin. Never reset while awake. */
	public double rolled() {
		return rolled;
	}

	@Override
	public void tick() {
		if (!getWorld().isClient) {
			PlayerEntity near = getWorld().getClosestPlayer(this, SETTLE);
			double distance = near == null ? Double.MAX_VALUE : near.squaredDistanceTo(this);
			if (isDozing()) {
				if (distance < STIR * STIR) wake();
			} else {
				// It settles on its own clock, not on the player's: walk away and it still takes a while to
				// decide the coast is clear, which is what keeps it from snapping shut the moment you turn.
				if (distance > SETTLE * SETTLE) {
					if (++calmFor > DOZE_AFTER) doze();
				} else {
					calmFor = 0;
				}
			}
		}
		super.tick();
		if (!isDozing()) {
			double dx = getX() - prevX;
			double dz = getZ() - prevZ;
			rolled += Math.sqrt(dx * dx + dz * dz);
		}
	}

	private void wake() {
		this.dataTracker.set(DOZING, false);
		calmFor = 0;
		voice(SoundEvents.ENTITY_SNIFFER_IDLE, 0.4f);
	}

	private void doze() {
		this.dataTracker.set(DOZING, true);
		calmFor = 0;
		getNavigation().stop();
	}

	/**
	 * Asleep it does not path and does not turn, so the goals are held off rather than removed. Doing it here
	 * rather than in a goal predicate keeps the whole of "asleep" in one place.
	 */
	@Override
	protected void mobTick() {
		if (isDozing()) {
			getNavigation().stop();
			setVelocity(getVelocity().multiply(0.0, 1.0, 0.0));
			return;
		}
		super.mobTick();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && !getWorld().isClient) wake();
		return hurt;
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return isDozing() ? null : SoundEvents.ENTITY_SNIFFER_IDLE;
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_SNIFFER_HURT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SNIFFER_DEATH;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Dozing", isDozing());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(DOZING, !nbt.contains("Dozing") || nbt.getBoolean("Dozing"));
	}
}
