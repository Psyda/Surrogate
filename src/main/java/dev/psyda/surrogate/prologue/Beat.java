package dev.psyda.surrogate.prologue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One step of a scripted sequence. A {@link Director} starts a beat, then ticks it until it says it is done.
 * Most beats finish instantly; the ones that wait (lines, walks, objectives) hold the script up.
 */
public abstract class Beat {
	void start(Director d) {
	}

	/** @return true once this beat is finished and the next one may start */
	abstract boolean tick(Director d);

	/** A named point the director can jump to. */
	public static final class Label extends Beat {
		final String name;

		public Label(String name) {
			this.name = name;
		}

		@Override
		void start(Director d) {
			Surrogate.LOGGER.info("{}: {}", d.getClass().getSimpleName(), name);
		}

		@Override
		boolean tick(Director d) {
			return true;
		}
	}

	public static final class Run extends Beat {
		private final Runnable action;

		public Run(Runnable action) {
			this.action = action;
		}

		@Override
		void start(Director d) {
			action.run();
		}

		@Override
		boolean tick(Director d) {
			return true;
		}
	}

	public static final class Wait extends Beat {
		private final int ticks;
		private int elapsed;

		public Wait(int ticks) {
			this.ticks = ticks;
		}

		@Override
		void start(Director d) {
			elapsed = 0;
		}

		@Override
		boolean tick(Director d) {
			return ++elapsed >= ticks;
		}
	}

	/** A subtitle. Spoken lines make the speaker look at the player; radio lines let the director react. */
	public static final class Line extends Beat {
		@Nullable
		private final Crew who;
		private final String speakerKey;
		private final String textKey;
		private final int style;
		private final int ticks;
		private final boolean wait;
		private final String arg;
		private int duration;
		private int elapsed;

		public Line(@Nullable Crew who, String speakerKey, String textKey, int style, int ticks, boolean wait, String arg) {
			this.who = who;
			this.speakerKey = speakerKey;
			this.textKey = textKey;
			this.style = style;
			this.ticks = ticks;
			this.wait = wait;
			this.arg = arg;
		}

		@Override
		void start(Director d) {
			boolean chat = style == CinematicPayloads.SPEECH || style == CinematicPayloads.RADIO
					|| style == CinematicPayloads.INTERCOM || style == CinematicPayloads.SYSTEM;
			duration = d.sendLine(speakerKey, textKey, style, ticks, chat, arg);
			elapsed = 0;
			if (who != null && style == CinematicPayloads.SPEECH) {
				CrewEntity crew = d.crew(who);
				if (crew != null && !crew.isFixed()) crew.setLookTarget(d.player());
			}
		}

		@Override
		boolean tick(Director d) {
			if (!wait) return true;
			elapsed++;
			if (who != null && style == CinematicPayloads.RADIO) d.onRadioLine(who);
			if (elapsed < duration + 8) return false;
			if (who != null) {
				CrewEntity crew = d.crew(who);
				if (crew != null) crew.setLookTarget(null);
			}
			return true;
		}
	}

	/** Waits for something the player does, nagging once on the way and giving up after a while. */
	public static final class Until extends Beat {
		private final BooleanSupplier condition;
		private final int timeout;
		@Nullable
		private final Crew nudgeWho;
		@Nullable
		private final String nudgeKey;
		private final int nudgeAfter;
		@Nullable
		private final Runnable onTimeout;
		private int elapsed;
		private boolean nudged;

		public Until(BooleanSupplier condition, int timeout, @Nullable Crew nudgeWho, @Nullable String nudgeKey, int nudgeAfter, @Nullable Runnable onTimeout) {
			this.condition = condition;
			this.timeout = timeout;
			this.nudgeWho = nudgeWho;
			this.nudgeKey = nudgeKey;
			this.nudgeAfter = nudgeAfter;
			this.onTimeout = onTimeout;
		}

		@Override
		void start(Director d) {
			elapsed = 0;
			nudged = false;
		}

		@Override
		boolean tick(Director d) {
			if (condition.getAsBoolean()) return true;
			elapsed++;
			if (!nudged && nudgeWho != null && nudgeKey != null && nudgeAfter > 0 && elapsed >= nudgeAfter) {
				nudged = true;
				d.nudge(nudgeWho, nudgeKey);
			}
			if (timeout > 0 && elapsed >= timeout) {
				if (onTimeout != null) onTimeout.run();
				return true;
			}
			return false;
		}
	}

	/** Sends a crew member walking and moves on without waiting. */
	public static final class Walk extends Beat {
		private final Crew who;
		private final Vec3d target;
		@Nullable
		private final BlockPos door;

		public Walk(Crew who, Vec3d target, @Nullable BlockPos door) {
			this.who = who;
			this.target = target;
			this.door = door;
		}

		@Override
		void start(Director d) {
			CrewEntity crew = d.crew(who);
			if (crew != null) crew.walkTo(d.at(target), door == null ? null : d.origin().add(door));
		}

		@Override
		boolean tick(Director d) {
			return true;
		}
	}

	/** Waits for a crew member to reach a spot, re-pathing if they stall and teleporting if they never make it. */
	public static final class Arrive extends Beat {
		private final Crew who;
		private final Vec3d target;
		private final int timeout;
		private int elapsed;

		public Arrive(Crew who, Vec3d target, int timeout) {
			this.who = who;
			this.target = target;
			this.timeout = timeout;
		}

		@Override
		void start(Director d) {
			elapsed = 0;
		}

		@Override
		boolean tick(Director d) {
			CrewEntity crew = d.crew(who);
			if (crew == null || crew.isFixed()) return true;
			Vec3d at = d.at(target);
			if (crew.hasArrived(at)) {
				crew.stopWalking();
				return true;
			}
			elapsed++;
			if (elapsed % 40 == 0 && crew.getNavigation().isIdle()) crew.retryWalk(at);
			if (elapsed >= timeout) {
				crew.stopWalking();
				crew.refreshPositionAndAngles(at.x, at.y, at.z, crew.getYaw(), 0f);
				return true;
			}
			return false;
		}
	}

	public static final class RobotWalk extends Beat {
		private final Vec3d target;

		public RobotWalk(Vec3d target) {
			this.target = target;
		}

		@Override
		void start(Director d) {
			RobotEntity robot = d.scriptedRobot();
			if (robot != null) robot.driveTo(d.atSurface(target));
		}

		@Override
		boolean tick(Director d) {
			return true;
		}
	}

	public static final class RobotArrive extends Beat {
		private final Vec3d target;
		private final int timeout;
		private int elapsed;

		public RobotArrive(Vec3d target, int timeout) {
			this.target = target;
			this.timeout = timeout;
		}

		@Override
		void start(Director d) {
			elapsed = 0;
		}

		@Override
		boolean tick(Director d) {
			RobotEntity robot = d.scriptedRobot();
			if (robot == null || !robot.isScripted()) return true;
			Vec3d at = d.atSurface(target);
			if (robot.hasArrived(at)) {
				robot.stopDriving();
				return true;
			}
			elapsed++;
			if (elapsed % 40 == 0 && robot.getNavigation().isIdle()) robot.driveTo(at);
			if (elapsed >= timeout) {
				robot.stopDriving();
				return true;
			}
			return false;
		}
	}

	/** Splices extra beats in right after itself when the condition holds. */
	public static final class Branch extends Beat {
		private final BooleanSupplier condition;
		private final List<Beat> beats;

		public Branch(BooleanSupplier condition, List<Beat> beats) {
			this.condition = condition;
			this.beats = beats;
		}

		@Override
		void start(Director d) {
			if (condition.getAsBoolean()) d.insertAfterCurrent(beats);
		}

		@Override
		boolean tick(Director d) {
			return true;
		}
	}

	public static final class JumpIf extends Beat {
		private final BooleanSupplier condition;
		private final String label;

		public JumpIf(BooleanSupplier condition, String label) {
			this.condition = condition;
			this.label = label;
		}

		@Override
		void start(Director d) {
			if (condition.getAsBoolean()) d.jump(label);
		}

		@Override
		boolean tick(Director d) {
			return true;
		}
	}

	/** Waits out whatever line was last sent outside the beat system (a radio call made from a lambda). */
	public static final class WaitLastLine extends Beat {
		private int elapsed;

		@Override
		void start(Director d) {
			elapsed = 0;
		}

		@Override
		boolean tick(Director d) {
			return ++elapsed >= d.lastLineTicks() + 8;
		}
	}
}
