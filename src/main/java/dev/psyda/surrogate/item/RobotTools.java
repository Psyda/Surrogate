package dev.psyda.surrogate.item;

import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/** Shared gating for tools that only work through a chassis. */
public final class RobotTools {
	private static long lastDenial;

	private RobotTools() {
	}

	/** The running chassis {@code user} is piloting, or null. */
	@Nullable
	public static RobotEntity chassisOf(@Nullable LivingEntity user) {
		if (user instanceof PlayerEntity player && player.getVehicle() instanceof RobotEntity robot
				&& robot.isPilot(player) && robot.getState() == RobotState.ONLINE) {
			return robot;
		}
		return null;
	}

	/** True when the user is in a chassis with at least {@code energy} to spare; otherwise tells them why not. */
	public static boolean check(@Nullable LivingEntity user, int energy) {
		RobotEntity robot = chassisOf(user);
		if (robot == null) {
			deny(user, "message.surrogate.tool_needs_chassis");
			return false;
		}
		if (robot.getEnergy() < energy) {
			deny(user, "message.surrogate.tool_no_power");
			return false;
		}
		return true;
	}

	private static void deny(@Nullable LivingEntity user, String key) {
		// Only the client shows the overlay, and only every so often: this runs every tick while the button is held.
		if (!(user instanceof PlayerEntity player) || !player.getWorld().isClient) return;
		long now = System.currentTimeMillis();
		if (now - lastDenial < 1500) return;
		lastDenial = now;
		player.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
	}
}
