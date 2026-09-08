package dev.psyda.surrogate.errand;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.psyda.surrogate.fauna.Specimen;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code /surrogate errand} — the optional work, on demand.
 *
 * <p>Every one of these errands is gated behind something that takes an hour of play to reach, which makes
 * them nearly impossible to iterate on. So: {@code start} opens one right now and does everything the sweep
 * would have done for it, {@code tp} puts you where it happens, and {@code done} marks it finished so what
 * comes after it can be looked at too.
 *
 * <p>{@code /surrogate fauna} is the same idea for the animals: spawn one in front of you, or go and find
 * the nearest one, because two of the four are placed by a sweep that will not run where you are standing.
 */
public final class ErrandCommand {
	private static final SuggestionProvider<ServerCommandSource> ERRANDS = (context, builder) -> {
		for (Errand errand : Errand.all()) builder.suggest(errand.key());
		return builder.buildFuture();
	};

	private static final SuggestionProvider<ServerCommandSource> SPECIES = (context, builder) -> {
		for (String name : new String[]{"trundle", "slagback", "tocker", "lantern_slug"}) builder.suggest(name);
		return builder.buildFuture();
	};

	private ErrandCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> errandCommand() {
		return CommandManager.literal("errand")
				.then(CommandManager.literal("list").executes(context -> list(context.getSource())))
				.then(CommandManager.literal("status").executes(context -> status(context.getSource())))
				.then(CommandManager.literal("start")
						.then(CommandManager.argument("errand", StringArgumentType.word()).suggests(ERRANDS)
								.executes(context -> start(context.getSource(), name(context)))))
				.then(CommandManager.literal("tp")
						.then(CommandManager.argument("errand", StringArgumentType.word()).suggests(ERRANDS)
								.executes(context -> teleport(context.getSource(), name(context)))))
				.then(CommandManager.literal("lift").executes(context -> lift(context.getSource())))
				.then(CommandManager.literal("done")
						.then(CommandManager.argument("errand", StringArgumentType.word()).suggests(ERRANDS)
								.executes(context -> done(context.getSource(), name(context)))))
				.then(CommandManager.literal("reset")
						.executes(context -> reset(context.getSource(), null))
						.then(CommandManager.argument("errand", StringArgumentType.word()).suggests(ERRANDS)
								.executes(context -> reset(context.getSource(), name(context)))));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> faunaCommand() {
		return CommandManager.literal("fauna")
				.then(CommandManager.literal("spawn")
						.then(CommandManager.argument("species", StringArgumentType.word()).suggests(SPECIES)
								.executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "species"), 1))
								.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 16))
										.executes(context -> spawn(context.getSource(),
												StringArgumentType.getString(context, "species"),
												IntegerArgumentType.getInteger(context, "count"))))))
				.then(CommandManager.literal("read")
						.executes(context -> readAll(context.getSource())));
	}

	private static String name(CommandContext<ServerCommandSource> context) {
		return StringArgumentType.getString(context, "errand");
	}

	// ------------------------------------------------------------------ the errands

	private static int list(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ErrandState errands = ErrandState.get(server);
		source.sendFeedback(() -> Text.literal("Errands: " + errands.doneCount() + " of " + Errand.all().length + " done")
				.formatted(Formatting.BOLD), false);
		for (Errand errand : Errand.all()) {
			String state = errands.done(errand) ? "done" : errands.offered(errand) ? "open" : "locked";
			Formatting colour = errands.done(errand) ? Formatting.GREEN
					: errands.offered(errand) ? Formatting.YELLOW : Formatting.DARK_GRAY;
			BlockPos at = Errands.locate(server, errand);
			String where = at == null ? "nowhere yet" : at.toShortString();
			source.sendFeedback(() -> Text.literal(String.format("  %-14s %-6s %-6s %s",
					errand.key(), errand.phase().name().toLowerCase(Locale.ROOT), state, where)).formatted(colour), false);
		}
		return Errand.all().length;
	}

	private static int status(ServerCommandSource source) {
		ErrandState errands = ErrandState.get(source.getServer());
		source.sendFeedback(() -> Text.literal("Survey: " + errands.readCount() + " of " + Specimen.all().length
				+ " read, disk " + (errands.disk ? "in" : "out")), false);
		source.sendFeedback(() -> Text.literal("Ark: " + errands.cagedCount() + " of " + Specimen.liveCount() + " crated"), false);
		source.sendFeedback(() -> Text.literal("Machines: " + errands.machinesFixed + " of " + Errands.MACHINES), false);
		StringBuilder missing = new StringBuilder();
		for (Specimen specimen : Specimen.all()) {
			if (errands.hasRead(specimen)) continue;
			if (missing.length() > 0) missing.append(", ");
			missing.append(specimen.key());
		}
		String unread = missing.length() == 0 ? "none" : missing.toString();
		source.sendFeedback(() -> Text.literal("Unread: " + unread).formatted(Formatting.GRAY), false);
		return errands.readCount();
	}

	private static int start(ServerCommandSource source, String key) {
		Errand errand = Errand.byKey(key);
		if (errand == null) {
			source.sendError(Text.literal("Errands: " + Errand.keys()));
			return 0;
		}
		MinecraftServer server = source.getServer();
		HabitatState habitat = HabitatState.get(server);
		if (habitat.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		ErrandState errands = ErrandState.get(server);
		// Opening one that is already open still re-runs its setup, because that is usually what a second
		// start is for: the body got destroyed, the vent got mined out, the cat came home too early.
		errands.done &= ~errand.bit();
		errands.offered &= ~errand.bit();
		errands.markDirty();
		if (errand == Errand.HOUSEWARMING) {
			Housewarming.force(server);
			source.sendFeedback(() -> Text.literal("Housewarming: running now."), true);
			return 1;
		}
		Errands.open(server, errands, habitat, errand);
		BlockPos at = Errands.locate(server, errand);
		source.sendFeedback(() -> Text.literal("Started " + errand.key()
				+ (at == null ? "" : " at " + at.toShortString())), true);
		return 1;
	}

	/**
	 * Picks up the burial's body, or puts it down again. The errand's own way in is a right-click on an
	 * armour stand while driving a chassis, which is two things a headless test cannot do and a nuisance
	 * to set up by hand; the interesting half of that errand is everything after the lift.
	 */
	private static int lift(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Run this as a player."));
			return 0;
		}
		ErrandState errands = ErrandState.get(source.getServer());
		ArmorStandEntity stand = Errands.body(source.getServer(), errands);
		if (stand == null) {
			source.sendError(Text.literal("No body placed. Try: surrogate errand start burial"));
			return 0;
		}
		boolean carried = !stand.hasVehicle();
		if (!Errands.lift(player, errands, stand)) {
			source.sendError(Text.literal("The burial is not open, or is already done."));
			return 0;
		}
		source.sendFeedback(() -> Text.literal(carried ? "Lifted the body." : "Put the body down."), true);
		return 1;
	}

	private static int teleport(ServerCommandSource source, String key) {
		Errand errand = Errand.byKey(key);
		if (errand == null) {
			source.sendError(Text.literal("Errands: " + Errand.keys()));
			return 0;
		}
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only a player can be teleported."));
			return 0;
		}
		BlockPos at = Errands.locate(source.getServer(), errand);
		if (at == null) {
			source.sendError(Text.literal("Nothing placed for " + errand.key() + " yet. Try: /surrogate errand start " + errand.key()));
			return 0;
		}
		// Land on the surface rather than inside whatever is there: several of these spots are the floor of
		// a shelter, and a teleport into a floor is a teleport into rock.
		net.minecraft.server.world.ServerWorld world = source.getWorld();
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.add(2, 0, 2));
		// Whatever the player is driving comes too, or a chassis pilot is teleported out of their own body.
		net.minecraft.entity.Entity ride = player.getVehicle() != null ? player.getVehicle() : player;
		ride.requestTeleport(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
		source.sendFeedback(() -> Text.literal("Teleported to " + errand.key() + " at " + top.toShortString()), false);
		return 1;
	}

	private static int done(ServerCommandSource source, String key) {
		Errand errand = Errand.byKey(key);
		if (errand == null) {
			source.sendError(Text.literal("Errands: " + Errand.keys()));
			return 0;
		}
		MinecraftServer server = source.getServer();
		ErrandState errands = ErrandState.get(server);
		// The survey and the ark are counts rather than flags, so marking them done has to fill the count or
		// the terminal page and the rocket's crates would disagree with the errand list.
		if (errand == Errand.SURVEY) {
			for (Specimen specimen : Specimen.all()) errands.fileReading(specimen);
			errands.disk = true;
		}
		if (errand == Errand.ARK) {
			for (Specimen specimen : Specimen.all()) {
				if (specimen.live()) errands.fileCage(specimen);
			}
		}
		if (errand == Errand.CORRODED) errands.machinesFixed = Errands.MACHINES;
		Errands.finish(server, errands, errand);
		source.sendFeedback(() -> Text.literal("Marked " + errand.key() + " done."), true);
		return 1;
	}

	private static int reset(ServerCommandSource source, @Nullable String key) {
		ErrandState errands = ErrandState.get(source.getServer());
		if (key == null) {
			errands.offered = 0;
			errands.done = 0;
			errands.read = 0;
			errands.caged = 0;
			errands.disk = false;
			errands.tiredDay = -1L;
			errands.machinesFixed = 0;
			errands.bodyPos = null;
			errands.bodyId = null;
			errands.gravePos = null;
			errands.markDirty();
			source.sendFeedback(() -> Text.literal("All errands reset."), true);
			return Errand.all().length;
		}
		Errand errand = Errand.byKey(key);
		if (errand == null) {
			source.sendError(Text.literal("Errands: " + Errand.keys()));
			return 0;
		}
		errands.offered &= ~errand.bit();
		errands.done &= ~errand.bit();
		if (errand == Errand.HOUSEWARMING) errands.tiredDay = -1L;
		if (errand == Errand.CORRODED) errands.machinesFixed = 0;
		if (errand == Errand.SURVEY) {
			errands.read = 0;
			errands.disk = false;
		}
		if (errand == Errand.ARK) errands.caged = 0;
		errands.markDirty();
		source.sendFeedback(() -> Text.literal("Reset " + errand.key() + "."), true);
		return 1;
	}

	// ------------------------------------------------------------------ the animals

	private static int spawn(ServerCommandSource source, String species, int count) {
		EntityType<? extends MobEntity> type = switch (species) {
			case "trundle" -> ModEntities.TRUNDLE;
			case "slagback" -> ModEntities.SLAGBACK;
			case "tocker" -> ModEntities.TOCKER;
			case "lantern_slug" -> ModEntities.LANTERN_SLUG;
			default -> null;
		};
		if (type == null) {
			source.sendError(Text.literal("Species: trundle, slagback, tocker, lantern_slug"));
			return 0;
		}
		net.minecraft.server.world.ServerWorld world = source.getWorld();
		BlockPos at = BlockPos.ofFloored(source.getPosition());
		int made = 0;
		for (int i = 0; i < count; i++) {
			MobEntity mob = type.create(world);
			if (mob == null) continue;
			BlockPos spot = at.add(world.random.nextInt(7) - 3, 0, world.random.nextInt(7) - 3);
			// A slug hangs off a ceiling, and one with no ceiling over it lets go and dies within a second —
			// which is correct behaviour and makes a slug dropped on open ground useless to look at. So find
			// the roof first, and say so rather than spawning one to die if there is not one.
			if (type == ModEntities.LANTERN_SLUG) {
				BlockPos roof = ceilingAbove(world, spot);
				// The spot is scattered three blocks either way, so one of a batch can easily land under open
				// sky while the rest are under a roof. Skip that one and try the next; giving up on the whole
				// batch made a working command look broken whenever the dice went that way.
				if (roof == null) continue;
				mob.setNoGravity(true);
				mob.refreshPositionAndAngles(roof.getX() + 0.5, roof.getY() + 0.55, roof.getZ() + 0.5,
						world.random.nextFloat() * 360f, 0f);
			} else {
				mob.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
						world.random.nextFloat() * 360f, 0f);
			}
			if (world.spawnEntity(mob)) made++;
		}
		int spawned = made;
		if (spawned == 0 && type == ModEntities.LANTERN_SLUG) {
			source.sendError(Text.literal("No ceiling within 12 blocks. Stand in a cave, or build one."));
			return 0;
		}
		source.sendFeedback(() -> Text.literal("Spawned " + spawned + " " + species + " at " + at.toShortString()), false);
		return spawned;
	}

	/**
	 * The highest air block under a solid one, within twelve blocks up. Null when there is nothing but sky,
	 * which for a lantern slug is the same as nowhere to be.
	 */
	@Nullable
	private static BlockPos ceilingAbove(net.minecraft.server.world.ServerWorld world, BlockPos from) {
		BlockPos probe = from;
		for (int up = 0; up < 12; up++) {
			BlockPos above = probe.up();
			if (world.getBlockState(above).isSolidBlock(world, above)) return world.isAir(probe) ? probe : null;
			probe = above;
		}
		return null;
	}

	/** Files a reading of everything, for looking at the terminal page without doing the survey. */
	private static int readAll(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			source.sendError(Text.literal("Only a player can file a reading."));
			return 0;
		}
		ErrandState errands = ErrandState.get(source.getServer());
		int filed = 0;
		for (Specimen specimen : Specimen.all()) {
			if (errands.fileReading(specimen)) filed++;
		}
		errands.disk = true;
		errands.markDirty();
		int count = filed;
		source.sendFeedback(() -> Text.literal("Filed " + count + " readings; disk installed."), true);
		return filed;
	}
}
