package dev.psyda.surrogate.entity;

import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.network.CinematicPayloads.Frame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

/**
 * The cinematic camera: an entity that is never added to a world, never saved and never rendered. The
 * client creates one, hands it to {@code MinecraftClient.setCameraEntity}, and steps it along a list of
 * keyframes once per tick; vanilla interpolates between ticks like it does for any other camera entity.
 */
public class CameraEntity extends Entity {
	private List<Frame> frames = List.of();
	private int index;
	private int ticksIntoFrame;

	public CameraEntity(EntityType<? extends CameraEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		setNoGravity(true);
		// Well clear of every id the server could hand out, so it can never shadow a real entity.
		setId(Integer.MIN_VALUE + 7331);
	}

	/** Starts a new path with a hard cut to its first frame. */
	public void setPath(List<Frame> frames) {
		this.frames = frames;
		this.index = 0;
		this.ticksIntoFrame = 0;
		if (frames.isEmpty()) return;
		Frame first = frames.get(0);
		setPos(first.x(), first.y(), first.z());
		setYaw(first.yaw());
		setPitch(first.pitch());
		resetPosition();
		this.prevYaw = first.yaw();
		this.prevPitch = first.pitch();
	}

	/** Called once per client tick. */
	public void step() {
		resetPosition();
		this.prevYaw = getYaw();
		this.prevPitch = getPitch();
		if (frames.size() < 2 || index >= frames.size() - 1) return;
		Frame from = frames.get(index);
		Frame to = frames.get(index + 1);
		ticksIntoFrame++;
		float t = from.ticks() <= 0 ? 1f : Math.min(1f, ticksIntoFrame / (float) from.ticks());
		float eased = ease(t, from.ease());
		setPos(MathHelper.lerp(eased, from.x(), to.x()), MathHelper.lerp(eased, from.y(), to.y()), MathHelper.lerp(eased, from.z(), to.z()));
		setYaw(MathHelper.lerpAngleDegrees(eased, from.yaw(), to.yaw()));
		setPitch(MathHelper.lerp(eased, from.pitch(), to.pitch()));
		if (ticksIntoFrame >= from.ticks()) {
			index++;
			ticksIntoFrame = 0;
		}
	}

	private static float ease(float t, int kind) {
		return switch (kind) {
			case CinematicPayloads.EASE_SMOOTH -> t * t * (3f - 2f * t);
			case CinematicPayloads.EASE_OUT -> 1f - (1f - t) * (1f - t);
			default -> t;
		};
	}

	@Override
	public void tick() {
		// Not in a world: nothing to do. Stepping happens from the client tick.
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	@Override
	public boolean shouldRender(double distance) {
		return false;
	}

	@Override
	public boolean isInvisible() {
		return true;
	}

	@Override
	public boolean canHit() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return false;
	}
}
