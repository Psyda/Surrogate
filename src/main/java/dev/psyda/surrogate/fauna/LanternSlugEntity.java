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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * On the ceiling of every cave under the wastes, in ones and twos, a hand-sized soft thing with four small
 * lights on the front of it. It does not come down and it does not do anything. It watches.
 *
 * <p>The eyes track whoever is nearest, at a range far longer than it could possibly need, and they are the
 * only light in most of these caves. That is the entire animal.
 *
 * <p>It has one hit point and it is stuck to a ceiling. Anything at all that touches it — a stray swing, a
 * pickaxe backswing, a thrown block — detaches it, and then it falls, and then it is dead on the floor with
 * its lights going out. There is no version of this where hitting one works out. Ferreira's note about them
 * is the shortest one on the disk and it is not about biology.
 */
public class LanternSlugEntity extends FaunaEntity {
	/** Stuck to the ceiling. False once for about a second and a half, on the way down. */
	private static final TrackedData<Boolean> CLINGING = DataTracker.registerData(LanternSlugEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** How far the eyes will follow somebody. Absurd for its size, and the point of it. */
	private static final double WATCH = 24.0;

	/** Ticks of falling before it stops being a live animal. It never survives the landing. */
	private int falling = -1;

	public LanternSlugEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, WATCH);
	}

	@Override
	public Specimen specimen() {
		return Specimen.LANTERN_SLUG;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(CLINGING, true);
	}

	@Override
	protected void initGoals() {
		// It cannot move, so there is nothing here but the watching.
		this.goalSelector.add(1, new net.minecraft.entity.ai.goal.LookAtEntityGoal(this, PlayerEntity.class, (float) WATCH, 1.0f));
		this.goalSelector.add(2, new net.minecraft.entity.ai.goal.LookAroundGoal(this));
	}

	public boolean isClinging() {
		return this.dataTracker.get(CLINGING);
	}

	/** Clinging, it hangs off the block above rather than standing on the one below. */
	@Override
	public boolean hasNoGravity() {
		return isClinging();
	}

	@Override
	public void tick() {
		if (!getWorld().isClient && isClinging()) {
			// The ceiling it is on can be mined out from under — over — it. That counts as a fall.
			BlockPos above = getBlockPos().up();
			if (getWorld().isAir(above)) {
				drop();
			} else {
				setVelocity(0.0, 0.0, 0.0);
			}
		}
		super.tick();
		// Landing normally is the ground check; the counter is only there so one that comes down somewhere
		// isOnGround never becomes true — a slab edge, a fluid — still finishes rather than lying there lit.
		if (!getWorld().isClient && falling > 0) {
			if (isOnGround() || --falling == 0) land();
		}
	}

	/**
	 * Let go. Everything after this is one short fall and a small light going out; there is no state in
	 * which it climbs back up.
	 */
	private void drop() {
		if (!isClinging()) return;
		this.dataTracker.set(CLINGING, false);
		setNoGravity(false);
		falling = 60;
		voice(SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 0.3f);
	}

	private void land() {
		falling = -1;
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.GLOW, getX(), getY() + 0.1, getZ(), 6, 0.2, 0.1, 0.2, 0.01);
		}
		// It dies of the landing, not of the hit, and it dies to the world rather than to whoever swung —
		// the point is that nobody meant it.
		kill();
	}

	/**
	 * Any damage at all is fatal, eventually. It does not take the hit so much as let go of the ceiling,
	 * which comes to the same thing about a second later.
	 */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld().isClient) return false;
		if (isClinging()) {
			drop();
			return true;
		}
		return super.damage(source, amount);
	}

	/** It is already dead when it hits; the fall must not be what kills it, or the death sound doubles. */
	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	/** A bag will hold one. Getting it into the bag without knocking it off the ceiling is the exercise. */
	@Override
	public boolean canBeBagged() {
		return isAlive() && isClinging();
	}

	@Nullable
	@Override
	public String bagRefusal() {
		return isClinging() ? null : "message.surrogate.bag.falling";
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_SLIME_SQUISH_SMALL;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_SLIME_DEATH_SMALL;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Clinging", isClinging());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(CLINGING, !nbt.contains("Clinging") || nbt.getBoolean("Clinging"));
	}
}
