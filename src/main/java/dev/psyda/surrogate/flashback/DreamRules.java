package dev.psyda.surrogate.flashback;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.registry.ModTags;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * What the dream will not let you do: take the house apart, or leave it by any door it did not put there.
 *
 * <p>The rest of the flashback is built on the opposite principle — the furniture comes off in your hands,
 * and a player who packs somebody's kitchen into a suitcase has understood the scene exactly. But a room you
 * can dismantle is a room you can dismantle a wall of, and behind every wall in there is a flat generator's
 * worth of nothing. So the shell is the shell: {@link ModTags#DREAM_FIXED} is the floor, the walls, the
 * ceiling, the stairs, the windows and the doors, and none of it moves.
 */
public final class DreamRules {
	/** How far below the floor counts as having got out, and where they are put back to. */
	private static final int FALLEN = 6;

	private DreamRules() {
	}

	public static void registerEvents() {
		PlayerBlockBreakEvents.BEFORE.register(DreamRules::onBreak);
	}

	private static boolean onBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, net.minecraft.block.entity.BlockEntity entity) {
		if (!DreamDimension.isDream(world) || !state.isIn(ModTags.DREAM_FIXED)) return true;
		if (player instanceof ServerPlayerEntity served) {
			served.sendMessage(Text.translatable("message.surrogate.dream.fixed").formatted(Formatting.GRAY), true);
		}
		return false;
	}

	/**
	 * Called every tick the dream is running: anybody who has got below the floor anyway goes back on it.
	 *
	 * <p>The shell holds, so this should never fire. It is here because the alternative to a cheap check is a
	 * player falling out of their own memory into a void with no bottom and no way back, in a dimension whose
	 * only exit is a scripted teleport.
	 */
	public static void keepInside(ServerWorld dream, ServerPlayerEntity player, Vec3d putBack) {
		if (player.getWorld() != dream) return;
		if (player.getY() > DreamDimension.ORIGIN.getY() - FALLEN) return;
		Surrogate.LOGGER.warn("Flashback: {} was below the floor at {}; putting them back",
				player.getGameProfile().getName(), player.getBlockPos().toShortString());
		player.teleport(dream, putBack.x, putBack.y, putBack.z, java.util.Set.of(), player.getYaw(), 0f);
		player.setVelocity(Vec3d.ZERO);
		player.fallDistance = 0f;
	}

	/**
	 * Everything in the dream that is not a player, removed: the dog, whoever was in the doorway, the seat
	 * under the player, the darts on the floor. Called on the way in as well as on the way out, so a night
	 * that ended badly (a crash mid-dream) does not leave last night's dog wandering through this one.
	 */
	public static int clearEntities(ServerWorld dream) {
		List<Entity> found = new ArrayList<>();
		for (Entity entity : dream.iterateEntities()) {
			if (!(entity instanceof PlayerEntity)) found.add(entity);
		}
		for (Entity entity : found) entity.discard();
		return found.size();
	}
}
