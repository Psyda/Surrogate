package dev.psyda.surrogate.fauna;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Go and stand near the brightest thing. That is the whole of a tocker's ambition.
 *
 * <p>A player counts as a light if they are standing in one — a headlamp, a lit room, a torch on the wall
 * beside them — which is why they follow a chassis about and why they stop bothering once you walk into the
 * dark. Between people it falls back to the brightest block position it can see, so a lamp left on a crate
 * gathers a small crowd.
 *
 * <p>It stops at {@code personal} blocks and stays there. Getting closer would mean getting trodden on, and
 * a tocker that has been trodden on does not come back.
 */
public class SeekLightGoal extends Goal {
	private final PathAwareEntity mob;
	private final double speed;
	private final double personal;
	private final double range;

	/** Where it is heading. Null between searches. */
	@Nullable
	private BlockPos target;
	/** Ticks until it looks again. Re-pathing every tick would make it jitter between two equal lamps. */
	private int recheck;

	public SeekLightGoal(PathAwareEntity mob, double speed, double personal, double range) {
		this.mob = mob;
		this.speed = speed;
		this.personal = personal;
		this.range = range;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (--recheck > 0) return false;
		recheck = 20;
		target = brightest();
		return target != null && mob.getBlockPos().getSquaredDistance(target) > personal * personal;
	}

	@Override
	public boolean shouldContinue() {
		return target != null
				&& !mob.getNavigation().isIdle()
				&& mob.getBlockPos().getSquaredDistance(target) > personal * personal;
	}

	@Override
	public void start() {
		if (target != null) mob.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
	}

	@Override
	public void stop() {
		target = null;
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (target != null) mob.getLookControl().lookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
	}

	/**
	 * The brightest thing worth walking to. People win ties against blocks at equal light, because a person
	 * carrying a lamp is a moving lamp and following one is the behaviour this is for.
	 */
	@Nullable
	private BlockPos brightest() {
		BlockPos best = null;
		int bestLight = mob.getWorld().getLightLevel(mob.getBlockPos());
		PlayerEntity player = mob.getWorld().getClosestPlayer(mob, range);
		if (player != null && !player.isSpectator()) {
			int light = mob.getWorld().getLightLevel(player.getBlockPos());
			if (light > bestLight) {
				bestLight = light;
				best = player.getBlockPos();
			}
		}
		// A coarse scan on a three-block lattice: enough to notice a lamp across a room without walking the
		// whole neighbourhood every second.
		BlockPos origin = mob.getBlockPos();
		int r = (int) range;
		for (int dx = -r; dx <= r; dx += 3) {
			for (int dz = -r; dz <= r; dz += 3) {
				for (int dy = -2; dy <= 3; dy += 3) {
					BlockPos at = origin.add(dx, dy, dz);
					int light = mob.getWorld().getLightLevel(at);
					if (light > bestLight) {
						bestLight = light;
						best = at;
					}
				}
			}
		}
		return best;
	}
}
