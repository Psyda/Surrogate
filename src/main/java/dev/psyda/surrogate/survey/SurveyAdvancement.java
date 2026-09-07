package dev.psyda.surrogate.survey;

import dev.psyda.surrogate.Surrogate;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The whole-map moment. When a survey network covers every shelter on Sallow at once, everybody standing
 * near the table is told so, loudly, exactly once each.
 *
 * <p>It is not a real advancement in the data pack sense — there is no criterion a datapack could write for
 * "the union of these discs contains all seven of these points" — so it is a message, a sound, and a set of
 * who has already had it. The set is in memory: getting it twice across two sessions is a smaller problem
 * than another persistent state file for one boolean.
 */
public final class SurveyAdvancement {
	/** How near the table you have to be to be the one who did it. */
	private static final double CREDIT = 16.0;

	private static final Set<UUID> TOLD = new HashSet<>();

	private SurveyAdvancement() {
	}

	public static void award(ServerWorld world, BlockPos station) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.getBlockPos().isWithinDistance(station, CREDIT)) continue;
			if (!TOLD.add(player.getUuid())) continue;
			player.sendMessage(Text.translatable("advancement.surrogate.whole_map.title")
					.formatted(Formatting.GOLD, Formatting.BOLD), false);
			player.sendMessage(Text.translatable("advancement.surrogate.whole_map.description")
					.formatted(Formatting.GRAY), false);
			world.playSound(null, station, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.0f);
			Surrogate.LOGGER.info("Survey: {} has the whole map on the table", player.getGameProfile().getName());
		}
	}

	/** For the test command and for a fresh world: forget who has been told. */
	public static void reset() {
		TOLD.clear();
	}
}
