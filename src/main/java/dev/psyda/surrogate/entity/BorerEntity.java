package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.SurrogateConfig;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.atmosphere.ModDamageTypes;
import dev.psyda.surrogate.hazard.Borers;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModSounds;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What is under the rock. A blind segmented animal that swims through stone towards whatever is making a
 * noise, damages everything it passes without opening a tunnel behind it, and bites once when it finds open
 * air with somebody in it.
 *
 * <p>It has no pathfinder and no goals: {@link dev.psyda.surrogate.hazard.Borers} decides when one wakes and
 * what it is coming for, and the steering below is the whole of its mind. Movement is noClip, so the rock is
 * not there as far as the physics are concerned; the damage it does to that rock is bookkeeping this class
 * does by hand, drawn with the same crack overlay a player's own mining uses.
 */
public class BorerEntity extends Entity {
	/** The collision box. The drawn body is six blocks of it trailing behind, which nothing collides with. */
	public static final float WIDTH = 1.2f;
	public static final float HEIGHT = 1.2f;

	/** How far the head moves before another body position is remembered, in blocks. */
	public static final double TRAIL_STEP = 0.35;
	/** How many of those the renderer has to draw a body from. */
	public static final int TRAIL = 24;

	/** Ticks of chewing one point of block hardness costs. Stone is 1.5, so a block of it is thirty. */
	private static final double TICKS_PER_HARDNESS = 20.0;
	/** What hull plating multiplies that by. A plated shaft is a wall, not a delay. */
	private static final double PLATED = 8.0;
	/** Blocks it works on at once: the cell its head is in and the six touching it. */
	private static final Direction[] CHEW = Direction.values();
	/** Chewed positions further than this from the head are forgotten, cracks and all. */
	private static final double CHEW_RANGE = 10.0;
	/** Breaker ids for the crack overlay live down here, far below any real entity id. */
	private static final int BREAKER_BASE = Integer.MIN_VALUE + 1_000_000;

	/** How close it has to be before it stops hunting and starts working the spot. */
	private static final double CIRCLE_RANGE = 7.0;
	private static final double CIRCLE_RADIUS = 5.0;
	/** How sharply it can turn: a fraction of the way to the heading it wants, per tick. */
	private static final double TURN = 0.05;
	/** How close it has to get to bite, and how long it stays out in the open doing it. */
	private static final double LUNGE_RANGE = 3.5;
	private static final int WITHDRAW_TICKS = 30;
	private static final int LUNGE_COOLDOWN = 200;
	/** Ticks it will work one target before it loses interest and goes back down. */
	private static final int PATIENCE = 4800;
	/** Ticks it spends leaving before it is gone. */
	private static final int LEAVE_TICKS = 400;
	private static final int GRIND_INTERVAL = 70;

	/** What it is doing. Ordinals are saved, so new ones go on the end. */
	public enum Mode {
		HUNTING, CIRCLING, LUNGING, LEAVING
	}

	private static final Mode[] MODES = Mode.values();

	/** Which of {@link Mode} it is in; the renderer reads it for how hard the body is working. */
	private static final TrackedData<Integer> MODE = DataTracker.registerData(BorerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	/** Its head is in open air rather than in the rock, so it can be seen and it can bite. */
	private static final TrackedData<Boolean> SURFACED = DataTracker.registerData(BorerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	/** Where it is steering: the noise, a point on the circle round it, or the way out. */
	private Vec3d aim = Vec3d.ZERO;
	/** The unit heading it is actually on, which only ever bends towards the one it wants. */
	private Vec3d heading = new Vec3d(0.0, 0.0, 1.0);
	@Nullable
	private UUID target;
	private int patience = PATIENCE;
	private int leaving;
	private int withdraw;
	private int lungeCooldown;
	private boolean tracked;

	/**
	 * Damage done to each block it has passed, against that block's own limit. Not saved: the crack overlay
	 * is a client-side effect that times itself out, so a reloaded borer starting the seam again is right.
	 */
	private final Long2DoubleOpenHashMap chew = new Long2DoubleOpenHashMap();
	/** The last few positions of the head, newest first, for the renderer to hang the body off. */
	private final Vec3d[] trail = new Vec3d[TRAIL];
	private int trailHead;

	public BorerEntity(EntityType<?> type, World world) {
		super(type, world);
		// Rock is not there as far as the physics are concerned; everything it costs is done by hand below.
		this.noClip = true;
		setNoGravity(true);
		this.chew.defaultReturnValue(0.0);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(MODE, Mode.HUNTING.ordinal());
		builder.add(SURFACED, false);
	}

	// ------------------------------------------------------------------ state

	public Mode getMode() {
		return MODES[MathHelper.clamp(this.dataTracker.get(MODE), 0, MODES.length - 1)];
	}

	private void setMode(Mode mode) {
		if (getMode() != mode) this.dataTracker.set(MODE, mode.ordinal());
	}

	public boolean isSurfaced() {
		return this.dataTracker.get(SURFACED);
	}

	/** Sends it after something, from wherever it woke. */
	public void send(Entity focus, Vec3d noise) {
		this.target = focus.getUuid();
		this.aim = noise;
		this.heading = noise.subtract(getPos()).normalize();
		if (this.heading.lengthSquared() < 1.0E-6) this.heading = new Vec3d(0.0, 0.0, 1.0);
		setMode(Mode.HUNTING);
	}

	@Nullable
	public UUID getTarget() {
		return this.target;
	}

	/** The position it was at, that many remembered steps back. Clamped to what it has actually recorded. */
	public Vec3d trailAt(int back) {
		int index = Math.floorMod(this.trailHead - MathHelper.clamp(back, 0, TRAIL - 1), TRAIL);
		Vec3d at = this.trail[index];
		return at == null ? getPos() : at;
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		recordTrail();
		if (getWorld().isClient) return;
		ServerWorld world = (ServerWorld) getWorld();
		if (!Borers.enabled(world)) {
			discard();
			return;
		}
		if (!this.tracked) {
			Borers.track(this);
			this.tracked = true;
		}
		Entity focus = resolveTarget(world);
		think(world, focus);
		swim();
		chew(world);
		if (this.age % 20 == 0) prune(world);
		if (focus != null && this.withdraw <= 0 && this.lungeCooldown <= 0 && isSurfaced()) lunge(world, focus);
		if (this.lungeCooldown > 0) this.lungeCooldown--;
		if (this.age % GRIND_INTERVAL == 0) {
			world.playSound(null, getX(), getY(), getZ(), ModSounds.BORER_GRIND, SoundCategory.HOSTILE,
					3.0f, 0.75f + this.random.nextFloat() * 0.2f);
		}
		if (this.age % 4 == 0 && getMode() != Mode.LEAVING) {
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 2, 0.4, 0.3, 0.4, 0.0);
		}
	}

	private void recordTrail() {
		Vec3d pos = getPos();
		if (this.trail[this.trailHead] == null) {
			for (int i = 0; i < TRAIL; i++) this.trail[i] = pos;
			return;
		}
		if (this.trail[this.trailHead].squaredDistanceTo(pos) < TRAIL_STEP * TRAIL_STEP) return;
		this.trailHead = Math.floorMod(this.trailHead + 1, TRAIL);
		this.trail[this.trailHead] = pos;
	}

	@Nullable
	private Entity resolveTarget(ServerWorld world) {
		if (this.target == null) return null;
		Entity found = world.getEntity(this.target);
		if (found == null || found.isRemoved()) return null;
		if (found instanceof LivingEntity living && !living.isAlive()) return null;
		return found;
	}

	/**
	 * The whole of its mind: close on the noise, work the spot when it gets there, and go back down when the
	 * noise stops, when a damper takes the fix off it, or when it is dragged above the line.
	 */
	private void think(ServerWorld world, @Nullable Entity focus) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		if (this.withdraw > 0) {
			this.withdraw--;
			if (this.withdraw == 0 && getMode() == Mode.LUNGING) setMode(Mode.CIRCLING);
		}
		if (getMode() == Mode.LEAVING) {
			this.leaving++;
			if (this.leaving > LEAVE_TICKS || getBlockY() < world.getBottomY() + 8) discard();
			return;
		}
		boolean quiet = Borers.damped(world, getBlockPos())
				|| (focus instanceof RobotEntity robot && robot.hasModule(RobotModule.DAMPER));
		if (focus == null || quiet || --this.patience <= 0 || getBlockY() >= cfg.borerDepthY) {
			leave();
			return;
		}
		// While it is pulling back from a bite it keeps the aim the bite gave it.
		if (this.withdraw > 0) return;
		Vec3d at = focus.getPos();
		double distance = at.distanceTo(getPos());
		if (distance <= CIRCLE_RANGE) {
			setMode(Mode.CIRCLING);
			// A slow orbit of whatever is making the noise, which is what eventually opens the ceiling.
			double angle = this.age * 0.02;
			this.aim = at.add(Math.cos(angle) * CIRCLE_RADIUS, -1.5, Math.sin(angle) * CIRCLE_RADIUS);
		} else {
			setMode(Mode.HUNTING);
			this.aim = at;
		}
		// It never comes up for air: the line is the one promise this hazard makes.
		double ceiling = cfg.borerDepthY - 2.0;
		if (this.aim.y > ceiling) this.aim = new Vec3d(this.aim.x, ceiling, this.aim.z);
	}

	/** Gives up and goes down. */
	private void leave() {
		if (getMode() == Mode.LEAVING) return;
		setMode(Mode.LEAVING);
		this.leaving = 0;
		this.target = null;
		this.aim = getPos().add(this.heading.x * 40.0, -30.0, this.heading.z * 40.0);
	}

	private void swim() {
		Vec3d want = this.aim.subtract(getPos());
		if (want.lengthSquared() < 1.0E-6) want = this.heading;
		Vec3d desired = want.normalize();
		this.heading = this.heading.multiply(1.0 - TURN).add(desired.multiply(TURN));
		if (this.heading.lengthSquared() < 1.0E-6) this.heading = desired;
		this.heading = this.heading.normalize();
		double speed = Surrogate.CONFIG.borerSpeed * (getMode() == Mode.LUNGING ? 3.0 : 1.0);
		Vec3d step = this.heading.multiply(speed);
		setVelocity(step);
		move(MovementType.SELF, step);
		setYaw((float) (MathHelper.atan2(step.z, step.x) * MathHelper.DEGREES_PER_RADIAN) - 90f);
		setPitch((float) (-MathHelper.atan2(step.y, Math.sqrt(step.x * step.x + step.z * step.z)) * MathHelper.DEGREES_PER_RADIAN));
	}

	// ------------------------------------------------------------------ the rock

	/**
	 * Damage, not a tunnel. Everything the head touches takes a little each tick and shows it through the
	 * vanilla crack overlay; a block only goes when it has taken enough, so one pass leaves a seam and a
	 * borer that keeps coming back opens a room.
	 */
	private void chew(ServerWorld world) {
		SurrogateConfig cfg = Surrogate.CONFIG;
		BlockPos head = getBlockPos();
		boolean blocked = false;
		this.dataTracker.set(SURFACED, world.getBlockState(head).isAir());
		for (int i = 0; i <= CHEW.length; i++) {
			BlockPos pos = i == CHEW.length ? head : head.offset(CHEW[i]);
			if (pos.getY() >= cfg.borerDepthY) continue;
			BlockState state = world.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			double limit = resistance(world, state, pos);
			if (limit < 0.0) {
				blocked = true;
				continue;
			}
			long key = pos.asLong();
			double before = this.chew.get(key);
			double after = before + cfg.borerChewPerTick;
			if (after >= limit) {
				if (partOfARoom(world, pos)) {
					// A sealed room cracks and holds. Its walls are the one thing it will not open.
					this.chew.put(key, limit * 0.95);
					if (stage(before, limit) != 9) world.setBlockBreakingInfo(breakerId(pos), pos, 9);
					continue;
				}
				take(world, pos, state);
				continue;
			}
			this.chew.put(key, after);
			int stage = stage(after, limit);
			if (stage != stage(before, limit)) world.setBlockBreakingInfo(breakerId(pos), pos, stage);
		}
		if (blocked) turnAway();
	}

	/** Removes the block with nothing to show for it, particles and all. Drops are suppressed outright. */
	private void take(ServerWorld world, BlockPos pos, BlockState state) {
		this.chew.remove(pos.asLong());
		world.setBlockBreakingInfo(breakerId(pos), pos, -1);
		world.syncWorldEvent(2001, pos, Block.getRawIdFromState(state));
		// SKIP_DROPS is what stops a container in the way scattering; FORCE_STATE stops neighbours undoing it.
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
	}

	/** How much chewing this block is worth, or a negative number for one that stops it dead. */
	private static double resistance(ServerWorld world, BlockState state, BlockPos pos) {
		if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN)) return -1.0;
		float hardness = state.getHardness(world, pos);
		if (hardness < 0f) return -1.0;
		double limit = hardness * TICKS_PER_HARDNESS;
		if (state.isOf(ModBlocks.HULL_PLATING)) limit *= PLATED;
		return limit;
	}

	/**
	 * A wall of somebody's sealed room. Cheap enough to ask at the moment a block would break: the
	 * atmosphere index is a handful of live life support units, each answering with one hash lookup.
	 */
	private static boolean partOfARoom(ServerWorld world, BlockPos pos) {
		if (Atmosphere.volumeAt(world, pos) != null) return true;
		for (Direction direction : Direction.values()) {
			if (Atmosphere.volumeAt(world, pos.offset(direction)) != null) return true;
		}
		return false;
	}

	private static int stage(double progress, double limit) {
		return MathHelper.clamp((int) (progress / limit * 10.0), 0, 9);
	}

	/**
	 * A breaker id per position rather than per borer: vanilla keeps one cracked block per breaker, so a
	 * seam of them needs a spread of ids. These sit a billion below any id the world will ever hand out.
	 */
	private static int breakerId(BlockPos pos) {
		long h = pos.asLong() * 0x9E3779B97F4A7C15L;
		h ^= h >>> 29;
		return BREAKER_BASE + (int) ((h >>> 32) & 0xFFFF);
	}

	/**
	 * Forgets blocks it has left behind. Their cracks are not cleared: the client times an untouched overlay
	 * out on its own after twenty seconds, which is exactly how long a seam should stand.
	 */
	private void prune(ServerWorld world) {
		if (this.chew.isEmpty()) return;
		double range = CHEW_RANGE * CHEW_RANGE;
		BlockPos head = getBlockPos();
		LongIterator keys = this.chew.keySet().iterator();
		while (keys.hasNext()) {
			BlockPos pos = BlockPos.fromLong(keys.nextLong());
			if (head.getSquaredDistance(pos) > range) keys.remove();
		}
	}

	/** Something it cannot eat. Bend away from it rather than grinding on the spot. */
	private void turnAway() {
		this.aim = getPos().add(this.heading.z * 12.0, -6.0, -this.heading.x * 12.0);
	}

	// ------------------------------------------------------------------ contact

	/** One bite, then back into the wall. It does not stay out where it can be seen. */
	private void lunge(ServerWorld world, Entity focus) {
		if (squaredDistanceTo(focus) > LUNGE_RANGE * LUNGE_RANGE) return;
		DamageSource source = ModDamageTypes.borer(world);
		focus.damage(source, Surrogate.CONFIG.borerLungeDamage);
		world.playSound(null, getX(), getY(), getZ(), ModSounds.BORER_LUNGE, SoundCategory.HOSTILE, 2.0f, 1.0f);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 24, 0.8, 0.6, 0.8, 0.02);
		setMode(Mode.LUNGING);
		this.withdraw = WITHDRAW_TICKS;
		this.lungeCooldown = LUNGE_COOLDOWN;
		// Straight back into the rock, away from whatever it just hit.
		Vec3d away = getPos().subtract(focus.getPos()).normalize();
		this.aim = getPos().add(away.multiply(14.0)).add(0.0, -6.0, 0.0);
	}

	// ------------------------------------------------------------------ housekeeping

	@Override
	public void remove(RemovalReason reason) {
		Borers.forget(this);
		super.remove(reason);
	}

	@Override
	public void onRemoved() {
		Borers.forget(this);
		super.onRemoved();
	}

	@Override
	public boolean isCollidable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** Nothing kills one. The counter is the damper and the way back up, not a fight. */
	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	/** Six blocks of body trailing a small box, and it is heard long before it is seen. */
	@Override
	public boolean shouldRender(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(MODE, MathHelper.clamp(nbt.getInt("Mode"), 0, MODES.length - 1));
		this.aim = new Vec3d(nbt.getDouble("AimX"), nbt.getDouble("AimY"), nbt.getDouble("AimZ"));
		this.heading = new Vec3d(nbt.getDouble("HeadingX"), nbt.getDouble("HeadingY"), nbt.getDouble("HeadingZ"));
		if (this.heading.lengthSquared() < 1.0E-6) this.heading = new Vec3d(0.0, 0.0, 1.0);
		this.target = nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
		this.patience = nbt.contains("Patience") ? nbt.getInt("Patience") : PATIENCE;
		this.leaving = nbt.getInt("Leaving");
		this.lungeCooldown = nbt.getInt("LungeCooldown");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Mode", getMode().ordinal());
		nbt.putDouble("AimX", this.aim.x);
		nbt.putDouble("AimY", this.aim.y);
		nbt.putDouble("AimZ", this.aim.z);
		nbt.putDouble("HeadingX", this.heading.x);
		nbt.putDouble("HeadingY", this.heading.y);
		nbt.putDouble("HeadingZ", this.heading.z);
		if (this.target != null) nbt.putUuid("Target", this.target);
		nbt.putInt("Patience", this.patience);
		nbt.putInt("Leaving", this.leaving);
		nbt.putInt("LungeCooldown", this.lungeCooldown);
	}
}
