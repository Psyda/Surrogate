package dev.psyda.surrogate.flashback;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * {@code /surrogate flashback} — the night before the contract, on demand.
 *
 * <p>Its trigger is sleeping in a bed within two days of landing, which is about forty minutes into a fresh
 * save and exactly once per save, so without this it could not be looked at twice.
 */
public final class FlashbackCommand {
	private FlashbackCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> flashbackCommand() {
		return CommandManager.literal("flashback")
				.then(CommandManager.literal("start").executes(context -> start(context.getSource(), null))
						.then(CommandManager.argument("place", com.mojang.brigadier.arguments.StringArgumentType.word())
								.executes(context -> start(context.getSource(),
										com.mojang.brigadier.arguments.StringArgumentType.getString(context, "place")))))
				.then(CommandManager.literal("skip").executes(context -> skip(context.getSource())))
				.then(CommandManager.literal("answer")
						.then(CommandManager.argument("option", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8))
								.executes(context -> answer(context.getSource(),
										com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "option")))))
				.then(CommandManager.literal("aim")
						.then(CommandManager.argument("who", com.mojang.brigadier.arguments.StringArgumentType.word())
								.executes(context -> aim(context.getSource(),
										com.mojang.brigadier.arguments.StringArgumentType.getString(context, "who")))))
				.then(CommandManager.literal("status").executes(context -> status(context.getSource())))
				.then(CommandManager.literal("reset").executes(context -> reset(context.getSource())));
	}

	/**
	 * The player the command is about: whoever ran it, or, from a console with nobody behind it (the dev
	 * client's scripted command runs as the server), the first player there is.
	 */
	private static ServerPlayerEntity subject(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player != null) return player;
		java.util.List<ServerPlayerEntity> players = source.getServer().getPlayerManager().getPlayerList();
		return players.isEmpty() ? null : players.get(0);
	}

	/** {@code start} goes under; {@code start home|work|bar} goes under and straight through that door. */
	private static int start(ServerCommandSource source, String placeKey) {
		ServerPlayerEntity player = subject(source);
		if (player == null) {
			source.sendError(Text.literal("Nobody to take: no player is online."));
			return 0;
		}
		DreamPlace where = null;
		if (placeKey != null) {
			where = DreamPlace.byKey(placeKey);
			if (where == null) {
				source.sendError(Text.literal("No such place: home, work or bar."));
				return 0;
			}
		}
		Flashback.force(source.getServer(), player, where);
		String going = where == null ? "going under." : "going under, straight to the " + where.key() + ".";
		source.sendFeedback(() -> Text.literal("Flashback: " + going), true);
		return 1;
	}

	private static int skip(ServerCommandSource source) {
		ServerPlayerEntity player = subject(source);
		if (player == null || !Flashback.isRunning()) {
			source.sendError(Text.literal("No flashback running."));
			return 0;
		}
		dev.psyda.surrogate.prologue.Director.skipRequested(player);
		return 1;
	}

	/**
	 * Picks one of the answers on the table.
	 *
	 * <p>The question is a screen, and a screen is the one thing a headless test cannot click. This is the
	 * same entry point the button uses, so what the test drives is what the player drives.
	 */
	private static int answer(ServerCommandSource source, int option) {
		ServerPlayerEntity player = subject(source);
		if (player == null || !Flashback.isRunning()) {
			source.sendError(Text.literal("No flashback running."));
			return 0;
		}
		if (!dev.psyda.surrogate.prologue.Director.choicePicked(player, option)) {
			// Saying "answered" for an answer nobody was waiting for is how a smoke test comes to believe it
			// has driven a conversation it never reached.
			source.sendError(Text.literal("No question on the table."));
			return 0;
		}
		source.sendFeedback(() -> Text.literal("Answered " + option + "."), true);
		return 1;
	}

	/**
	 * What a player is looking at, from the server's point of view: where their eyes are, which way, and the
	 * first block their reach lands on. For the smoke test, whose fake players click things they cannot see.
	 */
	private static int aim(ServerCommandSource source, String who) {
		ServerPlayerEntity player = source.getServer().getPlayerManager().getPlayer(who);
		if (player == null) {
			source.sendError(Text.literal("No player called " + who + "."));
			return 0;
		}
		net.minecraft.util.hit.HitResult hit = player.raycast(4.5, 0f, false);
		String target = hit instanceof net.minecraft.util.hit.BlockHitResult block
				? block.getBlockPos().toShortString() + " " + player.getWorld().getBlockState(block.getBlockPos()).getBlock().getTranslationKey() + " side " + block.getSide()
				: hit.getType().toString();
		source.sendFeedback(() -> Text.literal(String.format("aim: %s in %s at %s eye %.2f yaw %.1f pitch %.1f -> %s",
				who, player.getWorld().getRegistryKey().getValue(), player.getBlockPos().toShortString(),
				player.getEyeY(), player.getYaw(), player.getPitch(), target)), false);
		return 1;
	}

	private static int status(ServerCommandSource source) {
		FlashbackState state = FlashbackState.get(source.getServer());
		String where = state.place == null ? "unanswered" : state.place.key();
		String why = state.place == null || state.reason < 0 ? "unanswered" : state.place.reasonKey(state.reason);
		source.sendFeedback(() -> Text.literal("Flashback: " + (state.played ? "played" : "not yet")
				+ (Flashback.isRunning() ? " (running)" : "")).formatted(Formatting.BOLD), false);
		source.sendFeedback(() -> Text.literal("  where: " + where + "   why: " + why
				+ "   packed: " + state.packed), false);
		source.sendFeedback(() -> Text.literal("  inventory stashed: " + (state.stashed != null))
				.formatted(state.stashed != null ? Formatting.YELLOW : Formatting.GRAY), false);
		StringBuilder seen = new StringBuilder();
		for (DreamPlace been : DreamPlace.all()) {
			if (!state.hasVisited(been)) continue;
			if (seen.length() > 0) seen.append(",");
			seen.append(been.key());
		}
		String asking = dev.psyda.surrogate.prologue.Director.questionOnTheTable();
		source.sendFeedback(() -> Text.literal("  asking: " + (asking == null ? "none" : asking) + "   label: " + Flashback.label()
				+ "   done: " + Flashback.notes()), false);
		String visited = seen.length() == 0 ? "none" : seen.toString();
		source.sendFeedback(() -> Text.literal("  visited: " + visited + "   answers: " + state.answers.size()
				+ "   last night: " + state.lastNight), false);
		state.answers.forEach((question, choice) ->
				source.sendFeedback(() -> Text.literal("    " + question + " = " + choice).formatted(Formatting.DARK_GRAY), false));
		return 1;
	}

	/**
	 * Forget it happened, so it can happen again.
	 *
	 * <p>Hands back a stash first if one is somehow still held. That can only be true after a crash in the
	 * middle of a dream, and a reset that dropped it would be the one bug in this feature that costs the
	 * player something real.
	 */
	private static int reset(ServerCommandSource source) {
		FlashbackState state = FlashbackState.get(source.getServer());
		ServerPlayerEntity player = source.getPlayer();
		if (state.stashed != null && player != null) {
			player.getInventory().readNbt(state.stashed);
			source.sendFeedback(() -> Text.literal("Gave a stashed inventory back first.").formatted(Formatting.YELLOW), true);
		}
		state.stashed = null;
		state.played = false;
		state.place = null;
		state.reason = -1;
		state.packed = 0;
		// All of it, including the doors already used and what was said about the player. A reset that left
		// those behind would hand back a dream with two thirds of itself bricked up, which is not a reset.
		state.visited = 0;
		state.lastNight = -1L;
		state.answers.clear();
		state.markDirty();
		source.sendFeedback(() -> Text.literal("Flashback: reset."), true);
		return 1;
	}
}
