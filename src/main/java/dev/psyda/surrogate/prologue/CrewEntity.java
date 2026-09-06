package dev.psyda.surrogate.prologue;

import dev.psyda.surrogate.block.AirlockDoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A scripted person: the Habitat Seven crew, the ship's crew, or a pilot asleep in a transit chair. Has no
 * life of their own: the scripts walk them around, sit them in chairs and, at the end, lay them down.
 * Afterwards they stay where they were left.
 */
public class CrewEntity extends PathAwareEntity {
	private static final TrackedData<Integer> CHARACTER = DataTracker.registerData(CrewEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> SEATED = DataTracker.registerData(CrewEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> COLLAPSED = DataTracker.registerData(CrewEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Nullable
	private Entity lookTarget;
	@Nullable
	private BlockPos doorToOpen;
	private int doorCooldown;

	public CrewEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createCrewAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				// Marsh walks in from the dunes and her chassis from further; vanilla paths stop at 16 blocks.
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(CHARACTER, 0);
		builder.add(SEATED, false);
		builder.add(COLLAPSED, false);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f, 0.6f));
		this.goalSelector.add(3, new LookAroundGoal(this));
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		MobNavigation navigation = new MobNavigation(this, world);
		navigation.setCanPathThroughDoors(true);
		navigation.setCanEnterOpenDoors(true);
		return navigation;
	}

	// ------------------------------------------------------------------ identity and pose

	public Crew getCharacter() {
		return Crew.byId(this.dataTracker.get(CHARACTER));
	}

	public void setCharacter(Crew crew) {
		setCharacter(crew, crew.displayName());
	}

	/** A character with a name of their own, for the ones that share a skin. */
	public void setCharacter(Crew crew, Text name) {
		this.dataTracker.set(CHARACTER, crew.ordinal());
		setCustomName(name);
		setCustomNameVisible(true);
	}

	public boolean isSeated() {
		return this.dataTracker.get(SEATED);
	}

	public boolean isCollapsed() {
		return this.dataTracker.get(COLLAPSED);
	}

	/** Seated or collapsed: parked in place with no physics, AI or pathing. */
	public boolean isFixed() {
		return isSeated() || isCollapsed();
	}

	public void sitAt(Vec3d pos, float yaw) {
		stopWalking();
		refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
		face(yaw);
		this.dataTracker.set(COLLAPSED, false);
		this.dataTracker.set(SEATED, true);
		applyPose();
	}

	public void collapseAt(Vec3d pos, float yaw) {
		stopWalking();
		refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
		face(yaw);
		this.dataTracker.set(SEATED, false);
		this.dataTracker.set(COLLAPSED, true);
		applyPose();
	}

	public void stand() {
		this.dataTracker.set(SEATED, false);
		this.dataTracker.set(COLLAPSED, false);
		applyPose();
	}

	private void applyPose() {
		boolean fixed = isFixed();
		setAiDisabled(fixed);
		setNoGravity(fixed);
		this.noClip = fixed;
		setPose(isCollapsed() ? EntityPose.SLEEPING : EntityPose.STANDING);
		if (fixed) getNavigation().stop();
	}

	public void face(float yaw) {
		setYaw(yaw);
		setBodyYaw(yaw);
		setHeadYaw(yaw);
		this.prevYaw = yaw;
		this.prevBodyYaw = yaw;
		this.prevHeadYaw = yaw;
	}

	// ------------------------------------------------------------------ scripted movement

	/** Walks to {@code target}; if {@code door} is given it is opened on the way past. */
	public void walkTo(Vec3d target, @Nullable BlockPos door) {
		if (isFixed()) return;
		this.doorToOpen = door;
		getNavigation().startMovingTo(target.x, target.y, target.z, 0.8);
	}

	/** Starts the path again after a stall, keeping whatever door was on the way. */
	public void retryWalk(Vec3d target) {
		if (isFixed()) return;
		getNavigation().startMovingTo(target.x, target.y, target.z, 0.8);
	}

	public boolean hasArrived(Vec3d target) {
		double dx = getX() - target.x;
		double dz = getZ() - target.z;
		return dx * dx + dz * dz < 0.7;
	}

	public void stopWalking() {
		getNavigation().stop();
		this.doorToOpen = null;
	}

	public void setLookTarget(@Nullable Entity target) {
		this.lookTarget = target;
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (lookTarget != null) {
			if (lookTarget.isRemoved()) lookTarget = null;
			else getLookControl().lookAt(lookTarget, 30f, 30f);
		}
		if (doorCooldown > 0) doorCooldown--;
		if (doorToOpen != null && doorCooldown == 0 && !getNavigation().isIdle() && squaredDistanceTo(doorToOpen.toCenterPos()) < 7.0) {
			BlockState state = getWorld().getBlockState(doorToOpen);
			if (state.getBlock() instanceof AirlockDoorBlock door && !state.get(DoorBlock.OPEN)) {
				door.setOpenScripted(getWorld(), doorToOpen, true);
				doorCooldown = 30;
			}
		}
	}

	// ------------------------------------------------------------------ interaction

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.SUCCESS;
		if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
		Crew who = getCharacter();
		if (isCollapsed()) {
			Text epitaph = who == Crew.SLEEPER ? Text.translatable("crew.surrogate.sleeper.epitaph", getName()) : who.line("epitaph");
			serverPlayer.sendMessage(epitaph.copy().formatted(Formatting.GRAY, Formatting.ITALIC), false);
			return ActionResult.CONSUME;
		}
		if (who == Crew.SLEEPER) {
			serverPlayer.sendMessage(Text.translatable("crew.surrogate.sleeper.busy", getName()).formatted(Formatting.GRAY, Formatting.ITALIC), false);
			return ActionResult.CONSUME;
		}
		Director director = Director.current();
		if (director != null && director.onCrewUsed(who, serverPlayer, player.getStackInHand(hand))) return ActionResult.CONSUME;
		Text line = director != null ? director.hintFor(who) : null;
		if (line == null) line = who.line("busy");
		serverPlayer.sendMessage(Text.empty().append(getName().copy().formatted(Formatting.GOLD)).append(": ").append(line), false);
		return ActionResult.CONSUME;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	// ------------------------------------------------------------------ persistence

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Character", this.dataTracker.get(CHARACTER));
		nbt.putBoolean("Seated", isSeated());
		nbt.putBoolean("Collapsed", isCollapsed());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.dataTracker.set(CHARACTER, nbt.getInt("Character"));
		this.dataTracker.set(SEATED, nbt.getBoolean("Seated"));
		this.dataTracker.set(COLLAPSED, nbt.getBoolean("Collapsed"));
		applyPose();
	}
}
