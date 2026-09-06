package dev.psyda.surrogate.transit;

import dev.psyda.surrogate.Surrogate;
import dev.psyda.surrogate.atmosphere.Atmosphere;
import dev.psyda.surrogate.block.DiveChairBlockEntity;
import dev.psyda.surrogate.block.MicrowaveBlockEntity;
import dev.psyda.surrogate.entity.RobotEntity;
import dev.psyda.surrogate.entity.RobotState;
import dev.psyda.surrogate.network.CinematicPayloads;
import dev.psyda.surrogate.network.TransitPayload;
import dev.psyda.surrogate.pilot.PilotManager;
import dev.psyda.surrogate.prologue.Beat;
import dev.psyda.surrogate.prologue.Crew;
import dev.psyda.surrogate.prologue.CrewEntity;
import dev.psyda.surrogate.prologue.Director;
import dev.psyda.surrogate.prologue.Prologue;
import dev.psyda.surrogate.registry.ModEffects;
import dev.psyda.surrogate.registry.ModItems;
import dev.psyda.surrogate.registry.ModSounds;
import dev.psyda.surrogate.world.HabitatState;
import dev.psyda.surrogate.world.LightRefresh;
import dev.psyda.surrogate.registry.ModGameRules;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The week before the landing. The first player into a fresh Toxic Wastes world wakes on the supply ship
 * Provender six days out from Sallow, with a captain, a doctor, an engineer, and twelve pilots asleep in
 * the hold who are already working on the surface. Each day is a chapter: the port goes in, a sleeper dies,
 * the ship turns over and vents a hold, the planet fills the windows, the contract turns out to be one way,
 * and then the drop pod. It ends in the dark, where {@link Prologue} picks up with the title card.
 *
 * <p>Days end when the player sleeps in their bunk, or when the doctor loses patience. The script resumes
 * at the current day after a restart, and every objective has a timeout so it can never stall.
 */
public final class Transit extends Director {
	private static final String KEY = "cinematic.surrogate.transit.";
	private static final float DISTANCE_KM = 2_400_000f;

	@Nullable
	private static Transit running;
	private static boolean devHandled;
	/** A player who should be put aboard on the next tick, once the join that brought them is over. */
	@Nullable
	private static UUID pendingBoarding;
	@Nullable
	private static String pendingLabel;

	private final TransitState state;
	private final BlockPos origin = TransitDimension.ORIGIN;

	// The day in progress.
	private boolean restAllowed;
	private boolean woke;
	private boolean forcedRest;
	private boolean sleepFadeSent;
	private int keepalive;

	// The ship.
	private boolean gravityOff;
	private boolean engine = true;
	private boolean alarm;
	private final List<UUID> floating = new ArrayList<>();
	private int settleTicks;
	private int lightsMode;
	/** Lamp changes waiting to be applied, three a tick. */
	private final java.util.ArrayDeque<Runnable> lightQueue = new java.util.ArrayDeque<>();

	// Which way the story went.
	private boolean coolantByCrew;
	private boolean patchedByCrew;

	// Things the player can fiddle with.
	/** Which galley warnings the crew have given during the current heating cycle. */
	private int galleyWarned;
	/** The explosion count of the galley unit last time anyone looked; -1 until the first look. */
	private int galleyExplosions = -1;
	private int alarmPresses;
	private int drillTicks;
	private int lastFiddle;

	private Transit(MinecraftServer server, TransitState state) {
		super(server, KEY);
		this.state = state;
		buildScript();
	}

	// ------------------------------------------------------------------ lifecycle

	public static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pendingBoarding != null) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(pendingBoarding);
				String label = pendingLabel;
				pendingBoarding = null;
				pendingLabel = null;
				if (player != null) {
					if (label != null) {
						restart(player);
						if (running != null && !label.equals("day1")) running.jumpTo(label);
					} else {
						begin(player, TransitState.get(server));
					}
				}
			}
			if (running != null) running.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> running = null);
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (running == null || !running.isProtagonist(player) || !TransitDimension.isTransit(player.getWorld())) return null;
			if (running.restAllowed) return null;
			player.sendMessage(Text.translatable("message.surrogate.transit.not_yet").formatted(Formatting.GRAY), true);
			return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
		});
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (running != null && entity instanceof ServerPlayerEntity player && running.isProtagonist(player) && TransitDimension.isTransit(player.getWorld())) {
				running.sendFade(255, 70);
				running.sleepFadeSent = true;
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (running == null || !(entity instanceof ServerPlayerEntity player) || !running.isProtagonist(player)) return;
			if (!TransitDimension.isTransit(player.getWorld())) return;
			if (player.getSleepTimer() >= 100 && running.restAllowed) {
				running.woke = true;
			} else if (running.sleepFadeSent) {
				running.sendFade(0, 20);
				running.sleepFadeSent = false;
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (running != null && running.isProtagonist(player)) running.sendPayload();
			else if (!TransitDimension.isTransit(destination)) ServerPlayNetworking.send(player, TransitPayload.none());
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (running == null || world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
			if (!running.isProtagonist(serverPlayer) || !TransitDimension.isTransit(world)) return ActionResult.PASS;
			return running.onUseBlock(serverPlayer, hit);
		});
	}

	@Nullable
	public static Transit running() {
		return running;
	}

	public static boolean isRunning() {
		return running != null;
	}

	/** Whether someone is in the middle of the week, so nobody else should start the pod opening. */
	public static boolean inProgress(MinecraftServer server) {
		return TransitState.get(server).inProgress();
	}

	public static boolean shouldBegin(MinecraftServer server, HabitatState habitat) {
		TransitState state = TransitState.get(server);
		return Surrogate.CONFIG.transit && ModGameRules.intro(server) && state.stage == TransitState.STAGE_NONE && Prologue.shouldBegin(server, habitat)
				&& TransitDimension.world(server) != null;
	}

	/**
	 * A player joined. The protagonist of a half-told week goes back aboard; a fresh world starts the week
	 * for its first player. @return true when this player belongs on the ship
	 */
	public static boolean onJoin(ServerPlayerEntity player, HabitatState habitat) {
		MinecraftServer server = player.server;
		TransitState state = TransitState.get(server);
		if (!devHandled && FabricLoader.getInstance().isDevelopmentEnvironment() && TransitDimension.world(server) != null) {
			String dev = System.getProperty("surrogate.devTransit");
			if (dev != null && !dev.isEmpty()) {
				devHandled = true;
				if (Boolean.getBoolean("surrogate.devFast")) Director.fast = true;
				Surrogate.LOGGER.info("Transit: dev restart at {} for {}", dev, player.getName().getString());
				pendingBoarding = player.getUuid();
				pendingLabel = dev;
				return true;
			}
		}
		if (state.protagonist != null && state.protagonist.equals(player.getUuid()) && state.inProgress()) {
			if (running == null) {
				running = new Transit(server, state);
				running.resume();
			}
			running.putAboard(player);
			running.send(new CinematicPayloads.State(true, false, false));
			running.sendPayload();
			return true;
		}
		if (shouldBegin(server, habitat) && !PilotManager.data(player).welcomed) {
			// Changing dimension inside the join that brought the player in is asking for trouble: next tick.
			PilotManager.data(player).welcomed = true;
			pendingBoarding = player.getUuid();
			pendingLabel = null;
			return true;
		}
		return false;
	}

	/** Builds the ship, wakes the crew, and puts {@code player} in the thaw cot. */
	public static void begin(ServerPlayerEntity player, TransitState state) {
		MinecraftServer server = player.server;
		ServerWorld world = TransitDimension.world(server);
		if (world == null) return;
		// A replayed week lands in a fresh pod opening, with one cat.
		Prologue.reset(server);
		HabitatState habitat = HabitatState.get(server);
		if (habitat.cat != null && server.getOverworld().getEntity(habitat.cat) instanceof CatEntity earlier) earlier.discard();
		habitat.cat = null;
		ShipBuilder.forceChunks(world, TransitDimension.ORIGIN, true);
		ShipBuilder.build(world, TransitDimension.ORIGIN);
		ShipBuilder.spawnCrew(world, TransitDimension.ORIGIN, state, player.getUuid());
		state.built = true;
		state.protagonist = player.getUuid();
		state.stage = 1;
		state.label = "day1";
		state.hours = 6f;
		state.flipStart = -1L;
		state.flipped = false;
		state.breached = false;
		state.coolantClosed = false;
		state.vasquezDead = false;
		state.bodyRemoved = false;
		state.lights = 0;
		state.markDirty();
		PilotManager.data(player).welcomed = true;
		running = new Transit(server, state);
		running.applyWorldState();
		running.putAboard(player);
		running.startDelay = 40;
		running.takeStage();
		running.sendPayload();
		Surrogate.LOGGER.info("Transit: begins for {}", player.getName().getString());
	}

	/** Dev: start the week over for this player. */
	public static void restart(ServerPlayerEntity player) {
		MinecraftServer server = player.server;
		TransitState state = TransitState.get(server);
		ServerWorld world = TransitDimension.world(server);
		if (world == null) return;
		if (running != null) {
			running.resetClient();
			running.leaveStage();
		}
		running = null;
		if (PilotManager.isPiloting(player)) PilotManager.disconnect(player, true, null, false);
		ShipBuilder.clearCrew(world, TransitDimension.ORIGIN, state);
		state.stage = TransitState.STAGE_NONE;
		state.markDirty();
		begin(player, state);
	}

	/** Dev: straight to the drop. */
	public static void skipToDrop(ServerPlayerEntity player) {
		if (running != null && running.isProtagonist(player)) running.jumpTo("drop");
	}

	/** Dev: jump to the start of a day. */
	public static void jumpToDay(ServerPlayerEntity player, int day) {
		if (running != null && running.isProtagonist(player)) running.jumpTo("day" + MathHelper.clamp(day, 1, 7));
	}

	public boolean isProtagonist(PlayerEntity player) {
		return state.protagonist != null && state.protagonist.equals(player.getUuid());
	}

	public int day() {
		return state.stage;
	}

	@Override
	protected void onSkip(ServerPlayerEntity player) {
		if (!isProtagonist(player)) return;
		// Skip the scene in progress, not the week: land at the next point where the player has their hands.
		String next = nextLabel("free");
		Surrogate.LOGGER.info("Transit: skipped at {} to {}", currentLabel(), next);
		if (next != null) jump(next);
	}

	/** After a restart: rebuild the ship's mood and pick the script up at the saved label. */
	private void resume() {
		applyWorldState();
		String label = state.label.isEmpty() ? "day" + Math.max(1, Math.min(7, state.stage)) : state.label;
		if (indexOf(label) < 0) label = "day" + Math.max(1, Math.min(7, state.stage));
		jump(label);
		startDelay = 40;
		takeStage();
		Surrogate.LOGGER.info("Transit: resuming at {}", label);
	}

	/** Jump the script to a label, settling the ship into the state that label expects. */
	private void jumpTo(String label) {
		int target = indexOf(label);
		if (target < 0) return;
		resetClient();
		ServerPlayerEntity player = player();
		if (player != null && PilotManager.isPiloting(player)) PilotManager.disconnect(player, true, null, false);
		if (label.startsWith("day")) {
			int day = Integer.parseInt(label.substring(3));
			state.stage = day;
			state.hours = 6f;
			state.flipped = day > 4;
			// Long enough ago that the turn is over, but never negative: the client reads a negative start as no turn.
			state.flipStart = state.flipped ? Math.max(0L, world().getTime() - 100000L) : -1L;
			state.breached = false;
			state.coolantClosed = day > 3;
			state.vasquezDead = day > 3;
			state.bodyRemoved = day > 4;
		} else if (label.equals("drop")) {
			state.stage = 7;
			state.hours = 6f;
			state.flipped = true;
			state.flipStart = Math.max(0L, world().getTime() - 100000L);
			state.breached = false;
			state.coolantClosed = true;
			state.vasquezDead = true;
			state.bodyRemoved = true;
		}
		state.label = label;
		state.markDirty();
		restAllowed = false;
		woke = false;
		forcedRest = false;
		gravityOff = false;
		engine = true;
		alarm = false;
		applyWorldState();
		if (player != null) {
			Vec3d at = at(ShipBuilder.BUNK_STAND);
			player.teleport(world(), at.x, at.y, at.z, 90f, 0f);
			player.removeStatusEffect(StatusEffects.SLOW_FALLING);
			player.removeStatusEffect(StatusEffects.JUMP_BOOST);
		}
		sendPayload();
		jump(label);
	}

	/** Makes the blocks agree with the saved state: lamps, lever, the seam, the sleeper who is gone. */
	private void applyWorldState() {
		ServerWorld world = world();
		if (!state.built) return;
		ShipBuilder.forceChunks(world, origin, true);
		ShipBuilder.setDayLamps(world, origin, state.stage);
		ShipBuilder.setLights(world, origin, true);
		lightsMode = 0;
		ShipBuilder.setConsoles(world, origin, false);
		if (state.coolantClosed && !ShipBuilder.isLeverClosed(world, origin)) ShipBuilder.closeLever(world, origin);
		if (!state.breached && ShipBuilder.isBreached(world, origin)) ShipBuilder.patch(world, origin);
		if (state.vasquezDead) {
			ShipBuilder.markVasquezDead(world, origin);
			CrewEntity vasquez = sleeper(ShipBuilder.VASQUEZ);
			if (vasquez != null) {
				if (state.bodyRemoved) vasquez.discard();
				else if (!vasquez.isCollapsed()) vasquez.collapseAt(at(ShipBuilder.VASQUEZ_FLOOR), 90f);
			}
		}
		if (state.stage >= 5) ShipBuilder.putDescentProtocol(world, origin);
		ShipBuilder.topUpAir(world, origin);
		placeCrewForDay(state.stage);
	}

	/** Where the crew are found at the start of a day, when nothing is happening. */
	private void placeCrewForDay(int day) {
		CrewEntity c = crew(Crew.CASTELLANOS);
		CrewEntity f = crew(Crew.FERREIRA);
		CrewEntity t = crew(Crew.TEAGUE);
		if (c != null && !c.isFixed()) c.refreshPositionAndAngles(at(ShipBuilder.CASTELLANOS_START).x, at(ShipBuilder.CASTELLANOS_START).y, at(ShipBuilder.CASTELLANOS_START).z, 180f, 0f);
		if (f != null && !f.isFixed()) f.refreshPositionAndAngles(at(ShipBuilder.FERREIRA_DESK).x, at(ShipBuilder.FERREIRA_DESK).y, at(ShipBuilder.FERREIRA_DESK).z, 0f, 0f);
		if (t != null && !t.isFixed()) t.refreshPositionAndAngles(at(ShipBuilder.TEAGUE_START).x, at(ShipBuilder.TEAGUE_START).y, at(ShipBuilder.TEAGUE_START).z, 0f, 0f);
	}

	private void putAboard(ServerPlayerEntity player) {
		ServerWorld world = world();
		if (!TransitDimension.isTransit(player.getWorld())) {
			Vec3d at = at(state.stage <= 1 ? ShipBuilder.PLAYER_WAKE : ShipBuilder.BUNK_STAND);
			player.teleport(world, at.x, at.y, at.z, 90f, 0f);
		}
		player.setSpawnPoint(TransitDimension.WORLD, origin.add(ShipBuilder.PLAYER_BED_HEAD), 90f, true, false);
		// The ship goes up the same tick the player arrives; hand them its light once the engine has caught up.
		LightRefresh.schedule(player, TransitDimension.WORLD, origin, 3, 45);
	}

	// ------------------------------------------------------------------ ticking

	@Override
	protected void tick() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		super.tick();
		if (running != this) return;

		// The clock: it only runs while there is a day to run through.
		if (state.stage >= 1 && state.stage <= 7) {
			state.hours = Math.min(23.5f, state.hours + 24f / dayTicks());
			int wanted = alarm ? 2 : state.hours >= 20f ? 1 : 0;
			if (wanted != lightsMode) {
				lightsMode = wanted;
				lightQueue.clear();
				lightQueue.addAll(ShipBuilder.lightChanges(world(), origin, wanted == 0));
				state.lights = wanted;
				sendPayload();
			}
		}
		for (int i = 0; i < 3 && !lightQueue.isEmpty(); i++) lightQueue.poll().run();
		if (++keepalive % 200 == 0) {
			sendPayload();
			ShipBuilder.topUpAir(world(), origin);
			state.markDirty();
		}

		if (gravityOff && player.age % 20 == 0 && TransitDimension.isTransit(player.getWorld())) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false, false));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 3, true, false, false));
		}
		if (drillTicks > 0 && --drillTicks == 0 && !state.breached) {
			alarm = false;
			ShipBuilder.setConsoles(world(), origin, false);
			sendPayload();
		}
		if (settleTicks > 0 && --settleTicks == 0) {
			for (UUID id : floating) {
				Entity item = world().getEntity(id);
				if (item != null) item.discard();
			}
			floating.clear();
		}
		if (state.breached && player.age % 4 == 0) ShipBuilder.ventParticles(world(), origin);
		if (player.age % 10 == 0) watchGalley();
	}

	private int dayTicks() {
		return fast ? 600 : Math.max(600, Surrogate.CONFIG.transitDayTicks);
	}

	private int objectiveTimeout() {
		return fast ? 200 : Math.max(200, Surrogate.CONFIG.transitObjectiveTimeoutTicks);
	}

	private int restTimeout() {
		return fast ? 240 : Math.max(400, Surrogate.CONFIG.transitRestTimeoutTicks);
	}

	private void sendPayload() {
		float hoursPerTick = state.stage >= 1 && state.stage <= 7 ? 24f / dayTicks() : 0f;
		send(new TransitPayload(true, state.stage, state.hours, hoursPerTick, state.flipStart, flipTicks(), !gravityOff, state.lights, alarm, engine,
				state.stage >= TransitState.STAGE_DROP ? 1 : 0, state.breached));
	}

	// ------------------------------------------------------------------ things to fiddle with

	/** The galley unit and the general alarm answer back; everything else is left to the blocks. */
	private ActionResult onUseBlock(ServerPlayerEntity player, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (pos.equals(origin.add(ShipBuilder.ALARM_BUTTON))) {
			pressAlarm(player);
			return ActionResult.PASS;
		}
		return ActionResult.PASS;
	}

	/** A casserole, a heating cycle, and a crew who have seen this before. The unit does the work; they comment. */
	private void watchGalley() {
		if (!(world().getBlockEntity(origin.add(ShipBuilder.GALLEY_UNIT)) instanceof MicrowaveBlockEntity unit)) return;
		if (galleyExplosions < 0) galleyExplosions = unit.getExplosions();
		if (unit.getExplosions() > galleyExplosions) {
			galleyExplosions = unit.getExplosions();
			galleyWarned = 0;
			effect(CinematicPayloads.EFFECT_SHAKE, 0.5f, 20);
			fiddleLine(Crew.CASTELLANOS, "galley3");
			fiddleLine(Crew.TEAGUE, "galley4");
			return;
		}
		if (unit.getAbuse() >= 1 && galleyWarned < 1) {
			galleyWarned = 1;
			fiddleLine(Crew.TEAGUE, "galley1");
		}
		if (unit.getAbuse() >= 3 && galleyWarned < 2) {
			galleyWarned = 2;
			fiddleLine(Crew.TEAGUE, "galley2");
		}
		if (!unit.isRunning()) galleyWarned = 0;
	}

	/** The general alarm. It works. That is the problem. */
	private void pressAlarm(ServerPlayerEntity player) {
		if (player.age - lastFiddle < 10) return;
		lastFiddle = player.age;
		alarmPresses++;
		alarm = true;
		drillTicks = fast ? 60 : 120;
		ShipBuilder.setConsoles(world(), origin, true);
		sendPayload();
		for (Crew who : new Crew[]{Crew.CASTELLANOS, Crew.FERREIRA, Crew.TEAGUE}) {
			CrewEntity crew = crew(who);
			if (crew != null && !crew.isFixed()) crew.setLookTarget(player);
		}
		switch (Math.min(alarmPresses, 4)) {
			case 1 -> fiddleLine(Crew.CASTELLANOS, "alarm_button1");
			case 2 -> fiddleLine(Crew.TEAGUE, "alarm_button2");
			case 3 -> fiddleLine(Crew.CASTELLANOS, "alarm_button3");
			default -> {
				if (alarmPresses == 4) fiddleLine(Crew.FERREIRA, "alarm_button4");
			}
		}
	}

	/** An intercom line outside the script, when nobody else is talking. */
	private void fiddleLine(Crew who, String lineKey) {
		if (current instanceof Beat.Line) return;
		sendLine(who.nameKey(), KEY + lineKey, CinematicPayloads.INTERCOM, -1, true, "");
	}

	private int flipTicks() {
		return fast ? 240 : 1100;
	}

	/** How far along the week is, 0 at the first morning and 1 at the last night. */
	public static float weekFraction(int day, float hours) {
		return MathHelper.clamp(((day - 1) * 24f + (hours - 6f)) / 160f, 0f, 1f);
	}

	public static int distanceKm(int day, float hours) {
		float f = weekFraction(day, hours);
		return Math.round(DISTANCE_KM * (float) Math.pow(1f - f, 1.7));
	}

	private String distanceArg() {
		return String.format("%,d", distanceKm(state.stage, state.hours));
	}

	// ------------------------------------------------------------------ the script

	@Override
	protected void buildScript() {
		Crew C = Crew.CASTELLANOS;
		Crew F = Crew.FERREIRA;
		Crew T = Crew.TEAGUE;
		Crew H = Crew.HALLORAN;
		Crew M = Crew.MARSH;

		// ================================================================ Day one: thaw.
		label("day1");
		run(() -> {
			state(true, true, true);
			sendFade(255, 0);
			objectiveClear();
			ServerPlayerEntity player = player();
			if (player != null) {
				player.getHungerManager().setFoodLevel(8);
				Vec3d at = at(ShipBuilder.PLAYER_WAKE);
				player.teleport(world(), at.x, at.y, at.z, 90f, 10f);
			}
			crewTo(F, ShipBuilder.FERREIRA_START, -90f);
			crewTo(C, ShipBuilder.CASTELLANOS_START, 180f);
			crewTo(T, ShipBuilder.TEAGUE_START, 0f);
			playToPlayer(ModSounds.THAW, 0.8f);
		});
		wait(50);
		// A low shot from the cot, up at the lamp, then across to the doctor.
		shot(cam(5.5, 1.35, -11.1, 5.5, 4.0, -10.2, 100, CinematicPayloads.EASE_SMOOTH),
				cam(5.3, 1.7, -10.9, 5.5, 2.4, -9.5, 0, 0));
		wait(10);
		fade(0, 90);
		wait(60);
		say(F, "thaw1");
		shot(cam(3.6, 2.45, -9.9, 5.6, 2.25, -9.5, 0, 0));
		say(F, "thaw2");
		say(F, "thaw3");
		intercom(C, "hail1");
		say(F, "hail2");
		intercom(C, "hail3");
		say(F, "thaw5");
		say(F, "thaw4");
		label("free1a");
		run(() -> {
			release();
			state(true, false, false);
			sendFade(0, 20);
			crewTo(C, ShipBuilder.CASTELLANOS_WINDOW, 180f);
		});
		walk(F, ShipBuilder.FERREIRA_DESK, null);
		objective("bridge");
		hint("bridge_nudge");
		until(this::playerOnBridge, objectiveTimeout(), C, "bridge_nudge", 700, null);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			ServerPlayerEntity player = player();
			if (player != null && !playerOnBridge()) {
				Vec3d at = at(ShipBuilder.BRIDGE_PLAYER);
				player.teleport(world(), at.x, at.y, at.z, 180f, 0f);
			}
		});
		wait(10);
		shot(cam(2.6, 2.6, -16.4, -1.5, 2.3, -19.5, 0, 0));
		say(C, "bridge1");
		// The window, and the light in it.
		shot(cam(-0.5, 2.4, -17.5, -0.5, 2.5, -30.0, 140, CinematicPayloads.EASE_SMOOTH),
				cam(-0.5, 2.4, -19.6, -0.5, 2.5, -30.0, 0, 0));
		say(C, "bridge2");
		shot(cam(-2.8, 2.5, -18.2, -1.3, 2.3, -19.4, 0, 0));
		say(C, "bridge3");
		say(C, "bridge4");
		say(C, "bridge5");
		label("free1b");
		run(() -> {
			release();
			state(true, false, false);
		});
		walk(C, ShipBuilder.CASTELLANOS_START, null);
		objective("eat");
		hint("eat_nudge");
		until(this::playerFed, objectiveTimeout() * 2, F, "eat_nudge", 900, null);
		objectiveDone();
		wait(20);
		rest("rest_nudge", C);
		endDay();

		// ================================================================ Day two: the port.
		label("day2");
		run(this::startDay);
		intercom(F, "port_call");
		label("free2a");
		run(() -> {
			state(true, false, false);
			crewTo(F, ShipBuilder.FERREIRA_CHAIRSIDE, -90f);
		});
		objective("medbay");
		hint("medbay_nudge");
		until(this::playerInMedbay, objectiveTimeout(), F, "medbay_nudge", 700, null);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			ServerPlayerEntity player = player();
			if (player != null && !playerInMedbay()) {
				Vec3d at = at(ShipBuilder.FERREIRA_DESK);
				player.teleport(world(), at.x, at.y, at.z, -90f, 0f);
			}
		});
		shot(cam(3.6, 2.5, -9.6, 5.8, 2.2, -9.3, 0, 0));
		say(F, "port1");
		say(F, "port2");
		say(F, "port3");
		objective("link");
		run(() -> {
			release();
			state(true, false, false);
			startCalibration();
		});
		until(this::playerOnline, 600, null, null, 0, null);
		wait(20);
		radio(F, "link1");
		radio(F, "link2");
		radio(F, "link3");
		wait(260);
		run(() -> playToPlayer(ModSounds.BLEED, 0.9f));
		radio(M, "bleed1");
		radio(F, "bleed2");
		run(this::endCalibration);
		wait(40);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			crewTo(F, ShipBuilder.FERREIRA_CHAIRSIDE, -90f);
		});
		shot(cam(4.0, 2.5, -8.4, 5.6, 2.2, -8.7, 0, 0));
		say(F, "port4");
		say(F, "port5");
		label("free2b");
		run(() -> {
			release();
			state(true, false, false);
		});
		wait(200);
		intercom(C, "relay_warn");
		rest("rest2_nudge", F);
		endDay();

		// ================================================================ Day three: the manifest.
		label("day3");
		run(this::startDay);
		run(() -> crewTo(T, ShipBuilder.TEAGUE_BUNK_DOOR, -90f));
		say(T, "hold_call");
		label("free3a");
		run(() -> state(true, false, false));
		walk(T, ShipBuilder.TEAGUE_HOLD_ENTRY, ShipBuilder.HOLD_DOOR_POS);
		objective("hold");
		hint("hold_nudge");
		until(this::playerInHold, objectiveTimeout(), T, "hold_nudge", 700, null);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			crewTo(T, ShipBuilder.TEAGUE_HOLD_ENTRY, 0f);
			ServerPlayerEntity player = player();
			if (player != null && !playerInHold()) {
				Vec3d at = at(new Vec3d(0.5, 1, 8.5));
				player.teleport(world(), at.x, at.y, at.z, 0f, 0f);
			}
		});
		// The rack, both rows, from the door.
		shot(cam(0.5, 2.8, 5.6, 0.5, 1.6, 19.0, 160, CinematicPayloads.EASE_SMOOTH),
				cam(0.5, 2.8, 9.0, 0.5, 1.6, 19.0, 0, 0));
		say(T, "hold1");
		shot(cam(-2.5, 2.4, 8.5, -5.6, 1.4, 11.5, 0, 0));
		say(T, "hold2");
		shot(cam(2.0, 2.5, 7.4, 0.6, 2.2, 6.6, 0, 0));
		say(T, "hold3");
		label("free3b");
		run(() -> {
			release();
			state(true, false, false);
		});
		walk(T, ShipBuilder.TEAGUE_HOLD, null);
		objective("coolant");
		hint("coolant_nudge");
		until(this::leverClosed, objectiveTimeout(), T, "coolant_nudge", 600, () -> coolantByCrew = true);
		branch(() -> coolantByCrew, line(T, "coolant_timeout"), new Beat.Run(() -> ShipBuilder.closeLever(world(), origin)), new Beat.Wait(20));
		run(() -> {
			state.coolantClosed = true;
			state.markDirty();
		});
		objectiveDone();
		branch(() -> !coolantByCrew, line(T, "coolant_done"));
		wait(30);
		// Chair seven.
		run(() -> {
			state(true, true, true);
			sound(ModSounds.FLATLINE, new Vec3d(6.5, 1.5, 7.5), 1.5f, 1.0f);
		});
		shot(cam(3.2, 2.4, 10.2, 6.4, 1.3, 7.6, 0, 0));
		say(T, "alarm1");
		walk(F, ShipBuilder.FERREIRA_HOLD, ShipBuilder.HOLD_DOOR_POS);
		walk(T, ShipBuilder.TEAGUE_VASQUEZ, null);
		arrive(F, ShipBuilder.FERREIRA_HOLD, 300);
		shot(cam(2.6, 2.5, 9.6, 5.2, 1.7, 7.8, 0, 0));
		say(F, "alarm2");
		say(T, "alarm3");
		say(F, "alarm4");
		intercom(C, "alarm5");
		wait(30);
		say(F, "alarm6");
		run(this::vasquezDies);
		wait(60);
		say(F, "alarm7");
		intercom(C, "alarm8");
		shot(cam(0.5, 2.6, 12.5, 0.5, 1.6, 19.5, 0, 0));
		say(T, "alarm9");
		label("free3c");
		run(() -> {
			release();
			state(true, false, false);
		});
		walk(F, ShipBuilder.FERREIRA_DESK, ShipBuilder.HOLD_DOOR_POS);
		wait(60);
		rest("rest3_nudge", F);
		endDay();

		// ================================================================ Day four: turnover.
		label("day4");
		run(this::startDay);
		intercom(C, "turn_call");
		label("free4a");
		run(() -> {
			state(true, false, false);
			crewTo(C, ShipBuilder.CASTELLANOS_START, 180f);
		});
		walk(F, ShipBuilder.FERREIRA_BRIDGE, null);
		walk(T, ShipBuilder.TEAGUE_BRIDGE, null);
		objective("turnover");
		hint("turn_nudge");
		until(this::playerOnBridge, objectiveTimeout(), C, "turn_nudge", 700, null);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			ServerPlayerEntity player = player();
			if (player != null && !playerOnBridge()) {
				Vec3d at = at(ShipBuilder.BRIDGE_PLAYER);
				player.teleport(world(), at.x, at.y, at.z, 180f, 0f);
			}
			crewTo(F, ShipBuilder.FERREIRA_BRIDGE, 180f);
			crewTo(T, ShipBuilder.TEAGUE_BRIDGE, 180f);
		});
		shot(cam(2.6, 2.6, -15.2, 0.4, 2.2, -17.0, 0, 0));
		say(C, "turn1");
		say(C, "turn2");
		run(this::engineCut);
		shot(cam(1.6, 2.6, -17.5, -6.0, 2.4, -17.5, 0, 0));
		wait(40);
		say(T, "turn3");
		say(C, "turn4");
		say(F, "turn5");
		// The port window, and the whole sky going past it.
		shot(cam(-2.2, 2.5, -17.5, -9.0, 2.6, -17.5, 0, 0));
		wait(scaledFlip());
		say(C, "turn6");
		run(this::engineRelight);
		shot(cam(2.6, 2.6, -15.2, 0.4, 2.2, -17.0, 0, 0));
		wait(50);
		run(this::breachNow);
		shot(cam(-3.2, 2.6, -15.8, 3.8, 1.5, -20.8, 0, 0));
		say(T, "breach1");
		say(C, "breach2");
		say(T, "breach3");
		label("free4b");
		run(() -> {
			release();
			state(true, false, false);
			givePatchKit();
		});
		walk(T, ShipBuilder.TEAGUE_BREACH, ShipBuilder.HOLD_DOOR_POS);
		objective("patch");
		hint("patch_nudge");
		until(this::patched, objectiveTimeout() * 2, T, "patch_nudge", 500, () -> patchedByCrew = true);
		branch(() -> patchedByCrew, line(T, "patch_timeout"), new Beat.Run(() -> ShipBuilder.patch(world(), origin)), new Beat.Wait(20));
		run(this::breachOver);
		objectiveDone();
		branch(() -> !patchedByCrew, line(T, "patch_done"));
		wait(40);
		say(T, "breach4");
		intercom(C, "breach5");
		wait(30);
		radio(H, "relay1");
		radio(M, "relay2");
		intercom(C, "relay3");
		radio(H, "relay4");
		walk(T, ShipBuilder.TEAGUE_START, ShipBuilder.HOLD_DOOR_POS);
		rest("rest_nudge", C);
		endDay();

		// ================================================================ Day five: Sallow.
		label("day5");
		run(this::startDay);
		run(this::removeBody);
		intercom(C, "sallow_call");
		label("free5a");
		run(() -> state(true, false, false));
		walk(C, ShipBuilder.CASTELLANOS_AFT_WINDOW, ShipBuilder.HOLD_DOOR_POS);
		objective("window");
		hint("window_nudge");
		until(this::playerAtAftWindow, objectiveTimeout(), C, "window_nudge", 700, null);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			crewTo(C, ShipBuilder.CASTELLANOS_AFT_WINDOW, 0f);
			ServerPlayerEntity player = player();
			if (player != null && !playerAtAftWindow()) {
				Vec3d at = at(ShipBuilder.PLAYER_AFT_WINDOW);
				player.teleport(world(), at.x, at.y, at.z, 0f, 0f);
			}
		});
		// The aft window with the planet in it, then the captain against it.
		shot(cam(-1.0, 2.4, 14.5, -1.0, 2.6, 40.0, 160, CinematicPayloads.EASE_SMOOTH),
				cam(-1.0, 2.4, 18.2, -1.0, 2.6, 40.0, 0, 0));
		say(C, "sallow1");
		say(C, "sallow2");
		shot(cam(0.8, 2.5, 18.2, -1.3, 2.3, 19.6, 0, 0));
		say(C, "sallow3");
		say(C, "sallow4");
		say(C, "sallow5");
		label("free5b");
		run(() -> {
			release();
			state(true, false, false);
			ShipBuilder.putDescentProtocol(world(), origin);
		});
		walk(C, ShipBuilder.CASTELLANOS_START, ShipBuilder.HOLD_DOOR_POS);
		wait(100);
		intercom(F, "descent_brief");
		wait(200);
		radio(M, "relay5");
		rest("rest_nudge", C);
		endDay();

		// ================================================================ Day six: the contract.
		label("day6");
		run(this::startDay);
		intercom(C, "contract_call");
		label("free6a");
		run(() -> {
			state(true, false, false);
			crewTo(C, ShipBuilder.CASTELLANOS_START, 180f);
		});
		objective("contract");
		hint("contract_nudge");
		until(this::playerOnBridge, objectiveTimeout(), C, "contract_nudge", 700, null);
		objectiveDone();
		run(() -> {
			state(true, true, true);
			ServerPlayerEntity player = player();
			if (player != null && !playerOnBridge()) {
				Vec3d at = at(ShipBuilder.BRIDGE_PLAYER);
				player.teleport(world(), at.x, at.y, at.z, 180f, 0f);
			}
		});
		shot(cam(2.4, 2.5, -15.0, 0.5, 2.2, -16.6, 0, 0));
		say(C, "contract1");
		say(C, "contract2");
		shot(cam(-0.9, 2.4, -15.2, 0.5, 2.3, -16.6, 0, 0));
		say(C, "contract3");
		wait(50);
		say(C, "contract4");
		wait(40);
		run(() -> ShipBuilder.setConsoles(world(), origin, true));
		system("cinematic.surrogate.transit.ship", "system1");
		run(() -> ShipBuilder.setConsoles(world(), origin, false));
		wait(30);
		say(C, "contract5");
		walk(F, ShipBuilder.FERREIRA_BRIDGE, null);
		arrive(F, ShipBuilder.FERREIRA_BRIDGE, 200);
		say(F, "contract6");
		label("free6b");
		run(() -> {
			release();
			state(true, false, false);
		});
		walk(F, ShipBuilder.FERREIRA_DESK, null);
		wait(300);
		intercom(F, "port_check1");
		intercom(F, "port_check2");
		wait(200);
		radio(H, "relay6");
		radio(H, "relay7");
		rest("rest6_nudge", F);
		endDay();

		// ================================================================ Day seven: the drop.
		label("day7");
		run(this::startDay);
		intercom(C, "drop_call");
		label("free7a");
		run(() -> state(true, false, false));
		walk(C, ShipBuilder.CASTELLANOS_DROPBAY, ShipBuilder.HOLD_DOOR_POS);
		walk(F, ShipBuilder.FERREIRA_DROPBAY, ShipBuilder.HOLD_DOOR_POS);
		walk(T, ShipBuilder.TEAGUE_DROPBAY, ShipBuilder.HOLD_DOOR_POS);
		objective("dropbay");
		hint("drop_nudge");
		until(this::playerInDropbay, objectiveTimeout(), C, "drop_nudge", 700, null);
		objectiveDone();
		label("drop");
		run(() -> {
			state(true, true, true);
			crewTo(C, ShipBuilder.CASTELLANOS_DROPBAY, 90f);
			crewTo(F, ShipBuilder.FERREIRA_DROPBAY, -90f);
			crewTo(T, ShipBuilder.TEAGUE_DROPBAY, 0f);
			ServerPlayerEntity player = player();
			if (player != null && !playerInDropbay()) {
				Vec3d at = at(new Vec3d(5.5, 1, 22.5));
				player.teleport(world(), at.x, at.y, at.z, 0f, 0f);
			}
		});
		shot(cam(5.5, 2.5, 22.3, 5.5, 2.0, 25.5, 0, 0));
		say(F, "drop1");
		shot(cam(6.2, 2.4, 24.8, 3.6, 2.3, 23.4, 0, 0));
		say(C, "drop2");
		run(this::giveSeeds);
		say(C, "drop2c");
		say(C, "drop2b");
		run(this::catToThePod);
		say(T, "drop3");
		label("free7b");
		run(() -> {
			release();
			state(true, false, false);
		});
		objective("pod");
		hint("pod_nudge");
		until(this::playerInPod, objectiveTimeout(), F, "pod_nudge", 500, null);
		objectiveDone();
		run(this::seatInPod);
		shot(cam(5.5, 2.3, 23.4, 5.5, 2.4, 30.0, 0, 0));
		wait(20);
		say(F, "drop4");
		say(F, "drop5");
		run(this::sealHatch);
		system("cinematic.surrogate.transit.ship", "system2");
		run(() -> playToPlayer(ModSounds.SEDATE, 1.0f));
		fade(180, 120);
		intercom(C, "drop6");
		run(() -> {
			playToPlayer(ModSounds.DESCENT, 1.0f);
			effect(CinematicPayloads.EFFECT_SHAKE, 0.9f, 140);
			state.stage = TransitState.STAGE_DROP;
			state.label = "drop";
			state.markDirty();
			sendPayload();
		});
		fade(255, 90);
		wait(140);
		label("land");
		run(this::land);
	}

	// ------------------------------------------------------------------ the shape of a day

	/** Tells the player to rest, then waits for them to sleep or for the doctor to run out of patience. */
	private void rest(String nudgeKey, Crew nudger) {
		run(() -> {
			restAllowed = true;
			woke = false;
			forcedRest = false;
			objectiveNow("rest");
		});
		until(() -> woke, restTimeout(), nudger, nudgeKey, fast ? 60 : 3000, () -> forcedRest = true);
		branch(() -> forcedRest, new Beat.Run(this::sedate), new Beat.Wait(60));
	}

	private void sedate() {
		sendLine(Crew.FERREIRA.nameKey(), KEY + "rest_forced", CinematicPayloads.SPEECH, -1, true, "");
		playToPlayer(ModSounds.SEDATE, 0.8f);
		sendFade(255, 50);
		ServerPlayerEntity player = player();
		if (player != null && player.isSleeping()) player.wakeUp(true, true);
	}

	/** The morning: stage up, lamps on, a chapter card on black, then the ship fading in. */
	private void endDay() {
		run(() -> {
			objectiveDone0();
			restAllowed = false;
			woke = false;
			forcedRest = false;
			sleepFadeSent = false;
			state.stage = Math.min(7, state.stage + 1);
			state.hours = 6f;
			state.label = "day" + state.stage;
			state.markDirty();
			ServerPlayerEntity player = player();
			if (player != null) {
				if (player.isSleeping()) player.wakeUp(true, true);
				PilotManager.data(player).fatigue = 0;
				player.getHungerManager().setFoodLevel(Math.max(10, player.getHungerManager().getFoodLevel() - 4));
			}
			Surrogate.LOGGER.info("Transit: day {}", state.stage);
		});
		wait(20);
	}

	/** The first beat of every day after the first: black screen, chapter card, fade up. */
	private void startDay() {
		state(true, true, true);
		sendFade(255, 0);
		objectiveClear();
		lightsMode = -1;
		ShipBuilder.setDayLamps(world(), origin, state.stage);
		placeCrewForDay(state.stage);
		ShipBuilder.topUpAir(world(), origin);
		ServerPlayerEntity player = player();
		if (player != null) {
			Vec3d at = at(ShipBuilder.BUNK_STAND);
			if (!ShipBuilder.BUNKS.contains(origin, player.getPos())) player.teleport(world(), at.x, at.y, at.z, 90f, 0f);
			player.removeStatusEffect(StatusEffects.SLOW_FALLING);
			player.removeStatusEffect(StatusEffects.JUMP_BOOST);
		}
		sendPayload();
		String sub = state.stage >= 7 ? KEY + "chapter.orbit" : KEY + "chapter.sub";
		sendLine(sub, KEY + "day." + state.stage, CinematicPayloads.CHAPTER, scaled(110), false, distanceArg());
		beats.add(index + 1, new Beat.Run(() -> {
			sendFade(0, 80);
			release();
			state(true, false, false);
		}));
		beats.add(index + 1, new Beat.Wait(scaled(130)));
	}

	private int scaledFlip() {
		// The Wait beat scales itself; hand it the unscaled length.
		return fast ? 240 * 4 : 1100;
	}

	// ------------------------------------------------------------------ conditions

	private boolean inRoom(ShipBuilder.Room room) {
		ServerPlayerEntity player = player();
		return player != null && TransitDimension.isTransit(player.getWorld()) && room.contains(origin, player.getPos().add(0, 0.5, 0));
	}

	private boolean playerOnBridge() {
		return inRoom(ShipBuilder.BRIDGE);
	}

	private boolean playerInMedbay() {
		return inRoom(ShipBuilder.MEDBAY);
	}

	private boolean playerInHold() {
		return inRoom(ShipBuilder.HOLD);
	}

	private boolean playerInDropbay() {
		return inRoom(ShipBuilder.DROPBAY);
	}

	private boolean playerInPod() {
		ServerPlayerEntity player = player();
		return player != null && inRoom(ShipBuilder.DROPBAY) && player.getPos().distanceTo(at(ShipBuilder.POD_SEAT_POS)) < 1.6;
	}

	private boolean playerAtAftWindow() {
		ServerPlayerEntity player = player();
		if (player == null || !inRoom(ShipBuilder.AFT_WINDOW_AREA)) return false;
		float yaw = MathHelper.wrapDegrees(player.getYaw());
		return Math.abs(yaw) < 50f;
	}

	private boolean playerFed() {
		ServerPlayerEntity player = player();
		return player != null && player.getHungerManager().getFoodLevel() >= 14;
	}

	private boolean playerOnline() {
		ServerPlayerEntity player = player();
		return player != null && player.getVehicle() instanceof RobotEntity robot && robot.isPilot(player) && robot.getState() == RobotState.ONLINE;
	}

	private boolean leverClosed() {
		return ShipBuilder.isLeverClosed(world(), origin);
	}

	private boolean patched() {
		return Atmosphere.isAirtight(world(), origin.add(ShipBuilder.BREACH));
	}

	// ------------------------------------------------------------------ things that happen aboard

	private void crewTo(Crew who, Vec3d rel, float yaw) {
		CrewEntity crew = crew(who);
		if (crew == null) return;
		if (crew.isFixed()) crew.stand();
		crew.stopWalking();
		Vec3d at = at(rel);
		crew.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0f);
		crew.face(yaw);
	}

	/** The doctor sits the player down and opens the link to the unit in the hold. */
	private void startCalibration() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		if (PilotManager.isPiloting(player)) return;
		RobotEntity robot = calibrationRobot();
		if (robot != null) {
			robot.setEnergy(robot.getEnergyCapacity());
			if (robot.getState() != RobotState.OFFLINE) robot.forceOffline();
		}
		if (world().getBlockEntity(origin.add(ShipBuilder.LINK_CHAIR)) instanceof DiveChairBlockEntity chair) {
			if (chair.getLinkedRobot() == null && robot != null) chair.setLink(robot.getUuid(), robot.getName().getString());
			Vec3d at = at(new Vec3d(5.5, 1, -9.5));
			player.teleport(world(), at.x, at.y, at.z, -90f, 0f);
			PilotManager.requestDive(player, chair);
		}
	}

	private void endCalibration() {
		ServerPlayerEntity player = player();
		if (player != null && PilotManager.isPiloting(player)) {
			PilotManager.disconnect(player, true, Text.translatable("message.surrogate.transit.link_cut"), true);
		}
	}

	private void vasquezDies() {
		CrewEntity vasquez = sleeper(ShipBuilder.VASQUEZ);
		if (vasquez != null) {
			vasquez.stand();
			vasquez.collapseAt(at(ShipBuilder.VASQUEZ_FLOOR), 90f);
		}
		ShipBuilder.markVasquezDead(world(), origin);
		state.vasquezDead = true;
		state.markDirty();
		playToPlayer(ModSounds.SIGNAL_LOST, 0.8f, 0.7f);
	}

	private void removeBody() {
		if (state.bodyRemoved) return;
		CrewEntity vasquez = sleeper(ShipBuilder.VASQUEZ);
		if (vasquez != null) vasquez.discard();
		state.bodyRemoved = true;
		state.markDirty();
	}

	/** The main engine goes quiet and everything loose, including the player, stops weighing anything. */
	private void engineCut() {
		gravityOff = true;
		engine = false;
		state.flipStart = world().getTime();
		state.markDirty();
		playToPlayer(ModSounds.ENGINE_CUT, 1.0f);
		CrewEntity c = crew(Crew.CASTELLANOS);
		if (c != null) c.sitAt(at(ShipBuilder.CASTELLANOS_CHAIR), 180f);
		ServerWorld world = world();
		Random random = world.getRandom();
		ItemStack[] loose = {new ItemStack(ModItems.WRENCH), new ItemStack(Items.BREAD), new ItemStack(Items.IRON_NUGGET), new ItemStack(ModItems.POWER_CELL),
				new ItemStack(Items.PAPER), new ItemStack(Items.BOOK), new ItemStack(Items.APPLE), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.BOWL)};
		ShipBuilder.Room[] rooms = {ShipBuilder.BRIDGE, ShipBuilder.BRIDGE, ShipBuilder.BRIDGE, ShipBuilder.CORRIDOR, ShipBuilder.GALLEY, ShipBuilder.GALLEY,
				ShipBuilder.BRIDGE, ShipBuilder.CORRIDOR, ShipBuilder.GALLEY};
		floating.clear();
		for (int i = 0; i < loose.length; i++) {
			ShipBuilder.Room room = rooms[i];
			double x = origin.getX() + room.x1() + 0.5 + random.nextDouble() * (room.x2() - room.x1());
			double z = origin.getZ() + room.z1() + 0.5 + random.nextDouble() * (room.z2() - room.z1());
			double y = origin.getY() + 1.4 + random.nextDouble() * 1.4;
			ItemEntity item = new ItemEntity(world, x, y, z, loose[i]);
			item.setNoGravity(true);
			item.setVelocity((random.nextDouble() - 0.5) * 0.03, (random.nextDouble() - 0.3) * 0.02, (random.nextDouble() - 0.5) * 0.03);
			item.setPickupDelayInfinite();
			item.setNeverDespawn();
			world.spawnEntity(item);
			floating.add(item.getUuid());
		}
		sendPayload();
	}

	private void engineRelight() {
		gravityOff = false;
		engine = true;
		state.flipped = true;
		state.markDirty();
		playToPlayer(ModSounds.ENGINE_RELIGHT, 1.0f);
		effect(CinematicPayloads.EFFECT_SHAKE, 1.3f, 50);
		ServerPlayerEntity player = player();
		if (player != null) {
			player.removeStatusEffect(StatusEffects.SLOW_FALLING);
			player.removeStatusEffect(StatusEffects.JUMP_BOOST);
		}
		for (UUID id : floating) {
			if (world().getEntity(id) instanceof ItemEntity item) {
				item.setNoGravity(false);
				item.setVelocity(0, -0.1, 0);
			}
		}
		settleTicks = 1200;
		CrewEntity c = crew(Crew.CASTELLANOS);
		if (c != null) {
			c.stand();
			Vec3d at = at(ShipBuilder.CASTELLANOS_START);
			c.refreshPositionAndAngles(at.x, at.y, at.z, 180f, 0f);
		}
		sendPayload();
	}

	private void breachNow() {
		ShipBuilder.breach(world(), origin);
		state.breached = true;
		alarm = true;
		state.markDirty();
		ShipBuilder.setConsoles(world(), origin, true);
		effect(CinematicPayloads.EFFECT_SHAKE, 0.7f, 30);
		sendPayload();
	}

	private void givePatchKit() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		player.getInventory().offerOrDrop(new ItemStack(ModItems.HULL_PLATING, 4));
		player.getInventory().offerOrDrop(new ItemStack(ModItems.REBREATHER, 1));
		player.sendMessage(Text.translatable("message.surrogate.transit.patch_kit").formatted(Formatting.AQUA), true);
	}

	private void breachOver() {
		state.breached = false;
		alarm = false;
		state.markDirty();
		ShipBuilder.setConsoles(world(), origin, false);
		ShipBuilder.topUpAir(world(), origin);
		sendPayload();
	}

	/** Into the seat, facing the window, hands off. */
	private void seatInPod() {
		ServerPlayerEntity player = player();
		if (player == null) return;
		state(true, true, true);
		Vec3d at = at(new Vec3d(5.5, 1, 23.5));
		player.teleport(world(), at.x, at.y, at.z, 0f, 0f);
	}

	private void sealHatch() {
		BlockPos hatch = origin.add(ShipBuilder.HATCH_POS);
		world().playSound(null, hatch, ModSounds.HATCH, SoundCategory.BLOCKS, 1.5f, 1.0f);
		crewTo(Crew.FERREIRA, new Vec3d(5.5, 1, 20.5), 0f);
		crewTo(Crew.CASTELLANOS, new Vec3d(3.5, 1, 19.5), 0f);
		crewTo(Crew.TEAGUE, new Vec3d(6.5, 1, 19.5), 0f);
		ShipBuilder.setLights(world(), origin, false);
	}

	/** A packet of seeds from the galley, pressed into the pilot's hand. Halloran asked for them. */
	private void giveSeeds() {
		ServerPlayerEntity player = player();
		if (player == null || player.getInventory().containsAny(stack -> stack.isOf(ModItems.TOMATO_SEEDS))) return;
		ItemStack seeds = new ItemStack(ModItems.TOMATO_SEEDS);
		if (!player.getInventory().insertStack(seeds)) player.dropItem(seeds, false);
		player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.0f);
	}

	/** Ballast is carried into the drop bay and sits by the seat, hating it. */
	private void catToThePod() {
		CatEntity cat = cat();
		if (cat == null) return;
		Vec3d at = at(new Vec3d(6.5, 1, 24.5));
		cat.refreshPositionAndAngles(at.x, at.y, at.z, 180f, 0f);
		cat.setSitting(true);
		cat.setInSittingPose(true);
	}

	/**
	 * The cat comes down too: the same cat, remade on the ground beside the pod bed. The one aboard goes, and
	 * so does any earlier landing's cat the habitat still remembers; the pod opening sweeps up strays after.
	 */
	private void catLands(ServerPlayerEntity player, HabitatState habitat) {
		CatEntity aboard = cat();
		if (aboard != null) aboard.discard();
		state.cat = null;
		if (habitat.cat != null && server.getOverworld().getEntity(habitat.cat) instanceof CatEntity earlier) earlier.discard();
		habitat.cat = null;
		if (habitat.origin == null) return;
		Vec3d at = Vec3d.of(habitat.origin).add(2.5, 1, 1.5);
		CatEntity landed = ShipBuilder.spawnCat(server.getOverworld(), at, player.getUuid());
		habitat.cat = landed == null ? null : landed.getUuid();
		habitat.markDirty();
		state.markDirty();
	}

	/** Ballast aboard. After the landing she belongs to the habitat, not the ship. */
	@Nullable
	private CatEntity cat() {
		return state.cat != null && world().getEntity(state.cat) instanceof CatEntity cat ? cat : null;
	}

	/** The week is over: the ship goes quiet, and the story continues in the pod on the ground. */
	private void land() {
		ServerPlayerEntity player = player();
		state.stage = TransitState.STAGE_DONE;
		state.label = "done";
		state.markDirty();
		ShipBuilder.forceChunks(world(), origin, false);
		release();
		objectiveClear();
		sendFade(255, 0);
		leaveStage();
		running = null;
		if (player == null) return;
		if (PilotManager.isPiloting(player)) PilotManager.disconnect(player, true, null, false);
		player.removeStatusEffect(StatusEffects.SLOW_FALLING);
		player.removeStatusEffect(StatusEffects.JUMP_BOOST);
		player.removeStatusEffect(ModEffects.REBREATHER);
		ServerPlayNetworking.send(player, TransitPayload.none());
		HabitatState habitat = HabitatState.get(server);
		PilotManager.data(player).welcomed = true;
		catLands(player, habitat);
		if (habitat.origin != null) {
			player.setSpawnPoint(World.OVERWORLD, habitat.origin.up(), 180f, true, false);
			Surrogate.LOGGER.info("Transit: complete, landing {}", player.getName().getString());
			LightRefresh.schedule(player, World.OVERWORLD, habitat.origin, 2, 30);
			if (Prologue.shouldBegin(server, habitat)) {
				Prologue.begin(player, habitat);
			} else {
				BlockPos inside = habitat.origin.up();
				player.teleport(server.getOverworld(), inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 180f, 0f);
				send(new CinematicPayloads.State(false, false, false));
				send(new CinematicPayloads.Fade(0, 60));
			}
		}
	}

	// ------------------------------------------------------------------ world and actors

	@Override
	public ServerWorld world() {
		ServerWorld world = TransitDimension.world(server);
		return world != null ? world : server.getOverworld();
	}

	@Override
	public BlockPos origin() {
		return origin;
	}

	@Override
	@Nullable
	public ServerPlayerEntity player() {
		return state.protagonist == null ? null : server.getPlayerManager().getPlayer(state.protagonist);
	}

	@Override
	@Nullable
	public CrewEntity crew(Crew who) {
		UUID id = state.crewId(who);
		return id != null && world().getEntity(id) instanceof CrewEntity crew ? crew : null;
	}

	@Nullable
	private CrewEntity sleeper(int index) {
		if (index < 0 || index >= state.sleepers.size()) return null;
		return world().getEntity(state.sleepers.get(index)) instanceof CrewEntity crew ? crew : null;
	}

	@Nullable
	private RobotEntity calibrationRobot() {
		return state.calibrationRobot != null && world().getEntity(state.calibrationRobot) instanceof RobotEntity robot ? robot : null;
	}
}
