package dev.psyda.surrogate.entity;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.block.DockDoorBlock;
import dev.psyda.surrogate.crawler.CrawlerDimension;
import dev.psyda.surrogate.crawler.CrawlerDocking;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.crawler.CrawlerInteriors;
import dev.psyda.surrogate.crawler.CrawlerRoom;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.survivor.SurvivorShelter;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Direction;
import dev.psyda.surrogate.registry.ModEntities;
import dev.psyda.surrogate.world.HabitatBuilder;
import dev.psyda.surrogate.world.HabitatState;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * {@code /surrogate crawler spawn|dock|undock|charge|board|leave|seat|stand|dockat}: a hull on the starter
 * pod's apron, coupled to the collar or not, for testing; and {@code dockat home|<shelter>} moves an
 * existing hull straight onto a collar and couples it there, which is how the rescue leg is tested without
 * the drive.
 */
public final class CrawlerCommand {
	private static final double APRON_X = HabitatBuilder.DOCK_COLLAR.getX() - 8.5;

	private CrawlerCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> command() {
		return CommandManager.literal("crawler")
				.then(CommandManager.literal("spawn").executes(context -> spawn(context.getSource(), false)))
				.then(CommandManager.literal("dock").executes(context -> spawn(context.getSource(), true)))
				.then(CommandManager.literal("undock").executes(context -> undock(context.getSource())))
				.then(CommandManager.literal("charge").executes(context -> charge(context.getSource())))
				.then(CommandManager.literal("board").executes(context -> board(context.getSource())))
				.then(CommandManager.literal("leave").executes(context -> leave(context.getSource())))
				.then(CommandManager.literal("seat")
						.then(CommandManager.literal("helm").executes(context -> seat(context.getSource(), CrawlerInterior.SEAT_HELM)))
						.then(CommandManager.literal("dock").executes(context -> seat(context.getSource(), CrawlerInterior.SEAT_DOCK))))
				.then(CommandManager.literal("stand").executes(context -> stand(context.getSource())))
				.then(CommandManager.literal("lock").executes(context -> {
					CrawlerInterior.lockRequested(context.getSource().getPlayerOrThrow());
					return 1;
				}))
				.then(CommandManager.literal("dockat")
						.then(CommandManager.literal("home").executes(context -> dockAt(context.getSource(), -1)))
						.then(CommandManager.argument("shelter", IntegerArgumentType.integer(0))
								.executes(context -> dockAt(context.getSource(), IntegerArgumentType.getInteger(context, "shelter")))));
	}

	/** The nearest hull within sixteen blocks takes the player aboard; from the pod, the coupled one. */
	private static int board(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ServerWorld world = player.getServerWorld();
		CrawlerEntity nearest = null;
		double best = 32.0 * 32.0;
		for (CrawlerEntity hull : world.getEntitiesByClass(CrawlerEntity.class, player.getBoundingBox().expand(32.0), hull -> true)) {
			double d = hull.squaredDistanceTo(player);
			if (d < best) {
				best = d;
				nearest = hull;
			}
		}
		if (nearest == null) {
			source.sendError(Text.literal("No crawler within thirty-two blocks."));
			return 0;
		}
		return CrawlerInterior.board(player, nearest) ? 1 : 0;
	}

	private static int leave(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!CrawlerDimension.isCabin(player.getWorld())) {
			source.sendError(Text.literal("Not aboard a crawler."));
			return 0;
		}
		BlockPos origin = CrawlerInteriors.origin(CrawlerInteriors.indexAt(player.getBlockPos()));
		return CrawlerInterior.leave(player, origin.add(CrawlerRoom.HATCH)) ? 1 : 0;
	}

	private static int seat(ServerCommandSource source, int kind) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		if (!CrawlerDimension.isCabin(player.getWorld())) {
			source.sendError(Text.literal("Not aboard a crawler."));
			return 0;
		}
		BlockPos origin = CrawlerInteriors.origin(CrawlerInteriors.indexAt(player.getBlockPos()));
		if (kind == CrawlerInterior.SEAT_HELM) CrawlerInterior.sitAt(player, origin.add(CrawlerRoom.HELM), Direction.SOUTH, kind);
		else CrawlerInterior.sitAt(player, origin.add(CrawlerRoom.DOCK_CONSOLE), Direction.NORTH, kind);
		return 1;
	}

	private static int stand(ServerCommandSource source) throws CommandSyntaxException {
		CrawlerInterior.standUp(source.getPlayerOrThrow());
		return 1;
	}

	private static List<CrawlerEntity> crawlersNear(ServerWorld world, BlockPos origin) {
		return world.getEntitiesByClass(CrawlerEntity.class, new Box(origin).expand(48.0), crawler -> true);
	}

	private static int spawn(ServerCommandSource source, boolean docked) {
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		ServerWorld world = source.getServer().getOverworld();
		BlockPos origin = state.origin;
		for (CrawlerEntity old : crawlersNear(world, origin)) old.discard();
		BlockPos collar = origin.add(HabitatBuilder.DOCK_COLLAR);
		double y = origin.getY() + 1.0;
		Vec3d at = docked ? CrawlerDocking.dockedPose(collar, y) : new Vec3d(origin.getX() + APRON_X + 0.5, y, collar.getZ() + 0.5);
		double x = at.x;
		double z = at.z;
		CrawlerEntity crawler = new CrawlerEntity(ModEntities.CRAWLER, world);
		// Facing west, so the ring on its back faces the collar in the pod's west wall.
		crawler.refreshPositionAndAngles(x, y, z, 90f, 0f);
		crawler.setEnergy(Surrogate.CONFIG.crawlerEnergyCapacity);
		crawler.setDocked(docked);
		world.spawnEntity(crawler);
		DockDoorBlock.setDocked(world, collar, docked);
		String text = docked ? "Crawler coupled to the collar; the collar door opens now." : "Crawler on the apron, charged.";
		source.sendFeedback(() -> Text.literal(text), true);
		Surrogate.LOGGER.info("Crawler: {} at {}, {}, {}", docked ? "docked" : "spawned", x, origin.getY() + 1.0, z);
		return 1;
	}

	/**
	 * Moves the crawler (the one with a cabin, else any) onto a collar and couples it there: home, or shelter
	 * {@code n} once it is built. Coupling does what it does in play: a survivor boards or steps home.
	 */
	private static int dockAt(ServerCommandSource source, int shelter) {
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		ServerWorld world = source.getServer().getOverworld();
		CrawlerEntity hull = CrawlerInterior.findHull(source.getServer());
		if (hull == null) {
			source.sendError(Text.literal("No crawler loaded; holding its chunks, try again in a moment."));
			return 0;
		}
		CrawlerDocking.Collar collar;
		if (shelter < 0) {
			collar = new CrawlerDocking.Collar(state.origin.add(HabitatBuilder.DOCK_COLLAR), null, true);
		} else {
			List<SurvivorManager.Site> sites = SurvivorManager.get(source.getServer()).sites();
			if (shelter >= sites.size()) {
				source.sendError(Text.literal("No shelter " + shelter + "; there are " + sites.size() + "."));
				return 0;
			}
			SurvivorManager.Site site = sites.get(shelter);
			// Loading the chunk builds the shelter; give it a tick if it is new.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) world.getChunk((site.x >> 4) + dx, (site.z >> 4) + dz);
			}
			SurvivorManager.tick(source.getServer());
			if (!site.built) {
				source.sendError(Text.literal("Shelter " + shelter + " is not built yet; try again."));
				return 0;
			}
			collar = new CrawlerDocking.Collar(SurvivorShelter.collar(site), site, false);
		}
		if (hull.isDocked()) CrawlerDocking.uncouple(source.getServer(), hull);
		BlockPos door = collar.door();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) world.getChunk((door.getX() >> 4) + dx, (door.getZ() >> 4) + dz);
		}
		Vec3d pose = CrawlerDocking.dockedPose(door, door.getY());
		hull.refreshPositionAndAngles(pose.x, pose.y, pose.z, 90f, 0f);
		hull.setVelocity(Vec3d.ZERO);
		// Moved by hand, the hull would unload with the chunks it left; hold the ones it is in now.
		CrawlerInterior.holdHull(source.getServer(), hull);
		CrawlerDocking.couple(source.getServer(), hull, collar);
		String where = shelter < 0 ? "home" : "shelter " + shelter;
		source.sendFeedback(() -> Text.literal("Crawler coupled at " + where + " (" + door.toShortString() + ")."), true);
		return 1;
	}

	private static int undock(ServerCommandSource source) {
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		ServerWorld world = source.getServer().getOverworld();
		int count = 0;
		for (CrawlerEntity crawler : crawlersNear(world, state.origin)) {
			if (crawler.isDocked()) count++;
			crawler.setDocked(false);
		}
		DockDoorBlock.setDocked(world, state.origin.add(HabitatBuilder.DOCK_COLLAR), false);
		int found = count;
		source.sendFeedback(() -> Text.literal("Uncoupled " + found + " crawler(s); the collar door is sealed."), true);
		return 1;
	}

	private static int charge(ServerCommandSource source) {
		HabitatState state = HabitatState.get(source.getServer());
		if (state.origin == null) {
			source.sendError(Text.literal("No starter habitat in this world yet."));
			return 0;
		}
		ServerWorld world = source.getServer().getOverworld();
		int count = 0;
		for (CrawlerEntity crawler : crawlersNear(world, state.origin)) {
			crawler.setEnergy(crawler.getEnergyCapacity());
			count++;
		}
		int found = count;
		source.sendFeedback(() -> Text.literal("Charged " + found + " crawler(s)."), true);
		return 1;
	}
}
