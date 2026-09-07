package dev.psyda.surrogate.rescue;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.assay.AssayState;
import dev.psyda.surrogate.block.TerminalBlockEntity;
import dev.psyda.surrogate.crawler.CrawlerInterior;
import dev.psyda.surrogate.entity.CrawlerEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.registry.ModBlocks;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.survivor.Survivor;
import dev.psyda.surrogate.survivor.SurvivorEntity;
import dev.psyda.surrogate.survivor.SurvivorManager;
import dev.psyda.surrogate.survivor.SurvivorShelter;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.Valleys;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Acts four and five: the turn from contract to rescue, and the far side of the Rift.
 *
 * <p>Act four is one scene, {@link Conference}, and everything before and after it is here: the wait for the
 * shelters to start falling, Halloran asking the player to sit down at the hub terminal, and the mission
 * board that the call leaves behind and that stays live to the end of the game.
 *
 * <p>Act five is not a script at all. It is three obstacles and three people behind them, and the player may
 * take them in any order the world allows, so this is a sweep and a set of watchers rather than a list of
 * beats: bridge the Rift, starve the flow across the road to Ceramic Row, plate Clinic Nine's frame, and go
 * down the scree on your own legs for the one man a machine cannot reach. Nothing here asks anybody to
 * accept anything. Each piece notices when the world has changed and says so on the radio.
 */
public final class Rescue {
	/** How often the world is looked at. Everything expensive is cached off this. */
	private static final int SWEEP = 20;
	/** How near Brandt the player has to get before the vent across the road is laid down. */
	private static final int FLOW_BUILD = 260;
	/** How near the flow the player has to be for anyone to mention it. */
	private static final int FLOW_NOTICE = 48;
	/** How near a hull a carried man has to get to be aboard it. */
	private static final double DELIVER = 6.0;
	/** How wide the search for the narrows sweeps either side of the bearing to the far side. */
	private static final int CROSSING_SWEEP = 320;
	private static final int CROSSING_STEP = 16;
	private static final int CROSSING_REACH = 2800;
	/** The widest chasm the survey will call a crossing. Under a span kit's reach, with room for the anchor. */
	private static final int NOISE_SPAN = 40;

	private static int sweepIn;
	/** Whoever is being carried out of the Rift right now, and by whom. Not saved: a restart puts him down. */
	@Nullable
	private static UUID carrier;
	/** Set once per session per gate so the radio says a thing once and then lets it lie. */
	private static boolean saidCall;
	private static boolean saidFlow;

	private Rescue() {
	}

	// ------------------------------------------------------------------ lifecycle

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(Rescue::tick);
		ServerTickEvents.END_SERVER_TICK.register(RiftMist::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
		Conference.registerEvents();
		// Picking a man up off the floor of the Rift. Nothing else in the mod carries anybody.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!(entity instanceof SurvivorEntity survivor) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
			if (survivor.getCharacter() != Survivor.NOVAK) return ActionResult.PASS;
			return lift(serverPlayer, survivor) ? ActionResult.SUCCESS : ActionResult.PASS;
		});
	}

	/** Dev: forget what the radio has already said, so a replayed act says it again. */
	public static void forget() {
		saidCall = false;
		saidFlow = false;
	}

	private static void reset() {
		sweepIn = 0;
		carrier = null;
		saidCall = false;
		saidFlow = false;
		RiftMist.reset();
	}

	/** Whoever this arc is for: the protagonist if there is one, otherwise whoever is here. */
	@Nullable
	public static ServerPlayerEntity protagonist(MinecraftServer server) {
		HabitatState habitat = HabitatState.get(server);
		if (habitat.protagonist != null) {
			ServerPlayerEntity found = server.getPlayerManager().getPlayer(habitat.protagonist);
			if (found != null) return found;
		}
		return server.getPlayerManager().getPlayerList().stream().findFirst().orElse(null);
	}

	/**
	 * A player joined. The one thing act four puts on their screen is an objective banner, which lives on the
	 * client and is forgotten by a relog, and the radio line that came with it is said once a session. So it
	 * goes back up for anybody arriving into a call that is still waiting to be sat down at.
	 *
	 * <p>It never handles the join: the chain in {@code HabitatBuilder} reads a true as "this arc has the
	 * player now", and this arc never does.
	 */
	public static void onJoin(ServerPlayerEntity player) {
		if (!Surrogate.CONFIG.rescue) return;
		MinecraftServer server = player.server;
		RescueState rescue = RescueState.get(server);
		if (rescue.stage != RescueState.STAGE_CALL || rescue.called) return;
		if (server.getOverworld().getTimeOfDay() / 24000L < rescue.callDay) return;
		objective(player, "cinematic.surrogate.conference.objective.hub");
	}

	// ------------------------------------------------------------------ the sweep

	private static void tick(MinecraftServer server) {
		if (!Surrogate.CONFIG.rescue) return;
		if (--sweepIn > 0) return;
		sweepIn = SWEEP;
		ServerWorld world = server.getOverworld();
		if (!Valleys.isMesaWorld(world)) return;
		RescueState rescue = RescueState.get(server);
		SurvivorManager survivors = SurvivorManager.get(server);

		switch (rescue.stage) {
			case RescueState.STAGE_NONE -> waitForTheShip(server, world, rescue);
			case RescueState.STAGE_CALL -> waitForTheCall(server, world, rescue);
			case RescueState.STAGE_PLAN -> watchTheFarSide(server, world, rescue, survivors);
			default -> {
			}
		}
		if (carrier != null) tickCarry(server, world, rescue);
	}

	/** Act four opens on the day the ship does not come down. */
	private static void waitForTheShip(MinecraftServer server, ServerWorld world, RescueState rescue) {
		if (AssayState.get(server).stage < AssayState.STAGE_DONE) return;
		if (Director.current() != null) return;
		rescue.stage = RescueState.STAGE_CALL;
		rescue.callDay = world.getTimeOfDay() / 24000L + Math.max(0, Surrogate.CONFIG.conferenceAfterDays);
		rescue.markDirty();
		Surrogate.LOGGER.info("Rescue: the contract is closed; the call is due on day {}", rescue.callDay);
	}

	/** Halloran asks for the room, once, and then waits as long as it takes. */
	private static void waitForTheCall(MinecraftServer server, ServerWorld world, RescueState rescue) {
		if (rescue.called) {
			rescue.stage = RescueState.STAGE_PLAN;
			rescue.markDirty();
			return;
		}
		if (world.getTimeOfDay() / 24000L < rescue.callDay) return;
		if (saidCall || Director.current() != null) return;
		ServerPlayerEntity player = protagonist(server);
		if (player == null) return;
		saidCall = true;
		radio(server, "message.surrogate.rescue.summon");
		objective(player, "cinematic.surrogate.conference.objective.hub");
		Surrogate.LOGGER.info("Rescue: Halloran wants everyone on the band");
	}

	/**
	 * Act five, every second: has the bridge reached the other side, has the flow been cut, has Clinic Nine
	 * got its plates back, and is everybody home.
	 */
	private static void watchTheFarSide(MinecraftServer server, ServerWorld world, RescueState rescue, SurvivorManager survivors) {
		if (!rescue.flowCut && rescue.flow != null && MagmaFlow.wallStanding(world, rescue) <= MagmaFlow.wallBlocks() - MagmaFlow.WALL_CUT) {
			MagmaFlow.cut(world, rescue);
			radio(server, "message.surrogate.rescue.flow_cut");
		}
		SurvivorManager.Site brandt = site(survivors, Survivor.BRANDT);
		SurvivorManager.Site reyes = site(survivors, Survivor.REYES);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getServerWorld() != world) continue;
			BlockPos at = player.getBlockPos();
			// The vent goes in the first time somebody comes over the Rift and heads for Ceramic Row, so it
			// is never laid down under a player who is standing on it, and never on a world nobody visits.
			if (brandt != null && brandt.built && rescue.flow == null && at.isWithinDistance(brandt.origin(), FLOW_BUILD)) {
				HabitatState habitat = HabitatState.get(server);
				BlockPos from = habitat.origin == null ? at : habitat.origin;
				MagmaFlow.build(world, rescue, from, brandt.origin());
				rescue.markDirty();
			}
			if (!saidFlow && !rescue.flowCut && MagmaFlow.near(rescue, at, FLOW_NOTICE)) {
				saidFlow = true;
				radio(server, "message.surrogate.rescue.flow");
			}
		}
		// Clinic Nine. Her frame is checked here, on the sweep, rather than from the docking console, which
		// runs several times a tick per seated player and has no business reading blocks in another chunk.
		if (!rescue.airlock && reyes != null && reyes.built && loaded(world, reyes.origin(), 1)) {
			if (framePlated(world, reyes)) plated(server, world, rescue, reyes);
		}
		int home = 0;
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.rescued) home++;
		}
		if (!survivors.sites().isEmpty() && home >= survivors.sites().size() && rescue.stage != RescueState.STAGE_DONE) {
			rescue.stage = RescueState.STAGE_DONE;
			rescue.markDirty();
			radio(server, "message.surrogate.rescue.everyone");
			Surrogate.LOGGER.info("Rescue: everyone is off the far side");
		}
	}

	// ------------------------------------------------------------------ act four's leftovers

	/**
	 * The call is over. Sorensen's shop sends the one thing the plan needs that nobody has: a span kit, which
	 * is a bridge in a box and the only reason the far side is a drive rather than an afternoon of plating.
	 */
	public static void planAgreed(MinecraftServer server, @Nullable ServerPlayerEntity watcher) {
		ServerPlayerEntity player = watcher != null ? watcher : protagonist(server);
		if (player == null) return;
		ItemStack kit = new ItemStack(ModItems.SPAN_KIT);
		Text what = kit.getName();
		if (!player.giveItemStack(kit)) player.dropItem(kit, false);
		player.sendMessage(Text.translatable("message.surrogate.rescue.kit", what).formatted(Formatting.GOLD), false);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.9f, 1.3f);
		BlockPos crossing = crossing(server);
		if (crossing != null) {
			player.sendMessage(Text.translatable("message.surrogate.rescue.crossing", crossing.getX(), crossing.getZ()).formatted(Formatting.AQUA), false);
		}
	}

	/**
	 * A terminal was opened. The hub in the player's own pod is where the call happens and where the board
	 * lives; every other screen in the game is unaffected.
	 *
	 * @return true when the terminal was taken over and should not open its own screen
	 */
	public static boolean onTerminal(ServerPlayerEntity player, String unit) {
		if (!Surrogate.CONFIG.rescue) return false;
		if (!TerminalBlockEntity.HUB.equals(unit)) {
			// Whatever board this client is holding belongs to a hub, not to a shelter's port log.
			MissionBoard.clear(player);
			return false;
		}
		MinecraftServer server = player.server;
		RescueState rescue = RescueState.get(server);
		if (rescue.stage == RescueState.STAGE_CALL && !rescue.called
				&& server.getOverworld().getTimeOfDay() / 24000L >= rescue.callDay) {
			return Conference.begin(server, player);
		}
		if (rescue.called) MissionBoard.send(player);
		else MissionBoard.clear(player);
		return false;
	}

	// ------------------------------------------------------------------ Clinic Nine

	/** Every plate in Reyes' frame is a plate again. */
	public static boolean framePlated(ServerWorld world, SurvivorManager.Site site) {
		for (BlockPos offset : SurvivorShelter.FRAME) {
			BlockPos pos = site.origin().add(offset);
			if (!world.getBlockState(pos).isOf(ModBlocks.HULL_PLATING)) return false;
		}
		return true;
	}

	/**
	 * The lock cycles. She pays in the two things the next hour needs, and she is the gate on Novak whether
	 * or not the player has worked out why yet.
	 */
	private static void plated(MinecraftServer server, ServerWorld world, RescueState rescue, SurvivorManager.Site site) {
		rescue.airlock = true;
		rescue.markDirty();
		// Whoever is standing at the frame they just plated, and only then whoever the arc is nominally for.
		ServerPlayerEntity player = world.getClosestPlayer(site.x, site.y, site.z, 48.0, false) instanceof ServerPlayerEntity near
				? near : protagonist(server);
		for (SurvivorEntity entity : world.getEntitiesByClass(SurvivorEntity.class, new Box(site.origin()).expand(16.0),
				e -> e.getCharacter() == Survivor.REYES)) {
			entity.setRescued(true);
		}
		if (player != null) {
			ItemStack reward = Survivor.REYES.reward();
			Text what = reward.getName();
			if (!player.giveItemStack(reward)) player.dropItem(reward, false);
			player.sendMessage(say(Survivor.REYES, Survivor.REYES.line("thanks")), false);
			player.sendMessage(Text.translatable("message.surrogate.survivor.reward", Survivor.REYES.displayName(), what)
					.formatted(Formatting.GOLD), false);
		}
		SurvivorManager.get(server).broadcast(server, Survivor.REYES, Survivor.REYES.line("rescued_radio"));
		world.playSound(null, site.origin(), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 0.8f);
		Surrogate.LOGGER.info("Rescue: Clinic Nine's frame is plated and the lock cycles");
	}

	// ------------------------------------------------------------------ boarding

	/**
	 * Why this shelter's occupant will not come aboard a hull on their collar, as a translation key, or null
	 * when they will. Read from the docking path, so it has to be cheap: everything it wants has already
	 * been decided by the sweep.
	 */
	@Nullable
	public static String boardRefusal(MinecraftServer server, SurvivorManager.Site site) {
		if (!Surrogate.CONFIG.rescue) return null;
		if (site.survivor() != Survivor.REYES) return null;
		return RescueState.get(server).airlock ? null : "message.surrogate.rescue.reyes_sealed";
	}

	// ------------------------------------------------------------------ Novak

	/**
	 * Picking him up. A chassis has no hands for a broken leg and no air to spend, and Halloran will not have
	 * anybody going down the scree with nowhere to put him at the top, so both of those are refusals with a
	 * voice on them rather than a prompt that quietly does not appear.
	 */
	public static boolean lift(ServerPlayerEntity player, SurvivorEntity novak) {
		if (!Surrogate.CONFIG.rescue) return false;
		MinecraftServer server = player.server;
		SurvivorManager survivors = SurvivorManager.get(server);
		SurvivorManager.Site site = site(survivors, Survivor.NOVAK);
		if (site == null || site.rescued || site.aboard) return false;
		if (novak.getVehicle() == player) {
			// Using him again puts him down. Without it a player who lifts him and cannot get back to a hull
			// carries him and half their speed for the rest of the game.
			novak.stopRiding();
			carrier = null;
			player.sendMessage(Text.translatable("message.surrogate.rescue.set_down").formatted(Formatting.GRAY), true);
			return true;
		}
		if (novak.hasVehicle() || carrier != null) return false;
		if (player.getVehicle() instanceof RobotEntity) {
			// He says this himself, in his own words, in SurvivorEntity. Nothing to add.
			return false;
		}
		RescueState rescue = RescueState.get(server);
		SurvivorManager.Site reyes = site(survivors, Survivor.REYES);
		boolean doctorAboard = reyes != null && (reyes.aboard || reyes.rescued);
		if (!doctorAboard) {
			player.sendMessage(radioLine(Text.translatable(rescue.novakRefused
					? "message.surrogate.rescue.novak_refused_again"
					: "message.surrogate.rescue.novak_refused")), false);
			rescue.novakRefused = true;
			rescue.markDirty();
			return true;
		}
		novak.startRiding(player, true);
		carrier = player.getUuid();
		rescue.novakLifted = true;
		rescue.markDirty();
		player.sendMessage(say(Survivor.NOVAK, Survivor.NOVAK.line("greet")), false);
		player.sendMessage(Text.translatable("message.surrogate.rescue.carrying").formatted(Formatting.GOLD), true);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.9f, 0.7f);
		Surrogate.LOGGER.info("Rescue: {} has Novak off the floor", player.getName().getString());
		return true;
	}

	/** Carrying a person is half your speed and it does not care how long the air lasts. */
	private static void tickCarry(MinecraftServer server, ServerWorld world, RescueState rescue) {
		ServerPlayerEntity player = server.getPlayerManager().getPlayer(carrier);
		if (player == null) {
			carrier = null;
			return;
		}
		SurvivorEntity novak = null;
		for (net.minecraft.entity.Entity passenger : player.getPassengerList()) {
			if (passenger instanceof SurvivorEntity survivor && survivor.getCharacter() == Survivor.NOVAK) novak = survivor;
		}
		if (novak == null) {
			carrier = null;
			return;
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SWEEP * 3, 2, true, false, false));
		CrawlerEntity hull = nearestHull(world, player);
		if (hull != null) {
			SurvivorManager survivors = SurvivorManager.get(server);
			SurvivorManager.Site site = site(survivors, Survivor.NOVAK);
			if (site != null) {
				novak.stopRiding();
				survivors.boardCarried(server, hull, site, novak);
				carrier = null;
				rescue.markDirty();
				Surrogate.LOGGER.info("Rescue: Novak is aboard");
			}
		}
	}

	@Nullable
	private static CrawlerEntity nearestHull(ServerWorld world, ServerPlayerEntity player) {
		for (CrawlerEntity hull : world.getEntitiesByClass(CrawlerEntity.class, player.getBoundingBox().expand(DELIVER), CrawlerEntity::isAlive)) {
			if (CrawlerInterior.hasCabin(player.server, hull)) return hull;
		}
		return null;
	}

	/** Whether somebody is being carried right now, for the board and the dev command. */
	public static boolean carrying() {
		return carrier != null;
	}

	// ------------------------------------------------------------------ the narrows

	/**
	 * The narrowest place the two sides of the Rift come together on the way to somebody, worked out once and
	 * then remembered. It is not a gate on anything: the player may cross the chasm wherever they like, or
	 * not at all. It is a coordinate on the radio, so that the first thing act five asks for is somewhere to
	 * go.
	 *
	 * <p>It is searched towards all three of the far people rather than towards one of them, because they are
	 * not all in the same place. The world does not put a far side somewhere; it gives each of them a sector
	 * and a piece of floor in it that the pod cannot reach, and on a real map those sectors are a hundred and
	 * twenty degrees apart. A search aimed only at Brandt looks south-west, finds no chasm at all because
	 * what is in the way over there is a table, and reports that the world has no far side while a man sits
	 * at the bottom of one.
	 */
	@Nullable
	public static BlockPos crossing(MinecraftServer server) {
		RescueState rescue = RescueState.get(server);
		if (rescue.crossing != null) return rescue.crossing;
		// A world where the chasm is not on the way to anybody answers null, and the search that says so
		// costs seventy thousand noise samples. Ask once.
		if (rescue.crossingSearched) return null;
		ServerWorld world = server.getOverworld();
		HabitatState habitat = HabitatState.get(server);
		if (habitat.origin == null) return null;
		Valleys.Masks masks = Valleys.masks(world);
		if (masks == null) return null;
		Narrows best = null;
		int tried = 0;
		for (SurvivorManager.Site site : SurvivorManager.get(server).sites()) {
			if (!site.gated()) continue;
			tried++;
			Narrows found = findCrossing(masks, habitat.origin, new BlockPos(site.x, habitat.origin.getY(), site.z));
			if (found != null && (best == null || found.span < best.span)) best = found;
		}
		rescue.crossingSearched = true;
		rescue.markDirty();
		if (best == null) {
			Surrogate.LOGGER.info("Rescue: no narrows found on any of {} bearings; the chasm is not on the way to anybody", tried);
			return null;
		}
		rescue.crossing = best.shore;
		rescue.crossingStepX = best.stepX;
		rescue.crossingStepZ = best.stepZ;
		rescue.markDirty();
		Surrogate.LOGGER.info("Rescue: the narrows are at {}, {} blocks across", best.shore.toShortString(), best.span);
		return best.shore;
	}

	/** Which way a span off the narrows has to point. Falls back to east on a world that never found any. */
	public static Direction crossingFacing(MinecraftServer server) {
		RescueState rescue = RescueState.get(server);
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (direction.getOffsetX() == rescue.crossingStepX && direction.getOffsetZ() == rescue.crossingStepZ) return direction;
		}
		return Direction.EAST;
	}

	/** One candidate crossing: the near lip, how wide the chasm is there, and which way across is. */
	private record Narrows(BlockPos shore, int span, int stepX, int stepZ) {
	}

	@Nullable
	private static Narrows findCrossing(Valleys.Masks masks, BlockPos from, BlockPos toward) {
		double dx = toward.getX() - from.getX();
		double dz = toward.getZ() - from.getZ();
		double length = Math.hypot(dx, dz);
		if (length < 1.0) return null;
		double ux = dx / length;
		double uz = dz / length;
		// Perpendicular, for sweeping parallel lines either side of the bearing.
		double px = -uz;
		double pz = ux;
		// No further than the person being aimed at, and a bit. A ray that runs on for another kilometre
		// finds chasms that are behind them and no use to anybody.
		int reach = (int) Math.min(CROSSING_REACH, length * 1.25);
		Narrows best = null;
		for (int off = -CROSSING_SWEEP; off <= CROSSING_SWEEP; off += CROSSING_STEP) {
			int shoreT = -1;
			for (int t = 64; t <= reach; t += 4) {
				int x = (int) Math.round(from.getX() + ux * t + px * off);
				int z = (int) Math.round(from.getZ() + uz * t + pz * off);
				boolean rift = Valleys.inRift(masks, x, z);
				if (rift && shoreT < 0) {
					shoreT = t;
				} else if (!rift && shoreT >= 0) {
					int sx = (int) Math.round(from.getX() + ux * (shoreT - 4) + px * off);
					int sz = (int) Math.round(from.getZ() + uz * (shoreT - 4) + pz * off);
					// Noise only, and the pod's own level as a placeholder: whoever plants something here
					// resolves the real ground once the chunk is loaded, which the search never does.
					Narrows here = squareOn(masks, new BlockPos(sx, from.getY(), sz));
					if (here != null && (best == null || here.span < best.span)) best = here;
					break;
				}
			}
		}
		return best;
	}

	/**
	 * The narrowest of the four cardinals from this shore, or null when none of them is a crossing.
	 *
	 * <p>The bearing that found the shore is the bearing to somebody's shelter, and the chasm does not run
	 * square to it: a ray that clips a corner of a twenty block chasm at forty degrees reads it as sixty and
	 * a deck laid on that heading grazes along the lip instead of going over. A deck can only be laid on a
	 * cardinal anyway, so the survey squares up before it reports a width.
	 */
	@Nullable
	private static Narrows squareOn(Valleys.Masks masks, BlockPos shore) {
		// Both lips have to be valley floor. The chasm is cut through everything the mask covers, sea
		// included, and the noise is perfectly happy to report a twenty block crossing in the middle of a
		// lake: flat water at sea level for fifty blocks in each direction, no chasm to be seen, and an
		// anchor with nothing in front of it. Floor, not cliff, not table, not water.
		if (Valleys.classify(masks, shore.getX(), shore.getZ()) != Valleys.Kind.FLOOR) return null;
		Narrows best = null;
		for (Direction facing : Direction.Type.HORIZONTAL) {
			int entered = -1;
			for (int d = 2; d <= NOISE_SPAN; d += 2) {
				int x = shore.getX() + facing.getOffsetX() * d;
				int z = shore.getZ() + facing.getOffsetZ() * d;
				boolean rift = Valleys.inRift(masks, x, z);
				if (rift && entered < 0) {
					// The chasm has to be in front of you rather than a walk away, or the anchor goes down
					// somewhere with a perfectly good crossing thirty blocks to its left.
					if (d > 12) break;
					entered = d;
				} else if (!rift && entered >= 0) {
					// Somewhere to land, and somewhere worth landing: floor here and floor a bridge's length
					// beyond it, or the deck ends on a ledge with the chasm carrying on behind it.
					if (Valleys.classify(masks, x, z) != Valleys.Kind.FLOOR) break;
					int beyond = d + 24;
					if (Valleys.classify(masks, shore.getX() + facing.getOffsetX() * beyond,
							shore.getZ() + facing.getOffsetZ() * beyond) == Valleys.Kind.RIFT) break;
					int span = d - entered;
					// A two-block reading is the sampling step, not a chasm. Anything under it is a seam.
					if (span >= 8 && (best == null || span < best.span)) {
						best = new Narrows(shore, span, facing.getOffsetX(), facing.getOffsetZ());
					}
					break;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ odds and ends

	/**
	 * Whether the chunks {@code radius} around here are already in the world.
	 *
	 * <p>Everything this act watches is a long way from wherever the player is standing, and the sweep runs
	 * every second. A getBlockState on a chunk that is not there does not answer air: it generates the chunk,
	 * on the server thread, in the tick that asked.
	 */
	public static boolean loaded(ServerWorld world, BlockPos at, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!world.isChunkLoaded((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz)) return false;
			}
		}
		return true;
	}

	@Nullable
	public static SurvivorManager.Site site(SurvivorManager survivors, Survivor who) {
		for (SurvivorManager.Site site : survivors.sites()) {
			if (site.survivor() == who) return site;
		}
		return null;
	}

	/** Halloran, on the band, to everybody on Sallow. */
	private static void radio(MinecraftServer server, String key) {
		Text line = radioLine(Text.translatable(key));
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) player.sendMessage(line, false);
	}

	private static MutableText radioLine(Text body) {
		return Text.literal("[RADIO] ").formatted(Formatting.DARK_AQUA)
				.append(Crew.HALLORAN.displayName().copy().formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(body.copy().formatted(Formatting.WHITE));
	}

	private static MutableText say(Survivor who, Text line) {
		return Text.empty().append(who.displayName().copy().formatted(Formatting.GOLD)).append(": ").append(line);
	}

	private static void objective(ServerPlayerEntity player, String key) {
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
				new CinematicPayloads.Objective(key, CinematicPayloads.OBJECTIVE_SHOW));
	}
}
