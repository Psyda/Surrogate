package dev.psyda.surrogate.fauna;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The rock that is not a rock. It sits in the geyser fields with its back plates folded down, looks exactly
 * like the basalt it is sitting on, and does nothing at all for as long as nothing happens to it.
 *
 * <p>Two things rouse it: being hit, and being stood on. The second is the one that catches people, because
 * a geyser field is all boulders and the fastest way across is over them. Roused, it unfolds, and it is
 * genuinely dangerous for about fifteen seconds — slow, heavy, and hits hard enough that a chassis notices.
 * Then it loses interest, folds back down wherever it has ended up, and is a rock again.
 *
 * <p>It is not a mimic and it is not lying in wait. It is asleep, and it would rather have stayed that way.
 */
public class SlagbackEntity extends FaunaEntity {
	/** Unfolded and cross. The renderer draws plates and a mouth; folded, it draws a boulder. */
	private static final TrackedData<Boolean> ROUSED = DataTracker.registerData(SlagbackEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** How long it stays up after the last thing that annoyed it. */
	private static final int TEMPER = 300;
	/** How much of a hit gets through the plates while it is folded. It is armour, not invulnerability. */
	private static final float FOLDED_ARMOUR = 0.25f;

	private int angryFor;
	/** Ticks left of the unfold, so the renderer has something to interpolate rather than snapping open. */
	private int opening;

	public SlagbackEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				// Slow. A player who wants to leave can always leave; this is a punishment for standing on
				// something, not a chase.
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.19)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
				.add(EntityAttributes.GENERIC_ARMOR, 8.0)
				// It is a boulder. Nothing shoves it.
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 12.0);
	}

	@Override
	public Specimen specimen() {
		return Specimen.SLAGBACK;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROUSED, false);
	}

	@Override
	protected void initGoals() {
		// Only two, and both of them are gated on being roused by mobTick. A folded slagback has no mind.
		this.goalSelector.add(1, new net.minecraft.entity.ai.goal.MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.add(2, new net.minecraft.entity.ai.goal.LookAroundGoal(this));
		this.targetSelector.add(1, new net.minecraft.entity.ai.goal.RevengeGoal(this));
	}

	public boolean isRoused() {
		return this.dataTracker.get(ROUSED);
	}

	/** 0 folded, 1 fully open. The renderer's only input for the unfold. */
	public float openness(float tickDelta) {
		if (!isRoused()) return 0f;
		if (opening <= 0) return 1f;
		return 1f - (opening - tickDelta) / (float) OPEN_TICKS;
	}

	private static final int OPEN_TICKS = 12;

	@Override
	protected void mobTick() {
		if (!isRoused()) {
			// Folded: no navigation, no target, no goals worth running. The one thing it still does is
			// notice what is standing on it, which is the whole trap.
			getNavigation().stop();
			setTarget(null);
			if (steppedOn() != null) rouse(steppedOn());
			return;
		}
		if (opening > 0) opening--;
		if (--angryFor <= 0) fold();
		super.mobTick();
	}

	/**
	 * Whatever is resting on top of it. A box one block tall sitting directly on its own, so a player walking
	 * past at ground level does not count and a player who has climbed onto it does.
	 */
	@Nullable
	private LivingEntity steppedOn() {
		Box top = getBoundingBox().withMinY(getBoundingBox().maxY - 0.05).stretch(0.0, 1.0, 0.0);
		// Other animals do not count: a trundle rolling over a field of these used to keep the whole field
		// opening and closing, and every open and close is a noise.
		for (Entity e : getWorld().getOtherEntities(this, top, e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof FaunaEntity))) {
			return (LivingEntity) e;
		}
		return null;
	}

	private void rouse(@Nullable LivingEntity at) {
		if (!isRoused()) {
			this.dataTracker.set(ROUSED, true);
			opening = OPEN_TICKS;
			voice(SoundEvents.BLOCK_DEEPSLATE_BREAK, 0.3f);
			// A shrug, not an attack: whatever was standing on it gets tipped off before the plates open.
			if (at != null) at.addVelocity(0.0, 0.42, 0.0);
		}
		angryFor = TEMPER;
		if (at instanceof PlayerEntity player && !player.isCreative()) setTarget(at);
		else if (at != null && !(at instanceof PlayerEntity)) setTarget(at);
	}

	private void fold() {
		this.dataTracker.set(ROUSED, false);
		opening = 0;
		setTarget(null);
		getNavigation().stop();
		voice(SoundEvents.BLOCK_DEEPSLATE_PLACE, 0.3f);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Folded, it is mostly rock, and the hit that wakes it is the one that barely hurts. Waking has to
		// happen either way, or a player could chip one to death while it slept.
		float scaled = isRoused() ? amount : amount * FOLDED_ARMOUR;
		boolean hurt = super.damage(source, scaled);
		if (hurt && !getWorld().isClient) {
			rouse(source.getAttacker() instanceof LivingEntity living ? living : null);
		}
		return hurt;
	}

	/** A thing this heavy does not fall far enough to hurt itself, and does not tread on anything either. */
	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	/** Awake and swinging, it will not be handled. Fold it back down — or wait — and it will. */
	@Override
	public boolean canBeBagged() {
		return isAlive() && !isRoused();
	}

	@Nullable
	@Override
	public String bagRefusal() {
		return isRoused() ? "message.surrogate.bag.roused" : null;
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return isRoused() ? SoundEvents.BLOCK_BASALT_HIT : null;
	}

	/** Roused, it grinds now and then, not constantly. Folded, it says nothing at all. */
	@Override
	public int getMinAmbientSoundDelay() {
		return 240;
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_BASALT_BREAK;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_DEEPSLATE_BREAK;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Roused", isRoused());
		nbt.putInt("AngryFor", angryFor);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(ROUSED, nbt.getBoolean("Roused"));
		angryFor = nbt.getInt("AngryFor");
	}
}
