package dev.psyda.surrogate.fauna;

import dev.psyda.surrogate.registry.ModBlocks;
import net.minecraft.block.BlockState;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Knee-high, three-legged, and mostly eye. It answers you.
 *
 * <p>A tocker's whole behaviour is one loop: find the brightest warm thing nearby and go and sit next to it.
 * A lamp counts. A running life support unit counts. A chassis with its headlamp on counts, which is why
 * they follow you, and why a player who has been out for a while tends to come home with two or three.
 *
 * <p>The name is what it does. Beep at one — a radio, a chassis, a jukebox, anything the world plays as a
 * note — and it clicks the same pitch back a beat later, badly. Ferreira's note on the analysis disk is
 * about eight words long and one of them is "why".
 */
public class TockerEntity extends FaunaEntity {
	/** Curled up on something warm. Not the same as asleep: it still watches, it just will not move. */
	private static final TrackedData<Boolean> PERCHED = DataTracker.registerData(TockerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** How far it will follow a light. Beyond this it gives up and finds another one. */
	private static final double FOLLOW = 12.0;
	/** How close it settles. Any nearer and it gets stepped on, which it has learned. */
	private static final double PERSONAL = 2.5;
	/** Light level a block has to be for a tocker to consider it worth sitting by. */
	private static final int WARM_ENOUGH = 8;

	/** Ticks until it answers whatever it just heard, and at what pitch. */
	private int echoIn = -1;
	private float echoPitch = 1.0f;

	public TockerEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 6.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
	}

	@Override
	public Specimen specimen() {
		return Specimen.TOCKER;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(PERCHED, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new net.minecraft.entity.ai.goal.SwimGoal(this));
		// It keeps its distance and it keeps up: the two numbers are what make following read as company
		// rather than as pursuit.
		this.goalSelector.add(2, new SeekLightGoal(this, 1.0, PERSONAL, FOLLOW));
		this.goalSelector.add(3, new net.minecraft.entity.ai.goal.WanderAroundFarGoal(this, 0.9));
		this.goalSelector.add(4, new net.minecraft.entity.ai.goal.LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
		this.goalSelector.add(5, new net.minecraft.entity.ai.goal.LookAroundGoal(this));
	}

	public boolean isPerched() {
		return this.dataTracker.get(PERCHED);
	}

	@Override
	protected void mobTick() {
		if (isPerched()) {
			getNavigation().stop();
			// It only leaves a warm spot when the spot stops being warm, or when somebody stands over it.
			if (!warmHere() || getWorld().getClosestPlayer(this, 2.0) != null) {
				this.dataTracker.set(PERCHED, false);
			}
			return;
		}
		super.mobTick();
		if (this.age % 40 == 0 && warmHere() && getNavigation().isIdle()) {
			this.dataTracker.set(PERCHED, true);
			voice(SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, 0.5f);
		}
		if (echoIn > 0 && --echoIn == 0) answer();
	}

	/** Standing on or beside something the mod calls machinery, and lit. Both, or it is just a floor. */
	private boolean warmHere() {
		if (getWorld().getLightLevel(getBlockPos()) < WARM_ENOUGH) return false;
		BlockPos under = getBlockPos().down();
		BlockState state = getWorld().getBlockState(under);
		return state.isOf(ModBlocks.GEOTHERMAL_TAP)
				|| state.isOf(ModBlocks.LIFE_SUPPORT)
				|| state.isOf(ModBlocks.CHARGING_DOCK)
				|| state.isOf(ModBlocks.SOLAR_COLLECTOR)
				|| state.isOf(ModBlocks.MICROWAVE)
				|| state.isOf(ModBlocks.DECK_PLATING);
	}

	/**
	 * Something made a noise near it. Remember the pitch, badly, and plan to say it back in half a second —
	 * long enough that it reads as an answer rather than an echo.
	 */
	public void heard(float pitch) {
		if (echoIn > 0) return;
		echoIn = 10 + this.random.nextInt(8);
		// It is not a good mimic. The wobble is the joke.
		echoPitch = net.minecraft.util.math.MathHelper.clamp(pitch + (this.random.nextFloat() - 0.5f) * 0.3f, 0.5f, 2.0f);
	}

	private void answer() {
		getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(),
				SoundCategory.NEUTRAL, 0.5f, echoPitch);
		if (getWorld() instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.3, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		ItemStack stack = player.getStackInHand(hand);
		// Anything the sampler or the bag wants to do with it takes priority; a bare hand is a hello.
		if (!stack.isEmpty()) return ActionResult.PASS;
		if (!getWorld().isClient) {
			heard(1.0f + (this.random.nextFloat() - 0.5f) * 0.4f);
			if (getWorld() instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY(), getZ(), 3, 0.2, 0.2, 0.2, 0.0);
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt) this.dataTracker.set(PERCHED, false);
		return hurt;
	}

	/** Small enough that a step down is nothing. */
	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return fallDistance > 5.0f && super.handleFallDamage(fallDistance, damageMultiplier, damageSource);
	}

	@Nullable
	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ALLAY_HURT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ALLAY_DEATH;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Perched", isPerched());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(PERCHED, nbt.getBoolean("Perched"));
	}
}
