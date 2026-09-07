package dev.psyda.surrogate.rescue;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.psyda.surrogate.assay.AssayState;
import dev.psyda.surrogate.block.SpanAnchorBlock;
import dev.psyda.surrogate.item.SpanKitItem;
import dev.psyda.surrogate.network.MissionPayload;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.block.Block;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * {@code /surrogate rescue ...}: every piece of acts four and five, reachable without playing the ten hours
 * that lead to them.
 *
 * <p>The same argument the errand command makes applies twice over here. The conference is behind a finished
 * contract; the far side is behind a bridge, a lava channel, a blown airlock and a chasm; and Novak is behind
 * all four. None of that can be looked at, let alone tested, if the only way in is to earn it.
 */
public final class RescueCommand {
	private static final List<String> PLACES = List.of("hub", "crossing", "flow", "brandt", "reyes", "novak");

	private RescueCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> command() {
		return CommandManager.literal("rescue")
				.then(CommandManager.literal("status").executes(context -> status(context.getSource())))
				.then(CommandManager.literal("board").executes(context -> board(context.getSource())))
				.then(CommandManager.literal("call").executes(context -> call(context.getSource())))
				.then(CommandManager.literal("skip").executes(context -> skip(context.getSource())))
				.then(CommandManager.literal("plan").executes(context -> plan(context.getSource())))
				.then(CommandManager.literal("span").executes(context -> span(context.getSource())))
				.then(CommandManager.literal("flow").executes(context -> flow(context.getSource())))
				.then(CommandManager.literal("cut").executes(context -> cut(context.getSource())))
				.then(CommandManager.literal("plate").executes(context -> plate(context.getSource())))
				.then(CommandManager.literal("lift").executes(context -> lift(context.getSource())))
				.then(CommandManager.literal("kit").executes(context -> kit(context.getSource())))
				.then(CommandManager.literal("reset").executes(context -> reset(context.getSource())))
				.then(CommandManager.literal("tp").then(CommandManager.argument("where", StringArgumentType.word())
						.suggests((context, builder) -> CommandSource.suggestMatching(PLACES, builder))
						.executes(context -> teleport(context.getSource(), StringArgumentType.getString(context, "where")))));
	}

	// ------------------------------------------------------------------ reading

	private static int status(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		String text = "Rescue " + MissionBoard.summary(server)
				+ (Conference.isRunning() ? ", conference running at " + Conference.running().currentLabel() : "")
				+ (Rescue.carrying() ? ", carrying" : "")
				+ (Director.fast ? ", fast" : "");
		source.sendFeedback(() -> Text.literal(text), false);
		return 1;
	}

	/** The board as the terminal would show it, one row a line, for a test that has no screen to read. */
	private static int board(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		for (MissionPayload.Row row : MissionBoard.rows(server)) {
			String state = switch (row.state()) {
				case MissionPayload.HOME -> "home";
				case MissionPayload.ABOARD -> "aboard";
				case MissionPayload.REACHED -> "reached";
				default -> "unreached";
			};
			String line = "board " + Survivor.byId(row.survivor()).key() + ": " + state
					+ ", scrubber " + row.scrubber()
					+ ", blocked " + (row.blocked().isEmpty() ? "nothing" : row.blocked());
			source.sendFeedback(() -> Text.literal(line), false);
		}
		for (String note : MissionBoard.notes(server)) source.sendFeedback(() -> Text.literal("note " + note), false);
		return 1;
	}

	// ------------------------------------------------------------------ act four

	private static int call(ServerCommandSource source) throws CommandSyntaxException {
		MinecraftServer server = source.getServer();
		ready(server);
		Conference.restart(server);
		source.sendFeedback(() -> Text.literal("Conference restarted."), true);
		return Conference.isRunning() ? 1 : 0;
	}

	private static int skip(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!Conference.isRunning()) {
			source.sendError(Text.literal("The conference is not running."));
			return 0;
		}
		Director.skipRequested(player);
		source.sendFeedback(() -> Text.literal("Conference skipped."), true);
		return 1;
	}

	/** Everything the call leaves behind, without the four minutes: the plan, the kit and the board. */
	private static int plan(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ready(server);
		RescueState rescue = RescueState.get(server);
		rescue.called = true;
		rescue.stage = RescueState.STAGE_PLAN;
		rescue.markDirty();
		Rescue.planAgreed(server, source.getPlayer());
		source.sendFeedback(() -> Text.literal("The plan is agreed; the board is live."), true);
		return 1;
	}

	// ------------------------------------------------------------------ act five

	/** Plants an anchor at the narrows, aimed at the far side, and lays the whole deck. */
	private static int span(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ServerWorld world = server.getOverworld();
		RescueState rescue = RescueState.get(server);
		BlockPos crossing = Rescue.crossing(server);
		if (crossing == null) {
			source.sendError(Text.literal("No crossing found: this world has no far side."));
			return 0;
		}
		// The narrows are found off the noise with no world behind them, so the ground there is only a real
		// number once the chunks exist. Six chunks covers the survey, the anchor and the longest span.
		load(world, crossing, 6);
		SpanBuilder.Survey survey = SpanBuilder.survey(world, crossing, Rescue.crossingFacing(server), SpanBuilder.SURVEY_SPREAD);
		if (survey == null) {
			source.sendError(Text.literal("Nothing within " + SpanBuilder.SURVEY_SPREAD + " blocks of "
					+ crossing.toShortString() + " has a gap in front of it on any bearing."));
			// And what the ground actually does there, because "no gap" is not a diagnosis.
			dev.psyda.surrogate.world.Valleys.Masks masks = dev.psyda.surrogate.world.Valleys.masks(world);
			for (Direction facing : Direction.Type.HORIZONTAL) {
				StringBuilder profile = new StringBuilder("profile " + facing.asString() + ":");
				for (int d = 0; d <= 48; d += 4) {
					BlockPos at = crossing.offset(facing, d);
					profile.append(' ').append(world.getTopY(Heightmap.Type.OCEAN_FLOOR, at.getX(), at.getZ()));
					profile.append('/').append(masks == null ? "?"
							: String.format("%.2f", masks.rift(at.getX(), at.getZ())));
				}
				source.sendFeedback(() -> Text.literal(profile.toString()), false);
			}
			return 0;
		}
		BlockPos anchor = survey.anchor();
		Direction facing = survey.facing();
		int length = survey.length();
		world.setBlockState(anchor, ModBlocks.SPAN_ANCHOR.getDefaultState().with(SpanAnchorBlock.FACING, facing), Block.NOTIFY_ALL);
		rescue.anchor = anchor;
		rescue.spanStepX = facing.getOffsetX();
		rescue.spanStepZ = facing.getOffsetZ();
		rescue.spanLength = length;
		rescue.spanCourses = SpanBuilder.courses(length);
		rescue.spanDone = true;
		rescue.markDirty();
		for (int course = 1; course <= rescue.spanCourses; course++) SpanBuilder.lay(world, anchor, facing, length, course);
		source.sendFeedback(() -> Text.literal("Span of " + length + " blocks from " + anchor.toShortString()
				+ " facing " + facing.asString() + ", " + rescue.spanCourses + " courses, "
				+ (rescue.spanCourses * SpanKitItem.PLATES) + " plates' worth."), true);
		return 1;
	}

	private static int flow(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ServerWorld world = server.getOverworld();
		RescueState rescue = RescueState.get(server);
		SurvivorManager.Site brandt = Rescue.site(SurvivorManager.get(server), Survivor.BRANDT);
		if (brandt == null) {
			source.sendError(Text.literal("Brandt has no site on this world."));
			return 0;
		}
		HabitatState habitat = HabitatState.get(server);
		BlockPos from = habitat.origin == null ? BlockPos.ORIGIN : habitat.origin;
		if (rescue.flow != null) {
			source.sendFeedback(() -> Text.literal("Flow already at " + rescue.flow.toShortString() + "."), false);
			return 1;
		}
		// Around where the channel is going, not around Brandt: it is laid forty-four blocks back down the
		// road from him, and the builder now refuses to write into chunks that are not there.
		load(world, MagmaFlow.where(from, brandt.origin()), 4);
		MagmaFlow.build(world, rescue, from, brandt.origin());
		rescue.markDirty();
		BlockPos at = rescue.flow;
		source.sendFeedback(() -> Text.literal("Flow across the approach at " + (at == null ? "nowhere" : at.toShortString())
				+ ", wall at " + (rescue.flowWall == null ? "nowhere" : rescue.flowWall.toShortString()) + "."), true);
		return 1;
	}

	private static int cut(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		RescueState rescue = RescueState.get(server);
		if (rescue.flow == null) {
			source.sendError(Text.literal("There is no flow to cut. Run /surrogate rescue flow first."));
			return 0;
		}
		MagmaFlow.cut(server.getOverworld(), rescue);
		source.sendFeedback(() -> Text.literal("The channel is crust and the basin has it."), true);
		return 1;
	}

	private static int plate(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ServerWorld world = server.getOverworld();
		SurvivorManager.Site reyes = Rescue.site(SurvivorManager.get(server), Survivor.REYES);
		if (reyes == null || !reyes.built) {
			source.sendError(Text.literal("Clinic Nine has not been built yet. Go and load its chunks first."));
			return 0;
		}
		load(world, reyes.origin(), 4);
		for (BlockPos offset : dev.psyda.surrogate.survivor.SurvivorShelter.FRAME) {
			world.setBlockState(reyes.origin().add(offset), ModBlocks.HULL_PLATING.getDefaultState(), Block.NOTIFY_ALL);
		}
		source.sendFeedback(() -> Text.literal("Clinic Nine's frame is plated; the sweep will notice within a second."), true);
		return 1;
	}

	/**
	 * Picks Novak up, as using him would.
	 *
	 * <p>It exists because a Carpet fake player cannot press the button: its actions are driven straight into
	 * the interaction manager rather than through the play network handler, and Fabric's UseEntityCallback is
	 * injected into the handler, so the one interaction act five turns on is invisible to every headless
	 * test. This is the same call the callback makes, refusals and all.
	 */
	private static int lift(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ServerWorld world = player.getServerWorld();
		for (dev.psyda.surrogate.survivor.SurvivorEntity survivor : world.getEntitiesByClass(
				dev.psyda.surrogate.survivor.SurvivorEntity.class, player.getBoundingBox().expand(16.0),
				e -> e.getCharacter() == Survivor.NOVAK)) {
			boolean took = Rescue.lift(player, survivor);
			source.sendFeedback(() -> Text.literal(took ? "Used him." : "Nothing happened."), false);
			return took ? 1 : 0;
		}
		source.sendError(Text.literal("Novak is not within sixteen blocks."));
		return 0;
	}

	/**
	 * A span kit against the nearest anchor, as using one would. Here for the same reason {@code lift} is:
	 * a fake player's actions never reach an item's use, so this is the only way a headless run can see the
	 * kit's own state machine work rather than the command's shortcut.
	 */
	private static int kit(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ServerWorld world = player.getServerWorld();
		BlockPos at = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterateOutwards(at, 8, 6, 8)) {
			if (!world.getBlockState(pos).isOf(ModBlocks.SPAN_ANCHOR)) continue;
			boolean took = SpanKitItem.use(player, pos.toImmutable());
			source.sendFeedback(() -> Text.literal(took ? "Used the kit on the anchor at " + pos.toShortString()
					: "The anchor at " + pos.toShortString() + " had nothing for it."), false);
			return took ? 1 : 0;
		}
		source.sendError(Text.literal("No span anchor within eight blocks."));
		return 0;
	}

	private static int reset(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		RescueState rescue = RescueState.get(server);
		// The scene has its own field and its own clock, so clearing the stage does not stop it: a call left
		// running through a reset finishes a moment later, re-arms the act and hands over a second kit.
		Conference.stop();
		Director.clearStage();
		rescue.stage = RescueState.STAGE_NONE;
		rescue.called = false;
		rescue.callDay = -1L;
		rescue.anchor = null;
		rescue.spanStepX = 0;
		rescue.spanStepZ = 0;
		rescue.spanLength = 0;
		rescue.spanCourses = 0;
		rescue.spanDone = false;
		rescue.airlock = false;
		rescue.novakLifted = false;
		rescue.novakRefused = false;
		rescue.crossingSearched = false;
		rescue.markDirty();
		Rescue.forget();
		source.sendFeedback(() -> Text.literal("Rescue forgotten. What is built stays built."), true);
		return 1;
	}

	// ------------------------------------------------------------------ getting there

	private static int teleport(ServerCommandSource source, String where) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		MinecraftServer server = source.getServer();
		ServerWorld world = server.getOverworld();
		BlockPos target = place(server, where);
		if (target == null) {
			source.sendError(Text.literal("Nowhere called " + where + ". Try: " + String.join(", ", PLACES)));
			return 0;
		}
		load(world, target, 3);
		int y = where.equals("novak") ? target.getY() + 1
				: world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, target.getX(), target.getZ());
		player.teleport(world, target.getX() + 0.5, y, target.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
		source.sendFeedback(() -> Text.literal("At " + where + ": " + target.toShortString()), false);
		return 1;
	}

	@Nullable
	private static BlockPos place(MinecraftServer server, String where) {
		RescueState rescue = RescueState.get(server);
		SurvivorManager survivors = SurvivorManager.get(server);
		return switch (where) {
			case "hub" -> HabitatState.get(server).origin;
			case "crossing" -> Rescue.crossing(server);
			case "flow" -> rescue.flow;
			case "brandt" -> siteOf(survivors, Survivor.BRANDT);
			case "reyes" -> siteOf(survivors, Survivor.REYES);
			case "novak" -> siteOf(survivors, Survivor.NOVAK);
			default -> null;
		};
	}

	@Nullable
	private static BlockPos siteOf(SurvivorManager survivors, Survivor who) {
		SurvivorManager.Site site = Rescue.site(survivors, who);
		return site == null ? null : site.origin();
	}

	// ------------------------------------------------------------------ odds and ends

	/** The act needs a finished contract behind it; a dev jump provides one rather than refusing. */
	private static void ready(MinecraftServer server) {
		AssayState assay = AssayState.get(server);
		if (assay.stage < AssayState.STAGE_DONE) {
			assay.stage = AssayState.STAGE_DONE;
			assay.markDirty();
		}
	}

	private static void load(ServerWorld world, BlockPos at, int radius) {
		for (int cx = -radius; cx <= radius; cx++) {
			for (int cz = -radius; cz <= radius; cz++) {
				world.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
			}
		}
		SurvivorManager.tick(world.getServer());
	}
}
