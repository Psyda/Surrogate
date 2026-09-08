package dev.psyda.surrogate.prologue;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.psyda.surrogate.assay.Assay;
import dev.psyda.surrogate.assay.AssayState;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.psyda.surrogate.entity.CrawlerCommand;
import dev.psyda.surrogate.transit.Transit;
import dev.psyda.surrogate.transit.TransitState;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.TerrainScan;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /surrogate prologue start|skip|fast|status}: replay, cut short, speed up or inspect the opening in
 * the pod. {@code /surrogate transit start|skip|day <n>|fast|status}: the same for the week on the ship.
 * {@code /surrogate research start|stage <name>|skip|fast|status}: the same for acts one and two, the
 * neighbours' research runs and the range gate at Tanaka.
 * {@code /surrogate assay start|stage <name>|skip|site|status}: the same for Contract Seven, the corporation's
 * research task, where {@code site} teleports to the company's designated pad.
 */
public final class PrologueCommand {
	private PrologueCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				CommandManager.literal("surrogate").requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("prologue")
								.then(CommandManager.literal("start").executes(context -> start(context.getSource())))
								.then(CommandManager.literal("skip").executes(context -> skip(context.getSource())))
								.then(CommandManager.literal("day").then(CommandManager.argument("day", IntegerArgumentType.integer(1, 3))
										.executes(context -> day(context.getSource(), IntegerArgumentType.getInteger(context, "day")))))
								.then(CommandManager.literal("fast").executes(context -> fast(context.getSource())))
								.then(CommandManager.literal("status").executes(context -> status(context.getSource()))))
						.then(CommandManager.literal("transit")
								.then(CommandManager.literal("start").executes(context -> transitStart(context.getSource())))
								.then(CommandManager.literal("skip").executes(context -> transitSkip(context.getSource())))
								.then(CommandManager.literal("day").then(CommandManager.argument("day", IntegerArgumentType.integer(1, 7))
										.executes(context -> transitDay(context.getSource(), IntegerArgumentType.getInteger(context, "day")))))
								.then(CommandManager.literal("fast").executes(context -> fast(context.getSource())))
								.then(CommandManager.literal("status").executes(context -> transitStatus(context.getSource()))))
						.then(CommandManager.literal("research")
								.then(CommandManager.literal("start").executes(context -> researchAt(context.getSource(), "core")))
								.then(CommandManager.literal("stage").then(CommandManager.argument("stage", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(RESEARCH_STAGES, builder))
										.executes(context -> researchAt(context.getSource(), StringArgumentType.getString(context, "stage")))))
								.then(CommandManager.literal("skip").executes(context -> researchSkip(context.getSource())))
								.then(CommandManager.literal("fast").executes(context -> fast(context.getSource())))
								.then(CommandManager.literal("status").executes(context -> researchStatus(context.getSource()))))
						.then(CommandManager.literal("assay")
								.then(CommandManager.literal("start").executes(context -> assayAt(context.getSource(), "stake")))
								.then(CommandManager.literal("stage").then(CommandManager.argument("stage", StringArgumentType.word())
										.suggests((context, builder) -> CommandSource.suggestMatching(ASSAY_STAGES, builder))
										.executes(context -> assayAt(context.getSource(), StringArgumentType.getString(context, "stage")))))
								.then(CommandManager.literal("skip").executes(context -> assaySkip(context.getSource())))
								.then(CommandManager.literal("fast").executes(context -> fast(context.getSource())))
								.then(CommandManager.literal("site").executes(context -> assaySite(context.getSource())))
								.then(CommandManager.literal("ship").executes(context -> assayShip(context.getSource())))
								.then(CommandManager.literal("status").executes(context -> assayStatus(context.getSource()))))
						.then(TerrainScan.command())
						.then(CrawlerCommand.command())
						.then(dev.psyda.surrogate.flashback.FlashbackCommand.flashbackCommand())
						.then(dev.psyda.surrogate.errand.ErrandCommand.errandCommand())
						.then(dev.psyda.surrogate.errand.ErrandCommand.faunaCommand())
						.then(dev.psyda.surrogate.rescue.RescueCommand.command())));
	}

	private static int start(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		Prologue.restart(player);
		source.sendFeedback(() -> Text.literal("Prologue restarted."), true);
		return 1;
	}

	private static int day(ServerCommandSource source, int day) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		Prologue.restartAt(player, "day" + day);
		source.sendFeedback(() -> Text.literal("Prologue restarted at day " + day + "."), true);
		return 1;
	}

	private static int skip(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!Prologue.isRunning()) {
			source.sendError(Text.literal("The prologue is not running."));
			return 0;
		}
		Prologue.skip(player);
		source.sendFeedback(() -> Text.literal("Prologue skipped."), true);
		return 1;
	}

	private static int fast(ServerCommandSource source) {
		Director.fast = !Director.fast;
		source.sendFeedback(() -> Text.literal("Fast mode " + (Director.fast ? "on" : "off") + " (applies to the next start)."), true);
		return 1;
	}

	private static int status(ServerCommandSource source) {
		HabitatState state = HabitatState.get(source.getServer());
		Prologue running = Prologue.running();
		String text = "Prologue stage " + state.prologueStage
				+ (running == null ? ", not running" : ", running at " + running.currentLabel())
				+ (Director.fast ? ", fast" : "");
		source.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	private static int transitStart(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		Transit.restart(player);
		source.sendFeedback(() -> Text.literal("Transit restarted."), true);
		return 1;
	}

	private static int transitSkip(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!Transit.isRunning()) {
			source.sendError(Text.literal("The transit is not running."));
			return 0;
		}
		Transit.skipToDrop(player);
		source.sendFeedback(() -> Text.literal("Transit skipped to the drop."), true);
		return 1;
	}

	private static int transitDay(ServerCommandSource source, int day) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!Transit.isRunning()) {
			source.sendError(Text.literal("The transit is not running."));
			return 0;
		}
		Transit.jumpToDay(player, day);
		source.sendFeedback(() -> Text.literal("Transit jumped to day " + day + "."), true);
		return 1;
	}

	private static final java.util.List<String> RESEARCH_STAGES = java.util.List.of("core", "wind", "seep", "range");

	private static int researchAt(ServerCommandSource source, String stage) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!RESEARCH_STAGES.contains(stage)) {
			source.sendError(Text.literal("Stages: " + String.join(", ", RESEARCH_STAGES)));
			return 0;
		}
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		dev.psyda.surrogate.research.Research.restartAt(player, stage);
		source.sendFeedback(() -> Text.literal("Research restarted at " + stage + "."), true);
		return 1;
	}

	private static int researchSkip(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!dev.psyda.surrogate.research.Research.isRunning()) {
			source.sendError(Text.literal("The research runs are not running."));
			return 0;
		}
		Director.skipRequested(player);
		source.sendFeedback(() -> Text.literal("Research run skipped."), true);
		return 1;
	}

	private static int researchStatus(ServerCommandSource source) {
		dev.psyda.surrogate.research.ResearchState research = dev.psyda.surrogate.research.ResearchState.get(source.getServer());
		dev.psyda.surrogate.research.Research running = dev.psyda.surrogate.research.Research.running();
		String seep = research.seepHome ? "home" : research.seepTaken ? "taken" : "none";
		String text = "Research stage " + research.stage
				+ ", core " + research.coreDepth + "/" + dev.psyda.surrogate.research.ResearchState.CORE_DEPTH
				+ ", stakes " + research.stakes.size() + "/" + dev.psyda.surrogate.research.ResearchState.STAKES
				+ ", seep " + seep
				+ (research.schematic ? ", schematic" : "")
				+ (research.relay ? ", relay" : "")
				+ (research.damper ? ", damper" : "")
				+ ", errands " + research.errandsDone() + "/" + dev.psyda.surrogate.research.ResearchState.ERRANDS
				+ (running == null ? ", not running" : ", running at " + running.currentLabel())
				+ (Director.fast ? ", fast" : "");
		source.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	private static final java.util.List<String> ASSAY_STAGES = java.util.List.of("stake", "pad", "core", "payload", "ignition");

	private static int assayAt(ServerCommandSource source, String stage) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!ASSAY_STAGES.contains(stage)) {
			source.sendError(Text.literal("Stages: " + String.join(", ", ASSAY_STAGES)));
			return 0;
		}
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		Assay.restartAt(player, stage);
		source.sendFeedback(() -> Text.literal("Assay restarted at " + stage + "."), true);
		return 1;
	}

	private static int assaySkip(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!Assay.isRunning()) {
			source.sendError(Text.literal("The assay is not running."));
			return 0;
		}
		Director.skipRequested(player);
		source.sendFeedback(() -> Text.literal("Assay stage skipped."), true);
		return 1;
	}

	/** Puts the player at the company's designated pad, wherever it turned out to be. */
	/** Dev: fly the company ship past the player, so the flyover can be watched without running the arc. */
	private static int assayShip(ServerCommandSource source) throws CommandSyntaxException {
		// Usable from the console too, so a dev client can fire it without an op'd player.
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) player = source.getServer().getPlayerManager().getPlayerList().stream().findFirst().orElse(null);
		if (player == null) {
			source.sendError(Text.literal("No player to fly the ship past."));
			return 0;
		}
		dev.psyda.surrogate.entity.CompanyShipEntity ship =
				new dev.psyda.surrogate.entity.CompanyShipEntity(dev.psyda.surrogate.registry.ModEntities.COMPANY_SHIP, player.getServerWorld());
		ship.refreshPositionAndAngles(player.getX() - 90, player.getY() + 95, player.getZ(), 270f, 0f);
		ship.setSpeed(0.85f);
		ship.setLife(320);
		player.getServerWorld().spawnEntity(ship);
		source.sendFeedback(() -> Text.literal("Company ship inbound from the west."), true);
		return 1;
	}

	private static int assaySite(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		AssayState assay = AssayState.get(source.getServer());
		if (assay.padSite == null) {
			source.sendError(Text.literal("No pad site has been chosen yet."));
			return 0;
		}
		net.minecraft.util.math.BlockPos site = dev.psyda.surrogate.assay.PadSite.resolve(source.getServer().getOverworld(), assay);
		player.teleport(source.getServer().getOverworld(), site.getX() + 0.5, site.getY() + 1, site.getZ() + 12.5, 0f, 0f);
		source.sendFeedback(() -> Text.literal("Pad site at " + site.toShortString() + "."), true);
		return 1;
	}

	private static int assayStatus(ServerCommandSource source) {
		AssayState assay = AssayState.get(source.getServer());
		Assay running = Assay.running();
		String site = assay.padSite == null ? "unchosen" : assay.padSite.toShortString();
		String text = "Assay stage " + assay.stage + ", site " + site + ", courses " + assay.courses
				+ ", core " + assay.coreSamples + "/" + Assay.CORE_TARGET
				+ (assay.shipSeen ? ", ship seen" : "")
				+ (running == null ? ", not running" : ", running at " + running.currentLabel())
				+ (Director.fast ? ", fast" : "");
		source.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	private static int transitStatus(ServerCommandSource source) {
		TransitState state = TransitState.get(source.getServer());
		Transit running = Transit.running();
		String text = "Transit stage " + state.stage + " (" + state.label + ")"
				+ (running == null ? ", not running" : ", running at " + running.currentLabel())
				+ (Director.fast ? ", fast" : "");
		source.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}
}
